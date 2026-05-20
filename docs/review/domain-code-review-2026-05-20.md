# 도메인 코드 리뷰 — 2026-05-20

## 대상 도메인
User, FreePost, Comment, Like (PR #47 refactor/domaindev/#43 기반)

## 리뷰 결과: CHANGES_REQUESTED → 수정 완료

### 수정된 이슈

#### MEDIUM — 입력 검증 @Valid 패턴 통일
- UpdateBankRequest, UpdateCommentRequest, UpdateFreePostRequest에 @Valid 추가
- CreateCommentRequest, UpdateCommentRequest DTO에 Bean Validation 어노테이션 통일
- CommentCommandService createComment/updateComment 중복 수동 blank 검증 제거

#### MEDIUM — FreePostRepositoryImpl 예외 일관성
- resolveOrder()의 IllegalArgumentException → FreePostException(INVALID_CONTENT)
- 도메인 예외로 GlobalExceptionHandler에서 일관되게 처리됨

#### LOW — @NotBlank message 누락
- UpdateBillingKeyRequest @NotBlank에 message 추가

### 잔존 이슈 (향후 처리)

| 이슈 | 사유 | 처리 시점 |
|------|------|-----------|
| GET /posts/free/{id}에서 incrementViewCount 호출 (CQRS 위반) | Redis 카운터로 비동기 분리 필요 | Redis 고도화 시 |
| FreePostQueryService getPost의 orElse("") | 탈퇴 유저 게시글 처리 정책 미정 | 팀 논의 후 |
| PageableExecutionUtils count 최적화 | 기능 영향 없음 | 개선 sprint |

## 아키텍처 상태
- CQRS 패턴: CommandService/QueryService 분리 유지
- 소프트 딜리트: @SQLRestriction("deleted_at IS NULL") BaseEntity 상속
- QueryDSL: 동적 검색/정렬 (FreePost, User 도메인)
- JWT 인증: @AuthenticationPrincipal CustomUserDetails 전면 적용
