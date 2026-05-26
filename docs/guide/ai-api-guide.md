# AI 기능 API 가이드

| 항목 | 내용 |
|------|------|
| **버전** | v1.0 |
| **작성일** | 2026-05-26 |
| **상태** | 초안 (구현 전) |
| **관련 ADR** | ADR-004-ai-features.md |

---

## 공통 사항

- **Base URL**: `/api/ai`
- **인증**: 모든 엔드포인트에 Bearer 토큰 필요 (`Authorization: Bearer {token}`)
- **Content-Type**: `application/json` (SSE 엔드포인트 제외)
- **에러 응답 공통 형식**:

```json
{
  "status": 400,
  "code": "AI_ERROR_CODE",
  "message": "에러 메시지"
}
```

---

## 1. 카드 시장 분석 API

### 1-1. 분석 결과 조회 (캐시 우선)

```
GET /api/ai/cards/{cardId}/analysis
```

캐시에 분석 결과가 있으면 즉시 반환하고, 없으면 LLM을 호출하여 신규 분석 후 반환한다. TTL은 1시간이다.

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `cardId` | Long | 분석할 카드 ID |

**Request Headers**

```
Authorization: Bearer {token}
```

**Response (200 OK)**

```json
{
  "priceTrend": "RISING",
  "fairValueEstimate": 50000,
  "demandLevel": "HIGH",
  "summary": "최근 3개월간 거래량이 꾸준히 증가하였으며, 경매 낙찰가가 상승 추세입니다.",
  "highlights": ["SSR 등급 희귀도", "최근 대회 입상 선수 카드"],
  "riskFactors": ["신규 시즌 카드 출시 예정으로 수요 분산 가능"],
  "keywords": ["SSR", "인기", "상승세"],
  "analysisModel": "gemini-1.5-flash",
  "promptTokens": 512,
  "completionTokens": 256,
  "analyzedAt": "2026-05-26T10:00:00"
}
```

**Response Fields**

| 필드 | 타입 | 설명 |
|------|------|------|
| `priceTrend` | String (enum) | 가격 트렌드: `RISING` / `STABLE` / `FALLING` |
| `fairValueEstimate` | Integer | AI 추정 적정가 (원) |
| `demandLevel` | String (enum) | 수요 수준: `HIGH` / `MEDIUM` / `LOW` |
| `summary` | String | 분석 요약 텍스트 |
| `highlights` | String[] | 강점 목록 |
| `riskFactors` | String[] | 리스크 요인 목록 |
| `keywords` | String[] | 주요 키워드 |
| `analysisModel` | String | 사용된 LLM 모델명 |
| `promptTokens` | Integer | 프롬프트 토큰 수 |
| `completionTokens` | Integer | 완성 토큰 수 |
| `analyzedAt` | ISO 8601 | 분석 시각 |

---

### 1-2. 분석 강제 재생성

```
POST /api/ai/cards/{cardId}/analysis
```

기존 캐시를 무효화하고 LLM을 호출하여 분석 결과를 새로 생성한다.

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `cardId` | Long | 분석할 카드 ID |

**Request Headers**

```
Authorization: Bearer {token}
```

**Response**: `GET /api/ai/cards/{cardId}/analysis` 와 동일한 형식

---

### 장애 Fallback 동작

| 상황 | 동작 |
|------|------|
| LLM 응답 타임아웃 (5초 초과) | Redis 캐시에서 이전 분석 결과 반환 (캐시 없을 경우 503 반환) |
| Circuit Breaker OPEN 상태 | `{"message": "AI 분석 서비스 일시 중단. 잠시 후 재시도해주세요."}` (503) |
| Gemini API 일시 오류 | Resilience4j Retry 2회 후 Circuit Breaker 판정 위임 |

---

## 2. AI 카드 어시스턴트 API

### 2-1. 대화 요청

```
POST /api/ai/assistant/chat
```

자연어 메시지를 입력하면 AI가 Tool Calling을 통해 카드·경매 데이터를 조회한 후 자연어로 응답한다. `sessionId`를 통해 멀티턴 대화 이력을 유지한다.

**Request Headers**

```
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**

```json
{
  "message": "5만원 이하 SSR 카드 경매 중인 거 보여줘",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `message` | String | O | 사용자 자연어 질의 (최대 500자) |
| `sessionId` | String (UUID) | X | 대화 세션 ID. 미전송 시 신규 세션 생성 |

**Response (200 OK)**

```json
{
  "reply": "현재 5만원 이하로 경매 중인 SSR 카드는 3건입니다. [카드명 A] - 현재가 35,000원, [카드명 B] - 현재가 42,000원, ...",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "toolsUsed": ["searchCards", "getActiveAuctions"]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `reply` | String | AI 자연어 응답 |
| `sessionId` | String (UUID) | 대화 세션 ID (신규 생성 시 반환) |
| `toolsUsed` | String[] | 이번 응답에서 호출된 Tool 목록 |

---

## 3. SSE 스트리밍 API (도전 기능)

### 3-1. 스트리밍 대화 요청

```
GET /api/ai/assistant/stream?message={message}&sessionId={sessionId}
```

응답을 SSE(Server-Sent Events)로 청크 단위로 스트리밍한다.

**Request Headers**

```
Authorization: Bearer {token}
Accept: text/event-stream
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `message` | String | O | 사용자 자연어 질의 (URL 인코딩 필요) |
| `sessionId` | String (UUID) | X | 대화 세션 ID |

**Response**

```
Content-Type: text/event-stream
Cache-Control: no-cache
```

**SSE Event 형식**

```
data: {"type":"chunk","content":"현재 "}

data: {"type":"chunk","content":"5만원 이하 "}

data: {"type":"chunk","content":"SSR 카드는 "}

data: {"type":"done","sessionId":"550e8400-e29b-41d4-a716-446655440000","totalTokens":150}
```

| Event 타입 | 필드 | 설명 |
|-----------|------|------|
| `chunk` | `content` | 토큰 단위 응답 텍스트 조각 |
| `done` | `sessionId`, `totalTokens` | 스트리밍 종료, 총 토큰 수 |
| `error` | `message` | 스트리밍 중 오류 발생 시 |

---

## Tool 목록 (AI 어시스턴트 내부 호출)

AI 어시스턴트가 사용자 질의를 처리하기 위해 내부적으로 호출하는 Tool 함수 목록이다. 클라이언트에서 직접 호출하지 않는다.

| Tool 이름 | 설명 | 주요 파라미터 |
|-----------|------|--------------|
| `searchCards` | 조건에 맞는 카드 검색 | `grade` (등급), `maxPrice` (최대가격), `name` (카드명), `page` (페이지) |
| `getActiveAuctions` | 특정 카드의 진행 중 경매 조회 | `cardId` |
| `getCardPriceHistory` | 카드 가격 이력 조회 | `cardId`, `days` (조회 기간, 일) |
| `getUserBidHistory` | 사용자 입찰 이력 조회 | `userId` |

---

## 에러 코드 목록

| HTTP 상태 | 코드 | 설명 |
|-----------|------|------|
| 400 | `AI_INVALID_REQUEST` | 요청 파라미터 오류 |
| 401 | `UNAUTHORIZED` | 인증 토큰 없음 또는 만료 |
| 404 | `CARD_NOT_FOUND` | 존재하지 않는 카드 ID |
| 503 | `AI_SERVICE_UNAVAILABLE` | Circuit Breaker OPEN 또는 LLM 서비스 불가 |
| 503 | `AI_CACHE_MISS_AND_TIMEOUT` | 캐시 없고 LLM 타임아웃 (5초 초과) |

---

## 메트릭 (Prometheus)

AI 기능은 Micrometer를 통해 아래 메트릭을 Prometheus에 노출한다.

| 메트릭 이름 | 타입 | 설명 |
|-----------|------|------|
| `ai.analysis.request.total` | Counter | 시장 분석 요청 총 수 |
| `ai.analysis.cache.hit` | Counter | 캐시 히트 수 |
| `ai.analysis.tokens.prompt` | Counter | 프롬프트 토큰 누적 |
| `ai.analysis.tokens.completion` | Counter | 완성 토큰 누적 |
| `ai.analysis.latency` | Timer | LLM 응답 시간 분포 |
| `ai.circuit.breaker.state` | Gauge | Circuit Breaker 상태 (0=CLOSED, 1=OPEN, 0.5=HALF_OPEN) |
| `ai.assistant.tool.calls` | Counter | Tool 호출 수 (tag: toolName) |
