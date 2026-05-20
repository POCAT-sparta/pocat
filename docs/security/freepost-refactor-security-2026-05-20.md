# Security Audit: FreePost Refactor & Bug Fixes

**Date:** 2026-05-20
**Branch:** `feat/domaindev/#59`
**Auditor:** SECURITY agent
**Scope:** FreePostRepository, FreePostRankingService, ViewCountFlushScheduler, FreePostCommandService, UserCommandService, CommentCommandService

---

## Result: PASS — No HIGH or CRITICAL issues found

---

## Checklist

### AuthN / AuthZ
**Status: CLEAR**
Service-layer ownership checks are present across all modified command services. `findFreePostAndVerifyOwner` and `findCommentAndVerifyOwner` helpers enforce that the requesting user is the resource owner before any mutation proceeds.

---

### Input Validation
**Status: CLEAR**
Potential finding (blank `createPost` body) was a false positive. The DTO is annotated with `@NotBlank`, which is enforced at the controller layer before the service is reached. No gap.

---

### SQL Injection
**Status: CLEAR**
All JPQL queries use named `@Param` bindings. No string concatenation in query construction. The new `createdAt >= :since` predicate in `findTopByPopularScore` follows the same safe binding pattern.

---

### Sensitive Data Exposure
**Status: CLEAR**
Two false positives investigated:
- `/users/me` endpoint returns the authenticated user's own data — not a cross-user leak.
- `billingKey` is already surfaced as a boolean flag (`hasBillingKey`); the raw key value is never serialized to API responses.

---

### Distributed Lock / Lease Time
**Status: CLEAR**
No distributed locks are used in the changed files. The `@Transactional(REQUIRES_NEW)` isolation in `ViewCountFlushScheduler` uses standard JPA transaction semantics only.

---

### Idempotency Key Collision
**Status: LOW (residual, pre-existing)**
A multi-instance rename race exists where two concurrent requests for the same user could generate identical transient keys. This is a pre-existing design issue and was **not introduced by this PR**. No distributed deduplication mechanism is currently in place.

**Risk assessment:** LOW. Concurrent renames for the same user are rare in practice. The impact is a benign duplicate-update rather than data loss.
**Recommendation:** Track as a follow-up task; a Redis `SET NX` guard or optimistic-lock check on the entity version would mitigate this.

---

## Summary Table

| Area | Status | Notes |
|------|--------|-------|
| AuthN / AuthZ | CLEAR | Ownership helpers present in all command services |
| Input Validation | CLEAR | `@NotBlank` on DTO; false positive dismissed |
| SQL Injection | CLEAR | All JPQL uses `@Param` bindings |
| Sensitive Data Exposure | CLEAR | Two false positives dismissed |
| Distributed Lock Lease | CLEAR | No distributed locks used |
| Idempotency Key Collision | LOW | Pre-existing race; not introduced by this PR |

---

## Verdict

**PASS.** No HIGH or CRITICAL security issues were found. The single LOW item is pre-existing and documented for follow-up. This PR does not worsen the security posture of the codebase.
