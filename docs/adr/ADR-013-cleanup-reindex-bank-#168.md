# ADR-013: 테스트 태그 정리, RAG 벌크 재색인 엔드포인트, User 계좌 컬럼 제거 (#168)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-01 |
| **상태** | Accepted |
| **이슈** | #168 |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

이슈 #168은 3개의 독립적인 개선 작업을 하나의 PR로 처리한다.

1. 구현 완료된 테스트에 잔존하는 `[RED]` 태그 정리 (가독성)
2. RAG 벡터 스토어에 기존 ACTIVE 카드 데이터가 없을 경우 AI 어시스턴트가 항상 가이드 메시지만 반환하는 문제 해결
3. `User` 도메인의 계좌 정보(`bank_name`, `bank_account`)가 실제 결제/정산 로직에서 사용되지 않아 제거

---

## 결정 (Decision)

### 1. [RED] 태그 정리

**결정**: `@DisplayName`에서 `[RED #nnn]`, `[RED]` 접두어를 제거한다.

**배경**

해당 테스트들은 `CardAnalysisResponse` DTO 도입(#164) 이후 모두 GREEN 상태다. 코드 로직 변경은 없으며 가독성 개선만 해당한다.

**변경 범위**

| 대상 | 처리 |
|------|------|
| `@DisplayName` 내 `[RED #nnn]` 접두어 | 제거 |
| `@DisplayName` 내 `[RED]` 접두어 | 제거 |
| 테스트 코드 로직 | **변경 없음** |

**트레이드오프**

- 장점: 테스트 보고서에서 구현 상태 추적 잡음 제거; 태그가 현재 상태(GREEN)를 반영하게 됨
- 단점: 없음 (순수 가독성 개선)

### 2. RAG 벌크 재색인 엔드포인트

**결정**: `POST /api/v1/admin/ai/reindex` 어드민 엔드포인트를 추가한다.

**문제 분석**

`EmbeddingService`는 이벤트 기반(카드 승인 시)으로만 동작한다. 신규 배포 또는 벡터 스토어 초기화 시 기존 ACTIVE 카드가 RAG에 존재하지 않아 AI 어시스턴트의 모든 질의가 Layer 2 가이드 메시지로만 응답하는 문제가 발생한다.

**엔드포인트 명세**

| 항목 | 내용 |
|------|------|
| 경로 | `POST /api/v1/admin/ai/reindex` |
| 응답 코드 | `202 Accepted` (즉시 반환) |
| 인가 | `hasRole('ADMIN')` |
| 처리 방식 | `@Async` 비동기 처리 |

**처리 흐름**

```text
POST /api/v1/admin/ai/reindex
    │
    ├─ 202 Accepted 즉시 반환 (@Async — timeout 방지)
    │
    └─ [비동기]
        ├─ ACTIVE 카드 page size 100으로 순차 조회 (OOM 방지)
        ├─ 임베딩 실패 시 최대 2회 재시도 후 failedCount 누적
        └─ CardAnalysisService.buildCardContext() 동일 포맷의 cardText 생성 → embedCard() upsert
```

**트레이드오프**

- 장점: 벡터 스토어 초기화 후 어드민이 수동 재색인 가능; `@Async` + `202 Accepted`로 HTTP timeout 방지; page size 100 배치 처리로 OOM 방지; upsert 방식으로 동일 cardId Document 중복 방지
- 단점: 재색인 완료 여부를 클라이언트가 별도로 확인해야 함 (진행 상태 엔드포인트 미포함); 대용량 카드 데이터의 경우 재색인 소요 시간이 길 수 있음

**운영 고려사항**

| 항목 | 내용 |
|------|------|
| 재시도 정책 | 임베딩 API 일시 오류 시 최대 2회 재시도. 최종 실패 시 `failedCount` 누적 후 완료 로그에 집계 |
| 진행 상태 | 별도 상태 조회 엔드포인트 없음. 페이지 단위 INFO 로그 + 완료 시 `성공=N건, 실패=M건` 로그로 운영자 확인 |
| 타임아웃 | `@Async` 스레드 풀(`spring.task.execution.pool.keep-alive`)로 장기 실행 제어. 기본값 사용 |
| 장애 에스컬레이션 | 완료 로그에 `failedCount > 0` 확인 시 운영자가 재색인 엔드포인트 재호출로 복구 |
| AtomicBoolean 중복 방지 | 동시 요청 시 두 번째 요청은 202 반환 후 실제 작업 없이 silent 종료. 중복 실행 방지 |

### 3. User 계좌 컬럼 제거

**결정**: `User` 엔티티에서 `bank_name`, `bank_account` 컬럼 및 관련 코드를 제거하고 V9 마이그레이션을 적용한다.

**문제 분석**

`bank_name`, `bank_account`가 `User` 엔티티에 존재하나 실제 결제 흐름(빌링키 기반 PG 결제)에서 사용되지 않는다. `Settlement` 도메인의 `SettlementRepositoryCustomImpl`이 `seller.bankName`, `seller.bankAccount`를 조회하고 있으므로 컬럼 제거 시 `Settlement`도 함께 수정해야 한다.

**변경 범위**

| 대상 파일 / 항목 | 처리 |
|-----------------|------|
| `User.java` | `bankName`, `bankAccount` 필드 제거 |
| `UserController` | 계좌 관련 엔드포인트 제거 |
| `UserCommandService` | 계좌 업데이트 메서드 제거 |
| `UserResponse` | `bankName`, `bankAccount` 필드 제거 |
| `AdminUserResponse` | `bankName`, `bankAccount` 필드 제거 |
| `AdminSettlementResponse` | `bankName`, `bankAccount` 필드 제거 |
| `SettlementRepositoryCustomImpl` | `seller.bankName`, `seller.bankAccount` 참조 제거 |
| `UpdateBankRequest.java` | 파일 삭제 |
| `V9__remove_bank_columns.sql` | `ALTER TABLE users DROP COLUMN bank_name, bank_account` |

**마이그레이션 내용**

```sql
ALTER TABLE users
    DROP COLUMN bank_name,
    DROP COLUMN bank_account;
```

**운영 안전장치**

| 항목 | 내용 |
|------|------|
| 사전 백업 | V9 마이그레이션 실행 전 프로덕션 DB 전체 백업 필수 |
| 롤백 절차 | 백업 복원 또는 수동으로 `ALTER TABLE users ADD COLUMN bank_name VARCHAR(100), ADD COLUMN bank_account VARCHAR(100)` 후 데이터 복원 |
| 스테이징 검증 | 프로덕션 배포 전 스테이징 DB에서 V9 마이그레이션 실행 후 정상 동작 확인 |
| Flyway 상태 | `flyway:info`로 V9 적용 상태 확인 후 배포 진행 |

**사전 배포 체크리스트**

- [ ] 프로덕션 DB 백업 완료 및 복원 테스트 확인
- [ ] 스테이징 환경에서 V9 마이그레이션 실행 검증
- [ ] `bank_name`, `bank_account` 참조 코드 제거 확인 (컴파일 + 단위 테스트 PASS)
- [ ] 클라이언트 사전 공지 완료 (Breaking Change: `PUT /api/v1/users/me/bank-account` 제거)

**Breaking Change**

`PUT /api/v1/users/me/bank-account` 엔드포인트가 제거된다. 해당 엔드포인트를 사용하는 클라이언트는 사전 공지 후 마이그레이션이 필요하다.

**트레이드오프**

- 장점: 사용되지 않는 컬럼 제거로 User 엔티티 단순화; 실제 결제 흐름과 데이터 모델 일치; 계좌 정보가 API 응답에서 노출되지 않아 보안성 향상
- 단점: Breaking Change — `PUT /api/v1/users/me/bank-account` 엔드포인트 제거; `UserResponse`, `AdminUserResponse`, `AdminSettlementResponse` 응답 스키마 변경으로 클라이언트 사전 공지 필요

---

## 고려한 대안 (Alternatives Considered)

### 스케줄러 자동 재색인 (Decision 2 대안)

- **거절 이유**: 주기적 자동 재색인은 변경이 없는 경우에도 불필요한 임베딩 API 호출 비용이 발생한다. 벡터 스토어 초기화는 배포·장애 복구 등 특정 상황에서만 발생하므로 어드민 수동 트리거가 비용 효율적이다.

### bank_name, bank_account Deprecated 처리 후 단계적 제거 (Decision 3 대안)

- **거절 이유**: 해당 필드가 현재 어떤 클라이언트에서도 실제 사용되지 않음이 확인되었다. Deprecated 유지 기간 동안 불필요한 코드 복잡도가 증가하므로 즉시 제거가 적절하다.

---

## 결과 (Consequences)

### 긍정적 영향

- 테스트 보고서에서 `[RED]` 태그 잡음이 제거되어 가독성이 향상된다.
- RAG 벡터 스토어 초기화 후 어드민이 `POST /api/v1/admin/ai/reindex`로 수동 재색인하여 AI 어시스턴트 기능을 즉시 복구할 수 있다.
- `User` 엔티티에서 미사용 계좌 컬럼이 제거되어 데이터 모델이 실제 결제 흐름과 일치한다.
- 계좌 정보가 API 응답에서 제거되어 불필요한 개인 정보 노출이 줄어든다.

### 부정적 영향 / 트레이드오프

- `PUT /api/v1/users/me/bank-account` 엔드포인트 제거는 Breaking Change다. 기존 클라이언트가 해당 엔드포인트를 호출 중이라면 마이그레이션 기간을 설정하고 사전 공지해야 한다.
- `UserResponse`, `AdminUserResponse`, `AdminSettlementResponse`에서 `bankName`, `bankAccount` 필드가 제거된다. 해당 응답을 파싱하는 클라이언트는 수정이 필요하다.
- 재색인 진행 상태를 실시간으로 확인하는 엔드포인트가 없다. 대용량 데이터 환경에서는 재색인 완료 여부를 로그나 AI 응답 품질로 간접 확인해야 한다.

---

## 관련 문서

- `docs/adr/ADR-012-ai-api-hardening-#164.md` — AI API 강화 (CardAnalysisResponse DTO 분리, [RED] 태그 발생 원인)
- `docs/adr/ADR-011-ai-requirements-completion-#158.md` — AI 요구사항 완성 (EmbeddingService 이벤트 기반 설계 결정)
- `docs/adr/ADR-004-ai-features.md` — AI 기능 통합 전략 (RAG·벡터 DB 초기 결정)

## 관련 코드

- `User.java` — `bankName`, `bankAccount` 필드 제거
- `UserController` — `PUT /api/v1/users/me/bank-account` 엔드포인트 제거
- `UserCommandService` — 계좌 업데이트 메서드 제거
- `UserResponse`, `AdminUserResponse`, `AdminSettlementResponse` — `bankName`, `bankAccount` 필드 제거
- `SettlementRepositoryCustomImpl` — `seller.bankName`, `seller.bankAccount` 참조 제거
- `UpdateBankRequest.java` — 파일 삭제
- `V9__remove_bank_columns.sql` — 신규 Flyway 마이그레이션 (`bank_name`, `bank_account` 컬럼 DROP)
- `AdminAiController` (또는 신규) — `POST /api/v1/admin/ai/reindex` 엔드포인트 추가
- `EmbeddingService` (또는 신규 `ReindexService`) — `@Async` 벌크 재색인 로직

---

## Phase 4 검토 결과

### 수용된 피드백

- **@Async 메서드 N+1 및 lazy load 위험**: `AdminAiService.reindexAll()`에서 `card.getSeries()`, `card.getPokemonSet()` 접근 시 N+1 쿼리 및 @Async 스레드 컨텍스트 lazy load 실패 가능. `CardRepository.findWithDetailsByStatus()` 메서드를 `@EntityGraph(attributePaths = {"series", "pokemonSet"})`로 추가하고 reindexAll()에서 사용.
- **ReindexResponse DTO 미사용 제거**: void @Async 반환 방식으로 구현되어 실제 사용되지 않는 `ReindexResponse.java` 삭제.
- **동시 재색인 DoS 방지**: `AtomicBoolean reindexRunning` 필드 추가. 이미 실행 중인 경우 즉시 반환하여 중복 실행 방지.
- **실패 카운터 추가**: `failedCount` 추적하여 완료 로그에 성공/실패 건수 분리 출력.

### 거부된 피드백 (근거 포함)

- **V9 DROP COLUMN IF EXISTS 추가**: Flyway 버전 마이그레이션은 정확히 1회만 실행됨. IF EXISTS 추가는 오히려 컬럼 부존재 시 버그를 은폐함. 거부.
- **Admin 감사 로그 추가**: 현재 프로젝트에 별도 감사 인프라 없음. YAGNI. 거부.

### 보안 감사 결과

- 재색인 엔드포인트: SecurityConfig URL 패턴 + @PreAuthorize 이중 ADMIN 보호 확인
- 카드 텍스트: DB 엔티티 기반 생성, 사용자 입력 미포함 — 벡터 스토어 인젝션 위험 없음
- bank_name/bank_account 제거: PII/PCI 공격 면적 축소 (보안 개선)
