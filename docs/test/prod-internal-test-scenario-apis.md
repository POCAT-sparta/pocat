# Prod Internal Test Scenario APIs

prod profile 배포 환경에서 Toss/PortOne 테스트 결제 제약을 우회하고, 경매 종료부터 자동결제 실패, 직접결제 기한 만료, 차순위 승격까지 검증하기 위한 시나리오 문서입니다.

상세 API 동작 설명은 [prod-internal-test-api-reference.md](prod-internal-test-api-reference.md)를 같이 확인하세요.

## 공통 준비

모든 테스트 API는 내부 API입니다.

```http
X-Internal-Token: {POCAT_INTERNAL_TOKEN}
```

배포 환경에는 아래 설정이 필요합니다.

```text
POCAT_TEST_SCENARIOS_ENABLED=true
```

꺼져 있으면 `TEST_SCENARIO_DISABLED`로 차단됩니다. 일반 사용자 JWT나 관리자 JWT가 아니라 `X-Internal-Token`으로 호출해야 합니다.

## 공통 테스트 데이터 준비

아래 데이터가 있으면 모든 시나리오를 안정적으로 진행할 수 있습니다.

| 역할 | 필요 데이터 |
|---|---|
| 판매자 A | 승인된 카드 1장 이상 |
| 구매자 B | 입찰 가능한 일반 사용자, 빌링키 등록 여부는 실제 자동결제를 쓰지 않으므로 필수 아님 |
| 구매자 C | 차순위 승격 테스트용 일반 사용자 |
| 관리자 | 카드 승인, 경매 검수 통과 권한 |

권장 선행 흐름:

1. 판매자 A 회원가입/로그인
2. 구매자 B, 구매자 C 회원가입/로그인
3. 판매자 A가 카드 등록
4. 관리자가 카드 승인
5. 판매자 A가 경매 생성
6. 관리자가 경매 검수 통과
7. 구매자 B가 입찰
8. 구매자 C가 더 높은 금액으로 입찰

이렇게 만들면 구매자 C가 1순위, 구매자 B가 차순위가 됩니다. 이후 구매자 C의 결제를 실패시키고 직접결제 기한을 만료시키면 구매자 B에게 결제 기회가 넘어가는지 확인할 수 있습니다.

## 시나리오 1. Redis 경매 만료 감지 검증

목적: 경매 종료 Redis TTL key가 실제로 만료되고 `AuctionExpirationRedisSubscriber`가 만료 이벤트를 받아 경매를 종료하는지 검증합니다.

### 준비 상태

- 경매 상태: `ACTIVE`
- `endedAt`: 미래 시각
- 입찰자가 있는 케이스와 없는 케이스를 각각 테스트하면 좋습니다.
- 경매 검수 통과 직후 Redis 종료 key가 이미 잡혀 있을 수 있으므로, 이 API가 새 TTL로 다시 잡아줍니다.
- 차순위 승격까지 이어서 보려면 구매자 B와 C가 모두 입찰한 경매를 사용하세요.

### 호출

```bash
curl -X POST "https://{host}/internal/test/auctions/{auctionId}/expire-in?seconds=600" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

`seconds`는 10초 이상 3600초 이하입니다. 기본값은 600초입니다.

### 내부에서 실행되는 로직

- `AuctionTestScenarioService.scheduleExpiration` 실행
- `AuctionRepository.updateEndedAtByIdAndStatus`로 `endedAt = now + seconds` 변경
- `AuctionExpirationRedisService.setExpirationKeys`로 Redis key 재등록
- Redis key: `auction:end:{auctionId}`
- shadow key: `auction:end:shadow:{auctionId}`

### 기다린 뒤 기대 상태

Redis key가 만료되면 아래 흐름이 자동 실행됩니다.

- `AuctionExpirationRedisSubscriber.onMessage`
- `AuctionLifecycleService.closeExpiredAuction(auctionId)`

입찰자가 있으면:

- 경매 상태: `ACTIVE -> ENDED`
- 최고 입찰자 bid 상태: `LEADING -> WON`
- 밀린 입찰자 bid 상태: `OUTBID -> LOST`
- `AuctionEndedEvent` 발행
- Outbox topic: `auction`
- Kafka topic: `auction`, eventType: `auction.ended`
- 알림:
  - 낙찰자: `AUCTION_WON`, 메시지 `낙찰되었습니다.`
  - 판매자: `AUCTION_SOLD`, 메시지 `카드가 낙찰되었습니다.`
  - 패찰자: `AUCTION_LOST`, 메시지 `패찰하셨습니다.`

입찰자가 없으면:

- 경매 상태: `ACTIVE -> NO_BIDDER`
- `AuctionEndedEvent` 발행
- winnerId가 없으므로 낙찰자/판매자 낙찰 알림은 생성되지 않습니다.

### 확인 위치

- `GET /api/v1/auctions/{auctionId}` 또는 관리자 경매 목록에서 경매 상태 확인
- `GET /api/v1/auctions/{auctionId}/bids`에서 bid 상태 확인
- 낙찰자/판매자/패찰자 계정으로 `GET /api/v1/notifications` 확인
- 서버 로그에서 `Redis 만료 이벤트 기반 경매 종료 처리` 또는 `AUCTION` 관련 로그 확인
- Outbox/Kafka 로그에서 `eventType=auction.ended`, topic `auction` 확인

## 시나리오 2. 경매 마감 로직 즉시 검증

목적: Redis TTL 만료를 기다리지 않고 경매 마감 로직 자체를 바로 검증합니다.

### 준비 상태

- 경매 상태: `ACTIVE`
- 입찰이 1건 이상 있으면 낙찰 플로우 검증 가능
- Redis 만료 감지 자체를 보려는 목적이 아니라면 이 시나리오가 가장 빠릅니다.
- 경매 마감 후 주문까지 필요하면 입찰자가 있어야 합니다.

### 호출

1. 경매를 즉시 마감 가능한 상태로 만듭니다.

```bash
curl -X POST "https://{host}/internal/test/auctions/{auctionId}/expire-now" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

2. 기존 내부 마감 API를 호출합니다.

```bash
curl -X POST "https://{host}/internal/auctions/{auctionId}/close-expired" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

### 내부에서 실행되는 로직

`expire-now`:

- `endedAt = now - 5 seconds`로 변경
- Redis 경매 만료 key 삭제
- 다음 단계로 `/internal/auctions/{auctionId}/close-expired` 호출을 안내

`close-expired`:

- 분산락 `auction:lock:{auctionId}` 획득
- 경매가 `ACTIVE`이고 `endedAt <= now`인지 확인
- 입찰자 유무에 따라 `ENDED` 또는 `NO_BIDDER` 처리
- `AuctionEndedEvent` 발행

### 기대 이벤트와 알림

입찰자가 있는 경우 시나리오 1과 동일하게 `auction.ended` 이벤트와 `AUCTION_WON`, `AUCTION_SOLD`, `AUCTION_LOST` 알림이 생성되어야 합니다.

### 확인 위치

- `close-expired` 응답 data가 `true`인지 확인
- 경매 상세/관리자 목록에서 `ENDED` 또는 `NO_BIDDER` 확인
- 입찰 내역에서 `WON`, `LOST` 상태 확인
- 알림 목록에서 경매 종료 관련 알림 확인

## 시나리오 3. 자동결제 실패 주입

목적: Toss/PortOne 테스트 환경에서 실제 카드 자동결제 실패를 만들 수 없으므로, 우리 서버가 자동결제 실패를 받은 이후의 도메인 로직을 검증합니다.

### 준비 상태

- 경매 마감으로 낙찰 주문이 생성되어 있어야 합니다.
- 주문 상태: `PAYMENT_PENDING`
- 즉시구매 주문이 아니라 경매 낙찰 주문이면 직접결제 대기 상태로 전환됩니다.
- `orderUid`는 주문 목록, 주문 상세, DB, 로그 중 편한 방법으로 확인합니다.
- 이 단계 전에는 반드시 경매가 `ENDED`가 되어 있어야 합니다.
- 경매가 `ACTIVE`인 상태에서는 낙찰 주문이 없으므로 이 API를 호출할 수 없습니다.
- 일반 `/internal/auctions/{auctionId}/close-expired`는 정상 운영 흐름대로 자동결제를 바로 시도합니다. 배포 환경에서는 `PAYMENT_PENDING` 상태를 잡기 어렵기 때문에 이 시나리오에서는 테스트 전용 `/internal/test/auctions/{auctionId}/close-expired-without-auto-payment`로 주문을 준비합니다.

### 호출

1. 경매를 즉시 마감 가능한 상태로 만듭니다.

```bash
curl -X POST "https://{host}/internal/test/auctions/{auctionId}/expire-now" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

2. 경매를 종료하고 낙찰 주문을 만들되, 자동결제 이벤트는 막습니다.

```bash
curl -X POST "https://{host}/internal/test/auctions/{auctionId}/close-expired-without-auto-payment" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

응답의 `orderUid`를 다음 단계에서 사용합니다. 이 API는 `auction.ended`, `order.created`를 발행하지 않으므로 낙찰/판매/패찰 알림이 오지 않는 것이 정상입니다. 경매 종료 알림 검증은 시나리오 1 또는 2에서 따로 진행하세요.

3. 자동결제 실패를 주입합니다.

```bash
curl -X POST "https://{host}/internal/test/payments/{orderUid}/auto-fail" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

### 내부에서 실행되는 로직

- `PaymentTestScenarioService.injectAutoPaymentFailure` 실행
- 주문 상태가 `PAYMENT_PENDING`인지 확인
- `PaymentCommandService.createBillingKeyPaymentIfAbsent`로 `BILLING_KEY` 결제 레코드 생성 또는 재사용
- `FailureService.handleAutoPaymentFailure(paymentId, orderId)` 호출
- 결제 상태: `PENDING -> FAILED`
- 경매 낙찰 주문 상태: `PAYMENT_PENDING -> AUTO_PAYMENT_FAILED`
- `FailureService.autoPaymentFailEvent` 호출
- Outbox topic: `payment`
- Kafka topic: `payment`, eventType: `payment.auto.failed`

### 기대 이벤트와 알림

- 결제 이벤트: `payment.auto.failed`
- 알림:
  - 구매자: `AUTO_PAYMENT_FAILED`
  - 메시지: `자동결제에 실패했습니다. 직접 결제를 진행해 주세요.`
  - relatedData: `orderUid`

### 확인 위치

- `GET /api/v1/orders/{orderUid}`에서 주문 상태 `AUTO_PAYMENT_FAILED` 확인
- 결제 상세 또는 DB에서 `BILLING_KEY` 결제 상태 `FAILED` 확인
- 구매자 계정으로 `GET /api/v1/notifications` 확인
- Outbox/Kafka 로그에서 topic `payment`, eventType `payment.auto.failed` 확인
- 이후 `POST /api/v1/payments` 직접결제 생성 API가 가능한지 확인

### 왜 일반 `close-expired`를 쓰지 않는가

일반 마감 API는 실제 운영 흐름 검증에는 맞지만, 자동결제 실패 주입 준비에는 맞지 않습니다. 내부 흐름이 `AuctionLifecycleService.closeExpiredAuction -> AuctionEndedEvent -> AuctionEventConsumer.createOrderFromAuction -> OrderCreatedEvent -> PaymentKafkaConsumer.autoPayment`로 이어지기 때문입니다.

즉, 일반 마감 API를 호출하면 낙찰 주문이 만들어지는 즉시 자동결제가 시작됩니다. 테스트자가 그 사이에 `PAYMENT_PENDING` 주문을 잡아 `/auto-fail`을 호출하는 것은 배포 환경에서 안정적이지 않습니다. 그래서 시나리오 3은 이벤트를 발행하지 않는 준비 API로 `PAYMENT_PENDING` 주문을 먼저 만든 뒤 실패를 주입합니다.

## 시나리오 4. 직접결제 기한 Redis 만료 감지 검증

목적: 자동결제 실패 후 생기는 직접결제 대기 TTL이 실제로 Redis 만료 이벤트를 통해 차순위 승격 또는 경매 취소로 이어지는지 검증합니다.

### 준비 상태

- 주문 상태: `AUTO_PAYMENT_FAILED`
- 시나리오 3을 먼저 수행하면 됩니다.
- 차순위 입찰자를 만들고 싶으면 경매 종료 전 최소 2명의 입찰자가 있어야 합니다.
- 이 시나리오는 Redis 만료 감지 확인용입니다. 빠른 검증이 목적이면 시나리오 5를 사용하세요.
- 차순위 입찰자가 있는 케이스와 없는 케이스를 모두 테스트하면 복구/취소 흐름을 모두 확인할 수 있습니다.

### 호출

```bash
curl -X POST "https://{host}/internal/test/orders/{orderUid}/expire-payment-window-in?seconds=600" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

### 내부에서 실행되는 로직

- `OrderTestScenarioService.schedulePaymentWindowExpiration` 실행
- 주문이 `AUTO_PAYMENT_FAILED`인지 확인
- `paymentDeadline = now + seconds`로 변경
- 기존 Redis 결제 만료 key 삭제
- `SetExpireService.scheduleExpiry(orderId, seconds)`로 Redis key 재등록
- Redis key: `order:expire{orderId}`
- shadow key: `order:shadow{orderId}`

### 기다린 뒤 실행되는 내부 로직

Redis key가 만료되면:

- `ExpiryEventListener.onMessage`
- `OrderCommandService.escalateToNextRankWithDirectPayment(orderUid)`

차순위 입찰자가 있으면:

- 기존 주문은 `AUTO_PAYMENT_FAILED` 상태로 남습니다.
- 다음 순위 입찰자 주문이 생성됩니다.
- 새 주문 상태: `PAYMENT_PENDING -> AUTO_PAYMENT_FAILED`
- 새 주문에 직접결제 기한이 부여됩니다.
- `OrderEscalatedEvent` 발행
- 알림:
  - 차순위 입찰자: `ESCALATED_PAYMENT_OPPORTUNITY`
  - 메시지: `낙찰 기회가 생겼습니다. 1시간 내에 직접 결제를 진행해 주세요.`
  - relatedData: 새 `orderUid`

차순위 입찰자가 없거나 최대 승격 순위를 넘으면:

- 경매 상태: `CANCELLED`
- `OrderEscalatedEvent` 발행
- 알림:
  - 판매자: `PAYMENT_FINAL_FAILED`
  - 메시지: `구매자의 결제가 최종 실패하여 경매가 취소되었습니다.`
  - relatedData: 기존 `orderUid`

주의:

- `OrderEscalatedEvent`는 Kafka가 아니라 Spring application event 기반 알림 처리입니다.
- `NotificationOrderEscalationEventHandler`가 트랜잭션 커밋 후 알림을 생성합니다.

### 확인 위치

- 새 주문이 생겼다면 `GET /api/v1/orders/{nextOrderUid}`로 상태 확인
- 차순위 입찰자 계정으로 알림 `ESCALATED_PAYMENT_OPPORTUNITY` 확인
- 경매 취소 케이스에서는 경매 상세/관리자 목록에서 `CANCELLED` 확인
- 판매자 계정으로 알림 `PAYMENT_FINAL_FAILED` 확인
- 서버 로그에서 `[PAYMENT_ESCALATION]` 로그 확인

## 시나리오 5. 직접결제 기한 만료 즉시 주입

목적: Redis TTL을 기다리지 않고 차순위 승격/경매 취소 로직을 즉시 검증합니다.

### 준비 상태

- 주문 상태: `AUTO_PAYMENT_FAILED`
- 자동결제 실패 주입 API를 먼저 호출해야 합니다.
- 주문이 `PAYMENT_PENDING`, `PAYMENT_COMPLETED`, `CANCELLED` 상태이면 실패합니다.
- 차순위 승격을 보려면 경매 종료 전에 최소 2명 이상 입찰해야 합니다.
- 최종 취소를 보려면 입찰자 1명만 있는 경매를 사용하거나, 이미 2등까지 승격된 주문에서 다시 만료를 주입합니다.

### 호출

```bash
curl -X POST "https://{host}/internal/test/orders/{orderUid}/expire-payment-window" \
  -H "X-Internal-Token: {POCAT_INTERNAL_TOKEN}"
```

### 내부에서 실행되는 로직

- `OrderTestScenarioService.expirePaymentWindowNow` 실행
- 주문 상태가 `AUTO_PAYMENT_FAILED`인지 확인
- `OrderCommandService.escalateToNextRankWithDirectPayment(orderUid)` 직접 호출
- 기존 Redis 결제 만료 key 취소
- 차순위 입찰자 존재 여부에 따라 승격 또는 경매 취소

### 응답의 `escalationStatus`

| 값 | 의미 | 확인할 것 |
|---|---|---|
| `ESCALATED` | 차순위 입찰자에게 새 주문 생성 | `nextOrderUid`, 차순위 알림 |
| `CANCELLED` | 차순위 없음 또는 최대 승격 순위 초과 | 경매 `CANCELLED`, 판매자 알림 |
| `SKIPPED` | 상태 불일치 등으로 처리 없음 | source order 상태와 bidderRank |

### 기대 알림

`ESCALATED`:

- 수신자: 차순위 입찰자
- 타입: `ESCALATED_PAYMENT_OPPORTUNITY`
- 메시지: `낙찰 기회가 생겼습니다. 1시간 내에 직접 결제를 진행해 주세요.`

`CANCELLED`:

- 수신자: 판매자
- 타입: `PAYMENT_FINAL_FAILED`
- 메시지: `구매자의 결제가 최종 실패하여 경매가 취소되었습니다.`

## 전체 권장 E2E

가장 포괄적인 수동 검증 순서입니다.

1. 판매자 A가 카드 등록
2. 관리자가 카드 승인
3. 판매자 A가 경매 생성
4. 관리자가 경매 검수 통과
5. 구매자 B가 입찰
6. 선택: 구매자 C가 입찰해서 B가 `OUTBID`, C가 `LEADING`이 되도록 구성
7. 경매 Redis 만료 감지 검증이면 `/internal/test/auctions/{auctionId}/expire-in?seconds=600`
8. 경매 종료 이벤트/알림 검증이면 `/internal/test/auctions/{auctionId}/expire-now` 후 `/internal/auctions/{auctionId}/close-expired`
9. 자동결제 실패 주입 검증이면 `/internal/test/auctions/{auctionId}/expire-now` 후 `/internal/test/auctions/{auctionId}/close-expired-without-auto-payment`
10. 8번을 사용했다면 경매 `ENDED`, 낙찰/패찰 알림 확인
11. 9번을 사용했다면 응답의 낙찰 주문 `orderUid` 확인, 이때 경매 종료 알림은 오지 않는 것이 정상
12. `/internal/test/payments/{orderUid}/auto-fail`
13. 주문 `AUTO_PAYMENT_FAILED`, 구매자 `AUTO_PAYMENT_FAILED` 알림 확인
14. 직접결제 Redis 만료 감지 검증이면 `/internal/test/orders/{orderUid}/expire-payment-window-in?seconds=600`
15. 빠른 검증이면 `/internal/test/orders/{orderUid}/expire-payment-window`
16. 차순위 주문 생성 또는 경매 취소 확인
17. 차순위 입찰자 `ESCALATED_PAYMENT_OPPORTUNITY` 또는 판매자 `PAYMENT_FINAL_FAILED` 알림 확인

## 자주 막히는 지점

| 증상 | 확인할 것 |
|---|---|
| 401 | `X-Internal-Token` 누락 또는 값 불일치 |
| 403 `TEST_SCENARIO_DISABLED` | `POCAT_TEST_SCENARIOS_ENABLED=true` 설정 누락 |
| 경매 만료가 안 됨 | Redis `notify-keyspace-events Ex` 설정 |
| `/expire-now` 후 아무 변화 없음 | `/internal/auctions/{auctionId}/close-expired` 호출 여부 |
| 자동결제 실패 주입 실패 | 주문 상태가 `PAYMENT_PENDING`인지 확인 |
| `PAYMENT_PENDING` 주문을 만들기 전에 자동결제가 먼저 실행됨 | 일반 `close-expired` 대신 `/internal/test/auctions/{auctionId}/close-expired-without-auto-payment` 사용 |
| 직접결제 만료 주입 실패 | 주문 상태가 `AUTO_PAYMENT_FAILED`인지 확인 |
| 알림이 안 보임 | Kafka consumer 또는 Spring event handler 로그 확인 |
