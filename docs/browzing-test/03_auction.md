# 03. 경매 (Auction) / 입찰 (Bid) 테스트 시나리오

## 사전 조건
- 승인된 카드(cardId) 존재 → 어드민에서 카드 승인 선행 필요 ([09_admin.md](09_admin.md) 참조)
- 판매자 계정(User A) 로그인 완료

---

## TC-AUCTION-001: 경매 생성

**우선순위**: 최상  
**관련 API**: `POST /api/v1/auctions`  
**인증 필요**: 있음 (일반 사용자)

### 요청
```json
POST /api/v1/auctions
Authorization: Bearer {ACCESS_TOKEN_A}
Content-Type: application/json

{
  "cardId": {approvedCardId},
  "title": "PSA 10 피카츄 경매",
  "description": "상태 최상급 카드입니다.",
  "startingPrice": 10000,
  "buyoutPrice": 50000,
  "startedAt": "2026-06-12T10:00:00",
  "endedAt": "2026-06-14T10:00:00"
}
```

### 확인 항목
- [ ] 201 응답
- [ ] 경매 상태 `PENDING` (어드민 검수 대기)
- [ ] auctionId 반환 → 저장: `AUCTION_ID`
- [ ] 이미 경매 중인 카드로 재등록 시 409 또는 400

---

## TC-AUCTION-002: 인기 경매 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/auctions/popular`

### 확인 항목
- [ ] 200 응답
- [ ] 인기순(조회수/입찰 수 등) 정렬 확인
- [ ] 응답 캐싱 확인 (두 번 연속 호출 시 동일 결과)

---

## TC-AUCTION-003: 경매 목록 검색 및 필터

**우선순위**: 상  
**관련 API**: `GET /api/v1/auctions`

### 필터 조합 테스트

| 파라미터 | 값 | 확인 항목 |
|---------|----|-----------| 
| keyword | `피카츄` | 제목/카드명에 키워드 포함 |
| status | `ACTIVE` | 진행 중인 경매만 반환 |
| minPrice | `5000` | 현재가 5000 이상 |
| maxPrice | `100000` | 현재가 100000 이하 |
| grade | `PSA_10` | 해당 등급 카드 |
| sort | `HIGHEST_PRICE` / `LATEST` / `ENDING_SOON` | 정렬 확인 |

### 확인 항목
- [ ] 200 응답
- [ ] 필터 조건 반영 결과
- [ ] 페이지네이션 동작 (page, size)

---

## TC-AUCTION-004: 경매 상세 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/auctions/{auctionId}`

### 확인 항목
- [ ] 200 응답
- [ ] 경매 정보 (title, description, startingPrice, buyoutPrice, currentPrice, status, endedAt)
- [ ] 카드 정보 포함
- [ ] 판매자 정보 포함

---

## TC-AUCTION-005: 내 경매 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/auctions/me`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 본인이 등록한 경매만 반환

---

## TC-AUCTION-006: 경매 수정

**우선순위**: 중  
**관련 API**: `PATCH /api/v1/auctions/{auctionId}`  
**인증 필요**: 있음 (판매자 본인)

### 요청
```json
PATCH /api/v1/auctions/{AUCTION_ID}
Authorization: Bearer {ACCESS_TOKEN_A}

{
  "description": "수정된 설명입니다.",
  "buyoutPrice": 55000
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 수정된 필드 반영 확인
- [ ] 다른 사용자가 수정 시도 시 403

---

## TC-AUCTION-007: 입찰 생성

**우선순위**: 최상  
**관련 API**: `POST /api/v1/auctions/{auctionId}/bids`  
**인증 필요**: 있음 (구매자, User B)  
**사전 조건**: 경매 상태가 `ACTIVE` (어드민 검수 통과 후)

### 요청
```json
POST /api/v1/auctions/{AUCTION_ID}/bids
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "bidAmount": 15000
}
```

### 확인 항목
- [ ] 201 응답
- [ ] 경매의 currentPrice 업데이트 확인
- [ ] 판매자 본인이 입찰 시 400/403
- [ ] 현재가보다 낮은 금액 입찰 시 400
- [ ] 시작가보다 낮은 금액 입찰 시 400
- [ ] 동시 입찰 시 동시성 처리 확인 (선착순 처리)

---

## TC-AUCTION-008: 입찰 내역 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/auctions/{auctionId}/bids`

### 확인 항목
- [ ] 200 응답
- [ ] 입찰 목록 (bidderId, bidAmount, createdAt) 반환
- [ ] 금액 내림차순 정렬

---

## TC-AUCTION-009: 내 입찰 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/bids/me`  
**인증 필요**: 있음

### 상태 필터 테스트
```
GET /api/v1/bids/me?status=WINNING
GET /api/v1/bids/me?status=LOSING
GET /api/v1/bids/me?status=WON
```

### 확인 항목
- [ ] 200 응답
- [ ] status 필터 동작

---

## TC-AUCTION-010: 즉시구매 (Buyout)

**우선순위**: 최상  
**관련 API**: `POST /api/v1/auctions/{auctionId}/buyout`  
**인증 필요**: 있음 (구매자)  
**사전 조건**: buyoutPrice 설정된 ACTIVE 상태 경매

### 요청
```
POST /api/v1/auctions/{AUCTION_ID}/buyout
Authorization: Bearer {ACCESS_TOKEN_B}
```

### 확인 항목
- [ ] 201 응답
- [ ] 주문(Order) 자동 생성 확인 → orderId 저장
- [ ] 경매 상태 `ENDED` 변경 확인
- [ ] 이미 즉시구매 완료된 경매 재시도 시 409

---

## TC-AUCTION-011: 경매 취소 (판매자)

**우선순위**: 상  
**관련 API**: `PATCH /api/v1/auctions/{auctionId}/cancel`  
**인증 필요**: 있음 (판매자)

### 확인 항목
- [ ] 200 응답
- [ ] 경매 상태 `CANCELLED` 변경
- [ ] 입찰 내역이 있는 경우 취소 가능 여부 확인

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-AUCTION-001 | 경매 생성 | ⬜ |
| TC-AUCTION-002 | 인기 경매 목록 | ⬜ |
| TC-AUCTION-003 | 경매 목록 필터/검색 | ⬜ |
| TC-AUCTION-004 | 경매 상세 조회 | ⬜ |
| TC-AUCTION-005 | 내 경매 목록 | ⬜ |
| TC-AUCTION-006 | 경매 수정 | ⬜ |
| TC-AUCTION-007 | 입찰 생성 | ⬜ |
| TC-AUCTION-008 | 입찰 내역 조회 | ⬜ |
| TC-AUCTION-009 | 내 입찰 목록 | ⬜ |
| TC-AUCTION-010 | 즉시구매 | ⬜ |
| TC-AUCTION-011 | 경매 취소 | ⬜ |
