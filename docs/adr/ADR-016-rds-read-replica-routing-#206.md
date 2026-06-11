# ADR-016: RDS Read Replica 라우팅 도입 (코드 사전 준비)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-11 |
| **상태** | Accepted |
| **결정자** | POCAT 팀 |
| **이슈** | #206 |
| **구현 상태** | 코드 구현 완료 + Replica 인프라 프로비저닝 완료 (db.t4g.micro), read pool 확정 (5/2) + 커밋(`ac3709a`) 및 PR #210 생성 완료 (2026-06-11) |

---

## 맥락 (Context)

POCAT은 현재 단일 RDS 인스턴스(MySQL)를 모든 read/write 트래픽에 사용하고 있다. 트래픽 증가에 따라 read 부하 분산을 위한 Read Replica 도입이 검토되었다.

### 인프라 vs 코드 — 단계적 진행

설계 초기에는 Replica 인스턴스, 보안그룹, Parameter Store(`DB_READ_URL` 등), ECS Task Definition 환경변수 주입 등 **인프라 작업을 별도 배포팀이 별도 트랙으로 진행**하는 것을 전제로, 인프라 완료를 기다리지 않고 **코드 레벨 라우팅 인프라를 선제 구축**하는 코드 우선 전략을 채택했다.

이후 배포팀이 Replica 인스턴스(`db.t4g.micro`, `pocat-slave` 엔드포인트) 프로비저닝과 read pool 사이즈(5/2) 확정을 완료했고(상세는 "Replica 인프라 정보" 절 참고), 코드 레벨 변경 또한 cascading default(`${DB_READ_URL:${DB_URL:...}}`)를 통해 환경변수 주입만으로 즉시 read 트래픽이 분산되도록 구성을 마쳤다. Replica가 없는 환경(로컬)에서는 read 프로퍼티가 write 값으로 fallback되어 기존과 동일하게 동작한다.

---

## 결정 (Decision)

### 1. 라우팅 방식 — `AbstractRoutingDataSource` + `LazyConnectionDataSourceProxy`

`AbstractRoutingDataSource`를 상속한 `RoutingDataSource`를 만들고, 이를 `LazyConnectionDataSourceProxy`로 감싸 `@Primary` Bean으로 등록한다.

```text
LazyConnectionDataSourceProxy (@Primary)
  └─ RoutingDataSource (AbstractRoutingDataSource)
       ├─ WRITE → write HikariDataSource
       └─ READ  → read HikariDataSource
```

`determineCurrentLookupKey()`는 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`를 확인하여 `true`면 `READ`, `false`(또는 트랜잭션 외부)면 `WRITE`를 반환한다.

#### `LazyConnectionDataSourceProxy`가 필수인 이유

`AbstractRoutingDataSource`는 **커넥션을 가져오는 시점**에 `determineCurrentLookupKey()`를 호출한다. 그런데 Spring의 `@Transactional` AOP는 트랜잭션 시작 시 먼저 `TransactionSynchronizationManager`에 `readOnly` 플래그를 설정한 뒤, 트랜잭션 매니저가 실제 커넥션을 획득한다.

만약 일반 `DataSource`를 그대로 사용하면, 트랜잭션 매니저(`DataSourceTransactionManager`/`JpaTransactionManager`)가 트랜잭션 시작 시점에 즉시 커넥션을 획득하려 시도할 수 있어, `readOnly` 플래그가 동기화되기 전에 라우팅 키가 결정될 위험이 있다. `LazyConnectionDataSourceProxy`는 실제 커넥션 획득을 **첫 SQL 실행 시점까지 지연**시켜, 트랜잭션 동기화(`readOnly` 플래그 확정)가 완료된 이후 `RoutingDataSource.determineCurrentLookupKey()`가 호출되도록 보장한다.

### 2. 풀 구성 — write/read 별도 HikariCP 풀

`spring.datasource.*`(write)와 `spring.datasource.read.*`(read)를 각각 별도의 `HikariDataSource`로 구성한다. 각 풀은 독립적인 커넥션 풀 사이즈, 타임아웃 설정을 가질 수 있다.

### 3. Replica 부재 시 Fallback — YAML cascading default

`application-local.yaml`/`application-prod.yaml`의 read 프로퍼티는 cascading default 문법(`${DB_READ_URL:${DB_URL:...}}`)으로 작성한다. `DB_READ_URL` 등 read 전용 환경변수가 설정되지 않으면 write와 동일한 값(`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`)을 그대로 사용한다.

이로써 Replica가 아직 없는 환경에서는 **코드 변경 없이 100% 하위호환**되며(read 풀이 write와 동일한 DB를 가리킴), Replica가 추가되는 시점에는 Parameter Store/환경변수에 `DB_READ_URL` 등을 추가 주입하는 것만으로 라우팅이 활성화된다.

### 4. EntityManagerFactory / TransactionManager — 커스텀 빈 불필요

`@Primary`로 등록된 `RoutingDataSource`(via `LazyConnectionDataSourceProxy`)는 Spring Boot의 `JpaBaseConfiguration`이 자동으로 `EntityManagerFactory`/`PlatformTransactionManager` 구성에 사용한다. 별도의 `LocalContainerEntityManagerFactoryBean`, `JpaTransactionManager` 커스텀 빈 정의는 필요하지 않다. `JpaConfig.java`(`@EnableJpaAuditing`)는 변경 없음.

### 5. 락 안전성 — `PaymentQueryService`의 `FOR UPDATE` 라우팅

`PaymentQueryService`는 클래스 레벨에 `@Transactional(readOnly = true)`가 선언되어 있으며, 내부에 `PESSIMISTIC_WRITE`(`FOR UPDATE`) 락을 사용하는 조회 메서드를 포함한다. `readOnly = true`만 보면 이 메서드 호출 시 READ 라우팅이 발생하여 `FOR UPDATE`가 read replica에서 실행될 위험이 있는 것처럼 보인다.

그러나 실제 호출 경로를 분석한 결과, 해당 락 메서드의 모든 호출자(`PaymentCommandService`, `FailureService`, `PaymentApplicationService`)는 **write 트랜잭션**(`@Transactional` 기본 전파(`REQUIRED`) 또는 `REQUIRES_NEW`, `readOnly = false`) 내에서 `PaymentQueryService`를 호출한다.

Spring `@Transactional`의 전파(propagation) 규칙상, 이미 진행 중인 트랜잭션에 **참여(join)하는 경우 내부 메서드의 `readOnly` 속성은 트랜잭션 동기화 상태를 재설정하지 않는다**. 즉, 최외곽 트랜잭션이 `readOnly = false`로 시작되었다면, 내부에서 `readOnly = true`로 선언된 메서드를 호출(join)하더라도 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`는 계속 `false`를 반환한다. 따라서 `PaymentQueryService`의 `FOR UPDATE` 락 메서드는 cross-bean join 시 **WRITE 라우팅이 보장**된다.

(단, `PaymentQueryService`의 메서드가 트랜잭션 없이 단독으로 호출되거나 새로운 `readOnly = true` 트랜잭션의 진입점이 되는 경로가 추가될 경우, READ로 라우팅되어 `FOR UPDATE`가 replica에서 실패(또는 무시)할 수 있다. 신규 호출 경로 추가 시 이 점을 반드시 검토해야 한다.)

---

## 신규/수정 파일

### 신규

- `global/config/DataSourceType.java` — `enum { WRITE, READ }`
- `global/config/RoutingDataSource.java` — `AbstractRoutingDataSource` 구현, `determineCurrentLookupKey()`
- `global/config/DataSourceConfig.java` — write/read `HikariDataSource` Bean, `RoutingDataSource` 조립, `LazyConnectionDataSourceProxy`(`@Primary`) 등록

### 수정

- `application-local.yaml` — `spring.datasource.read.*` 추가 (cascading default로 write 값 fallback)
- `application-prod.yaml` — `spring.datasource.read.*` 추가 (cascading default로 write 값 fallback)
- `src/test/resources/application.yaml` — read 프로퍼티 추가 (H2 fallback URL로 구성 완료)

### 변경 없음

- `JpaConfig.java` — `@EnableJpaAuditing`만 유지, EMF/TransactionManager 커스텀 빈 불필요
- `build.gradle` — 신규 외부 의존성 없음 (`spring-boot-starter-jdbc`/HikariCP는 기존 `spring-boot-starter-data-jpa`에 포함)

---

## 거부된 대안 (Rejected Options)

| 대안 | 기각 이유 |
|------|----------|
| **read 프로퍼티 없을 시 Java 코드에서 명시적 fallback 분기** | YAML cascading default(`${DB_READ_URL:${DB_URL:...}}`)만으로 충분하며, Java 코드에 조건 분기를 추가하는 것은 불필요한 복잡도 증가 → 기각 |
| **JPA `@QueryHints` / Hibernate 세션 레벨 라우팅** | Hibernate 세션 단위 read/write 분기는 JPA/Hibernate 구현에 종속적이며 트랜잭션 경계와 분리되어 관리 포인트가 늘어남. Spring 표준 `AbstractRoutingDataSource`가 더 범용적이고 `@Transactional(readOnly=...)`와 자연스럽게 통합됨 → 채택하지 않음 |
| **`read.hikari` 풀 사이즈 prod에 명시적 설정** | 현재 write 풀도 prod 환경에서 `hikari.*` 사이즈를 명시하지 않고 기본값을 사용 중. 정책 정합성을 위해 read 풀도 동일하게 미설정 상태로 두고, Replica 도입 후 실측 데이터를 바탕으로 조정 → 보류 |

---

## 영향 (Consequences)

### 긍정적 효과

- Replica 인스턴스가 실제로 추가되는 시점에 **애플리케이션 코드 변경 없이 환경변수(`DB_READ_URL` 등)만 추가**하면 즉시 read 트래픽이 분산됨
- 이미 `@Transactional(readOnly = true)`로 선언되어 있는 조회 전용 서비스 메서드는 **자동으로 READ 라우팅 혜택**을 받음 (코드 추가 수정 불필요)
- write/read 풀이 분리되어 있어, read 트래픽 폭주 시 write 풀 고갈로 인한 쓰기 지연을 방지하는 격리 효과 기대

### 부정적 효과 / 주의사항

- **Replica 복제 지연(replication lag)**: write 직후 동일 트랜잭션 외부에서 바로 read 하는 시나리오("write 후 즉시 read")에서 stale read가 발생할 수 있다. 현재는 read 프로퍼티가 write와 동일한 값으로 fallback되어 있어 영향이 없으나, 실제 Replica가 연결되면 영향을 받는 메서드(예: 생성 직후 상세 조회 API)를 식별하여 `@Transactional`(write 라우팅 강제) 또는 read-after-write 캐싱 등의 대응이 필요하다 (후속 이슈로 분리)
- **`LazyConnectionDataSourceProxy`로 인한 커넥션 획득 지연**: 트랜잭션 외부에서 `DataSource`를 직접 주입받아 `getConnection()`을 호출하는 코드가 있다면, 커넥션 획득 시점이 첫 사용 시점까지 지연되는 동작 변화가 있을 수 있다. 현재 코드베이스 분석 결과 해당 사례는 없는 것으로 확인됨
- **`PaymentQueryService`의 `FOR UPDATE` 락 메서드**: 위 "결정 5"에서 분석한 대로 현재 모든 호출 경로는 WRITE 트랜잭션에 join되어 안전하지만, 신규 호출 경로 추가 시 READ 라우팅 여부를 반드시 재검토해야 함

---

## 구현 체크리스트

### Phase 3b (이번 PR 범위)

- [x] `DataSourceType.java` 작성 (`enum { WRITE, READ }`)
- [x] `RoutingDataSource.java` 작성 (`AbstractRoutingDataSource` 상속, `determineCurrentLookupKey()` — `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 분기)
- [x] `DataSourceConfig.java` 작성 (write/read `HikariDataSource` Bean, `RoutingDataSource` 조립 + targetDataSources 매핑, `LazyConnectionDataSourceProxy`를 `@Primary`로 등록)
- [x] `application-local.yaml` — `spring.datasource.read.url/username/password`를 cascading default(`${DB_READ_URL:${DB_URL:...}}` 등)로 추가
- [x] `application-prod.yaml` — 동일하게 `spring.datasource.read.*` cascading default 추가
- [x] `src/test/resources/application.yaml` — read 데이터소스 설정 추가 (H2 환경에서 라우팅 동작 확인 가능하도록)
- [x] `./gradlew test` GREEN 확인 (기존 테스트가 read/write 분리로 인해 깨지지 않는지 검증)

### 보류 항목 (TODO(#206))

- [x] **`local`/`prod`의 `read.hikari` 풀 사이즈 확정** — `maximum-pool-size=5`, `minimum-idle=2`. 근거: Replica 인스턴스 `db.t4g.micro`(1GiB)의 `max_connections ≈ 85`(공식 `{DBInstanceClassMemory/12582880}` = `1,073,741,824/12,582,880 ≈ 85.33` → 85). write(10) + read(5) = 15/task 기준, ECS 태스크 5개까지 `15 × 5 = 75 ≤ 85`로 여유 확보
- [ ] **(보류, TODO(#206))** `RoutingDataSource` readOnly 라우팅 통합 테스트 — Testcontainers 등 실제 멀티 DB 환경 필요
- [ ] **(보류, TODO(#206))** `open-in-view=false` 전환 검토 — 별도 후속 이슈

### 인프라팀 작업 (별도 트랙)

- [x] Replica RDS 인스턴스 생성 — `db.t4g.micro`, `pocat-slave` 엔드포인트 (상세는 "Replica 인프라 정보" 절 참고)
- [ ] 보안그룹 설정 (Replica 접근 허용)
- [x] Parameter Store에 `DB_READ_URL` 등록 완료
- [ ] ECS Task Definition에 read 관련 환경변수 주입

---

## Phase 4 검증 결과

### REVIEW

총 8건의 지적 사항 중 핵심 2건은 직접 코드 검증 결과 **오판단으로 반려**되었다. 나머지는 non-blocking이거나 본 ADR에서 이미 결정된 사항이다.

- **반려 (오판단)**: `@ConfigurationProperties("spring.datasource.hikari")`/`@ConfigurationProperties("spring.datasource.read.hikari")`를 `HikariDataSource` 빈에 직접 적용한 것이 잘못되었다는 주장 — `DataSourceProperties`(`writeDataSourceProperties`/`readDataSourceProperties`)에는 `maximumPoolSize`, `minimumIdle` 등 HikariCP 전용 필드가 존재하지 않으므로, 풀 사이즈/타임아웃 설정을 적용하려면 `HikariDataSource` 빈에 `@ConfigurationProperties("spring.datasource.hikari")`(및 `.read.hikari`)를 직접 바인딩하는 것이 Spring Boot 공식 dual-datasource 패턴([Spring Boot 레퍼런스 "Configure Two DataSources" 예제]와 동일)이다. `DataSourceConfigTest`의 GREEN 결과로 빈 등록 및 타입이 의도대로 구성됨을 확인했다.
- **non-blocking / 기결정 사항**: 나머지 6건은 본 ADR에서 이미 다룬 사항(read.hikari 풀 사이즈 보류, readOnly 통합 테스트 보류, open-in-view 전환 보류 등 TODO(#206) 항목)과 중복되거나, 코드 스타일/주석 보강 수준으로 머지를 막을 사유가 아님.

### SECURITY

**PASS**

- 자격 증명(`DB_USERNAME`/`DB_PASSWORD`/`DB_READ_USERNAME`/`DB_READ_PASSWORD`) 노출 없음 — 모두 환경변수 참조이며 코드/yaml에 평문 시크릿 없음
- cross-environment fallback 누출 없음 — `application-local.yaml`/`application-prod.yaml`의 cascading default는 동일 환경 내 write 값으로만 fallback되며, local↔prod 간 자격 증명 혼선 가능성 없음
- 라우팅 로직(`RoutingDataSource.determineCurrentLookupKey()`)은 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`만 참조하며 외부 입력(요청 파라미터, 헤더 등)과 무관 — 인젝션이나 라우팅 우회를 통한 write replica 접근 등의 공격 경로 없음
- 신규 attack surface 없음 — 신규 의존성 추가 없이 기존 `spring-boot-starter-data-jpa`/HikariCP 범위 내에서 구성

### 테스트

- `RoutingDataSourceTest`: 3/3 GREEN
- `DataSourceConfigTest`: 3/3 GREEN
- `global.config` 패키지 전체 회귀 없음

---

## Replica 인프라 정보

배포팀으로부터 전달받은 Replica 프로비저닝 결과는 다음과 같다.

| 항목 | 값 | 비고 |
|------|-----|------|
| Replica endpoint | `pocat-slave.czykgqcswy2z.ap-northeast-2.rds.amazonaws.com` | Parameter Store `DB_READ_URL=jdbc:mysql://pocat-slave.czykgqcswy2z.ap-northeast-2.rds.amazonaws.com:3306/pocat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` 로 설정 완료 |
| 인스턴스 클래스 | `db.t4g.micro` (1GiB) | |
| `max_connections` 계산식 | `{DBInstanceClassMemory/12582880}` | RDS MySQL 파라미터 그룹 기본 공식 |
| `max_connections` 값 | `1,073,741,824 / 12,582,880 ≈ 85.33` → **85** | |
| `read.hikari.maximum-pool-size` | **5** | write(10) + read(5) = 15/task |
| `read.hikari.minimum-idle` | **2** | |
| 근거 | `15/task × 5 ECS tasks = 75 ≤ 85` | 5개 ECS 태스크까지 여유 확보 |

---

## 모니터링 (후속, Replica 도입 후)

| 항목 | 감시 대상 | 방법 |
|------|-----------|------|
| write/read 풀 상태 | 풀별 active/idle connections, 대기 스레드 수 | Micrometer HikariCP 메트릭 (`hikaricp_connections_*` with pool name tag) |
| Replica 복제 지연 | replication lag (ms) | CloudWatch `ReplicaLag` 메트릭 |
| READ 라우팅 비율 | `RoutingDataSource.determineCurrentLookupKey()`가 READ를 반환한 비율 | 커스텀 메트릭 또는 로그 샘플링 |
| Stale read 영향 | write 직후 read API의 데이터 정합성 | 후속 이슈에서 식별된 메서드 대상 통합 테스트 |

---

## 보안 고려사항

| 항목 | 현재 (Replica 없음) | Replica 도입 후 |
|------|---------------------|------------------|
| 자격 증명 | write/read 모두 동일 `DB_USERNAME`/`DB_PASSWORD` (cascading default) | Replica 전용 read-only 계정 분리 검토 (least privilege) |
| 네트워크 접근 | 단일 RDS 보안그룹 | Replica 보안그룹은 ECS 서비스에서만 접근 허용 (인프라팀 작업) |
| Parameter Store | `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`만 존재 | `DB_READ_URL` 등 read 전용 키 추가 시 동일한 암호화/접근 정책 적용 |
| 라우팅 로직 노출 | `determineCurrentLookupKey()`는 내부 트랜잭션 상태만 참조, 외부 입력에 의한 라우팅 조작 불가 | 변경 없음 |

---

## 관련 문서

- [ADR-014: 메인 앱 @Scheduled 스케줄러 8개 pocat-batch 완전 이전](ADR-014-scheduler-batch-migration-#171.md) — 배치 서버에서도 동일한 `RoutingDataSource` 구성 적용 여부는 후속 검토
- [ADR-015: Redis 단일 인스턴스에서 Cluster 모드로 전환](ADR-015-redis-cluster-migration.md) — 환경변수 기반 cascading default 패턴 선례
- [ADR-006: Payment 결제 실패 재시도 정책](ADR-006-payment-failure-retry-policy.md) — `PaymentQueryService`/`PaymentCommandService` 트랜잭션 경계 관련
