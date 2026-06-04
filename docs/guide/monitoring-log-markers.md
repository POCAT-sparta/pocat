# Monitoring Log Markers Guide

모니터링 자동화 파이프라인(Loki → Grafana → n8n → Slack)이 감지하는 구조화 로그 마커 정의.

---

## 마커 목록

| 마커 | 레벨 | 감지 대상 | Loki LogQL |
|------|------|-----------|-----------|
| `[AUCTION_ANOMALY]` | WARN | 이상 낙찰가 감지 | <code>|= "AUCTION_ANOMALY"</code> |
| `[EMBEDDING_FAIL]` | ERROR | 벡터 임베딩 실패 | <code>|= "EMBEDDING_FAIL"</code> |

---

## [AUCTION_ANOMALY]

**목적**: 낙찰가가 시장 평균가 대비 threshold 배 초과 시 이상 거래 감지

**발생 조건**:
- `finalPrice > marketPrice * pocat.monitoring.auction-anomaly-threshold`
- 기본 threshold: `3.0` (application.yaml 외부화)

**발생 경로**:
1. `AuctionLifecycleService.closeExpiredAuction()` — 경매 만료 낙찰 (`type=EXPIRED_WIN`)
2. `AuctionBuyoutTransactionService.completeBuyout()` — 즉시 구매 (`type=BUYOUT`)

**로그 필드**:

| 필드 | 설명 |
|------|------|
| `type` | `EXPIRED_WIN` 또는 `BUYOUT` |
| `auctionId` | 경매 ID |
| `cardId` | 카드 ID |
| `sellerId` | 판매자 User ID |
| `winnerId` / `buyerId` | 낙찰자/구매자 User ID |
| `finalPrice` | 최종 결제 금액 |
| `marketPrice` | 시장 평균가 (카드 평균 거래가; 거래 이력 없으면 시작가 폴백) |
| `ratio` | `finalPrice / marketPrice` (소수점 2자리) |

**예외 처리**:
- `marketPrice <= 0 또는 null (시장가·시작가 모두 없는 경우)` → 로그 미출력, 비즈니스 흐름 무영향

**n8n 연동 포인트**:
- Grafana Alert: `{app="pocat"} |= "AUCTION_ANOMALY"` 발생 → n8n Webhook
- n8n에서 `cardId`, `finalPrice`, `marketPrice`, `ratio` 파싱 후 Gemini 분석 → Slack `#admin-alert`

---

## [EMBEDDING_FAIL]

**목적**: 카드/거래글 벡터 임베딩 실패 감지 및 자동 재시도 트리거

**발생 조건**:
- `EmbeddingService.embedCard()` 또는 `embedTradePost()` 내 `vectorStore.add()` 예외 발생

**로그 필드**:

| 필드 | 설명 |
|------|------|
| `eventType` | `CARD` 또는 `TRADE_POST` |
| `targetId` | 카드 ID 또는 거래글 ID |
| `exceptionType` | 예외 클래스명 (`RuntimeException`, `TimeoutException` 등) |
| `reason` | `e.getMessage()` |

**비즈니스 영향**:
- 임베딩 실패는 비즈니스 로직에 영향 없음 (비동기 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`)
- 예외 상위 전파 없음

**n8n 연동 포인트**:
- Grafana Alert: `{app="pocat"} |= "EMBEDDING_FAIL"` N회/분↑ → n8n Webhook
- n8n 분기:
  - 일시적 오류(`TimeoutException`, `ConnectionException`) → `AdminAiController` reindex API 자동 호출
  - 반복 실패(3회↑) → Gemini 원인 분류 → Slack `#backend`

---

## threshold 조정

```yaml
# application.yaml 또는 환경변수
pocat:
  monitoring:
    auction-anomaly-threshold: 3.0  # 기본값, 운영 환경에서 조정 가능
```

운영 중 threshold 변경 시 재배포 필요 (현재 동적 변경 미지원).
