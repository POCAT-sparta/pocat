# Test Report — Issue #133

| 항목 | 내용 |
|------|------|
| **Date** | 2026-05-29 |
| **Issue** | #133 — 플랫폼 전역 동시성 제어 및 Rate Limiting 구현 |
| **Branch** | `feat/concurrency-ratelimit/#133` |
| **작성자** | DOCS Agent (Phase 4.5) |

---

## 1. 신규 테스트 파일

| 파일 | 위치 | 설명 |
|------|------|------|
| `LikeCommandServiceTest` | `src/test/java/...` | 분산 락(Redisson) 획득·실패 시나리오, Rate Limit 통합 검증 |
| `RedisRateLimiterTest` | `src/test/java/...` | 고정 윈도우 Rate Limiting 정상·초과·Redis 장애(fail-open) 시나리오 |
| `AuthServiceTest` | `src/test/java/...` | `signup` 중복 이메일 예외 처리 RED 테스트 추가 |

---

## 2. RED → GREEN 결과

| 단계 | 테스트 수 | 상태 |
|------|-----------|------|
| RED (구현 전) | 10 | FAIL |
| GREEN (구현 후) | 10 | PASS |

### 통과된 테스트 목록 (요약)

- `RedisRateLimiterTest` — 허용 범위 내 요청 통과, 한도 초과 시 false 반환, Redis 장애 시 fail-open(true 반환) 검증
- `LikeCommandServiceTest` — 락 정상 획득 후 좋아요 처리, 락 타임아웃 시 예외 전파, Rate Limit 초과 시 예외 전파
- `AuthServiceTest` (signup) — 중복 이메일 가입 시 `DuplicateEmailException` 발생 검증

---

## 3. Regression 확인

### 기존 실패 (브랜치 분기 이전부터 존재, #133 범위 외)

총 **34건** 사전 존재 실패 — #133 커밋으로 인한 신규 실패 없음.

#### 3-1. 인프라 의존 테스트 (Redis/Spring Context 필요)

| 테스트 클래스 | 실패 원인 |
|--------------|-----------|
| `CacheConfigTest` | 실행 중인 Redis 인스턴스 필요 |
| `CachePerformanceTest` | 실행 중인 Redis 인스턴스 필요 |
| `RealRedisTimingTest` | 실행 중인 Redis 인스턴스 필요 |

> 로컬 CI 환경에서 Redis가 기동되지 않은 상태로 실행되므로 실패. 통합 테스트 환경(Docker Compose)에서는 정상 통과.

#### 3-2. 사전 존재 Mock 누락 테스트

| 테스트 클래스 | 실패 원인 |
|--------------|-----------|
| `FreePostCommandServiceTest` (delete) | 기존 Mock 설정 누락 |
| `FreePostCommandServiceTest` (update) | 기존 Mock 설정 누락 |
| `AuctionQueryServiceTest` | 기존 Mock 설정 누락 |
| `SettlementCommandServiceTest` | 기존 Mock 설정 누락 |
| 기타 (~26건) | 동일 — 브랜치 분기 이전부터 존재 |

#### 3-3. 결론

- **#133 범위 내 신규 실패: 0건**
- 기존 34건 실패는 별도 이슈로 추적 예정 (인프라 환경 구성 및 Mock 보완)

---

## 4. 테스트 커버리지 기여

| 대상 클래스 | 추가 커버리지 |
|------------|--------------|
| `RedisRateLimiter` | 핵심 경로 100% (정상·초과·장애) |
| `LikeCommandService` | 락 획득·실패·Rate Limit 초과 경로 |
| `AuthService#signup` | 중복 이메일 예외 분기 |

---

## 5. 실행 명령

```bash
# 단위 테스트만 실행 (인프라 불필요)
./gradlew test --tests "com.rocketcrew.pocat.domain.like.*"
./gradlew test --tests "com.rocketcrew.pocat.global.ratelimit.*"
./gradlew test --tests "com.rocketcrew.pocat.domain.auth.*"

# 전체 테스트 (Redis 기동 필요)
docker-compose up -d redis
./gradlew test
```
