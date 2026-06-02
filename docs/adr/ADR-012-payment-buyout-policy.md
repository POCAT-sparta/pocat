# ADR-012: 즉시구매 자동결제 및 결제 생성 정책

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-01 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

즉시구매 API는 사용자가 경매 종료를 기다리지 않고 구매를 확정하는 동기 API다. 이 흐름에서는 주문 생성, 자동결제, 경매 확정 또는 복구가 하나의 사용자 요청 안에서 이어진다.

기존 결제 흐름에는 다음과 같은 위험이 있었다.

- 즉시구매 주문 생성 트랜잭션 안에서 외부 PG 자동결제를 호출하면 네트워크 지연이나 타임아웃 동안 DB 트랜잭션이 길게 유지된다.
- 자동결제 메서드가 여러 진입점에서 호출될 수 있어, Kafka 중복 소비나 API 재시도 시 같은 주문에 대해 자동결제 Payment가 중복 생성될 수 있다.
- 낙찰 주문과 즉시구매 주문의 자동결제 실패 정책이 다르지만, 실패 이벤트와 로그만 보면 두 흐름이 구분되지 않는다.
- 직접결제는 결제창 재발급을 위해 여러 Payment 생성을 허용해야 하므로, 결제 테이블 전체에 단순 unique 제약을 걸기 어렵다.

따라서 즉시구매, 낙찰 주문, 자동결제, 직접결제의 정책을 명확히 분리할 필요가 있다.

---

## 결정 (Decision)

### 1. 즉시구매 주문은 자동결제만 허용한다.

즉시구매 주문(`OrderType.BUYOUT`)은 자동결제(`PaymentType.BILLING_KEY`)만 허용한다.

즉시구매 자동결제에 실패하면 해당 주문은 `CANCELLED`로 종료한다. 즉시구매 실패 주문은 직접결제 전환을 허용하지 않는다.

즉시구매에 실패한 사용자가 다시 구매하려면 즉시구매 API를 다시 호출해야 한다. 이 경우 새로운 주문과 새로운 자동결제 시도가 생성된다.

### 2. 낙찰 주문은 자동결제 실패 후 직접결제를 허용한다.

낙찰 주문(`OrderType.AUCTION`)은 자동결제 실패 시 `AUTO_PAYMENT_FAILED`로 전환하고, 결제 기한 안에서 직접결제(`PaymentType.PG_DIRECT`)를 허용한다.

`AutoPaymentFailedEvent`는 낙찰 주문의 직접결제 대기 흐름을 위한 이벤트로 사용한다. 즉시구매 자동결제 실패에는 이 이벤트를 발행하지 않는다.

### 3. 직접결제는 여러 번 생성할 수 있고, 자동결제는 주문당 하나만 생성한다.

직접결제 Payment는 결제창 재발급과 사용자 재시도를 위해 여러 번 생성할 수 있다.

자동결제 Payment는 같은 주문에 대해 하나만 생성한다. 이를 위해 자동결제 Payment 생성은 `PaymentCommandService.createBillingKeyPaymentIfAbsent(orderId)`를 통해서만 수행한다.

중복 방지 방식은 다음과 같다.

- 주문 row를 비관적 락으로 조회한다.
- 같은 주문에 기존 `BILLING_KEY` Payment가 있으면 새 Payment를 만들지 않는다.
- 기존 `BILLING_KEY` Payment가 있으면 PortOne 자동결제를 다시 호출하지 않고 기존 결제 응답을 반환한다.
- DB unique 제약은 사용하지 않는다. 직접결제는 여러 번 생성 가능해야 하기 때문이다.

### 4. 즉시구매 API는 동기 처리한다.

즉시구매 API는 다음 순서로 동기 처리한다.

1. 경매를 `PAYMENT_PENDING`으로 선점한다.
2. 즉시구매 주문을 생성한다.
3. 생성된 주문의 `orderUid`로 자동결제를 호출한다.
4. 자동결제가 성공하면 경매를 즉시구매 낙찰로 확정한다.
5. 자동결제가 실패하거나 완료 상태가 확인되지 않으면 경매를 `ACTIVE`로 복구한다.

즉시구매 자동결제 실패 시 주문은 결제 도메인에서 `CANCELLED`로 종료하며, 직접결제 대기 이벤트는 발행하지 않는다.

### 5. 주문 생성 트랜잭션과 외부 PG 호출을 분리한다.

`OrderCommandService.createOrderFromBuyout()`은 즉시구매 주문 생성만 담당한다.

자동결제 호출은 `AuctionBuyoutService.buyout()`에서 주문 생성 이후 수행한다. 이를 통해 주문 저장 트랜잭션 안에서 외부 PG 호출을 기다리지 않는다.

결제 완료 처리는 `PaymentCommandService.completePayment(paymentId, orderId, ...)`에서 수행한다.

- `completePayment()`는 `REQUIRES_NEW` 트랜잭션을 연다.
- 전달받은 엔티티를 직접 수정하지 않고, 트랜잭션 안에서 `Payment`와 `Order`를 ID로 다시 조회한다.
- 조회된 영속 엔티티에 `payment.complete()`과 `order.completePayment()`를 적용한다.
- 완료된 `Payment`를 반환하고, 응답은 반환값 기준으로 생성한다.

이 방식은 `autoPayment()`, 직접결제 confirm, PortOne webhook 등 어느 진입점에서 호출되더라도 결제 완료 상태 변경이 같은 방식으로 저장되도록 한다.

---

## 결과 (Consequences)

### 긍정적 영향

- 즉시구매 실패 정책이 명확해진다. 실패한 즉시구매 주문은 직접결제로 넘어가지 않고 취소로 종료된다.
- 낙찰 주문의 자동결제 실패와 즉시구매 자동결제 실패가 서로 다른 흐름으로 분리된다.
- Kafka 중복 소비나 API 재시도 상황에서도 같은 주문의 자동결제 Payment가 중복 생성되지 않는다.
- 주문 생성 트랜잭션이 외부 PG 네트워크 I/O에 묶이지 않는다.
- 직접결제 Payment는 기존 요구사항대로 여러 번 생성할 수 있다.

### 주의사항

- 자동결제 중복 방지는 DB unique 제약이 아니라 서비스 정책과 비관적 락에 의존한다.
- 즉시구매 자동결제 실패를 다른 서비스가 알아야 하는 요구사항이 생기면 `AutoPaymentFailedEvent`를 재사용하지 말고 별도 이벤트를 정의해야 한다.
- `AutoPaymentFailedEvent`는 직접결제 대기 흐름을 의미하므로 즉시구매 실패에 사용하면 의미가 섞인다.

---

## 관련 코드

- `AuctionBuyoutService` - 즉시구매 선점, 주문 생성 후 자동결제 호출, 성공 확정 또는 경매 복구
- `OrderCommandService.createOrderFromBuyout()` - 즉시구매 주문 생성
- `PaymentApplicationService.autoPayment()` - 자동결제 진입점
- `PaymentCommandService.createBillingKeyPaymentIfAbsent()` - 주문당 자동결제 Payment 중복 생성 방지
- `PaymentCommandService.completePayment()` - 결제 완료 트랜잭션 처리
- `FailureService.persistBillingKeyFailure()` - 주문 유형별 자동결제 실패 처리
- `PaymentApplicationService.generatePayment()` - 즉시구매 주문의 직접결제 생성 차단
- `PaymentKafkaConsumer` - 자동결제 실패 메시지 소비 및 비즈니스 실패 skip-ack 처리
