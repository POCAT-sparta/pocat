# ADR-005: AI 인프라 결함 수정 스프린트 (#116)

**Status:** Accepted  
**Date:** 2026-05-27  
**Issue:** #116

---

## Context

AI 기능 레이어(#106)를 검토한 결과 10건의 인프라 결함이 확인되었다.

1. Flyway 의존성 누락 — V1/V2 SQL 파일 존재하지만 마이그레이션 미실행
2. EmbeddingService 미호출 — RAG 인덱스 항상 비어 있음
3. AiAssistantService 토큰 카운팅 하드코딩(0)
4. AiStreamController에서 Reactor `doOnComplete` 내부 `@Transactional` 블로킹 호출
5. 운영 설정에 `show-sql: true` 노출
6. Kafka `bootstrap-servers`가 `localhost`로 하드코딩
7. AI 엔드포인트에 Rate Limiting 없음
8. AiAssistantService에 CircuitBreaker 없음
9. `card_ai_analysis` 테이블 미기록
10. 로컬/운영 프로파일 미분리

---

## Decision

| 항목 | 결정 |
|------|------|
| Flyway | `baseline-on-migrate: true`, `baseline-version: 2` — 기존 `ddl-auto` 테이블 유지, V3+부터 신규 마이그레이션 추적 |
| Embedding 트리거 | `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` — 주 트랜잭션과 완전 분리 |
| Rate Limiting | Resilience4j `@RateLimiter` (기 도입) — 10 req/min/user, 초과 시 HTTP 429 반환 |
| CircuitBreaker | Resilience4j `@CircuitBreaker` — AiAssistantService 외부 AI 호출에 적용 |
| 프로파일 분리 | `application-local.yaml` (개발 오버라이드) + `application.yaml` (운영 안전 기본값) |
| ddl-auto | 현재 `update` 유지 — 핵심 테이블 전체 Flyway 커버리지는 별도 future task |
| 토큰 카운팅 | 실제 토큰 수 계산 로직으로 교체 |
| card_ai_analysis | AI 분석 완료 후 결과 저장 로직 추가 |

---

## Consequences

**긍정적:**
- DB 마이그레이션이 실제로 실행되어 스키마 일관성 확보
- RAG 검색 정확도 향상 (임베딩 인덱스 정상화)
- 운영 환경 로그 노출 제거 및 설정 보안 강화
- AI 엔드포인트 과부하 방지 및 장애 격리

**주의사항:**
- `baseline-version: 2`로 인해 V1·V2는 실행되지 않음 — 해당 SQL이 이미 적용된 상태여야 함
- `ddl-auto: update`는 임시 조치 — 전체 Flyway 전환은 별도 이슈로 추적 필요
- `@Async` 임베딩 처리 실패 시 알림 메커니즘(DLQ 또는 재시도) 추가 고려 필요
