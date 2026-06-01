# Security Audit — Issue #148

| 항목 | 내용 |
|------|------|
| **Date** | 2026-05-30 |
| **Issue** | #148 — 보안 취약점 후속 조치 |
| **Branch** | `feat/security-ratelimit/#148` |
| **작성자** | DOCS Agent (Phase 4.5) |

---

## 1. 보안 점검 체크리스트

| # | 점검 항목 | 결과 | 비고 |
|---|-----------|------|------|
| 1 | **H1: XFF [last] ALB 구조 대응** | FIXED | `split()[last]` 적용 |
| 2 | **H2: login 레이트리밋** | FIXED | `rate:ip:login:{ip}` 적용 |
| 3 | **M2: 쓰기 엔드포인트 레이트리밋 완성** | FIXED | 컨트롤러 11개 + 서비스 2개 |
| 4 | **M3: AuctionController Admin `@PreAuthorize`** | FIXED | 메서드 레벨 3개 적용 |
| 5 | **M6: `application-prod.yaml` `ddl-auto: validate`** | FIXED | 신규 파일 생성 |
| 6 | **L3: CORS 환경변수화** | FIXED | `CORS_ALLOWED_ORIGINS` |

---

## 2. 발견된 취약점 및 조치

### [FIXED] H1 — XFF 헤더 첫 번째 IP 신뢰 (ALB 구조 미대응)

| 항목 | 내용 |
|------|------|
| **심각도** | HIGH |
| **위치** | `HttpRequestUtils.java` |
| **문제** | `X-Forwarded-For` 헤더의 첫 번째 값(`[0]`)을 클라이언트 IP로 사용 — ALB 환경에서 공격자가 임의 IP를 삽입 가능 |
| **수정** | `split()[last]` 방식으로 마지막 신뢰 가능한 IP 추출 |

---

### [FIXED] H2 — /auth/login 레이트리밋 미적용

| 항목 | 내용 |
|------|------|
| **심각도** | HIGH |
| **위치** | `AuthController.java`, `RateLimitService.java` |
| **문제** | 로그인 엔드포인트에 레이트리밋 없음 — 브루트포스·크리덴셜 스터핑 공격 노출 |
| **수정** | Redis 키 `rate:ip:login:{ip}` 기반 IP별 레이트리밋 적용, 초과 시 429 반환 |

---

### [FIXED] M2 — 쓰기 엔드포인트 레이트리밋 누락

| 항목 | 내용 |
|------|------|
| **심각도** | MEDIUM |
| **위치** | 컨트롤러 11개, 서비스 2개 |
| **문제** | POST·PUT·DELETE 등 쓰기 엔드포인트에 레이트리밋 미적용 — 과도한 리소스 소모 가능 |
| **수정** | 전체 쓰기 엔드포인트에 레이트리밋 어노테이션 적용 |

---

### [FIXED] M3 — AuctionController Admin 메서드 인가 누락

| 항목 | 내용 |
|------|------|
| **심각도** | MEDIUM |
| **위치** | `AuctionController.java` |
| **문제** | Admin 전용 핸들러 3개에 메서드 레벨 `@PreAuthorize` 없음 — URL 패턴 단일 의존 |
| **수정** | 메서드 레벨 `@PreAuthorize("hasRole('ADMIN')")` 3개 적용으로 이중 방어 |

---

### [FIXED] M6 — 프로덕션 `ddl-auto` 미설정

| 항목 | 내용 |
|------|------|
| **심각도** | MEDIUM |
| **위치** | `application-prod.yaml` (신규) |
| **문제** | 프로덕션 환경 전용 설정 파일 부재 — `ddl-auto` 기본값에 의존, 스키마 변경 사고 위험 |
| **수정** | `application-prod.yaml` 신규 생성, `ddl-auto: validate` 명시 |

---

### [FIXED] L3 — CORS 허용 도메인 하드코딩

| 항목 | 내용 |
|------|------|
| **심각도** | LOW |
| **위치** | `SecurityConfig.java` |
| **문제** | CORS `allowedOrigins` 값이 코드에 하드코딩 — 환경별 유연성 부족 |
| **수정** | 환경변수 `CORS_ALLOWED_ORIGINS`로 외부화 |

---

## 3. Accepted Risk

### [RISK] Redis fail-open 정책

| 항목 | 내용 |
|------|------|
| **심각도** | MEDIUM |
| **내용** | Redis 장애 시 레이트리밋을 우회(fail-open)하여 요청을 허용 |
| **선택 이유** | 가용성 우선 — 부트캠프 환경에서 Redis 일시 장애로 인한 서비스 중단이 더 큰 위험으로 판단 |

---

### [RISK] CORS 패턴 검증 미적용

| 항목 | 내용 |
|------|------|
| **심각도** | LOW |
| **내용** | `CORS_ALLOWED_ORIGINS` 환경변수 값에 대한 패턴·형식 검증 없음 |
| **선택 이유** | 운영자 직접 설정 구조 — 잘못된 값 입력 시 CORS 오류로 즉시 발견 가능 |

---

### [RISK] localhost 와일드카드 기본값

| 항목 | 내용 |
|------|------|
| **심각도** | LOW |
| **내용** | CORS 기본값에 `localhost` 패턴 포함 |
| **선택 이유** | 로컬 개발 전용 — 프로덕션에서는 `CORS_ALLOWED_ORIGINS` 환경변수로 재정의 |

---

## 4. 관련 문서

- ADR: `docs/adr/ADR-009-security-ratelimit-#148.md`
- Test Report: `docs/test/test-report-#148.md`
- API Spec: `docs/policy/API_SPEC.md`
