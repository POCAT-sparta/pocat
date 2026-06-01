# 결제 동시성 버그 — 발견 및 수정

## 요약

비관적 락이 있어도 `generatePayment()`가 Order 상태를 변경하지 않아 동일 주문에 다중 PENDING 결제가 생성되는 버그를 k6 동시성 테스트로 실증하고 수정했다.

---

## 발견 경위

k6 `shared-iterations` 실행기로 30 VU가 동일 `orderId`에 동시 결제 요청을 보내는 시나리오(`04-payment-concurrency.js`)를 실행했다.

### 버그 재현 결과

```
concurrent_successes: 10   ← 정상이면 1
concurrent_failures:  20   ← 레이트리밋(429) 차단
```

DB 확인 결과, **동일 주문에 PENDING 결제 10건이 약 80ms 안에 생성됨**:

```
PAY-0QJ59EQK3TPDG  PENDING  11:39:16.892
PAY-0QJ59EQNKTPDH  PENDING  11:39:16.908
...
PAY-0QJ59EQXKTPDS  PENDING  11:39:16.972
```

---

## 원인

`generatePayment()`는 Order에 비관적 락을 걸고 결제 생성 후 트랜잭션을 커밋한다.
그러나 **결제 생성 후 Order 상태를 변경하지 않아**, 락이 풀린 뒤 다음 요청이 락을 잡아도 상태 검증을 그대로 통과한다.

```
Thread A ─ 락 획득 → AUTO_PAYMENT_FAILED 확인 → PENDING 결제 생성 → 커밋(락 해제)
Thread B ─ 락 획득 → AUTO_PAYMENT_FAILED 확인 (여전히 동일) → PENDING 결제 또 생성
Thread C ─ ...
```

---

## 수정 내용

**비관적 락 범위 안에서 PENDING 결제 존재 여부를 확인**하는 중복 방지 체크를 추가했다.
락이 직렬화를 보장하므로 TOCTOU 없이 안전하게 확인할 수 있다.

```java
// PaymentApplicationService.generatePayment()
if (paymentQueryService.findByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING).isPresent()) {
    throw new PaymentException(ErrorCode.PAYMENT_ALREADY_PENDING);  // 409 Conflict
}
```

추가된 ErrorCode:
```java
PAYMENT_ALREADY_PENDING(HttpStatus.CONFLICT, "이미 진행 중인 결제가 있습니다.")
```

---

## 수정 후 기대 동작

```
Thread A ─ 락 획득 → PENDING 없음 확인 → 결제 생성 → 커밋(락 해제)  → 201
Thread B ─ 락 획득 → PENDING 존재 확인 → 409 반환
Thread C ─ 락 획득 → PENDING 존재 확인 → 409 반환
```

`concurrent_successes == 1` 달성.

---

## 부수 발견: 레이트리밋의 역할

동시 30 요청 중 20개는 `RATE_LIMIT_EXCEEDED(429)`로 비즈니스 로직 도달 전에 차단됐다.
레이트리밋이 1차 방어선으로 동작하고 있으나, 이것만으로는 충분하지 않다는 점도 실증됐다.
(레이트리밋 설정에 따라 복수 요청이 통과할 수 있으므로 서비스 레이어 수준의 중복 방지가 필수다.)
