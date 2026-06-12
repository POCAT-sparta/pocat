# 12. E2E 통합 시나리오

전체 플로우를 처음부터 끝까지 순서대로 테스트하는 시나리오입니다.

---

## E2E-001: 경매 → 즉시구매 → 결제 완료 플로우

### 참여자
- **User A**: 판매자
- **User B**: 구매자
- **Admin**: 관리자

### 순서

```text
Step 1. [User A] 회원가입 + 로그인
  POST /api/v1/auth/signup
  POST /api/v1/auth/login
  → ACCESS_TOKEN_A 획득

Step 2. [User B] 회원가입 + 로그인
  POST /api/v1/auth/signup
  POST /api/v1/auth/login
  → ACCESS_TOKEN_B 획득

Step 3. [Admin] 로그인
  POST /api/v1/auth/login
  → ACCESS_TOKEN_ADMIN 획득

Step 4. [User A] 카드 등록 신청
  POST /api/v1/cards/upload (이미지 포함)
  → CARD_ID 획득 (status: PENDING)

Step 5. [Admin] 카드 승인
  PATCH /api/v1/admin/cards/{CARD_ID}/approve
  → 카드 status: APPROVED

Step 6. [User A] 경매 생성
  POST /api/v1/auctions
  → AUCTION_ID 획득 (status: PENDING_INSPECTION)

Step 7. [Admin] 경매 검수 통과
  PATCH /api/v1/admin/auctions/{AUCTION_ID}/inspection (approved: true)
  → 경매 status: ACTIVE

Step 8. [User B] 빌링키 등록
  POST /api/v1/users/me/billing-key

Step 9. [User B] 즉시구매
  POST /api/v1/auctions/{AUCTION_ID}/buyout
  → ORDER_UID 획득 (status: PENDING_PAYMENT)

Step 10. [User B] 결제 요청 생성
  POST /api/v1/payments { orderUid: ORDER_UID }
  → PAYMENT_UID 획득

Step 11. [User B] PortOne SDK로 실제 결제 (클라이언트 작업)

Step 12. [User B] 결제 확인
  PATCH /api/v1/payments/{PAYMENT_UID} { impUid: ... }
  → 결제 status: COMPLETED, 주문 status: PAYMENT_COMPLETED

Step 13. [Admin] 정산 목록 조회
  GET /api/v1/admin/settlements
  → 정산 status: PENDING 확인

Step 14. [Admin] 정산 완료 처리
  PATCH /api/v1/admin/settlements/{SETTLEMENT_UID}/complete
  → 정산 status: COMPLETED

Step 15. [User A] 알림 확인
  GET /api/v1/notifications
  → 결제 완료 알림, 정산 완료 알림 수신 확인
```

### 최종 상태 확인

- [ ] 경매: `ENDED`
- [ ] 주문: `PAYMENT_COMPLETED`
- [ ] 결제: `COMPLETED`
- [ ] 정산: `COMPLETED`
- [ ] User A 알림: 결제 완료 + 정산 완료
- [ ] User B 알림: 낙찰 확정

---

## E2E-002: 경매 입찰 → 낙찰 → 결제 → 환불 플로우

### 순서 (E2E-001과 동일한 인증 세팅 가정)

```text
Step 1~7. E2E-001과 동일 (카드 승인 + 경매 ACTIVE 상태까지)

Step 8. [User B] 입찰
  POST /api/v1/auctions/{AUCTION_ID}/bids { bidAmount: 15000 }

Step 9. 경매 종료 대기 (또는 어드민 강제 종료)

Step 10. [User B] 낙찰 확인 + 주문 생성 확인
  GET /api/v1/orders/me?status=PENDING_PAYMENT

Step 11. [User B] 결제 완료 (E2E-001 Step 10~12와 동일)

Step 12. [User B] 환불 요청
  POST /api/v1/refunds { orderId: ..., reason: "상품 설명과 다름" }
  → REFUND_ID 획득 (status: PENDING)

Step 13. [Admin] 환불 승인
  PATCH /api/v1/admin/refunds/{REFUND_ID}/approve
  → 환불 status: APPROVED, PortOne 환불 처리

Step 14. [User B] 환불 완료 알림 확인
  GET /api/v1/notifications
```

### 최종 상태 확인

- [ ] 환불: `APPROVED`
- [ ] User B 알림: 환불 완료

---

## E2E-003: 커뮤니티 → 채팅 연계 플로우

```text
Step 1. [User A] 거래게시글 작성
  POST /api/v1/posts/trade { title: "리자몽 교환 원해요", ... }
  → TRADE_POST_ID 획득

Step 2. [User B] 거래게시글 조회
  GET /api/v1/posts/trade/{TRADE_POST_ID}

Step 3. [User B] User A에게 채팅 시작
  POST /api/v1/chats { targetUserId: USER_A_ID }
  → CHAT_ID 획득

Step 4. [WebSocket] User B → User A 메시지 전송
  SEND /chat/{CHAT_ID} { "message": "거래게시글 보고 연락드립니다" }

Step 5. [User A] 알림 확인
  GET /api/v1/notifications
  → 새 메시지 알림 수신

Step 6. [User A] 채팅 읽음 처리
  PATCH /api/v1/chats/{CHAT_ID}/read

Step 7. unreadCount 0 확인
  GET /api/v1/chats/me
```

### 최종 상태 확인

- [ ] 거래게시글: 조회 가능
- [ ] 채팅방: 생성됨
- [ ] User A 새 메시지 알림 수신
- [ ] 채팅 읽음 처리 후 unreadCount: 0

---

## E2E-004: 카드 거절 → 재신청 플로우

```text
Step 1. [User A] 카드 신청
  POST /api/v1/cards/upload

Step 2. [Admin] 카드 거절
  PATCH /api/v1/admin/cards/{CARD_ID}/reject { rejectReason: "이미지 불명확" }

Step 3. [User A] 알림 확인
  GET /api/v1/notifications → 거절 알림 수신

Step 4. [User A] 내 카드 목록에서 거절 사유 확인
  GET /api/v1/cards/my-requests → status: REJECTED, rejectReason 확인

Step 5. [User A] 새 이미지로 재신청
  POST /api/v1/cards/upload (다른 이미지)

Step 6. [Admin] 재승인
  PATCH /api/v1/admin/cards/{NEW_CARD_ID}/approve
```

### 최종 상태 확인

- [ ] 거절된 카드: status `REJECTED`, rejectReason 존재
- [ ] 재신청 카드: status `APPROVED`
- [ ] User A 알림: 거절 알림 + 승인 알림 수신

---

## 체크리스트 요약

| 시나리오 | 설명 | 결과 |
|---------|------|------|
| E2E-001 | 즉시구매 → 결제 → 정산 전체 플로우 | ⬜ |
| E2E-002 | 경매 입찰 → 낙찰 → 결제 → 환불 | ⬜ |
| E2E-003 | 거래게시글 → 채팅 연계 | ⬜ |
| E2E-004 | 카드 거절 → 재신청 | ⬜ |
