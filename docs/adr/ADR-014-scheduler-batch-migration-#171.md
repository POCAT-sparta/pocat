# ADR-014: 메인 앱 @Scheduled 스케줄러 8개 pocat-batch 완전 이전

| 항목 | 내용 |
|------|------|
| **Status** | Proposed |
| **Date** | 2026-06-02 |
| **Deciders** | POCAT 팀 |
| **Issue** | #171 |

---

## Context

ADR-003에서 자유게시판 스케줄러 2개(`FreePostRankingScheduler`, `ViewCountFlushScheduler`)를 pocat-batch로 이전하며 배치 서버 분리 기반을 확립하였다.

그러나 POCAT 메인 앱에는 나머지 `@Scheduled` 스케줄러 6개가 여전히 내장되어 있다. EC2 ASG 다중 인스턴스 배포 시 **모든 스케줄러가 각 인스턴스에서 중복 실행**되어 다음 문제가 발생한다.

- 경매 활성화/만료 이벤트 중복 발행 → 낙찰·환불 로직 이중 실행 위험
- OutboxRelayScheduler 중복 relay → Kafka 메시지 중복 발행
- AiSessionCleanupScheduler 중복 실행 → 불필요한 DB/Redis 부하
- CardSyncScheduler 중복 실행 → TCGdex API 과호출 및 DB write 충돌

이전 대상 스케줄러 6개 및 재검토 대상 2개(ADR-003 이전 완료 포함) 전체 목록:

| # | 클래스 | 주기 | 도메인 |
|---|--------|------|--------|
| 1 | AiSessionCleanupScheduler | fixedRate 5min | AI 세션 |
| 2 | AuctionRankingScheduler | fixedDelay 60s | 경매 랭킹 (Redis ZSet) |
| 3 | OutboxRelayScheduler | fixedDelay 5s | Outbox → Kafka relay |
| 4 | CardSyncService.syncAll() | cron 매주 일 00:00 | TCGdex 카드 동기화 |
| 5 | AuctionActivationScheduler | cron 19:00 | 경매 활성화 (Redisson RLock) |
| 6 | AuctionExpirationBackupScheduler | cron 19:05~19:30 | 경매 만료·낙찰 백업 (Redisson RLock) |
| 7 | AuctionBuyoutRecoveryScheduler | fixedDelay 60s | 낙찰 복구 (결제 도메인) |
| 8 | RefundRetryScheduler | fixedDelay 60s | 환불 재시도 (결제 도메인) |

---

## Decision

스케줄러 성격에 따라 세 가지 이전 전략을 적용한다.

### 전략 A — 단순/중간 그룹: Lift-and-Shift 복제 (#1~4)

배치 서버에 동일 로직을 Spring Batch Tasklet으로 복제한다. 의존 도메인이 단순하고 멱등성이 보장되어 중복 실행 부작용이 없거나 무시 가능한 스케줄러에 적용한다.

| # | Tasklet | 주기 | 비고 |
|---|---------|------|------|
| 1 | AiSessionCleanupTasklet | fixedRate 5min | Redis TTL 기반, 멱등 |
| 2 | AuctionRankingTasklet | fixedDelay 60s | Redis ZSet ZADD/EXPIRE, 멱등 |
| 3 | OutboxRelayTasklet | fixedDelay 5s | 금융/일반 KafkaTemplate 분리 유지 |
| 4 | CardSyncTasklet | cron 매주 일 00:00 | TCGdex API → upsert, 멱등 |

### 전략 B — 복잡 그룹 Option 3: Tasklet 직접 DB 상태 전환 + Outbox write (#5·6)

Redisson RLock을 배치 서버에서 획득하고, Tasklet이 DB 상태를 직접 전환(`PENDING→ACTIVE`, `ACTIVE→EXPIRED`)한 뒤 `outbox_events` 테이블에 이벤트를 직접 삽입(write)한다. OutboxRelayTasklet(#3)이 Kafka로 중계한다.

- `@TransactionalEventListener` fast-path는 배치 서버 ApplicationContext에서 동작하지 않으므로 직접 Outbox write를 선택한다.
- Redisson RLock 으로 단일 실행 보장 (배치 인스턴스 스케일아웃 시에도 안전).

| # | Tasklet | 주기 | 비고 |
|---|---------|------|------|
| 5 | AuctionActivationTasklet | cron 19:00 | Redisson RLock + Outbox write |
| 6 | AuctionExpirationBackupTasklet | cron 19:05~19:30 | Redisson RLock + Outbox write |

### 전략 C — 복잡 그룹 Option 2: 메인 앱 Internal REST API 위임 (#7·8)

결제(PortOne) 도메인 전체를 배치 서버에 복제하는 대신, 배치 서버가 메인 앱의 Internal REST API를 호출하여 복구·환불 재시도를 위임한다.

- PortOne 클라이언트를 배치 서버에 복제하면 이중 결제 취소 위험이 발생하므로 기각.
- 메인 앱이 결제 로직·트랜잭션 경계를 단독 소유.

| # | Tasklet | 주기 | 위임 엔드포인트 |
|---|---------|------|----------------|
| 7 | AuctionBuyoutRecoveryTasklet | fixedDelay 60s | `POST /internal/auctions/{id}/recover-buyout` |
| 8 | RefundRetryTasklet | fixedDelay 60s | `POST /internal/refunds/{id}/retry` |

**내부 API 인증**: `X-Internal-Token` 헤더 (환경변수 주입). 배치 서버는 `POCAT_API_BASE_URL` 환경변수로 메인 앱 엔드포인트를 참조한다.

### 공통 결정 사항

**ES 인덱싱 비동기 위임**
- 배치 Tasklet은 ES를 직접 쿼리하지 않고 Outbox/Kafka를 경유하여 비동기 위임한다.
- SLA: p99 ≤ 30s. ES 쿼리 방어 필터를 주방어선으로 유지.

**Feature Flag 전환**
- 각 스케줄러에 `pocat.scheduler.{name}.enabled` 플래그를 적용한다.
- 메인 앱의 기존 스케줄러는 플래그로 비활성화 후 검증, 검증 완료 시 코드 삭제.

**Outbox 보조 작업**
- `OutboxReaperTasklet`: PROCESSING 상태 5분 초과 레코드 → PENDING 리셋 (stuck 회수)
- `OutboxCleanupTasklet`: SENT 상태 7일 초과 레코드 → 삭제 (테이블 비대화 방지)

---

## Rejected Options

| 옵션 | 기각 이유 |
|------|----------|
| 전체 스케줄러 단순 lift-and-shift | `@TransactionalEventListener` fast-path가 배치 서버 ApplicationContext에서 동작 안 함; PortOne·결제 도메인 전체 복제 불필요 |
| OutboxRelayScheduler를 메인 앱에 유지 | ASG 다중 인스턴스에서 relay 중복 발행이 근본 문제이므로 해결 안 됨 |
| PortOne 클라이언트 배치 서버 복제 (#7·8) | 이중 결제 취소 위험, 결제 트랜잭션 경계 분산으로 정합성 보장 불가 |
| #5·6 메인 앱 Internal API 위임 (Option 2) | 경매 상태 전환이 결제와 달리 배치 서버에서 직접 처리 가능; Outbox write 패턴으로 충분 |

---

## Migration Plan

| 단계 | 내용 | 담당 |
|------|------|------|
| 1 | pocat-batch에 Tasklet 구현 (#1~6) + Internal API 클라이언트 구현 (#7·8) | 개발팀 |
| 2 | `pocat.scheduler.{name}.enabled=false` 로 메인 앱 스케줄러 비활성화 (Feature Flag) | 개발팀 |
| 3 | 스테이징 환경 병행 검증 (7일 이상, 아래 종료 기준 확인) | 개발팀 + QA |
| 4 | 운영 배포 후 병행 운영 기간 진행 (롤백 조건: 오류율 > 1% 또는 데이터 불일치 감지 시 배치 중단) | 개발팀 + 운영팀 |
| 5 | 종료 기준 충족 후 메인 앱 스케줄러 코드 삭제, Feature Flag 제거 | 개발팀 |

**병행 운영 종료 기준 (checklist):**
- [ ] 7일 이상 연속 배치 Job 오류율 0% 유지
- [ ] Outbox PROCESSING stuck 레코드 미누적 (OutboxReaper 정상 동작 확인)
- [ ] 경매 활성화/만료/낙찰/환불 데이터 정합성 검증 완료
- [ ] Internal API (#7·8) 5xx 응답률 < 0.1%
- [ ] ES 인덱싱 p99 지연 ≤ 30s 유지

---

## Consequences

**긍정적 효과**
- 메인 앱: 순수 API 서버로 단일 책임 달성
- pocat-batch: 모든 비동기·배치 작업 단일 책임
- EC2 ASG 스케일아웃 시 스케줄러 중복 실행 문제 완전 해소
- Redisson RLock으로 배치 서버 다중 인스턴스 환경에서도 단일 실행 보장
- Spring Batch 메타테이블을 통한 Job 실행 이력 영속 관리

**부정적 효과 / 주의사항**
- 병행 운영 기간 중 Feature Flag 미적용 시 중복 실행 지속 (플래그 적용 필수)
- Internal API (#7·8) 네트워크 장애 시 배치 재시도 정책 필요
- OutboxRelayTasklet(#3) 이전 전 메인 앱 OutboxRelayScheduler 비활성화 순서 준수 필요
- 배치 서버 인프라 관리 포인트 유지 (ADR-003 대비 추가 없음, 기존 pocat-batch 확장)

**스키마 변경**: 없음 (기존 `outbox_events`, `BATCH_*` 메타테이블 재사용)

---

## Monitoring

| 항목 | 감시 대상 | 방법 |
|------|-----------|------|
| Outbox relay 중복 | outbox_events 처리 건수 vs Kafka 발행 건수 | 로그 집계 |
| Outbox stuck | PROCESSING 5분 초과 레코드 수 | DB 모니터링 |
| 경매 상태 전환 정합성 | PENDING→ACTIVE→EXPIRED 전환 누락·중복 | 상태 카운트 비교 |
| Internal API 응답률 | /internal/auctions/.../recover-buyout, /internal/refunds/.../retry 5xx | APM |
| ES 인덱싱 지연 | Outbox write → ES 반영 시간 | p99 latency |
| OutboxCleanup | outbox_events 테이블 크기 추이 | DB 메트릭 |

---

## Related

- [ADR-003: 배치 서버 분리](ADR-003-batch-server-extraction.md) — pocat-batch 기반 확립, 자유게시판 스케줄러 2개 이전
- [pocat-batch 레포](https://github.com/POCAT-sparta/pocat-batch) — 배치 서버 구현체
- Issue #171 — 메인 앱 스케줄러 완전 이전 작업 트래킹
