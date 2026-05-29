# ADR-008: Admin 인가 이중 방어, N+1 개선, Swagger UI 도입 (#143)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-29 |
| **상태** | Accepted |
| **이슈** | #143 |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

### Admin 컨트롤러 단일 방어 구조

Admin 관련 컨트롤러 5개(`AdminOrderController`, `AdminSettlementController`, `AdminUserController`, `AdminAuctionController`, `AdminChatController`)는 Spring Security `SecurityConfig`의 URL 패턴(`/api/v1/admin/**`) 하나에만 의존하고 있었다. ADR-007에서 `AdminCardController`에 메서드 레벨 인가를 추가하는 패턴을 확립했으나, 나머지 Admin 컨트롤러 5개는 동일한 조치가 이루어지지 않아 Security 설정 변경 시 내부 서비스 메서드까지 보호되지 않는 취약 구조가 잔존하였다.

### OrderQueryService N+1 문제

`OrderQueryService.getOneOrder()`에서 주문의 구매자(`buyerId`)와 판매자(`sellerId`)를 각각 `userRepository.findById(buyerId)`, `userRepository.findById(sellerId)`로 개별 조회하고 있었다. 단건 주문 조회 시 User 조회만으로 DB 왕복이 2회 발생하여 총 4쿼리(Order 1 + User 2 + 추가 조회 1)가 실행되었다.

### API 문서화 도구 부재

프로젝트에 API 명세를 자동으로 생성·제공하는 도구가 없었다. 개발자와 QA가 엔드포인트 목록을 코드에서 직접 확인해야 했으며, 협업 효율이 저하되었다.

---

## 결정 (Decision)

### 1. Admin 컨트롤러 5개에 클래스 레벨 @PreAuthorize 추가

`AdminOrderController`, `AdminSettlementController`, `AdminUserController`, `AdminAuctionController`, `AdminChatController` 5개 컨트롤러의 클래스 선언부에 `@PreAuthorize("hasRole('ADMIN')")` 애노테이션을 추가한다.

- URL 패턴 보안(SecurityConfig)과 메서드 보안(@PreAuthorize)을 함께 적용하는 Defense-in-Depth 원칙을 준수한다.
- ADR-007에서 `AdminCardController`에 적용한 패턴과 일관성을 확보한다.
- 클래스 레벨 적용으로 해당 컨트롤러의 모든 핸들러 메서드에 일괄 적용되어 누락 위험을 최소화한다.

### 2. OrderQueryService.getOneOrder — 배치 조회로 변경

`userRepository.findById(buyerId)` + `userRepository.findById(sellerId)` 두 번의 개별 호출을 `userRepository.findAllById(List.of(buyerId, sellerId))` 단일 배치 조회로 변경한다.

- DB 쿼리 수: 4쿼리 → 3쿼리 (User 조회 2회 → 1회)
- 조회 결과 Map으로 변환 후 buyerId·sellerId 기준으로 각 User 엔티티를 추출한다.

### 3. springdoc-openapi-starter-webmvc-ui 추가 및 SwaggerConfig 작성

`springdoc-openapi-starter-webmvc-ui` 의존성을 `build.gradle`에 추가하고, `SwaggerConfig` 클래스를 신규 작성한다.

- `/swagger-ui.html` 경로로 Swagger UI에 접근 가능하다.
- SecurityConfig에서 `/swagger-ui/**`, `/v3/api-docs/**` 경로를 인증 예외 처리한다.

---

## 고려한 대안 (Alternatives Considered)

### 메서드 레벨 @PreAuthorize (핸들러별 개별 적용)

- **거절 이유**: 클래스 내 모든 핸들러에 반복 적용이 필요하여 코드 중복이 증가하고 향후 핸들러 추가 시 누락 가능성이 높다. 컨트롤러 전체가 동일 ADMIN 역할을 요구하는 구조에서는 클래스 레벨 적용이 명확하고 유지보수에 유리하다.

### SettlementQueryService.getOneSettlement 배치 조회 적용

- **거절 이유**: `getOneSettlement()`의 내부 조회 흐름에 순차적 의존성(sequential dependency)이 존재한다. 선행 조회 결과에 따라 후속 조회 대상이 결정되는 구조이므로 `findAllById` 배치 조회로 단순 치환이 불가능하다. 별도 리팩터링 검토가 필요하다.

### Springfox (구버전 Swagger 라이브러리)

- **거절 이유**: Springfox는 Spring Boot 3.x와 호환성 문제가 있으며 유지보수가 사실상 중단된 상태다. `springdoc-openapi`는 OpenAPI 3 표준을 지원하고 Spring Boot 3.x와 공식 호환된다.

---

## 결과 (Consequences)

### 긍정적 영향

- Admin 컨트롤러 전체에 Defense-in-Depth 인가 구조가 확립되어 `AdminCardController`와 일관성이 확보된다.
- Security 설정 변경 또는 URL 패턴 실수 시에도 메서드 레벨 인가가 최후 방어선으로 동작한다.
- 단건 주문 조회 시 DB 쿼리 1회 감소로 응답 성능이 소폭 향상된다.
- `/swagger-ui.html`에서 API 명세를 시각적으로 확인할 수 있어 개발·QA 협업 효율이 개선된다.

### 부정적 영향 / 트레이드오프

- Swagger UI 엔드포인트(`/swagger-ui/**`, `/v3/api-docs/**`)가 인증 없이 접근 가능하므로 프로덕션 환경에서는 별도 접근 제어 또는 비활성화를 검토해야 한다.
- 개별 엔드포인트에 대한 Swagger 어노테이션(`@Operation`, `@ApiResponse` 등) 전체 문서화는 이번 범위에 포함되지 않는다. 별도 이슈로 처리한다.

### 새로운 의존성

- `springdoc-openapi-starter-webmvc-ui` — Swagger UI 및 OpenAPI 3 명세 자동 생성

---

## 관련 코드

- `AdminOrderController`, `AdminSettlementController`, `AdminUserController`, `AdminAuctionController`, `AdminChatController` — 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 추가
- `OrderQueryService.getOneOrder()` — `findAllById` 배치 조회로 변경
- `SwaggerConfig` — 신규 작성 (OpenApiBean 정의)
- `SecurityConfig` — `/swagger-ui/**`, `/v3/api-docs/**` 인증 예외 추가
- `build.gradle` — `springdoc-openapi-starter-webmvc-ui` 의존성 추가
