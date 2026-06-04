# Test Report — Issue #178

| 항목 | 내용 |
|------|------|
| **Date** | 2026-06-04 |
| **Issue** | #178 — 모니터링 자동화를 위한 구조화 로그 추가 |
| **Branch** | `feat/monitoring-logs/#178` |
| **작성자** | DOCS Agent (Phase 4.5) |

---

## 1. 신규·변경 테스트 파일

| 파일 | 변경 유형 | 설명 |
|------|-----------|------|
| `AuctionLifecycleServiceTest` | 수정 | AUCTION_ANOMALY 로그 출력 조건 3케이스 추가 |
| `AuctionBuyoutServiceTest` | 수정 | BUYOUT 경로 AUCTION_ANOMALY 로그 출력 1케이스 추가 |
| `EmbeddingServiceTest` | 신규 | EMBEDDING_FAIL 로그 마커·예외 비전파 검증 3케이스 |

---

## 2. GREEN 결과

| 테스트 클래스 | 테스트 수 | 상태 |
|--------------|-----------|------|
| `AuctionLifecycleServiceTest` | 5 | PASS |
| `AuctionBuyoutServiceTest` | 4 | PASS |
| `EmbeddingServiceTest` | 3 | PASS |

총 12개 테스트 전원 GREEN.

---

## 3. 로그 마커 스펙

### AUCTION_ANOMALY

| 필드 | 값 |
|------|----|
| 레벨 | `WARN` |
| 마커 | `[AUCTION_ANOMALY]` |
| 조건 | `finalPrice > startingPrice * threshold` (기본 threshold=3.0) |
| 삽입 위치 | `AuctionLifecycleService.closeExpiredAuction()` (EXPIRED_WIN), `AuctionBuyoutTransactionService.completeBuyout()` (BUYOUT) |

로그 예시:

```log
WARN [AUCTION_ANOMALY] type=EXPIRED_WIN auctionId=42 cardId=7 sellerId=3 winnerId=10 finalPrice=30000 startingPrice=5000 ratio=6.00
WARN [AUCTION_ANOMALY] type=BUYOUT auctionId=42 cardId=7 sellerId=3 buyerId=10 finalPrice=30000 startingPrice=5000 ratio=6.00
```

Loki LogQL: `{app="pocat"} |= "AUCTION_ANOMALY"`

---

### EMBEDDING_FAIL

| 필드 | 값 |
|------|----|
| 레벨 | `ERROR` |
| 마커 | `[EMBEDDING_FAIL]` |
| 조건 | `vectorStore.add()` 예외 발생 시 |
| 삽입 위치 | `EmbeddingService.embedCard()`, `EmbeddingService.embedTradePost()` catch 블록 |

로그 예시:

```log
ERROR [EMBEDDING_FAIL] eventType=CARD targetId=15 exceptionType=RuntimeException reason=connection refused
ERROR [EMBEDDING_FAIL] eventType=TRADE_POST targetId=88 exceptionType=TimeoutException reason=read timed out
```

Loki LogQL: `{app="pocat"} |= "EMBEDDING_FAIL"`

---

## 4. 설정

```yaml
# application.yaml
pocat:
  monitoring:
    auction-anomaly-threshold: 3.0  # 시작가 대비 N배 초과 시 이상 감지
```

---

## 5. 미테스트 항목

| 항목 | 이유 |
|------|------|
| `AuctionAnomalyProperties` Spring 바인딩 | `@ConfigurationProperties` 동작은 통합 테스트 환경에서 확인 |
| Loki 수집·n8n 트리거 | 외부 인프라 의존 — 모니터링 담당자 설정 범위 |

---

## 6. Regression 확인

- **#178 범위 내 신규 실패: 0건**
- 비즈니스 로직 변경 없음 — 기존 경매·임베딩 흐름 영향 없음

---

## 7. 실행 명령

```bash
./gradlew cleanTest test \
  --tests "*.AuctionLifecycleServiceTest" \
  --tests "*.AuctionBuyoutServiceTest" \
  --tests "*.EmbeddingServiceTest"
```

---

## 8. 관련 문서

- Monitoring Guide: `docs/guide/monitoring-log-markers.md`
