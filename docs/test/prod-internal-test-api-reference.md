# Prod Internal Test API Reference

prod profile 배포 환경에서 사용하는 테스트용 내부 API 설명입니다. 각 API는 “어떤 상태가 미리 필요하고”, “호출하면 내부에서 어떤 로직이 실행되며”, “이후 어디서 무엇을 확인해야 하는지”를 기준으로 정리했습니다.

## 공통 조건

모든 테스트 API는 내부 토큰 인증을 사용합니다.

```http
X-Internal-Token: {POCAT_INTERNAL_TOKEN}
```

서버 설정도 켜져 있어야 합니다.

```text
POCAT_TEST_SCENARIOS_ENABLED=true
```

테스트 API가 비활성화되어 있으면 `TEST_SCENARIO_DISABLED`가 반환됩니다. 일반 사용자 JWT나 관리자 JWT로는 호출하지 않습니다.

## 패키지 구조와 보안 경계

테스트 시나리오 API 코드는 실제 도메인 API와 섞이지 않도록 `com.rocketcrew.pocat.internal.testscenario` 하위에 모았습니다.

```text
internal.testscenario
├── auction
├── payment
├── order
└── support
```

- controller/service/dto는 테스트 시나리오 패키지에만 둡니다.
- 실제 도메인 엔티티, 리포지토리, 운영 서비스는 기존 `domain.*` 패키지를 그대로 사용합니다.
- `TestScenarioGuard`가 `POCAT_TEST_SCENARIOS_ENABLED` 값을 공통으로 검사합니다.
- URL은 모두 `/internal/test/**`이므로 `SecurityConfig`의 `/internal/**` 인증 규칙과 `InternalTokenAuthFilter` 적용 대상에 포함됩니다.
- 호출 시 `X-Internal-Token`이 없거나 값이 다르면 Security filter 단계에서 401로 차단됩니다.

## 1. `POST /internal/test/auctions/{auctionId}/expire-in?seconds=600`

ACTIVE 경매를 “지금부터 N초 뒤 종료되는 경매”로 바꾸고 Redis 만료 key를 다시 등록합니다. Redis key 만료 감지 자체가 제대로 되는지 확인할 때 사용합니다.

### 호출 전제조건

- 카드가 등록되어 있어야 합니다.
- 관리자가 카드를 승인해야 합니다.
- 판매자가 경매를 생성해야 합니다.
- 관리자가 경매 검수를 통과시켜 경매가 `ACTIVE` 상태여야 합니다.
- Redis keyspace notification 설정이 필요합니다: `notify-keyspace-events Ex`
- 낙찰/패찰 알림까지 보려면 입찰이 1건 이상 있어야 합니다.
- 패찰 알림까지 보려면 입찰자가 2명 이상이면 좋습니다.

### 선행 API 흐름

1. 카드 등록 API
2. 관리자 카드 승인 API
3. 경매 생성 API
4. 관리자 경매 검수 통과 API
5. 선택: 입찰 API
6. 이 API 호출

### 내부 로직

- `AuctionTestScenarioController.scheduleExpiration`
  - 이 API 요청을 받는 컨트롤러입니다.
  - `auctionId`와 `seconds` 값을 읽어서 테스트 서비스로 전달합니다.
- `AuctionTestScenarioService.scheduleExpiration`
  - 테스트 API 활성화 여부를 확인합니다.
  - 경매가 존재하는지 조회합니다.
  - 경매 상태가 `ACTIVE`인지 확인합니다.
  - `endedAt`을 `현재 UTC 시각 + seconds`로 변경합니다.
  - 예: `seconds=600`이면 원래 3일짜리 경매라도 지금부터 10분 뒤 종료되도록 바꿉니다.
- `AuctionExpirationRedisService.setExpirationKeys`
  - Redis `auction:end:{auctionId}` key를 새 TTL로 등록합니다.
  - Redis `auction:end:shadow:{auctionId}` shadow key도 등록합니다.

이 API 자체는 경매를 종료하지 않습니다. 종료 시간이 짧아지도록 DB와 Redis TTL만 다시 맞춥니다.

### 이후 기대 흐름

`auction:end:{auctionId}` key가 만료되면:

- `AuctionExpirationRedisSubscriber.onMessage`가 Redis 만료 이벤트를 받습니다.
- 내부에서 `AuctionLifecycleService.closeExpiredAuction(auctionId)`를 호출합니다.
- 경매 종료 처리가 실행됩니다.

입찰자가 있으면:

- 경매 상태: `ACTIVE -> ENDED`
- 최고 입찰자의 bid 상태: `LEADING -> WON`
- 밀린 입찰자의 bid 상태: `OUTBID -> LOST`
- `AuctionEndedEvent` 발행
- Outbox topic: `auction`
- Kafka topic: `auction`
- eventType: `auction.ended`

입찰자가 없으면:

- 경매 상태: `ACTIVE -> NO_BIDDER`
- `AuctionEndedEvent` 발행
- winnerId가 없으므로 낙찰자/판매자 낙찰 알림은 생성되지 않습니다.

### 기대 알림

입찰자가 있는 경매 종료 시:

- 낙찰자: `AUCTION_WON`, `낙찰되었습니다.`
- 판매자: `AUCTION_SOLD`, `카드가 낙찰되었습니다.`
- 패찰자: `AUCTION_LOST`, `패찰하셨습니다.`

### 확인 위치

- 경매 상세 또는 관리자 경매 목록에서 `ENDED` 또는 `NO_BIDDER`
- 입찰 내역에서 `WON`, `LOST`
- 각 사용자 알림 목록
- 서버 로그의 Redis 만료 이벤트 처리 로그
- Outbox/Kafka 로그의 `auction.ended`

## 2. `POST /internal/test/auctions/{auctionId}/expire-now`

ACTIVE 경매의 종료 시각을 과거로 당겨서, 바로 마감 API를 호출할 수 있는 상태로 만듭니다. Redis 만료 감지를 기다리지 않고 경매 마감 로직만 빠르게 검증할 때 사용합니다.

### 호출 전제조건

- 경매가 생성되어 있어야 합니다.
- 관리자 검수 통과 후 경매 상태가 `ACTIVE`여야 합니다.
- 이 API만 호출하면 경매 상태는 아직 `ACTIVE`입니다.
- 실제 마감은 반드시 `/internal/auctions/{auctionId}/close-expired`를 이어서 호출해야 합니다.

### 선행 API 흐름

1. 카드 승인
2. 경매 생성
3. 경매 검수 통과
4. 선택: 입찰 생성
5. 이 API 호출
6. `/internal/auctions/{auctionId}/close-expired` 호출

### 내부 로직

- `AuctionTestScenarioController.makeExpired`
  - API 요청을 받는 컨트롤러입니다.
  - `auctionId`를 테스트 서비스로 전달합니다.
- `AuctionTestScenarioService.makeExpired`
  - 테스트 API 활성화 여부를 확인합니다.
  - 경매를 조회합니다.
  - 경매 상태가 `ACTIVE`인지 확인합니다.
  - `endedAt`을 `현재 UTC 시각 - 5초`로 변경합니다.
  - 즉, 시스템 기준으로 “이미 종료 시간이 지난 ACTIVE 경매”로 만듭니다.
- `AuctionExpirationRedisService.deleteExpirationKeys`
  - 기존 Redis 경매 종료 key를 삭제합니다.
  - Redis 만료 이벤트가 먼저 끼어들어 테스트 타이밍을 흐리지 않게 합니다.

### 이후 기대 흐름

이 API 이후에는 아래 API를 호출해야 실제 마감이 됩니다.

```http
POST /internal/auctions/{auctionId}/close-expired
```

### 확인 위치

- 응답의 `beforeEndedAt`, `afterEndedAt`
- DB 또는 관리자 경매 목록의 `endedAt`
- 아직 상태는 `ACTIVE`인 것이 정상입니다.

## 3. `POST /internal/auctions/{auctionId}/close-expired`

종료 시각이 지난 ACTIVE 경매를 실제로 마감합니다. 기존 내부 API이며 Redis subscriber나 배치가 원래 호출하는 마감 로직과 같은 경로입니다.

### 호출 전제조건

- 경매 상태가 `ACTIVE`여야 합니다.
- `endedAt <= 현재 UTC 시각`이어야 합니다.
- 보통 아래 중 하나가 먼저 필요합니다.
  - `/internal/test/auctions/{auctionId}/expire-now`
  - 또는 `/internal/test/auctions/{auctionId}/expire-in` 후 실제 Redis 만료 대기

### 내부 로직

- `InternalAuctionController.closeExpired`
  - 기존 내부 마감 API 컨트롤러입니다.
- `AuctionLifecycleService.closeExpiredAuction`
  - Redisson 분산락 `auction:lock:{auctionId}`를 획득합니다.
  - 경매가 `ACTIVE`인지 확인합니다.
  - `endedAt`이 현재 시각보다 지났는지 확인합니다.
  - 조건이 맞지 않으면 `false`를 반환하고 종료합니다.
  - 입찰 내역을 조회합니다.
  - 최고 입찰자가 없으면 경매를 `NO_BIDDER`로 변경합니다.
  - 최고 입찰자가 있으면 최고 입찰자는 `WON`, 밀린 입찰자는 `LOST`로 변경합니다.
  - 경매를 `ENDED`로 변경합니다.
  - `AuctionEndedEvent`를 발행합니다.
  - 트랜잭션 커밋 후 ES 경매 문서 status를 업데이트합니다.

### 기대 이벤트와 알림

- Spring event: `AuctionEndedEvent`
- Outbox topic: `auction`
- Kafka topic: `auction`
- eventType: `auction.ended`
- 알림:
  - 낙찰자: `AUCTION_WON`
  - 판매자: `AUCTION_SOLD`
  - 패찰자: `AUCTION_LOST`

### 확인 위치

- API 응답 data가 `true`인지 확인
- 경매 상태: `ENDED` 또는 `NO_BIDDER`
- bid 상태: `WON`, `LOST`
- 알림 목록
- ES 검색 결과의 경매 status

## 4. `POST /internal/test/auctions/{auctionId}/close-expired-without-auto-payment`

자동결제 실패 주입 시나리오를 준비하기 위한 테스트 전용 마감 API입니다. 경매는 `ENDED`로 바꾸고 낙찰 주문은 `PAYMENT_PENDING`으로 만들지만, `auction.ended`와 `order.created` 이벤트를 발행하지 않습니다. 따라서 `PaymentKafkaConsumer`가 자동결제를 먼저 실행하지 않습니다.

일반 `/internal/auctions/{auctionId}/close-expired`를 쓰면 정상 운영 흐름대로 `auction.ended -> order.created -> PaymentKafkaConsumer.autoPayment`가 이어집니다. 배포 환경에서는 이 속도가 빠르기 때문에 `PAYMENT_PENDING` 주문을 사람이 잡아서 실패 주입하기 어렵습니다. 자동결제 실패 주입 전 준비 단계에서는 이 API를 사용하세요.

### 호출 전제조건

- 테스트 API가 활성화되어 있어야 합니다. `POCAT_TEST_SCENARIOS_ENABLED=true`
- 경매 상태가 `ACTIVE`여야 합니다.
- `endedAt <= 현재 UTC 시각`이어야 합니다.
- 보통 `/internal/test/auctions/{auctionId}/expire-now`를 먼저 호출해서 즉시 마감 가능한 상태로 만듭니다.
- 낙찰 주문을 만들려면 최소 1건 이상의 입찰이 있어야 합니다.
- 이미 일반 `close-expired`나 Redis 만료로 경매가 종료된 뒤에는 사용할 수 없습니다.

### 선행 API 흐름

1. 카드 등록
2. 관리자 카드 승인
3. 경매 생성
4. 관리자 경매 검수 통과
5. 구매자 입찰
6. `/internal/test/auctions/{auctionId}/expire-now`
7. 이 API 호출
8. 응답의 `orderUid`로 `/internal/test/payments/{orderUid}/auto-fail` 호출

### 내부 로직

- `AuctionTestScenarioController.closeExpiredWithoutAutoPayment`
  - API 요청을 받는 테스트 컨트롤러입니다.
  - `auctionId`를 테스트 서비스로 전달합니다.
- `AuctionTestScenarioService.closeExpiredWithoutAutoPayment`
  - 테스트 API 활성화 여부를 확인합니다.
  - Redisson 분산락 `auction:lock:{auctionId}`를 획득합니다.
  - 경매가 `ACTIVE`이고 종료 시각이 지났는지 확인합니다.
  - 입찰 내역을 조회합니다.
  - 최고 입찰자가 없으면 경매를 `NO_BIDDER`로 변경하고 주문은 만들지 않습니다.
  - 최고 입찰자가 있으면 최고 입찰 bid를 `WON`, 패찰 bid를 `LOST`로 변경합니다.
  - 경매를 `ENDED`로 변경합니다.
  - `Order.fromAuction(...)`으로 낙찰 주문을 직접 생성합니다.
  - Redis 경매 만료 key를 삭제합니다.
  - 트랜잭션 커밋 후 ES 경매 status를 갱신합니다.

### 일부러 실행하지 않는 로직

- `AuctionEndedEvent`를 발행하지 않습니다.
- outbox topic `auction`에 이벤트를 쓰지 않습니다.
- `AuctionEventConsumer`를 통해 주문을 만들지 않습니다.
- `OrderCreatedEvent`를 발행하지 않습니다.
- outbox topic `order`에 이벤트를 쓰지 않습니다.
- `PaymentKafkaConsumer.autoPayment`가 실행되지 않습니다.

### 기대 상태

- 경매 상태: `ACTIVE -> ENDED`
- 최고 입찰 bid 상태: `LEADING -> WON`
- 패찰 bid 상태: `OUTBID -> LOST`
- 낙찰 주문 상태: `PAYMENT_PENDING`
- 응답 `auctionEndedEventPublished`: `false`
- 응답 `orderCreatedEventPublished`: `false`
- 응답 `autoPaymentSuppressed`: `true`
- 응답 `nextStep`: `/internal/test/payments/{orderUid}/auto-fail`

### 기대하지 않아야 하는 것

이 API는 자동결제 실패 주입을 위한 상태 준비용입니다. 정상 이벤트/알림 검증용이 아닙니다.

- 낙찰자 `AUCTION_WON` 알림이 오지 않는 것이 정상입니다.
- 판매자 `AUCTION_SOLD` 알림이 오지 않는 것이 정상입니다.
- 패찰자 `AUCTION_LOST` 알림이 오지 않는 것이 정상입니다.
- Kafka `auction.ended`, `order.created` 이벤트가 보이지 않는 것이 정상입니다.

경매 종료 이벤트와 알림 자체를 검증하려면 일반 `/internal/auctions/{auctionId}/close-expired` 또는 Redis 만료 시나리오를 사용하세요.

### 확인 위치

- API 응답의 `orderUid`, `orderStatus`
- 주문 상세 또는 DB에서 주문 상태 `PAYMENT_PENDING`
- 경매 상세 또는 관리자 목록에서 경매 상태 `ENDED`
- 입찰 내역에서 `WON`, `LOST`
- 로그에서 `[TEST_SCENARIO] auction closed and order prepared without auto payment`
- outbox/Kafka 로그에 `order.created`가 새로 생기지 않았는지 확인

## 5. `POST /internal/test/payments/{orderUid}/auto-fail`

자동결제 실패를 강제로 주입합니다. 실제 Toss/PortOne 호출은 하지 않고, PortOne이 실패 응답을 준 뒤 우리 서버가 실행해야 하는 후속 로직만 태웁니다.

### 호출 전제조건

- 주문이 이미 생성되어 있어야 합니다.
- 주문 상태가 `PAYMENT_PENDING`이어야 합니다.
- 경매 낙찰 주문이면 `AUTO_PAYMENT_FAILED` 상태로 전환됩니다.
- 즉시구매 주문이면 자동결제 실패 시 주문이 취소됩니다.
- 낙찰 주문을 만들려면 먼저 경매 마감이 필요합니다.

### 선행 API 흐름

1. 경매 생성
2. 경매 검수 통과
3. 입찰 생성
4. 경매 종료 처리
5. 생성된 낙찰 주문 `orderUid` 확인
6. 이 API 호출

### 내부 로직

- `PaymentTestScenarioController.injectAutoPaymentFailure`
  - API 요청을 받는 컨트롤러입니다.
  - `orderUid`를 테스트 서비스로 전달합니다.
- `PaymentTestScenarioService.injectAutoPaymentFailure`
  - 테스트 API 활성화 여부를 확인합니다.
  - 주문을 조회합니다.
  - 주문 상태가 `PAYMENT_PENDING`인지 확인합니다.
  - 자동결제용 `BILLING_KEY` 결제 레코드를 생성하거나 기존 레코드를 재사용합니다.
- `FailureService.handleAutoPaymentFailure`
  - 결제 상태를 `FAILED`로 변경합니다.
  - 경매 주문이면 주문 상태를 `AUTO_PAYMENT_FAILED`로 변경합니다.
  - 즉시구매 주문이면 주문 상태를 `CANCELLED`로 변경합니다.
  - 경매 주문이면 자동결제 실패 이벤트를 발행합니다.

### 기대 이벤트와 알림

경매 낙찰 주문:

- Spring event: `AutoPaymentFailedEvent`
- Outbox topic: `payment`
- Kafka topic: `payment`
- eventType: `payment.auto.failed`
- 구매자 알림:
  - 타입: `AUTO_PAYMENT_FAILED`
  - 메시지: `자동결제에 실패했습니다. 직접 결제를 진행해 주세요.`

즉시구매 주문:

- 주문이 `CANCELLED` 됩니다.
- 현재 구현상 `payment.auto.failed` 이벤트는 발행하지 않습니다.

### 확인 위치

- 주문 상세: `AUTO_PAYMENT_FAILED`
- 결제 상세 또는 DB: `BILLING_KEY` 결제 `FAILED`
- 구매자 알림 목록: `AUTO_PAYMENT_FAILED`
- Outbox/Kafka 로그: `payment.auto.failed`
- 이후 `POST /api/v1/payments` 직접결제 생성 가능 여부

## 6. `POST /internal/test/orders/{orderUid}/expire-payment-window-in?seconds=600`

직접결제 제한 시간을 짧게 줄이고 Redis 결제 만료 key를 다시 등록합니다. 자동결제 실패 후 “1시간 내 직접결제를 하지 않으면 차순위 승격 또는 경매 취소가 되는지”를 Redis 만료 감지로 검증할 때 사용합니다.

### 호출 전제조건

- 주문 상태가 `AUTO_PAYMENT_FAILED`여야 합니다.
- 보통 `/internal/test/payments/{orderUid}/auto-fail`을 먼저 호출합니다.
- 차순위 승격을 검증하려면 경매 종료 전 입찰자가 2명 이상이어야 합니다.
- 입찰자가 1명뿐이면 최종 결제 실패로 경매 취소 흐름을 검증합니다.
- Redis keyspace notification 설정이 필요합니다: `notify-keyspace-events Ex`

### 선행 API 흐름

1. 경매 종료로 낙찰 주문 생성
2. `/internal/test/payments/{orderUid}/auto-fail`
3. 주문 상태 `AUTO_PAYMENT_FAILED` 확인
4. 이 API 호출

### 내부 로직

- `OrderTestScenarioController.schedulePaymentWindowExpiration`
  - API 요청을 받는 컨트롤러입니다.
  - `orderUid`와 `seconds`를 테스트 서비스로 전달합니다.
- `OrderTestScenarioService.schedulePaymentWindowExpiration`
  - 테스트 API 활성화 여부를 확인합니다.
  - 주문을 조회합니다.
  - 주문 상태가 `AUTO_PAYMENT_FAILED`인지 확인합니다.
  - `paymentDeadline`을 `현재 시각 + seconds`로 변경합니다.
  - 기존 결제 만료 Redis key를 삭제합니다.
- `SetExpireService.scheduleExpiry`
  - Redis `order:expire{orderId}` TTL key를 생성합니다.
  - Redis `order:shadow{orderId}` shadow key를 생성합니다.

### 이후 기대 흐름

Redis `order:expire{orderId}` key가 만료되면:

- `ExpiryEventListener.onMessage`
- `OrderCommandService.escalateToNextRankWithDirectPayment(orderUid)`

차순위 입찰자가 있으면:

- 새 주문 생성
- 새 주문 상태: `AUTO_PAYMENT_FAILED`
- 새 주문에 1시간 직접결제 기한 부여
- `OrderEscalatedEvent` 발행
- 차순위 입찰자 알림:
  - 타입: `ESCALATED_PAYMENT_OPPORTUNITY`
  - 메시지: `낙찰 기회가 생겼습니다. 1시간 내에 직접 결제를 진행해 주세요.`

차순위 입찰자가 없으면:

- 경매 상태: `CANCELLED`
- `OrderEscalatedEvent` 발행
- 판매자 알림:
  - 타입: `PAYMENT_FINAL_FAILED`
  - 메시지: `구매자의 결제가 최종 실패하여 경매가 취소되었습니다.`

### 확인 위치

- 새 주문 상세 또는 주문 목록
- 경매 상태
- 차순위 입찰자 알림 또는 판매자 알림
- 서버 로그 `[PAYMENT_ESCALATION]`

## 7. `POST /internal/test/orders/{orderUid}/expire-payment-window`

직접결제 기한 만료를 즉시 처리합니다. Redis TTL 만료를 기다리지 않고 차순위 승격/경매 취소 로직을 바로 검증합니다.

### 호출 전제조건

- 주문 상태가 `AUTO_PAYMENT_FAILED`여야 합니다.
- 보통 `/internal/test/payments/{orderUid}/auto-fail`을 먼저 호출합니다.
- 차순위 승격을 보려면 경매 종료 전 입찰자가 2명 이상이어야 합니다.
- 차순위가 없거나 최대 승격 순위를 넘으면 경매 취소 흐름으로 갑니다.

### 내부 로직

- `OrderTestScenarioController.expirePaymentWindowNow`
  - API 요청을 받는 컨트롤러입니다.
  - `orderUid`를 테스트 서비스로 전달합니다.
- `OrderTestScenarioService.expirePaymentWindowNow`
  - 테스트 API 활성화 여부를 확인합니다.
  - 주문을 조회합니다.
  - 주문 상태가 `AUTO_PAYMENT_FAILED`인지 확인합니다.
- `OrderCommandService.escalateToNextRankWithDirectPayment`
  - 기존 결제 만료 Redis key를 삭제합니다.
  - 현재 주문의 `bidderRank`를 확인합니다.
  - 다음 순위 입찰자를 찾습니다.
  - 최대 2등까지만 승격합니다.
  - 차순위 입찰자가 있으면 새 주문을 생성합니다.
  - 새 주문에는 다시 1시간 직접결제 기한을 부여합니다.
  - 차순위 입찰자가 없으면 경매를 `CANCELLED`로 변경합니다.
  - `OrderEscalatedEvent`를 발행합니다.

### 기대 이벤트와 알림

- Spring event: `OrderEscalatedEvent`
- eventType: `order.escalated`
- Kafka outbox가 아니라 Spring application event 기반 알림 처리입니다.

`ESCALATED`:

- 차순위 입찰자 알림: `ESCALATED_PAYMENT_OPPORTUNITY`

`CANCELLED`:

- 판매자 알림: `PAYMENT_FINAL_FAILED`

`SKIPPED`:

- 상태 불일치 등으로 처리 없음
- 알림 없음

### 확인 위치

- 응답의 `escalationStatus`
- 응답의 `nextOrderUid`
- 새 주문 상세
- 경매 상태
- 알림 목록
- 서버 로그 `[PAYMENT_ESCALATION]`

## 실패 응답 확인표

| 케이스 | 대표 에러 |
|---|---|
| 테스트 API 비활성화 | `TEST_SCENARIO_DISABLED` |
| 내부 토큰 누락/불일치 | 401 |
| ACTIVE가 아닌 경매에 경매 테스트 API 호출 | `AUCTION_NOT_ACTIVE` |
| `PAYMENT_PENDING`이 아닌 주문에 자동결제 실패 주입 | `ORDER_CANNOT_FAIL_PAYMENT` |
| `AUTO_PAYMENT_FAILED`가 아닌 주문에 직접결제 만료 주입 | `ORDER_CANNOT_FAIL_PAYMENT` |
| TTL 범위가 10초 미만 또는 3600초 초과 | `INVALID_INPUT` |
