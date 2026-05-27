# ADR-004: 경매 라이프사이클 처리 전략 (Scheduler + Redis TTL + Kafka Event)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-26 |
| **상태** | Implemented |
| **결정자** | 개발팀 전체 |
| **최종 업데이트** | 2026-05-26 |

---

## 맥락 (Context)

경매는 검수 승인 이후 특정 시각에 시작되고, 정해진 종료 시각에 맞춰 낙찰 또는 유찰 상태로 전환되어야 한다.

기존 구현에는 `PENDING`, `APPROVED`, `ACTIVE`, `ENDED`, `NO_BIDDER` 등의 상태는 존재하지만, 다음 흐름을 자동으로 처리하는 구조가 부족했다.

- 승인된 경매를 정해진 시간에 일괄 `ACTIVE` 처리
- 활성화된 경매의 종료 시각에 맞춘 자동 종료
- 입찰자가 없는 경매의 `NO_BIDDER` 처리
- 종료 결과를 Kafka 이벤트로 발행
- Redis 장애 또는 Redis key 만료 이벤트 유실 시 보정 처리

경매 종료는 사용자 알림, 주문 생성, 결제 요청 등 후속 처리를 유발할 수 있으므로 DB 상태 변경과 Kafka 이벤트 발행의 순서가 중요하다. DB 상태 변경이 실패했는데 Kafka 이벤트가 먼저 발행되면 consumer가 존재하지 않는 상태를 기준으로 후속 처리를 할 수 있다.

---

## 결정 (Decision)

### 1. 경매 활성화는 19시 이후 순차 활성화로 처리한다.

**결정**: 매일 19:00 KST에 `APPROVED` 상태 경매를 조회하여 `ACTIVE`로 전환하고, 실제 처리 시각을 `startedAt`으로 확정한다.

- 스케줄러: `AuctionActivationScheduler`
- 실행 시각: `0 0 19 * * *`, `Asia/Seoul`
- 상태 전이: `APPROVED -> ACTIVE`
- 시작 시각: `startedAt = now`
- 종료 시각: `endedAt = startedAt + 3일`
- 활성화 성공 시 `AuctionActivatedEvent` 발행

경매 생성 또는 검수 통과 시점에 즉시 시작하지 않고, 운영 정책상 매일 오후 7시 이후 스케줄러가 순차적으로 시작한다. 따라서 경매별 실제 시작/종료 시각은 처리 순서에 따라 약간 달라질 수 있다. 공개 목록과 인기 랭킹은 방어적으로 `startedAt <= now < endedAt` 조건을 함께 사용해 오픈 전/종료 후 경매가 섞이지 않도록 한다.

### 2. 경매 종료 처리는 단일 서비스로 모은다.

**결정**: Redis 만료 이벤트와 백업 스케줄러 모두 `AuctionLifecycleService.closeExpiredAuction()`을 호출한다.

이유:

- Redis listener와 scheduler가 같은 경매를 동시에 처리할 수 있으므로 종료 로직은 한 곳에서 멱등적으로 관리해야 한다.
- 상태 전이, 입찰 결과 정리, Kafka 이벤트 발행 조건을 한 서비스에 모아 중복 구현을 방지한다.
- Redisson 분산 락으로 중복 종료 처리를 방지한다.

종료 처리 규칙:

- `ACTIVE` 상태이고 `endedAt <= now`인 경매만 종료 가능
- `highestBidderId`가 있으면 `ENDED`
- `highestBidderId`가 없으면 `NO_BIDDER`
- 최종 최고 입찰은 `WON`
- 이미 최고가 갱신으로 밀려난 입찰은 `LOST`
- 종료 성공 시 `AuctionEndedEvent` 발행

### 3. Redis TTL key를 빠른 종료 트리거로 사용한다.

**결정**: 경매가 `ACTIVE`로 커밋된 뒤 Redis에 종료 TTL key와 shadow key를 저장한다.

키 정책:

| 키 | TTL | 역할 |
|---|---:|---|
| `auction:end:{auctionId}` | `endedAt - now` | Redis 만료 이벤트를 발생시키는 종료 트리거 |
| `auction:end:shadow:{auctionId}` | 없음 | 종료 처리 성공 전까지 남겨두는 미처리 추적 키 |

`auction:end:{auctionId}`가 만료되면 Redis Keyspace Notification이 expired 이벤트를 발행한다. 애플리케이션은 해당 이벤트를 받아 경매 종료 처리를 수행한다.

shadow key는 TTL을 주지 않는다. 종료 처리가 성공한 경우에만 `AuctionEndedEvent`의 `AFTER_COMMIT` 핸들러에서 공통 삭제한다. 따라서 Redis 만료 이벤트를 받지 못하거나 종료 처리 중 오류가 발생하면 shadow key가 남아 미처리 경매 추적에 사용할 수 있다.

### 4. Redis Keyspace Notification을 구독한다.

**결정**: Redis expired keyevent 채널을 구독하고, `auction:end:` key만 경매 종료 트리거로 처리한다.

- Subscriber: `AuctionExpirationRedisSubscriber`
- 구독 패턴: `__keyevent@*__:expired`
- 처리 대상 key prefix: `auction:end:`
- 종료 트리거만 수행하고, shadow key 삭제는 종료 커밋 후 공통 cleanup handler가 수행

Redis 설정:

```text
notify-keyspace-events Ex
```

- `E`: keyevent 채널로 이벤트 발행
- `x`: expired 이벤트 발행

로컬 Docker Compose Redis에는 다음 실행 옵션을 추가했다.

```yaml
command: ["redis-server", "--notify-keyspace-events", "Ex"]
```

AWS ElastiCache Redis를 사용할 경우 Parameter Group에서 `notify-keyspace-events = Ex`를 설정해야 한다.

### 5. Redis 이벤트 유실 대비 백업 스케줄러를 둔다.

**결정**: 매일 19:05~19:30 KST에 1분 단위로 DB 기준 만료 `ACTIVE` 경매를 조회해 보정 종료한다.

- 스케줄러: `AuctionExpirationBackupScheduler`
- 실행 시각: `0 5-30 19 * * *`, `Asia/Seoul`
- 조회 조건: `status = ACTIVE and endedAt <= now`
- 정렬: `endedAt ASC`
- 종료 처리: `AuctionLifecycleService.closeExpiredAuction(auctionId)`

Redis Keyspace Notification은 다음 상황에서 유실될 수 있다.

- Redis 장애
- 애플리케이션 장애 또는 재시작
- Redis 설정 누락
- 네트워크 장애

따라서 Redis는 빠른 종료 트리거로 사용하고, 최종 보정은 DB 스케줄러가 담당한다.

### 6. Kafka 발행은 트랜잭션 AFTER_COMMIT 이후 수행한다.

**결정**: 서비스는 Spring 도메인 이벤트를 발행하고, Kafka producer 호출은 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`에서 수행한다.

`AFTER_COMMIT`은 DB 트랜잭션이 성공적으로 커밋된 뒤 실행된다는 뜻이다.

적용 흐름:

```text
AuctionLifecycleService
-> DB 상태 변경
-> eventPublisher.publishEvent(...)
-> transaction commit
-> AuctionEventHandler
-> Kafka 발행
```

이 구조의 장점:

- DB 상태 변경이 롤백되면 Kafka 이벤트가 발행되지 않는다.
- Kafka consumer가 DB에 반영되지 않은 상태를 기준으로 후속 처리를 하는 위험을 줄인다.
- Redis TTL key도 경매 활성화 트랜잭션 커밋 이후에 생성된다.

현재 발행 이벤트:

| 이벤트 | eventType | 용도 |
|---|---|---|
| `AuctionActivatedEvent` | `auction.activated` | 경매 활성화 알림 및 Redis TTL 등록 트리거 |
| `AuctionEndedEvent` | `auction.ended` | 낙찰 또는 유찰 후속 처리 트리거 |

입찰자가 없는 유찰 경매도 별도 이벤트를 만들지 않고 `AuctionEndedEvent`를 그대로 사용한다.

- `winnerId = null`
- `loserIds = []`
- `finalPrice = null`
- 경매 상태는 `NO_BIDDER`

### 7. 엔티티 상태 전이 검증을 추가한다.

**결정**: 서비스에서 상태를 확인하더라도 엔티티 메서드에서도 상태 전이 규칙을 검증한다.

경매 상태 전이:

| 메서드 | 허용 전 상태 | 결과 상태 |
|---|---|---|
| `Auction.activate()` | `APPROVED` | `ACTIVE` |
| `Auction.end()` | `ACTIVE` | `ENDED` |
| `Auction.markNoBidder()` | `ACTIVE` | `NO_BIDDER` |

입찰 상태 전이:

| 메서드 | 허용 전 상태 | 결과 상태 |
|---|---|---|
| `AuctionBid.markOutbid()` | `LEADING` | `OUTBID` |
| `AuctionBid.markWon()` | `LEADING` | `WON` |
| `AuctionBid.markLost()` | `OUTBID` | `LOST` |
| `AuctionBid.cancel()` | `LEADING`, `OUTBID` | `CANCELLED` |

상태 전이 실패 시 다음 에러코드를 사용한다.

- `AUCTION_INVALID_STATUS_TRANSITION`
- `BID_INVALID_STATUS_TRANSITION`

---

## 결과 (Consequences)

### 긍정적 영향

- **정확한 상태 전이**: 경매 활성화/종료/유찰 처리가 명확한 도메인 규칙으로 관리된다.
- **빠른 종료 처리**: Redis TTL 만료 이벤트를 통해 경매 종료 시점에 가까운 처리가 가능하다.
- **장애 보정**: Redis 이벤트가 유실되어도 19:05~19:30 백업 스케줄러가 DB 기준으로 보정한다.
- **Kafka 정합성 개선**: DB 커밋 이후 Kafka를 발행하므로 rollback과 이벤트 발행 불일치를 줄인다.
- **미처리 추적 가능**: shadow key가 TTL 없이 남아 종료 처리 실패 경매를 추적할 수 있다.
- **중복 처리 방지**: Redisson lock으로 Redis listener와 scheduler의 동시 종료 처리를 막는다.

### 주의사항

- **Redis Keyspace Notification 설정 필수**: Redis에 `notify-keyspace-events Ex`가 설정되어야 만료 이벤트를 받을 수 있다.
- **Redis 이벤트는 보장형 메시지가 아님**: Redis 만료 이벤트는 Kafka처럼 durable하지 않으므로 유실될 수 있다. 백업 스케줄러가 반드시 필요하다.
- **백업 스케줄러 지연**: Redis 이벤트가 동작하지 않으면 종료 처리는 19:05~19:30 백업 스케줄러까지 지연될 수 있다.
- **Kafka consumer는 별도 구현 필요**: 현재 구현은 `auction` topic 발행까지 담당하며, 후속 소비 로직은 별도 consumer에서 처리해야 한다.
- **단일 Kafka broker 운영 리스크**: AWS에서 Kafka 서버를 1대만 운영하면 broker 장애 시 이벤트 발행/소비가 중단될 수 있다.
- **배치 서버 도입 시 리팩토링 필요**: 현재 활성화/백업 종료 스케줄러는 백엔드 애플리케이션 내부에서 실행된다. 추후 스케줄러 전용 배치 서버를 분리하면 `AuctionActivationScheduler`, `AuctionExpirationBackupScheduler`와 관련 라이프사이클 호출부를 배치 서버 책임으로 옮기고, API 서버는 요청 처리와 이벤트 소비/발행 책임에 집중하도록 리팩토링한다.

---

## 운영 설정

### 로컬 Docker Compose

Redis:

```yaml
redis:
  image: redis:7.2-alpine
  command: ["redis-server", "--notify-keyspace-events", "Ex"]
```

Kafka:

```yaml
SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka-1:29092,kafka-2:29092,kafka-3:29092
```

### AWS 배포 시

Redis를 EC2에 직접 띄우는 경우:

```bash
redis-server --notify-keyspace-events Ex
```

또는 `redis.conf`:

```conf
notify-keyspace-events Ex
```

ElastiCache Redis를 사용하는 경우:

```text
Parameter Group: notify-keyspace-events = Ex
```

Spring 환경변수:

```text
REDIS_HOST={redis-host}
REDIS_PORT=6379
SPRING_KAFKA_BOOTSTRAP_SERVERS={kafka-host}:{port}
```

---

## 관련 코드

- `AuctionLifecycleService.java` - 경매 활성화/종료 상태 전이 중심 서비스
- `AuctionActivationScheduler.java` - 매일 오후 7시 승인 경매 활성화
- `AuctionExpirationBackupScheduler.java` - 매일 19:05~19:30 만료 경매 보정 종료
- `AuctionExpirationRedisService.java` - Redis TTL key 및 shadow key 관리
- `AuctionExpirationRedisSubscriber.java` - Redis expired keyevent 구독 및 종료 트리거
- `AuctionExpirationRegistrationEventHandler.java` - 활성화 커밋 후 Redis 종료 key 등록
- `AuctionExpirationCleanupEventHandler.java` - 종료 커밋 후 shadow key 공통 삭제
- `AuctionEventHandler.java` - 경매 도메인 이벤트를 AFTER_COMMIT 이후 Kafka로 발행
- `AuctionEventProducer.java` - `auction` topic Kafka producer
- `RedisConfig.java` - Redis listener container와 expired event 구독 등록
- `docker-compose.yml` - 로컬 Redis keyspace notification 설정
