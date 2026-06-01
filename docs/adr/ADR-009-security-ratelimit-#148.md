# ADR-009: 보안 취약점 후속 조치 — X-Forwarded-For 수정·레이트리밋 완성·인가 일관성·운영 설정 (#148)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-29 |
| **상태** | Accepted |
| **이슈** | #148 |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

### H1: XFF(X-Forwarded-For) 헤더 첫 번째 값 신뢰 — IP 스푸핑 취약점

`HttpRequestUtils`에서 `X-Forwarded-For` 헤더 값을 파싱할 때 첫 번째 값(`[0]`)을 실제 클라이언트 IP로 신뢰하고 있었다. ALB(Application Load Balancer) 환경에서 ALB는 실제 클라이언트 IP를 헤더 맨 뒤에 append한다. 따라서 `[0]` 신뢰는 공격자가 임의의 IP를 헤더 앞에 삽입하여 레이트리밋·IP 기반 보안 정책을 우회할 수 있는 IP 스푸핑 취약점을 유발한다.

### H2: `/auth/login` 엔드포인트 레이트리밋 미적용

`/auth/signup`, `/auth/reissue`에는 IP 기반 레이트리밋이 적용되어 있었으나 `/auth/login`은 누락된 상태였다. 로그인 엔드포인트는 자격증명 브루트포스 공격의 주요 대상임에도 불구하고 무제한 요청이 허용되었다.

### M2: 쓰기 엔드포인트 레이트리밋 불완전 적용

경매(Auction)·입찰(Bid)·채팅(Chat) 등 일부 쓰기 엔드포인트에는 레이트리밋이 적용되어 있었으나 주문(Order)·결제(Payment)·환불(Refund)·카드(Card)·사용자(User)·AI 엔드포인트는 미적용 상태였다. 엔드포인트별 보호 수준이 불균일하여 고부하 공격 및 자원 남용에 노출되어 있었다.

### M3: `AuctionController` Admin 핸들러 메서드 레벨 인가 누락

`AuctionController`에는 일반 사용자용 핸들러와 Admin용 핸들러가 혼재한다. ADR-007·ADR-008에서 순수 Admin 컨트롤러에는 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 패턴을 확립하였으나, `AuctionController`는 클래스 레벨 적용이 불가능한 구조였다. Admin 전용 핸들러 3개(`registerAuction`, `closeAuction`, `deleteAuction`)에 메서드 레벨 인가가 누락되어 SecurityConfig URL 패턴 단일 방어에 의존하고 있었다.

### M6: 프로덕션 환경 DDL 자동 실행 위험

`application-prod.yaml`이 존재하지 않아 프로덕션 환경에서 `ddl-auto` 설정이 기본값 또는 공통 설정에 의존하는 상태였다. 잘못된 기본값(`create`, `update`)이 적용될 경우 프로덕션 데이터베이스 스키마가 의도치 않게 변경되거나 손실될 위험이 있었다.

### L3: CORS `allowedOriginPatterns` 하드코딩

`SecurityConfig`의 CORS 설정에서 허용 출처(`allowedOriginPatterns`)가 코드에 하드코딩되어 있었다. 배포 환경별 도메인 변경 시 코드 수정 및 재배포가 필요하고, 민감한 도메인 정보가 소스코드에 노출된다.

---

## 결정 (Decision)

### 1. [H1] HttpRequestUtils XFF 파싱 — `[0]` → `[last]`

`HttpRequestUtils`의 XFF 헤더 파싱 로직을 수정하여 마지막 값(`[last]`)을 실제 클라이언트 IP로 사용한다.

- ALB 환경에서 ALB가 신뢰할 수 있는 실제 클라이언트 IP를 XFF 헤더 맨 뒤에 append하는 동작 방식에 맞춘다.
- `[0]` 신뢰 시 공격자가 헤더 앞에 임의 IP를 삽입하는 IP 스푸핑이 가능하므로, `[last]`를 사용해 ALB가 보장하는 값만 신뢰한다.
- XFF 헤더가 없는 경우 `request.getRemoteAddr()`로 폴백한다.

### 2. [H2] `/auth/login` IP 기반 레이트리밋 추가

`/auth/login` 엔드포인트에 IP 기반 레이트리밋을 적용한다. signup·reissue 엔드포인트와 동일한 패턴을 사용한다.

- Redis 키 패턴: `rate:ip:login:{ip}`
- 적용 방식: signup·reissue 엔드포인트와 동일한 Bucket4j 기반 IP 레이트리밋 구현 재사용

### 3. [M2] 쓰기 엔드포인트 레이트리밋 완성

미적용 상태였던 쓰기 엔드포인트에 사용자 ID 기반 레이트리밋을 추가한다. Admin 엔드포인트는 제외한다.

**Redis 키 패턴 (전체)**

| 유형 | 키 패턴 |
|------|---------|
| IP 기반 | `rate:ip:login:{ip}` |
| IP 기반 | `rate:ip:signup:{ip}` |
| IP 기반 | `rate:ip:reissue:{ip}` |
| 사용자 기반 | `rate:user:auction:{userId}` |
| 사용자 기반 | `rate:user:bid:{userId}` |
| 사용자 기반 | `rate:user:chat:{userId}` |
| 사용자 기반 | `rate:user:order:{userId}` |
| 사용자 기반 | `rate:user:payment:{userId}` |
| 사용자 기반 | `rate:user:refund:{userId}` |
| 사용자 기반 | `rate:user:card:{userId}` |
| 사용자 기반 | `rate:user:user:{userId}` |
| 사용자 기반 | `rate:user:ai:{userId}` |

- 적용 위치: 컨트롤러 레벨 또는 서비스 레벨 혼합 적용 (기존 패턴 준수)
- Admin 쓰기 엔드포인트는 레이트리밋 제외 (아래 "Admin 제외 근거" 참조)

### 4. [M3] `AuctionController` Admin 핸들러 메서드 레벨 `@PreAuthorize` 추가

`AuctionController` 내 Admin 전용 핸들러 3개에 메서드 레벨 `@PreAuthorize("hasRole('ADMIN')")` 애노테이션을 추가한다.

- 대상 메서드: `registerAuction`, `closeAuction`, `deleteAuction`
- 클래스 레벨 적용이 불가능한 이유: `AuctionController`에 일반 사용자용 핸들러와 Admin용 핸들러가 혼재하므로 클래스 레벨 `@PreAuthorize` 적용 시 일반 사용자 핸들러까지 Admin 인가가 요구된다.
- ADR-007·ADR-008에서 확립한 Defense-in-Depth 원칙을 `AuctionController`의 구조적 제약 내에서 일관되게 적용한다.

### 5. [M6] `application-prod.yaml` 신규 생성 — `ddl-auto: validate`

프로덕션 전용 설정 파일 `application-prod.yaml`을 신규 생성하고 `spring.jpa.hibernate.ddl-auto: validate`를 명시한다.

- `validate`: 애플리케이션 기동 시 엔티티와 실제 DB 스키마의 일치 여부를 검증하되, 스키마를 수정하지 않는다.
- 프로덕션 환경에서 의도치 않은 DDL 실행(테이블 생성·삭제·변경)을 원천 차단한다.

### 6. [L3] SecurityConfig CORS `allowedOriginPatterns` 환경변수화

CORS 허용 출처를 환경변수(`CORS_ALLOWED_ORIGINS`)로 외부화한다.

- `SecurityConfig`에서 `@Value("${cors.allowed-origins}")` 또는 환경변수 바인딩으로 허용 출처를 주입받는다.
- 배포 환경별 도메인을 코드 변경 없이 환경변수 또는 설정 파일로 관리한다.

---

## 고려한 대안 (Alternatives Considered)

### XFF `[0]` 유지 + 별도 화이트리스트 검증

- **거절 이유**: 화이트리스트 기반 검증은 인프라 구성 변경 시마다 목록을 갱신해야 하며 운영 부담이 증가한다. ALB가 보장하는 `[last]` 값을 직접 신뢰하는 것이 구조적으로 단순하고 안전하다.

### Admin 쓰기 엔드포인트 레이트리밋 적용

- **거절 이유**: Admin 사용자는 극소수이며 `hasRole('ADMIN')` 인가로 이중 보호된다. 운영 배치작업(대량 데이터 처리, 마이그레이션 등)에서 레이트리밋이 정상 운영을 방해할 수 있다. Admin 계정 탈취 시나리오는 레이트리밋보다 계정 보안 강화로 대응한다.

### `AuctionController` Admin 핸들러 분리 (별도 AdminAuctionController)

- **거절 이유**: 컨트롤러 분리는 라우팅 구조, URL 패턴, SecurityConfig 수정 등 범위가 크며 이번 이슈 범위를 초과한다. 메서드 레벨 `@PreAuthorize` 적용으로 동등한 보안 효과를 달성할 수 있다. 컨트롤러 분리는 별도 리팩터링 이슈로 검토한다.

### `ddl-auto: none` (검증도 생략)

- **거절 이유**: `none`은 스키마 불일치를 침묵하여 런타임에 예상치 못한 오류를 유발할 수 있다. `validate`는 배포 직후 스키마 불일치를 기동 단계에서 즉시 감지하여 빠른 롤백을 가능하게 한다.

### CORS 허용 출처 프로파일별 yaml 분기

- **거절 이유**: 환경마다 yaml 파일을 별도 관리하면 변경 시 여러 파일을 동기화해야 하는 부담이 생긴다. 환경변수 단일 지점 관리가 12-Factor App 원칙에 부합하고 CI/CD 파이프라인과 연동이 용이하다.

---

## 결과 (Consequences)

### 긍정적 영향

- ALB 환경에서 XFF 기반 IP 스푸핑 취약점이 제거되어 IP 기반 레이트리밋·보안 정책의 신뢰성이 확보된다.
- 로그인 엔드포인트 브루트포스 공격이 IP 레이트리밋으로 차단되어 자격증명 보호 수준이 향상된다.
- 전체 쓰기 엔드포인트에 레이트리밋이 완성되어 자원 남용 및 DoS 공격 표면이 축소된다.
- `AuctionController` Admin 핸들러에 Defense-in-Depth 인가가 적용되어 ADR-007·ADR-008의 인가 일관성 원칙이 전체 컨트롤러로 확장된다.
- 프로덕션 환경에서 의도치 않은 DDL 실행이 차단되고 스키마 불일치가 기동 단계에서 조기 감지된다.
- CORS 허용 출처 환경변수화로 배포 유연성이 향상되고 민감 도메인 정보가 소스코드에서 제거된다.

### 부정적 영향 / 트레이드오프

- XFF `[last]` 신뢰는 ALB를 경유하지 않는 직접 접근(예: 로컬 개발, 헬스체크) 시 IP 추출 결과가 달라질 수 있다. 로컬 프로파일에서는 별도 처리 또는 `RemoteAddr` 폴백을 확인해야 한다.
- 레이트리밋 Redis 키 증가로 Redis 메모리 사용량이 소폭 늘어난다. 키 TTL 설정을 통해 자동 만료로 관리한다.
- `ddl-auto: validate`는 엔티티-스키마 불일치 시 애플리케이션 기동 자체가 실패한다. 마이그레이션 스크립트(Flyway/Liquibase)와 배포 순서를 철저히 관리해야 한다.

### 새로운 파일

- `application-prod.yaml` — 프로덕션 전용 JPA DDL 설정

---

## 관련 코드

- `HttpRequestUtils` — XFF 파싱 `[0]` → `[last]` 수정
- `AuthController` (또는 `RateLimitFilter`) — `/auth/login` IP 레이트리밋 추가
- `OrderController`, `PaymentController`, `RefundController`, `CardController`, `UserController`, AI 관련 컨트롤러/서비스 — 사용자 기반 레이트리밋 추가
- `AuctionController` — `registerAuction`, `closeAuction`, `deleteAuction` 메서드 레벨 `@PreAuthorize("hasRole('ADMIN')")` 추가
- `application-prod.yaml` — 신규 생성 (`ddl-auto: validate`)
- `SecurityConfig` — CORS `allowedOriginPatterns` 환경변수 바인딩 적용
