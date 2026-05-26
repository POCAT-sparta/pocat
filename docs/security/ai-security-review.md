# AI Feature Security Review

**Feature**: AI 카드 분석 및 AI 어시스턴트 (#106)
**Review Date**: 2026-05-26
**Reviewer**: SECURITY Agent (Phase 4)

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 0 |
| Medium | 0 |
| Low | 2 (deferred) |
| OK | 4 |

Overall risk: **LOW** — 즉각적인 blocking 이슈 없음.

---

## OK — No Action Required

### 1. API Key 관리
- `GEMINI_API_KEY`는 환경 변수 `${GEMINI_API_KEY}`로 주입되며 코드에 하드코딩되지 않음.
- Spring AI 설정(`application.yml`)에서 `${GEMINI_API_KEY}` 참조 확인됨.

### 2. Tool Calling 입력 검증
- `CardSearchTool`, `AuctionTool`, `BidTool`의 LLM Tool Calling 입력값은 각각 `CardRepository`, `AuctionRepository`, `BidRepository`를 통해 처리되며, JPA 쿼리 파라미터 바인딩으로 SQL Injection 위험 없음.

### 3. Redis 캐시 TTL
- 카드 분석 캐시 TTL 24시간 설정으로 오래된 데이터 무한 누적 방지.
- 캐시 키는 카드 ID 기반으로 격리되어 사용자 간 데이터 오염 없음.

### 4. Circuit Breaker 폭발 반경 제한
- Resilience4j Circuit Breaker 적용으로 LLM(Gemini API) 장애 시 전체 서비스 영향 최소화.
- fallback 응답이 정의되어 있어 LLM 실패가 사용자에게 500 에러로 전파되지 않음.

---

## Low Severity — Noted, Deferred

### L-1. Tool Calling `findAll()` 전체 테이블 스캔
- **해당 컴포넌트**: `CardSearchTool`, `AuctionTool`, `BidTool`
- **현상**: 데이터 조회 시 `findAll()` 사용으로 테이블 전체 스캔 발생.
- **위험**: 데이터 증가 시 성능 저하 및 대량 데이터 노출 가능성.
- **권고**: 후속 PR에서 조건 기반 커스텀 JPQL 쿼리로 교체.
- **조치**: follow-up issue 등록 후 진행 예정 — 현재 트래픽 규모에서 즉각적인 보안 위협 없음.

### L-2. `AiChatSession` 사용자 격리
- **해당 컴포넌트**: `AiAssistantService`
- **현상**: 세션 접근 시 `validateSessionOwner()`로 userId 소유권 검증.
- **위험**: 현재 구현은 현재 규모에 적합하나, 세션 ID 예측 가능성 존재 시 우회 가능.
- **권고**: 세션 ID를 UUID v4로 생성하고 있는지 확인 (현재 적절).
- **조치**: 현재 규모에서 adequate — 사용자 증가 시 추가 검토 필요.

---

## Configuration Requirements

### 필수 환경 변수

| 변수명 | 설명 | 필수 여부 |
|--------|------|----------|
| `GEMINI_API_KEY` | Google Gemini API 인증 키 | 필수 |

> **배포 Runbook 주의**: `GEMINI_API_KEY`가 환경에 설정되지 않으면 AI 기능 전체가 기동 시 실패합니다. 배포 전 환경 변수 설정 여부를 반드시 확인하십시오.

### Spring AI 설정

```yaml
spring:
  ai:
    openai:
      base-url: https://generativelanguage.googleapis.com/v1beta/openai
      api-key: ${GEMINI_API_KEY}
```

- `spring.ai.openai.base-url`은 Gemini의 OpenAI 호환 엔드포인트를 가리킵니다.
- Spring AI OpenAI 클라이언트를 재사용하여 Gemini API와 통신합니다.

---

## References

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- POCAT ADR: AI 기능 아키텍처 결정 (`docs/adr/`)
