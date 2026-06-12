# ADR-017: [긴급] open-in-view=true로 인한 Read/Write 라우팅 고착(sticky) 버그 수정

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-12 |
| **상태** | Accepted |
| **결정자** | POCAT 팀 |
| **이슈** | #219 |
| **구현 상태** | 구현 완료 (Phase 3a/3b 완료, REVIEW APPROVED, SECURITY PASS) |

---

## 맥락 (Context)

[ADR-016](ADR-016-rds-read-replica-routing-%23206.md)에서 `AbstractRoutingDataSource` + `LazyConnectionDataSourceProxy` 기반 RDS Read Replica 라우팅을 도입했다. `RoutingDataSource.determineCurrentLookupKey()`는 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`를 확인하여 `true`면 `READ`, `false`(또는 트랜잭션 외부)면 `WRITE`로 라우팅한다.

ADR-016 머지 시점에는 `spring.jpa.open-in-view`가 명시적으로 설정되지 않아 Spring Boot 기본값인 `true`가 적용되었다(ADR-016의 보류 항목에도 "open-in-view=false 전환 검토 — 별도 후속 이슈"로 명시됨). `open-in-view=true`에서는 Hibernate `Session`(및 그에 연결된 JDBC 커넥션)이 HTTP 요청 전체에 바인딩되는 `OpenEntityManagerInViewInterceptor`가 활성화된다.

### 라우팅 고착(sticky) 메커니즘

`open-in-view=true` 환경에서 한 HTTP 요청 내 라우팅 흐름은 다음과 같다.

1. 요청 내 **첫 DB 접근**(흔히 `readOnly = true` 트랜잭션)에서 `LazyConnectionDataSourceProxy`가 첫 SQL 실행 시점에 물리 커넥션을 획득하며, 이때 `RoutingDataSource.determineCurrentLookupKey()`가 `READ`를 반환하여 read 풀에서 커넥션을 1회 resolve한다.
2. `open-in-view=true`이므로 이 물리 커넥션은 Hibernate `Session`과 함께 **요청 스코프**에 바인딩되어 유지된다.
3. 이후 같은 요청 내에서 시작되는 모든 트랜잭션(`REQUIRES_NEW` write 트랜잭션 포함)이 **이미 resolve된 동일 커넥션을 재사용**하게 되어, 라우팅 결정이 "요청당 1회"로 고착(sticky)된다.

결과적으로, 요청 내 첫 트랜잭션이 read였다면 이후 모든 write 트랜잭션도 read replica의 커넥션으로 실행된다.

### 실제 프로덕션 장애

즉시구매(buyout) API에서 다음 순서로 호출이 발생한다.

1. `UserQueryService.getUserEntity(buyerId)` (`src/main/java/com/rocketcrew/pocat/domain/user/service/UserQueryService.java`) — `readOnly = true` 트랜잭션, 요청 내 첫 DB 접근 → read 커넥션이 resolve되어 요청에 고착됨
2. `AuctionBuyoutTransactionService.reserveBuyout(...)` / `completeBuyout(...)` (`src/main/java/com/rocketcrew/pocat/domain/auction/service/AuctionBuyoutTransactionService.java`) — `@Transactional(propagation = Propagation.REQUIRES_NEW)` write 트랜잭션이지만, 고착된 read 커넥션을 재사용

그 결과 write 쿼리가 read replica로 전달되어 `The MySQL server is running with the --read-only option` 에러로 즉시구매가 실패하는 장애가 발생했다.

---

## 결정 (Decision)

### `spring.jpa.open-in-view=false`로 전환

`open-in-view=false`로 설정하면 `OpenEntityManagerInViewInterceptor`가 비활성화되어, **트랜잭션 경계마다 EntityManager/Session/JDBC 커넥션이 새로 생성·반환**된다. 트랜잭션이 시작될 때마다 `TransactionSynchronizationManager`의 `readOnly` 플래그가 새로 평가되고, `LazyConnectionDataSourceProxy`도 그 트랜잭션의 첫 SQL 실행 시점에 맞춰 커넥션을 다시 resolve하므로, `RoutingDataSource.determineCurrentLookupKey()`가 **트랜잭션 단위로 정상 재평가**된다.

이로써 `UserQueryService.getUserEntity`(readOnly)는 READ로, `AuctionBuyoutTransactionService.reserveBuyout/completeBuyout`(REQUIRES_NEW, write)는 WRITE로 각각 독립적으로 올바르게 라우팅된다.

---

## 트레이드오프 — LazyInitializationException(LIE) 위험

`open-in-view=false`로 전환하면 트랜잭션이 종료된 이후(예: 컨트롤러 응답 직렬화, `afterCommit` 콜백 등) Hibernate Session도 함께 닫힌다. 이 시점에 **LAZY 연관관계**에 접근하면 `LazyInitializationException`이 발생한다.

### 전수 점검 결과

코드베이스 전체에서 LAZY 연관관계는 다음 2건뿐이다.

- `Card.pokemon`
- `PokemonSet.series`

### 위험 지점 식별 및 방어 조치

트랜잭션 외부(커밋 이후)에서 위 LAZY 연관관계에 접근하는 지점은 2곳이며, 모두 `card.getPokemon()` 접근이다.

| ID | 위치 | 접근 시점 | 방어 조치 |
|----|------|-----------|-----------|
| B1 | `AuctionEsIndexService.index()` (`src/main/java/com/rocketcrew/pocat/domain/auction/service/AuctionEsIndexService.java`) | `afterCommit` 콜백 내 `card.getPokemon()` | `CardRepository.findByIdWithPokemon`(fetch join 신규 메서드)로 재조회 후 사용 |
| B2 | `CardCommandService.doIndexCard()` (`src/main/java/com/rocketcrew/pocat/domain/card/service/CardCommandService.java`) | `afterCommit` 콜백 내 `card.getPokemon()` | 동일하게 `CardRepository.findByIdWithPokemon`으로 재조회 후 사용 |

> B2는 BLUEPRINT CRITIC Round 1에서 추가 발견된 P0 이슈로, Round 2 BLUEPRINT에 반영되어 APPROVED되었다 (아래 "검토 과정" 참고).

### 안전 확인된 나머지 점검 지점

다음 지점들은 모두 **트랜잭션 내부**에서 LAZY 연관관계에 접근하므로 `open-in-view=false` 전환의 영향을 받지 않는다.

- `PokemonSetResponse` (`PokemonSet.series` 접근)
- `AuctionQueryService`
- `AuctionRankingService`
- `AuctionEsMigrationService`
- `CardEsMigrationService`

---

## 신규/수정 파일

### 수정 (5건)

- `src/main/resources/application.yaml` — `spring.jpa.open-in-view: false` 추가
- `src/test/resources/application.yaml` — `spring.jpa.open-in-view: false` 추가
- `CardRepository.java` (`src/main/java/com/rocketcrew/pocat/domain/card/repository/CardRepository.java`) — `findByIdWithPokemon` 신규 메서드 (fetch join)
- `AuctionEsIndexService.java` — B1 방어: `afterCommit` 콜백에서 `findByIdWithPokemon`으로 재조회
- `CardCommandService.java` — B2 방어: `doIndexCard()`의 `afterCommit` 콜백에서 `findByIdWithPokemon`으로 재조회

### 신규 테스트 (3건)

- `AuctionEsIndexLazyLoadingIntegrationTest` — B1 시나리오, `open-in-view=false` 환경에서 `AuctionEsIndexService.index()`의 `afterCommit` 콜백이 LIE 없이 동작하는지 검증
- `AuctionLifecycleEsIndexIntegrationTest` — 경매 생명주기 전체 흐름에서 ES 색인이 LIE 없이 동작하는지 검증. `TransactionTemplate` 사용, `@Transactional` 테스트 어노테이션 사용 금지(테스트 트랜잭션이 프로덕션 트랜잭션 경계를 가려 회귀를 놓치는 것을 방지)
- `OpenInViewRoutingIntegrationTest` — 즉시구매(buyout) 시나리오 재현: `readOnly` 조회 후 `REQUIRES_NEW` write 트랜잭션이 트랜잭션 단위로 올바르게 재라우팅되는지 검증 (회귀 방지)

### 수정 테스트 (1건)

- `CardCommandServiceTest` — B2 방어 조치(`findByIdWithPokemon` 재조회)에 따른 테스트 수정

### 의도적 제외

- `application-prod.yaml` / `application-local.yaml` — `spring.jpa.open-in-view` 설정을 별도로 추가하지 않음. Spring Boot의 프로필별 yaml은 base `application.yaml`과 **deep-merge**되므로, base에 추가한 `open-in-view: false`가 그대로 전파된다. 각 프로필 파일에는 `ddl-auto` 등 프로필 고유 설정만 있는 `jpa.hibernate` 블록이 존재하므로, 해당 블록을 건드리지 않고 보존한다.

---

## 거부된 대안 (Rejected Options)

| 대안 | 기각 이유 |
|------|----------|
| **요청 내 트랜잭션 순서를 재배치하여 첫 접근을 write로 강제** | 호출 순서는 비즈니스 로직에 따라 달라지며, 향후 신규 API에서 동일 문제가 재발할 수 있음. 근본 원인(`open-in-view=true`로 인한 커넥션 고착) 자체를 해결하지 않는 임시 봉합 → 기각 |
| **`AuctionBuyoutTransactionService`에 `@Transactional` 대신 별도 트랜잭션 매니저/별도 DataSource 직접 주입** | 트랜잭션 경계마다 라우팅을 수동으로 관리해야 하므로 ADR-016에서 구축한 `AbstractRoutingDataSource` 자동 라우팅의 이점을 상실하고 관리 포인트가 급증 → 기각 |
| **`RoutingDataSource.determineCurrentLookupKey()`에서 커넥션 캐싱을 우회하는 커스텀 `ConnectionProxy` 도입** | `LazyConnectionDataSourceProxy`의 표준 동작을 오버라이드하는 비표준 패턴으로, 유지보수성과 Spring 업그레이드 호환성 저하 → 기각. `open-in-view=false`가 Spring 공식 권장 설정이며 가장 단순한 근본 해결책 |
| **모든 LAZY 연관관계를 EAGER로 전환** | `Card.pokemon`, `PokemonSet.series`는 대부분의 조회 경로에서 불필요하게 N+1 또는 과도한 fetch를 유발할 수 있음. 필요한 2개 지점(B1, B2)만 `fetch join` 재조회로 좁게 해결하는 것이 더 안전 → 기각 |

---

## 영향 (Consequences)

### 긍정적 효과

- ADR-016의 보류 항목("open-in-view=false 전환 검토")을 해소하며, RDS Read Replica 라우팅이 **트랜잭션 단위로 정확하게 동작**하게 됨
- `@Transactional(readOnly = true)`/`REQUIRES_NEW` 등 트랜잭션 전파 속성이 라우팅에 즉시 반영되어, 향후 추가되는 서비스 메서드도 별도 조치 없이 올바르게 라우팅됨
- 트랜잭션 종료 시점에 EntityManager/Session이 즉시 반환되어, 요청 전체에 걸친 불필요한 커넥션 점유가 줄어듦 (커넥션 풀 효율 개선)

### 부정적 효과 / 주의사항

- **LazyInitializationException 노출 위험**: `Card.pokemon`, `PokemonSet.series` 외에 향후 LAZY 연관관계가 추가될 경우, 트랜잭션 외부(`afterCommit` 콜백, 컨트롤러 응답 직렬화 등)에서의 접근 패턴을 신규 점검해야 함. 본 ADR에서 식별한 B1/B2 외 신규 위험 지점이 추가되지 않도록 코드 리뷰 시 LAZY 연관관계 신규 추가 여부를 확인할 것
- **트랜잭션 경계 외부에서의 엔티티 접근 전면 금지**: `open-in-view=false` 전환 이후, 컨트롤러/뷰 레이어에서 엔티티를 직접 직렬화하는 패턴이 있다면 영향을 받을 수 있음 (현재 코드베이스는 DTO/Response 변환을 트랜잭션 내부에서 수행하므로 영향 없음으로 확인됨)
- **테스트 작성 시 `@Transactional` 사용 주의**: `AuctionLifecycleEsIndexIntegrationTest`처럼 프로덕션 트랜잭션 경계(`afterCommit` 콜백 등)를 검증하는 테스트에서는 테스트 클래스/메서드에 `@Transactional`을 사용하면 테스트 트랜잭션이 프로덕션 커밋을 가려 회귀를 놓칠 수 있음. `TransactionTemplate`을 사용하여 실제 커밋이 발생하도록 작성해야 함

---

## 구현 체크리스트

### Phase 3b (이번 PR 범위)

- [x] `src/main/resources/application.yaml` — `spring.jpa.open-in-view: false` 추가
- [x] `src/test/resources/application.yaml` — `spring.jpa.open-in-view: false` 추가
- [x] `CardRepository.java` — `findByIdWithPokemon(Long id)` 신규 메서드 (fetch join)
- [x] `AuctionEsIndexService.java` — B1: `index()`의 `afterCommit` 콜백에서 `findByIdWithPokemon`으로 `Card` 재조회 후 `card.getPokemon()` 접근
- [x] `CardCommandService.java` — B2: `doIndexCard()`의 `afterCommit` 콜백에서 `findByIdWithPokemon`으로 `Card` 재조회 후 `card.getPokemon()` 접근
- [x] `AuctionEsIndexLazyLoadingIntegrationTest` 작성 — B1 시나리오 GREEN
- [x] `AuctionLifecycleEsIndexIntegrationTest` 작성 — `TransactionTemplate` 사용, `@Transactional` 미사용, GREEN
- [x] `OpenInViewRoutingIntegrationTest` 작성 — buyout 시나리오 재현, 트랜잭션 단위 재라우팅 검증 GREEN
- [x] `CardCommandServiceTest` 수정 — `findByIdWithPokemon` 재조회 반영
- [x] `./gradlew test` GREEN 확인 (전체 회귀 없음)

### 의도적 제외 (작업 범위 아님)

- [x] `application-prod.yaml` / `application-local.yaml` — 변경 없음 (deep-merge로 base 설정 전파, `ddl-auto`만 있는 `jpa.hibernate` 블록 보존)

---

## 검토 과정 (BLUEPRINT CRITIC)

- **Round 1**: `CardCommandService.doIndexCard()`의 `afterCommit` 콜백에서 `card.getPokemon()`에 접근하는 패턴이 `AuctionEsIndexService.index()`(B1)와 **동일한 LIE 위험(P0)**임을 추가 발견. 초기 BLUEPRINT에는 B1만 포함되어 있었음
- **Round 2**: B2(`CardCommandService.doIndexCard()`)를 구현 범위에 추가하고 `CardCommandServiceTest` 수정 항목을 반영 → **APPROVED**

---

## Phase 4 검증 결과

### REVIEW

**APPROVED** (P0: 0, P1: 0, P2: 2)

- P2 2건은 모두 기록용 지적으로, 코드 수정 없이 머지 가능한 수준으로 확인됨

### SECURITY

**PASS** (HIGH: 0, MEDIUM: 0, LOW: 1)

- LOW 1건: B1(`AuctionEsIndexService.index()`)의 `afterCommit` 콜백에서 수행하는 `findByIdWithPokemon` 재조회가 READ replica로 라우팅될 수 있어, replication lag가 발생하는 환경에서는 직전 커밋이 아직 replica에 반영되지 않아 read-after-write staleness가 발생할 가능성이 있음. 보안 영향은 없으며 ES 인덱싱 데이터 정합성 관점의 참고 사항으로만 기록(코드 수정 없음)

### 테스트 결과

전체 `BUILD SUCCESSFUL`. 신규/수정 테스트 결과:

| 테스트 | 결과 |
|--------|------|
| `AuctionEsIndexLazyLoadingIntegrationTest` | 2/2 GREEN |
| `AuctionLifecycleEsIndexIntegrationTest` | 2/2 GREEN |
| `OpenInViewRoutingIntegrationTest` | 3/3 GREEN |
| `CardCommandServiceTest` | 17/17 GREEN |
| `domain.auction.*` / `domain.card.*` 패키지 전체 | 회귀 없음, BUILD SUCCESSFUL |

---

## 관련 문서

- [ADR-016: RDS Read Replica 라우팅 도입 (코드 사전 준비)](ADR-016-rds-read-replica-routing-%23206.md) — 본 ADR이 다루는 라우팅 고착 버그의 원인이 된 `RoutingDataSource`/`LazyConnectionDataSourceProxy` 구성, "open-in-view=false 전환 검토" 보류 항목의 후속 처리
