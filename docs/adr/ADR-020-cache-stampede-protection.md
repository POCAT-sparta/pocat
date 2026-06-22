# ADR-020: 카드 평균가 캐시 스탬피드 방지 — Redis SETNX 분산락 적용

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-22 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

`CardQueryService.getAveragePrice()`는 카드 평균 거래가를 Redis에 1시간 TTL로 캐싱하는 cache-aside 패턴을 사용한다.

```java
String cached = redisTemplate.opsForValue().get(cacheKey);
if (cached != null) return deserialize(cached);

CardAveragePriceResponse response = orderQueryService.getAveragePriceByCard(cardId); // DB 조회
redisTemplate.opsForValue().set(cacheKey, serialize(response), 1, TimeUnit.HOURS);
return response;
```

캐시 TTL이 만료되는 순간 다수의 요청이 동시에 `cached == null` 조건을 통과하면 **모든 요청이 DB를 직접 조회**한다. 이를 캐시 스탬피드(Cache Stampede)라 하며, DB 커넥션 풀 고갈 및 응답 지연 급증을 유발할 수 있다.

k6 부하 테스트(`03-avg-price-cache-stampede.js`)로 재현한 결과, 50 VU 동시 출발 시 캐시 콜드 구간 avg **99.85ms**, max **113.6ms**를 기록했다. 캐시 히트 안정 구간(avg **3.19ms**)과 **30배** 차이가 발생했다.

---

## 결정 (Decision)

`CardQueryService.getAveragePrice()`에 **Redis SETNX 분산락**을 적용한다.

### 동작 흐름

```text
요청 N개 동시 도착
    ↓
캐시 조회 → 히트 시 즉시 반환
    ↓ (캐시 미스)
SETNX lock:avgprice:{cardId} (TTL 3s)
    ├─ 락 획득 (1개) → DB 조회 → 캐시 set → 락 해제 → 반환
    └─ 락 획득 실패 (N-1개) → 100ms 간격으로 최대 5회 캐시 재조회
           ├─ 캐시 히트 → 반환
           └─ 5회 초과 → DB 직접 조회 (폴백, warn 로그)
```

### 상수 설정

| 상수 | 값 | 근거 |
|------|----|------|
| `LOCK_TTL_SECONDS` | 3s | DB 조회 최대 소요 시간의 약 10배 여유 |
| `LOCK_RETRY_COUNT` | 5회 | 최대 대기 500ms — p(99) DB 응답 시간 내 |
| `LOCK_RETRY_INTERVAL_MS` | 100ms | 불필요한 폴링 최소화 |

### 적용 범위

평균가 조회(`card:avgprice:{cardId}`)만 적용한다. `@Cacheable` 기반 다른 캐시(USER_PROFILE, POST_FREE_DETAIL 등)는 현재 트래픽 수준에서 스탬피드 위험이 낮아 이번 결정에서 제외한다.

---

## 고려한 대안 (Alternatives Considered)

### @Cacheable(sync = true)

- **거절 이유**: Spring의 `sync=true`는 JVM 인스턴스 내 단일 로컬 락이다. 현재 프로젝트는 ECS 멀티 인스턴스 배포 환경이므로 인스턴스 간 동시성을 제어하지 못한다.

### Redisson RLock

- **거절 이유**: `AuctionBidCommandService` 등에서 이미 사용 중이나, 입찰처럼 "락 획득 실패 = 즉시 오류" 가 아닌 평균가 조회는 잠시 대기 후 캐시에서 읽는 재시도 패턴이 더 적합하다. SETNX + 재시도로 충분하며 추가 의존성이 없다.

### Probabilistic Early Expiration (PER)

- **거절 이유**: 구현 복잡도가 높고, TTL 1시간 캐시에서 조기 갱신 타이밍 산정이 어렵다. 현재 요구사항에서 오버엔지니어링이다.

---

## 결과 (Consequences)

### 긍정적 영향

- 캐시 만료 시 DB 중복 조회 제거 — 단 1개 요청만 DB 호출
- 나머지 요청은 캐시 재조회로 처리되어 DB 커넥션 풀 안정성 확보
- k6 검증: 50 VU 동시 스탬피드 상황에서 에러율 0%, 모든 임계값 통과

### 부정적 영향 / 트레이드오프

- 캐시 콜드 최초 요청 시 락 대기 최대 500ms 지연 발생 (정상 동작)
- 5회 재시도 초과 시 DB 직접 조회 폴백 — 극단적 부하에서 DB 요청이 일부 증가할 수 있음
- Redis 장애 시 락 획득 실패로 폴백 경로를 타며 정상 서비스 유지 (Fail-open)

### k6 테스트 결과 요약

| 구간 | avg | p(95) | max |
|------|-----|-------|-----|
| stampede (캐시 콜드, 50 VU 동시) | 99.85ms | 113.53ms | 113.6ms |
| steady (캐시 워밍 후) | 3.19ms | 5.86ms | 20.9ms |

---

## 관련 코드

- `CardQueryService.getAveragePrice()` — SETNX 분산락 적용
- `k6/scenarios/03-avg-price-cache-stampede.js` — 스탬피드 재현 및 검증 시나리오 개선
