# 🛠 POCAT 관리자 API 명세서

> **Pokemon Card Trading Platform** | 7조 로켓단  
> Base URL: `https://{host}/api/v1`  
> 인증 방식: `Authorization: Bearer {accessToken}` (JWT)  
> 권한: **기본적으로 모든 엔드포인트는 `ADMIN` 역할 필수** (단, internal-only API는 예외 — [8.2](#82-카드-임베딩-재색인-청크-처리-내부-api) 및 ADR-018 참고)  
> 공통 응답 포맷: `ApiResponse<T>` 래핑

---

## 📌 목차

1. [공통 규격](#0-공통-규격)
2. [유저 관리](#1-유저-관리)
3. [카드 관리](#2-카드-관리)
4. [경매 관리](#3-경매-관리)
5. [주문 관리](#4-주문-관리)
6. [환불 관리](#5-환불-관리)
7. [정산 관리](#6-정산-관리)
8. [포켓몬 데이터 관리](#7-포켓몬-데이터-관리)
9. [AI 관리](#8-ai-관리)

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

### HTTP 상태 코드

| 상태 코드 | 의미 |
|---|---|
| `200 OK` | 조회·수정·삭제 성공 |
| `201 Created` | 생성 성공 |
| `202 Accepted` | 비동기 작업 접수 (즉시 완료되지 않음) |
| `400 Bad Request` | 요청 값 검증 실패 |
| `401 Unauthorized` | 미인증 요청 |
| `403 Forbidden` | 관리자 권한 없음 |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 비즈니스 정책 충돌 |
| `500 Internal Server Error` | 서버 오류 |

---

## 1. 유저 관리

### 1.1 전체 유저 목록 조회

- **GET** `/api/v1/admin/users`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | String | N | 이메일 / 닉네임 검색 |
| `isBidBlocked` | Boolean | N | 입찰 차단 여부 필터 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 10) |
| `sort` | String | N | 정렬 기준 (default: createdAt,desc) |

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
        "phone": "***-****-5678",
        "role": "USER",
        "address": "서울시 강남구...",
        "unpaidStrike": 0,
        "isBidBlocked": false,
        "hasBillingKey": true,
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "totalElements": 100,
    "totalPages": 10,
    "size": 10,
    "number": 0
  },
  "message": ""
}
```

> 📌 `phone` 필드는 마스킹 처리됩니다 (`***-****-XXXX` 형식).

---

## 2. 카드 관리

### 2.1 카드 등록 요청 목록 조회

- **GET** `/api/v1/admin/cards/requests`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 (`PENDING`, `ACTIVE`, `REJECTED`) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (default: createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 10,
        "userId": 1,
        "tcgdexId": "swsh1-1",
        "name": "리자몽",
        "series": "소드&쉴드",
        "setName": "칼과방패",
        "cardNumber": "001",
        "rarity": "Rare Holo",
        "category": "POKEMON",
        "grade": "PSA_10",
        "imageUrl": "https://...",
        "source": "TCGDEX",
        "status": "PENDING",
        "rejectReason": null,
        "createdAt": "2026-01-01T00:00:00",
        "updatedAt": "2026-01-01T00:00:00"
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

### 2.2 카드 등록 승인

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

### 2.3 카드 등록 거절

- **PATCH** `/api/v1/admin/cards/{cardId}/reject`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `cardId` | Long | 카드 ID |

**Request Body**

```json
{
  "rejectReason": "이미지 불명확"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `rejectReason` | String | ✅ | 거절 사유 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "status": "REJECTED",
    "rejectReason": "이미지 불명확"
  },
  "message": "카드 등록 거절 완료"
}
```

---

### 2.4 카드 정보 수정

- **PATCH** `/api/v1/admin/cards/{cardId}`
- **권한**: `ADMIN`
- **설명**: 변경할 필드만 포함. 포함되지 않은 필드는 변경하지 않음.

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `cardId` | Long | 카드 ID |

**Request Body**

```json
{
  "name": "리자몽 (수정)",
  "rarity": "Rare Holo V",
  "grade": "PSA_10",
  "imageUrl": "https://..."
}
```

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "name": "리자몽 (수정)",
    "status": "ACTIVE"
  },
  "message": "카드 수정 완료"
}
```

---

### 2.5 카드 삭제

- **DELETE** `/api/v1/admin/cards/{cardId}`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `cardId` | Long | 카드 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

### 2.6 TCGdex 카드 수동 동기화

- **POST** `/api/v1/admin/cards/sync`
- **권한**: `ADMIN`
- **설명**: TCGdex 외부 API에서 전체 카드를 동기화한다. 비동기로 실행되며 즉시 응답을 반환하고, 백그라운드에서 동기화가 진행된다. 완료 여부는 서버 로그의 `[CardSync] 주간 전체 동기화 완료` 메시지로 확인.

**Response** `202 Accepted`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

### 2.7 카드 이미지 S3 마이그레이션

- **POST** `/api/v1/admin/cards/migrate-images`
- **권한**: `ADMIN`
- **설명**: TCGdex 카드 이미지를 S3로 마이그레이션한다. 비동기로 실행되며 즉시 응답을 반환한다. 완료 여부는 서버 로그의 `[ImageMigration] 완료` 메시지로 확인.

**Response** `202 Accepted`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

## 3. 경매 관리

### 3.1 전체 경매 목록 조회

- **GET** `/api/v1/admin/auctions`
- **권한**: `ADMIN`
- **설명**: 모든 상태의 경매를 조회한다. `status` 미입력 시 전체 조회.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | String | N | 카드명 / 경매 제목 / 시리즈명 / 확장팩명 / setId / 카드번호 |
| `series` | String | N | 카드 시리즈 필터 |
| `setName` | String | N | 확장팩명 필터 |
| `grade` | String | N | 카드 등급 필터 (`PSA_10`, `PSA_9`, `BGS_10`) |
| `category` | String | N | 카드 카테고리 필터 (`POKEMON`, `TRAINERS`, `ENERGY`, `UNKNOWN`) |
| `status` | String | N | 경매 상태 필터 (미입력 시 전체) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (default: createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "auctionId": 42,
        "sellerId": 2,
        "sellerNickname": "카드마스터",
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
        "endedAt": "2026-05-18T12:00:00"
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

### 3.2 경매 검수 승인 / 거절

- **PATCH** `/api/v1/admin/auctions/{auctionId}/inspection`
- **권한**: `ADMIN`
- **설명**: `PENDING` 또는 `INSPECTING` 상태의 경매를 검수한다.  
  - `PASSED` → `APPROVED` 상태로 전환, 이후 스케줄러가 `ACTIVE`로 자동 활성화  
  - `FAILED` → `REJECTED` 상태로 전환, `reason` 필드에 사유 저장

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `auctionId` | Long | 경매 ID |

**Request Body**

```json
{
  "result": "PASSED",
  "reason": ""
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `result` | String | ✅ | 검수 결과 (`PASSED` / `FAILED`) |
| `reason` | String | N | 거절 사유 (`FAILED` 시 필수) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "status": "APPROVED",
    "reason": null,
    "inspectedAt": "2026-05-21T19:00:00",
    "inspectedBy": 1
  },
  "message": ""
}
```

---

### 3.3 경매 강제 취소

- **PATCH** `/api/v1/admin/auctions/{auctionId}/cancel`
- **권한**: `ADMIN`
- **설명**: 진행 중인 경매를 강제 취소한다. 취소 사유를 `reason`에 저장하고, 현재 최고입찰이 있으면 해당 입찰 상태를 `CANCELLED`로 변경한다. 이후 최고입찰자 알림은 Kafka로 발행한다.

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `auctionId` | Long | 경매 ID |

**Request Body**

```json
{
  "reason": "규정 위반"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `reason` | String | ✅ | 취소 사유 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "auctionId": 1,
    "status": "CANCELLED",
    "reason": "규정 위반"
  },
  "message": ""
}
```

---

## 4. 주문 관리

### 4.1 전체 주문 목록 조회

- **GET** `/api/v1/admin/orders`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `orderStatus` | String | N | 주문 상태 필터 (`PAYMENT_PENDING`, `AUTO_PAYMENT_FAILED`, `DIRECT_PAYMENT_FAILED`, `PAYMENT_COMPLETED`, `ORDER_COMPLETED`, `CANCELLED`, `REFUNDED`) |
| `cardGrade` | String | N | 카드 등급 필터 (`PSA_10`, `PSA_9`, `BGS_10`) |
| `startDate` | DateTime | N | 주문 생성일 시작 (ISO 8601) |
| `endDate` | DateTime | N | 주문 생성일 종료 (ISO 8601) |
| `buyerNickname` | String | N | 구매자 닉네임 검색 |
| `sellerNickname` | String | N | 판매자 닉네임 검색 |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (default: createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "orderId": 5,
        "orderUid": "ORDER-UUID-001",
        "buyerNickname": "포켓몬마스터",
        "sellerNickname": "카드마스터",
        "cardName": "리자몽",
        "cardGrade": "PSA_10",
        "finalPrice": 250000,
        "status": "PAYMENT_COMPLETED",
        "createdAt": "2026-05-04T12:00:00"
      }
    ],
    "totalElements": 200,
    "totalPages": 10,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

## 5. 환불 관리

### 5.1 전체 환불 목록 조회

- **GET** `/api/v1/admin/refunds`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 (`REQUESTED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED_RETRYABLE`, `FAILED_FINAL`) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (default: createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "refundId": 7,
        "orderId": 42,
        "buyerNickname": "포켓몬마스터",
        "amount": 150000,
        "reason": "상품 상태 불량",
        "status": "REQUESTED",
        "createdAt": "2026-05-15T15:00:00",
        "updatedAt": "2026-05-15T15:00:00"
      }
    ],
    "totalElements": 35,
    "totalPages": 2,
    "size": 20,
    "number": 0
  },
  "message": ""
}
```

---

### 5.2 환불 승인

- **PATCH** `/api/v1/admin/refunds/{refundId}/approve`
- **권한**: `ADMIN`
- **설명**: `REQUESTED` 상태의 환불을 승인한다. 승인 시 아래 3개 테이블 상태가 일괄 변경된다.  
  - `refunds.status` → `PROCESSING` (PortOne 취소 호출 시작)  
  - `payments.status` → `REFUNDED`  
  - `orders.status` → `REFUNDED`  
  - 실제 금액 이체는 관리자가 별도 수동 처리 (PortOne 부분환불 미구현)

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `refundId` | Long | 환불 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "refundId": 7,
    "orderId": 1,
    "amount": 150000,
    "status": "PROCESSING",
    "updatedAt": "2026-05-15T16:00:00"
  },
  "message": ""
}
```

**Error Cases**

| 상태 코드 | 사유 |
|---|---|
| `404` | 환불 건 미존재 |
| `409` | `REQUESTED` 상태가 아님 |

---

### 5.3 환불 거절

- **PATCH** `/api/v1/admin/refunds/{refundId}/reject`
- **권한**: `ADMIN`
- **설명**: `REQUESTED` 상태의 환불을 거절한다. `refunds.status` → `REJECTED`, `orders.status`는 기존 상태 유지.

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `refundId` | Long | 환불 ID |

**Request Body**

```json
{
  "rejectReason": "환불 정책 기간 초과"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `rejectReason` | String | ✅ | 거절 사유 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "refundId": 7,
    "orderId": 42,
    "amount": 150000,
    "status": "REJECTED",
    "rejectReason": "환불 정책 기간 초과",
    "updatedAt": "2026-05-15T16:00:00"
  },
  "message": ""
}
```

---

## 6. 정산 관리

### 6.1 전체 정산 목록 조회

- **GET** `/api/v1/admin/settlements`
- **권한**: `ADMIN`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | N | 상태 필터 (`PENDING`, `COMPLETED`, `REFUNDED`) |
| `sellerNickname` | String | N | 판매자 닉네임 검색 |
| `startDate` | Date | N | 정산 생성일 시작 (ISO 8601 date: `YYYY-MM-DD`) |
| `endDate` | Date | N | 정산 생성일 종료 (ISO 8601 date: `YYYY-MM-DD`) |
| `page` | int | N | 페이지 번호 (default: 0) |
| `size` | int | N | 페이지 크기 (default: 20) |
| `sort` | String | N | 정렬 기준 (default: createdAt,desc) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "settlementUid": "SETTLE-ABC123",
        "orderUid": "ORDER-UUID-001",
        "sellerNickname": "카드마스터",
        "cardName": "리자몽",
        "cardGrade": "PSA_10",
        "totalPrice": 250000,
        "platformFee": 12500,
        "sellerAmount": 237500,
        "status": "PENDING",
        "settledAt": null,
        "createdAt": "2026-05-04T12:00:00"
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

### 6.2 정산 완료 처리

- **PATCH** `/api/v1/admin/settlements/{settlementUid}/complete`
- **권한**: `ADMIN`
- **설명**: `PENDING` 상태의 정산을 완료 처리한다. 실제 입금 완료 후 호출한다.

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `settlementUid` | String | 정산 고유 식별자 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "settlementUid": "SETTLE-ABC123",
    "status": "COMPLETED",
    "settledAt": "2026-05-10T00:00:00"
  },
  "message": "정산 완료 처리됨"
}
```

**Error Cases**

| 상태 코드 | 사유 |
|---|---|
| `404` | 정산 건 미존재 |
| `409` | `PENDING` 상태가 아님 |

---

## 7. 포켓몬 데이터 관리

> TCGdex 동기화 보완 및 한국어 번역 데이터 관리용 API

### 7.1 시리즈 (Series)

#### 7.1.1 시리즈 전체 목록 조회

- **GET** `/api/v1/admin/series`
- **권한**: `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 1,
      "name": "Sword & Shield",
      "nameKo": "소드&쉴드"
    }
  ],
  "message": ""
}
```

---

#### 7.1.2 시리즈 등록

- **POST** `/api/v1/admin/series`
- **권한**: `ADMIN`

**Request Body**

```json
{
  "name": "Scarlet & Violet",
  "nameKo": "스칼렛&바이올렛"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | String | ✅ | 시리즈 영문명 (고유값) |
| `nameKo` | String | N | 시리즈 한국어명 |

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 5,
    "name": "Scarlet & Violet",
    "nameKo": "스칼렛&바이올렛"
  },
  "message": ""
}
```

---

#### 7.1.3 시리즈 한국어명 수정

- **PATCH** `/api/v1/admin/series/{id}/name-ko`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 시리즈 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nameKo` | String | ✅ | 한국어명 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 5,
    "name": "Scarlet & Violet",
    "nameKo": "스칼렛&바이올렛 (수정)"
  },
  "message": ""
}
```

---

#### 7.1.4 시리즈 삭제

- **DELETE** `/api/v1/admin/series/{id}`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 시리즈 ID |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

### 7.2 확장팩 (PokemonSet)

#### 7.2.1 확장팩 전체 목록 조회

- **GET** `/api/v1/admin/sets`
- **권한**: `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 1,
      "setId": "swsh1",
      "name": "Sword & Shield",
      "nameKo": "칼과방패",
      "seriesId": 1,
      "seriesName": "소드&쉴드"
    }
  ],
  "message": ""
}
```

---

#### 7.2.2 확장팩 등록

- **POST** `/api/v1/admin/sets`
- **권한**: `ADMIN`

**Request Body**

```json
{
  "setId": "sv1",
  "name": "Scarlet & Violet",
  "nameKo": "스칼렛&바이올렛",
  "seriesId": 5
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `setId` | String | ✅ | TCGdex 세트 ID (고유값) |
| `name` | String | ✅ | 확장팩 영문명 |
| `nameKo` | String | N | 확장팩 한국어명 |
| `seriesId` | Long | N | 시리즈 ID (FK) |

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "setId": "sv1",
    "name": "Scarlet & Violet",
    "nameKo": "스칼렛&바이올렛",
    "seriesId": 5,
    "seriesName": "스칼렛&바이올렛"
  },
  "message": ""
}
```

---

#### 7.2.3 확장팩 한국어명 수정

- **PATCH** `/api/v1/admin/sets/{id}/name-ko`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 확장팩 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nameKo` | String | ✅ | 한국어명 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 10,
    "setId": "sv1",
    "name": "Scarlet & Violet",
    "nameKo": "스칼렛&바이올렛 (수정)"
  },
  "message": ""
}
```

---

#### 7.2.4 확장팩 삭제

- **DELETE** `/api/v1/admin/sets/{id}`
- **권한**: `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

### 7.3 포켓몬 (Pokemon)

#### 7.3.1 포켓몬 전체 목록 조회

- **GET** `/api/v1/admin/pokemon`
- **권한**: `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 1,
      "name": "Charizard",
      "nameKo": "리자몽"
    }
  ],
  "message": ""
}
```

---

#### 7.3.2 포켓몬 등록

- **POST** `/api/v1/admin/pokemon`
- **권한**: `ADMIN`
- **설명**: 동일 이름이 이미 존재하면 기존 데이터를 반환한다 (upsert).

**Request Body**

```json
{
  "name": "Pikachu",
  "nameKo": "피카츄"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | String | ✅ | 포켓몬 영문명 (고유값) |
| `nameKo` | String | N | 포켓몬 한국어명 |

**Response** `201 Created`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 25,
    "name": "Pikachu",
    "nameKo": "피카츄"
  },
  "message": ""
}
```

---

#### 7.3.3 포켓몬 한국어명 수정

- **PATCH** `/api/v1/admin/pokemon/{id}/name-ko`
- **권한**: `ADMIN`

**Path Variables**

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 포켓몬 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nameKo` | String | ✅ | 한국어명 |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 25,
    "name": "Pikachu",
    "nameKo": "피카츄 (수정)"
  },
  "message": ""
}
```

---

#### 7.3.4 포켓몬 삭제

- **DELETE** `/api/v1/admin/pokemon/{id}`
- **권한**: `ADMIN`

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

---

## 8. AI 관리

### 8.1 카드 벡터 스토어 재색인

- **POST** `/api/v1/admin/ai/reindex`
- **권한**: `ADMIN`
- **설명**: 활성(`ACTIVE`) 상태의 전체 카드를 벡터 스토어에 재색인한다. 비동기로 실행되므로 `202 Accepted`를 즉시 반환하고, 백그라운드에서 처리된다. RAG 기반 AI 어시스턴트의 검색 정확도가 떨어질 때 수동으로 호출한다.

**Response** `202 Accepted`

```json
{
  "status": "SUCCESS",
  "data": null,
  "message": ""
}
```

### 8.2 카드 임베딩 재색인 청크 처리 (내부 API)

> **ADR-018**: pocat-batch `aiReindexJob`이 ACTIVE 카드 ID를 100개 단위 청크로 호출하는 내부 전용 엔드포인트. `ADMIN` 사용자 인증이 아닌 `X-Internal-Token` 헤더 기반 서비스 간 인증을 사용한다 (ADR-014 `InternalTokenAuthFilter`).

- **POST** `/internal/ai/reindex-cards`
- **권한**: 내부 서비스 전용 (`X-Internal-Token` 헤더 필수, ADMIN 역할 불필요)
- **설명**: 전달받은 카드 ID 목록(최대 100개) 중 ES(`pocat-ai-index`)에 `metadata.cardId.keyword`로 미인덱싱된 카드만 골라 Gemini 임베딩 생성 후 ES upsert한다. `RedisRateLimiter`(key=`ratelimit:ai-embedding`, 80/60s)로 호출량을 제한하며, 한도 도달 시 `rateLimited: true`를 반환하여 pocat-batch가 이후 청크 호출을 조기 종료하도록 한다. 실패한 카드는 ES에 반영되지 않아 다음 배치 회차에 자동 재시도된다(self-healing).

**Request Headers**

```http
X-Internal-Token: {INTERNAL_TOKEN}
Idempotency-Key: reindex-cards-{firstCardId}-{lastCardId}-{jobExecutionId}
Content-Type: application/json
```

**Request Body**

```json
{
  "cardIds": [1001, 1002, 1003]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `cardIds` | Long[] | 재색인 대상 후보 카드 ID 목록 (최대 100개) |

**Response** `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "processedCount": 100,
    "skippedCount": 60,
    "indexedCount": 38,
    "failedCount": 2,
    "rateLimited": false
  },
  "message": ""
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `processedCount` | int | 전달받은 카드 ID 총 개수 |
| `skippedCount` | int | 이미 ES에 인덱싱되어 스킵한 카드 수 |
| `indexedCount` | int | 신규로 임베딩·색인 처리된 카드 수 |
| `failedCount` | int | 임베딩/색인 실패 카드 수 (다음 회차 자동 재시도 대상) |
| `rateLimited` | boolean | `RedisRateLimiter` 한도 도달로 처리를 조기 종료했는지 여부 |

---

## 📋 엔드포인트 요약표

| 도메인 | Method | 경로 | 설명 |
|---|---|---|---|
| **유저** | GET | `/api/v1/admin/users` | 전체 유저 목록 |
| **카드** | GET | `/api/v1/admin/cards/requests` | 카드 등록 요청 목록 |
| **카드** | PATCH | `/api/v1/admin/cards/{cardId}/approve` | 카드 등록 승인 |
| **카드** | PATCH | `/api/v1/admin/cards/{cardId}/reject` | 카드 등록 거절 |
| **카드** | PATCH | `/api/v1/admin/cards/{cardId}` | 카드 정보 수정 |
| **카드** | DELETE | `/api/v1/admin/cards/{cardId}` | 카드 삭제 |
| **카드** | POST | `/api/v1/admin/cards/sync` | TCGdex 수동 동기화 |
| **카드** | POST | `/api/v1/admin/cards/migrate-images` | 카드 이미지 S3 마이그레이션 |
| **경매** | GET | `/api/v1/admin/auctions` | 전체 경매 목록 |
| **경매** | PATCH | `/api/v1/admin/auctions/{auctionId}/inspection` | 경매 검수 승인/거절 |
| **경매** | PATCH | `/api/v1/admin/auctions/{auctionId}/cancel` | 경매 강제 취소 |
| **주문** | GET | `/api/v1/admin/orders` | 전체 주문 목록 |
| **환불** | GET | `/api/v1/admin/refunds` | 전체 환불 목록 |
| **환불** | PATCH | `/api/v1/admin/refunds/{refundId}/approve` | 환불 승인 |
| **환불** | PATCH | `/api/v1/admin/refunds/{refundId}/reject` | 환불 거절 |
| **정산** | GET | `/api/v1/admin/settlements` | 전체 정산 목록 |
| **정산** | PATCH | `/api/v1/admin/settlements/{settlementUid}/complete` | 정산 완료 처리 |
| **시리즈** | GET | `/api/v1/admin/series` | 시리즈 목록 |
| **시리즈** | POST | `/api/v1/admin/series` | 시리즈 등록 |
| **시리즈** | PATCH | `/api/v1/admin/series/{id}/name-ko` | 시리즈 한국어명 수정 |
| **시리즈** | DELETE | `/api/v1/admin/series/{id}` | 시리즈 삭제 |
| **확장팩** | GET | `/api/v1/admin/sets` | 확장팩 목록 |
| **확장팩** | POST | `/api/v1/admin/sets` | 확장팩 등록 |
| **확장팩** | PATCH | `/api/v1/admin/sets/{id}/name-ko` | 확장팩 한국어명 수정 |
| **확장팩** | DELETE | `/api/v1/admin/sets/{id}` | 확장팩 삭제 |
| **포켓몬** | GET | `/api/v1/admin/pokemon` | 포켓몬 목록 |
| **포켓몬** | POST | `/api/v1/admin/pokemon` | 포켓몬 등록 |
| **포켓몬** | PATCH | `/api/v1/admin/pokemon/{id}/name-ko` | 포켓몬 한국어명 수정 |
| **포켓몬** | DELETE | `/api/v1/admin/pokemon/{id}` | 포켓몬 삭제 |
| **AI** | POST | `/api/v1/admin/ai/reindex` | 카드 벡터 스토어 재색인 |
| **AI (내부)** | POST | `/internal/ai/reindex-cards` | 카드 임베딩 재색인 청크 처리 (pocat-batch `aiReindexJob` 전용, ADR-018) |
