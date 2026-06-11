# 04. 주문 (Order) / 결제 (Payment) 테스트 시나리오

## 사전 조건
- 즉시구매 또는 경매 낙찰로 생성된 주문(orderId, orderUid) 존재
- PortOne 결제 연동 설정 완료

---

## TC-ORDER-001: 내 주문 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/orders/me`  
**인증 필요**: 있음

### 요청
```
GET /api/v1/orders/me
GET /api/v1/orders/me?status=PENDING_PAYMENT
GET /api/v1/orders/me?status=COMPLETED
```

### 상태값 목록
| status | 설명 |
|--------|------|
| PENDING_PAYMENT | 결제 대기 |
| PAYMENT_COMPLETED | 결제 완료 |
| CANCELLED | 취소됨 |
| COMPLETED | 거래 완료 |

### 확인 항목
- [ ] 200 응답
- [ ] status 필터 동작
- [ ] 각 주문에 orderId, orderUid, finalPrice, status, paymentDeadline 포함

---

## TC-ORDER-002: 주문 상세 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/orders/{orderUid}`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 주문 상세 (orderUid, finalPrice, status, cardInfo, buyerInfo, sellerInfo)
- [ ] 본인의 주문이 아닌 경우 403

---

## TC-ORDER-003: 주문 취소

**우선순위**: 상  
**관련 API**: `PATCH /api/v1/orders/{orderUid}/cancel`  
**인증 필요**: 있음 (구매자)

### 요청
```json
PATCH /api/v1/orders/{ORDER_UID}/cancel
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "cancelReason": "단순 변심"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 주문 상태 `CANCELLED` 변경
- [ ] 결제 완료 후 취소 시도 → 결제 취소 연동 확인
- [ ] paymentDeadline 초과 후 취소 시 처리 확인

---

## TC-PAYMENT-001: 결제 요청 생성

**우선순위**: 최상  
**관련 API**: `POST /api/v1/payments`  
**인증 필요**: 있음

### 요청
```json
POST /api/v1/payments
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "orderUid": "{ORDER_UID}"
}
```

### 예상 응답
```json
{
  "paymentUid": "payment_xxxx",
  "amount": 50000,
  "orderUid": "...",
  "pgProvider": "PORTONE"
}
```

### 확인 항목
- [ ] 201 응답
- [ ] paymentUid 반환 → 저장: `PAYMENT_UID`
- [ ] amount가 주문 금액과 일치
- [ ] 이미 결제된 주문에 대한 재시도 시 409

---

## TC-PAYMENT-002: 결제 확인 (Confirm)

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/payments/{paymentUid}`  
**인증 필요**: 있음

### 사전 조건
- 클라이언트 측에서 PortOne SDK를 통해 실제 결제 완료
- PortOne에서 발급한 imp_uid 확보

### 요청
```json
PATCH /api/v1/payments/{PAYMENT_UID}
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "impUid": "imp_xxxx_portone_uid"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 결제 상태 `COMPLETED` 변경
- [ ] 주문 상태 `PAYMENT_COMPLETED` 변경
- [ ] 정산(Settlement) 자동 생성 확인
- [ ] 금액 불일치 시 결제 취소 처리 확인 (위변조 방지)

---

## TC-PAYMENT-003: 결제 상세 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/payments/{paymentUid}`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 결제 정보 (paymentUid, amount, status, pgProvider, paymentKey)
- [ ] 본인 결제가 아닌 경우 403

---

## TC-PAYMENT-004: PortOne 웹훅 처리

**우선순위**: 최상  
**관련 API**: `POST /api/v1/payments/webhook`  
**인증 필요**: 없음 (IP 화이트리스트 + HMAC 검증)

### 확인 항목
- [ ] 유효한 HMAC 서명의 웹훅 → 200 응답 및 상태 업데이트
- [ ] 잘못된 HMAC 서명 → 401 반환
- [ ] 이미 처리된 결제 웹훅 재수신 → 멱등성 처리 (중복 처리 방지)

---

## 결제 플로우 전체 흐름

```
[구매자]
1. 즉시구매/낙찰 → 주문 자동 생성 (status: PENDING_PAYMENT)
2. POST /payments → paymentUid 발급
3. PortOne SDK로 실제 결제 진행 (클라이언트 측)
4. PATCH /payments/{paymentUid} → 결제 확인 요청
5. 서버에서 PortOne API로 결제 금액 검증
6. 결제 완료 → 주문 status: PAYMENT_COMPLETED
7. 정산 자동 생성 (status: PENDING)
```

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-ORDER-001 | 내 주문 목록 (필터) | ⬜ |
| TC-ORDER-002 | 주문 상세 조회 | ⬜ |
| TC-ORDER-003 | 주문 취소 | ⬜ |
| TC-PAYMENT-001 | 결제 요청 생성 | ⬜ |
| TC-PAYMENT-002 | 결제 확인 (금액 검증 포함) | ⬜ |
| TC-PAYMENT-003 | 결제 상세 조회 | ⬜ |
| TC-PAYMENT-004 | 웹훅 처리 (HMAC 검증) | ⬜ |
