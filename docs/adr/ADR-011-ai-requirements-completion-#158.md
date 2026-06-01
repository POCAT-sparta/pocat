# ADR-011: AI 요구사항 완성 — 하이퍼파라미터·대화 이력·환각 방어·벡터 DB 전략 (#158)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-01 |
| **상태** | Accepted |
| **이슈** | #158 |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

ADR-004(AI 기능 통합 전략)에서 LLM·벡터 DB·프레임워크 선택 결정이 이루어졌으나, 운영에 직접 영향을 미치는 세부 하이퍼파라미터(`temperature`, `max_tokens`), 대화 이력 관리(`MAX_HISTORY_TURNS`), 환각(hallucination) 방어 전략, 벡터 DB 기술 세부 사항에 대한 명시적 결정이 기록되지 않았다. 이 ADR은 해당 결정 사항을 공식화하여 이후 튜닝·장애 대응의 기준선(baseline)으로 삼는다.

---

## 결정 (Decision)

### 1. temperature=0.3 선택

**결정**: 카드 시장 분석 AI 및 AI 카드 어시스턴트 모두 `temperature=0.3`으로 통일한다.

**근거**

| AI 기능 | 요구 특성 | temperature 방향 |
|---------|---------|----------------|
| 카드 시장 분석 (Structured Output) | JSON 구조 준수 필수, 필드 누락·타입 오류 시 파싱 실패 | 낮을수록 유리 |
| AI 카드 어시스턴트 (Tool Calling) | 자연스러운 한국어 대화 응답 선호 | 다소 높아야 자연스러움 |

카드 시장 분석은 `BeanOutputConverter`로 JSON 파싱을 수행하므로 구조 이탈(예: 잘못된 키 이름, 타입 오류, 필드 누락)이 파싱 실패로 즉시 이어진다. 이를 최우선 방어 목표로 설정하여 분석 AI에 `temperature=0.3`을 적용한다.

어시스턴트는 자연스러운 대화가 필요하여 다소 높은 온도가 이상적이나, 설정 단순화와 Tool Calling 정확도 유지를 위해 동일한 `0.3`을 채택한다. 두 기능이 동일한 `ChatClient` 빈을 공유하는 현재 구조에서 기능별로 temperature를 분리하려면 별도 `ChatClient` 인스턴스가 필요하고, 이는 Spring AI 자동 구성 범위를 벗어나는 추가 구현 비용이 발생한다.

**트레이드오프**
- 장점: 설정 단일화로 운영 복잡도 최소화; 분석 AI에서 JSON 파싱 실패 위험 감소; Tool Calling 응답의 구조 일관성 확보
- 단점: 어시스턴트 응답의 자연스러움이 `temperature=0.7` 대비 다소 제한될 수 있음; 향후 어시스턴트 품질 향상을 위해 `ChatClient` 분리 재검토 필요

### 2. max_tokens=1024 선택

**결정**: LLM 완성 토큰 상한을 `max_tokens=1024`로 설정한다.

**근거**

카드 시장 분석 JSON 응답의 예상 크기를 기준으로 산정한다.

| JSON 필드 | 예상 토큰 |
|---------|---------|
| `priceTrend` (RISING/STABLE/FALLING) | ~5 |
| `fairValueEstimate` (숫자) | ~5 |
| `demandLevel` (HIGH/MEDIUM/LOW) | ~5 |
| `summary` (한국어 2~4문장) | ~120 |
| `highlights` (3~5개 항목) | ~100 |
| `riskFactors` (2~4개 항목) | ~80 |
| `keywords` (5~10개 단어) | ~40 |
| JSON 구조 오버헤드 (괄호·키·따옴표) | ~50 |
| **소계 (분석 JSON)** | **~405** |

어시스턴트 응답은 단일 대화 턴당 한국어 3~6문장 + Tool Calling 결과 요약으로 평균 200~400 토큰으로 추정된다.

1024는 분석 JSON(~405 토큰)과 어시스턴트 응답(~400 토큰) 모두를 여유 있게 수용하면서, 비정상적으로 긴 응답(환각성 장문 출력)을 억제하는 상한선이다. 2048로 확장 시 API 비용 증가 대비 실질 품질 향상이 미미하다고 판단하여 1024를 유지한다.

**트레이드오프**
- 장점: 장문 환각 응답 억제; API 토큰 비용 제어; 현재 응답 유형에 충분한 여유 확보
- 단점: 매우 복잡한 카드 세트 분석 또는 긴 대화 응답이 잘릴 가능성 존재; 향후 응답 복잡도 증가 시 상향 재검토 필요

### 3. MAX_HISTORY_TURNS=10 선택

**결정**: 멀티턴 어시스턴트 대화에서 컨텍스트로 주입할 최대 이력 턴 수를 10으로 제한한다.

**근거**

| 항목 | 계산 |
|-----|-----|
| 턴당 평균 토큰 (user + assistant) | ~400 토큰 |
| 10턴 누적 이력 토큰 | ~4,000 토큰 |
| RAG 컨텍스트 토큰 (Top-5 문서) | ~500 토큰 |
| System Prompt 토큰 | ~100 토큰 |
| **총 프롬프트 토큰 (10턴 기준)** | **~4,600 토큰** |
| Gemini 1.5 Flash 입력 컨텍스트 한도 | 1,000,000 토큰 (여유 충분) |

10턴은 단일 카드 거래 상담 세션의 실제 평균 대화 길이를 커버한다. 10턴 초과 시 세션 만료(`is_expired=true`) 처리를 통해 장기 세션을 제한하며, 사용자는 새 세션을 시작하도록 유도한다. 이 정책은 `AiSessionCleanupScheduler`와 연동하여 장기 미활동 세션을 자동 만료한다.

**트레이드오프**
- 장점: 프롬프트 토큰 비용 예측 가능; 장기 세션 남용 방지; 세션 만료 정책과 일관된 UX 제공
- 단점: 10턴 초과 대화 컨텍스트 손실; 복잡한 거래 협상 시나리오에서 문맥 단절 발생 가능; 향후 슬라이딩 윈도우 요약(Summarization) 전략으로 대체 검토 필요

### 4. 환각(Hallucination) 방어 전략

**결정**: 두 계층의 환각 방어 전략을 적용한다.

#### Layer 1: 구조화 출력 파싱 실패 → 1회 재시도

카드 시장 분석 AI는 `BeanOutputConverter`로 LLM 응답을 `CardAnalysisResult` 레코드로 파싱한다. 파싱 실패(JSON 구조 이탈, 필드 누락, 타입 오류)는 LLM이 지시된 출력 형식을 무시했거나 환각성 자유 텍스트를 생성했음을 의미한다.

현재 구현(`CardAnalysisService.callLlmForAnalysis`)에서 파싱 실패 시 `ServiceException(INTERNAL_SERVER_ERROR)`을 던지고 Circuit Breaker가 fallback을 수행한다. 이 ADR에서 1회 재시도 정책을 공식화한다.

| 단계 | 처리 |
|-----|-----|
| 1차 LLM 호출 | `BeanOutputConverter`로 파싱 시도 |
| 파싱 실패 | 동일 프롬프트로 1회 재시도 (백오프 없음) |
| 재시도 후 재실패 | `ServiceException` → Circuit Breaker fallback (캐시 또는 기본 응답) |

#### Layer 2: RAG 빈 결과 → LLM 미호출

`RagService.search()`가 `similarityThreshold=0.7` 기준으로 유사 문서를 찾지 못할 경우(`results.isEmpty()`), 빈 컨텍스트를 LLM에 주입하는 대신 "관련 문서를 찾을 수 없습니다." 문자열을 반환한다. 이는 관련 없는 컨텍스트를 주입하여 LLM이 근거 없는 답변을 생성하는 환각을 방지한다.

현재 구현(`RagService.buildContext`)에서 빈 리스트 처리가 이미 적용되어 있으며, 이 정책을 명시적으로 공식화한다.

**트레이드오프**
- 장점: JSON 파싱 오류가 즉각 감지되어 잘못된 데이터가 DB에 저장되지 않음; RAG 미적중 시 근거 없는 LLM 응답을 원천 차단
- 단점: Layer 1 재시도는 LLM API 호출 비용 2배 발생 가능; Layer 2에서 RAG 미적중 시 어시스턴트가 실시간 DB 조회(Tool Calling) 없이도 응답 품질이 저하될 수 있음

### 5. 벡터 DB 기술 세부 사항

**결정**: Elasticsearch 8.18 기반 벡터 스토어를 사용하며 cosine similarity, threshold=0.7, Top-K=5를 적용한다.

**배경**: ADR-004에서 Redis Vector Store를 1차 채택하였으나, 구현 과정에서 Redis Stack 모듈 설치 복잡도와 Spring AI 호환성 문제로 Elasticsearch VectorStore로 전환하였다. `application.yaml`의 `spring.ai.vectorstore.elasticsearch` 설정이 이를 반영한다.

| 항목 | 값 | 선택 근거 |
|-----|---|---------|
| 엔진 | Elasticsearch 8.18 | 기존 카드 검색 인프라 재활용; Spring AI 공식 지원 |
| 임베딩 모델 | `text-embedding-004` (Gemini) | LLM과 동일 공급사로 설정 단순화; 768차원 지원 |
| 인덱스명 | `pocat-ai-index` | 프로덕션·개발 환경 분리 |
| 차원(dimensions) | 768 | `text-embedding-004` 기본 출력 차원 |
| 유사도 함수 | cosine similarity | 텍스트 의미 유사도 측정에 표준; L2와 비교 시 벡터 크기 정규화 불필요 |
| 유사도 임계값 | 0.7 | 낮으면 관련 없는 문서 포함 → 환각 위험 증가; 높으면 RAG 미적중 증가; 0.7은 초기 운영 기준 |
| Top-K | 5 | 컨텍스트 토큰 오버헤드(~500 토큰) 대비 정보량 균형 |
| 스키마 초기화 | `initialize-schema: true` | 첫 기동 시 인덱스 자동 생성 |

**트레이드오프**
- 장점: Elasticsearch 기존 인프라 재활용으로 추가 컨테이너 불필요; cosine 유사도는 한국어 텍스트 의미 검색에 검증된 방식
- 단점: ADR-004의 Redis Vector 결정에서 변경 — Redis Stack 전환 비용 절감 반면 Elasticsearch 벡터 기능 학습 곡선 발생; `initialize-schema: true`는 프로덕션 환경에서 인덱스 재생성 위험이 있으므로 초기화 후 `false`로 전환 검토 필요

---

## 고려한 대안 (Alternatives Considered)

### temperature=0.7 (어시스턴트 전용 상향)

- **거절 이유**: 두 AI 기능이 동일한 `ChatClient` 빈을 공유하므로 기능별 temperature 분리를 위해 별도 `ChatClient` 인스턴스와 추가 자동 구성이 필요하다. 현재 이슈 범위를 초과하며, 대화 품질 개선 효과가 구현 비용을 정당화하지 못한다. 필요 시 별도 이슈로 분리한다.

### max_tokens=2048 (상향)

- **거절 이유**: 현재 분석 JSON 및 대화 응답 평균 크기가 각각 400~450 토큰 수준이므로 1024는 충분한 여유를 제공한다. 2048은 비용 증가 대비 실질 품질 향상이 미미하며, 장문 환각 응답 억제 효과가 감소한다.

### MAX_HISTORY_TURNS=20 (상향)

- **거절 이유**: 20턴 누적 이력은 ~8,000 토큰으로, RAG 컨텍스트·System Prompt 포함 시 총 프롬프트가 ~8,600 토큰에 달한다. 비용 증가 대비 10턴을 초과하는 대화 연속성의 실제 사용자 필요성이 검증되지 않았다. 10턴 초과 사용 패턴이 확인되면 슬라이딩 윈도우 요약 전략과 함께 재검토한다.

### 재시도 횟수 3회 (Layer 1)

- **거절 이유**: 파싱 실패가 프롬프트 구조 문제일 경우 동일 프롬프트로 3회 재시도해도 성공률이 낮다. LLM API 비용이 최대 4배(원본+3회 재시도) 증가한다. 1회 재시도 후 Circuit Breaker fallback이 비용·속도·안정성 균형에서 더 적합하다.

### L2 distance (유사도 함수 대안)

- **거절 이유**: L2(유클리드 거리) 기반 검색은 벡터 크기에 민감하여 정규화가 필요하다. cosine은 방향 유사도만 측정하므로 텍스트 임베딩의 의미 유사도 측정에 더 적합하고, Spring AI Elasticsearch VectorStore의 기본값이다.

---

## 결과 (Consequences)

### 긍정적 영향

- `temperature=0.3` 통일로 Structured Output JSON 파싱 안정성이 확보되어 분석 API 오류율이 감소한다.
- `max_tokens=1024`로 장문 환각 응답이 억제되고 API 토큰 비용이 예측 가능한 범위로 제어된다.
- `MAX_HISTORY_TURNS=10` + 세션 만료 정책으로 장기 세션 남용이 방지되고 프롬프트 비용이 일정 범위 내로 유지된다.
- 두 계층의 환각 방어(파싱 재시도 + RAG 빈 결과 미호출)로 잘못된 데이터가 DB에 저장되거나 사용자에게 노출될 위험이 감소한다.
- Elasticsearch 기반 벡터 DB가 기존 인프라와 통합되어 추가 운영 복잡도 없이 RAG 파이프라인이 동작한다.

### 부정적 영향 / 트레이드오프

- 어시스턴트의 대화 자연스러움이 `temperature=0.3` 제약으로 다소 제한될 수 있다. 사용자 피드백 수집 후 기능별 `ChatClient` 분리 여부를 재검토한다.
- Layer 1 재시도는 파싱 실패 시 API 비용 2배를 유발하므로, 파싱 실패율을 Micrometer 메트릭으로 추적하여 프롬프트 개선에 활용한다.
- `initialize-schema: true`는 프로덕션 첫 배포 후 `false`로 전환하여 인덱스 재생성 사고를 방지해야 한다.

---

## 관련 문서

- `docs/adr/ADR-004-ai-features.md` — AI 기능 통합 전략 (LLM·벡터 DB 초기 결정)
- `docs/guide/ai-prompt-history.md` — 프롬프트 개선 이력 (v1.0 초기 프롬프트 구조 기록)
- `docs/guide/ai-api-guide.md` — AI 기능 API 엔드포인트 명세

## 관련 코드

- `application.yaml` — `spring.ai.openai.chat.options.temperature: 0.3`, `max-tokens: 1024`, `spring.ai.vectorstore.elasticsearch.*`
- `AiAssistantService` — `MAX_HISTORY_TURNS = 10`, System Prompt 하드코딩
- `CardAnalysisService` — `callLlmForAnalysis()`, `BeanOutputConverter` 파싱, Circuit Breaker fallback
- `RagService` — `SIMILARITY_THRESHOLD = 0.7`, `TOP_K = 5`, `buildContext()` 빈 결과 처리
- `AiPromptTemplateService` — 카드 등급별 프롬프트 DB 조회 (`DEFAULT` 폴백)

## Phase 4 코드리뷰 결과

### 반영된 피드백

- `recordUsage()` 호출 시 `FALLBACK_MODEL` 하드코딩 → `result.analysisModel()` 실값 사용으로 수정
- 응답시간 테스트 `latencyMs > 0` → `>= 0` (CI 환경 flakiness 방지)

### 별도 이슈로 분리된 항목

- `CardAnalysisResult` DTO 클라이언트 노출 (`analysisModel`, `promptTokens` 등) — 기존 이슈
- `reanalyzeCard()` → `self.analyzeCard()` 경로 Rate Limit 이중 소비 — 기존 이슈, 별도 이슈 제기 필요
- `AiStreamController` Reactive 환경 블로킹 호출 — 기존 이슈
