# 통합 테스트 결과 보고서

| 항목 | 내용 |
|------|------|
| **Date** | 2026-06-02 |
| **Branch** | `dev` |
| **대상** | 결제 · 자동결제 · 입찰 · 정산 · 환불 · Kafka consumer · confirmPayment · 즉시구매 · 환불재시도 |
| **환경** | H2 in-memory (MySQL MODE), Spring Boot Test, JUnit 5 |
| **외부 의존성 격리** | Redis · Redisson · PortOne · Kafka · Elasticsearch → @MockBean |

---

## 1. 테스트 파일 목록

| 파일 | 위치 | 테스트 수 |
|------|------|-----------|
| `PaymentConcurrencyIntegrationTest` | `domain/payment/service` | 3 |
| `AutoPaymentIdempotencyIntegrationTest` | `domain/payment/service` | 4 |
| `PaymentKafkaConsumerIdempotencyTest` | `domain/payment/service` | 2 |
| `ConfirmPaymentIdempotencyIntegrationTest` | `domain/payment/service` | 2 |
| `AuctionBidConcurrencyIntegrationTest` | `domain/bid/service` | 1 |
| `AuctionBuyoutConcurrencyIntegrationTest` | `domain/auction/service` | 1 |
| `SettlementIdempotencyIntegrationTest` | `domain/settlement/service` | 2 |
| `RefundConcurrencyIntegrationTest` | `domain/refund/service` | 1 |
| `RefundRetryIntegrationTest` | `domain/refund/service` | 4 |

---

## 2. 전체 결과

**총 20개 테스트 — 전부 PASS**

| # | 테스트명 | 클래스 | 소요 시간 | 결과 |
|---|---------|--------|-----------|------|
| 1 | 동시 요청 시 PENDING 여러 건 생성 — 중복 완료 차단은 confirmPayment에서 보장 | PaymentConcurrencyIntegrationTest | 0.096s | ✅ PASS |
| 2 | 정상: 단건 요청 → PENDING 1건 생성 | PaymentConcurrencyIntegrationTest | 0.378s | ✅ PASS |
| 3 | 실패: 결제 기한 초과 주문 → PAYMENT_WINDOW_EXPIRED | PaymentConcurrencyIntegrationTest | 0.383s | ✅ PASS |
| 4 | 동시 호출 시 BILLING_KEY 결제 1건만 생성되고 COMPLETED 상태로 완료됨 | AutoPaymentIdempotencyIntegrationTest | 0.180s | ✅ PASS |
| 5 | 이미 완료된 주문에 재호출 시 새 결제 생성 없이 기존 결제 반환 | AutoPaymentIdempotencyIntegrationTest | 0.053s | ✅ PASS |
| 6 | 순차 중복 호출 시 BILLING_KEY 결제 1건만 생성됨 | AutoPaymentIdempotencyIntegrationTest | 0.466s | ✅ PASS |
| 7 | 즉시구매(BUYOUT) 주문은 직접결제 불가 → PAYMENT_BUYOUT_DIRECT_NOT_ALLOWED | AutoPaymentIdempotencyIntegrationTest | 0.385s | ✅ PASS |
| 8 | 동시 입찰: Redisson 락으로 1건만 성공, 나머지는 즉시 실패 | AuctionBidConcurrencyIntegrationTest | 1.807s | ✅ PASS |
| 9 | 순차 중복 호출: 두 번 호출해도 정산 1건만 생성됨 (existsByOrderId 1차 방어) | SettlementIdempotencyIntegrationTest | 0.040s | ✅ PASS |
| 10 | 동시 중복 호출: 10개 스레드가 같은 orderUid로 요청해도 정산 1건만 생성됨 | SettlementIdempotencyIntegrationTest | 0.110s | ✅ PASS |
| 11 | 동시 환불 요청: Order 비관적 락으로 직렬화 → 환불 1건만 생성, 나머지는 REFUND_ALREADY_EXISTS | RefundConcurrencyIntegrationTest | 1.297s | ✅ PASS |
| 12 | 동일 order.created 이벤트 2회 소비 → 결제 1건, PortOne 1회 호출 | PaymentKafkaConsumerIdempotencyTest | — | ✅ PASS |
| 13 | order.created 아닌 이벤트는 autoPayment 미호출 후 ack | PaymentKafkaConsumerIdempotencyTest | — | ✅ PASS |
| 14 | 동시 10스레드 confirmPayment → COMPLETED 1건, 나머지는 isFinalized 조기 반환 | ConfirmPaymentIdempotencyIntegrationTest | — | ✅ PASS |
| 15 | 순차 2회 confirmPayment → COMPLETED 1건, PortOne 1회 호출 | ConfirmPaymentIdempotencyIntegrationTest | — | ✅ PASS |
| 16 | 동시 10스레드 즉시구매 → Redisson 락으로 1건만 성공, 나머지는 AUCTION_LOCK_FAILED | AuctionBuyoutConcurrencyIntegrationTest | — | ✅ PASS |
| 17 | 환불 재시도: PortOne 성공 → COMPLETED, Payment·Order REFUNDED | RefundRetryIntegrationTest | — | ✅ PASS |
| 18 | 환불 재시도: PortOne 실패 (retryCount < 5) → FAILED_RETRYABLE, retryCount 증가 | RefundRetryIntegrationTest | — | ✅ PASS |
| 19 | 환불 재시도: PortOne 실패 (retryCount = 5) → FAILED_FINAL | RefundRetryIntegrationTest | — | ✅ PASS |
| 20 | 환불 재시도: nextRetryAt 미도래 → 스킵, PortOne 미호출 | RefundRetryIntegrationTest | — | ✅ PASS |

---

## 3. 도메인별 상세

### 3-1. 직접결제 (generatePayment)

**검증 목적**: `generatePayment()` 동시성 동작 및 경계 조건

**설계 의도**
`generatePayment()`는 동시 호출 시 동일 주문에 PENDING 결제를 여러 건 생성할 수 있다.
이는 의도된 동작이며, 중복 완료 차단은 `confirmPayment()` 단계에서 Order 비관적 락 +
`completePayment()`의 상태 전이 검증(`ORDER_CANNOT_COMPLETE_PAYMENT`)으로 보장한다.

**시나리오**

| 케이스 | 조건 | 기대 동작 |
|--------|------|-----------|
| 동시 10스레드 결제 요청 | 동일 orderId | PENDING 여러 건 생성 가능 (설계 의도) |
| 단건 정상 요청 | 유효 주문 | PENDING 1건 생성 |
| 결제 기한 초과 주문 | paymentDeadline 2시간 초과 | `PAYMENT_WINDOW_EXPIRED` 예외 |
| 즉시구매 주문 | OrderType.BUYOUT | `PAYMENT_BUYOUT_DIRECT_NOT_ALLOWED` 예외 + 결제 레코드 미생성 |

---

### 3-2. 자동결제 (autoPayment)

**검증 목적**: `autoPayment()` 동시·중복 호출 시 결제 레코드 1건만 생성 및 PortOne 중복 호출 방지

`autoPayment()`는 Kafka consumer에서 호출되므로 네트워크 단절·재전송 등으로 동일 메시지가
중복 소비될 수 있다. 이에 3단계 멱등성 보호가 적용된다.

**3단계 멱등성 보호 구조**

```
1차 — 결제 레코드 중복 방지
  createBillingKeyPaymentIfAbsent()
    └─ REQUIRES_NEW + Order PESSIMISTIC_WRITE 락
         → 동시 호출 직렬화, BILLING_KEY 결제 레코드 1건만 생성

2차 — PortOne API 중복 호출 방지
  markBillingKeyRequested()
    └─ REQUIRES_NEW + Payment PESSIMISTIC_WRITE 락
         → billingKeyRequestedAt 필드로 attemptBillingKeyPayment() 1회만 호출
         → 이미 요청된 경우 getPayment()로 상태 조회만 수행

3차 — 결제 완료 중복 방지
  completePayment()
    └─ REQUIRES_NEW + Payment·Order PESSIMISTIC_WRITE 락
         → isFinalized() 체크로 COMPLETED 중복 전이 차단
```

**결과**

| 케이스 | 스레드 수 | BILLING_KEY 결제 | attemptBillingKeyPayment 호출 | 최종 상태 |
|--------|-----------|-----------------|-------------------------------|-----------|
| 동시 10회 호출 | 10 | **1건** | **1회** | COMPLETED |
| 순차 2회 호출 | 1 | **1건** | **1회** | COMPLETED |
| 완료 주문 재호출 | 1 | 0건 (기존 반환) | **0회** | COMPLETED |

---

### 3-3. 입찰 (Bid)

**검증 목적**: `createBid()` 동시 호출 시 Redisson 분산 락 직렬화 효과

**동시성 보호 메커니즘**
```text
AuctionBidCommandService.createBid()
  └─ redissonClient.getLock("auction:lock:{auctionId}")
       └─ lock.tryLock(0, SECONDS)  ← 대기 없이 즉시 실패
            성공 스레드: 입찰 생성 → afterCompletion에서 락 해제
            실패 스레드: BID_LOCK_FAILED 즉시 반환
```

테스트에서는 Redisson을 `AtomicBoolean.compareAndSet`으로 시뮬레이션해 실제 Redis 없이 락 직렬화를 검증했다.

**결과**

| 항목 | 값 |
|------|----|
| 동시 스레드 수 | 10 |
| 성공 | 1건 |
| 실패 (BID_LOCK_FAILED) | 9건 |
| DB 생성된 입찰 | 1건 (LEADING) |
| 입찰가 정확성 | 2,000원 정확히 반영 |

---

### 3-4. 정산 (Settlement)

**검증 목적**: `createSettlement()` 멱등성 — 같은 orderUid로 여러 번 호출해도 정산 1건만 생성

**이중 방어 구조**

```text
1차 방어 (순차 중복)
  └─ existsByOrderId() 체크 → true면 즉시 return

2차 방어 (동시 레이스 컨디션)
  └─ settlements.order_id UNIQUE 제약
       └─ 두 번째 INSERT → DataIntegrityViolationException
            └─ catch 블록에서 existsByOrderId() 재확인 후 무시
```

**결과**

| 케이스 | 스레드 수 | DB 정산 건수 |
|--------|-----------|--------------|
| 순차 2회 호출 | 1 | 1건 |
| 동시 10회 호출 | 10 | 1건 |

> 동시 케이스에서 일부 스레드는 JPA 세션 오염으로 예외가 발생할 수 있으나,
> DB 결과 정합성은 UNIQUE 제약이 보장한다.

---

### 3-5. 환불 (Refund)

**검증 목적**: `createRefund()` 동시 호출 시 Order 비관적 락 직렬화 효과

**동시성 보호 메커니즘**
```text
RefundCommandService.createRefund()
  └─ orderRepository.findByIdWithLock(orderId)  ← PESSIMISTIC_WRITE
       직렬화 보장:
         Thread 1: 락 획득 → 활성 환불 없음 확인 → 환불 생성 → 커밋(락 해제)
         Thread 2: 락 대기 → (Thread 1 커밋 후) 락 획득
                   → existsByOrderIdAndStatusIn() = true
                   → REFUND_ALREADY_EXISTS 예외
```

**결과**

| 항목 | 값 |
|------|----|
| 동시 스레드 수 | 10 |
| 성공 | 1건 |
| 실패 (REFUND_ALREADY_EXISTS) | 9건 |
| DB 생성된 환불 | 1건 (REQUESTED) |

---

### 3-6. Kafka consumer 멱등성

**검증 목적**: `PaymentKafkaConsumer.consume()`이 동일 메시지를 중복 소비해도 결제 1건만 생성

consumer 메서드를 직접 호출해 실제 Kafka 브로커 없이 검증한다.
두 번째 소비 시 `autoPayment()`의 PAYMENT_COMPLETED 조기 반환 경로가 동작한다.

**결과**

| 케이스 | 소비 횟수 | 결제 건수 | PortOne 호출 |
|--------|-----------|-----------|--------------|
| order.created 중복 소비 | 2회 | 1건 | 1회 |
| 다른 이벤트 타입 | 1회 | 0건 | 0회 |

---

### 3-7. 직접결제 확정 (confirmPayment)

**검증 목적**: 동시·중복 confirmPayment 호출 시 COMPLETED 결제 1건만 생성

**이중 방어 구조**
```text
1차 (락 없음)
  └─ payment.isFinalized() 조기 반환

2차 (락 있음)
  └─ findPaymentByUidWithLock() + completePayment() REQUIRES_NEW
       → 먼저 도착한 스레드만 complete(), 이후 스레드는 isFinalized=true 조기 반환
```

**결과**

| 케이스 | 스레드 수 | COMPLETED 건수 | PortOne 호출 |
|--------|-----------|---------------|--------------|
| 동시 10스레드 | 10 | 1건 | 1회 |
| 순차 2회 | 1 | 1건 | 1회 |

---

### 3-8. 즉시구매 (buyout)

**검증 목적**: `buyout()` 동시 호출 시 Redisson 분산 락 직렬화 효과

**결과**

| 항목 | 값 |
|------|----|
| 동시 스레드 수 | 10 |
| 성공 | 1건 |
| 실패 (AUCTION_LOCK_FAILED) | 9건 |
| 경매 최종 상태 | ENDED |
| 생성된 주문 | 1건 |

---

### 3-9. 환불 재시도 (retryRefund)

**검증 목적**: `retryRefund()` 시나리오별 상태 전이 및 지수 백오프 정책 검증

**재시도 정책 (RefundRetryPolicy.MAX_RETRY_COUNT = 5)**
```text
retryCount < 5  → FAILED_RETRYABLE (nextRetryAt 지수 백오프 갱신)
retryCount >= 5 → FAILED_FINAL     (수동 처리 필요)
nextRetryAt 미도래 → 스킵 (상태 변화 없음)
```

**결과**

| 케이스 | 초기 retryCount | PortOne | 결과 상태 |
|--------|----------------|---------|-----------|
| 취소 성공 | 0 | 성공 | COMPLETED (Payment·Order도 REFUNDED) |
| 취소 실패 (미소진) | 1 | 실패 | FAILED_RETRYABLE (retryCount=2) |
| 취소 실패 (한도 초과) | 5 | 실패 | FAILED_FINAL |
| nextRetryAt 미도래 | 1 | 미호출 | FAILED_RETRYABLE (변화 없음) |

---

## 4. 공통 테스트 인프라

**소프트 딜리트 처리**

모든 엔티티(`User`, `Order`, `Payment`, `Auction`, `AuctionBid`, `Settlement`, `Refund`)가
`@SQLDelete` 소프트 딜리트를 사용한다. `@AfterEach`에서 `repository.delete()`를 쓰면
`UPDATE ... SET deleted_at = NOW()`만 실행되어 다음 테스트의 `@BeforeEach`에서
unique constraint 충돌이 발생한다.

**해결**: `JdbcTemplate`으로 하드 DELETE 수행

```java
@AfterEach
void tearDown() {
    jdbcTemplate.update("DELETE FROM <table> WHERE <condition>", ...);
}
```

**Spring 컨텍스트 캐싱**

9개 테스트 클래스 모두 동일한 `@MockBean` 세트를 사용하므로 Spring이 컨텍스트를 재사용한다.
전체 실행 시간 단축에 기여한다.

**테스트 작성 중 발견 및 수정한 프로덕션 코드 이슈**

| 파일 | 문제 | 수정 |
|------|------|------|
| `PaymentRepository` | `findAllByOrderId()` — 테스트 검증용으로만 사용되는 메서드가 프로덕션 레포지토리에 선언됨 | 메서드 제거, 테스트는 `JdbcTemplate` 직접 쿼리로 대체 |
| `SecurityConfig` | `@Value("${cors.allowed-origins}")` — 기본값 없음. 테스트 컨텍스트 로드 시 `PlaceholderResolutionException` 발생 | `@Value("${cors.allowed-origins:http://localhost:*}")` 기본값 추가, `src/test/resources/application.yaml`의 임시 cors 항목 제거 |

---

## 5. 미검증 영역 (향후 과제)

기존 미검증 항목은 모두 구현 완료. 남은 영역:

| 영역 | 이유 | 제안 방향 |
|------|------|-----------|
| Kafka 이벤트 멱등성 (실제 브로커) | consumer 메서드 직접 호출 방식으로 검증 — 실제 at-least-once 전달은 미검증 | Testcontainers Kafka 도입 |
| `confirmPayment` 금액 불일치 취소 경로 | PortOne 금액 불일치 케이스 미검증 | cancelPayment stub 추가 |
| 환불 재시도 동시 호출 직렬화 | 비관적 락으로 직렬화 보장하나 동시성 테스트 미작성 | 동시 10스레드 retryRefund 검증 |
