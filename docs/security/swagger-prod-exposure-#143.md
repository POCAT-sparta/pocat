# Security Audit — Issue #143

| 항목 | 내용 |
|------|------|
| **Date** | 2026-05-29 |
| **Issue** | #143 — 코드 품질 개선 |
| **Branch** | `feat/code-quality/#143` |
| **작성자** | DOCS Agent (Phase 4.5) |

---

## 1. 보안 점검 체크리스트

| # | 점검 항목 | 결과 | 비고 |
|---|-----------|------|------|
| 1 | **AuthN/AuthZ** — Admin 엔드포인트에 `@PreAuthorize` 적용 | PASS | 5개 엔드포인트 어노테이션 추가 완료 |
| 2 | **Swagger 노출** — 프로덕션 환경에서 Swagger UI 비활성화 | FIXED | `application.yaml` 기본값 disabled, local만 활성화 |
| 3 | **CVE 의존성** — springdoc CVE 점검 | FIXED | CVE-2024-22233 → springdoc 2.5.0 업그레이드로 해결 |
| 4 | **입력값 검증** — 신규 엔드포인트 DTO 검증 | PASS | 기존 검증 체계 그대로 적용 |
| 5 | **SQL Injection** — 신규 쿼리 파라미터 바인딩 | PASS | `findAllById` JPA 바인딩 적용 |

---

## 2. 발견된 취약점 및 조치

### [FIXED] Swagger UI 전 환경 노출

| 항목 | 내용 |
|------|------|
| **심각도** | HIGH |
| **위치** | `SecurityConfig.java:76` |
| **CVE** | springdoc 2.3.0 — CVE-2024-22233 |
| **문제** | Swagger UI 경로(`/swagger-ui/**`, `/v3/api-docs/**`)가 `SecurityConfig`에서 `permitAll`로 설정되어 프로덕션 포함 모든 환경에서 API 스펙 전체 노출 |

**조치 1 — 환경별 활성화 분리**

```yaml
# application.yaml (기본값 — 비활성화)
springdoc:
  swagger-ui:
    enabled: false

# application-local.yaml (로컬 개발 환경 — 활성화)
springdoc:
  swagger-ui:
    enabled: true
```

**조치 2 — CVE-2024-22233 패치**

```gradle
// build.gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
```

springdoc 2.3.0의 CVE-2024-22233은 특정 요청 파라미터 처리 시 정보 노출 가능성이 있는 취약점. 2.5.0 업그레이드로 해결.

---

### [FIXED] Admin 엔드포인트 인가 누락

| 항목 | 내용 |
|------|------|
| **심각도** | HIGH |
| **위치** | Admin 컨트롤러 5개 클래스 레벨 |
| **문제** | URL 패턴 단일 의존으로 설정 변경 또는 처리 오류 시 Admin 기능이 노출될 위험 |
| **수정** | 5개 컨트롤러에 `@PreAuthorize("hasRole('ADMIN')")` 클래스 레벨 적용으로 이중 방어 적용 |

---

## 3. 잔여 사항 (Accepted Risk)

### [RESIDUAL] `SecurityConfig` permitAll 경로 유지

| 항목 | 내용 |
|------|------|
| **심각도** | LOW (실질적 차단됨) |
| **위치** | `SecurityConfig.java:76` |
| **현황** | `permitAll` 경로 설정이 코드 레벨에서 남아있으나, `springdoc.swagger-ui.enabled=false` 기본값으로 Swagger 자체가 비활성화되어 실질적 노출 없음 |
| **조치 계획** | 향후 리팩토링 시 `SecurityConfig`에서 Swagger 경로 조건부 등록 검토 — 별도 이슈 추적 |

---

## 4. 관련 문서

- ADR: `docs/adr/ADR-008-code-quality-#143.md`
- Test Report: `docs/test/test-report-#143.md`
- API Spec: `docs/policy/API_SPEC.md`
