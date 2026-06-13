# 테스트 결과 — AI 카드 임베딩 재색인 배치 이전 (#222)

**날짜**: 2026-06-13
**브랜치**: feat/ai-batch-reindex/#222

[ADR-018: AI 카드 임베딩 재색인 스케줄 배치 이전](../adr/ADR-018-ai-card-reindex-batch-migration-%23222.md)에 따라, 관리자 수동 트리거(`AdminAiService.reindexAll()`) 방식을 pocat-batch의 `aiReindexJob` + 메인 백엔드 internal API(`POST /internal/ai/reindex-cards`) 위임 방식으로 전환했다.

## 테스트 범위

| 저장소 | 패키지 / 모듈 | 비고 |
|---|---|---|
| POCAT | `domain.ai.rag` (controller, service, dto) | `InternalAiController`, `AiReindexChunkService`, `EmbeddingService`, `ReindexChunkRequest`/`ReindexChunkResponse` 등 |
| pocat-batch | `job/aireindex` (`aiReindexJob`), `client/MainAiReindexClient` | cursor 기반 청크 조회 + internal API 클라이언트 |

## 실행 환경

| 항목 | 내용 |
|------|------|
| JDK | 17.0.12 LTS |
| Spring Boot | 3.5.14 |
| DB (테스트) | H2 in-memory (MODE=MySQL) |
| 프레임워크 | JUnit 5, Mockito 5, Spring Batch Test |

## 결과

### POCAT — GREEN

| 항목 | 결과 |
|---|---|
| `domain.ai.rag` 패키지 | 26 / 26 PASS |
| 전체 테스트 스위트 | 549 tests, 0 failures, 0 errors — PASS |

```bash
./gradlew test --tests "com.rocketcrew.pocat.domain.ai.rag.*"
./gradlew test
```

### pocat-batch — GREEN

| 항목 | 결과 |
|---|---|
| `aireindex` / `MainAiReindexClient` 관련 | 9 tests PASS |
| 전체 빌드 | BUILD SUCCESSFUL |

```bash
./gradlew test --tests "com.rocketcrew.pocatbatch.job.aireindex.*" --tests "com.rocketcrew.pocatbatch.client.MainAiReindexClientTest"
./gradlew build
```

---

## 주요 검증 항목

- 멱등성 키 형식 검증 (`reindex-cards-{firstCardId}-{lastCardId}-{jobExecutionId}`)
- ES `metadata.cardId.keyword` 기반 기인덱싱 카드 필터링
- Rate limit 도달 시 `rateLimited=true` 응답 및 배치 조기 종료
- `ApiResponseDto<ReindexChunkResponse>` 언래핑 정상 동작 (Phase 4 수정 후, `ApiResponseEnvelope<T>` 통한 `data` 필드 추출)
- ES `IOException` 발생 시 fail-open 동작 (해당 청크 전체를 미인덱싱으로 간주하여 재시도 대상에 포함)
- 빈 `cardIds` 목록 수신 시 조기 반환 (불필요한 ES/Gemini 호출 없음)
- 입력 검증: `cardIds`가 비어있거나 100개를 초과하는 경우 400 응답 (`@NotEmpty @Size(max=100)`)

---

## Notes

- 모든 테스트는 실제 외부 의존성(Gemini API, Elasticsearch, Redis) 없이 Mock 기반으로 실행되었다.
- pocat-batch 측 `MainAiReindexClient`는 `RestTemplate` + `ApiResponseEnvelope<ReindexChunkResponse>` 역직렬화로 메인 백엔드 internal API 응답을 처리한다.
- 배치 측 cursor 기반 청크 조회(`findActiveCardIdsAfter`) 및 조기종료 조건(rate limit 도달, cursor 끝 도달, 재시도 3회 실패)은 `aiReindexJob` 테스트에서 함께 검증되었다.
