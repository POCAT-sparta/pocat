# 프롬프트 개선 이력

| 항목 | 내용 |
|------|------|
| **작성일** | 2026-05-26 |
| **관련 ADR** | ADR-004-ai-features.md |
| **관련 가이드** | ai-api-guide.md |

---

## 목적

LLM 프롬프트는 출력 품질·토큰 비용·일관성에 직접 영향을 미친다. 이 문서는 카드 시장 분석 및 AI 어시스턴트 프롬프트의 변경 이력을 추적하여, 개선 근거와 효과를 기록한다.

---

## v1.0 — 초기 버전 (2026-05-26)

| 항목 | 내용 |
|------|------|
| **변경 동기** | 최초 프롬프트 설계 — 구조화 출력(Structured Output) 및 Tool Calling 기반 초안 |
| **적용 대상** | 카드 시장 분석 API, AI 카드 어시스턴트 |
| **작성자** | 개발팀 전체 |
| **하이퍼파라미터** | temperature=0.3 / max_tokens=1024 (ADR-011 참조) |

### 프롬프트 구조

#### 카드 시장 분석 AI (Structured Output)

프롬프트는 `ai_prompt_template` 테이블에서 카드 등급(`cardGrade`)을 키로 조회하며, 등급별 템플릿이 없을 경우 `DEFAULT` 템플릿으로 폴백한다(`AiPromptTemplateService`). 템플릿은 `{cardContext}`와 `{format}` 두 개의 변수를 치환한다.

```
[User Prompt 템플릿 구조 — PromptTemplate 치환 변수]

{cardContext}  ← CardAnalysisService.buildCardContext(card) 생성값
  카드 이름: {card.getName()}
  등급: {card.getGrade()}          ← PSA_10 / PSA_9 / BGS_10 등
  시리즈: {series.getName()}        ← 없으면 "N/A"
  세트: {pokemonSet.getName()}      ← 없으면 "N/A"
  URL: {card.getImageUrl()}         ← 없으면 "N/A"
  레어도: {card.getRarity()}

{format}       ← BeanOutputConverter<CardAnalysisResult>.getFormat() 자동 생성
  (Spring AI가 CardAnalysisResult 레코드 필드로부터 JSON Schema 지시문 생성)
  포함 필드: priceTrend(RISING/STABLE/FALLING), fairValueEstimate(Long),
             demandLevel(HIGH/MEDIUM/LOW), summary(String),
             highlights(List<String>), riskFactors(List<String>),
             keywords(List<String>), analysisModel(String),
             promptTokens(Integer), completionTokens(Integer)
```

등급별 `promptText` 본문은 DB 시드 데이터(`ai_prompt_template` 테이블)에서 관리하며, 초기 버전에서는 BACKEND 에이전트가 삽입한 DEFAULT 템플릿이 모든 등급에 적용된다.

#### AI 카드 어시스턴트 (Tool Calling + RAG)

System Prompt는 `AiAssistantService.chat()`에 하드코딩되어 있다.

```
[System Prompt — AiAssistantService v1.0]

"당신은 POCAT 카드 거래 플랫폼 어시스턴트입니다.
사용자가 카드, 경매, 입찰에 관한 질문을 할 때 정확하고 도움이 되는 정보를 제공하세요.
다음의 RAG 컨텍스트를 활용하여 답변하세요:
{ragContext}
{historyContext}"

RAG 컨텍스트 구성:
  - RagService.search(query): Elasticsearch cosine 유사도 ≥0.7, Top-5 문서 검색
  - RagService.buildContext(docs): "[{type} #{id}]: {text}" 형식으로 포맷팅
  - 빈 결과 시: "관련 문서를 찾을 수 없습니다." (LLM 호출은 계속 진행)

대화 이력 컨텍스트:
  - 최근 10턴(MAX_HISTORY_TURNS) 메시지를 "\n대화 이력:\n" 접두사와 함께 System Prompt에 추가
  - 이력 없을 경우 빈 문자열

등록된 Tools:
  - CardSearchTool: 카드 검색
  - AuctionTool: 활성 경매 조회
  - BidTool: 사용자 입찰 이력 조회
```

### 초기 프롬프트의 한계점

1. **어시스턴트 System Prompt 하드코딩**: 분석 AI의 `promptText`는 DB 테이블(`ai_prompt_template`)에서 관리되어 재배포 없이 수정 가능하나, 어시스턴트의 System Prompt는 `AiAssistantService` 소스코드에 직접 삽입되어 있다. 변경 시 재배포가 필요하고, 등급별 분기가 불가능하다.

2. **RAG 빈 결과 시 LLM 계속 호출**: `ragContext`가 "관련 문서를 찾을 수 없습니다." 문자열일 때에도 LLM 호출이 진행된다. Tool Calling으로 실시간 DB 조회가 보완되지만, RAG 미적중 시 응답 품질이 저하될 수 있으며 불필요한 토큰 비용이 발생한다.

3. **분석 AI 프롬프트 시드 데이터 부재**: `V1__ai_tables.sql`은 `ai_prompt_template` 테이블 스키마만 정의하고 초기 시드 데이터(DEFAULT 프롬프트 본문)를 포함하지 않는다. 별도 데이터 삽입 없이 기동 시 `IllegalStateException("DEFAULT 프롬프트를 찾을 수 없습니다")`가 발생한다.

4. **temperature 단일값 적용**: 분석 AI(구조화 출력)와 어시스턴트(자연어 대화) 모두 `temperature=0.3`을 사용한다. 어시스턴트의 대화 자연스러움이 제한될 수 있으며, 향후 기능별 `ChatClient` 분리를 통한 개별 설정 적용이 필요하다.

### 토큰 소비

| 구분 | 평균 프롬프트 토큰 | 평균 완성 토큰 | 평균 총 토큰 |
|------|-----------------|--------------|-------------|
| 카드 시장 분석 | 측정 대기 | ~405 (설계 추정) | 측정 대기 |
| AI 어시스턴트 (단일 턴) | 측정 대기 | ~200~400 (설계 추정) | 측정 대기 |

> 실측값은 `card_ai_analysis.prompt_tokens / completion_tokens` 컬럼 및 `AiUsageMetrics` Micrometer 메트릭으로 수집 예정.

### 출력 품질 평가

| 평가 항목 | 점수 (1~5) | 비고 |
|----------|-----------|------|
| 구조화 출력 JSON 파싱 성공률 | 측정 대기 | BeanOutputConverter 파싱 실패율 추적 필요 |
| 한국어 자연스러움 | 측정 대기 | temperature=0.3 제약으로 다소 단조로울 가능성 |
| 분석 정확도 (적정가 추정) | 측정 대기 | 실거래 데이터 대비 fairValueEstimate 오차 측정 필요 |
| Tool Calling 정확도 | 측정 대기 | CardSearchTool·AuctionTool·BidTool 호출 성공률 측정 필요 |

### 다음 개선 방향

1. 어시스턴트 System Prompt를 DB 테이블로 이전하여 재배포 없이 수정 가능하도록 개선
2. `ai_prompt_template` 초기 시드 데이터 Flyway 마이그레이션 스크립트 추가
3. RAG 미적중 시 LLM 호출 생략 여부 정책 재검토 (Layer 2 환각 방어 강화)
4. 실측 토큰 데이터 수집 후 v2.0 프롬프트 개선 계획 수립

---

## v2.0 — 등급별 프롬프트 분기 도입

| 항목 | 내용 |
|------|------|
| **변경 동기** | v1.0 단일 DEFAULT 프롬프트로는 PSA_10(최상급)과 PSA_9(일반급) 카드의 시장 특성 차이를 반영하지 못함 |
| **변경 일자** | 2026-05-30 |
| **적용 대상** | 카드 시장 분석 API (`CardAnalysisService`) |
| **작성자** | 개발팀 (BACKEND 에이전트) |
| **하이퍼파라미터** | temperature=0.3 / max_tokens=1024 유지 (ADR-011 참조) |

### 변경 내용

`AiPromptTemplateService` 도입으로 `ai_prompt_template` DB 테이블에서 카드 등급(`cardGrade`)을 키로 프롬프트를 조회한다. 등급별 전용 템플릿이 없으면 `DEFAULT` 템플릿으로 폴백한다.

```
[등급별 프롬프트 분기 구조]

PSA_10 템플릿:
  - 등급 특화 지시: 최상급 카드 희소성·수집가 수요·경매 프리미엄 요소 강조
  - 적정가 추정 기준: PSA_10 팝 리포트(PSA Population Report) 기반 희소도 가중치 적용

PSA_9 템플릿:
  - 등급 특화 지시: 일반 유통 카드 대비 가격 프리미엄 범위 및 등급 하락 리스크 명시
  - 적정가 추정 기준: 최근 90일 거래 중앙값 기반

BGS_10 템플릿:
  - 등급 특화 지시: Beckett Black Label 기준 극희소성·장기 보유 투자 가치 관점 분석

DEFAULT 템플릿:
  - PSA_10/PSA_9/BGS_10 매핑 미존재 시 폴백
  - 일반 시장 분석 지시사항 적용
```

v1.0 대비 변경점:
- `AiPromptTemplateService.getPrompt(grade)` 호출로 등급 매핑 추상화
- `CardAnalysisService.analyzeCard()`에서 `card.getGrade().toString()`을 키로 DB 조회
- 등급별 `promptText` 본문은 Flyway 마이그레이션 시드 데이터로 관리

### 토큰 소비 비교

| 구분 | v1.0 총 토큰 | v2.0 총 토큰 | 변화율 |
|------|------------|------------|-------|
| 카드 시장 분석 (PSA_10) | 측정 대기 | 측정 대기 | 등급 특화 지시사항 추가로 프롬프트 토큰 약 +15% 추정 |
| 카드 시장 분석 (PSA_9) | 측정 대기 | 측정 대기 | 동일 추정 |
| AI 어시스턴트 | — | — | 변경 없음 (어시스턴트 경로 미변경) |

### 출력 품질 비교

| 평가 항목 | v1.0 | v2.0 | 개선 여부 |
|----------|------|------|---------|
| JSON 파싱 성공률 | 측정 대기 | 측정 대기 | — |
| 한국어 자연스러움 | 측정 대기 | 측정 대기 | — |
| 분석 정확도 (적정가 추정) | 측정 대기 | 측정 대기 | 등급별 가격 특성 반영으로 향상 예상 |

### 다음 개선 방향

1. RAG 빈 결과 시에도 LLM 호출이 계속 진행되어 존재하지 않는 카드 데이터 생성(환각) 발생 — Layer2 환각 방어 도입 필요
2. BeanOutputConverter 파싱 실패 시 즉시 오류 반환 — 재시도 로직(Layer1) 도입 필요
3. `latencyMs` 메트릭 실측값이 `0L` 하드코딩 — 실측 전달로 개선 필요

---

## v3.0 — 환각 방어 Layer1/2 도입 (#158)

| 항목 | 내용 |
|------|------|
| **변경 동기** | RAG 빈 결과에도 LLM 호출 → 존재하지 않는 카드 데이터 생성(환각) 발생; BeanOutputConverter 파싱 실패 시 즉시 오류 반환으로 불필요한 에러 노출 |
| **변경 일자** | 2026-06-01 |
| **적용 대상** | 카드 시장 분석 API (`CardAnalysisService`), AI 어시스턴트 (`AiAssistantService`, `AiStreamController`) |
| **작성자** | 개발팀 (BACKEND 에이전트, #158) |
| **하이퍼파라미터** | temperature=0.3 / max_tokens=1024 유지 (ADR-011 참조) |

### 변경 내용

#### Layer1 — 파싱 실패 시 1회 재시도 (`CardAnalysisService.callLlmForAnalysis`)

LLM이 간헐적으로 JSON 형식을 벗어난 응답을 반환할 때 `BeanOutputConverter.convert()` 파싱이 실패한다. v3.0부터 첫 번째 파싱 실패 시 동일 프롬프트로 1회 즉시 재시도한다. 재시도에서도 파싱이 실패하면 `PARSE_FAILED_AFTER_RETRY` 에러 메트릭을 기록하고 `ServiceException(INTERNAL_SERVER_ERROR)`를 발생시킨다.

```
callLlmForAnalysis() 흐름:
  1. LLM 호출 → response 획득
  2. outputConverter.convert(response) 시도
     ├─ 성공 → 결과 반환
     └─ 실패 → warn 로그 + 1회 재시도
         ├─ 재시도 성공 → 결과 반환
         └─ 재시도 실패 → error 로그 + PARSE_FAILED_AFTER_RETRY 메트릭 + ServiceException
```

#### Layer2 — RAG 빈 결과 시 LLM 미호출 (`AiAssistantService.chat`, `AiStreamController.stream`)

`ragResults.isEmpty()` 체크를 LLM 호출 전에 수행한다. RAG 결과가 없으면 LLM을 호출하지 않고 안내 메시지를 즉시 반환한다. 세션 메시지는 정상적으로 저장하여 대화 이력 연속성을 유지한다.

```
안내 메시지:
  "관련 카드 정보를 찾을 수 없습니다. 카드명, 등급(PSA_10/PSA_9 등), 또는 경매 번호를 더 구체적으로 알려주세요."
```

스트리밍 경로(`AiStreamController`)에서는 즉시 두 개의 SSE 이벤트를 반환한다:
- `id=1, event=message`: 안내 메시지
- `id=2, event=done`: 완료 신호

#### 응답시간 실측 (`latencyMs`)

`CardAnalysisService.analyzeCard()`와 `AiAssistantService.chat()` 모두 ChatClient 호출 전후에 `System.currentTimeMillis()`로 latency를 측정하여 `aiUsageMetrics.recordUsage()`에 실값을 전달한다. v2.0까지는 `0L` 하드코딩이었다.

### 토큰 소비 비교

| 구분 | v2.0 총 토큰 | v3.0 총 토큰 | 변화율 |
|------|------------|------------|-------|
| 카드 시장 분석 (정상 경로) | 측정 대기 | 측정 대기 | Layer1 재시도 발생 시 최대 2배 |
| AI 어시스턴트 (RAG 빈 결과) | 측정 대기 | 0 | Layer2로 해당 케이스 LLM 비용 100% 절감 |
| AI 어시스턴트 (RAG 정상) | 측정 대기 | 측정 대기 | 변화 없음 |

### 출력 품질 비교

| 평가 항목 | v2.0 | v3.0 | 개선 여부 |
|----------|------|------|---------|
| JSON 파싱 성공률 | 측정 대기 | 측정 대기 | Layer1 재시도로 파싱 실패율 감소 예상 |
| 한국어 자연스러움 | 측정 대기 | 측정 대기 | 변화 없음 |
| 환각 발생률 (RAG 빈 결과) | 측정 대기 | 0% | Layer2로 RAG 미적중 환각 완전 차단 |

### 다음 개선 방향

1. 스트리밍 경로 토큰 모니터링: `AiStreamController`는 현재 토큰 수를 메트릭에 기록하지 않음 — 스트리밍 완료 후 usage 수집 기능 추가 필요
2. 프롬프트 A/B 테스트: Layer1 재시도 발생률이 일정 임계값(예: 5%) 이상이면 프롬프트 구조 재검토
3. RAG 검색 임계값 튜닝: cosine 유사도 임계값 0.7이 과도하게 높아 RAG 빈 결과 빈도가 높을 경우 임계값 하향 조정 검토
4. Layer2 안내 메시지 다국어화 및 카테고리별 세분화

---

## 프롬프트 관리 원칙

1. **버전 변경 기준**: 프롬프트 구조 또는 System Prompt 핵심 지시사항이 변경된 경우에만 버전을 올린다. 단순 오탈자 수정은 현재 버전 내 수정으로 처리한다.
2. **측정 필수**: 버전 업 전후 토큰 소비 및 출력 품질을 반드시 비교 측정한다.
3. **롤백 기준**: 신규 버전에서 JSON 파싱 실패율이 5% 이상이거나 사용자 불만이 증가하면 이전 버전으로 즉시 롤백한다.
4. **보안 주의**: 프롬프트에 사용자 개인정보(이름, 연락처 등)를 직접 포함하지 않는다.
