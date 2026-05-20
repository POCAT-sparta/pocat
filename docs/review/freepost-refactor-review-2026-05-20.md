# 코드 리뷰: FreePost 리팩터링 및 버그 수정

**날짜:** 2026-05-20
**브랜치:** `feat/domaindev/#59`
**리뷰어:** REVIEW 에이전트
**범위:** FreePostRepository, FreePostRankingService, ViewCountFlushScheduler, FreePostCommandService, UserCommandService, CommentCommandService

---

## 요약

User, FreePost, Comment 도메인 전반에 걸친 버그 수정 및 리팩터링 작업입니다. 발견된 모든 조치 사항이 해결되었으며, 남은 블로커는 없습니다.

---

## 발견 사항

### 1. FreePostRepository — 인기 점수 쿼리 시간 범위 (수정 완료)

**파일:** `FreePostRepository`
**발견 사항:** `findTopByPopularScore`에 시간 범위 제한이 없어, 오래된 게시글이 상위에 노출될 수 있었습니다.
**해결 방법:** `COMMENT_WEIGHT = 3` 인터페이스 상수를 추가하고, JPQL 쿼리에 `createdAt >= :since` (7일 범위) 조건을 추가했습니다.

**참고 — JPQL 내 하드코딩된 가중치:**
SpEL의 `T()` 표현식은 JPQL 내부에서 지원되지 않으므로, 점수 산식의 `* 3` 리터럴은 쿼리 레벨에서 상수를 참조할 수 없습니다. 해당 상수는 Java 코드 내에서 공식적인 기준값 역할을 계속 수행합니다. 허용된 제약 사항으로, 추가 조치는 필요하지 않습니다.

---

### 2. FreePostRankingService — Redis 파싱 방어 처리 및 N+1 수정 (수정 완료)

**파일:** `FreePostRankingService`
**발견 사항 (a):** `ZSet` 멤버 파싱에 방어 처리가 없어, Redis 항목이 올바르지 않은 형식일 경우 `NumberFormatException`이 발생하여 전체 랭킹 갱신이 중단될 수 있었습니다.
**해결 방법:** 각 항목별 try-catch로 파싱을 감싸, 잘못된 항목은 로그를 남기고 건너뜁니다.

**발견 사항 (b):** `fallbackFromDb`에서 게시글 작성자별로 `findById`를 개별 호출하는 전형적인 N+1 문제가 있었습니다.
**해결 방법:** 배치 조회(`findAllById`)로 교체하고, 결과를 ID 기준으로 매핑하여 O(1) 조회가 가능하도록 했습니다.

**추가 사항:** `POPULAR_DAYS = 7` 상수를 추출하고, 리터럴 중복을 피하기 위해 `FreePostRepository.COMMENT_WEIGHT`를 직접 참조하도록 변경했습니다.

---

### 3. ViewCountFlushScheduler — 트랜잭션 격리 (수정 완료)

**파일:** `ViewCountFlushScheduler`
**발견 사항:** 단일 `@Transactional` 메서드가 세 가지 플러시 작업(`TRADE_VIEW`, `FREE_VIEW`, `FREE_COMMENT`)을 모두 감싸고 있어, 하나의 실패가 나머지 셋 전체를 롤백시켰습니다.
**해결 방법:** `@Lazy` 셀프 인젝션을 통해 각각 독립적인 `@Transactional(REQUIRES_NEW)` 메서드로 분리했습니다. 이제 각 플러시는 완전히 격리되며, 한 작업의 실패가 나머지에 영향을 주지 않습니다. `flush()` 내부에 호출별 try-catch를 추가하여 `flushTrade()`, `flushFreeView()`, `flushFreeComment()` 각각의 오류를 로그로 남기고 계속 진행합니다.

---

### 4. FreePostCommandService — 의존성 및 중복 정리 (수정 완료)

**파일:** `FreePostCommandService`
**발견 사항:** `CommentRepository`가 `FreePost` 애그리거트의 `getCommentCount()`로 이미 관리되고 있는 댓글 수를 조회하는 용도만으로 주입되어 있었습니다.
**해결 방법:** `CommentRepository` 의존성을 제거하고, `freePost.getCommentCount()`를 직접 사용하도록 변경했습니다.

**추가 사항:** 반복되는 조회-검증 패턴을 아래 private 헬퍼 메서드로 추출했습니다:
- `findFreePostAndVerifyOwner`
- `findUserOrThrow`
- `validateIfPresent`

---

### 5. UserCommandService — 중복 정리 (수정 완료)

**파일:** `UserCommandService`
**발견 사항:** 여러 메서드에 걸쳐 인라인 조회-검증 패턴이 반복되어 있었습니다.
**해결 방법:** `findUserOrThrow` 및 `validateIfPresent` 헬퍼 메서드로 추출했습니다.

---

### 6. CommentCommandService — 중복 정리 (수정 완료)

**파일:** `CommentCommandService`
**발견 사항:** 댓글 조회 및 소유자 검증 패턴이 인라인으로 반복되어 있었습니다.
**해결 방법:** `findCommentAndVerifyOwner` 헬퍼 메서드로 추출했습니다.

---

## 건너뛴 항목 / 수용된 항목

| 항목 | 사유 |
|------|------|
| JPQL 내 `* 3` 리터럴 (상수 참조 불가) | JPQL에서 SpEL `T()` 미지원; Java 코드에 상수 존재 — 허용된 제약 사항 |
| 이름 변경 원자성 / 분산 락 | 기존 설계 결정 사항; 분산 락은 이번 PR 범위 외 |
| 랭킹에서 삭제된 게시글 부분 노출 | 허용 가능한 UX 트레이드오프; 문서화됨 |
| `CommentCommandService` 게시글 존재 여부 확인 | 기존 패턴; 이번 PR에서 도입된 것이 아님 |

---

## 최종 판정

**승인.** 발견된 모든 조치 사항이 해결되었습니다. 코드베이스는 이번 PR 이전보다 더 명확하고 견고한 상태입니다.
