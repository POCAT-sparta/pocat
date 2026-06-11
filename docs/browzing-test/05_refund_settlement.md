# 05. 환불 (Refund) / 정산 (Settlement) 테스트 시나리오

## 사전 조건
- 결제 완료된 주문(orderUid) 존재

---

## TC-REFUND-001: 환불 요청

**우선순위**: 최상  
**관련 API**: `POST /api/v1/refunds`  
**인증 필요**: 있음 (구매자)

### 요청
```json
POST /api/v1/refunds
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "orderId": {ORDER_ID},
  "reason": "상품 설명과 다른 상태"
}
```

### 확인 항목
- [ ] 201 응답
- [ ] 환불 상태 `PENDING` (어드민 검토 대기)
- [ ] refundId 반환 → 저장: `REFUND_ID`
- [ ] 결제 미완료 주문에 대한 환불 요청 → 400

---

## TC-REFUND-002: 내 환불 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/refunds/me`  
**인증 필요**: 있음

### 요청
```
GET /api/v1/refunds/me
GET /api/v1/refunds/me?status=PENDING
GET /api/v1/refunds/me?status=APPROVED
GET /api/v1/refunds/me?status=REJECTED
```

### 확인 항목
- [ ] 200 응답
- [ ] status 필터 동작
- [ ] 본인 환불 내역만 반환

---

## TC-REFUND-003: 환불 상세 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/refunds/{refundId}`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 환불 정보 (refundId, orderId, status, reason, rejectionReason)
- [ ] 본인 환불이 아닌 경우 403

---

## TC-SETTLEMENT-001: 내 정산 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/settlements/me`  
**인증 필요**: 있음 (판매자)

### 요청
```
GET /api/v1/settlements/me
Authorization: Bearer {ACCESS_TOKEN_A}
```

### 확인 항목
- [ ] 200 응답
- [ ] 본인 판매 건에 대한 정산 목록
- [ ] 각 정산에 settlementUid, settlementAmount, status 포함

---

## TC-SETTLEMENT-002: 정산 상세 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/settlements/{settlementUid}`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] settlementAmount가 최종 낙찰가 기반인지 확인
- [ ] status: PENDING (어드민 완료 처리 전)

---

## 환불/정산 플로우

```
[환불 플로우]
구매자 환불 요청 → status: PENDING
어드민 승인 → status: APPROVED → PortOne 자동 환불 처리
어드민 거절 → status: REJECTED (사유 포함)

[정산 플로우]
결제 완료 → 정산 자동 생성 (status: PENDING)
어드민 정산 완료 처리 → status: COMPLETED
```

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-REFUND-001 | 환불 요청 | ⬜ |
| TC-REFUND-002 | 내 환불 목록 (필터) | ⬜ |
| TC-REFUND-003 | 환불 상세 조회 | ⬜ |
| TC-SETTLEMENT-001 | 내 정산 목록 | ⬜ |
| TC-SETTLEMENT-002 | 정산 상세 조회 | ⬜ |
