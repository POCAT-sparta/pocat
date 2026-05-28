# ADR-006: 결제 실패 후 재시도 정책

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-28 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

경매 낙찰 후 결제는 자동결제(빌링키) → 직접결제(PG) → 재경매 순서로 진행된다.
자동결제가 실패하면 낙찰자가 직접 PG 결제를 시도할 수 있는 기회가 필요하다.
1순위도 실패하면 2순위 낙찰자에게 동일한 기회를 부여하고, 모두 실패하면 재경매로 처리한다.

---

## 결정 (Decision)

### 결제 단계별 흐름

```
경매 종료
  └─ 1순위 낙찰자 자동결제(빌링키) 시도
       ├─ 성공 → PAYMENT_COMPLETED
       └─ 실패 → AUTO_PAYMENT_FAILED
              └─ 1시간 직접결제 기회 부여 (paymentDeadline = now + 1h, Redis TTL 등록)
                   ├─ 직접결제 성공 → PAYMENT_COMPLETED
                   └─ 1시간 만료 or 직접결제 실패 → PaymentWindowExpiredEvent 발행
                          └─ 2순위 낙찰자 존재?
                               ├─ 있음 → 2순위 낙찰자에게 1시간 기회 부여
                               └─ 없음 → 재경매 처리
```

### OrderStatus 전이

| 이전 상태 | 이벤트 | 이후 상태 |
|----------|--------|----------|
| PAYMENT_PENDING | 자동결제 성공 | PAYMENT_COMPLETED |
| PAYMENT_PENDING | 자동결제 실패 | AUTO_PAYMENT_FAILED |
| AUTO_PAYMENT_FAILED | 직접결제 성공 | PAYMENT_COMPLETED |
| AUTO_PAYMENT_FAILED | 직접결제 실패 | DIRECT_PAYMENT_FAILED |
| DIRECT_PAYMENT_FAILED | 결제창 만료 | (PaymentWindowExpiredEvent 발행, 다음 순위로 전환) |

### Redis TTL 만료 처리 (`FailureService.markFailed`)

- Redis keyspace expired 이벤트 수신 → `PaymentExpiryEventListener.onMessage()`
- `AUTO_PAYMENT_FAILED` 상태인 주문에 대해 `PaymentWindowExpiredEvent` 발행
- Consumer(주문 도메인)에서 다음 순위 입찰자 처리

### 직접결제 접근 조건 (`PaymentApplicationService.generatePayment`)

- Order 상태가 `AUTO_PAYMENT_FAILED`인 경우에만 직접결제 레코드 생성 허용
- 1시간 이내(`paymentDeadline` 기준) 요청만 허용

---

## 관련 코드

- `PaymentApplicationService.generatePayment()` — 직접결제 레코드 생성 (AUTO_PAYMENT_FAILED 체크)
- `FailureService.persistBillingKeyFailure()` — 자동결제 실패 처리 + Redis TTL 등록
- `FailureService.markFailed()` — TTL 만료 시 결제창 만료 이벤트 발행 (AUTO_PAYMENT_FAILED 체크)
- `PaymentExpiryEventListener` — Redis keyspace expired 이벤트 수신 → markFailed 호출
