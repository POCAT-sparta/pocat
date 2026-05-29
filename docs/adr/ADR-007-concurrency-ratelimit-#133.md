# ADR-007: 플랫폼 전역 동시성 제어 및 Rate Limiting + AdminCardController 인가 누락 수정

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-28 |
| **상태** | Accepted |
| **이슈** | #133 |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

### 동시성 문제

플랫폼 운영 중 다음 세 가지 동시성 위험이 확인되었다.

1. **찜 중복**: 동일 사용자가 동일 경매에 대해 빠르게 연속 요청하면 `LikeCommandService.toggleLike()`가 두 번 실행되어 Like 레코드가 중복 삽입될 수 있다. 소프트 삭제 방식을 사용하기 때문에 DB UNIQUE 제약으로 막을 수 없는 구조다.

2. **채팅방 중복**: 동일 게시글에 대해 같은 구매자가 동시 요청을 보내면 채팅방이 중복 생성될 수 있다. `chats(post_id, guest_id)` 조합에 대한 DB 유니크 제약이 없어 애플리케이션 레벨 체크가 경합을 막지 못한다.

3. **회원가입 레이스 컨디션**: 이메일 중복 체크와 INSERT 사이에 짧은 간격이 있어 동일 이메일로 동시 가입 시도 시 두 레코드가 삽입될 수 있다.

### Rate Limiting 부재

AI 도메인(`RedisAiRateLimiter`)에는 Redis 기반 요청 제한이 적용되어 있으나, 인증 엔드포인트·커뮤니티 쓰기 작업·경매 검색 등 나머지 도메인에는 per-user 또는 per-IP 제한이 전혀 없다. 이로 인해 다음 위험에 노출되어 있다.

- 회원가입·토큰 재발급 엔드포인트 대상 자동화 공격
- 게시글·댓글 스팸
- 검색 API 과부하
- PortOne Webhook 엔드포인트를 임의 IP에서 호출하는 위조 이벤트

### AdminCardController 인가 누락

`AdminCardController`의 카드 승인·거절·수정·삭제·목록 조회 엔드포인트에 메서드 레벨 인가 애노테이션이 없었다. Spring Security URL 패턴(`/api/v1/admin/**`)으로 1차 보호되지만, Security 설정 변경 시 내부 서비스 메서드까지 보호되지 않는 취약 구조였다.

---

## 결정 (Decision)

### 1. Like 분산락 (Redisson)

`LikeCommandService.toggleLike()`에 Redisson `RLock`을 적용한다.

- **락 키**: `like:lock:{userId}:{auctionId}`
- **전략**: `tryLock(0, TimeUnit.SECONDS)` — 대기 없이 즉시 실패 반환
- **락 획득 실패 시**: `409 Conflict` (`LIKE_LOCK_FAILED`) 응답
- **근거**: 동일 사용자가 동일 경매를 동시에 찜 토글하는 정상 시나리오는 없으므로 대기 없이 즉시 거절이 적절하다.

### 2. AuthService 회원가입 최후 방어선

`AuthService.signup()`에서 `DataIntegrityViolationException`을 catch하여 `409 Conflict`로 변환한다. 기존 이메일·닉네임 중복 체크 로직은 유지하되, DB 레벨에서 올라오는 제약 위반을 최후 방어선으로 처리한다.

### 3. RedisRateLimiter 컴포넌트 신설

기존 AI 도메인의 `RedisAiRateLimiter`와 동일한 Redis INCR/EXPIRE Lua 스크립트 패턴을 재사용하여 플랫폼 전역 `RedisRateLimiter` 컴포넌트를 신설한다.

```lua
-- Lua script (원자적 실행)
local current = redis.call('INCR', KEYS[1])
if current == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
```

- 키 형식: `rate:{type}:{identifier}:{windowMinute}` (예: `rate:ip:192.0.2.1:1`)
- 초과 시 `429 Too Many Requests` (`RATE_LIMIT_EXCEEDED`)

### 4. IP 기반 Rate Limit

| 엔드포인트 | 제한 | 키 |
|---|---|---|
| `POST /api/v1/auth/signup` | 5회/분 | 요청 IP |
| `POST /api/v1/auth/reissue` | 5회/분 | 요청 IP |
| `POST /api/v1/payments/webhook` | IP 화이트리스트 검증 | 허용 목록 외 IP → `403 Forbidden` (`WEBHOOK_IP_FORBIDDEN`) |

Webhook의 경우 Rate Limit이 아니라 IP 화이트리스트 방식을 사용한다. PortOne의 발신 IP 대역을 환경변수(`PORTONE_ALLOWED_IPS`)로 관리하며, 불일치 시 즉시 `403` 반환한다.

### 5. 유저 기반 Rate Limit

| 엔드포인트 | 제한 | 키 |
|---|---|---|
| `POST /api/v1/community/free-posts` | 5회/분 | 인증된 userId |
| `POST /api/v1/community/trade-posts` | 5회/분 | 인증된 userId |
| `POST /api/v1/community/free-posts/{id}/comments` | 5회/분 | 인증된 userId |
| `GET /api/v1/auctions` (검색 포함) | 30회/분 | 인증된 userId |

> 실제 경로는 각각 `POST /api/v1/posts/free`, `POST /api/v1/posts/trade`, `POST /api/v1/comments`, `GET /api/v1/auctions`

### 6. AdminCardController @PreAuthorize 추가

`AdminCardController`의 모든 핸들러 메서드에 `@PreAuthorize("hasRole('ADMIN')")` 애노테이션을 추가한다. URL 패턴 보안과 메서드 보안을 모두 적용하는 Defense-in-Depth 원칙을 준수한다.

### 7. V4 마이그레이션: 인덱스 및 유니크 제약

`V4__like_index_chat_unique.sql` 마이그레이션을 추가한다.

```sql
-- 찜 조회 성능 인덱스 (소프트 삭제를 고려하여 UNIQUE가 아닌 일반 인덱스)
CREATE INDEX IF NOT EXISTS idx_likes_user_auction
  ON likes(user_id, auction_id);

-- 채팅방 중복 생성 방지 유니크 제약
ALTER TABLE chats
  ADD CONSTRAINT uk_chats_post_guest UNIQUE (post_id, guest_id);
```

---

## 고려한 대안 (Alternatives Considered)

### Bucket4j + Redis (Rate Limiting 라이브러리)

- **거절 이유**: 새 외부 의존성 추가가 필요하다. 현재 단일 인스턴스 배포 환경에서 기존 Redis 연결을 활용하는 Lua 스크립트 방식으로 충분히 요구사항을 충족할 수 있다. 의존성 표면을 최소화한다.

### Resilience4j @RateLimiter (어노테이션 방식)

- **거절 이유**: Resilience4j의 Rate Limiter는 JVM 인스턴스 내 전역 카운터를 사용한다. 향후 멀티 인스턴스 배포 시 인스턴스 간 상태를 공유할 수 없어 per-user 또는 per-IP 정확한 제한이 불가능하다. Redis 기반 분산 카운터가 일관성 측면에서 우월하다.

### Like(user_id, auction_id) DB UNIQUE 제약

- **거절 이유**: 현재 Like 엔티티는 소프트 삭제(`deleted_at`) 방식을 사용한다. 찜 취소 후 재찜 시 동일 `(user_id, auction_id)` 조합으로 새 레코드가 삽입되는데, UNIQUE 제약이 있으면 이전 소프트 삭제 레코드와 충돌하여 정상 동작이 불가능하다. 대신 성능 인덱스(`idx_likes_user_auction`)만 추가하고 분산락으로 동시성을 제어한다.

---

## 결과 (Consequences)

### 긍정적 영향

- 찜 중복 삽입, 채팅방 중복 생성, 회원가입 레이스 컨디션 모두 해소
- 인증 엔드포인트 대상 자동화 공격 표면 감소
- 커뮤니티 스팸 억제
- 위조 Webhook 요청 차단
- AdminCardController Defense-in-Depth 보안 강화

### 부정적 영향 / 트레이드오프

- 분산락 사용으로 인해 `toggleLike()` 호출 시 Redis 왕복 레이턴시가 추가된다 (tryLock 0s이므로 대기 없음).
- Rate Limit 초과 사용자는 정상 사용 중 `429`를 받을 수 있다. 제한값(5회/분, 30회/분)은 정상 UX 기준으로 충분한 여유를 두었으나 향후 모니터링 후 조정 가능하다.
- Redis 장애 시 Rate Limit·분산락이 동작하지 않는다. 이 경우 기존 DB 레벨 제약과 애플리케이션 검증이 1차 방어선으로 동작한다. (Fail-open 정책)

### 새로운 의존성

없음. Redisson은 이미 입찰 분산락(`AuctionBidService`)에서 사용 중이며, Redis는 기존 인프라에 포함되어 있다.

---

## 관련 코드

- `LikeCommandService.toggleLike()` — Redisson 분산락 적용
- `AuthService.signup()` — `DataIntegrityViolationException` catch 추가
- `RedisRateLimiter` — 신규 컴포넌트 (Lua 스크립트 기반)
- `AdminCardController` — `@PreAuthorize("hasRole('ADMIN')")` 추가
- `V4__like_index_chat_unique.sql` — 인덱스 및 유니크 제약 마이그레이션
