# 보안 검토 결과 — 배치 스케줄러 이전 (#171)

**날짜**: 2026-06-02
**이슈**: #171
**검토 범위**: pocat-batch 신규 구현 + POCAT main Internal API

## 수정 완료 항목

### CRITICAL

| 파일 | 문제 | 수정 |
|---|---|---|
| `application.yaml` | `POCAT_INTERNAL_TOKEN` 기본값 `dev-token` 하드코딩 | 기본값 제거, 환경변수 필수화 |
| `AuctionActivationTasklet` | `@Transactional private` 메서드 (Spring AOP 우회) | `AuctionBatchService` 별도 Bean 추출, `@Transactional public` |
| `AuctionExpirationTasklet` | 동일 | 동일 |
| `OutboxReaperTasklet` | `findAll()` OOM 가능 | `findByStatusAndProcessedAtBefore()` DB 레벨 필터링 |
| `OutboxReaperTasklet` | `changeStatusToProcessing()` — PENDING 복구 안 됨 | `resetToPending()` 추가 및 사용 |

### HIGH

| 파일 | 문제 | 수정 |
|---|---|---|
| `InternalAuctionController` | `String.equals()` timing attack 취약 | `MessageDigest.isEqual()` 사용 |
| `InternalRefundController` | 동일 | 동일 |
| `InternalAuctionController` | 모든 예외 삼킴 → 200 OK | `IllegalStateException`/`IllegalArgumentException` → 200, `Exception` → 500 |
| `InternalRefundController` | 동일 | 동일 |
| `AuctionActivationTasklet` | `tryLock()` leaseTime 없음 → watchdog 무한 갱신 | `tryLock(0, 30, TimeUnit.SECONDS)` |
| `AuctionExpirationTasklet` | 동일 | 동일 |

## 후속 조치 권장 (운영 전)

1. **IP 레벨 접근 제어**: `/internal/**` 엔드포인트에 배치 서버 IP 대역만 허용 (`SecurityConfig` 또는 네트워크 레이어)
2. **Idempotency-Key 수신 측 검증**: `InternalAuctionController` / `InternalRefundController`에서 `Idempotency-Key` 헤더를 Redis SETNX로 실제 검증하는 로직 추가 (현재 헤더 수신만, 검증 없음)
3. **cron 재확인**: `BatchScheduler.runAuctionExpiration` cron `"0 5-30 19 * * *"` — 19:05~19:30 매분(26회) 실행이 의도인지, 아니면 특정 시각 1회가 의도인지 재확인 필요

## 체크리스트 최종

| 항목 | 결과 |
|------|------|
| AuthN/AuthZ | HIGH 수정 완료 |
| 입력값 검증 | MEDIUM — `@Positive` 추가 후속 이슈 권장 |
| SQL Injection | PASS |
| 민감 데이터 노출 | PASS |
| 분산 락 leaseTime | HIGH 수정 완료 |
| 멱등성 키 검증 | LOW — 후속 이슈 권장 |
| 의존성 CVE | PASS |
