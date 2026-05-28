# ADR-006: 테스트 커버리지 확장 및 캐시 실측 스프린트 (#127)

**Status:** Implemented  
**Date:** 2026-05-28  
**Issue:** #127

---

## Context

AI 인프라 결함 수정 스프린트(#116) 이후 다음 세 가지 공백이 확인되었다.

1. **테스트 공백** — notification / chat / tradepost 모듈에 단위 테스트가 전무하다. 해당 모듈은 비즈니스 로직을 포함하고 있음에도 검증 코드가 없어 회귀 위험이 존재한다.
2. **AI 장애 격리 검증 부재** — #116에서 Resilience4j CircuitBreaker·RateLimiter를 도입했으나, fallback 동작 및 ServiceException 형태에 대한 자동화 검증이 없다. 어노테이션 존재 여부조차 테스트로 확인된 바 없다.
3. **ADR-003 캐시 수치 미기재** — ADR-003(캐싱 전략)에서 캐시 적중률과 DB 호출 감소 효과를 정성적으로만 기술했다. 실측 근거 없이 의사결정이 이루어진 상태이며, 수치 기반의 근거가 필요하다.

이 세 가지 공백을 하나의 스프린트(#127)에서 해소한다.

---

## Decision

### 1. 테스트 전략

**대상 모듈:** notification, chat, tradepost

- **단위 테스트** — `@ExtendWith(MockitoExtension.class)` 기반으로 작성한다. Spring 컨텍스트를 로드하지 않아 빌드 속도에 영향을 최소화한다.
- **컨트롤러 슬라이스 테스트** — `MockMvc.standaloneSetup(controller)` 방식을 채택한다. `@WebMvcTest`는 사용하지 않으며, auction / order / payment 모듈의 기존 테스트 설정에 일절 접촉하지 않는다.
- **기존 테스트 격리** — 신규 테스트는 독립 패키지에 배치하여 기존 통합 테스트 슬라이스와 충돌하지 않도록 한다.

### 2. AI 장애 격리 검증

#116에서 도입한 Resilience4j 어노테이션과 fallback 로직을 다음 방식으로 검증한다.

- **어노테이션 리플렉션 검증** — `private` 접근 제어자로 선언된 fallback 메서드는 직접 호출이 불가능하므로, Java Reflection API로 메서드 존재 여부 및 `@CircuitBreaker` / `@RateLimiter` 어노테이션 부착 여부를 확인한다.
- **ServiceException shape 검증** — fallback 경로에서 반환되는 예외가 `ServiceException` 타입이며, 정해진 에러 코드(ErrorCode)와 HTTP 상태를 포함하는지 단언한다.
- **외부 의존성 배제** — Gemini API, Redis, Kafka 등 외부 인프라 없이 Mockito stub만으로 장애 상황을 시뮬레이션한다.

### 3. 캐시 실측

ADR-003에서 정성적으로만 기술한 캐시 효과를 수치로 확인한다.

| 항목 | 결정 |
|------|------|
| 테스트 범위 | `@SpringBootTest` (캐시 AOP가 실제로 동작해야 하므로 전체 컨텍스트 필요) |
| Redis 대체 | `MockRedisTestConfig` — `ConcurrentMapCacheManager` 인메모리 구현으로 교체. Docker 불필요 |
| 측정 방식 | Repository / DAO를 Mockito `@SpyBean`으로 감싸고 `verify(spy, times(N)).method()` 로 DB 호출 횟수를 단언 |
| 측정 시나리오 | ① 캐시 COLD: 첫 조회 시 DB 1회 호출 확인 ② 캐시 HOT: 동일 키 재조회 시 DB 0회 호출 확인 ③ 캐시 EVICT: 데이터 변경 후 캐시 무효화 + 재조회 시 DB 1회 호출 확인 |
| 결과 보고 | 측정 수치를 ADR-003 `Consequences` 섹션에 보완 기재 |

---

## Consequences

**긍정적:**
- notification / chat / tradepost 모듈의 회귀 안전망 확보
- AI 장애 격리 어노테이션이 배포 후에도 제거되지 않음을 자동으로 보장
- ADR-003 캐시 전략에 실측 수치 근거 확보 — 향후 캐시 TTL 튜닝 및 용량 계획의 기준점 마련
- `ConcurrentMapCacheManager` 활용으로 Docker 환경 없이도 CI에서 캐시 테스트 실행 가능

**주의사항:**
- `@SpringBootTest` 사용으로 캐시 실측 테스트는 단위 테스트 대비 빌드 시간이 증가한다. 슬라이스 테스트와 분리된 Maven/Gradle 태그(tag)로 관리하여 로컬 빠른 피드백 루프와 CI 전체 검증을 구분하는 것을 권장한다.
- `standaloneSetup` 방식은 Spring Security 필터 체인을 포함하지 않으므로, 인증 관련 동작 검증이 필요한 경우 별도 슬라이스 전략을 재검토해야 한다.
- 인메모리 캐시(`ConcurrentMapCacheManager`)는 TTL을 지원하지 않아 만료 관련 동작은 테스트 범위에서 제외된다. TTL 검증이 필요한 경우 Testcontainers + 실제 Redis를 고려한다.

---

## 결과 요약

| 구분 | 내용 |
|------|------|
| 신규 테스트 파일 | **10개** (notification 3, chat 3, tradepost 3 도메인 단위+컨트롤러 / CachePerformanceTest 1) |
| 수정 테스트 파일 | **2개** (AI 분석·어시스턴트 서비스 — CircuitBreaker/RateLimiter 어노테이션 검증 및 timeout AI_SERVICE_UNAVAILABLE 시나리오 추가) |
| 부가 수정 | `test/resources/application.yaml` — Flyway H2 충돌 비활성화, OpenAI dummy key, PortOne 바인딩 설정 추가, Elasticsearch URI 설정 추가 |
| Pre-existing bug fix | `PaymentWebhookService.markFailed()` — `String reason` 인자 누락 컴파일 에러 수정 (PR #122 머지 후 잔존) |
| 전체 신규 테스트 | **GREEN** — 회귀 없음 확인 |
