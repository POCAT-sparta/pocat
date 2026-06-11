# 09. 어드민 (Admin) 테스트 시나리오

## 사전 조건
- 어드민 계정으로 로그인 완료 → `ACCESS_TOKEN_ADMIN` 보유
- 일반 사용자 A, B 계정 준비 ([01_auth.md](01_auth.md) 참조)

---

## 카드 관리

### TC-ADMIN-CARD-001: 카드 신청 목록 조회

**우선순위**: 최상  
**관련 API**: `GET /api/v1/admin/cards/requests`

### 요청
```
GET /api/v1/admin/cards/requests
GET /api/v1/admin/cards/requests?status=PENDING
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] PENDING 상태 카드 목록 반환
- [ ] 각 카드에 cardId, userId, imageUrl, grade, rarity, status 포함
- [ ] 일반 사용자로 접근 시 403

---

### TC-ADMIN-CARD-002: 카드 승인 (핵심 플로우)

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/cards/{cardId}/approve`

### 사전 상태
- 카드 status: `PENDING`

### 요청
```
PATCH /api/v1/admin/cards/{CARD_ID}/approve
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 승인 후 확인 항목
- [ ] 200 응답
- [ ] 카드 status: `APPROVED`로 변경
- [ ] `GET /api/v1/cards/{CARD_ID}` 조회 시 공개 목록에 노출
- [ ] 신청자(User A)에게 승인 알림 발송 → `GET /api/v1/notifications` 확인
- [ ] 승인된 카드로 경매 생성 가능 여부 확인
- [ ] 이미 승인된 카드 재승인 시도 → 400/409

---

### TC-ADMIN-CARD-003: 카드 거절

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/cards/{cardId}/reject`

### 사전 상태
- 카드 status: `PENDING`

### 요청
```json
PATCH /api/v1/admin/cards/{CARD_ID}/reject
Authorization: Bearer {ACCESS_TOKEN_ADMIN}

{
  "rejectReason": "제출된 이미지가 불명확합니다. 카드 전면을 정면에서 촬영해주세요."
}
```

### 거절 후 확인 항목
- [ ] 200 응답
- [ ] 카드 status: `REJECTED`로 변경
- [ ] rejectReason 저장 확인 (`GET /api/v1/cards/my-requests` 응답에 포함)
- [ ] 신청자(User A)에게 거절 알림 발송 확인
- [ ] `GET /api/v1/cards` 공개 목록에 미노출 확인
- [ ] rejectReason 없이 거절 시도 → 400

---

### TC-ADMIN-CARD-004: 카드 정보 수정 (어드민)

**우선순위**: 중  
**관련 API**: `PATCH /api/v1/admin/cards/{cardId}`

### 요청
```json
PATCH /api/v1/admin/cards/{CARD_ID}
Authorization: Bearer {ACCESS_TOKEN_ADMIN}

{
  "grade": "PSA_9",
  "rarity": "HOLO_RARE"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 수정된 필드 반영

---

### TC-ADMIN-CARD-005: 카드 삭제 (어드민)

**우선순위**: 중  
**관련 API**: `DELETE /api/v1/admin/cards/{cardId}`

### 확인 항목
- [ ] 200 응답
- [ ] 삭제 후 `GET /api/v1/cards/{cardId}` 404 반환
- [ ] 진행 중인 경매가 있는 카드 삭제 시 처리 확인 (400 또는 cascade 처리)

---

### TC-ADMIN-CARD-006: TCGdex 카드 동기화

**우선순위**: 중  
**관련 API**: `POST /api/v1/admin/cards/sync`

### 요청
```
POST /api/v1/admin/cards/sync
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 202 Accepted 응답 (비동기 처리)
- [ ] 동기화 완료 후 새 카드 목록 확인
- [ ] 중복 카드 동기화 시 upsert 처리 확인 (기존 카드 중복 생성 금지)

---

### TC-ADMIN-CARD-007: 카드 이미지 S3 마이그레이션

**우선순위**: 낮음  
**관련 API**: `POST /api/v1/admin/cards/migrate-images`

### 확인 항목
- [ ] 202 Accepted 응답 (비동기 처리)
- [ ] 마이그레이션 후 이미지 URL이 S3 URL로 변경 확인

---

## 경매 관리

### TC-ADMIN-AUCTION-001: 경매 목록 조회 (어드민)

**우선순위**: 상  
**관련 API**: `GET /api/v1/admin/auctions`

### 요청
```
GET /api/v1/admin/auctions
GET /api/v1/admin/auctions?status=PENDING_INSPECTION
GET /api/v1/admin/auctions?status=ACTIVE
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] 전체 경매 목록 (status 필터 포함)

---

### TC-ADMIN-AUCTION-002: 경매 검수 통과 (핵심 플로우)

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/auctions/{auctionId}/inspection`

### 사전 상태
- 경매 status: `PENDING_INSPECTION`

### 요청
```json
PATCH /api/v1/admin/auctions/{AUCTION_ID}/inspection
Authorization: Bearer {ACCESS_TOKEN_ADMIN}

{
  "approved": true
}
```

### 검수 통과 후 확인 항목
- [ ] 200 응답
- [ ] 경매 status: `ACTIVE` (또는 `startedAt` 이후 자동 활성화 여부 확인)
- [ ] inspectedAt, inspectedBy 필드 저장 확인
- [ ] 검수 통과 후 일반 사용자가 입찰 가능한지 확인
- [ ] `GET /api/v1/auctions` 공개 목록에 노출 확인

---

### TC-ADMIN-AUCTION-003: 경매 검수 거절

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/auctions/{auctionId}/inspection`

### 사전 상태
- 경매 status: `PENDING_INSPECTION`

### 요청
```json
PATCH /api/v1/admin/auctions/{AUCTION_ID}/inspection
Authorization: Bearer {ACCESS_TOKEN_ADMIN}

{
  "approved": false,
  "rejectReason": "카드 상태가 등록 등급과 상이합니다."
}
```

### 거절 후 확인 항목
- [ ] 200 응답
- [ ] 경매 status: `CANCELLED` 변경
- [ ] 판매자(User A)에게 거절 알림 발송 확인
- [ ] 거절된 경매로 입찰 불가 확인

---

### TC-ADMIN-AUCTION-004: 경매 강제 취소 (어드민)

**우선순위**: 상  
**관련 API**: `PATCH /api/v1/admin/auctions/{auctionId}/cancel`

### 사전 상태
- 경매 status: `ACTIVE` 또는 `PENDING_INSPECTION`

### 요청
```json
PATCH /api/v1/admin/auctions/{AUCTION_ID}/cancel
Authorization: Bearer {ACCESS_TOKEN_ADMIN}

{
  "cancelReason": "부정 거래 신고 접수로 인한 강제 취소"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 경매 status: `CANCELLED`
- [ ] 기존 입찰자들에게 취소 알림 발송
- [ ] 기존 입찰 금액 환불 처리 확인

---

## 사용자 관리

### TC-ADMIN-USER-001: 사용자 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/admin/users`

### 요청
```
GET /api/v1/admin/users?page=0&size=20
GET /api/v1/admin/users?keyword=user_a
GET /api/v1/admin/users?isBidBlocked=true
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] 전체 사용자 목록 반환
- [ ] keyword 검색 동작 (이름/이메일 검색)
- [ ] isBidBlocked 필터 동작
- [ ] 각 사용자에 userId, email, nickname, isBidBlocked, unpaidStrike 포함

---

### TC-ADMIN-USER-002: 사용자 입찰 차단

> **주의**: `AdminUserCommandService.toggleBidBlock()` 서비스는 구현되어 있으나  
> **현재 컨트롤러 엔드포인트가 존재하지 않음**. 배포 전 엔드포인트 노출 여부 확인 필요.

**예상 API** (구현 필요 확인): `PATCH /api/v1/admin/users/{userId}/block`

### 서비스 로직
```java
// AdminUserCommandService
public void toggleBidBlock(Long userId, boolean blocked) {
    // user.block() 또는 user.unblock() 호출
    // USER_BID_BLOCKED, USER_PROFILE 캐시 삭제
}
```

### 확인 항목
- [ ] 엔드포인트 존재 여부 확인
- [ ] 차단 후 해당 사용자 입찰 시 403/400 반환 확인
- [ ] 차단 해제 후 정상 입찰 가능 확인
- [ ] unpaidStrike 기반 자동 차단 로직 존재 여부 확인

---

## 환불 관리

### TC-ADMIN-REFUND-001: 환불 목록 조회

**우선순위**: 최상  
**관련 API**: `GET /api/v1/admin/refunds`

### 요청
```
GET /api/v1/admin/refunds?page=0&size=20
GET /api/v1/admin/refunds?status=PENDING
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] status 필터 동작 (PENDING / APPROVED / REJECTED)
- [ ] 각 환불에 refundId, orderId, userId, reason, status 포함

---

### TC-ADMIN-REFUND-002: 환불 승인 (핵심 플로우)

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/refunds/{refundId}/approve`

### 사전 상태
- 환불 status: `PENDING`

### 요청
```
PATCH /api/v1/admin/refunds/{REFUND_ID}/approve
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 승인 후 확인 항목
- [ ] 200 응답
- [ ] 환불 status: `APPROVED`
- [ ] PortOne API를 통한 자동 환불 처리 확인 (실제 결제 취소)
- [ ] 구매자(User B)에게 환불 완료 알림 발송
- [ ] 주문 status 변경 확인 (CANCELLED 또는 REFUNDED)
- [ ] 연관 정산 status 처리 확인 (정산 취소 여부)

---

### TC-ADMIN-REFUND-003: 환불 거절

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/refunds/{refundId}/reject`

### 사전 상태
- 환불 status: `PENDING`

### 요청
```json
PATCH /api/v1/admin/refunds/{REFUND_ID}/reject
Authorization: Bearer {ACCESS_TOKEN_ADMIN}

{
  "rejectionReason": "환불 정책 기준에 해당하지 않습니다. (단순 변심)"
}
```

### 거절 후 확인 항목
- [ ] 200 응답
- [ ] 환불 status: `REJECTED`
- [ ] rejectionReason 저장 확인
- [ ] 구매자(User B)에게 거절 알림 발송
- [ ] rejectionReason 없이 거절 시도 → 400

---

## 정산 관리

### TC-ADMIN-SETTLEMENT-001: 정산 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/admin/settlements`

### 요청
```
GET /api/v1/admin/settlements
GET /api/v1/admin/settlements?status=PENDING
GET /api/v1/admin/settlements?status=COMPLETED
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] 각 정산에 settlementUid, userId, settlementAmount, status 포함
- [ ] status 필터 동작

---

### TC-ADMIN-SETTLEMENT-002: 정산 완료 처리 (핵심 플로우)

**우선순위**: 최상  
**관련 API**: `PATCH /api/v1/admin/settlements/{settlementUid}/complete`

### 사전 상태
- 정산 status: `PENDING`

### 요청
```
PATCH /api/v1/admin/settlements/{SETTLEMENT_UID}/complete
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 완료 처리 후 확인 항목
- [ ] 200 응답
- [ ] 정산 status: `COMPLETED`
- [ ] 판매자(User A)에게 정산 완료 알림 발송
- [ ] `GET /api/v1/settlements/me` 조회 시 반영 확인
- [ ] 이미 COMPLETED인 정산 재처리 시 400/409

---

## 주문 관리

### TC-ADMIN-ORDER-001: 주문 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/admin/orders`

### 요청
```
GET /api/v1/admin/orders
GET /api/v1/admin/orders?status=PENDING_PAYMENT
GET /api/v1/admin/orders?status=PAYMENT_COMPLETED
GET /api/v1/admin/orders?buyerId={USER_B_ID}
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] 전체 주문 목록 (상태 필터 가능)

---

## 레퍼런스 데이터 관리

### TC-ADMIN-REF-001: 포켓몬 CRUD

```
GET    /api/v1/admin/pokemon
POST   /api/v1/admin/pokemon    { "name": "Pikachu", "nameKo": "피카츄" }
PATCH  /api/v1/admin/pokemon/{id}/name-ko    { "nameKo": "피카츄" }
DELETE /api/v1/admin/pokemon/{id}
```

### 확인 항목
- [ ] 포켓몬 생성 후 카드 등록 시 선택 가능 여부
- [ ] 삭제 시 연관 카드 존재하는 경우 처리 확인

---

### TC-ADMIN-REF-002: 시리즈 CRUD

```
GET    /api/v1/admin/series
POST   /api/v1/admin/series    { "name": "Base Set", "nameKo": "기본판" }
PATCH  /api/v1/admin/series/{id}/name-ko
DELETE /api/v1/admin/series/{id}
```

### 확인 항목
- [ ] 생성 후 공개 API(`GET /api/v1/series`)에 노출
- [ ] 세트가 연결된 시리즈 삭제 시 처리 확인

---

### TC-ADMIN-REF-003: 세트 CRUD

```
GET    /api/v1/admin/sets
POST   /api/v1/admin/sets    { "seriesId": ..., "name": "Jungle", "nameKo": "정글" }
PATCH  /api/v1/admin/sets/{id}/name-ko
DELETE /api/v1/admin/sets/{id}
```

### 확인 항목
- [ ] 생성 후 공개 API(`GET /api/v1/sets?seriesId=...`)에 노출
- [ ] 카드가 연결된 세트 삭제 시 처리 확인

---

## AI 및 Elasticsearch 관리

### TC-ADMIN-AI-001: 벡터스토어 전체 리인덱싱

**우선순위**: 중  
**관련 API**: `POST /api/v1/admin/ai/reindex`

### 요청
```
POST /api/v1/admin/ai/reindex
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 202 Accepted 응답 (비동기)
- [ ] 리인덱싱 완료 후 AI 어시스턴트의 RAG 응답 품질 변화 확인

---

### TC-ADMIN-ES-001: DB → ES 마이그레이션

**우선순위**: 상  
**관련 API**: `POST /api/v1/admin/es-migrate`

### 요청
```
POST /api/v1/admin/es-migrate
Authorization: Bearer {ACCESS_TOKEN_ADMIN}
```

### 확인 항목
- [ ] 200 응답
- [ ] 마이그레이션 후 카드 검색 기능 동작 확인
  - `GET /api/v1/cards?keyword=피카츄` 결과 확인
- [ ] 마이그레이션 후 경매 검색 동작 확인

---

### TC-ADMIN-ES-002: ES 인덱스 별칭 초기 설정 (최초 1회)

**관련 API**: `POST /api/v1/admin/es-alias-setup`

### 확인 항목
- [ ] 200 응답
- [ ] 이미 설정된 경우 오류 또는 멱등성 처리

---

### TC-ADMIN-ES-003: 제로다운타임 리인덱싱

**관련 API**: `POST /api/v1/admin/es-reindex`

### 확인 항목
- [ ] 202 Accepted
- [ ] 리인덱싱 진행 중에 검색 API 정상 동작 (무중단 확인)
- [ ] 리인덱싱 완료 후 alias 전환 확인

---

## 내부 통계 API (Internal)

### TC-INTERNAL-001: 일일 통계 조회

**우선순위**: 중  
**관련 API**: `GET /internal/stats/daily`  
**인증 방식**: `X-Internal-Token` 헤더 (InternalTokenAuthFilter)

### 요청
```
GET /internal/stats/daily
X-Internal-Token: {INTERNAL_TOKEN}
```

### 확인 항목
- [ ] 200 응답
- [ ] 일일 통계 데이터 (신규 가입, 경매 수, 거래 금액 등)
- [ ] 토큰 없이 접근 시 401 반환

---

## 어드민 권한 검증 (공통)

### TC-ADMIN-AUTH-001: 일반 사용자의 어드민 API 접근 차단

모든 `/api/v1/admin/**` 엔드포인트에 대해:

```
GET /api/v1/admin/cards/requests
Authorization: Bearer {ACCESS_TOKEN_A}  ← 일반 사용자 토큰
```

### 확인 항목
- [ ] 403 Forbidden 반환
- [ ] 로그인하지 않은 경우 401 반환

---

## 어드민 전체 승인 플로우 요약

```
[카드 승인 플로우]
User → POST /cards/upload (PENDING)
         ↓
Admin → GET /admin/cards/requests (목록 확인)
         ↓
Admin → PATCH /admin/cards/{id}/approve (APPROVED) 또는 /reject (REJECTED)
         ↓
User → GET /notifications (승인/거절 알림 수신)

[경매 검수 플로우]
User → POST /auctions (PENDING_INSPECTION)
         ↓
Admin → GET /admin/auctions?status=PENDING_INSPECTION (목록 확인)
         ↓
Admin → PATCH /admin/auctions/{id}/inspection (ACTIVE) 또는 (CANCELLED)
         ↓
User → GET /notifications (검수 결과 알림 수신)

[환불 처리 플로우]
User → POST /refunds (PENDING)
         ↓
Admin → GET /admin/refunds?status=PENDING (목록 확인)
         ↓
Admin → PATCH /admin/refunds/{id}/approve (APPROVED) 또는 /reject (REJECTED)
         ↓ (승인 시)
PortOne 자동 환불 처리
         ↓
User → GET /notifications (환불 결과 알림 수신)

[정산 완료 플로우]
결제 완료 → 정산 자동 생성 (PENDING)
         ↓
Admin → GET /admin/settlements?status=PENDING (목록 확인)
         ↓
Admin → PATCH /admin/settlements/{uid}/complete (COMPLETED)
         ↓
Seller → GET /notifications (정산 완료 알림 수신)
```

---

## 체크리스트 요약

| TC | 설명 | 우선순위 | 결과 |
|----|------|----------|------|
| TC-ADMIN-CARD-001 | 카드 신청 목록 조회 | 최상 | ⬜ |
| TC-ADMIN-CARD-002 | 카드 승인 + 알림 확인 | 최상 | ⬜ |
| TC-ADMIN-CARD-003 | 카드 거절 + 사유 + 알림 | 최상 | ⬜ |
| TC-ADMIN-CARD-004 | 카드 정보 수정 | 중 | ⬜ |
| TC-ADMIN-CARD-005 | 카드 삭제 | 중 | ⬜ |
| TC-ADMIN-CARD-006 | TCGdex 동기화 | 중 | ⬜ |
| TC-ADMIN-CARD-007 | S3 이미지 마이그레이션 | 낮음 | ⬜ |
| TC-ADMIN-AUCTION-001 | 경매 목록 조회 | 상 | ⬜ |
| TC-ADMIN-AUCTION-002 | 경매 검수 통과 + 입찰 가능 확인 | 최상 | ⬜ |
| TC-ADMIN-AUCTION-003 | 경매 검수 거절 + 알림 | 최상 | ⬜ |
| TC-ADMIN-AUCTION-004 | 경매 강제 취소 | 상 | ⬜ |
| TC-ADMIN-USER-001 | 사용자 목록 + 필터 | 상 | ⬜ |
| TC-ADMIN-USER-002 | 사용자 입찰 차단 (**엔드포인트 노출 여부 확인 필요**) | 상 | ⬜ |
| TC-ADMIN-REFUND-001 | 환불 목록 조회 | 최상 | ⬜ |
| TC-ADMIN-REFUND-002 | 환불 승인 + PortOne 환불 | 최상 | ⬜ |
| TC-ADMIN-REFUND-003 | 환불 거절 + 사유 + 알림 | 최상 | ⬜ |
| TC-ADMIN-SETTLEMENT-001 | 정산 목록 조회 | 상 | ⬜ |
| TC-ADMIN-SETTLEMENT-002 | 정산 완료 처리 + 알림 | 최상 | ⬜ |
| TC-ADMIN-ORDER-001 | 주문 목록 조회 | 상 | ⬜ |
| TC-ADMIN-REF-001 | 포켓몬 CRUD | 중 | ⬜ |
| TC-ADMIN-REF-002 | 시리즈 CRUD | 중 | ⬜ |
| TC-ADMIN-REF-003 | 세트 CRUD | 중 | ⬜ |
| TC-ADMIN-AI-001 | AI 벡터스토어 리인덱싱 | 중 | ⬜ |
| TC-ADMIN-ES-001 | DB → ES 마이그레이션 | 상 | ⬜ |
| TC-ADMIN-ES-002 | ES 별칭 초기 설정 | 중 | ⬜ |
| TC-ADMIN-ES-003 | 제로다운타임 리인덱싱 | 중 | ⬜ |
| TC-INTERNAL-001 | 일일 통계 조회 | 중 | ⬜ |
| TC-ADMIN-AUTH-001 | 어드민 API 권한 차단 검증 | 최상 | ⬜ |
