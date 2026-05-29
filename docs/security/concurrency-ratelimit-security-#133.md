# Security Audit — Issue #133

| 항목 | 내용 |
|------|------|
| **Date** | 2026-05-29 |
| **Issue** | #133 — 플랫폼 전역 동시성 제어 및 Rate Limiting 구현 |
| **Branch** | `feat/concurrency-ratelimit/#133` |
| **작성자** | DOCS Agent (Phase 4.5) |

---

## 1. 보안 점검 체크리스트 (7항목)

| # | 점검 항목 | 결과 | 비고 |
|---|-----------|------|------|
| 1 | **AuthN/AuthZ** — Rate Limit 및 Lock 적용 대상이 인증된 사용자로 한정되는가 | PASS | `userId` 기반 키 사용, 비인가 접근 불가 |
| 2 | **입력값 검증** — Rate Limit 키 생성 시 사용자 입력값이 그대로 노출되지 않는가 | PASS | `rate:user:like:{userId}` 형태로 내부 ID만 사용 |
| 3 | **SQL Injection** — 신규 쿼리에 파라미터 바인딩이 적용되는가 | PASS | JPA/QueryDSL 파라미터 바인딩 일관 적용 |
| 4 | **민감 데이터 노출** — Rate Limit 초과 응답에 내부 구조(Redis 키, 한도값)가 노출되지 않는가 | PASS | 에러 응답은 `ErrorCode` 상수만 반환, 내부 구조 미노출 |
| 5 | **분산 락 leaseTime** — leaseTime 미설정으로 인한 락 영구 점유 위험이 없는가 | PASS | `tryLock(waitTime=0, leaseTime=3s, SECONDS)` 명시적 설정 |
| 6 | **멱등성 키 충돌** — 서로 다른 기능 간 Redis 키 네임스페이스가 충돌하지 않는가 | PASS | `lock:like:{userId}:{cardId}` / `rate:user:like:{userId}` 분리 |
| 7 | **CVE 의존성** — 이번 PR에서 추가된 라이브러리에 알려진 CVE가 없는가 | PASS | 신규 외부 의존성 없음 (Redisson, Spring Data Redis 기존 버전 유지) |

---

## 2. 이번 PR에서 수정된 보안 취약점

### [FIXED] RedisRateLimiter — Redis 장애 시 500 전파 (fail-open 미적용)

| 항목 | 내용 |
|------|------|
| **심각도** | MEDIUM |
| **위치** | `RedisRateLimiter#isAllowed()` |
| **문제** | Redis 연결 실패 시 예외가 상위로 전파되어 HTTP 500 반환 → 서비스 전체 장애로 확산 가능 |
| **수정** | `try-catch`로 Redis 예외 포착 후 로그 기록, `true` 반환(fail-open) — Rate Limit 기능은 일시 비활성화되나 서비스 가용성 유지 |
| **trade-off** | Redis 장애 중 Rate Limit 우회 가능 → 모니터링 알럿으로 보완 |

```java
// Before
public boolean isAllowed(String key, int limit, Duration window) {
    Long count = redisTemplate.opsForValue().increment(key);
    // Redis 예외 시 unchecked exception 전파
    ...
}

// After
public boolean isAllowed(String key, int limit, Duration window) {
    try {
        Long count = redisTemplate.opsForValue().increment(key);
        ...
    } catch (Exception e) {
        log.warn("[RateLimiter] Redis 장애 발생 — fail-open 처리: {}", e.getMessage());
        return true;
    }
}
```

---

### [FIXED] LikeCommandService — Rate Limit 미적용 (Lock만 존재)

| 항목 | 내용 |
|------|------|
| **심각도** | MEDIUM |
| **위치** | `LikeCommandService#like()` |
| **문제** | 분산 락만 적용되어 순차 처리는 보장되나, 동일 사용자의 반복 요청(DoS-like)을 제한하지 못함 |
| **수정** | `rate:user:like:{userId}` 키로 분당 10회 Rate Limit 추가 — Lock 진입 전 선제 차단 |

---

## 3. 이번 PR 범위 외 — 별도 이슈로 이관된 사항

### [DEFERRED] X-Forwarded-For 헤더 스푸핑 위험

| 항목 | 내용 |
|------|------|
| **심각도** | LOW~MEDIUM (환경 의존) |
| **위치** | `HttpRequestUtils#getClientIp()` |
| **문제** | `X-Forwarded-For` 헤더를 신뢰하여 IP 기반 Rate Limit 우회 가능 |
| **현황** | #133 이전부터 존재하는 사전 취약점 |
| **조치 계획** | 리버스 프록시(Nginx/ALB) 레벨에서 헤더 주입 차단 설정 검토 — 별도 이슈 등록 필요 |

---

### [DEFERRED] UpdateCardRequest 입력값 검증 누락

| 항목 | 내용 |
|------|------|
| **심각도** | LOW |
| **위치** | `UpdateCardRequest` DTO |
| **문제** | `@NotNull` / `@NotBlank` 어노테이션 미적용 필드 존재 |
| **현황** | #133 이전부터 존재하는 사전 취약점, #133 범위 외 |
| **조치 계획** | 별도 유효성 검증 강화 이슈로 추적 |

---

## 4. 알려진 설계 한계 (Accepted Risk)

### 고정 윈도우(Fixed-Window) Rate Limiting의 버스트 허용 문제

| 항목 | 내용 |
|------|------|
| **현상** | 윈도우 경계(예: 59초~1초 사이)에서 최대 2배 요청 허용 가능 |
| **예시** | 분당 10회 제한 시, 00:59에 10회 + 01:00에 10회 = 20회 순간 허용 |
| **결정** | 현재 트래픽 규모에서 허용 가능한 수준으로 판단 — Sliding Window 전환은 추후 부하 증가 시 재검토 |
| **근거** | 슬라이딩 윈도우 대비 Redis 연산 단순 (INCR + EXPIRE), 운영 부담 최소화 |

---

## 5. 관련 문서

- ADR: `docs/adr/ADR-007-concurrency-ratelimit-#133.md`
- API Spec: `docs/policy/API_SPEC.md`
- Test Report: `docs/test/test-report-#133.md`
