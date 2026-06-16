# ADR-019: 경매 활성화/종료 및 CardSync Internal API 위임

| 항목 | 내용 |
|------|------|
| **Status** | Accepted |
| **Date** | 2026-06-15 |
| **Deciders** | POCAT 팀 |
| **Issue** | #235 |

---

## Context

pocat-batch의 `AuctionActivationTasklet`/`AuctionExpirationTasklet`은 자체 도메인 로직을 재구현한 `AuctionBatchService`를 호출한다. 이 로직은 메인 앱 `AuctionLifecycleService`와 분기되어 있으며, 다음 문제를 가진다.

- **치명적 버그**: `AuctionBatchService.activateAuction()`이 경매 활성화 기간을 `now.plusHours(7)`로 설정한다. 반면 메인 앱 `AuctionLifecycleService.activateApprovedAuction()`은 `AUCTION_DURATION_DAYS = 3일`을 사용한다. pocat-batch 경로로 활성화된 경매는 의도와 다르게 7시간짜리로 생성된다.
- **추가 갭**: pocat-batch 측 로직은 ES 인덱싱을 수행하지 않고, 입찰 상태 갱신(`markBidResults`)·`NO_BIDDER` 분기·이상거래 탐지(`logAnomalyIfNeeded`)가 누락되어 있다.

`CardSyncTasklet` 역시 TCGdex API/S3/`CardRepository`를 직접 호출하는 별도 구현으로, 메인 앱 `CardSyncService.syncAll()`과 중복·분기되어 있다.

ADR-014(전략 C — 메인 앱 internal API 위임)와 ADR-018(#222, cursor + chunk 기반 internal API 위임)에서 동일한 문제 패턴(pocat-batch의 도메인 로직 재구현으로 인한 중복·분기)을 해결한 선례가 있다. 본 ADR은 동일 패턴을 경매 활성화/종료, CardSync에 적용한다.

---

## Decision

### 1. 경매 활성화/종료 — `InternalAuctionController`에 신규 엔드포인트 추가

POCAT `InternalAuctionController`에 다음 두 엔드포인트를 신설한다 (기존 `/internal/auctions/{id}/recover-buyout`과 동일 컨트롤러).

- `POST /internal/auctions/{id}/activate` → `AuctionLifecycleService.activateApprovedAuction(id)` 그대로 호출
- `POST /internal/auctions/{id}/close-expired` → `AuctionLifecycleService.closeExpiredAuction(id)` 그대로 호출

두 메서드 모두 메인 앱이 이미 보유한 로직(Redisson 락 자체 보유, 최신 상태 재검증, ES 인덱싱, 이벤트 발행)을 그대로 사용한다. 응답은 `ApiResponseDto<Boolean>` — `true`=처리됨, `false`=스킵(상태 불일치 등 정상 흐름).

**에러 매핑** (`recoverBuyout`의 `IllegalStateException → 200` 패턴 확장):

- `AUCTION_LOCK_FAILED`(분산 락 충돌), `AUCTION_NOT_FOUND` → `200 OK` + `ApiResponseDto.success(false)`로 변환. pocat-batch 측에서 이를 "이번 회차 스킵, 다음 회차에 대상 조회 쿼리로 자동 재포함되어 재시도"로 처리하기 위함이다.
- 그 외 예외 → `500 Internal Server Error`.

### 2. CardSync — 비동기 트리거 + 신규 `InternalCardSyncController`

POCAT `CardSyncService.syncAll()`에 `@Async("syncExecutor")`를 부여한다. 신규 `InternalCardSyncController`의 `POST /internal/cards/sync`는 `syncAll()`을 트리거한 뒤 즉시 `202 Accepted` + `ApiResponseDto<Void>`를 응답한다 (fire-and-forget — 전체 동기화는 백그라운드에서 진행).

`syncExecutor`는 `corePoolSize=1`, `queueCapacity=1`로 설정되어 동시에 1개 동기화만 진행 가능하다. 이미 동기화가 진행 중이고 큐도 가득 찬 상태에서 추가 트리거 요청이 들어오면 `TaskRejectedException`이 발생하며, 이를 `409 Conflict` + 신규 `ErrorCode.CARD_SYNC_IN_PROGRESS`로 매핑한다.

### 3. pocat-batch 신규 클라이언트 — `MainAuctionLifecycleClient` / `MainCardSyncClient`

ADR-018의 `MainAiReindexClient`와 동일한 패턴으로 신설한다.

- `X-Internal-Token` 헤더 인증
- `Idempotency-Key` 헤더: `"auction-activate-{auctionId}-{jobExecutionId}"` / `"auction-close-{auctionId}-{jobExecutionId}"`
- `ApiResponseEnvelope<T>`로 메인 앱 응답(`ApiResponseDto<T>`)을 언래핑 (ADR-018 Phase 4 리뷰에서 발견된 언래핑 누락 버그를 재발하지 않도록 최초 구현부터 적용)
- 3회 재시도, 지수 백오프(1s → 2s → 4s), 4xx는 즉시 스킵(재시도 없음)

### 4. `AuctionActivationTasklet` / `AuctionExpirationTasklet` — thin tasklet으로 재작성

대상 조회는 기존 그대로 유지한다.

- 활성화 대상: `findAllByStatus(APPROVED)`
- 종료 대상: `findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(ACTIVE, now)`

조회된 각 `auctionId`에 대해 `MainAuctionLifecycleClient`를 호출하고, 응답(`true`/`false`/예외)에 따라 성공/스킵/실패를 집계하는 thin tasklet으로 재작성한다. 자체 Redisson 락 획득 코드와 `AuctionBatchService` 호출을 제거한다.

### 5. `CardSyncTasklet` — 단일 트리거 호출로 대체

TCGdex API, S3, `CardRepository`, `OutboxEventWriter`, `RestTemplate` 의존을 전부 제거하고, `MainCardSyncClient.triggerSync(jobExecutionId)` 1회 호출로 대체한다.

### 6. `AuctionBatchService` 삭제, `OutboxEventWriter` 조건부 삭제

`AuctionBatchService`를 삭제한다. `OutboxEventWriter`는 pocat-batch 내 참조가 0이 되면 함께 삭제한다 (OutboxRelay/Cleanup/Reaper/Repository/Entity는 별개 인프라 컴포넌트로 유지 — 본 작업 범위 외).

### 7. 락 소유권 — 항상 `AuctionLifecycleService`(메인 앱)가 소유

`auction:lock:{id}` Redisson 락은 `tryLock(0, SECONDS)` 즉시 실패 방식으로, 항상 메인 앱 `AuctionLifecycleService`가 획득/해제한다. 호출 경로(메인 앱 내부 직접 호출 vs pocat-batch의 internal API 호출)는 락 동작에 영향을 주지 않는다. pocat-batch와 메인 앱이 동일 Redis 클러스터(`REDIS_CLUSTER_NODES`)를 공유함을 확인했다.

### 8. 부수 효과 — 경매 활성화 기간 버그 수정

본 PR의 핵심 버그 수정으로서, 경매 활성화 기간이 (pocat-batch 경로 기준) 7시간 → 3일로 정정된다. 이는 원래 의도된 동작(`AUCTION_DURATION_DAYS = 3`)으로의 복원이므로 별도 운영 공지는 불필요하다.

---

## Internal API 스펙

ADR-018과 동일하게, 별도 `docs/api/` 문서를 생성하지 않고 본 ADR에 스펙을 직접 기술한다.

| 항목 | 내용 |
|---|---|
| Endpoint | `POST /internal/auctions/{id}/activate` |
| 호출 서비스 | `AuctionLifecycleService.activateApprovedAuction(Long auctionId)` |
| 응답 | `200 OK`, `ApiResponseDto<Boolean>` (`true`=활성화됨, `false`=스킵) |
| 인증 | `X-Internal-Token` (`InternalTokenAuthFilter`, `/internal/**` 공통 적용) |
| Idempotency-Key | `auction-activate-{auctionId}-{jobExecutionId}` (수신만, 상태 기반 자연 멱등이므로 서버측 dedup 키로는 미사용 — `recoverBuyout`과 동일 선례) |
| 에러 매핑 | `AUCTION_LOCK_FAILED`/`AUCTION_NOT_FOUND` → `200` + `success(false)`, 그 외 → `500` |

| 항목 | 내용 |
|---|---|
| Endpoint | `POST /internal/auctions/{id}/close-expired` |
| 호출 서비스 | `AuctionLifecycleService.closeExpiredAuction(Long auctionId)` |
| 응답 | `200 OK`, `ApiResponseDto<Boolean>` (`true`=종료됨, `false`=스킵) |
| 인증 | `X-Internal-Token` (`InternalTokenAuthFilter`, `/internal/**` 공통 적용) |
| Idempotency-Key | `auction-close-{auctionId}-{jobExecutionId}` (수신만, 상태 기반 자연 멱등) |
| 에러 매핑 | `AUCTION_LOCK_FAILED`/`AUCTION_NOT_FOUND` → `200` + `success(false)`, 그 외 → `500` |

| 항목 | 내용 |
|---|---|
| Endpoint | `POST /internal/cards/sync` |
| 호출 서비스 | `CardSyncService.syncAll()` (`@Async("syncExecutor")`, fire-and-forget) |
| 응답 | `202 Accepted`, `ApiResponseDto<Void>` |
| 인증 | `X-Internal-Token` (`InternalTokenAuthFilter`, `/internal/**` 공통 적용) |
| Idempotency-Key | `cardsync-{jobExecutionId}` (수신만, 동기화 자체는 `existsByTcgdexId`/`findByTcgdexIdIncludingDeleted` 기준 자연 멱등) |
| 에러 매핑 | `syncExecutor` 큐 만재(`TaskRejectedException`) → `409` + `ErrorCode.CARD_SYNC_IN_PROGRESS` |

다음 항목은 기존 internal API 컨벤션(`/internal/{domain}/{id}/{action}`, `X-Internal-Token`, 3회 재시도)을 그대로 유지한다. `/internal/cards/sync`는 단일 리소스가 아닌 배치 트리거이므로 path-variable 없는 고정 경로를 사용하며, 이는 ADR-018의 `/internal/ai/reindex-cards`와 동일한 컨벤션 이탈 사례다.

---

## 고려한 대안 (Rejected Options)

| 대안 | 기각 이유 |
|------|----------|
| pocat-batch 자체 로직을 메인 앱과 동기화 유지(양쪽 수정) | 분기 재발 위험이 상존하며, ADR-014/ADR-018에서 확립한 internal API 위임 선례와 불일치 → 기각 |
| CardSync에도 cursor + chunk 패턴(ADR-018) 적용 | CardSync는 TCGdex API 자체가 페이징을 제공하지 않고, 전체 동기화가 원자적 단위에 가깝다. 비동기 단일 트리거(fire-and-forget)가 더 적합하며, 불필요한 청크 분할·재호출 복잡도를 피할 수 있음 → 기각 |

**스키마 변경**: 없음

---

## 잔존 리스크 (Consequences)

| 항목 | 내용 |
|------|------|
| **CardSync 비동기 트리거의 진행 상태 가시성 부족** | `202 Accepted` 응답 후 실제 동기화 완료 여부를 pocat-batch가 직접 알 수 없다. 완료 확인은 다음 배치 회차의 `findByTcgdexIdIncludingDeleted` 자연 멱등 검사로 간접 확인된다. 별도 상태 조회 API는 범위 외. |
| **`syncExecutor` 단일 워커 한계** | `corePoolSize=1`/`queueCapacity=1`이므로, 동기화가 오래 걸리는 동안 추가 트리거는 `409 CARD_SYNC_IN_PROGRESS`로 거부된다. pocat-batch는 이를 실패로 집계하되 다음 회차 재시도로 자연 해소됨을 전제한다. |
| **internal API 호출 증가 (N+1)** | 활성화/종료 대상 건수만큼 internal API를 개별 호출한다(청크화 없음). 대상 건수가 일반적으로 적음(수십 건 이하)을 전제하며, 대량화 시 ADR-018식 청크 API로의 전환 필요성을 향후 재검토한다. |

---

## 구현 후 반영 사항 (Phase 4 리뷰)

Phase 4(REVIEW+SECURITY) 결과 다음 항목이 수정 또는 의도적으로 결정되었다.

- **`GlobalExceptionHandler` `ConstraintViolationException` 400 처리 추가**: `InternalAuctionController`의 `@Validated`+`@Positive` path variable 검증 실패가 기존에 미처리 500으로 떨어지던 버그를 수정했다. 전역 핸들러 추가로 `/internal/**` 외의 `@Validated` 컨트롤러에도 동일하게 적용되나, 기존 테스트에서 500을 기대하는 케이스가 없어 회귀 없음. 의도적 개선으로 기록.
- **`CardSyncTasklet`의 `RuntimeException` 전파 정책**: `triggerSync()` 호출 실패(예: 401 토큰 오류) 시 step 전체 실패로 처리한다. 이는 "카드 동기화 트리거 자체가 fire-and-forget이므로 트리거 실패는 배치 운영자 인지가 필요"라는 의도 — `AuctionActivationTasklet`/`AuctionExpirationTasklet`의 건별 스킵 전략과 의도적으로 다름. 이유: 경매는 건별 부분실패가 허용되나(다음 회차 DB 재조회로 자동 재시도), 카드 동기화는 단일 트리거이므로 실패 시 아예 스킵되어 운영자 인지 없이 누락될 위험이 있음.
- **`MainAuctionLifecycleClient`/`MainCardSyncClient` 4xx 스킵 정책**: HTTP 401 → `RuntimeException`(step 실패, 인증설정 이상), 기타 4xx(`400`, `404`, `409 CARD_SYNC_IN_PROGRESS`) → 로그 후 스킵(정상). 단, 404는 경로 오류 등 배포 설정 이상을 나타낼 수 있으므로 운영 초기 배포 후 로그 모니터링 권장.
- **`Idempotency-Key` 헤더**: 서버 측에서 중복요청 차단에 사용되지 않는 추적/로깅 전용 헤더. 실질 멱등성은 `AuctionLifecycleService`의 `tryLock(0, SECONDS)` + 상태 가드(`status==APPROVED`/`isClosable()`)로 보장된다.

---

## Related

- [ADR-014: 메인 앱 @Scheduled 스케줄러 8개 pocat-batch 완전 이전](ADR-014-scheduler-batch-migration-%23171.md) — internal API 위임 패턴(전략 C) 선례
- [ADR-018: AI 카드 임베딩 재색인 스케줄 배치 이전](ADR-018-ai-card-reindex-batch-migration-%23222.md) — cursor + chunk 기반 internal API 위임, `ApiResponseEnvelope` 언래핑 패턴
- [PRD #235 — 경매 활성화/종료 및 CardSync, Internal API 위임을 통한 배치 로직 통합](../prd/PRD-235.md)
- Issue #235
