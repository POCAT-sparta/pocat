# Test Report — Issue #133

| 항목 | 내용 |
|------|------|
| **Date** | 2026-05-29 |
| **Issue** | #133 — 플랫폼 전역 동시성 제어 및 Rate Limiting 구현 |
| **Branch** | `feat/concurrency-ratelimit/#133` |
| **작성자** | DOCS Agent (Phase 4.5) |

---

## 1. 신규·변경 테스트 파일

| 파일 | 변경 유형 | 설명 |
|------|-----------|------|
| `LikeCommandServiceTest` | 전면 교체 | 리플렉션 RED → 동작 테스트 6개 (rate limit·lock·toggle·DataIntegrity) |
| `RedisRateLimiterTest` | 전면 교체 | 리플렉션 RED → 동작 테스트 4개 (allow·block·null·Redis 장애) |
| `AuthServiceTest` | 기존 유지 | `signup` 중복 이메일 예외 처리 검증 |
| `AuthControllerTest` | 수정 | LENIENT→per-stub, 429 Rate Limit 테스트 추가 |
| `AuctionControllerTest` | 수정 | 검색 엔드포인트 429 Rate Limit 테스트 추가 |
| `FailureServiceTest` | 버그픽스 | `OutboxEventWriter @Mock` 누락 → NPE 수정 |

---

## 2. RED → GREEN 결과

### 초기 구현 (#133 Phase 3a/3b)

| 단계 | 테스트 수 | 상태 |
|------|-----------|------|
| RED (구현 전) | 10 | FAIL |
| GREEN (구현 후) | 10 | PASS |

### PR 리뷰 반영 (Phase 3b 2차)

| 단계 | 테스트 수 | 상태 |
|------|-----------|------|
| 리뷰 반영 후 대상 | 44 | PASS |
| 버그 발견 (getMostSpecificCause→getCause) | 1 | 즉시 수정 |
| 최종 | 44 | PASS |

### 통과된 테스트 목록 (요약)

- `LikeCommandServiceTest` — rate limit 초과·락 실패·최초 좋아요·좋아요 취소·unique 제약 위반·비unique 전파 (6개)
- `RedisRateLimiterTest` — 허용·차단·null 반환·Redis 장애 fail-open (4개)
- `AuthControllerTest` — signup/login/reissue/logout 기존 + 429 Rate Limit (9개)
- `AuctionControllerTest` — 기존 + 429 Rate Limit (기존 수 + 1개)
- `FailureServiceTest` — markFailed 성공·실패 (2개, OutboxEventWriter NPE 수정)

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
| `RedisRateLimiter` | allow·block·null·장애(fail-open) 전체 경로 |
| `LikeCommandService` | rate limit·락 실패·toggle 정상·DataIntegrity 분기 |
| `AuthService#signup` | 중복 이메일 예외 분기 |
| `AuthController` | signup/reissue Rate Limit 차단(429) 경로 |
| `AuctionController` | 검색 Rate Limit 차단(429) 경로 |
| `FailureService#markFailed` | OutboxEventWriter 포함 성공·실패 전체 경로 |

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
