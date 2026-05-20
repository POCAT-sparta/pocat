# 🃏 POCAT API 명세서

> **Pokemon Card Trading Platform** | 7조 로켓단  
> Base URL: `https://{host}/api/v1`  
> 인증 방식: `Authorization: Bearer {accessToken}` (JWT)  
> 공통 응답 포맷: `ApiResponse<T>` 래핑

---

## 📌 목차

1. [공통 규격](#0-공통-규격)
2. [인증 (Auth)](#1-인증-auth)
3. [유저 (User)](#2-유저-user)
4. [카드 (Card)](#3-카드-card)
5. [경매 (Auction)](#4-경매-auction)
6. [주문 (Order)](#5-주문-order)
7. [결제 (Payment)](#6-결제-payment)
8. [환불 (Refund)](#7-환불-refund)
9. [정산 (Settlement)](#8-정산-settlement)
10. [커뮤니티 - 자유게시판 (Free Post)](#9-커뮤니티---자유게시판-free-post)
11. [커뮤니티 - 거래게시판 (Trade Post)](#10-커뮤니티---거래게시판-trade-post)
12. [댓글 (Comment)](#11-댓글-comment)
13. [채팅 (Chat)](#12-채팅-chat)
14. [알림 (Notification)](#13-알림-notification)
15. [찜 (Like)](#14-찜-like)

---

## 0. 공통 규격

### 공통 응답 구조

```json
{
  "status": "SUCCESS",
  "data": { ... },
  "message": ""
}
```

### 공통 에러 응답

```json
{
  "status": "ERROR",
  "data": null,
  "message": "에러 메시지"
}
```

### HTTP 상태 코드 규약

| 상태 코드 | 의미 |
|---|---|
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 요청 값 검증 실패 |
| `401 Unauthorized` | 미인증 요청 |
| `403 Forbidden` | 권한 없음 |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 비즈니스 정책 충돌 |
| `429 Too Many Requests` | 요청 횟수 초과 |
| `500 Internal Server Error` | 서버 오류 |

### 권한 레벨

| 레벨 | 설명 |
|---|---|
| `PUBLIC` | 인증 불필요 |
| `USER` | 로그인 필요 |
| `ADMIN` | 관리자 전용 (`/api/v1/admin/**`) |

---

## 1. 인증 (Auth)

> 담당자: 정태규

### 1.1 회원가입

- **POST** `/api/v1/auth/signup`
- **권한**: `PUBLIC`

**Request Body**

```json
{
  "email": "user@example.com",
  "password": "password123!",
  "nickname": "포켓몬마스터",
  "phone": "010-1234-5678"
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "포켓몬마스터"
  },
  "message": "회원가입 성공"
}
```

---

### 1.2 로그인

- **POST** `/api/v1/auth/login`
- **권한**: `PUBLIC`

**Request Body**

```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci..."
  },
  "message": "로그인 성공"
}
```

---

### 1.3 토큰 재발급 (RTR)

- **POST** `/api/v1/auth/reissue`
- **권한**: `PUBLIC`
- **설명**: Refresh Token Rotation 방식. 기존 Refresh Token은 즉시 무효화되고 새 토큰 쌍 발급

**Request Body**

```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci..."
  },
  "message": "토큰 재발급 성공"
}
```

---

### 1.4 로그아웃

- **POST** `/api/v1/auth/logout`
- **권한**: `USER`
- **설명**: Access Token을 Redis 블랙리스트에 등록하여 재사용 차단

**Request Header**

```
Authorization: Bearer {accessToken}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": "로그아웃 성공"
}
```

---

## 2. 유저 (User)

> 담당자: 이석형

### 2.1 내 정보 조회

- **GET** `/api/v1/users/me`
- **권한**: `USER`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "포켓몬마스터",
    "phone": "010-1234-5678",
    "role": "USER",
    "bankName": "카카오뱅크",
    "bankAccount": "1234-56-789012",
    "address": "서울시 강남구...",
    "unpaidStrike": 0,
    "isBidBlocked": false,
    "hasBillingKey": true,
    "createdAt": "2026-01-01T00:00:00"
  },
  "message": ""
}
```

---

### 2.2 내 정보 수정

- **PATCH** `/api/v1/users/me`
- **권한**: `USER`

**Request Body** (변경할 필드만 포함)

```json
{
  "nickname": "새닉네임",
  "phone": "010-9999-8888",
  "address": "서울시 서초구..."
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "nickname": "새닉네임",
    "phone": "010-9999-8888",
    "address": "서울시 서초구..."
  },
  "message": "내 정보 수정 성공"
}
```

---

### 2.3 빌링키 등록

- **POST** `/api/v1/users/me/billing-key`
- **권한**: `USER`
- **설명**: PortOne V2에서 발급받은 빌링키 등록. 경매 입찰 전 필수 등록

**Request Body**

```json
{
  "billingKey": "billing_key_from_portone"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": "빌링키 등록 성공"
}
```

---

### 2.4 빌링키 삭제

- **DELETE** `/api/v1/users/me/billing-key`
- **권한**: `USER`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": "빌링키 삭제 성공"
}
```

---

### 2.5 계좌 등록/수정

- **PUT** `/api/v1/users/me/bank-account`
- **권한**: `USER`
- **설명**: 판매자 정산용 계좌. 마이페이지에서 원하는 시점에 등록·수정 가능

**Request Body**

```json
{
  "bankName": "카카오뱅크",
  "bankAccount": "1234-56-789012"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": "계좌 등록/수정 성공"
}
```

---

### 2.6 전체 유저 목록 조회 (관리자)

- **GET** `/api/v1/admin/users`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `keyword` | String | N | 이메일/닉네임 검색 |
| `isBidBlocked` | boolean | N | 입찰 차단 여부 필터 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "email": "user@example.com",
        "nickname": "포켓몬마스터",
        "role": "USER",
        "unpaidStrike": 0,
        "isBidBlocked": false,
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

## 3. 카드 (Card)

> 담당자: 정태규

### 3.1 카드 목록 조회 (검색/필터)

- **GET** `/api/v1/cards`
- **권한**: `PUBLIC`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | String | N | 카드명 검색 |
| `series` | String | N | 시리즈 필터 |
| `setName` | String | N | 세트명 필터 |
| `rarity` | String | N | 희귀도 필터 |
| `grade` | String | N | 등급 필터 (PSA_10, PSA_9, BGS_10) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "tcgdexId": "swsh1-1",
        "name": "리자몽",
        "series": "소드&쉴드",
        "setName": "칼과방패",
        "cardNumber": "001",
        "rarity": "Rare Holo",
        "grade": "PSA_10",
        "imageUrl": "https://...",
        "source": "TCGDEX",
        "status": "ACTIVE"
      }
    ],
    "totalElements": 500,
    "totalPages": 25,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 3.2 카드 상세 조회

- **GET** `/api/v1/cards/{cardId}`
- **권한**: `PUBLIC`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `cardId` | Long | 카드 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "tcgdexId": "swsh1-1",
    "name": "리자몽",
    "series": "소드&쉴드",
    "setName": "칼과방패",
    "cardNumber": "001",
    "rarity": "Rare Holo",
    "grade": "PSA_10",
    "imageUrl": "https://...",
    "source": "TCGDEX",
    "status": "ACTIVE",
    "createdAt": "2026-01-01T00:00:00"
  },
  "message": ""
}
```

---

### 3.3 카드 등록 요청 (유저)

- **POST** `/api/v1/cards`
- **권한**: `USER`
- **설명**: 유저가 카드를 등록 요청. 최초 상태는 `PENDING`

**Request Body**

```json
{
  "tcgdexId": "swsh1-1",
  "name": "리자몽",
  "series": "소드&쉴드",
  "setName": "칼과방패",
  "cardNumber": "001",
  "rarity": "Rare Holo",
  "grade": "PSA_10",
  "imageUrl": "https://...",
  "source": "TCGDEX"
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "name": "리자몽",
    "grade": "PSA_10",
    "status": "PENDING"
  },
  "message": "카드 등록 요청 완료"
}
```

---

### 3.4 내 카드 요청 목록 조회

- **GET** `/api/v1/cards/my-requests`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 10,
        "name": "리자몽",
        "grade": "PSA_10",
        "status": "PENDING",
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "totalElements": 3,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 3.5 카드 등록 승인 (관리자)

- **PATCH** `/api/v1/admin/cards/{cardId}/approve`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `cardId` | Long | 카드 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "status": "ACTIVE"
  },
  "message": "카드 등록 승인 완료"
}
```

---

### 3.6 카드 등록 거절 (관리자)

- **PATCH** `/api/v1/admin/cards/{cardId}/reject`
- **권한**: `ADMIN`

**Request Body**

```json
{
  "rejectReason": "이미지 불명확"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "status": "PENDING"
  },
  "message": "카드 등록 거절 완료"
}
```

---

### 3.7 카드 수정 (관리자)

- **PATCH** `/api/v1/admin/cards/{cardId}`
- **권한**: `ADMIN`

**Request Body** (변경할 필드만 포함)

```json
{
  "name": "리자몽 (수정)",
  "rarity": "Rare Holo V"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "name": "리자몽 (수정)"
  },
  "message": "카드 수정 완료"
}
```

---

### 3.8 카드 삭제 (관리자)

- **DELETE** `/api/v1/admin/cards/{cardId}`
- **권한**: `ADMIN`

**Response** `204 No Content`

---

### 3.9 TCGdex 연동 동기화 (관리자)

- **POST** `/api/v1/admin/cards/sync`
- **권한**: `ADMIN`
- **설명**: TCGdex 외부 API를 통해 카드 데이터 동기화

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "syncedCount": 150
  },
  "message": "TCGdex 동기화 완료"
}
```

---

### 3.10 등록 요청 전체 목록 (관리자)

- **GET** `/api/v1/admin/cards/requests`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 (PENDING, ACTIVE) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 10,
        "userId": 1,
        "userNickname": "포켓몬마스터",
        "name": "리자몽",
        "grade": "PSA_10",
        "status": "PENDING",
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "totalElements": 30,
    "totalPages": 2,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

## 4. 경매 (Auction)

> 담당자: 박소영

### 4.1 경매 목록 조회 (유저)

- **GET** `/api/v1/auctions`
- **권한**: `PUBLIC`

**Query Parameters**

| 파라미터     | 타입 | 필수 | 설명                       |
|----------|---|---|--------------------------|
| `keyword` | String | N | 카드 이름, 경매 제목, 시리즈명, 확장팩명, 카드번호 검색   |
| `series` | String | N | 카드 시리즈 필터                |
| `setName` | String | N | 카드 확장팩 이름 필터             |
| `grade`  | String | N | 카드 등급 필터                 |
| `page`   | int | N | 페이지 번호 (default: 0)      |
| `size`   | int | N | 페이지 크기 (default: 20)     |
| `sort`   | String | N | 정렬 기준 (endedAt,asc / createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "auctionId": 1,
        "title": "PSA 10 리자몽 경매",
        "cardId": 1,
        "cardName": "리자몽",
        "grade": "PSA_10",
        "cardImageUrl": "https://...",
        "startingPrice": 100000,
        "buyoutPrice": 1000000,
        "highestPrice": 200000,
        "status": "ACTIVE",
        "startedAt": "2026-05-01T00:00:00",
        "endedAt": "2026-05-04T00:00:00"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 4.2 경매 목록 조회 (관리자)

- **GET** `/api/v1/admin/auctions`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터     | 타입 | 필수 | 설명                                |
|----------|---|---|-----------------------------------|
| `keyword` | String | N | 카드 이름, 경매 제목, 시리즈명, 확장팩명, 카드번호 검색 |
| `series` | String | N | 카드 시리즈 필터                         |
| `setName` | String | N | 카드 확장팩 이름 필터                      |
| `grade`  | String | N | 카드 등급 필터                          |
| `status` | String | N | 경매 상태 필터                          |
| `page`   | int | N | 페이지 번호 (default: 0)               |
| `size`   | int | N | 페이지 크기 (default: 20)              |
| `sort`   | String | N | 정렬 기준 (endedAt,asc / createdAt,desc) |

**Response** `200 OK` (4.1 응답 구조 동일, 관리자 전용 필드 추가)

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "auctionId": 42,
        "title": "PSA 10 피카츄 1세대 경매",
        "cardId": 1,
        "cardName": "피카츄",
        "grade": "PSA_10",
        "cardImageUrl": "https://example.com/card.jpg",
        "startingPrice": 100000,
        "buyoutPrice": 1000000,
        "highestPrice": 150000,
        "status": "ACTIVE",
        "startedAt": "2026-05-15T12:00:00",
        "endedAt": "2026-05-18T12:00:00",
        "createdAt": "2026-05-15T11:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```


---

### 4.3 내 경매 목록 조회 (판매자)

- **GET** `/api/v1/auctions/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK` (4.1 응답 구조 동일, 관리자 전용 필드 추가)

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "auctionId": 42,
        "title": "PSA 10 피카츄 1세대 경매",
        "cardId": 1,
        "cardName": "피카츄",
        "grade": "PSA_10",
        "cardImageUrl": "https://example.com/card.jpg",
        "startingPrice": 100000,
        "highestPrice": 150000,
        "buyoutPrice": 1000000,
        "status": "ACTIVE",
        "startedAt": "2026-05-15T12:00:00",
        "endedAt": "2026-05-18T12:00:00",
        "createdAt": "2026-05-15T11:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "page": 0,
    "size": 20
  },
  "message": ""
}
```

---

### 4.4 경매 상세 조회

- **GET** `/api/v1/auctions/{auctionId}`
- **권한**: `PUBLIC`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `auctionId` | Long | 경매 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "sellerId": 2,
    "sellerNickname": "카드마스터",
    "title": "PSA 10 리자몽 경매",
    "description": "2016년 출시 원판 리자몽",
    "cardId": 1,
    "cardName": "리자몽",
    "grade": "PSA_10",
    "cardImageUrl": "https://...",
    "startingPrice": 100000,
    "buyoutPrice": 1000000,
    "highestPrice": 200000,
    "highestBidderId": 3,
    "highestBidderNickname": "피카헌터",
    "status": "ACTIVE",
    "startedAt": "2026-05-01T00:00:00",
    "endedAt": "2026-05-04T00:00:00",
    "likeCount": 15,
    "isLiked": true
  },
  "message": ""
}
```

---

### 4.5 경매 등록 (판매자)

- **POST** `/api/v1/auctions`
- **권한**: `USER`

**Request Body**

```json
{
  "cardId": 1,
  "title": "PSA 10 리자몽 경매",
  "description": "2016년 출시 원판 리자몽",
  "startingPrice": 100000,
  "buyoutPrice": 1000000
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "title": "PSA 10 리자몽 경매",
    "status": "PENDING"
  },
  "message":""
}
```

---

### 4.6 경매 수정 (PENDING 상태만)

- **PATCH** `/api/v1/auctions/{auctionId}`
- **권한**: `USER` (본인)

**Request Body** (변경할 필드만 포함)

```json
{
  "title": "수정된 경매 제목",
  "description": "수정된 설명",
  "startingPrice": 150000,
  "buyoutPrice": 1200000
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "title": "수정된 경매 제목",
    "description": "수정된 설명",
    "startingPrice": 150000,
    "buyoutPrice": 1200000,
    "status": "PENDING"
  },
  "message": ""
}
```

---

### 4.7 경매 취소 (판매자)

- **PATCH** `/api/v1/auctions/{auctionId}/cancel`
- **권한**: `USER` (본인)

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "status": "CANCELLED"
  },
  "message": ""
}
```

---

### 4.8 입찰 (빌링키 필수)

- **POST** `/api/v1/auctions/{auctionId}/bids`
- **권한**: `USER`
- **설명**: Redis 분산락 적용. 빌링키 미등록 / 입찰 차단 사용자 입찰 불가. 현재 최고가 초과 금액만 입찰 가능

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `auctionId` | Long | 경매 ID |

**Request Body**

```json
{
  "bidPrice": 250000
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "bidId": 10,
    "auctionId": 1,
    "bidPrice": 250000,
    "status": "ACTIVE"
  },
  "message": ""
}
```

**Error Cases**

| 상태 코드 | 사유                              |
|-------|---------------------------------|
| `400` | 입력값이 올바르지 않은 경우                 |
| `403` | 빌링키 미등록 / 본인 경매에 입찰 시도 시 / 입찰 차단 사용자 |
| `404` | 경매 미존재                          |
| `409` | 경매 비활성 상태, 락 획득 실패, 최고가보다 낮은 입찰 |


---

### 4.9 내 입찰 목록 조회 (구매자)

- **GET** `/api/v1/bids/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 (ACTIVE, WON, LOST, CANCELLED) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "bidId": 10,
        "auctionId": 1,
        "auctionTitle": "PSA 10 리자몽 경매",
        "bidPrice": 250000,
        "status": "ACTIVE",
        "createdAt": "2026-05-02T10:00:00"
      }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 4.10 즉시 구매

- **POST** `/api/v1/auctions/{auctionId}/buyout`
- **권한**: `USER`
- **설명**: 즉시구매가 이상 금액으로 입찰 시 경매가 `PAYMENT_PENDING` 상태로 전환되고 빌링키 자동결제 시도

**Request Body**

```json
{
  "bidPrice": 1000000
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "bidId": 10,
    "orderUid": "ORD_20260519_abc123",
    "orderStatus": "PAYMENT_COMPLETED",
    "paymentUid": "PAY_20260519_xyz789",
    "paymentStatus": "COMPLETED",
    "paidAmount": 1000000,
    "auctionStatus": "ENDED",
    "purchasedAt": "2026-05-19T12:00:00"
  },
  "message": ""
}
```

---

### 4.11 경매 입찰 내역 조회

- **GET** `/api/v1/auctions/{auctionId}/bids`
- **권한**: `PUBLIC`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "bidId": 10,
        "bidderId": 23,
        "bidderNickname": "포켓몬마스터",
        "bidPrice": 250000,
        "createdAt": "2026-05-02T10:00:00"
      }
    ],
    "totalElements": 10,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 4.12 경매 검수 승인 / 거절 (관리자)

- **PATCH** `/api/v1/admin/auctions/{auctionId}/inspect`
- **권한**: `ADMIN`

**Request Body**

```json
{
  "result": "PASSED",
  "rejectReason": null
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "status": "ACTIVE"
  },
  "message": ""
}
```

---

### 4.13 경매 강제 취소 (관리자)

- **PATCH** `/api/v1/admin/auctions/{auctionId}/cancel`
- **권한**: `ADMIN`

**Request Body**

```json
{
  "reason": "규정 위반"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "status": "CANCELLED"
  },
  "message": ""
}
```

---

## 5. 주문 (Order)

> 담당자: 이승현

### 5.1 주문 내역 목록 조회 (관리자)

- **GET** `/api/v1/admin/orders`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 주문 상태 필터 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 5,
        "orderUid": "ORDER-UUID-001",
        "auctionId": 1,
        "buyerNickname": "포켓몬마스터",
        "sellerNickname": "카드마스터",
        "finalPrice": 250000,
        "status": "PAYMENT_COMPLETED",
        "deliveryStatus": "PREPARING",
        "createdAt": "2026-05-04T12:00:00"
      }
    ],
    "totalElements": 20,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 5.2 내 주문 목록

- **GET** `/api/v1/orders/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 주문 상태 필터 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

---

### 5.3 주문 상세 조회

- **GET** `/api/v1/orders/{orderUid}`
- **권한**: `USER` (본인 주문) / `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `orderUid` | String | 주문 고유 식별자 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 5,
    "orderUid": "ORDER-UUID-001",
    "auctionId": 1,
    "auctionTitle": "PSA 10 리자몽 경매",
    "buyerId": 3,
    "sellerId": 2,
    "finalPrice": 250000,
    "status": "PAYMENT_COMPLETED",
    "deliveryStatus": "PREPARING",
    "snapshot": {
      "finalPrice": 250000,
      "feeRate": 0.05,
      "fee": 12500,
      "sellerAmount": 237500
    },
    "createdAt": "2026-05-04T12:00:00"
  },
  "message": ""
}
```

---

### 5.4 주문 취소 / 결제 취소 / 환불

- **PATCH** `/api/v1/orders/{orderUid}/cancel`
- **권한**: `USER` (본인)

**Request Body**

```json
{
  "reason": "단순 변심"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "orderUid": "ORDER-UUID-001",
    "status": "CANCELLED"
  },
  "message": "주문 취소 완료"
}
```

---

## 6. 결제 (Payment)

> 담당자: 이재민

### 6.1 결제 요청 (PG 직접결제, 낙찰 실패 후 1h)

- **POST** `/api/v1/payments`
- **권한**: `USER`
- **설명**:
  - 낙찰 후 자동결제(billingKey)가 실패한 경우, 구매자가 PG 직접결제를 시작하기 전에 서버에 결제 레코드를 미리 생성하고 `paymentUid`를 발급받는 API.
  - 클라이언트는 이 API에서 받은 `paymentUid`와 `amount`를 PortOne SDK에 전달하여 결제창을 호출한다.
  - 이렇게 하면 서버가 금액을 먼저 확정해두기 때문에 클라이언트의 금액 위변조를 원천 차단할 수 있다.
  - **이 API는 PG 직접결제 전용이다. 빌링키 자동결제는 서버 내부에서 처리되므로 호출하지 않는다.**
    - 주문 상태가 `PAYMENT_FAILED`여야만 요청 가능
    - 결제 실패 시각 기준 **1시간 초과** 시 요청 불가
    - 요청자가 해당 주문의 `buyer_id`와 동일해야 함
    - 동일 주문에 `PENDING` 상태 결제 레코드가 이미 존재하면 신규 생성하지 않고 기존 `paymentUid` 반환 (중복 방지)
    - 금액은 클라이언트 요청값을 신뢰하지 않고 서버가 `orders.final_price`에서 직접 확정

**Request Body**

```json
{
  "orderId": 1
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "code": "201",
  "data": {
    "paymentUid": "pocat-payment-a1b2c3d4",
    "orderId": 1,
    "amount": 150000,
    "status": "PENDING",
    "createdAt": "2026-05-15T14:30:00.000000"
  },
  "timestamp": "2026-05-15T14:30:00.000000"
}
```

---

### 6.2 결제 확정 요청

- **PATCH** `/api/v1/payments/{paymentUid}`
- **권한**: `USER`
- **설명**: 
  - 클라이언트가 PortOne SDK로 결제창에서 결제를 완료한 뒤, 서버에 결제 확정을 요청하는 API.
  - 서버는 전달받은 `paymentUid`로 PortOne API를 직접 조회하여 결제 상태·금액을 검증하고, 검증 통과 시 `payments`와 `orders` 상태를 업데이트한다. 
  - 이 흐름은 빠른 UX를 위한 경로(Client Confirm)이며, Webhook이 안전장치로 병렬 동작한다.

```json
클라이언트 결제 완료
  ↓
PATCH /api/v1/payments/{paymentUid}   ← 이 API
  ↓
서버가 PortOne API 조회 (paymentUid)
  ↓
금액 검증 → DB 업데이트
(Webhook이 먼저 처리했으면 멱등성 체크 후 200 반환)
```
- 요청자가 해당 결제의 주문 `buyer_id`와 동일해야 함
- 이미 `COMPLETED` 또는 `REFUNDED` 상태면 재처리 없이 현재 상태 그대로 200 반환 (멱등성)
- PortOne 조회 결과 금액이 `payments.amount`와 불일치하면 결제 취소 후 실패 처리

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `paymentUid` | String | 결제 고유 식별자 |

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "paymentUid": "pocat-payment-a1b2c3d4",
    "orderId": 1,
    "amount": 150000,
    "paymentType": "PG_DIRECT",
    "paymentMethod": "CARD",
    "status": "COMPLETED",
    "paidAt": "2026-05-15T14:32:00.000000",
    "createdAt": "2026-05-15T14:30:00.000000"
  },
  "timestamp": "2026-05-15T14:32:05.000000"
}
```

---

### 6.3 결제 상세 조회

- **GET** `/api/v1/payments/{paymentUid}`
- **권한**: `USER` (본인) / `ADMIN`
- **설명**:
  - `paymentUid`로 특정 결제 건의 상세 정보를 조회하는 API.
  - 요청자가 해당 결제의 주문 `buyer_id` 또는 `ADMIN`이어야 함

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "paymentUid": "portone_abc123xyz",
    "orderId": 42,
    "amount": 150000,
    "paymentType": "BILLING_KEY",
    "paymentMethod": "CARD",
    "status": "COMPLETED",
    "paidAt": "2026-05-15T13:00:00.000000",
    "createdAt": "2026-05-15T12:59:58.000000"
  },
  "timestamp": "2026-05-15T13:05:00.000000"
}
```

---

### 6.4 PortOne Webhook 수신 (서명 검증)

- **POST** `/api/v1/payments/webhook`
- **권한**: `PUBLIC` (PortOne 서버 → 우리 서버)
- **설명**:
  - PortOne이 결제 이벤트 발생 시 서버로 직접 전송하는 Webhook을 수신하는 API. 
  - Client Confirm(PATCH)과 **동일한 결제 확정 로직을 공유**하며, 둘 중 먼저 도착한 쪽이 처리하고 나머지는 멱등성 체크로 스킵한다. 최종 정합성은 이 Webhook이 보장한다.
  - **빌링키 자동결제의 결과도 이 Webhook으로 수신한다.** 자동결제는 Client Confirm 경로가 없으므로 Webhook이 유일한 수신 경로다.

```json
[PG 직접결제]
Client Confirm(PATCH) ──┐
                        ├── 먼저 도착한 쪽 처리, 나머지 멱등성 스킵
Webhook ────────────────┘

[빌링키 자동결제]
Webhook ───── 유일한 수신 경로 ───── 결제 확정 처리
```
- **별도 사용자 인증 없음** (PortOne 서버가 직접 호출)
- `X-PortOne-Signature` 헤더 서명 검증 필수 → 실패 시 즉시 거절
- 서명 검증 통과 후 PortOne API를 재조회하여 실제 상태·금액 2차 검증
- **멱등성 보장**: 이미 최종 상태(`COMPLETED` / `FAILED` / `REFUNDED`)면 처리 없이 200 반환
- PortOne은 200을 받지 못하면 재전송하므로 반드시 200 반환

**Request Header**

```
X-PortOne-Signature: {서명값}
```

**Request Body**

```json
{
  "type": "Transaction.Paid",
  "timestamp": "2026-05-15T14:32:00.000000Z",
  "data": {
    "paymentId": "pocat-payment-a1b2c3d4",
    "transactionId": "tx_xyz789",
    "storeId": "store_pocat",
    "amount": {
      "total": 150000
    },
    "status": "PAID"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": null,
  "timestamp": "2026-05-15T14:32:01.000000"
}
```

---

## 7. 환불 (Refund)

> 담당자: 이재민

### 7.1 환불 요청

- **POST** `/api/v1/refunds`
- **권한**: `USER`
- **설명**: 
  - 결제 완료된 주문에 대해 구매자가 환불을 요청하는 API.
  - `orders.status`가 `PAYMENT_COMPLETED` / `SHIPPING` / `COMPLETED` 중 하나여야 요청 가능
  - 동일 주문에 `REQUESTED` 또는 `COMPLETED` 상태 환불이 이미 존재하면 중복 요청 불가
  - 실제 환불 처리(이체)는 관리자 승인 후 수동 진행 (PortOne 부분환불 미구현)
  - 환불 금액은 `payments.amount` 전액 자동 적용
  - 요청자가 해당 주문의 `buyer_id`와 동일해야 함

**Request Body**

```json
{
  "orderId": 1,
  "reason": "상품 상태 불량"
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "code": "201",
  "data": {
    "refundId": 7,
    "orderId": 42,
    "paymentId": 15,
    "amount": 150000,
    "reason": "상품 상태 불량",
    "status": "REQUESTED",
    "createdAt": "2026-05-15T15:00:00.000000"
  },
  "timestamp": "2026-05-15T15:00:00.000000"
}
```

- PortOne이 200을 받지 못하면 재전송한다. 처리 결과와 무관하게 서명 검증만 통과하면 200을 반환하고 내부 처리는 별도로 진행한다.

---

### 7.2 내 환불 내역 조회

- **GET** `/api/v1/refunds/me`
- **권한**: `USER`
- **설명**:
  - 로그인한 사용자의 환불 내역을 페이지네이션으로 조회하는 API.
    - `status` 쿼리 파라미터로 상태 필터링 가능 (미입력 시 전체 조회)
    - Offset 기반 페이징 (기본 10개, 생성일 내림차순)

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명                                        |
|---|---|---|-------------------------------------------|
|`status`|String|N| REQUESTED / COMPLETED / REJECTED / FAILED |
| `page` | int | N | 페이지 번호 (default: 0)                       |
| `size` | int | N | 페이지 크기 (default: 10)                      |

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "content": [
      {
        "refundId": 7,
        "orderId": 1,
        "amount": 150000,
        "reason": "상품 상태 불량",
        "status": "REQUESTED",
        "createdAt": "2026-05-15T15:00:00.000000",
        "updatedAt": "2026-05-15T15:00:00.000000"
      }
    ],
    "pageable": {...},
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "size": 10,
    "number": 0
  },
  "timestamp": "2026-05-15T15:05:00.000000"
}
```

---

### 7.3 환불 상세 조회

- **GET** `/api/v1/refunds/{refundId}`
- **권한**: `USER` (본인) / `ADMIN`
- **설명**: 
  - `refundId`로 특정 환불 건의 상세 정보를 조회하는 API.
    - 해당 환불의 주문 `buyer_id` 또는 `ADMIN`만 접근 가능

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "refundId": 7,
    "orderId": 1,
    "paymentId": 15,
    "amount": 150000,
    "reason": "상품 상태 불량",
    "status": "COMPLETED",
    "createdAt": "2026-05-15T15:00:00.000000",
    "updatedAt": "2026-05-15T16:00:00.000000"
  },
  "timestamp": "2026-05-15T16:05:00.000000"
}
```

---

### 7.4 환불 승인 (관리자)

- **PATCH** `/api/v1/admin/refunds/{refundId}/approve`
- **권한**: `ADMIN`
- **설명**: 
  - 관리자가 `REQUESTED` 상태의 환불을 승인하는 API.
  - ADMIN 권한 필수
  - 환불 상태가 `REQUESTED`여야만 처리 가능
  - 승인 시 3개 테이블 상태 일괄 변경
      - `refunds.status` → `COMPLETED`
      - `payments.status` → `REFUNDED`
      - `orders.status` → `REFUNDED`
  - 실제 금액 이체는 관리자가 별도 수동 처리 (PortOne 부분환불 미구현)

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "refundId": 7,
    "orderId": 1,
    "amount": 150000,
    "status": "COMPLETED",
    "updatedAt": "2026-05-15T16:00:00.000000"
  },
  "timestamp": "2026-05-15T16:00:00.000000"
}
```

---

### 7.5 환불 거절 (관리자)

- **PATCH** `/api/v1/admin/refunds/{refundId}/reject`
- **권한**: `ADMIN`
- **설명**: 
  - 관리자가 `REQUESTED` 상태의 환불을 거절하는 API.
    - ADMIN 권한 필수
    - 환불 상태가 `REQUESTED`여야만 처리 가능
    - 거절 시: `refunds.status` → `REJECTED`, `orders.status`는 기존 상태 유지

**Request Body**

```json
{
  "rejectReason": "환불 정책 기간 초과"
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "refundId": 7,
    "orderId": 42,
    "amount": 150000,
    "status": "REJECTED",
    "rejectReason": "환불 정책 기간 초과",
    "updatedAt": "2026-05-15T16:00:00.000000"
  },
  "timestamp": "2026-05-15T16:00:00.000000"
}
```

---

### 7.6 전체 환불 목록 (관리자)

- **GET** `/api/v1/admin/refunds`
- **권한**: `ADMIN`
- **설명**: 
  - 관리자가 전체 환불 내역을 페이지네이션으로 조회하는 API.
    - ADMIN 권한 필수
    - `status` 쿼리 파라미터로 상태 필터링 가능 (미입력 시 전체 조회)
    - Offset 기반 페이징 (기본 20개, 생성일 내림차순)
    - QueryDSL Projections로 DTO 직접 조회

**Query Parameters**

| 파라미터     | 타입     | 필수 | 설명 |
|----------|--------|---|---|
| `status` | String | N | 상태 필터 (REQUESTED, COMPLETED, REJECTED, FAILED) |
| `page`   | int    | N | 페이지 번호 (default: 0) |
| `size`   | int    | N | 페이지 크기 (default: 20) |
| `sort`   | String      | N | 정렬 기준 |

**Response** `200 OK`

```json
{
  "success": true,
  "code": "200",
  "data": {
    "content": [
      {
        "refundId": 7,
        "orderId": 42,
        "buyerNickname": "trainer_ash",
        "amount": 150000,
        "reason": "상품 상태 불량",
        "status": "REQUESTED",
        "createdAt": "2026-05-15T15:00:00.000000",
        "updatedAt": "2026-05-15T15:00:00.000000"
      }
    ],
    "pageable": {...},
    "totalElements": 35,
    "totalPages": 2,
    "last": false,
    "size": 20,
    "number": 0
  },
  "timestamp": "2026-05-15T16:05:00.000000"
}
```


---

## 8. 정산 (Settlement)

> 담당자: 이승현

### 8.1 내 정산 목록 조회 (판매자)

- **GET** `/api/v1/settlements/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 (PENDING, COMPLETED) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

---

### 8.2 정산 상세 조회 (판매자)

- **GET** `/api/v1/settlements/{settlementId}`
- **권한**: `USER` (본인) / `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "settlementId": 1,
    "orderId": 5,
    "totalPrice": 250000,
    "platformFee": 12500,
    "sellerAmount": 237500,
    "status": "COMPLETED",
    "settledAt": "2026-05-10T00:00:00"
  },
  "message": ""
}
```

---

### 8.3 전체 정산 목록 (관리자)

- **GET** `/api/v1/admin/settlements`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

---

### 8.4 정산 완료 처리 (관리자)

- **PATCH** `/api/v1/admin/settlements/{settlementId}/complete`
- **권한**: `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "settlementId": 1,
    "status": "COMPLETED",
    "settledAt": "2026-05-10T00:00:00"
  },
  "message": "정산 완료 처리됨"
}
```

---

## 9. 커뮤니티 - 자유게시판 (Free Post)

> 담당자: 이석형

### 9.1 내 자유 게시글 목록

- **GET** `/api/v1/posts/free/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "오늘 리자몽 득템했어요",
        "viewCount": 120,
        "commentCount": 5,
        "createdAt": "2026-05-01T00:00:00"
      }
    ],
    "totalElements": 10,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 9.2 자유게시판 목록 조회

- **GET** `/api/v1/posts/free`
- **권한**: `PUBLIC`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | String | N | 제목/내용 검색 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (createdAt,desc / viewCount,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "오늘 리자몽 득템했어요",
        "authorNickname": "포켓몬마스터",
        "viewCount": 120,
        "commentCount": 5,
        "createdAt": "2026-05-01T00:00:00"
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 9.3 자유게시판 상세 조회

- **GET** `/api/v1/posts/free/{freePostId}`
- **권한**: `PUBLIC`
- **설명**: 조회 시 `viewCount` 증가

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `freePostId` | Long | 자유게시글 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "title": "오늘 리자몽 득템했어요",
    "content": "드디어 PSA 10을 구했습니다...",
    "authorId": 1,
    "authorNickname": "포켓몬마스터",
    "viewCount": 121,
    "createdAt": "2026-05-01T00:00:00",
    "updatedAt": "2026-05-01T00:00:00"
  },
  "message": ""
}
```

---

### 9.4 자유게시판 글 등록

- **POST** `/api/v1/posts/free`
- **권한**: `USER`

**Request Body**

```json
{
  "title": "오늘 리자몽 득템했어요",
  "content": "드디어 PSA 10을 구했습니다..."
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "title": "오늘 리자몽 득템했어요"
  },
  "message": "게시글 등록 완료"
}
```

---

### 9.5 자유게시판 글 수정

- **PATCH** `/api/v1/posts/free/{freePostId}`
- **권한**: `USER` (본인)

**Request Body** (변경할 필드만 포함)

```json
{
  "title": "수정된 제목",
  "content": "수정된 내용"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "title": "수정된 제목"
  },
  "message": "게시글 수정 완료"
}
```

---

### 9.6 자유게시판 글 삭제

- **DELETE** `/api/v1/posts/free/{freePostId}`
- **권한**: `USER` (본인) / `ADMIN`

**Response** `204 No Content`

---

## 10. 커뮤니티 - 거래게시판 (Trade Post)

> 담당자: 최재민

### 10.1 내 거래 게시글 목록

- **GET** `/api/v1/posts/trade/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

---

### 10.2 거래게시판 목록 조회

- **GET** `/api/v1/posts/trade`
- **권한**: `PUBLIC`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | String | N | 제목/내용 검색 |
| `minPrice` | Long | N | 최소 가격 |
| `maxPrice` | Long | N | 최대 가격 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (createdAt,desc / price,asc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "리자몽 PSA 9 팝니다",
        "authorNickname": "카드마스터",
        "price": 300000,
        "thumbnail": "https://...",
        "viewCount": 80,
        "createdAt": "2026-05-01T00:00:00"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 10.3 거래게시판 상세 조회

- **GET** `/api/v1/posts/trade/{tradePostId}`
- **권한**: `PUBLIC`
- **설명**: 조회 시 `viewCount` 증가

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "title": "리자몽 PSA 9 팝니다",
    "content": "상태 S급입니다. 직거래 가능",
    "authorId": 2,
    "authorNickname": "카드마스터",
    "price": 300000,
    "thumbnail": "https://...",
    "viewCount": 81,
    "createdAt": "2026-05-01T00:00:00",
    "updatedAt": "2026-05-01T00:00:00"
  },
  "message": ""
}
```

---

### 10.4 거래게시판 글 등록

- **POST** `/api/v1/posts/trade`
- **권한**: `USER`

**Request Body**

```json
{
  "title": "리자몽 PSA 9 팝니다",
  "content": "상태 S급입니다. 직거래 가능",
  "price": 300000,
  "thumbnail": "https://..."
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "title": "리자몽 PSA 9 팝니다"
  },
  "message": "거래 게시글 등록 완료"
}
```

---

### 10.5 거래게시판 글 수정

- **PATCH** `/api/v1/posts/trade/{tradePostId}`
- **권한**: `USER` (본인)

**Request Body** (변경할 필드만 포함)

```json
{
  "title": "수정된 제목",
  "price": 280000
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "title": "수정된 제목",
    "price": 280000
  },
  "message": "거래 게시글 수정 완료"
}
```

---

### 10.6 거래게시판 글 삭제

- **DELETE** `/api/v1/posts/trade/{tradePostId}`
- **권한**: `USER` (본인) / `ADMIN`

**Response** `204 No Content`

---

## 11. 댓글 (Comment)

> 담당자: 이석형  
> 설명: 자유게시판 기반 2-depth 댓글 구조 (`parent_id`가 null이면 최상위 댓글, 있으면 대댓글)

### 11.1 댓글 목록 조회 (2depth)

- **GET** `/api/v1/comments`
- **권한**: `PUBLIC`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `postId` | Long | **Y** | 게시글 ID |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "postId": 1,
        "parentId": null,
        "authorId": 2,
        "authorNickname": "카드마스터",
        "content": "부럽네요!",
        "createdAt": "2026-05-01T01:00:00",
        "updatedAt": "2026-05-01T01:00:00",
        "children": [
          {
            "id": 2,
            "parentId": 1,
            "authorId": 1,
            "authorNickname": "포켓몬마스터",
            "content": "감사합니다!",
            "createdAt": "2026-05-01T02:00:00",
            "updatedAt": "2026-05-01T02:00:00"
          }
        ]
      }
    ],
    "totalElements": 3,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 11.2 댓글 등록

- **POST** `/api/v1/comments`
- **권한**: `USER`

**Request Body**

```json
{
  "postId": 1,
  "parentId": null,
  "content": "부럽네요!"
}
```

> `parentId`가 null이면 최상위 댓글, 값이 있으면 대댓글

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "postId": 1,
    "parentId": null,
    "content": "부럽네요!"
  },
  "message": "댓글 등록 완료"
}
```

---

### 11.3 댓글 수정

- **PATCH** `/api/v1/comments/{commentId}`
- **권한**: `USER` (본인)

**Request Body**

```json
{
  "content": "수정된 댓글 내용"
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "content": "수정된 댓글 내용"
  },
  "message": "댓글 수정 완료"
}
```

---

### 11.4 댓글 삭제

- **DELETE** `/api/v1/comments/{commentId}`
- **권한**: `USER` (본인) / `ADMIN`

**Response** `204 No Content`

---

## 12. 채팅 (Chat)

> 담당자: 최재민  
> 설명: 거래게시판 기반 1:1 채팅. 채팅 개설 이후 거래는 당사자 간 자율 진행 (서비스 측 결제·배송 관여 없음)

### HTTP REST API

### 12.1 채팅방 생성 (구매자)

- **POST** `/api/v1/chats`
- **권한**: `USER`

**Request Body**

```json
{
  "postId": 1
}
```

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "chatId": 1,
    "postId": 1,
    "ownerId": 2,
    "guestId": 3,
    "status": "ACTIVE"
  },
  "message": "채팅방 생성 완료"
}
```

---

### 12.2 내 채팅방 목록 조회

- **GET** `/api/v1/chats/me`
- **권한**: `USER`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": [
    {
      "chatId": 1,
      "postTitle": "리자몽 PSA 9 팝니다",
      "opponentNickname": "카드마스터",
      "lastMessage": "직거래 가능한가요?",
      "status": "ACTIVE",
      "updatedAt": "2026-05-02T10:00:00"
    }
  ],
  "message": ""
}
```

---

### 12.3 채팅 메시지 조회 (페이징)

- **GET** `/api/v1/chats/{chatId}/messages`
- **권한**: `USER` (채팅 참여자)

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 30) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "senderId": 3,
        "senderNickname": "포켓몬마스터",
        "message": "직거래 가능한가요?",
        "isRead": true,
        "createdAt": "2026-05-02T10:00:00"
      }
    ],
    "totalElements": 20,
    "totalPages": 1,
    "size": 30,
    "number": 0
  },
  "message": ""
}
```

---

### 12.4 채팅방 나가기

- **DELETE** `/api/v1/chats/{chatId}`
- **권한**: `USER` (채팅 참여자)

**Response** `204 No Content`

---

### 12.5 읽음 처리 (입장 시 일괄)

- **PATCH** `/api/v1/chats/{chatId}/read`
- **권한**: `USER` (채팅 참여자)
- **설명**: 채팅방 입장 시 호출. 내가 받은 미읽음 메시지를 전체 읽음 처리

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

### WebSocket (STOMP)

> 연결 엔드포인트: `ws://{host}/ws/chat`  
> 인증: WebSocket 핸드셰이크 시 `Authorization` 헤더 또는 `token` 네이티브 헤더로 JWT 전달

### 12.6 WebSocket 핸드셰이크 연결

```
CONNECT ws://{host}/ws/chat
Authorization: Bearer {accessToken}
```

---

### 12.7 메시지 전송

- **SEND** `/pub/chat/{chatId}`

**Payload**

```json
{
  "message": "직거래 가능한가요?"
}
```

---

### 12.8 실시간 읽음 처리

- **SEND** `/pub/chat/{chatId}/read`
- **설명**: 채팅방이 열려 있는 상태에서 메시지 수신 시 호출. 서버가 읽음 처리 후 READ 이벤트를 브로드캐스트

**Payload 없음**

---

### 12.9 이벤트 수신 구독

- **SUBSCRIBE** `/sub/chat/{chatId}`
- **설명**: 메시지(MESSAGE)와 읽음(READ) 두 가지 이벤트를 `type` 필드로 구분

**메시지 이벤트 (type: MESSAGE)**

```json
{
  "type": "MESSAGE",
  "chatId": 1,
  "senderId": 3,
  "senderNickname": "포켓몬마스터",
  "message": "직거래 가능한가요?",
  "createdAt": "2026-05-02T10:00:00"
}
```

**읽음 이벤트 (type: READ)**

```json
{
  "type": "READ",
  "chatId": 1,
  "readerId": 3
}
```

---

### 12.10 WebSocket 연결 해제

```
DISCONNECT ws://{host}/ws/chat
```

### 12.10 읽음 처리

- **SUBSCRIBE** `/sub/chat/{chatId}/read`

```json
{
  "chatId": 1,
  "readerId": 3
}
```

---

## 13. 알림 (Notification)

> 담당자: 이승현

### HTTP REST API

### 13.1 알림 목록 조회

- **GET** `/api/v1/notifications`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `isRead` | boolean | N | 읽음 여부 필터 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "type": "BID_OUTBID",
        "title": "입찰이 갱신되었습니다",
        "message": "다른 사용자가 더 높은 금액으로 입찰했습니다.",
        "isRead": false,
        "createdAt": "2026-05-02T10:30:00"
      }
    ],
    "totalElements": 10,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

**알림 타입 정의**

| type | 발생 시점 |
|---|---|
| `BID_OUTBID` | 다른 사용자가 더 높은 금액으로 입찰 시 |
| `AUCTION_WON` | 경매 낙찰 확정 시 |
| `AUCTION_LOST` | 경매 종료 후 낙찰 실패 시 |
| `INSPECTION_PASSED` | 관리자 검수 통과 시 |
| `INSPECTION_FAILED` | 관리자 검수 실패 시 |
| `SHIPPING` | 배송 시작 시 |
| `SHIPPING_COMPLETED` | 배송 완료 시 |

---

### 13.2 알림 개별 읽음 처리

- **PATCH** `/api/v1/notifications/{notificationId}/read`
- **권한**: `USER` (본인)

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1,
    "isRead": true
  },
  "message": "읽음 처리 완료"
}
```

---

### 13.3 알림 전체 읽음 처리

- **PATCH** `/api/v1/notifications/read-all`
- **권한**: `USER`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": "전체 읽음 처리 완료"
}
```

---

### 13.4 알림 개별 삭제

- **DELETE** `/api/v1/notifications/{notificationId}`
- **권한**: `USER` (본인)

**Response** `204 No Content`

---

### 13.5 알림 전체 삭제

- **DELETE** `/api/v1/notifications`
- **권한**: `USER`

**Response** `204 No Content`

---

### WebSocket (STOMP)

> 연결 엔드포인트: `ws://{host}/ws/notification`

### 13.6 알림 WebSocket 연결

```
CONNECT /ws/notification
Authorization: Bearer {accessToken}
```

---

### 13.7 실시간 알림 수신 구독

- **SUBSCRIBE** `/sub/notifications/{userId}`

**수신 메시지 형식**

```json
{
  "id": 1,
  "type": "BID_OUTBID",
  "title": "입찰이 갱신되었습니다",
  "message": "다른 사용자가 더 높은 금액으로 입찰했습니다.",
  "createdAt": "2026-05-02T10:30:00"
}
```

---

### 13.8 알림 WebSocket 연결 해제

```
DISCONNECT /ws/notification
```

---

## 14. 찜 (Like)

> 담당자: 이석형

### 14.1 찜 토글 (추가/취소)

- **POST** `/api/v1/likes`
- **권한**: `USER`
- **설명**: 이미 찜한 경매라면 찜 취소, 아니라면 찜 추가 (토글 방식)

**Request Body**

```json
{
  "auctionId": 1
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "isLiked": true
  },
  "message": "찜 추가 완료"
}
```

> `isLiked: false`이면 "찜 취소 완료"

---

### 14.2 내 찜 목록 조회

- **GET** `/api/v1/likes/me`
- **권한**: `USER`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "likeId": 1,
        "auctionId": 1,
        "auctionTitle": "PSA 10 리자몽 경매",
        "cardName": "리자몽",
        "grade": "PSA_10",
        "cardImageUrl": "https://...",
        "highestPrice": 250000,
        "endedAt": "2026-05-04T00:00:00",
        "status": "ACTIVE",
        "createdAt": "2026-05-02T00:00:00"
      }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

*© 2026 POCAT Team — 7조 로켓단*