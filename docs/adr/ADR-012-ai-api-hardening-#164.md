# ADR-012: AI API 강화 — DTO 분리, RateLimiter 단일화, 프롬프트 시드, WebFlux 블로킹 분석 (#164)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-01 |
| **상태** | Accepted |
| **이슈** | #164 |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

Issue #158(ADR-011) 구현 완료 후 Phase 4 코드리뷰에서 4개의 후속 문제가 식별되었다.
별도 이슈 #164로 분리하여 처리하며, 이 ADR은 해당 4개 결정 사항을 공식화한다.

1. `CardAnalysisResult`가 API 응답으로 직접 사용되어 LLM 내부 메타데이터가 클라이언트에 노출됨
2. `reanalyzeCard()` → `self.analyzeCard()` 호출 경로에서 Rate Limit 토큰이 이중 소비됨
3. `AiStreamController`의 서블릿 스레드 블로킹 호출이 WebFlux 환경에서 문제가 될 수 있음
4. `V1__ai_tables.sql`에 시드 INSERT가 없어 신규 환경 기동 시 `IllegalStateException` 발생

---

## 결정 (Decision)

### 1. DTO 분리 — CardAnalysisResponse 신규 생성

**결정**: `CardAnalysisResult`와 별개로 `CardAnalysisResponse` record를 신규 생성하여 API 응답 계약에 사용한다.

**포함 필드 (7개)**

| 필드 | 타입 | 설명 |
|------|------|------|
| `priceTrend` | enum | 가격 추세 (RISING / STABLE / FALLING) |
| `fairValueEstimate` | Long | 적정 가치 추정가 |
| `demandLevel` | enum | 수요 수준 (HIGH / MEDIUM / LOW) |
| `summary` | String | 분석 요약 |
| `highlights` | List\<String\> | 긍정 포인트 목록 |
| `riskFactors` | List\<String\> | 위험 요인 목록 |
| `keywords` | List\<String\> | 핵심 키워드 목록 |

**제거 필드 (4개)**

| 필드 | 제거 이유 |
|------|---------|
| `analysisModel` | LLM 구현 세부 사항 — 내부 DB/메트릭에만 저장 |
| `promptTokens` | 과금 메트릭 — 외부 API 계약에 불필요 |
| `completionTokens` | 과금 메트릭 — 외부 API 계약에 불필요 |
| `analyzedAt` | 분석 타임스탬프 — 내부 이력 추적 전용 |

**근거**

`analysisModel`, `promptTokens`, `completionTokens`, `analyzedAt`은 LLM 공급사·모델·비용 정보를 노출하므로 외부 API 계약에서 제거해야 한다. 이 값들은 내부 DB(`card_analyses` 테이블)와 Micrometer 메트릭으로 이미 추적된다.

`CardAnalysisResult`를 직접 수정하여 4개 필드를 제거하는 방안은 `BeanOutputConverter`가 이 레코드의 필드를 LLM 프롬프트 스키마 생성에 사용하므로 채택 불가하다. LLM 응답 역직렬화 스키마(내부)와 API 응답 계약(외부)을 분리하는 것이 올바른 설계 방향이다.

**트레이드오프**

- 장점: LLM 구현 세부 사항이 외부 계약에서 은닉됨; LLM 교체 시 API 응답 변경 불필요; `CardAnalysisResult`의 `BeanOutputConverter` 스키마 안전하게 유지
- 단점: API 응답에서 4개 필드 제거는 파괴적 변경(Breaking Change)이므로 클라이언트 사전 공지 필요; record 2개 유지로 소폭의 관리 비용 증가

### 2. RateLimiter 단일화 — reanalyzeCard() 중복 제거

**결정**: `reanalyzeCard()`의 `@RateLimiter` 어노테이션과 `reanalyzeCardRateLimitFallback()` 메서드를 동시에 제거한다. Rate Limit는 `analyzeCard()` 단일 지점에서만 처리한다.

**문제 분석**

```text
reanalyzeCard()          ← @RateLimiter (토큰 1 소비)
    └─ self.analyzeCard() ← @RateLimiter (토큰 1 소비)
                           → 1회 호출에 토큰 2개 소비
```

`reanalyzeCardRateLimitFallback()`은 `self.analyzeCard()`의 fallback이 먼저 작동하므로 실질적으로 도달할 수 없는 dead code 상태이다.

**제거 범위**

| 대상 | 처리 |
|------|------|
| `reanalyzeCard()`의 `@RateLimiter` 어노테이션 | 제거 |
| `reanalyzeCardRateLimitFallback()` 메서드 | 제거 |
| `reanalyzeCard()`의 `@CircuitBreaker` | **유지** |
| `reanalyzeCard()`의 `@CacheEvict` | **유지** |

**주의사항**: `@RateLimiter` 어노테이션과 `reanalyzeCardRateLimitFallback()` 메서드를 반드시 원자적으로 동시에 제거해야 한다. 어노테이션만 제거하고 fallback 메서드를 남기면 Resilience4j가 불필요한 빈 등록을 시도할 수 있으며, 반대로 fallback 메서드만 제거하고 어노테이션을 남기면 `fallbackMethod` 참조 오류로 애플리케이션 기동이 실패한다.

**트레이드오프**

- 장점: `reanalyzeCard()` 호출 시 Rate Limit 토큰 소비 50% 감소; dead code 제거로 코드 복잡도 감소; Rate Limit 정책이 단일 진입점(`analyzeCard()`)에서 명확하게 관리됨
- 단점: `reanalyzeCard()` 경로에 별도의 Rate Limit 정책을 적용할 수 없게 됨 (현재 요구사항에서는 동일 정책이 적절하다고 판단)

### 3. AiStreamController 블로킹 호출 분석 (코드 변경 없음)

**결정**: `AiStreamController`의 현재 구현은 변경하지 않는다. Spring MVC 환경에서의 블로킹 호출은 정상 동작이다.

**분석 배경**

`AiStreamController`가 `Flux<ServerSentEvent<String>>`을 반환하기 전 서블릿 스레드에서 다음 블로킹 I/O 호출을 수행한다는 우려가 제기되었다.

| 블로킹 호출 | 위치 |
|------------|------|
| `sessionService.getOrCreateSession()` | Reactive 파이프라인 진입 전 |
| `sessionService.validateSessionOwner()` | Reactive 파이프라인 진입 전 |
| `sessionService.getRecentMessages()` | Reactive 파이프라인 진입 전 |
| `ragService.search()` | Reactive 파이프라인 진입 전 |

**확인 결과**

`build.gradle` 의존성 검토 결과:

- `spring-boot-starter-web` (Spring MVC / Tomcat) 사용 중
- `spring-boot-starter-webflux` **없음**
- `springdoc-openapi-starter-webmvc-ui` 존재 (Spring MVC 확인)

**결론**

Spring MVC 환경에서 Tomcat 서블릿 스레드는 블로킹을 허용한다. `Flux<ServerSentEvent<String>>` 반환은 SSE 스트리밍을 위한 것이며, Reactive 런타임(Netty 이벤트 루프)을 사용하지 않으므로 블로킹 우려가 적용되지 않는다. 코드 변경 불필요.

**향후 참고 (WebFlux 전환 시)**

향후 `spring-boot-starter-webflux`로 전환할 경우 서블릿 스레드의 블로킹 호출을 아래와 같이 래핑해야 한다.

```java
Mono.fromCallable(() -> sessionService.getOrCreateSession(sessionId))
    .subscribeOn(Schedulers.boundedElastic())
    .flatMap(session -> /* Reactive 파이프라인 */);
```

### 4. 프롬프트 시드 데이터 — V8 마이그레이션 추가

**결정**: `V8__ai_prompt_seed.sql` 신규 마이그레이션을 생성하여 등급별 프롬프트 시드 데이터를 INSERT하고 `card_grade` UNIQUE INDEX를 추가한다.

**문제 분석**

`V1__ai_tables.sql`은 `ai_prompt_templates` 테이블 DDL만 생성하고 시드 INSERT가 없다. 신규 환경 기동 시 `AiPromptTemplateService.getPrompt()`가 빈 테이블에서 조회를 수행하여 `IllegalStateException`이 발생한다.

`V1__ai_tables.sql`을 직접 수정하는 방안은 Flyway 체크섬 충돌로 인해 기존 환경의 마이그레이션이 실패하므로 채택 불가하다.

**마이그레이션 내용**

| 항목 | 내용 |
|------|------|
| 파일명 | `V8__ai_prompt_seed.sql` |
| 멱등성 | `WHERE NOT EXISTS` 조건으로 중복 INSERT 방지 |
| UNIQUE INDEX | `card_grade` 컬럼에 추가 |

**시드 등급 목록**

| `card_grade` | 용도 |
|-------------|------|
| `DEFAULT` | 등급 미지정 폴백 프롬프트 |
| `PSA_10` | PSA 10등급 전용 프롬프트 |
| `PSA_9` | PSA 9등급 전용 프롬프트 |
| `BGS_10` | BGS 10등급 전용 프롬프트 |

**UNIQUE INDEX 추가 이유**

`AiPromptTemplateService.findByCardGradeAndIsActiveTrue()`는 단일 결과를 기대한다. UNIQUE INDEX 없이 동일 `card_grade`로 다중 활성 레코드가 존재하면 `IncorrectResultSizeDataAccessException`이 발생한다. UNIQUE INDEX로 데이터 무결성을 DB 수준에서 보장한다.

**트레이드오프**

- 장점: 신규 환경 기동 시 `IllegalStateException` 완전 제거; `WHERE NOT EXISTS` 멱등 INSERT로 재실행 안전성 확보; UNIQUE INDEX로 데이터 무결성 DB 수준 보장
- 단점: 시드 데이터가 SQL 파일에 하드코딩되어 프롬프트 내용 수정 시 새 마이그레이션 필요 (애플리케이션 기동 시 자동 적용되므로 관리 UI 우선순위는 낮음)

---

## 고려한 대안 (Alternatives Considered)

### CardAnalysisResult 필드 직접 제거 (Decision 1 대안)

- **거절 이유**: `BeanOutputConverter`는 Java record의 필드 구조를 기반으로 LLM에 전달할 JSON 스키마를 생성한다. `analysisModel`, `promptTokens` 등의 필드를 제거하면 LLM 응답 역직렬화 스키마가 깨져 파싱 실패가 발생한다. 스키마 유지와 API 계약 분리를 동시에 달성하려면 record 분리가 유일한 방법이다.

### reanalyzeCard()에 analyzeCard() Rate Limit 우회 (Decision 2 대안)

- **거절 이유**: `analyzeCard()`의 `@RateLimiter`를 우회하는 내부 호출 경로를 만들면 Rate Limit 적용 여부가 호출 경로에 따라 달라져 일관성이 깨진다. Rate Limit는 LLM API 비용 보호 목적이므로 경로에 관계없이 단일 지점에서 일관 적용해야 한다.

### WebFlux 전환 (Decision 3 대안)

- **거절 이유**: 현재 이슈 범위를 크게 초과하는 작업이다. Spring MVC에서 WebFlux로의 전환은 서블릿 필터, Spring Security 설정, 모든 서비스 레이어의 비동기 전환을 수반한다. 현재 블로킹 호출이 실제 문제를 일으키지 않음이 확인되었으므로 전환 필요성이 없다.

### V1__ai_tables.sql 직접 수정 (Decision 4 대안)

- **거절 이유**: Flyway는 이미 적용된 마이그레이션 파일의 체크섬을 DB에 기록한다. `V1__ai_tables.sql`을 수정하면 체크섬 불일치로 기존 환경에서 `FlywayValidationException`이 발생하여 애플리케이션 기동이 실패한다. 신규 버전 마이그레이션 추가가 Flyway 표준 운영 방식이다.

---

## 결과 (Consequences)

### 긍정적 영향

- `CardAnalysisResponse` 분리로 LLM 내부 메타데이터(`analysisModel`, `promptTokens`, `completionTokens`, `analyzedAt`) 4개 필드가 외부 API 계약에서 은닉된다.
- `reanalyzeCard()` Rate Limit 이중 소비가 제거되어 동일 Rate Limit 버킷에서 토큰 소비가 50% 감소한다.
- `V8__ai_prompt_seed.sql` 적용으로 신규 환경 기동 시 `IllegalStateException`이 완전히 제거된다.
- WebFlux 전환 논의 시 Decision 3의 분석 결과를 기준선으로 활용할 수 있다.

### 부정적 영향 / 트레이드오프

- `CardAnalysisResponse`의 4개 필드 제거는 파괴적 변경(Breaking Change)이다. 기존 클라이언트가 `analysisModel`, `promptTokens`, `completionTokens`, `analyzedAt` 필드를 사용 중이라면 마이그레이션 기간을 설정하고 사전 공지해야 한다.
- `reanalyzeCard()` Rate Limit 제거 후 재분석 요청이 `analyzeCard()` Rate Limit에만 의존하므로, 재분석 남용 패턴이 발생할 경우 `reanalyzeCard()` 전용 Rate Limit 정책 재도입을 검토한다.
- 시드 프롬프트 내용 변경 시 새 Flyway 마이그레이션이 필요하다. 운영 중 프롬프트 수정은 관리 API 또는 직접 DB 업데이트를 사용한다.

---

## 관련 문서

- `docs/adr/ADR-011-ai-requirements-completion-#158.md` — AI 요구사항 완성 (Phase 4 코드리뷰에서 이 ADR 항목들이 식별됨)
- `docs/adr/ADR-004-ai-features.md` — AI 기능 통합 전략 (LLM·벡터 DB 초기 결정)
- `docs/guide/ai-api-guide.md` — AI 기능 API 엔드포인트 명세 (CardAnalysisResponse 변경 반영 필요)

## 관련 코드

- `CardAnalysisResult` — `BeanOutputConverter` 역직렬화 스키마 record (변경 없음, 내부 전용 유지)
- `CardAnalysisResponse` — 신규 API 응답 record (7개 필드, 4개 LLM 메타데이터 필드 미포함)
- `CardAnalysisService.reanalyzeCard()` — `@RateLimiter` 제거, `reanalyzeCardRateLimitFallback()` 제거
- `CardAnalysisService.analyzeCard()` — `@RateLimiter` 단일 지점 유지 (변경 없음)
- `AiStreamController` — 블로킹 호출 현행 유지 (Spring MVC 환경 확인)
- `V8__ai_prompt_seed.sql` — 신규 Flyway 마이그레이션 (DEFAULT, PSA_10, PSA_9, BGS_10 시드 + UNIQUE INDEX)
- `build.gradle` — `spring-boot-starter-web` 의존성 (WebFlux 미사용 확인 근거)

---

## Phase 4 검토 결과

### 수용된 피드백

- **V8 마이그레이션 UNIQUE INDEX 멱등성**: ALTER TABLE ADD UNIQUE INDEX에 information_schema.STATISTICS 존재 체크 추가. Flyway repair 시나리오에서 중복 인덱스 생성 오류 방지.

### 거부된 피드백 (근거 포함)

- **analyzeCardRateLimitFallback 제거 제안**: analyzeCard()의 @RateLimiter(fallbackMethod="analyzeCardRateLimitFallback")가 여전히 활성 상태이므로 해당 메서드는 dead code가 아님. 제거 불가.
- **프롬프트에서 내부 필드(analysisModel 등) 제거 제안**: BeanOutputConverter가 CardAnalysisResult 전체 필드를 역직렬화하므로 프롬프트에서 해당 필드 지시가 없으면 파싱 실패. LLM 응답 스키마는 CardAnalysisResult와 일치해야 함. 제거 불가.
- **Redis fail-open, GET/POST 비대칭 rate limit**: 사전 존재 설계 이슈. #164 범위 외.

### 보안 감사 결과

- SQL 인젝션 없음 (정적 리터럴 값만 사용)
- CardAnalysisResponse가 LLM 내부 필드 4개 완전 제거 확인
- reanalyzeCard() Rate Limit 변경: 서비스 레이어 @RateLimiter(프로세스 전역 Resilience4j) 제거 → 컨트롤러에서 RedisRateLimiter("rate:user:ai:{userId}" 키) 사용자별 적용. analyzeCard()는 @RateLimiter(name="aiEndpoint") 프로세스 전역 Resilience4j 유지.
