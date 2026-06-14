# ADR-018: AI 카드 임베딩 재색인 스케줄 배치 이전

| 항목 | 내용 |
|------|------|
| **Status** | Accepted |
| **Date** | 2026-06-13 |
| **Deciders** | POCAT 팀 |
| **Issue** | #222 |

---

## Context

현재 AI 카드 임베딩 재색인은 `AdminAiService.reindexAll()`(`/api/v1/admin/ai/reindex`)을 통해 관리자가 수동으로 트리거해야 한다.

이 방식은 다음 문제를 가진다.

- **수동 트리거 의존**: 신규 카드 누락, ES 색인 장애 복구 등 재색인이 필요한 상황마다 관리자가 직접 API를 호출해야 한다.
- **Gemini rate-limit(100/min) 도달 시 비일률적 동작**: 한도 도달 시 재시도 후에도 실패하면 해당 카드를 skip하고 다음 카드로 진행한다. 어떤 카드가 처리되었고 어떤 카드가 skip되었는지 추적이 어렵다.
- **대량 처리 시 서버 다운 전례**: 4만 장 이상의 카드를 한 번에 처리할 때 서버가 다운된 전례가 있다. 단일 요청-응답 사이클 내에서 전체 카드를 동기 처리하는 구조이므로, 대량 처리 시 타임아웃·메모리·커넥션 점유 문제가 누적된다.

ADR-014에서 메인 앱의 `@Scheduled` 스케줄러 8개를 pocat-batch로 이전하며, 복잡한 도메인(결제 등)은 "전략 C — 메인 앱 Internal REST API 위임" 패턴을 확립했다. AI 임베딩 재색인 역시 pocat-batch가 직접 Gemini/ES를 호출하기보다, 이 선례를 따라 메인 백엔드에 위임하는 것이 적절하다.

---

## Decision

### pocat-batch에 `aiReindexJob` 신설 (cursor 기반 청크 + internal API 위임)

pocat-batch에 스케줄 배치 `aiReindexJob`을 신설한다.

1. **카드 ID 청크 수집**: `findActiveCardIdsAfter`(cursor 기반 페이징)으로 ACTIVE 상태 카드 ID를 100개 단위 청크로 조회한다.
2. **메인 백엔드 internal endpoint 호출**: 각 청크를 메인 백엔드 신규 internal endpoint `POST /internal/ai/reindex-cards`에 전달한다.
3. **메인 백엔드 처리**: 메인 백엔드는 ES(`pocat-ai-index`)에서 `metadata.cardId.keyword` terms query로 이미 인덱싱된 카드를 필터링하고, 미인덱싱 카드만 `RedisRateLimiter`(key=`ratelimit:ai-embedding`, 80/60s, 분산 rate-limit) 제한 하에 Gemini 임베딩 생성 후 ES upsert한다.

### "인덱싱 완료" 판정 — DB 컬럼 추가 없음

본 설계는 신규 DB 컬럼·테이블을 추가하지 않는다. "인덱싱 완료" 여부는 ES 문서 존재 여부(`metadata.cardId.keyword`)만으로 판정한다.

- 실패한 카드는 ES에 반영되지 않은 상태로 남는다.
- 다음 배치 회차에서 동일 카드가 다시 "미인덱싱"으로 분류되어 자동 재시도된다 (self-healing).
- 별도의 실패 추적·재시도 로직이 불필요하다.

### Redis 기반 분산 Rate Limit

`RedisRateLimiter`(key=`ratelimit:ai-embedding`, 80/60s)를 사용하여 Gemini API 호출을 제한한다. Gemini의 실제 한도(100/min)보다 낮은 80/min을 적용하여 여유를 둔다.

---

## 고려한 대안 (Rejected Options)

| 대안 | 기각 이유 |
|------|----------|
| **resilience4j JVM-local RateLimiter** | EC2 ASG 멀티 인스턴스(ADR-014) 환경에서 인스턴스별로 독립된 한도가 적용되어, 전체 합산 호출량이 Gemini 한도(100/min)를 초과할 수 있다. 인스턴스 간 합산 한도를 보장할 수 없어 기각 — Redis 기반 분산 rate-limit(`RedisRateLimiter`) 채택 |
| **DB에 `embedding_indexed` 플래그 컬럼 추가** | 색인 완료 여부를 명시적으로 추적할 수 있으나, 스키마 고정성(기존 운영 DB에 컬럼 추가)을 이유로 거부됨. ES 문서 존재 여부로 동등한 판정이 가능하므로 대체 |
| **pocat-batch가 ES/Gemini 직접 호출** | pocat-batch에는 Spring AI·Elasticsearch 클라이언트 의존성이 없다. ADR-014 전략 C(`AuctionBuyoutRecoveryTasklet`, `RefundRetryTasklet`)의 internal API 위임 선례와 동일하게, AI 임베딩·ES 색인 로직의 단일 책임을 메인 백엔드에 유지하는 것이 일관성 있음 → 기각 |

---

## Internal API 컨벤션 이탈

기존 internal API(`/internal/auctions/{id}/recover-buyout`, `/internal/refunds/{id}/retry` 등)는 다음 패턴을 따른다.

- `/internal/{domain}/{id}/{action}` — path-variable 기반, body 없음, 단일 리소스 대상

`/internal/ai/reindex-cards`는 다음과 같이 이 컨벤션에서 의도적으로 이탈한다.

- 최대 100개 cardId를 담은 **청크**를 한 번에 전달해야 하므로 **요청 body**를 사용한다 (`{ "cardIds": [Long, ...] }`).
- 단일 리소스가 아닌 **배치 작업 단위**이므로 path-variable 대신 고정 경로(`/internal/ai/reindex-cards`)를 사용한다.
- 응답은 `ApiResponseDto<ReindexChunkResponse>`로, 통계 5필드(`processedCount`, `skippedCount`, `indexedCount`, `failedCount`, `rateLimited`)를 포함한다. 이는 pocat-batch가 조기 종료(rate limit 도달 시 다음 청크 호출 중단) 여부를 판단하는 데 사용된다.

다음 항목은 기존 internal API 컨벤션을 그대로 유지한다.

- `X-Internal-Token` 헤더 인증
- `Idempotency-Key` 헤더 (형식: `reindex-cards-{firstCardId}-{lastCardId}-{jobExecutionId}`)
- 3회 재시도, 4xx 응답 시 skip 처리

---

## 잔존 리스크 (Consequences)

| 항목 | 내용 |
|------|------|
| **RedisRateLimiter fail-open** | Redis 장애 시 rate-limit이 무력화되어 Gemini 호출 제한이 적용되지 않을 수 있다. 본 배치는 저빈도 스케줄로 실행되므로 노출 빈도는 최소화되나, 잔존 리스크로 기록한다. |
| **처리 소요 시간** | 4만 장 × 80/min ≈ 8.3시간이 소요된다. 단일 회차 내 완료를 보장하지 않으나, self-healing(미인덱싱 카드는 다음 회차에 자동 재시도) 구조이므로 여러 회차에 걸쳐 점진적으로 수렴하는 것을 허용한다. |
| **`metadata.cardId.keyword` ES 동적 매핑 의존** | terms query가 `metadata.cardId.keyword` 필드의 동적 매핑(keyword 타입)에 의존한다. 구현 시 `_mapping` API로 1회 확인을 권장한다. |

**스키마 변경**: 없음

---

## 구현 후 반영 사항 (Phase 4 리뷰)

Phase 4(REVIEW+SECURITY) 결과 다음 항목이 수정되었다.

- pocat-batch `MainAiReindexClient`가 메인 백엔드 응답(`ApiResponseDto<ReindexChunkResponse>`)을 언래핑하지 않고 그대로 역직렬화하던 버그를 수정했다. `ApiResponseEnvelope<T>`를 도입하여 `data` 필드를 추출한 뒤 `ReindexChunkResponse`로 반환한다.
- POCAT `EmbeddingService`의 Gemini 호출 rate-limit 설정값(80/60s)을 하드코딩 상수 대신 `RateLimitProperties.aiEmbeddingLimit` / `aiEmbeddingWindowSeconds`로 주입받도록 변경했다.
- `ReindexChunkRequest.cardIds`에 `@NotEmpty @Size(max=100)` 입력 검증을 추가하여, 빈 목록 또는 100개 초과 요청 시 400 응답을 반환하도록 했다.

---

## Related

- [ADR-014: 메인 앱 @Scheduled 스케줄러 8개 pocat-batch 완전 이전](ADR-014-scheduler-batch-migration-%23171.md) — internal API 위임 패턴(전략 C) 선례
- [ADR-013: cleanup/reindex/bank 정리](ADR-013-cleanup-reindex-bank-%23168.md) — RAG bulk reindex 최초 도입
- Issue #222 — AI 카드 임베딩 재색인 배치 이전 트래킹
