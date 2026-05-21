# ADR-002: 인기 경매 조회 랭킹 설계

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-21 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

POCAT 경매 서비스에서 사용자에게 인기 있는 경매 목록을 빠르게 제공해야 하는 요구사항이 생겼다. 인기 경매란 좋아요(like)와 입찰(bid) 활동이 많은 경매를 의미하며, 실시간에 가까운 최신성을 유지하면서도 DB에 과도한 부하를 주지 않아야 한다.

기존 커뮤니티 자유게시판(FreePost)에서 `ranking:free:popular` Redis ZSet + 60초 스케줄러 갱신 패턴을 이미 검증하였다. 경매 도메인에도 동일 패턴을 적용하되, 경매 특성에 맞는 점수 산식·쿼리 범위·TTL을 별도로 결정해야 했다.

---

## 결정 (Decision)

### 1. 갱신 방식: 스케줄러 기반 전체 재계산 (Option A)

**대안 검토**

| 방식 | 설명 | 문제점 |
|------|------|--------|
| **Option A** (채택) | 60초마다 스케줄러가 DB를 전체 조회해 ZSet을 재빌드 | — |
| Option B | 좋아요·입찰 이벤트 발생 시 `ZINCRBY`로 점수 즉시 반영 | 좋아요 **취소** 시 Redis 값을 정확하게 감소시킬 수 없어 DB와 Redis 간 불일치가 비가역적으로 누적될 위험 |

**결정**: Option A (스케줄러 기반 전체 재계산)

- DB가 유일한 source of truth이므로, 주기적으로 DB 기준 전체 재계산하면 오염 없이 self-healing됨
- FreePost에서 검증된 동일 패턴(`refreshRanking` + atomic rename) 재사용으로 구현 위험 최소화
- Redis ZSet 키: `ranking:auction:popular`
- 스케줄러: `@Scheduled(fixedDelay = 60_000)` (60초)
- Atomic 교체: `ranking:auction:popular:new` 에 빌드 후 `RENAME` → 기존 키 교체

### 2. 점수 산식: `likeCount × 1.0 + bidCount × 3.0`

**결정**: `score = likeCount × 1.0 + bidCount × 3.0`

- 입찰(bid)은 단순 관심 표현인 좋아요(like)보다 실제 구매 의향이 훨씬 강한 신호이므로 가중치 3배 부여
- 좋아요는 소극적 관심 표현이므로 가중치 1.0 (기준값)
- 향후 지표 추가(예: 조회수) 시 산식 확장 가능

### 3. score = 0 경매 제외

**결정**: `likeCount + bidCount = 0`인 경매는 랭킹에서 제외

- 좋아요와 입찰이 모두 0인 경매는 실질적인 인기 신호가 없으므로 인기 경매 목록에 노출할 이유가 없음
- score > 0 조건으로 필터링하여 의미 없는 항목이 ZSet에 적재되는 것을 방지

### 4. 스케줄러 조회 범위: ACTIVE 경매 전체, ORDER BY started_at DESC

**결정**: DB에서 `status = ACTIVE`인 경매를 `LIMIT` 없이 전체 조회, `ORDER BY started_at DESC`

**LIMIT 불필요 근거**:
- 경매 기간이 3일 고정이므로 특정 시점의 ACTIVE 경매 수 = 최근 3일간 승인된 경매 수
- 플랫폼 특성상 일 승인 건수에 자연적인 상한이 존재하며, 전체 ACTIVE 경매 수도 이에 비례한 자연 상한을 갖음
- 별도의 임의 LIMIT을 두는 것은 서비스 자체의 경매 노출을 제한하는 결과가 되어 부적절

**`ORDER BY started_at DESC` 근거**:
- `createdAt`은 사용자가 경매를 제출한 시점(승인 전)이므로 경매 실제 시작 시점을 반영하지 못함
- `startedAt`은 관리자 승인 후 경매가 실제 활성화된 시점으로, ACTIVE 상태이면 반드시 세팅되어 있음
- 최신 경매를 우선 처리하는 의도에 `startedAt` DESC가 부합

### 5. TTL: 70초

**결정**: Redis ZSet TTL = 70초

- 스케줄러 갱신 주기(60초)보다 10초 버퍼를 둠으로써 갱신 직전 순간에도 캐시가 만료되지 않도록 보장
- FreePost 랭킹과 동일한 TTL 정책 적용

---

## 결과 (Consequences)

### 긍정적 영향

- **읽기 성능**: 인기 경매 조회가 Redis ZSet 단순 range 조회로 처리되어 DB 부하 없음
- **정확성 보장**: 스케줄러가 DB 기준으로 주기적으로 재계산하므로 Redis/DB 불일치가 자동 복구됨
- **구현 안전성**: FreePost에서 검증된 패턴(ZSet atomic rename + fallback) 재사용으로 신규 위험 최소화
- **Fallback 내성**: Redis 장애 시 DB `GROUP BY` 쿼리로 자동 폴백, 서비스 연속성 보장

### 부정적 영향 / 주의사항

- **최대 60초 지연**: 좋아요·입찰 발생 즉시 랭킹에 반영되지 않으며 최대 60초의 반영 지연이 존재
- **스케줄러 주기 부하**: 60초마다 ACTIVE 경매 전체를 DB에서 읽으므로, 서비스 규모 확대 시 쿼리 비용을 재평가해야 함 (현 규모에서는 자연 상한으로 문제 없음)
- **Redis 단일 장애점**: Redis 장애 시 폴백은 동작하나, 폴백 DB 쿼리가 빈번해지면 DB에 순간적인 부하 집중 가능 — 모니터링 알림 설정 권장
- **스케줄러 중복 실행**: 다중 인스턴스 배포 시 각 인스턴스에서 스케줄러가 동시에 실행될 수 있음 — atomic rename으로 ZSet 교체의 원자성은 보장되나, 불필요한 DB 조회가 중복 발생할 수 있어 향후 분산 락 적용을 검토해야 함

---

## 관련 문서

- `ranking:auction:popular` — Redis ZSet 키
- `ranking:free:popular` — FreePost 선행 패턴 (참고)
- `FreePostRankingService.java` — 패턴 원본 구현체
- `FreePostRankingScheduler.java` — 스케줄러 원본 구현체
- `docs/policy/API_SPEC.md` — API 명세 (`GET /api/v1/auctions/popular?size=10`)
