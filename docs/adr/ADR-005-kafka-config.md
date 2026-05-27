# ADR-005: Kafka Producer/Consumer 설정 전략

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-27 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

POCAT은 경매, 입찰, 주문, 결제, 환불, 정산, 알림 등 다양한 도메인 간 비동기 이벤트 처리를 위해 Kafka를 도입했다.

도메인 성격이 서로 달라 단일 Producer/Consumer 설정으로는 요건을 충족하기 어려웠다:

- **금전 도메인** (결제, 환불, 정산): 메시지 유실이 곧 금전 손실이므로 강한 내구성 보장 필요
- **일반 도메인** (경매, 입찰, 주문, 알림): 처리 속도와 운영 단순성 우선, 다소의 지연 허용 가능

---

## 결정 (Decision)

### 1. 설정 2-티어 분리

도메인 성격에 따라 `KafkaConfig`에서 두 가지 설정 그룹을 정의한다.

| 구분 | 대상 토픽 | 적용 설정 |
|------|----------|----------|
| 일반 (General) | auction, bid, order, notification | `kafkaTemplate`, `kafkaListenerContainerFactory` |
| 금전 (Financial) | payment, refund, settlement | `{domain}KafkaTemplate`, `{domain}KafkaListenerContainerFactory` |

### 2. Producer 설정

| 항목 | 일반 | 금전 |
|------|------|------|
| `acks` | `1` (리더 확인) | `all` (전체 ISR 확인) |
| `retries` | 1 | 1 |
| `retry.backoff.ms` | 500 | 500 |
| `enable.idempotence` | - | `true` |
| 직렬화 | StringSerializer | StringSerializer |

**일반 도메인 acks=1 이유**: 리더 브로커 저장 확인만으로도 충분하며, all 대비 레이턴시 유리.

**금전 도메인 acks=all + idempotence 이유**: 브로커 장애 시 메시지 유실 방지. 멱등성 설정으로 재시도 중복 발행 제거.

### 3. Consumer 설정

| 항목 | 일반 | 금전 |
|------|------|------|
| `auto.offset.reset` | `earliest` | `earliest` |
| `enable.auto.commit` | `false` | `false` |
| `AckMode` | `RECORD` | `MANUAL` |
| concurrency | 3 | 3 |

**일반 RECORD 모드**: 레코드 처리 완료 시 자동 커밋. 코드가 단순해지고 일반 도메인 수준의 내구성으로 충분.

**금전 MANUAL 모드**: 비즈니스 로직 성공 여부 확인 후 직접 `Acknowledgment.acknowledge()` 호출. 처리 실패 시 오프셋을 커밋하지 않아 재처리 보장.

### 4. DLQ (Dead Letter Queue) 에러 핸들러

모든 컨테이너 팩토리에 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`를 적용한다.

| 항목 | 일반 | 금전 |
|------|------|------|
| 재시도 간격 | 1,000ms | 2,000ms |
| 최대 재시도 횟수 | 3회 | 5회 |
| 최종 실패 처리 | DLQ 토픽으로 발행 (`{topic}.DLT`) | DLQ 토픽으로 발행 (`{topic}.DLT`) |

금전 도메인은 재시도 간격·횟수를 더 늘려 일시적 장애로 인한 DLQ 유입을 최소화한다.

### 5. 토픽 자동 생성 설정

`KafkaConfig`에서 `NewTopic` 빈으로 7개 토픽을 정의한다. 토픽이 이미 존재하면 변경하지 않는다 (Spring Kafka 기본 동작).

| 토픽명 | 파티션 수 | 복제 인수 |
|--------|---------|---------|
| `payment` | 3 | 3 |
| `refund` | 3 | 3 |
| `settlement` | 3 | 3 |
| `order` | 3 | 3 |
| `auction` | 3 | 3 |
| `bid` | 3 | 3 |
| `notification` | 3 | 3 |

파티션 3 / 복제 인수 3은 ADR-001에서 결정한 3-브로커 KRaft 클러스터에 맞춰 설계됐다. 모든 브로커에 복제본이 분산되어 단일 브로커 장애 시에도 토픽 가용성이 유지된다.

### 6. 직렬화 전략

프로듀서/컨슈머 모두 `String` 직렬화를 사용한다. 이벤트 페이로드는 애플리케이션 레이어에서 Jackson으로 JSON 문자열로 변환하여 발행한다.

**이유**: Kafka 레이어에서 스키마 의존성을 없애고, 페이로드 구조 변경 시 Kafka 설정 변경 없이 애플리케이션 코드만 수정 가능.

---

## 결과 (Consequences)

### 긍정적 영향

- **금전 도메인 데이터 안전성**: acks=all + 멱등 프로듀서 + MANUAL 커밋 + 5회 재시도로 메시지 유실·중복 방지
- **일반 도메인 단순성**: RECORD 모드로 별도 ack 코드 불필요, 운영 복잡도 낮음
- **장애 격리**: DLQ 패턴으로 처리 불가 메시지가 컨슈머 파이프라인을 블로킹하지 않음
- **클러스터 정합성**: 파티션/복제 인수를 브로커 수와 일치시켜 ISR 구성 보장

### 부정적 영향 / 주의사항

- **금전 도메인 컨슈머 코드 복잡도**: `Acknowledgment` 파라미터를 직접 관리해야 하므로 누락 시 오프셋 미커밋으로 무한 재처리 발생 가능
- **DLQ 모니터링 필요**: `.DLT` 토픽에 쌓인 메시지를 주기적으로 확인하는 운영 프로세스 별도 필요
- **로컬 개발 시 복제 인수 주의**: 단일 브로커로 로컬 실행 시 `replicas=3` 설정으로 ISR 부족 경고 발생 — `docker compose --profile kafka up`으로 3-브로커 클러스터 기동 권장 (ADR-001 참조)
- **금전 도메인 팩토리 3개 중복**: payment/refund/settlement가 동일한 `financialProducerProps()` / `financialConsumerProps()`를 공유하나 빈 이름이 달라 별도 선언 필요 — 도메인별 `containerFactory` 지정을 위한 불가피한 구조

---

## 관련 문서

- `src/main/java/com/rocketcrew/pocat/global/config/KafkaConfig.java` — 설정 구현체
- `src/main/resources/application.yaml` — bootstrap-servers 등 외부 설정값
- `docs/adr/ADR-001-docker-local-dev-setup.md` — 3-브로커 KRaft 클러스터 구성 결정
