# ADR-003: 애플리케이션 레이어 캐싱 전략 도입 (Redis Cache + Spring Cache Abstraction)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-23 |
| **상태** | Implemented |
| **결정자** | 개발팀 전체 |
| **최종 업데이트** | 2026-05-23 (Phase 4.5 — 구현 반영) |

---

## 맥락 (Context)

팀 회의에서 캐싱 대상 9개를 확정하였다. 기존 인프라에서 Redis는 이미 운영 중이나, Spring `@Cacheable` 어노테이션은 미사용 상태이며 `RedisCacheManager` Bean도 등록되어 있지 않다.

주요 성능 병목 지점:

- `getNicknamesByUserIds()`: 게시판 목록 페이지를 조회할 때마다 반복적으로 DB 배치 조회가 발생
- `getUserById()`: 마이페이지·게시글 상세 진입 시 매 요청마다 DB를 조회

운영 중인 Redis를 캐시 레이어로 활용하여 반복 조회로 인한 DB 부하를 줄이고, 장애 상황에서도 서비스 연속성을 보장하는 전략이 필요하다.

---

## 결정 (Decision)

### 1. Spring Cache Abstraction + RedisCacheManager 도입

**결정**: `@Cacheable` / `@CacheEvict` 어노테이션과 `RedisCacheManager` Bean을 신규 등록하여 Spring Cache Abstraction을 전면 도입한다.

- Spring 표준 캐시 추상화를 사용함으로써 캐시 구현체 교체 시 비즈니스 코드 변경을 최소화한다.
- `RedisCacheManager`를 Bean으로 등록하고, 캐시 네임스페이스별 TTL을 개별 설정한다.
- `sync=true` 옵션을 적용하여 동일 키에 대한 동시 캐시 미스(Cache Stampede)를 방지한다.

### 2. C-02 닉네임 배치 조회: 수동 Cache-Aside 패턴 (UserNicknameCacheService)

**결정**: `getNicknamesByUserIds()`는 `@Cacheable` 미지원(컬렉션 키) 구조이므로, `UserNicknameCacheService`를 별도로 구현하여 수동 Cache-Aside 패턴을 적용한다.

**대안 검토**

| 방식 | 설명 | 문제점 |
|------|------|--------|
| **수동 Cache-Aside** (채택) | 캐시 히트 키와 미스 키를 분리하여 미스 분에 대해서만 DB 배치 조회 후 캐시 적재 | — |
| `@Cacheable` 직접 적용 | 메서드 파라미터(컬렉션)를 단일 캐시 키로 표현 | 파라미터 조합이 요청마다 달라 사실상 캐시 재사용 불가 |

- 미스된 ID에 대해서만 DB 배치 조회를 수행하고, 결과를 키별로 분산 저장하여 재사용성을 높인다.
- 네임스페이스: `user:nickname::{userId}`, TTL 60분

**구현 확정 사항 (Phase 4.5 반영)**

- `StringRedisTemplate`으로 직접 Redis에 저장하며, `RedisCacheManager` 관할 캐시 네임스페이스에서 `user:nickname`을 **제거**하였다.
  - 이유: `RedisCacheManager`가 동일 prefix를 관리할 경우 직렬화 방식 차이(String vs. JSON)로 키 충돌 및 역직렬화 오류가 발생할 수 있어 네임스페이스를 분리하였다.
- 키 형식: `user:nickname::{userId}` (수동 관리, TTL 60분, `set(key, value, ttl)` 원자적 저장으로 TTL 누락된 영구 키 생성을 방지)
- `multiGet` 호출부에 try-catch를 추가하여 Redis 장애 시 전체 ID 목록을 미스로 처리하고 DB 폴백을 보장한다.

### 3. C-04 종료 경매 입찰 이력: 서비스 메서드 분리 후 ENDED 상태만 캐싱

**결정**: 입찰 이력 조회 서비스 메서드를 경매 상태별로 분리하고, `ENDED` 상태 경매의 입찰 이력에만 `@Cacheable`을 적용한다.

- `ACTIVE` 상태 경매의 입찰 이력은 실시간성이 요구되므로 캐싱하지 않는다.
- `ENDED` 상태는 입찰 이력이 변경되지 않으므로 장기 TTL 캐싱이 적합하다.
- 네임스페이스: `auction:bid-history::{auctionId}`, TTL 24시간

**구현 확정 사항 (Phase 4.5 반영)**

- `@Cacheable`이 선언된 메서드를 같은 Bean 내부에서 self-call하면 Spring AOP 프록시를 우회하여 캐시가 동작하지 않는 문제가 발생하였다.
- 이를 해결하기 위해 `AuctionBidCacheService`를 **신규 Bean으로 분리**하였다.
  - 캐시 키: `auction:bid-history::{auctionId}:page:{page}:size:{size}` (Pageable 파라미터 포함으로 페이지별 독립 캐시 지원)
  - 기존 설계의 단순 `{auctionId}` 키에서 Pageable을 포함한 복합 키로 변경되었다.

### 4. C-09 HTTP 응답 캐싱: ShallowEtagHeaderFilter + CacheControlInterceptor

**결정**: `ShallowEtagHeaderFilter`와 `CacheControlInterceptor`를 조합하여 HTTP 레이어의 응답 캐싱(ETag)을 적용한다.

- `ShallowEtagHeaderFilter`가 응답 본문 해시를 기반으로 ETag를 생성하고, 동일한 ETag를 가진 재요청에는 `304 Not Modified`를 반환한다.
- `CacheControlInterceptor`를 통해 엔드포인트별 `Cache-Control` 헤더 정책을 관리한다.

**구현 확정 사항 (Phase 4.5 반영)**

- Phase 4 보안 검토 결과, `ShallowEtagHeaderFilter`의 URL 패턴을 `/api/v1/users/**`를 **제외**하도록 제한하였다.
  - 이유: 사용자 프로필 등 개인 정보 응답에 ETag가 적용될 경우, 응답 본문 해시가 클라이언트에 노출되어 미세 채널(side-channel) 정보 유출 위험이 있다.
  - 적용 범위를 공개 정적 리소스 및 목록 조회 API로 한정하여 보안 리스크를 제거하였다.

### 5. @CacheEvict: 트랜잭션 AFTER_COMMIT 보장

**결정**: 모든 `@CacheEvict`는 트랜잭션 커밋 완료 후 실행되도록 `TransactionSynchronizationManager`를 활용하여 AFTER_COMMIT을 보장한다.

- 트랜잭션 롤백 시 캐시가 먼저 무효화되어 DB와 캐시 불일치가 발생하는 문제를 방지한다.

### 6. 보안 강화: 관리자 캐시 무효화 API 접근 제어 (Phase 4 추가)

**결정**: Phase 4 보안 검토 결과, `AdminUserCommandService.toggleBidBlock()`에 `@PreAuthorize("hasRole('ADMIN')")` 어노테이션을 **추가**하였다.

- 입찰 차단 상태(`user:bid-blocked::{userId}`) 캐시를 무효화(`@CacheEvict`)하는 작업은 관리자 전용 권한이 요구된다.
- 기존 구현에서 서비스 레이어의 인가 검사가 누락되어 있었으며, 이를 메서드 수준 보안으로 보완하였다.

### 7. Redis 장애 내성: RedisCacheErrorHandler (DB 패스스루) 및 수동 캐시 폴백 강화

**결정**: `RedisCacheErrorHandler`를 구현하여 Redis 장애 시 캐시를 무시하고 DB를 직접 조회(패스스루)하도록 처리한다.

- Redis 장애가 서비스 장애로 전파되지 않도록 격리한다.
- 에러 발생 시 경고 로그를 기록하여 모니터링과 알림 설정이 가능하도록 한다.

**구현 확정 사항 (Phase 4.5 반영)**

- `RedisCacheErrorHandler`는 Spring Cache Abstraction 관할 캐시(`@Cacheable` / `@CacheEvict`)에만 적용된다.
- `UserNicknameCacheService`(수동 Cache-Aside)는 Abstraction 외부에 있으므로 `multiGet` 호출부에 독립적인 try-catch를 추가하여 Redis 장애 시 전체 ID를 미스로 처리하고 DB 배치 조회로 폴백한다.
- 두 계층의 장애 처리를 독립적으로 구성함으로써 수동·자동 캐시 경로 모두에서 DB 패스스루가 보장된다.

---

## 캐시 키 네임스페이스 및 TTL

| 캐시 ID | 키 패턴 | TTL | 관리 방식 | 비고 |
|---------|---------|-----|-----------|------|
| C-01 | `user:profile::{userId}` | 30분 | `RedisCacheManager` (`@Cacheable`) | — |
| C-02 | `user:nickname::{userId}` | 60분 | `StringRedisTemplate` (수동) | `RedisCacheManager` 네임스페이스에서 제외, TTL 원자적 저장 |
| C-04 | `auction:bid-history::{auctionId}:page:{page}:size:{size}` | 24시간 | `RedisCacheManager` (`AuctionBidCacheService`) | ENDED 상태만 캐싱; Pageable 포함 복합 키로 변경 |
| C-05 | `post:free:detail::{postId}` | 10분 | `RedisCacheManager` (`@Cacheable`) | — |
| C-06 | `post:trade:detail::{postId}` | 10분 | `RedisCacheManager` (`@Cacheable`) | — |
| C-07 | `user:bid-blocked::{userId}` | 60분 | `RedisCacheManager` (`@Cacheable`) | 무효화 API에 `@PreAuthorize("hasRole('ADMIN')")` 적용 |
| C-08 | `post:comments::{postId}:page:{N}` | 10분 | `RedisCacheManager` (`@Cacheable`) | 반환 타입 `CachedPage<CommentTreeResponse>` 래퍼 사용 |

> **C-08 반환 타입 변경**: Jackson이 `Page<T>` 인터페이스를 역직렬화할 때 구체 타입 정보가 없어 오류가 발생하였다.
> 이를 해결하기 위해 `CachedPage<CommentTreeResponse>` 직렬화 래퍼 클래스를 도입하였다.
> (`CommentQueryService`의 반환 타입이 `Page<CommentTreeResponse>` → `CachedPage<CommentTreeResponse>`로 변경됨)

---

## 결과 (Consequences)

### 긍정적 영향

- **DB 부하 감소**: 반복적인 사용자 프로필·닉네임·게시글 상세 조회가 Redis 캐시로 처리되어 DB I/O가 감소한다.
- **닉네임 배치 조회 제거**: 게시판 목록 페이지마다 발생하던 반복 DB 배치 조회가 캐시 히트로 대체된다.
- **Redis 장애 내성 보장**: `RedisCacheErrorHandler`를 통해 Redis 장애 시에도 DB 패스스루로 서비스가 중단 없이 유지된다.
- **Cache Stampede 방지**: `sync=true` 적용으로 동시 캐시 미스로 인한 DB 과부하를 방지한다.
- **트랜잭션 일관성**: AFTER_COMMIT 보장으로 캐시와 DB 간 불일치 위험을 제거한다.

### 부정적 영향 / 주의사항

- **캐시 정합성 관리 복잡도 증가**: 데이터 변경 시 `@CacheEvict` 누락 또는 AFTER_COMMIT 미적용으로 인한 불일치가 발생할 수 있으므로 코드 리뷰 시 주의가 필요하다.
- **C-02 수동 구현 유지보수**: `UserNicknameCacheService`는 Spring Cache Abstraction 외부에 존재하므로 팀 내 별도 관리 기준이 필요하다. 특히 `RedisCacheManager`와 키 네임스페이스가 중복되지 않도록 주의해야 한다.
- **C-04 AOP self-call 제약**: `@Cacheable`이 선언된 메서드를 같은 Bean에서 self-call하면 AOP 프록시를 우회하므로, 캐시 적용이 필요한 메서드는 반드시 별도 Bean(`AuctionBidCacheService` 패턴)으로 분리해야 한다.
- **C-08 직렬화 래퍼 의존성**: `CachedPage` 래퍼 클래스를 변경하거나 삭제할 경우 기존 Redis에 저장된 캐시 데이터와 역직렬화 불일치가 발생한다. 스키마 변경 시 해당 캐시 네임스페이스를 flush해야 한다.
- **TTL 만료 시 일시적 DB 부하**: 다수의 캐시 키가 동시에 만료될 경우 DB 조회가 집중될 수 있으며, 캐시 네임스페이스별 TTL 분산으로 일부 완화한다.
- **Redis 메모리 사용량 증가**: 캐시 대상 확대로 Redis 메모리 사용량을 주기적으로 모니터링해야 한다.
- **ShallowEtagHeaderFilter 적용 범위 제한**: `/api/v1/users/**`를 제외한 범위에만 ETag를 적용하므로, 향후 신규 사용자 관련 API 추가 시 필터 URL 패턴을 함께 검토해야 한다.

---

## 관련 문서

- `UserNicknameCacheService.java` — 수동 Cache-Aside 구현체 (C-02); `StringRedisTemplate` 직접 사용, try-catch 폴백 포함
- `AuctionBidCacheService.java` — 입찰 이력 캐시 전담 Bean (C-04); self-call AOP 우회 문제 해결을 위해 분리
- `CachedPage.java` — `Page<T>` Jackson 역직렬화 래퍼 (C-08)
- `CacheConfig.java` — `RedisCacheManager` Bean 및 네임스페이스별 TTL 설정 (`user:nickname` 제외)
- `RedisCacheErrorHandler.java` — Redis 장애 시 DB 패스스루 핸들러 (Spring Cache Abstraction 관할)
- `CacheControlInterceptor.java` — HTTP 레이어 `Cache-Control` 헤더 관리 (C-09)
- `AdminUserCommandService.java` — `toggleBidBlock()` 메서드에 `@PreAuthorize("hasRole('ADMIN')")` 적용
- `docs/adr/ADR-002-auction-popular-ranking.md` — Redis ZSet 기반 랭킹 캐시 선행 결정

---

## 실측 검증 결과 (2026-05-28)

### 검증 방법
`CachePerformanceTest` (`@SpringBootTest` + `ConcurrentMapCacheManager`) — `verify(repository, times(N))` 기반 DB 호출 횟수 측정. 실제 Redis 연결 없이 Spring Cache Abstraction 동작을 결정론적으로 검증.

### Before / After 비교

| 캐시 ID | 대상 | Before (캐시 없음) | After (캐시 적용) | 근거 |
|---------|------|------------------|-----------------|------|
| C-01 | `user:profile` getUserById() | 동일 userId N회 호출 → DB N회 조회 | DB 1회 조회 → 이후 0회 (캐시 히트) | `verify(userRepository, times(1)).findById()` |
| C-06 | `post:trade:detail` getPost() | 상세 조회마다 DB 1회 | 캐시 히트 시 DB 0회 | `verify(tradePostRepository, times(0)).findById()` on 2nd call |
| C-06 | `post:trade:detail` updatePost() 후 | 캐시 Stale 가능 | `@CacheEvict` → 다음 조회 DB 재적재 1회 | `verify(tradePostRepository, times(1)).findById()` after evict |
| cardAnalysis | AI 분석 결과 | 동일 cardId 재분석마다 LLM 호출 | 캐시 히트 시 LLM 0회 | `verify(chatClient, never()).prompt()` |
| (인프라) | Redis 직접 응답 시간 | - | SET < 5ms, GET < 2ms (localhost Docker) | `RealRedisTimingTest` 실측 |

### 주의사항
- 응답 시간(ms) 수치는 `ConcurrentMapCacheManager`(in-memory) 기반 측정이므로 실제 Redis 네트워크 지연과 차이 있음
- DB 호출 횟수 감소 효과는 실제 Redis 환경과 동일하게 적용됨
- TTL 만료 동작은 `ConcurrentMapCacheManager`에서 미지원 — 운영 환경 Redis TTL 설정은 ADR-003 §결정 참조
- Redis 직접 응답 시간은 Docker localhost 기준 실측값 (SET ~1–3ms, GET ~0.5–1ms); 운영 환경(네트워크 홉 포함)에서는 더 높을 수 있음
