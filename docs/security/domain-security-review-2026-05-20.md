# 도메인 보안 리뷰 — 2026-05-20

## 대상 도메인
User, FreePost, Comment, Like

## 결과: HIGH 이슈 3건 수정 완료

### [HIGH] 관리자 API PII 노출 — 수정 완료
- **엔드포인트:** GET /api/v1/admin/users
- **문제:** UserResponse에 bankAccount(전체), phone(전체)가 마스킹 없이 노출
- **수정:** AdminUserResponse 분리 — phone 뒤 4자리, bankAccount 뒤 4자리만 표시
- **파일:** AdminUserResponse.java (신규), UserQueryService.java, UserController.java

### [HIGH] 입력값 검증 누락 — 수정 완료
- **문제:** UpdateBankRequest, UpdateUserRequest, UpdateCommentRequest에 @Valid/@NotBlank 없음
  - 공백/null 값이 DB에 저장 가능
- **수정:** 각 DTO에 @NotBlank(message) 추가, 컨트롤러에 @Valid 추가
  - UpdateUserRequest: partial update 패턴 유지, 서비스 레이어에서 non-null blank 거부
- **파일:** UpdateBankRequest.java, UpdateCommentRequest.java, UserController.java, CommentController.java, UserCommandService.java

### [CRITICAL 오탐] Spring Boot 3.5.14
- 보안 스캐너가 "존재하지 않는 버전"으로 판단했으나 실제 의존성 해결 성공
- ./gradlew dependencies로 3.5.14 확인됨 — 오탐 처리

## 통과 항목
- SQL Injection: QueryDSL 타입 안전 쿼리, JPQL named parameter — PASS
- 수평적 권한 상승: 모든 수정 엔드포인트에서 JWT userId ↔ 리소스 userId 검증 — PASS  
- 빌링키 TOCTOU: updateBillingKeyIfNull atomic UPDATE (WHERE billing_key IS NULL) — PASS
- password 노출: UserResponse에 미포함 — PASS
- billingKey: hasBillingKey boolean으로 마스킹 — PASS

## 향후 과제
- incrementViewCount GET 핸들러 분리 (Redis/Kafka 고도화 시)
- WebhookRateLimitFilter Redis 기반 rate-limit 구현
