# RDS Read Replica 라우팅 코드 사전 구현 검증 결과

| 항목 | 내용 |
|------|------|
| 이슈 | #206 |
| 날짜 | 2026-06-11 |
| 브랜치 | feat/db-read-replica/#206 |

## 구현 내역

`AbstractRoutingDataSource` + `LazyConnectionDataSourceProxy` 기반으로 write/read DataSource를 라우팅하는 코드 레벨 인프라를 사전 구축했다. Replica가 없는 현재 환경에서는 read 프로퍼티가 YAML cascading default로 write 값에 fallback되어 기존과 100% 동일하게 동작한다 (상세 설계는 [ADR-016](../adr/ADR-016-rds-read-replica-routing-%23206.md) 참고).

### 신규 파일

| 파일 | 내용 |
|------|------|
| `global/config/DataSourceType.java` | `enum { WRITE, READ }` — 라우팅 키 정의 |
| `global/config/RoutingDataSource.java` | `AbstractRoutingDataSource` 구현. `determineCurrentLookupKey()`가 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`를 확인하여 `true`면 `READ`, 그 외(false 또는 트랜잭션 외부)면 `WRITE` 반환 |
| `global/config/DataSourceConfig.java` | write/read 각각의 `DataSourceProperties`/`HikariDataSource` 빈 구성, `RoutingDataSource`에 `targetDataSources`(WRITE/READ) 매핑 및 `defaultTargetDataSource=write` 설정, 결과를 `LazyConnectionDataSourceProxy`로 감싸 `@Primary` `DataSource` 빈으로 등록 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `src/main/resources/application-local.yaml` | `spring.datasource.read.*` 추가 — `url`/`username`/`password`/`driver-class-name`을 cascading default(`${DB_READ_URL:${DB_URL:...}}` 등)로 구성. `read.hikari.*` 풀 사이즈는 write와 동일 값을 placeholder로 설정 (TODO(#206)) |
| `src/main/resources/application-prod.yaml` | `spring.datasource.read.*` 추가 — 동일하게 `${DB_READ_URL:${DB_URL}}` 등 cascading default. `DB_READ_*` 환경변수 미설정 시 write와 동일한 prod RDS를 가리킴 (TODO(#206): Parameter Store에 `DB_READ_*` 등록 시 활성화) |
| `src/test/resources/application.yaml` | `spring.datasource.read.*`에 동일한 H2 인메모리 URL(`jdbc:h2:mem:testdb;...;MODE=MySQL`) 및 `sa`/빈 비밀번호 추가 — 테스트 환경에서 read/write 빈이 모두 정상 등록되는지 확인 가능하도록 구성 |

## 테스트 결과

### `RoutingDataSourceTest` (3/3 GREEN)

| 케이스 | 검증 내용 |
|--------|----------|
| `determineCurrentLookupKey_readOnlyTransaction_returnsRead` | `TransactionSynchronizationManager`에 `actualTransactionActive=true`, `currentTransactionReadOnly=true`를 설정한 상태에서 `determineCurrentLookupKey()` 호출 시 `DataSourceType.READ` 반환 확인 |
| `determineCurrentLookupKey_writeTransaction_returnsWrite` | `currentTransactionReadOnly=false`로 설정한 상태에서 `determineCurrentLookupKey()` 호출 시 `DataSourceType.WRITE` 반환 확인 |
| `determineCurrentLookupKey_noTransaction_returnsWrite` | 트랜잭션 컨텍스트가 전혀 없는 상태(readOnly 미설정)에서 `determineCurrentLookupKey()` 호출 시 기본값으로 `DataSourceType.WRITE` 반환 확인 |

### `DataSourceConfigTest` (3/3 GREEN)

`@SpringBootTest(webEnvironment = NONE)` + `@ActiveProfiles("test")`로 전체 Spring Context를 로딩하여 검증 (Redis/ES 등 인프라 빈은 `@MockBean`으로 대체).

| 케이스 | 검증 내용 |
|--------|----------|
| `routingDataSource_isRoutingDataSourceType` | `routingDataSource` 빈이 `RoutingDataSource` 타입으로 정상 등록되는지 확인 |
| `dataSource_isLazyConnectionDataSourceProxyType` | `@Primary` `dataSource` 빈이 `LazyConnectionDataSourceProxy` 타입으로 등록되고, `ApplicationContext`에서 `"dataSource"` 빈을 조회해도 동일 타입인지 확인 |
| `writeAndReadDataSources_areHikariDataSourceType` | `writeDataSource`/`readDataSource` 빈이 각각 `HikariDataSource` 타입으로 정상 등록되는지 확인 (read는 H2 fallback URL 사용) |

## 코드 리뷰 결과

### REVIEW

총 8건의 지적 사항 중 핵심 2건(`@ConfigurationProperties("spring.datasource.hikari")`/`.read.hikari`를 `HikariDataSource` 빈에 직접 적용한 것이 잘못되었다는 주장)은 직접 코드 검증 결과 **오판단으로 반려**되었다. `DataSourceProperties`(`writeDataSourceProperties`/`readDataSourceProperties`)에는 `maximumPoolSize`/`minimumIdle` 등 HikariCP 전용 필드가 없으므로, 풀 설정을 적용하려면 `HikariDataSource` 빈에 `@ConfigurationProperties("spring.datasource.hikari")`(및 `.read.hikari`)를 직접 바인딩하는 것이 Spring Boot 공식 dual-datasource 패턴이며, `DataSourceConfigTest` GREEN으로 빈 구성이 의도대로 동작함을 확인했다. 나머지 6건은 non-blocking이거나 본 ADR에서 이미 결정된 사항(TODO(#206) 보류 항목 등)과 중복된다.

### SECURITY

**PASS**

- 자격 증명(`DB_USERNAME`/`DB_PASSWORD`/`DB_READ_USERNAME`/`DB_READ_PASSWORD`) 노출 없음 — 모두 환경변수 참조이며 평문 시크릿 없음
- cross-environment fallback 누출 없음 — cascading default는 동일 환경 내 write 값으로만 fallback
- 라우팅 로직(`determineCurrentLookupKey`)은 트랜잭션 동기화 상태만 참조하며 외부 입력과 무관 — 인젝션/라우팅 우회 불가
- 신규 의존성 없음 — 기존 `spring-boot-starter-data-jpa`/HikariCP 범위 내 구성, 신규 attack surface 없음

## 후속 조치 (TODO(#206))

- [x] **read.hikari 풀 사이즈 확정** — `maximum-pool-size=5`, `minimum-idle=2`로 확정. 근거: Replica 인스턴스 `db.t4g.micro`(1GiB)의 `max_connections ≈ 85`(공식 `{DBInstanceClassMemory/12582880}` = `1,073,741,824/12,582,880 ≈ 85.33` → 85). write(10) + read(5) = 15/task 기준, ECS 태스크 5개까지 `15 × 5 = 75 ≤ 85`로 여유 확보. `application-local.yaml`/`application-prod.yaml`에 반영 완료
- [ ] **`RoutingDataSource` readOnly 라우팅 통합 테스트** — Testcontainers 등을 활용한 실제 멀티 DB(write/read) 환경에서 `@Transactional(readOnly=true)`/`(readOnly=false)` 메서드의 라우팅 결과를 통합 테스트로 검증. 추후 별도 작업으로 진행 예정
- [ ] **`open-in-view=false` 전환 검토** — Replica 도입에 따른 트랜잭션 경계 영향을 재평가하여 별도 후속 이슈로 전환 여부 검토. 추후 별도 작업으로 진행 예정

## 커밋 상태

배포팀으로부터 Replica 인프라(엔드포인트, 인스턴스 클래스, `max_connections`, Parameter Store의 `DB_READ_URL` 등) 정보를 전달받아 코드/문서 반영을 완료했다. **코드/문서 반영 완료, 커밋 및 PR 생성 완료 (PR #210, 2026-06-11)**.
