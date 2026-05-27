# ADR-004: AI 기능 통합 전략 (Spring AI + Gemini + Redis Vector)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-26 |
| **상태** | Proposed |
| **결정자** | 개발팀 전체 |
| **최종 업데이트** | 2026-05-26 (Phase 2.5 — 초기 설계) |

---

## 맥락 (Context)

POCAT은 카드 거래(경매·직거래) 플랫폼으로, 현재 사용자들이 카드 시세 판단, 경매 참여 전략, 희귀 카드 검색 등을 직접 수행해야 한다. 이에 따라 다음 문제가 발생한다.

- **정보 비대칭**: 초보 사용자는 카드 시장 가격 흐름을 파악하기 어려워 과가격 입찰 또는 저가 거래 피해가 발생한다.
- **탐색 비효율**: 특정 조건(등급, 가격대, 종류)을 조합한 카드 검색이 UI 필터만으로는 번거롭다.
- **개인화 부재**: 사용자의 입찰 이력이나 선호 카드를 반영한 추천 기능이 없다.

이를 해결하기 위해 LLM 기반 AI 기능을 통합한다. 단, 기존 인프라(Redis, Prometheus/Micrometer, Resilience4j)를 최대한 재활용하여 운영 복잡도를 최소화하는 방향으로 결정한다.

---

## 결정 (Decision)

### 1. LLM: Google Gemini (Spring AI Google AI 스타터)

**결정**: `spring-ai-google-ai-gemini-spring-boot-starter`를 통해 Google Gemini를 LLM으로 채택한다.

**대안 검토**

| 후보 | 비용 | 한국어 품질 | 스프링 통합 | 선택 여부 |
|------|------|------------|-------------|----------|
| **Google Gemini** (채택) | 무료 티어 존재, Flash 모델 저렴 | 우수 | Spring AI 공식 스타터 | O |
| OpenAI GPT-4o | 유료, 상대적으로 고비용 | 우수 | Spring AI 공식 지원 | X |
| Anthropic Claude | 유료 | 우수 | Spring AI 지원 | X |
| 로컬 모델 (Ollama) | 무료 | 제한적 | Spring AI 지원 | X |

**트레이드오프**
- 장점: 무료 티어(`gemini-1.5-flash`)로 초기 개발·테스트 비용 절감, 한국어 성능 우수, Spring AI 공식 스타터로 설정 단순화
- 단점: Google Cloud 의존성 추가, 네트워크 레이턴시(외부 API 호출), 무료 티어 Rate Limit(분당 요청 제한) 존재

### 2. 벡터 DB: Redis Vector (Redis Stack)

**결정**: 기존 운영 중인 Redis를 Redis Stack으로 활용하여 벡터 검색(Vector Search)을 적용한다.

**대안 검토**

| 후보 | 특징 | 기존 인프라 활용 | 선택 여부 |
|------|------|----------------|----------|
| **Redis Vector** (채택) | Redis Stack의 RediSearch 모듈, 기존 Redis 재활용 | O | O |
| Pinecone | 관리형 벡터 DB, 높은 완성도 | X (신규 외부 서비스) | X |
| Weaviate | 오픈소스 벡터 DB | X (신규 컨테이너 필요) | X |
| pgvector | PostgreSQL 확장 | X (신규 DB 설정 필요) | X |

**트레이드오프**
- 장점: 기존 Redis 인프라 재활용으로 운영 비용·복잡도 최소화, Spring AI `RedisVectorStore` 공식 지원, 인메모리 속도
- 단점: Redis Stack 전환 필요(Redis 모듈 설치), 대규모 벡터 데이터셋에서 전용 벡터 DB 대비 성능 한계, Redis 메모리 사용량 증가

### 3. AI 프레임워크: Spring AI 1.0

**결정**: Spring AI 1.0 GA를 AI 통합 프레임워크로 채택한다.

**트레이드오프**
- 장점: Spring Boot 생태계와 자연스러운 통합(자동 구성, DI), LLM 교체 추상화(ChatClient API), Tool Calling·RAG·Structured Output 공식 지원, 팀의 Spring 숙련도 활용
- 단점: 비교적 신생 프레임워크(1.0 GA)로 커뮤니티 레퍼런스 제한적, 버전 변경 시 API 호환성 유의 필요

### 4. Circuit Breaker: Resilience4j

**결정**: LLM API 호출에 Resilience4j Circuit Breaker를 적용한다.

**트레이드오프**
- 장점: 프로젝트에 이미 의존성 존재, Spring Boot 자동 구성 지원, 슬라이딩 윈도우 방식으로 장애 감지 정교함
- 단점: Circuit Breaker 상태 전환 임계값 튜닝 필요, Half-Open 상태 관리에 주의

**설정 기준**
- 실패율 임계값: 50% (10회 호출 중 5회 실패 시 Open)
- 대기 시간: 30초 (Open → Half-Open)
- LLM 타임아웃: 5초 (Redis 캐시 폴백 전환 기준)

### 5. 메트릭: Micrometer (기존 Prometheus 연동)

**결정**: 기존 Micrometer + Prometheus 스택을 활용하여 AI 기능 메트릭을 수집한다.

**트레이드오프**
- 장점: 기존 모니터링 인프라 재활용, 추가 운영 비용 없음, Grafana 대시보드 확장으로 AI 비용 시각화 가능
- 단점: AI 토큰 비용 메트릭은 커스텀 Counter/Gauge 직접 구현 필요, Spring AI 메트릭 자동 수집 범위 확인 필요

---

## 구현할 AI 기능 목록

### 기본 기능 (필수 구현)

#### 1. 카드 시장 분석 AI (Structured Output)

Spring AI의 `BeanOutputConverter`를 활용한 구조화 출력으로 카드별 시장 분석 리포트를 생성한다.

- 입력: 카드 ID, 최근 거래 이력, 경매 데이터
- 출력: 가격 트렌드(`RISING/STABLE/FALLING`), 적정가 추정, 수요 수준, 요약 텍스트, 강점·리스크 키워드
- 캐싱: Redis에 분석 결과 캐시 (TTL 1시간), 타임아웃 시 이전 캐시 반환

#### 2. AI 카드 어시스턴트 (Tool Calling)

Spring AI Tool Calling을 활용하여 자연어 질의를 카드·경매 DB 조회로 연결하는 대화형 어시스턴트를 구현한다.

- Tools: `searchCards`, `getActiveAuctions`, `getCardPriceHistory`, `getUserBidHistory`
- 멀티턴 대화 지원 (`sessionId` 기반 대화 이력 관리)

#### 3. 장애 격리 + 비용 대시보드

- Circuit Breaker(Resilience4j) + 타임아웃 폴백으로 LLM 장애 시 서비스 중단 방지
- Micrometer Custom Metric으로 토큰 사용량·비용·응답시간을 Prometheus에 수집
- Grafana 대시보드 패널 추가 (AI 비용 모니터링)

### 도전 기능 (선택 구현)

#### 4. RAG 파이프라인 (Retrieval-Augmented Generation)

Redis Vector Store를 활용하여 카드 메타데이터·거래 패턴을 벡터화하고, LLM 프롬프트에 컨텍스트로 주입하는 RAG 파이프라인을 구축한다.

- Embedding: Spring AI EmbeddingModel (Gemini Embedding 또는 대체 모델)
- Vector Store: RedisVectorStore (Redis Stack RediSearch)
- 검색: 유사도 기반 Top-K 카드 정보 retrieval 후 프롬프트 augmentation

#### 5. SSE 스트리밍 + 멀티턴 대화 (도전)

- Spring AI `StreamingChatClient`를 활용한 SSE(Server-Sent Events) 스트리밍 응답
- `Flux<String>` → SSE 이벤트 변환 (Spring WebFlux 또는 SseEmitter 활용)
- 멀티턴 대화 이력 관리 (세션 기반 `MessageHistory`)

---

## 결과 (Consequences)

### 기대 효과 (긍정적 영향)

- **사용자 경험 개선**: 초보 거래자도 AI 분석 리포트로 적정 가격 판단이 가능해져 정보 비대칭 해소
- **플랫폼 차별화**: 카드 거래 플랫폼 중 AI 어시스턴트 탑재로 경쟁 우위 확보
- **기존 인프라 활용**: Redis(캐시·벡터), Resilience4j, Micrometer 재활용으로 추가 인프라 비용 최소화
- **장애 격리 보장**: Circuit Breaker + 캐시 폴백으로 LLM 외부 API 장애가 핵심 거래 기능에 전파되지 않음
- **비용 가시성**: Prometheus/Grafana 연동으로 AI API 토큰 비용을 실시간 모니터링·제어 가능

### 부정적 영향 / 주의사항

- **외부 API 의존성**: Gemini API 장애 시 AI 기능 전체 불가 — Circuit Breaker 및 캐시 폴백 필수
- **레이턴시 증가**: LLM 응답 시간(평균 1~3초)으로 인해 분석 API 응답 속도가 일반 API 대비 느림 — 비동기 처리 및 캐싱으로 완화
- **비용 관리 필요**: 토큰 사용량에 따라 API 비용 발생 — 무료 티어 한도 초과 시 과금, 모니터링 알림 설정 필수
- **Spring AI 성숙도**: 1.0 GA이나 장기 운영 레퍼런스 부족 — 버전 업그레이드 시 API 변경 가능성 유의
- **Redis Stack 전환**: 기존 Redis를 Redis Stack으로 교체 필요 (Docker Compose 변경) — 기존 캐시 데이터 호환성 확인 필요
- **RAG 벡터 인덱스 관리**: 카드 메타데이터 변경 시 벡터 인덱스 재구축 필요 — 인덱스 갱신 전략 별도 수립 필요

---

## 관련 문서

- `docs/guide/ai-api-guide.md` — AI 기능 API 엔드포인트 명세
- `docs/guide/ai-prompt-history.md` — 프롬프트 개선 이력 추적
- `docs/adr/ADR-003-caching-strategy.md` — Redis 캐시 전략 (AI 캐시 폴백 기반)
- `docs/adr/ADR-002-auction-popular-ranking.md` — Redis ZSet 인프라 참고
