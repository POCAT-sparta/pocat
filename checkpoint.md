# Checkpoint: feat/concurrency-ratelimit/#133

phase: 2.5
branch: feat/concurrency-ratelimit/#133
issue: 133

## Tasks
### Phase 3a — TEST
- [ ] T1: LikeCommandService 동시성 테스트
- [ ] T2: RedisRateLimiter 단위 테스트
- [ ] T3: AuthService.signup DataIntegrityViolationException 테스트

### Phase 3b — BACKEND
- [ ] B1: ErrorCode 신규 코드 추가
- [ ] B2: V4 Flyway 마이그레이션
- [ ] B3: RedisRateLimiter.java 신규
- [ ] B4: RateLimitProperties.java 신규
- [ ] B5: Like.java @Index 추가
- [ ] B6: LikeCommandService Redisson lock
- [ ] B7: AuthService signup exception catch
- [ ] B8: AuthController IP rate limit
- [ ] B9: FreePost/TradePost/Comment User rate limit
- [ ] B10: PaymentController webhook IP whitelist
- [ ] B11: AuctionController/QueryService search rate limit
- [ ] B12: AdminCardController @PreAuthorize
- [ ] B13: GlobalExceptionHandler RequestNotPermitted
- [ ] B14: application.yaml 설정 추가
