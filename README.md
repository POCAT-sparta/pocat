# POCAT — 포켓몬 카드 경매 플랫폼

> **Rocket Crew** 팀이 개발한 포켓몬 카드 실시간 경매 플랫폼입니다.  
> 카드 검수 → 경매 → 낙찰 → 결제 → 정산까지 전체 거래 흐름을 다루며,  
> AI 어시스턴트·실시간 채팅·커뮤니티 기능까지 통합 제공합니다.

---

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [시스템 아키텍처](#2-시스템-아키텍처)
3. [기술 스택](#3-기술-스택)
4. [서버 구성](#4-서버-구성)
5. [주요 기능](#5-주요-기능)
6. [ERD](#6-erd)
7. [API 명세](#7-api-명세)
8. [배치 Job 목록](#8-배치-job-목록)
9. [모니터링](#9-모니터링)
10. [테스트](#10-테스트)
11. [CI/CD](#11-cicd)
12. [로컬 실행 방법](#12-로컬-실행-방법)
13. [팀원 역할](#13-팀원-역할)

---

## 1. 프로젝트 소개

POCAT은 포켓몬 TCG 카드 거래의 새로운 기준을 제시하는 **실시간 신뢰 기반 경매 거래 플랫폼**입니다.
단순한 중고 거래를 넘어, 전문 검수 시스템과 실시간 경매를 결합해 수집가와 트레이더 모두가 안심하고 참여할 수 있는 환경을 만들어갑니다.

### 핵심 가치

**신뢰 (Trust)**

모든 카드는 거래 전 철저한 전문 검수 과정을 거칩니다. 상태 등급, 진위 여부, 희귀도까지 꼼꼼히 확인해 허위 매물 없는 투명한 거래를 보장합니다.

**실시간성 (Real-time)**

라이브 경매 시스템을 통해 원하는 카드를 실시간으로 경쟁 입찰할 수 있습니다. 시장 가격이 자연스럽게 형성되어 판매자와 구매자 모두에게 공정한 기회를 제공합니다. 

**전문성 (Expertise)**

포켓몬 TCG에 특화된 플랫폼으로, 카드의 에디션, 언어판, 등급 등 전문적인 정보를 체계적으로 제공합니다. 입문자부터 고급 수집가까지 모두를 위한 정보 환경을 구축합니다. 

**커뮤니티 (Community)**

단순 거래를 넘어 포켓몬 TCG를 사랑하는 사람들이 모이는 공간입니다. 수집 경험과 정보를 나누며 함께 성장하는 커뮤니티를 지향합니다.

| 구분 | 내용 |
|------|------|
| 팀명 | Rocket Crew |
| 개발 기간 | 2026년 5월 ~ 2026년 6월 |
| 핵심 도메인 | 포켓몬 카드 경매 · 결제 · AI 분석 · 커뮤니티 |

### 핵심 흐름

```
카드 등록 → 관리자 검수 → 경매 활성화 → 실시간 입찰 → 낙찰 → 결제(직접/자동) → 정산
```

---

## 2. 아키텍처

> 시스템 아키텍쳐
> 
![시스템 아키텍처](https://cdn.kuromi.click/projectinfo/%EC%8B%9C%EC%8A%A4%ED%85%9C%20%EC%95%84%ED%82%A4%ED%83%9D%EC%B3%90.png)

> 배포 아키텍쳐
> 
![배포 아키텍쳐](https://cdn.kuromi.click/projectinfo/cloudArci2.png) 

---

## 3. 기술 스택

### Backend

| 분류 | 기술                             |
|------|--------------------------------|
| Language / Runtime | Java 17                        |
| Framework | Spring Boot 3.5                |
| ORM | Spring Data JPA + QueryDSL 5.0 |
| Security | Spring Security + JWT          |
| 실시간 통신 | WebSocket (STOMP)              |
| 메시지 브로커 | Apache Kafka 3.7.0 (KRaft, 3-broker) |
| 캐시 | Redis Cluster (6-node, Redisson) |
| 검색 | ElasticSearch 8.18             |
| AI | Spring AI + Google Gemini 2.5 Flash (OpenAI-compatible) |
| AI 벡터스토어 | ElasticSearch Vector Store (768-dim, cosine) |
| 결제 | PortOne (포트원)                  |
| 파일 스토리지 | AWS S3                         |
| DB 마이그레이션 | Flyway                         |
| 분산 잠금 | Redisson / ShedLock            |
| 회로차단기 | Resilience4j                   |

### Infrastructure

| 분류 | 기술 |
|------|-----|
| Database | MySQL 8.0 (Master / Read Replica) |
| Container | Docker, Docker Compose |
| Cloud | AWS ECS + ECR + S3 + Parameter Store |
| CI/CD | GitHub Actions |
| 모니터링 | Prometheus + Grafana + Loki + n8n |
| 로그 수집 | Logstash + Loki Logback Appender |
| 알림 자동화 | n8n |

### Frontend

| 분류 | 기술 |
|------|------|
| Framework | [(pocat-front) ](https://github.com/POCAT-sparta/pocat-front)|
| 스타일 | 포켓몬 UI 테마 |

---

## 4. 서버 구성

| 서버 | 설명 | 주요 기술 |
|------|------|-----------|
| **pocat** | 메인 API 서버 | Spring Boot, JPA, Redis, Kafka, ES, Spring AI |
| **pocat-batch** | 배치 서버 (11개 Job) | Spring Batch, ShedLock, Redis, Kafka |
| **pocat-eventServer** | 이벤트/워커 서버 | WebSocket, Redis Pub/Sub, Kafka Consumer |
| **pocat-monitoring** | 모니터링 스택 | Prometheus, Grafana, Loki, n8n |

### 서버별 역할

#### pocat (메인 API)
- 비즈니스 도메인 API 전체 (인증, 카드, 경매, 결제, 커뮤니티, AI 등) 
- JWT 인증, QueryDSL, DB Master/Slave 구조
- Kafka 이벤트 Produce
- Outbox 패턴 릴레이 (배치 위임)
- ElasticSearch 카드 색인 및 벡터 임베딩

#### pocat-batch
- Spring Batch + ShedLock으로 분산 환경에서 중복 실행 방지
- 11개 스케줄 Job 운영 (상세는 [pocat-batch](https://github.com/POCAT-sparta/pocat-batch) 참고)

#### pocat-eventServer
- 실시간 채팅 (WebSocket + Redis Pub/Sub)
- Kafka Consumer: 경매·결제·주문 이벤트를 받아 알림 발행

#### pocat-monitoring
- **Prometheus**: 모든 서버의 메트릭 수집 (JVM, Kafka, Redis, MySQL, ES)
- **Grafana**: 대시보드 시각화
- **Loki**: 구조화 로그 수집 (Logback Appender)
- **n8n**: 에러 로그를 수집하여 반복 발생하는 에러 알람 발송

---

## 5. 주요 기능

### 5-1. 포켓몬 카드 등록 & 검수

- 사용자가 카드를 등록하면 **PENDING** 상태로 대기
- 관리자가 검수 승인 → **ACTIVE** / 거절 → **REJECTED**
- TCGDex API 연동으로 카드 메타데이터 자동 동기화 (CardSync Batch)
- ElasticSearch 색인: 카드명(한국어 우선), 등급, 카테고리, 시리즈 검색

### 5-2. 실시간 경매

- **경매 상태**: `PENDING → INSPECTING → APPROVED/REJECTED → ACTIVE → ENDED / NO_BIDDER / CANCELLED / PAYMENT_PENDING`
- Redis TTL 만료 이벤트로 경매 자동 종료
- Kafka 이벤트로 낙찰·취소·후처리 비동기 처리
- **즉시구매(Buyout)** 지원: Redisson 분산 락으로 동시성 제어
- 입찰 동시성 테스트: `AuctionBidConcurrencyIntegrationTest`

### 5-3. 결제 시스템 (포트원 연동)

- **직접결제(PG_DIRECT)**: 카드/간편결제 직접 처리
- **자동결제(BILLING_KEY)**: 빌링키 등록 후 서버 측 자동 청구
- 결제 성공/실패 → Kafka 이벤트 → Outbox 패턴으로 안정적 전달
- Webhook 멱등성 처리 (`webhook_events` 테이블)
- 자동 결제 실패 시 원본 key 및 shadow key 생성 → 직접 결제 기회 제공(1시간)
- 직접 결제도 최종 실패 시 **2등 낙찰자에게 결제 기회 에스컬레이션** (2등 입찰가 기준 주문 생성)
- 동시성 보호: `PaymentConcurrencyIntegrationTest`

### 5-4. 정산 & 환불

- 낙찰 후 결제 완료 시 자동 정산 생성
- 플랫폼 수수료 차감 후 판매자 정산액 산출
- 환불 요청 → 관리자 승인 → 포트원 환불 API 호출
- 환불 실패 시 자동 재시도 배치 (RetryableFailure → RefundRetryJob) 

### 5-5. AI 어시스턴트

| 기능 | 설명 |
|------|------|
| **AI 채팅** | Gemini 2.5 Flash 기반 멀티턴 대화 |
| **SSE 스트리밍** | 응답 토큰 실시간 스트리밍 |
| **RAG** | ElasticSearch 벡터 검색으로 카드 정보 기반 답변 |
| **Tool Calling** | 가격 조회·도감 검색 등 외부 데이터 연결 |
| **카드 AI 분석** | 등급별 가치 분석 + TCGDex 시세 반영 |
| **Circuit Breaker** | Resilience4j로 AI 서비스 장애 격리 |
| **Rate Limiter** | 분당 10회 / 임베딩 분당 80회 제한 |

### 5-6. 1:1 채팅 (거래게시판 연동)

- 거래게시판 글에서 채팅방 생성 → 구매자·판매자 1:1 채팅
- Redis Pub/Sub으로 WebSocket 브로드캐스트
- 읽음 처리, 채팅방 나가기 지원
- 채팅 알림은 pocat-eventServer에서 Kafka 소비 후 발행

### 5-7. 커뮤니티

- **자유게시판**: Redis 조회수 버퍼링, 7일 인기글 랭킹
- **거래게시판**: 카드 개인 거래 중개, CQRS 패턴, IP 암호화
- **댓글**: 계층형 댓글 (parent_id)
- **좋아요**: 경매/게시글 좋아요

### 5-8. 알림

- 경매·결제·주문·환불·정산 등 24종 알림 타입
- Kafka Consumer → 알림 저장 → 읽음 처리 API

### 5-9. 보안 

| 항목 | 내용 |
|------|------|
| 인증 | JWT (Access + Refresh Token) |
| 내부 API | POCAT_INTERNAL_TOKEN 헤더 기반 인가 |
| Rate Limiting | Redis 기반 슬라이딩 윈도우 — 입찰 30회, 채팅 20회, AI 채팅·결제·주문 10회, 환불·게시글 5회, AI 임베딩 80회 (분당) |
| 금지어 필터 | 제목·내용 금지어 검사 |
| IP 처리 | X-Forwarded-For 파싱, 거래게시판 IP 암호화 |
| 관리자 권한 | `ADMIN` Role 기반 API 분리 |

---

## 6. ERD


![POCAT 260617.png](images/POCAT%20260617.png)

---

### 주요 테이블 관계

```
series ──< pokemon_sets ──< cards
                              │
                         auctions ──< auction_bids
                              │      └─< auction_snapshots
                              │
users ──────────────────< orders ──< payments ──< refunds
  │                           │
  ├──< notifications      settlements
  ├──< free_posts ──< comments
  ├──< trade_posts ──< chats ──< chat_messages
  └──< ai_chat_sessions ──< ai_chat_messages
```

### 주요 테이블 설명

| 테이블 | 설명 |
|--------|------|
| `users` | 회원, 빌링키, 입찰차단 여부 |
| `cards` | 포켓몬 카드 (PENDING/ACTIVE/REJECTED), 등급(PSA_9/10, BGS_10) |
| `auctions` | 경매, 시작가·즉시구매가·종료 시각 |
| `auction_bids` | 입찰 내역, 상태(LEADING/OUTBID/WON/LOST/CANCELLED) |
| `auction_snapshots` | 경매 종료 시점 스냅샷 (최종가 보존) |
| `orders` | 주문 (AUCTION/BUYOUT 타입), 결제 기한 |
| `payments` | 결제 (PG_DIRECT/BILLING_KEY 타입) |
| `settlements` | 정산 (플랫폼 수수료 차감 후 판매자 금액) |
| `refunds` | 환불, 재시도 상태 관리 |
| `outbox_events` | Kafka 전달 보장을 위한 Outbox 패턴 테이블 |
| `webhook_events` | 포트원 Webhook 멱등성 처리 |
| `ai_chat_sessions` | AI 멀티턴 대화 세션 |
| `ai_prompt_template` | 카드 등급별 AI 프롬프트 템플릿 |
| `notifications` | 22종 알림 타입 |
| `free_posts` | 자유게시판 |
| `trade_posts` | 거래게시판 |
| `chats` / `chat_messages` | 1:1 채팅 |

---

## 7. API 명세

로컬 실행 후 Swagger UI에서 전체 명세 확인 가능합니다.

```
http://localhost:8080/swagger-ui/index.html
```

### 주요 API 그룹

#### 인증 (`auth`)

| Method | Path                  | 설명 |
|--------|-----------------------|------|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login`     | 로그인 (JWT 발급) |
| POST | `/api/v1/auth/reissue`   | Access Token 재발급 |
| DELETE | `/api/v1/auth/logout`    | 로그아웃 |

#### 카드 (`cards`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/cards` | 카드 목록 검색 (ElasticSearch) |
| POST | `/api/v1/cards` | 카드 등록 |
| GET | `/api/v1/cards/{cardId}` | 카드 상세 조회 |
| PATCH | `/api/v1/admin/cards/{cardId}/approve` | 카드 검수 승인 (관리자) |
| PATCH | `/api/v1/admin/cards/{cardId}/reject` | 카드 검수 거절 (관리자) |

#### 경매 (`auctions`)

| Method | Path | 설명 |
|--------|------|------|
| POST   | `/api/v1/auctions` | 경매 등록 |
| GET    | `/api/v1/auctions` | 경매 목록 조회 |
| GET    | `/api/v1/auctions/{auctionId}` | 경매 상세 조회 |
| POST   | `/api/v1/auctions/{auctionId}/bids` | 입찰 |
| POST   | `/api/v1/auctions/{auctionId}/buyout` | 즉시구매 |
| PATCH  | `/api/v1/auctions/{auctionId}` | 경매 취소 |

#### 결제 (`payments`)

| Method | Path                    | 설명             |
|--------|-------------------------|----------------|
| POST | `/api/v1/payments`      | 결제 요청          |
| POST | `/api/v1/payments/webhook` | 포트원 Webhook 수신 |
| GET | `/api/v1/payments/{paymentUid}` | 결제 내역 조회       |

#### 주문 (`orders`)

| Method | Path                        | 설명         |
|--------|-----------------------------|------------|
| GET | `/api/v1/orders/me`         | 내 주문 목록 조회 |
| GET | `/api/v1/orders/{orderUid}` | 주문 상세      |

#### 정산 (`settlements`)

| Method | Path                                  | 설명      |
|--------|---------------------------------------|---------|
| GET | `/api/v1/settlements/me`              | 내 정산 목록 |
| GET | `/api/v1/settlements/{settlementUid}` | 정산 상세   |

#### 환불 (`refunds`)

| Method | Path | 설명          |
|--------|------|-------------|
| POST | `/api/v1/refunds` | 환불 요청       |
| GET | `/api/v1/refunds/{refundId}` | 환불 상세 조회    |
| PATCH | `/api/v1/admin/refunds/{refundId}/approve` | 환불 승인 (관리자) |
| PATCH | `/api/v1/admin/refunds/{refundId}/reject` | 환불 거절 (관리자) |

#### AI (`ai`)

| Method | Path                              | 설명 |
|--------|-----------------------------------|------|
| POST | `/api/ai/assistant/chat`          | AI 어시스턴트 채팅 |
| GET | `/api/ai/assistant/stream`        | SSE 스트리밍 응답 |
| POST | `/api/ai/cards/{cardId}/analysis` | 카드 AI 가치 분석 |
| POST | `/api/v1/admin/ai/reindex`        | 카드 벡터 재색인 (관리자) |

#### 커뮤니티

| Method | Path                                | 설명 |
|--------|-------------------------------------|------|
| GET/POST | `/api/v1/posts/free`                | 자유게시판 목록/작성 |
| GET/POST | `/api/v1/posts/trade`               | 거래게시판 목록/작성 |
| POST | `/api/v1/comments` | 댓글 작성 |
| POST | `/api/v1/likes`   | 좋아요 토글 |

#### 알림 (`notifications`)

| Method | Path                                          | 설명 |
|--------|-----------------------------------------------|------|
| GET | `/api/v1/notifications`                       | 알림 목록 |
| PATCH | `/api/v1/notifications/{notificationId}/read` | 읽음 처리 |

#### 포켓몬 도감 (`pokemons`, `series`, `pokemon-sets`)

| Method | Path                 | 설명 |
|--------|----------------------|------|
| GET | `/api/v1/admin/pokemons` | 포켓몬 목록 |
| GET | `/api/v1/admin/series` | 시리즈 목록 |
| GET | `/api/v1/admin/sets` | 세트 목록 |

---

## 8. 배치 Job 목록

`pocat-batch` 서버에서 Spring Batch + ShedLock으로 운영합니다.

| Job | 설명                      | 주기               |
|-----|-------------------------|------------------|
| `AuctionActivationJob` | 예약 경매를 시작 시각에 ACTIVE 전환 | 매 19:00          |
| `AuctionExpirationJob` | 종료 시각 도래한 경매 ENDED 처리   | 19:05 ~ 19:30 매분 |
| `AuctionRankingJob` | 경매 랭킹 집계 (좋아요·입찰 가중치 합산) | 1분마다             |
| `OutboxRelayJob` | Outbox 미처리 이벤트 Kafka 재발행 | 5초마다             |
| `OrderCompletionJob` | 배송 완료 후 일정 기간 지난 주문 완료 처리 | 매 02:00          |
| `RefundRetryJob` | 환불 실패(Retryable) 건 재시도  | 1분마다             |
| `BuyoutRecoveryJob` | 즉시구매 결제 미완료 주문 복구       | 1분마다             |
| `CardSyncJob` | TCGDex API 카드 메타데이터 동기화 | 매주 일요일 00:00     |
| `FreePostRankingJob` | 자유게시판 7일 인기글 랭킹 집계      | 1분마다             |
| `ViewCountFlushJob` | Redis 조회수 버퍼 → DB 반영    | 1분마다             |
| `AiSessionCleanupJob` | 만료된 AI 채팅 세션 정리         | 5분마다             |

---

## 9. 모니터링

### 스택 구성

| 도구 | 역할 |
|------|------|
| **Prometheus** | 모든 서버 메트릭 수집 (Spring Actuator + 각종 Exporter) |
| **Grafana** | 메트릭 대시보드 시각화 |
| **Loki** | 구조화 로그 수집 (Logback → Loki Appender) |
| **n8n** | `/internal/stats/daily` 호출 → 일일 통계 자동 알림 |

### 수집 대상

| Exporter | 대상 |
|----------|------|
| Spring Actuator | 메인·배치·이벤트 서버 JVM 메트릭 |
| kafka-exporter | Kafka 브로커 메트릭 |
| redis-exporter | Redis Cluster 메트릭 |
| mysqld-exporter | MySQL 쿼리·연결 메트릭 |
| elasticsearch-exporter | ES 인덱스·검색 메트릭 |

### 이상 거래 감지

- 최종 낙찰가 및 즉시구매가가 해당 카드 시장 평균가(6개월 기준)의 3배를 초과하면 AUCTION_ANOMALY 경고 로그를 발행합니다
- n8n이 해당 로그 감지 → Slack/Email 알림

---

## 10. 테스트

### 테스트 현황

| 구분 | 수량 |
|------|------|
| 전체 테스트 파일 | 105개 |
| 단위 테스트 | 서비스·컨트롤러 레이어 전반 |
| 통합 테스트 | Kafka, ElasticSearch (TestContainers) |
| 동시성 테스트 | 입찰·결제·환불 경합 상황 |
| 멱등성 테스트 | 결제·정산 중복 처리 방어 |

### 주요 테스트

| 테스트 | 내용 |
|--------|------|
| `AuctionBidConcurrencyIntegrationTest` | 동시 입찰 경합 테스트 |
| `AuctionBuyoutConcurrencyIntegrationTest` | 즉시구매 동시 요청 |
| `PaymentConcurrencyIntegrationTest` | 결제 중복 요청 방어 |
| `RefundConcurrencyIntegrationTest` | 환불 동시 처리 |
| `ConfirmPaymentIdempotencyIntegrationTest` | 결제 멱등성 검증 |
| `SettlementIdempotencyIntegrationTest` | 정산 멱등성 검증 |
| `PaymentKafkaAtLeastOnceDeliveryTest` | Kafka At-Least-Once 전달 보장 |
| `CardEsAliasReindexServiceIntegrationTest` | ES 무중단 재색인 |
| `AutoPaymentIdempotencyIntegrationTest` | 자동결제 중복 방어 |

### 테스트 실행

```bash
# 기본 테스트 (동시성·성능·통합 제외)
./gradlew test

# ElasticSearch TestContainers 통합 테스트 (Docker 필요)
./gradlew integrationTest
```

---

## 11. CI/CD

GitHub Actions → AWS ECR → AWS ECS 파이프라인

```
push to dev 브랜치
      │
      ▼
[GitHub Actions]
  1. JDK 17 설정
  2. ./gradlew build (테스트 포함)
  3. Docker Buildx (ARM64 이미지)
  4. ECR 푸시
  5. ECS 배포 (force-new-deployment)
  6. ECS 안정화 대기
```

- **대상 브랜치**: `dev`
- **이미지 플랫폼**: `linux/arm64` (AWS Graviton)
- **이미지 경량화**: `amazoncorretto:17-alpine` 베이스 + `.dockerignore`로 JAR만 포함 + GHA 레이어 캐시 (`cache-from/to: type=gha,mode=max`)
- **레지스트리**: AWS ECR (`pocat-backend`)
- **클러스터**: `pocat-app-cluster`

---

## 12. 로컬 실행 방법

### 사전 요구사항

- Docker & Docker Compose
- Java 17
- `.env` 파일 설정 (`.env.example` 참고)

### 인프라 실행

```bash
# pocat 디렉토리에서
cd pocat
docker compose up -d
```

포함 서비스: MySQL 8.0, Redis Cluster (6-node), Kafka KRaft (3-broker), ElasticSearch 8, Logstash, Kibana, Kafka UI

### 메인 서버 실행

```bash
cd pocat
./gradlew bootRun
```

### 배치 서버 실행

```bash
cd pocat-batch
./gradlew bootRun
```

### 이벤트 서버 실행

```bash
cd pocat-eventServer
./gradlew bootRun
```

### 로컬 접속 주소

| 서비스 | 주소 |
|--------|------|
| 메인 API | http://localhost:8080 |
| Kafka UI | http://localhost:8085 |
| Kibana | http://localhost:5601 |
| ElasticSearch | http://localhost:9200 |

---

## 13. 팀원 역할

| 팀원      | 담당 영역                                                                                                                                                                                                           |
|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **이석형** | 프로젝트 관리, PR 리뷰 및 브랜치 전략, 유저, 자유게시판, 찜, AI 기능 (RAG·Tool Calling·SSE), AI Code Review 도입, ES vector store embedding, Redis 캐싱, Spring Batch 분리, 보안, RDS Read Replica, flyway 도입, 모니터링 알림 자동화, 프론트 UX 개선, 테스트 코드 작성 |
| **최재민** | 결제(포트원), 환불, 실시간 채팅(WebSocket), 거래게시판, 워커 서버 구축, 모니터링, AI 분석 보강, 프론트엔드, 포트원 Webhook 연동, 멱등성 보강, 모니터링 알림 자동화, 인프라 트러블슈팅                                                                                          |
| **박소영** | 경매, 입찰, 경매/주문/결제 상태 및 트랜잭션 관리, Kafka 후처리, Redis TTL 기반 자동 종료, 결제 안정화, CI/CD 파이프라인, 배포 인프라 구축, 경매 플로우 테스트                                                                                                  |
| **정태규** | JWT 인증/인가, 카드, 외부 API 연동, ElasticSearch 검색 안정화 (한국어 카드명, 정렬 수정), ELK 스택 환경 구축, Alias 무중단 인덱싱, K6                                                                                                                |
| **이승현** | 주문, 정산, 대용량 트래픽 대비(Kafka 플로우 구축), OutBox 패턴, Redis pub/sub(알림), CI/CD 파이프라인, 배포 인프라 구축                                                                                                                          |
