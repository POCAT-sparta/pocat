## SA문서

# 🃏 POCAT — SA 문서 (v2)

> **Pokemon Card Trading Platform** | 7조 로켓단
>

---

## 📌 목차

1. 프로젝트 개요
2. 기술 스택
3. 시스템 아키텍처
4. 비즈니스 흐름
5. 결제 정책
6. ERD 상세 설명
7. API 설계 방향
8. 성능 및 기술 정책
9. 보안 정책
10. 주요 기능 목록
11. 리스크 및 고려사항
12. 용어 정의

---

## 1. 프로젝트 개요

### 1.1 목적

POCAT은 포켓몬 카드를 테마로 한 온라인 C2C 거래 플랫폼이다.
판매자는 등급 감정을 받은 카드를 **경매** 형태로 등록하고, 구매자는 입찰을 통해 안전하게 카드를 거래할 수 있다.

---

### 1.2 사용자 시나리오

### 판매자 시나리오

- 자신의 카드를 판매 등록하고 카드를 플랫폼에 배송 (Pending 상태)
- 관리자가 상품 검증 (가격검증 X, 실물 정품 카드인지만 검증)
- 경매 게시글 Approve → 경매 게시글 활성화
- 입찰 → 경매 낙찰 → 자동 결제 → 배송 → 판매자에게 수수료 제외 입금

### 구매자 시나리오

- 원하는 카드를 검색하여 현재 등급의 카드가 있는지 빠르게 확인
- 조회 후 원하는 카드의 입찰에 참여 → 가격 경쟁
- 승리 시 구매
- 패배 시 구매 불가

---

### 1.3 주요 도메인 범위

| 도메인 | 설명 |
| --- | --- |
| 유저 (User) | 회원가입/로그인, 마이페이지, 빌링키 등록, 계좌 등록, 제재(입찰 차단) 관리 |
| 카드 카탈로그 | TCGdex API 연동 카드 정보 DB 저장 / 수동 등록, 등급 (PSA_10 / PSA_9) 등 관리 |
| 경매 (Auction) | 경매 등록 → 검수 → 승인(Approved) → 스케줄러 활성화 → 종료 전 과정, 즉시 구매, 입찰 관리 |
| 주문 (Order) | AUCTION 주문 관리 |
| 결제 (Payment) | PortOne V2 빌링키 자동결제 중심, 경매 낙찰 실패 시 1시간 내 직접결제 허용 |
| 환불 (Refund) | 환불 요청 → 처리 → 상태 추적 (PortOne 부분환불 미포함) |
| 정산 (Settlement) | 배송 완료 후 판매자 정산 테이블 관리 |
| 커뮤니티 | 자유게시판 / 거래게시판, 2depth 댓글 |
| 채팅 (Chat) | 거래 게시판 기반 1:1 채팅 (채팅 이후 거래 진행은 당사자 간 자율) |
| 알림 (Notification) | 입찰 갱신·낙찰·배송 등 실시간 알림 |
| 찜 (Like) | 경매 좋아요/찜 기능 |

---

## 2. 기술 스택

### 2.1 Backend

| 분류 | 기술 | 비고 |
| --- | --- | --- |
| 언어/프레임워크 | Java 17 + Spring Boot 3.x |  |
| ORM | Spring Data JPA + QueryDSL | CQRS 패턴 적용 |
| DB | MySQL 8.x |  |
| 캐시/분산락 | Redis | 입찰 동시성 제어, 캐싱 |
| 검색 | ElasticSearch | 카드 카탈로그 풀텍스트 검색 |
| 메시지 큐 | Kafka | 경매 종료 이벤트 → 주문 생성 |
| 실시간 통신 | WebSocket (STOMP) | 채팅, 알림 |
| 결제 | PortOne V2 (KG이니시스) | 빌링키 자동결제 / PG 직접결제 |
| 외부 API | TCGdex REST API | 포켓몬 카드 데이터 |
| API 문서화 | Swagger (SpringDoc) | Notion 병행 |
| 스케줄러 | Spring Scheduler | 배송 상태 60분 주기 변경 (편의상) |

---

### 2.2 Infra / DevOps

| 분류 | 기술 | 비고 |
| --- | --- | --- |
| 컨테이너 | Docker | **Amazon ECR** 이미지 관리 |
| CI/CD | GitHub Actions | PR → Build → Push → Deploy |
| 클라우드 | AWS (ECS Fargate, ECR) |  |
| 로드밸런서 | AWS ALB | 인터넷 경계 |
| 오토스케일링 | AWS ASG | 무중단 배포 |
| 보안 | AWS IAM, Security Group | 최소 권한 원칙 |
| 이미지 저장 | AWS S3 | 카드 이미지 URL 저장 |
| 아키텍처 패턴 | MSA (조건부 검토) | 진행 속도가 계획보다 빠를 경우 그때 고려 |

---

## 3. 시스템 아키텍처

### 3.1 전체 구성

```
[ Client (Browser) ]
        | HTTPS
[ AWS ALB ]  ← 인터넷 경계
        |
[ ECS Fargate — Spring Boot App ]
    |           |            |
[ MySQL ]   [ Redis ]   [ Kafka ]
    |                        |
[ ElasticSearch ]     [ Scheduler ]
        |
[ PortOne V2 PG ]   [ TCGdex API ]   [ AWS S3 ]
```

---

### 3.2 배포 파이프라인

```
GitHub PR
  ① 단위 테스트 / 빌드
  ② Docker Image Build
  ③ Amazon ECR Push
  ④ ECS 서비스 업데이트 (Rolling Update)
  ⑤ ALB Health Check → 정상 확인 후 구버전 종료 (무중단 배포)
```

---

### 3.3 아키텍처 패턴 요약

- **CQRS**: 조회(Query)와 커맨드(Command)를 분리하여 읽기 성능 최적화
- **분산락**: Redis Redisson을 이용한 입찰 동시성 제어
- **이벤트 드리븐**: Kafka를 통한 경매 종료 → 주문 생성 비동기 처리
- **스냅샷**: **경매 낙찰 시점** 및 **주문 확정 시점**의 가격·수수료 정보를 별도 스냅샷 테이블에 영구 보관

---

## 4. 비즈니스 흐름

### 4.1 경매 등록 흐름 (판매자)

```
판매자가 판매할 카드의 카탈로그 조회 
  ↓
카드 선택 → CardCatalog upsert
  ↓
경매 정보 입력
  ├─ 시작가 (ex. 100,000원)
  ├─ 즉시구매가 (선택, ex. 1,000,000원)
  ├─ 경매 기간 (ex. 3일)
  └─ 등급 (PSA_10 / PSA_9)
  ↓
플랫폼으로 실물 카드 배송
  ↓
관리자 검수 (Inspection)
  ├─ PASSED → Auction status: ACTIVE → 검수 완료 정보(inspectedAt / inspectedBy) 저장
  │          └─ startedAt: 다음날 19:00, endedAt: 3일 후 19:00
  └─ FAILED → Auction status: REJECTED → reason 저장 → 카드 반송 → Notification 발송
```

---

### 4.2 입찰 흐름 (구매자)

```
입찰 전 조건 확인
  ├─ 빌링키(BillingKey) 등록 여부 검증 → 미등록 시 입찰 불가
  └─ 결제수단 ACTIVE 여부 확인
  ↓
Redis 분산락 획득 (key: "auction:{auctionId}")
  ↓
현재 최고 입찰가(highest_price)보다 높은지 검증
  ↳ 즉시구매가(buyout_price) 이상이면 입찰 거절 (즉시구매 API 사용)
  ↓
높으면 → AuctionBid 생성, 최고 입찰자(highest_bidder_id) 갱신
         이전 최고 입찰자 → status: OUTBID
낮으면 → 입찰 거절 (예외 처리)
  ↓
락 해제
```

> 기존 최고입찰자 OUTBID 알림은 Kafka 발행 연동 전까지 보류한다.

> 💡 **경매 종료 시 스냅샷 촬영**: 낙찰 확정 시점에 `auction_snapshots` 테이블에 최고 입찰가 등 정보를 기록한다.
>

---

### 4.3 즉시 구매 흐름

```
구매자가 즉시구매가(buyout_price) 이상 입찰
  ↓
분산락 획득 → 즉시구매가 이상 감지
  ↓
Auction status: PAYMENT_PENDING (결제 대기 상태 — 다른 입찰 차단)
  ↓
billingKey 자동결제 시도
  ├─ 결제 성공 → Auction status: ENDED
  │              해당 입찰자 → AuctionBid status: WON
  │              나머지 입찰자 → AuctionBid status: LOST
  │              auction_snapshots 스냅샷 생성
  │              Order 생성으로 이동 (낙찰 확정 흐름 진입)
  └─ 결제 실패 → 결제 실패 처리 진행
```

> ⚠️ 즉시 ENDED 처리 대신 **결제 대기(PAYMENT_PENDING) 상태를 중간에 삽입**하여, 결제가 실제로 완료된 후에 ENDED로 전환한다.
>

---

### 4.4 경매 종료 흐름

```
Deadline Worker
 → Redis ZSet에서 만료된 auctionId 조회
 → DB에서 status OPEN 확인 후 CLOSED 변경
 → Kafka auction-ended 발행
  ↓
입찰자 없음 → Auction status: NO_BIDDER (종료)
입찰자 있음 → Auction status: ENDED
             AuctionBid status: WON (최고 입찰자)
             AuctionBid status: LOST (나머지)
             auction_snapshots 스냅샷 생성
  ↓
Kafka 이벤트 발행 → Notification 발송 → Order 생성으로 이동
```

---

### 4.5 낙찰 확정 → 주문 생성

```
Order 생성
  ├─ auction_id: 해당 경매 ID
  ├─ buyer_id: 낙찰자
  ├─ seller_id: 판매자
  ├─ final_price: 최고 입찰가
  └─ status: PAYMENT_PENDING
  ↓
billingKey로 자동결제 1회 시도 (PortOne V2)
  성공 → status: PAYMENT_COMPLETED
  실패 → status: PAYMENT_FAILED
         → 1시간 내 직접 결제 가능
         → 미결제 확정 시: 거래 취소 + 패널티(unpaid_strike) +1 기록
         → 패널티 3회 누적 시: is_bid_blocked = true (입찰 차단)
  ↓
order_snapshots 생성
  ├─ final_price
  ├─ fee_rate
  ├─ fee
  └─ seller_amount (= final_price × (1 - fee_rate))
  ↓
배송 시작 (delivery_status: PREPARING → SHIPPING → COMPLETED)
  ↓
Settlement 생성 → 판매자 정산 테이블 기록
```

> 🔄 **재결제 정책**: 낙찰 자동결제 실패 시 **1시간 이내**에만 직접 결제 허용
>

---

### 4.6 일반 거래 흐름 (거래게시판)

```
판매자 trade_post 등록
  ├─ 카드 정보 (CardCatalog 선택적 연결)
  ├─ 고정 가격
  └─ 카드 상태 설명 + 이미지
  ↓
구매자가 채팅 시작 (Chat)
  ↓
이후 거래 진행은 당사자 간 자율 진행
  └─ 서비스 측에서 로직상 관여하지 않음
     (결제·배송·완료 처리 없음)
```

> 📌 일반 거래(거래게시판)는 서비스가 결제·배송 흐름을 지원하지 않는다. 채팅 개설 이후의 거래는 판매자와 구매자가 직접 진행한다.
>

---

## 5. 결제 정책

### 5.1 결제 유형 분류

| 결제 유형 | 대상 | 방식 |
| --- | --- | --- |
| BILLING_KEY (자동결제) | 경매 낙찰 | 사전 등록 빌링키로 서버에서 자동 청구 |
| PG_DIRECT (직접결제) | 경매 낙찰 자동결제 실패 후 1시간 이내 | 포트원 결제창 직접 호출 |

**핵심 정책:**

- **빌링키 미등록 시 경매 입찰 시도 자체가 불가**
- 경매 낙찰 후 자동결제 실패 시 → **1시간 이내** 직접결제 허용

---

### 5.2 경매 결제 상세 정책

- 입찰 전 빌링키 등록 필수 (미등록 시 입찰 불가)
- 입찰 전 결제수단 ACTIVE 상태 확인
- 최고 입찰자 변경 시 이전 최고 입찰자 OUTBID 처리 (알림 발송은 Kafka 연동 전까지 보류)

**낙찰 후 처리:**

1. billingKey로 자동결제 1회 시도
2. 실패 → PAYMENT_FAILED 상태, **1시간** 이내 직접결제 기회 부여
3. 1시간 내 미결제 확정 → 거래 취소 + 패널티(unpaid_strike) +1
4. 1순위 낙찰 실패 시 → 2순위에게 알림 (24시간 유효)
5. 2순위도 실패 시 → 판매자에게 선택권: 재경매 or 즉시판매

---

### 5.3 수수료 및 정산

- 플랫폼 수수료: 낙찰가의 5% (`fee_rate`)
- 판매자 실수령액: `final_price × (1 - fee_rate)` → `seller_amount`
- `order_snapshots` 테이블에 `final_price`, `fee_rate`, `fee`, `seller_amount` 영구 보존
- 배송 완료 후 `settlements` 테이블에 정산 정보 기록 (별도 정산 테이블 운영)

---

## 6. ERD 상세 설명

### 6.1 핵심 테이블 관계

```
users  ──────────────┬── auctions (seller_id, highest_bidder_id)
                     ├── auction_bids (user_id)
                     ├── orders (buyer_id, seller_id)
                     ├── card_catalogs (user_id)
                     └── notifications (user_id)

auctions ────────────┬── auction_bids (auction_id)
                     ├── orders (auction_id)
                     └── auction_snapshots (auction_id)

orders ──────────────┬── payments (order_id)
                     ├── refunds (order_id)
                     └── order_snapshots (order_id)

trade_posts ──────────── chats (free_post_id)
free_posts ───────────── comments (trade_post_id)

likes ───────────────── auctions (auction_id)
```

---

### 6.2 주요 테이블 명세

### `users`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `billing_key` | VARCHAR(255) | 경매/이벤트 자동결제용 빌링키 (PortOne V2) |
| `unpaid_strike` | INT | 미결제 패널티 횟수 누적 |
| `is_bid_blocked` | BOOLEAN | 입찰 차단 여부 (패널티 누적 시 true) |
| `bank_name` / `bank_account` | VARCHAR | 판매자 정산용 계좌 (마이페이지에서 원하는 시기에 등록 가능) |
| `role` | VARCHAR(20) | USER / ADMIN |

> 📌 계좌 정보/ 주소지는 회원가입 후 **마이페이지에서 원하는 시점에 등록·수정** 가능
>

---

### `auctions`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `status` | VARCHAR(30) | PENDING / INSPECTING / REJECTED / ACTIVE / ENDED / NO_BIDDER / CANCELLED / PAYMENT_PENDING |
| `buyout_price` | BIGINT | 즉시구매가 (선택값, null 가능) |
| `highest_price` | BIGINT | 현재 최고 입찰가 (실시간 갱신) |
| `highest_bidder_id` | BIGINT | 현재 최고 입찰자 FK |
| `card_catalog_id` | BIGINT | CardCatalog FK (카드 등급 정보) |

---

### `orders`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `auction_id` | BIGINT | 경매 FK (null 가능) |
| `card_catalog_id` | BIGINT | 카드 카탈로그 FK |
| `seller_id` | BIGINT | 판매자 (이벤트 상품이면 null) |
| `buyer_id` | BIGINT | 구매자 |
| `status` | VARCHAR(30) | PAYMENT_PENDING / CANCELLED / PAYMENT_COMPLETED / SHIPPING / COMPLETED / REFUNDED |
| `delivery_status` | VARCHAR(20) | PREPARING / SHIPPING / COMPLETED |

---

### `order_snapshots`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `order_id` | BIGINT | 주문 FK |
| `final_price` | DECIMAL(12,2) | 낙찰/확정 금액 |
| `fee_rate` | DECIMAL(5,2) | 적용 수수료율 |
| `fee` | DECIMAL(12,2) | 수수료 금액 |
| `seller_amount` | DECIMAL(12,2) | 판매자 실수령액 |
| `snapshot_json` | TEXT | 전체 상태 JSON (선택) |

---

### `payments`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `payment_type` | VARCHAR(20) | BILLING_KEY / PG_DIRECT |
| `payment_method` | VARCHAR(50) | CARD / KAKAO_PAY / TOSS 등 |
| `status` | VARCHAR(20) | PENDING / COMPLETED / FAILED / REFUNDED |
| `payment_uuid` | VARCHAR(50) | PortOne 거래 고유 식별자 |

---

### `notifications` — 알림 타입 정의

| type 값 | 발생 시점 |
| --- | --- |
| `BID_OUTBID` | 다른 사용자가 더 높은 금액으로 입찰 시 |
| `AUCTION_WON` | 경매 낙찰 확정 시 |
| `AUCTION_LOST` | 경매 종료 후 낙찰 실패 시 |
| `INSPECTION_PASSED` | 관리자 검수 통과 시 |
| `INSPECTION_FAILED` | 관리자 검수 실패 (REJECTED) 시 |
| `SHIPPING` | 배송 시작 시 |
| `SHIPPING_COMPLETED` | 배송 완료 시 |

---

### `settlements` *(별도 정산 테이블 — 추가 설계 필요)*

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | BIGINT | PK |
| `order_id` | BIGINT | 주문 FK |
| `seller_id` | BIGINT | 정산 대상 판매자 |
| `total_price` | BIGINT | 낙찰 총액 |
| `platform_fee` | BIGINT | 플랫폼 수수료 (총액의 N%) |
| `seller_amount` | BIGINT | 판매자 실수령액 |
| `status` | VARCHAR(20) | PENDING / COMPLETED |
| `settled_at` | TIMESTAMP | 정산 완료 시각 |
| `created_at` | TIMESTAMP | 생성 시각 |

---

## 7. API 설계 방향

### 7.1 REST API 원칙

- **Base URL**: `/api/v1/{resource}`
- **인증**: JWT Bearer Token (Authorization 헤더)
- **응답 포맷**: { "status": "SUCCESS", "data": {...}, "message": "" }
- **API 문서화**: Swagger (SpringDoc) + Notion 병행

---

### 7.2 주요 엔드포인트 (요약)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 (JWT 발급) |
| POST | `/api/v1/auth/reissue` | RTR 기반 토큰 재발급 |
| POST | `/api/v1/auth/logout` | 로그아웃 (블랙리스트 등록) |
| GET | `/api/v1/card-catalogs` | 카드 카탈로그 목록 (검색/필터) |
| POST | `/api/v1/auctions` | 경매 등록 |
| GET | `/api/v1/auctions/{id}` | 경매 상세 조회 |
| POST | `/api/v1/auctions/{id}/bids` | 입찰 |
| POST | `/api/v1/auctions/{id}/buyout` | 즉시 구매 |
| POST | `/api/v1/orders` | 주문 생성 |
| POST | `/api/v1/payments` | 결제 요청 |
| POST | `/api/v1/refunds` | 환불 요청 |
| GET | `/api/v1/notifications` | 내 알림 목록 |
| GET | `/api/v1/posts/free` | 자유게시판 목록 |
| GET | `/api/v1/posts/trade` | 거래게시판 목록 |
| POST | `/api/v1/chats`  | 채팅방 생성 |
| WS | `/ws/chat/{chatId}` | 채팅 WebSocket 연결 |

---

## 8. 성능 및 기술 정책

### 8.1 동시성 제어 (입찰)

- Redis Redisson 분산락으로 동일 경매의 중복 입찰 방지
- 락 키: `"auction:{auctionId}"`
- 락 획득 실패 시 즉시 예외 반환 (busy-wait 금지)

---

### 8.2 캐싱 전략

- 경매 현재 입찰가(`highest_price`): Redis 캐싱, 입찰 시 DB와 동기화
- 카드 카탈로그 목록: 변경 빈도 낮음 → Redis TTL 캐싱 적용

---

### 8.3 검색 최적화

- 카드 이름·시리즈·세트명 검색: ElasticSearch 풀텍스트 인덱스
- 경매 목록 조회: MySQL 인덱스 (`status`, `ended_at`, `created_at`)
- QueryDSL 동적 쿼리로 다양한 필터 조건 처리

---

### 8.4 쿼리 성능 정책

- Slow Query 모니터링 설정
- 페이지네이션: Offset 기반 형식을 기본으로 하고 Cursor 기반 (무한 스크롤) 필요 시 도입

---

### 8.5 데이터 무결성

- **경매 낙찰 시점**: `auction_snapshots`에 최고 입찰가 기록
- **주문 확정 시점**: `order_snapshots`에 금액·수수료 정보 영구 보존
- 환불 처리 시 `payments.status → REFUNDED`, `refunds` 테이블에 사유·금액 기록

---

## 9. 보안 정책

### 9.1 인증/인가

- **JWT 이중 구조**: Access Token (단기) + Refresh Token (장기)
- **RTR(Refresh Token Rotation) 기법 도입**: Refresh Token 사용 시 새 Refresh Token 발급 및 기존 토큰 무효화
- **토큰 블랙리스트 도입**: 로그아웃/강제 만료 시 Access Token을 Redis 블랙리스트에 등록
- Spring Security 기반 Role(`USER` / `ADMIN`) 접근 제어
- 관리자 전용 API: `/api/v1/admin/**` (ADMIN Role만 접근)

---

### 9.2 결제 보안

- 빌링키는 `users.billing_key`에 암호화 저장 (AES 256)
- PortOne Webhook 서명 검증 (`X-PortOne-Signature`)
- 결제 금액 서버 사이드 검증 필수 (클라이언트 금액 신뢰 금지)

---

### 9.3 인프라 보안

- AWS Security Group: 필요한 포트만 열람
- IAM 최소 권한 원칙 적용
- 환경변수(DB 패스워드, API 키 등): AWS Secrets Manager 관리

---

## 10. 주요 기능 목록

### 10.1 기능 분류 표

| 기능 영역 | 세부 기능 |
| --- | --- |
| 유저 | 회원가입, 로그인(JWT + RTR), 마이페이지, 빌링키 등록, 계좌 등록(마이페이지에서 자율 등록), 입찰 차단 |
| 카드 카탈로그 | TCGdex API 연동 후 DB에 카드 정보 저장, 수동 등록, 카드 등급 관리 (PSA_10 / PSA_9), 이미지 저장 |
| 경매 | 경매 등록, 검수(PASSED/REJECTED), 입찰, 즉시구매, 경매 종료, 스냅샷 |
| 주문 | AUCTION 주문 생성, 상태 관리, 배송 추적 |
| 결제 | 빌링키 자동결제, 1시간 내 직접결제(낙찰 실패 시), 결제 재시도, 패널티 |
| 환불 | 환불 요청, 관리자 승인/거절 (**PortOne 부분환불 미포함**) |
| 정산 (Settlement) | 배송 완료 후 정산 테이블 기록, 판매자 정산 관리 |
| 배송 | 배송 상태 업데이트, 구매자 확인 |
| 커뮤니티 | 자유게시판 CRUD, 거래게시판 CRUD, 2depth 댓글 |
| 채팅 | 거래 게시판 기반 1:1 채팅방 생성, 실시간 메시지(WebSocket), 채팅 이후 거래는 자율 |
| 알림 | 알림 유형별 발송, 읽음 처리, 알림 목록 조회 |
| 찜 | 경매 좋아요 토글, 찜 목록 조회 |
| 관리자 | 카탈로그 승인/거절, 경매 검수, 배송 관리, 정산 관리 |

---

## 11. 리스크 및 고려사항

### 11.1 기술적 리스크

| 리스크 | 영향도 | 대응 방안 |
| --- | --- | --- |
| 입찰 동시성 충돌 | 높음 | Redis 분산락으로 직렬화 처리 |
| 결제 실패 후 데이터 불일치 | 높음 | PortOne Webhook + 보상 트랜잭션 |
| 즉시구매 결제 대기 중 상태 충돌 | 높음 | PAYMENT_PENDING 상태에서 추가 입찰 차단 |
| 스케줄러 중복 실행 | 중간 | ShedLock 또는 DB락으로 단일 실행 보장 |
| 실물 카드 배송 분쟁 | 중간 | 검수(Inspection) 단계 필수화, 스냅샷 보존 |

---

### 11.2 확정된 정책

| 항목 | 확정 내용 |
| --- | --- |
| 판매자 글 등록 조건 | 회원만 글 등록 가능 |
| 계좌 정보 등록 시점 | 회원가입 후 마이페이지에서 원하는 시기에 등록(수정) 가능 |
| 빌링키 등록 시점 | 입찰 시도 전 반드시 등록 필요 |
| 일반거래 검수 | 불필요. 서비스 로직상 관여하지 않음 |
| 정산 시스템 | settlements 테이블 운영으로 관리 |
| MSA 전환 시점 | 프로젝트 진행 속도가 계획보다 빠를 경우에만 부분적 분리 고려 |

---

## 12. 용어 정의

| 용어 | 설명 |
| --- | --- |
| 빌링키 (Billing Key) | PortOne에서 발급하는 카드 자동결제 키. 입찰 및 이벤트 구매 전 사전 등록 필수 |
| 즉시구매가 (Buyout Price) | 해당 금액 이상 입찰 시 경매 즉시 결제 대기(PAYMENT_PENDING) 전환 후 결제 완료 시 ENDED |
| 검수 (Inspection) | 플랫폼에 실물 카드 발송 후 관리자가 정품 여부를 확인하는 단계 |
| OUTBID | 이전 최고 입찰자가 새로운 입찰로 인해 밀려나는 상태 |
| Snapshot | 경매 낙찰 / 주문 확정 시점의 가격 정보를 변경 불가 형태로 저장한 레코드 |
| unpaid_strike | 낙찰 후 미결제로 발생한 패널티 누적 횟수 |
| Settlement | 배송 완료 후 판매자에게 수수료를 제외한 금액을 정산하는 테이블 |
| TCGdex | 포켓몬 트레이딩 카드 게임 외부 카드 데이터 API |
| PSA / BGS | 카드 등급 감정 전문 기관. 본 서비스는 PSA_10 / PSA_9 두 등급만 지원 |
| PortOne V2 | 한국 PG사 통합 결제 플랫폼 (KG이니시스 등 연동) |
| RTR | Refresh Token Rotation. Refresh Token 사용 시 새 토큰 발급 + 기존 토큰 즉시 무효화 |
| 토큰 블랙리스트 | 로그아웃된 Access Token을 Redis에 등록하여 재사용 방지하는 보안 기법 |

---

*© 2026 POCAT Team — 7조 로켓단*
