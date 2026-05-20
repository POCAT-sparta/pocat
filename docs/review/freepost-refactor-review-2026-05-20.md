# Code Review: FreePost Refactor & Bug Fixes

**Date:** 2026-05-20
**Branch:** `feat/domaindev/#59`
**Reviewer:** REVIEW agent
**Scope:** FreePostRepository, FreePostRankingService, ViewCountFlushScheduler, FreePostCommandService, UserCommandService, CommentCommandService

---

## Summary

Bug-fix and refactoring pass across the User, FreePost, and Comment domains. All actionable findings were resolved. No blockers remain.

---

## Findings

### 1. FreePostRepository — Popular Score Query Window (FIXED)

**File:** `FreePostRepository`
**Finding:** `findTopByPopularScore` had no time-bound, so arbitrarily old posts could rank at the top.
**Resolution:** Added `COMMENT_WEIGHT = 3` interface constant and a `createdAt >= :since` (7-day window) predicate to the JPQL query.

**Note — hardcoded multiplier in JPQL:**
SpEL `T()` expressions are not supported inside JPQL, so the literal `* 3` in the score formula cannot reference the constant at the query level. The constant still serves as the canonical source of truth in Java code. Accepted limitation; no further action required.

---

### 2. FreePostRankingService — Defensive Redis Parsing & N+1 Fix (FIXED)

**File:** `FreePostRankingService`
**Finding (a):** `ZSet` member parsing was not guarded; a malformed Redis entry would throw `NumberFormatException` and abort the entire ranking refresh.
**Resolution:** Per-entry try-catch wraps the parse; bad entries are logged and skipped.

**Finding (b):** `fallbackFromDb` issued one `findById` per post author — classic N+1.
**Resolution:** Replaced with a batch user fetch (`findAllById`); result mapped by ID for O(1) lookup.

**Additional:** `POPULAR_DAYS = 7` constant extracted; `FreePostRepository.COMMENT_WEIGHT` referenced directly instead of duplicating the literal.

---

### 3. ViewCountFlushScheduler — Transaction Isolation (FIXED)

**File:** `ViewCountFlushScheduler`
**Finding:** A single `@Transactional` method wrapped all three flush operations (views, likes, scraps). One failure rolled back all three.
**Resolution:** Split into three independent `@Transactional(REQUIRES_NEW)` methods via `@Lazy` self-injection. Each flush is now fully isolated; a failure in one does not affect the others. Per-call try-catch added inside `flush()` to log and continue.

---

### 4. FreePostCommandService — Dependency & Duplication Cleanup (FIXED)

**File:** `FreePostCommandService`
**Finding:** `CommentRepository` was injected solely to retrieve a count that is already maintained on the `FreePost` aggregate (`getCommentCount()`).
**Resolution:** `CommentRepository` dependency removed; `freePost.getCommentCount()` used directly.

**Additional:** Duplicated lookup-and-validate patterns extracted into private helpers:
- `findFreePostAndVerifyOwner`
- `findUserOrThrow`
- `validateIfPresent`

---

### 5. UserCommandService — Duplication Cleanup (FIXED)

**File:** `UserCommandService`
**Finding:** Inline lookup-and-validate patterns repeated across multiple methods.
**Resolution:** Extracted into `findUserOrThrow` and `validateIfPresent` helpers.

---

### 6. CommentCommandService — Duplication Cleanup (FIXED)

**File:** `CommentCommandService`
**Finding:** Inline comment-lookup-and-ownership-verify pattern repeated.
**Resolution:** Extracted into `findCommentAndVerifyOwner` helper.

---

## Skipped / Accepted Items

| Item | Reason |
|------|--------|
| JPQL `* 3` literal instead of constant reference | SpEL `T()` unsupported in JPQL; constant exists in Java — accepted limitation |
| Rename atomicity / distributed lock | Pre-existing design decision; distributed lock is out of scope for this PR |
| Deleted-post partial results in ranking | Acceptable UX trade-off; documented |
| `CommentCommandService` post-existence check | Pre-existing pattern; not introduced by this PR |

---

## Verdict

**APPROVED.** All actionable findings resolved. Codebase is in a cleaner and more resilient state than before this PR.
