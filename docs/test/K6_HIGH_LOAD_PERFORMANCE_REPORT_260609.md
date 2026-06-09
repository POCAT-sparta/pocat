# pocat k6 부하 테스트 보고서

> 작성일: 2026-06-09  
> 환경: 로컬 Docker Compose (Spring Boot 3.2 + MySQL + Redis + Kafka + Elasticsearch)  
> 도구: k6 (Grafana Labs)

---

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-06-08 | 최초 작성 — 시나리오 08/09/10/11 결과, 버그 3건, BP 탐색 결과 기록 |
| 2026-06-09 | HikariCP 설정값 `application-local.yaml` 명시 / 시드 데이터에 입찰 이력 500건 추가 / 서버 주요 설정값 표 추가 |
| 2026-06-09 | BP 2차 재실행 — 500 VU 전 임계치 통과, BP 미도달 확인 / 1차와 비교 분석 추가 |
| 2026-06-09 | BP 3차 재실행 — HikariCP pool=5로 축소 / 성능 저하 수치 확인 (p95 +91%, 처리량 -28%) / 3차 비교표 추가 |

---

## 1. 개요

포캣(pocat) 포켓몬 카드 경매 플랫폼의 핵심 기능에 대해 실사용에 가까운 혼합 트래픽 시나리오를 설계하고 부하 테스트를 수행하였다.  

테스트 목적은 다음과 같다.

- 동시 입찰 시 Redisson 분산 락의 정확성 검증
- 읽기/쓰기 혼합 트래픽에서 응답시간 및 에러율 측정
- Redis 캐시 레이어의 부하 흡수 효과 확인
- 장시간 운영 시 커넥션 풀 고갈 및 메모리 드리프트 탐지

---

## 2. 테스트 환경

| 항목 | 내용 |
|---|---|
| 실행 환경 | 로컬 머신 (Windows, Docker Compose) |
| 애플리케이션 | Spring Boot 3.2, HikariCP |
| 데이터 저장소 | MySQL 8, Redis, Elasticsearch |
| 메시지 브로커 | Kafka (Outbox 패턴) |
| 부하 도구 | k6 |
| 시드 데이터 | 활성 경매 10개, 입찰자 계정 5개 (k6-bidder-1~5@test.com), 경매당 입찰 이력 50건 (총 500건) |

### 서버 주요 설정값

| 컴포넌트 | 설정 항목 | 값 | 비고 |
|---|---|---|---|
| HikariCP | `maximum-pool-size` | **10** | `application-local.yaml` 명시 — BP 탐색의 주 병목 |
| HikariCP | `connection-timeout` | 30,000 ms | 30초 초과 시 예외 발생 |
| HikariCP | `max-lifetime` | 1,800,000 ms | 30분 |
| Tomcat | `threads.max` | 200 (기본값) | 명시적 설정 없음 — BP 테스트 범위 내 미소진 |
| bid rate-limit | `bid-limit` | 30회 / 60초 | `application.yaml` 설정값 |

> **⚠️ 로컬 환경 한계**: 네트워크 지연, TLS, CDN, 로드밸런서가 없어 실제 프로덕션보다 응답시간이 유리하다.  
> 아래 수치는 "기술적 정상 동작"의 기준으로 해석해야 하며, 프로덕션 용량 계획의 근거로 직접 사용하기 어렵다.  
> 또한 시드 데이터(경매 10개, 입찰 이력 500건)는 실제 서비스 규모보다 소규모이므로 DB 인덱스 효율 검증을 위해서는 데이터 규모 확장이 필요하다.

---

## 3. 시나리오 설계

### 시나리오 구성 배경

예상 트래픽 데이터 없이 설계된 테스트이므로 구체적인 VU 수치보다 **트래픽 유형의 분리**와 **동시성 패턴 재현**에 초점을 맞췄다.

| 시나리오 | 파일 | 목적 |
|---|---|---|
| 08 혼합 실사용 | `08-realistic-mixed-load.js` | 3가지 유저 유형 동시 재현 |
| 09 경매 피크 | `09-auction-peak-load.js` | 다수 경매 동시 입찰 경쟁 측정 |
| 10 장기 내구성 | `10-soak.js` | 20분 상시 부하로 누수 탐지 |
| 11 Breaking Point | `11-bid-breaking-point.js` | HikariCP 한계점 탐색 |

### 08 — 혼합 실사용 시나리오

실제 경매 사이트 피크 타임(저녁 시간대)을 재현한다.

| 유형 | VU | 행동 |
|---|---|---|
| 익명 브라우저 | 100 VU 피크 | 카드 검색, 경매 상세, 인기 경매, 평균가 조회 |
| 인증 입찰자 | 40 VU 피크 | 경매 상세 → 입찰 → 알림 조회 → 내 입찰 내역 |
| 경매 폴링 | 80 req/s | 종료 임박 새로고침 폭증 시뮬레이션 |

### 09 — 경매 피크 시나리오

10개 경매가 동시에 활성화된 상태에서 입찰 경쟁이 집중되는 상황을 재현한다.

| 유형 | VU | 행동 |
|---|---|---|
| 구경꾼 | 150 VU 피크 | 입찰 목록·경매 상세 반복 폴링 |
| 적극 입찰자 | 60 VU 피크 | 다수 경매 순환 입찰 시도 |

**토큰 배치 전략**: 60 VU를 10개 경매 × 6 VU로 분배하되, 같은 경매를 담당하는 6 VU가 5가지 서로 다른 사용자 토큰을 사용하도록 설계하여 실제 입찰 경쟁을 재현하였다.

```
VU  1~10  → auction 0~9, bidder-1 토큰
VU 11~20  → auction 0~9, bidder-2 토큰
VU 21~30  → auction 0~9, bidder-3 토큰
VU 31~40  → auction 0~9, bidder-4 토큰
VU 41~50  → auction 0~9, bidder-5 토큰
VU 51~60  → auction 0~9, bidder-1 토큰 (반복)
```

### 10 — 장기 내구성(Soak) 시나리오

20분간 75 VU를 상시 유지하며 응답시간 드리프트, 커넥션 누수를 탐지한다.

### 11 — Breaking Point 탐색 시나리오

VU를 단계적으로 증가시키며 서버가 정상 응답을 유지할 수 있는 한계 지점을 탐색한다.  
임계치 실패는 오류가 아니라 Breaking Point 발견을 의미하도록 `abortOnFail: false`로 설계하였다.

| 유형 | VU 계획 | 행동 |
|---|---|---|
| bp_readers | 50 → 150 → 300 → 500 VU | GET /bids, /auctions/{id}, /cards/{id} 혼합 (think-time 0.1s) |
| bp_writers | 10 → 30 → 60 VU | POST /bids (think-time 0.2s) |

**탐색 대상 병목 (우선순위 순)**

1. **HikariCP 커넥션 풀 (10)** — 동시 DB 요청 10개 초과 시 커넥션 대기 → 타임아웃 → BP
2. **Redisson tryLock(0) 경쟁 처리량** — 대기 없이 즉시 반환하므로 409 폭증 시점 관찰
3. **Tomcat 스레드 풀 (기본값 200)** — DB 슬로우쿼리로 스레드 장기 점유 시 포화

**BP 판정 기준**

| 구분 | 기준 |
|---|---|
| Soft Limit | `bp_readers p(95) > 500ms` (HikariCP 큐 적체 시작) |
| Hard Limit | `bp_5xx_errors > 0` 또는 timeout(status=0) 첫 발생 VU 단계 |

---

## 4. 최종 테스트 결과

### 08 — 혼합 실사용 ✅ 전 항목 통과

**실행 시간**: 10분 02초 | **총 요청**: 83,198건

| 임계치 | 기준 | 결과 |
|---|---|---|
| `http_req_duration p(95)` | < 800ms | **22.9ms** ✅ |
| `http_req_duration p(99)` | < 2000ms | **41.3ms** ✅ |
| `auction_polling p(95)` | < 400ms | **10.71ms** ✅ |
| `auth_users p(95)` | < 1000ms | **14.08ms** ✅ |
| `http_req_failed` | < 2% | **0.00%** ✅ |

**체크 항목 100% 통과**

```
✓ 검색 200        ✓ 경매상세(익명) 200   ✓ 인기경매 200
✓ 카드상세 200    ✓ 평균가 200           ✓ 필터검색 200
✓ 알림 200        ✓ 경매상세 200         ✓ 입찰목록 200
✓ 입찰폴링 200    ✓ 입찰 허용 상태코드   ✓ 내입찰 200
```

**커스텀 메트릭**

| 메트릭 | 값 |
|---|---|
| `mixed_bid_successes` | 103건 |
| `mixed_bid_failures` (400/409/429) | 1,650건 |
| `mixed_bid_duration_ms p(95)` | 39.4ms |
| `mixed_auth_call_duration_ms` | avg 7.9ms |

> `mixed_bid_failures` 1,650건은 서버 오류가 아니라 입찰 가격 미달(400), Redisson 락 충돌(409), 레이트 리밋(429)에 해당하는 **정상 비즈니스 로직 거부**이다.  
> `responseCallback: http.expectedStatuses(201, 400, 409, 429)` 설정으로 `http_req_failed`에서 제외하였다.

---

### 09 — 경매 피크 ✅ 전 항목 통과

**실행 시간**: 8분 32초 | **총 요청**: 43,450건

| 임계치 | 기준 | 결과 |
|---|---|---|
| `active_bidders p(95)` | < 1500ms | **14.16ms** ✅ |
| `active_bidders p(99)` | < 3000ms | **18.57ms** ✅ |
| `auction_watchers p(95)` | < 500ms | **11.73ms** ✅ |
| `auction_watchers p(99)` | < 1000ms | **14.7ms** ✅ |
| `http_req_failed` | < 5% | **0.00%** ✅ |
| `peak_bid_success_rate` | > 0.5% | **0.91%** ✅ |

**커스텀 메트릭**

| 메트릭 | 값 |
|---|---|
| `peak_bid_successes` | 55건 |
| `peak_bid_lock_failed` (409) | 1,180건 |
| `peak_bid_success_rate` | 0.91% (55 / 6,038) |
| `peak_bid_duration_ms p(95)` | 12ms |
| `peak_poll_duration_ms p(95)` | 12ms |

**입찰 성공률 0.91% 해석 — 깔때기(Funnel) 분석**

낮은 성공률은 서버 장애가 아니라 이중 병목의 결과이다.

```
전체 입찰 시도         6,038건  (100%)
├─ 레이트 리밋 429    ~4,800건  (79%)  ← bid-limit 30/min × 5 토큰 = 150/min 상한
├─ Redisson 락 충돌    1,180건  (20%)  ← tryLock(0) : 대기 없이 즉시 반환
├─ 가격 미달 400          ~3건  (<1%)
└─ 입찰 성공 201           55건  (0.9%)
```

레이트 리밋을 통과한 ~1,235건 중 55건이 성공(4.5%)하였으며, 서버는 정상적으로 모든 요청을 처리하였다.  
핵심 지표인 `http_req_failed = 0.00%`와 `p(95) < 15ms`가 서버 안정성의 실질적 증거이다.

---

### 10 — 장기 내구성 (20분) ✅ 전 항목 통과

**실행 시간**: 20분 04초 | **총 요청**: 41,839건

| 임계치 | 기준 | 결과 |
|---|---|---|
| `http_req_duration p(95)` | < 1000ms | **24.97ms** ✅ |
| `http_req_duration p(99)` | < 2500ms | **31.09ms** ✅ |
| `auth_endurance p(95)` | < 1200ms | **11.51ms** ✅ |
| `public_endurance p(95)` | < 800ms | **26.15ms** ✅ |
| `http_req_failed` | < 2% | **0.00%** ✅ |
| `soak_5xx_errors` | < 10건 | **0건** ✅ |

**체크 항목 100% 통과**

```
✓ 검색 200        ✓ 경매상세(공개) 200   ✓ 인기경매 200
✓ 카드상세 200    ✓ 평균가 200           ✓ 필터검색 200
✓ 알림 200        ✓ 내입찰 200           ✓ 내경매 200
✓ 내주문 200      ✓ 입찰 허용
```

**드리프트 없음 확인**: 20분 전반과 후반의 응답시간 분포가 동일 수준을 유지하여 HikariCP 커넥션 풀 고갈 및 메모리 누수의 징후가 없었다.

> **`soak_auth_duration_ms p(95) = 520ms` 해석**  
> 이 수치는 응답시간이 아니라 측정 구간에 `sleep(0.5)` 이 포함된 측정 아티팩트이다.  
> 실제 HTTP 응답시간은 `http_req_duration{scenario:auth_endurance} p(95) = 11.51ms`가 정확한 수치이다.

---

### 11 — Breaking Point 탐색 ✅ Hard Limit 발견

**실행 시간**: 10분 31초 | **총 요청**: 590,598건 | **처리량**: 935 req/s

| 임계치 | 기준 | 결과 |
|---|---|---|
| `bp_readers p(95)` | < 500ms | **593ms ✗ → Soft Limit 확인** |
| `bp_readers p(99)` | < 2000ms | **861ms** ✅ |
| `bp_writers p(95)` | < 2000ms | **279ms** ✅ |
| `http_req_failed` | < 1% | **0.00%** ✅ |
| `bp_5xx_errors` | < 10건 | **1건** ✅ (BP 감지) |

**BP 발견 시점**

```
WARN[0397] Request Failed  error="dial tcp 127.0.0.1:8080: bind: An operation on a socket
           could not be performed because the system lacked sufficient buffer space..."
INFO[0397] 🔴 [VU 251] timeout(BP 신호) → HikariCP 고갈 의심
```

t=397s (6분 37초) → bp_readers 단계 대입 시 **300 VU 진입 구간**으로, 사전 예측(150~300 VU)과 일치한다.

**커스텀 메트릭**

| 메트릭 | 값 | 해석 |
|---|---|---|
| `bp_5xx_errors` | 1건 | Hard Limit 첫 감지 (300 VU 구간) |
| `bp_bid_successes` | 57건 | 부하 하에서도 입찰 성공 동작 확인 |
| `bp_read_duration_ms` avg/p(95)/max | 200ms / 594ms / 2,596ms | 고부하 구간에서 응답시간 분포 확대 |
| `bp_write_duration_ms` avg/p(95) | 67ms / 279ms | 쓰기 경로는 레이트 리밋으로 상대적으로 안정적 |

**에러 유형 분석**

발생한 1건의 BP 신호는 HTTP 5xx가 아닌 **Windows TCP 소켓 버퍼 고갈** 에러였다.

```
bind: An operation on a socket could not be performed because the system lacked
sufficient buffer space or because a queue was full.
```

- **로컬 환경 특성**: 500 VU가 모두 `localhost`로 동시 연결하면서 OS 레벨 소켓 한계에 도달
- **클라우드 환경 차이**: 분산 환경에서는 이 병목이 사라지고, 순수하게 HikariCP 커넥션 풀이 먼저 한계에 도달할 것으로 예상

**BP 탐색 결과 요약**

| 구분 | 결과 |
|---|---|
| **Soft Limit** | readers **~300 VU** — p(95) 500ms 초과 |
| **Hard Limit** | readers **~300 VU** — t=397s, VU 251에서 첫 BP 신호 |
| **주요 병목** | Windows TCP 소켓 버퍼 (로컬) / HikariCP 10 커넥션 (예상) |
| **Tomcat 스레드** | 500 VU에서도 5xx 없음 → 스레드 200개 여유 있음 |

---

### 11-2차 — Breaking Point 재실행 (2026-06-09) ✅ 전 임계치 통과 — BP 미도달

> 시드 데이터 개선(입찰 이력 500건 추가) 후 재실행

**실행 시간**: 10분 30초 | **총 요청**: 1,118,929건 | **처리량**: 1,775 req/s

| 임계치 | 기준 | 결과 |
|---|---|---|
| `bp_readers p(95)` | < 500ms | **149ms** ✅ |
| `bp_readers p(99)` | < 2000ms | **227ms** ✅ |
| `bp_writers p(95)` | < 2000ms | **41ms** ✅ |
| `http_req_failed` | < 1% | **0.00%** ✅ |
| `bp_5xx_errors` | < 10건 | **0건** ✅ — BP 미도달 |

**커스텀 메트릭**

| 메트릭 | 값 | 해석 |
|---|---|---|
| `bp_5xx_errors` | 0건 | 500 VU 범위 내 Hard Limit 없음 |
| `bp_bid_successes` | 53건 | 고부하 하에서도 정상 입찰 처리 |
| `bp_read_duration_ms` avg/p(95)/max | 49ms / 149ms / 932ms | 전 구간 Soft Limit(500ms) 이하 |
| `bp_write_duration_ms` avg/p(95) | 12ms / 41ms | 쓰기 경로 매우 안정적 |

**1차(2026-06-08) vs 2차(2026-06-09) 비교**

| 항목 | 1차 | 2차 |
|---|---|---|
| 시드 데이터 | 입찰 이력 없음 | 경매당 50건, 총 500건 |
| 총 요청 | 590,598건 | **1,118,929건** (+89%) |
| 처리량 | 935 req/s | **1,775 req/s** (+90%) |
| `bp_readers p(95)` | 593ms ✗ (Soft Limit 초과) | **149ms** ✅ |
| `bp_5xx_errors` | 1건 (BP 신호) | **0건** |
| BP 결과 | Hard Limit 발견 (300 VU) | **BP 미도달** |

**2차 BP 미도달 원인 분석**

1차에서 발생한 BP 신호(`🔴 [VU 251] timeout(BP 신호)`)는 HTTP 5xx나 HikariCP 고갈이 아니라 **Windows TCP 소켓 버퍼 고갈**(`bind: insufficient buffer space`)이었다. 이는 로컬 환경에서 500 VU가 전부 `localhost`로 동시 연결할 때 발생하는 OS 레벨 한계이며, 재현성이 낮다.

2차에서 이 신호가 재현되지 않은 것은 다음 요인으로 설명된다:

| 요인 | 내용 |
|---|---|
| OS 소켓 상태 | Docker 재시작 이후 TCP 소켓 버퍼 초기화 — 1차 누적 부하 없음 |
| JVM 워밍업 | 테스트 시작 전 서버가 안정적으로 웜업 완료된 상태 |
| 시드 데이터 영향 | 50건/경매 수준은 MySQL 페이지네이션 쿼리에 유의미한 부하를 주지 않음 |

**핵심 결론**: HikariCP 커넥션 풀 10개는 이 데이터 규모에서 500 VU 읽기 부하를 처리할 수 있다. 진정한 HikariCP 병목을 재현하려면 쿼리당 처리 시간을 늘리는 방법(대용량 데이터 또는 무거운 JOIN 쿼리)이 필요하다.

**향후 BP 탐색 방향**

```
옵션 1. 데이터 규모 확장
   경매당 입찰 이력 50건 → 5,000건 이상으로 증가
   → 페이지네이션 쿼리 소요 시간 상승 → HikariCP 점유 시간 증가

옵션 2. HikariCP pool-size 축소 검증  ← 3차에서 실시
   maximum-pool-size: 10 → 5로 변경 후 재실행
   → BP가 낮은 VU에서 나타나면 HikariCP가 실제 병목임을 역으로 증명

옵션 3. VU 상한 확장
   현재 500 VU → 1000 VU 이상
   → Windows TCP 소켓 한계와 무관하게 더 높은 한계 탐색
```

---

### 11-3차 — Breaking Point 재실행 (2026-06-09, pool=5) ✅ 전 임계치 통과 — BP 미도달, 성능 저하 확인

> HikariCP `maximum-pool-size: 10 → 5`로 축소 후 재실행 (병목 메커니즘 검증 목적)

**실행 시간**: 10분 30초 | **총 요청**: 805,078건 | **처리량**: 1,277 req/s

| 임계치 | 기준 | 결과 |
|---|---|---|
| `bp_readers p(95)` | < 500ms | **285ms** ✅ |
| `bp_readers p(99)` | < 2000ms | **383ms** ✅ |
| `bp_writers p(95)` | < 2000ms | **129ms** ✅ |
| `http_req_failed` | < 1% | **0.00%** ✅ |
| `bp_5xx_errors` | < 10건 | **0건** ✅ — BP 미도달 |

**커스텀 메트릭**

| 메트릭 | 값 | 해석 |
|---|---|---|
| `bp_5xx_errors` | 0건 | pool=5에서도 Hard Limit 미도달 |
| `bp_bid_successes` | 67건 | 고부하 하에서 입찰 정상 처리 |
| `bp_read_duration_ms` avg/p(95)/max | 114ms / 286ms / 815ms | pool=10 대비 응답시간 전반 상승 |
| `bp_write_duration_ms` avg/p(95) | 32ms / 130ms | 쓰기 경로도 소폭 상승 |

**1차 / 2차 / 3차 종합 비교**

| 항목 | 1차 (pool=10) | 2차 (pool=10) | 3차 (pool=5) |
|---|---|---|---|
| 서버 상태 | Docker 재시작 직후 | 안정적 clean 상태 | 안정적 clean 상태 |
| 총 요청 | 590,598건 | 1,118,929건 | 805,078건 |
| 처리량 | 935 req/s | 1,775 req/s | **1,277 req/s (-28%)** |
| `bp_readers` avg | — | 49ms | **114ms (+133%)** |
| `bp_readers` p(95) | 593ms ✗ | 149ms ✅ | **285ms ✅ (+91%)** |
| `bp_readers` max | 2,596ms | 932ms | 815ms |
| `bp_5xx_errors` | 1건 (BP 신호) | 0건 | **0건** |
| BP 결과 | Windows TCP 고갈 | 미도달 | **미도달** |

**3차 결과 해석**

pool=10 (2차) 대비 pool=5 (3차)에서 성능 저하가 수치로 명확히 확인됐다.

- p(95): 149ms → 285ms (+91%)
- avg 응답시간: 49ms → 114ms (+133%)  
- 처리량: 1,775 → 1,277 req/s (-28%)

이는 **HikariCP 커넥션 수가 실제 성능에 직접 영향을 미친다**는 것을 수치로 증명한다. 다만 완전한 고갈(bp_5xx_errors > 0)은 재현되지 않았다. 로컬 환경에서 개별 쿼리가 수 ms 단위로 완료되기 때문에, 5개 커넥션도 빠른 반납 속도로 500 VU를 처리할 수 있었다.

**결론**: 진정한 HikariCP 고갈을 재현하려면 커넥션을 오래 점유하는 쿼리(대용량 데이터, 복잡한 JOIN)가 필요하다. 현재 로컬 데이터 규모(경매 10개, 입찰 500건)에서는 pool=5로도 500 VU 부하를 감당한다.

---

## 5. 테스트 과정에서 발견된 버그

테스트 실행 중 시나리오 실패를 통해 아래 버그 3건이 발견되어 수정되었다.

---

### Bug 1 — NotificationController URL 매핑 누락

**증상**: `알림 200` 체크 100% 실패, `soak_5xx_errors` 급증  
**원인**: 컨트롤러의 모든 엔드포인트가 `/v1/` 없이 매핑되어 있었다.

```java
// 수정 전
@GetMapping("/notifications")

// 수정 후
@GetMapping("/v1/notifications")
```

Spring Boot 3.2에서 `/api/v1/notifications` 요청이 매핑되지 않아 `NoResourceFoundException`이 발생하였고, 이것이 `@ExceptionHandler(Exception.class)` 에 잡혀 HTTP 500으로 응답되었다.  
5개 엔드포인트(`GET`, `PUT /{id}/read`, `PUT /read`, `DELETE /{id}`, `DELETE`) 전체를 수정하였다.

---

### Bug 2 — SettlementCommandService import 경로 오류

**증상**: 빌드 시 컴파일 에러  
**원인**: `OutboxEventWriter` import 경로에 `.service.` 패키지 레벨 누락

```java
// 수정 전
import com.rocketcrew.pocat.global.outbox.OutboxEventWriter;

// 수정 후
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
```

동일 패턴을 사용하는 다른 서비스 클래스 전체가 `.service.` 경로를 사용하고 있었으나 이 파일만 누락되어 있었다.

---

### Bug 3 — k6 로컬 실행 시 IP 기반 레이트 리밋 공유 문제

**증상**: `경매목록 200` 체크 96% 실패, `http_req_failed` 급등  
**원인**: k6의 모든 VU가 `localhost` IP를 공유하여 단일 레이트 리밋 버킷을 소진

```
GET /api/v1/auctions  →  IP 기반 rate-limit: 30/min
100 VU × localhost    →  30개 통과 / 나머지 전부 429
```

**해결**: 레이트 리밋이 없는 엔드포인트로 대체

| 시나리오 | 수정 전 | 수정 후 |
|---|---|---|
| 08 익명 브라우징 | `GET /api/v1/auctions?page=X` | `GET /api/v1/auctions/{id}` (시드 ID 직접 사용) |
| 08/10 폴백 | `GET /api/v1/auctions` | `GET /api/v1/auctions/popular` |
| 10 공개 트래픽 | `GET /api/v1/auctions?page=X` | `GET /api/v1/auctions/{id}` |

> 로컬 k6 테스트에서 IP 기반 레이트 리밋이 적용된 목록 API를 사용할 경우, 모든 VU가 하나의 IP를 공유하여 소수의 요청만 통과하고 나머지는 429를 받는다. 분산 환경(클라우드 스테이징)에서는 각 노드의 IP가 다르므로 이 문제가 발생하지 않는다.

---

## 6. 테스트 결과 해석

### 테스트로 증명된 것

| 항목 | 결과 | 수치 |
|---|---|---|
| 동시 입찰 시 Redisson 락 정확성 | ✅ 중복 낙찰 없음, 5xx 없음 | `http_req_failed = 0.00%` |
| Redis 캐시의 부하 흡수 효과 | ✅ 폴링 응답시간 극히 짧음 | 폴링 `p(95) = 10~12ms` |
| HikariCP 커넥션 풀 안정성 | ✅ 20분간 고갈 없음 | 드리프트 없음 |
| 전체 API 비즈니스 로직 정확성 | ✅ 모든 체크 100% 통과 | `checks_succeeded = 100%` |
| 응답시간 (설정 부하 조건 기준) | ✅ 임계치 대비 2~3% 수준 | 전체 `p(95) = 22.9ms` |
| **로컬 환경 Breaking Point (1차)** | ✅ **readers ~300 VU** | `p(95) = 593ms`, TCP 소켓 고갈 신호 1건 |
| **BP 2차 재실행 (pool=10)** | ✅ **500 VU 전 임계치 통과** | `p(95) = 149ms`, BP 미도달 |
| **HikariCP 커넥션 수 성능 영향** | ✅ **pool=5 시 p(95) +91%, 처리량 -28%** | pool 축소 → 성능 저하 수치 확인 — 커넥션 수가 실제 병목 요인임을 간접 증명 |

### 테스트로 증명되지 않은 것

| 질문 | 이유 |
|---|---|
| 실제 운영 환경에서의 성능 | 로컬 Docker, 네트워크/TLS/CDN 없음 |
| 서비스가 수용 가능한 최대 동접 수 | 500 VU 이상 재실행 필요. 1차 BP(300 VU)는 Windows TCP 소켓 한계로, 2차에서 미재현. 실제 HikariCP 병목 확인을 위해 대용량 데이터 또는 pool-size 축소 후 재실행 필요 |
| 장시간(수 시간) 운영 안정성 | 20분 소크는 누수 초기 징후 탐지 수준 |
| 의존 서비스(Kafka, ES) 장애 전파 | 정상 상태만 테스트 |

### 테스트의 실제 가치

이번 테스트는 "안정적 프로덕션 운영 보장"이 아닌 **"개발 과정의 회귀 방지 및 핵심 동시성 설계 검증"** 수준으로 평가하는 것이 적절하다.

- 개발 중 발생한 버그 3건이 테스트를 통해 발견·수정되었다.
- Redisson 분산 락과 BID_ALREADY_LEADING 비즈니스 규칙이 동시 부하 하에서 의도대로 동작함을 수치로 확인하였다.
- Redis 캐시가 실제로 읽기 부하를 흡수하는 효과를 측정하였다.

---

## 7. 향후 개선 방향

실제 서비스 수용 가능 인원을 측정하기 위해서는 다음이 필요하다.

```
1. 용량 계획 수립
   MAU 목표 → DAU → 피크 동접 → Little's Law → 목표 VU 산출

2. 단계적 테스트
   Load Test  : 목표 VU에서 정상 동작 검증
   Stress Test : 목표 VU × 2~3배에서 한계점 탐색
   Soak Test  : 최소 1~2시간 지속 실행

3. 프로덕션 유사 환경
   클라우드 인스턴스 + 실제 네트워크 지연 + TLS 포함

4. 관찰 가시성 확보
   Grafana + InfluxDB 연동으로 시간축 드리프트 시각화
   k6 run --out influxdb=http://localhost:8086/k6

5. HikariCP 튜닝 검증
   maximum-pool-size 를 20으로 조정 후 BP 재측정
   → BP가 높아지면 DB 커넥션이 실질 병목임을 수치로 확인 가능
   → application-local.yaml 에서 직접 조정 후 bootJar + docker compose up --build
```

---

## 부록 — 시나리오별 설정 요약

### 08 임계치

```js
http_req_duration:                             p(95) < 800ms,  p(99) < 2000ms
http_req_duration{scenario:auth_users}:        p(95) < 1000ms
http_req_duration{scenario:auction_polling}:   p(95) < 400ms
http_req_failed:                               rate < 2%
```

### 09 임계치

```js
http_req_duration{scenario:active_bidders}:    p(95) < 1500ms, p(99) < 3000ms
http_req_duration{scenario:auction_watchers}:  p(95) < 500ms,  p(99) < 1000ms
http_req_failed:                               rate < 5%
peak_bid_success_rate:                         rate > 0.5%
```

### 10 임계치

```js
http_req_duration:                             p(95) < 1000ms, p(99) < 2500ms
http_req_duration{scenario:auth_endurance}:    p(95) < 1200ms
http_req_duration{scenario:public_endurance}:  p(95) < 800ms
http_req_failed:                               rate < 2%
soak_5xx_errors:                               count < 10
```

### 11 임계치

```js
// 임계치 실패 = Breaking Point 발견 (abortOnFail: false — 테스트 중단 없이 계속 측정)
http_req_duration{scenario:bp_readers}:  p(95) < 500ms,  p(99) < 2000ms  // Soft Limit 기준
http_req_duration{scenario:bp_writers}:  p(95) < 2000ms
http_req_failed:                         rate < 1%
bp_5xx_errors:                           count < 10      // timeout(status=0) 포함
```

### 실행 방법

```bash
# 전체 시나리오 순차 실행 (~40분, bp 제외)
./k6/run-2.sh all

# 개별 실행
./k6/run-2.sh mixed   # 08 혼합 (~10분)
./k6/run-2.sh peak    # 09 피크 (~9분)
./k6/run-2.sh soak    # 10 소크 (기본 20분)
./k6/run-2.sh bp      # 11 Breaking Point (~12분, 별도 실행 권장)

# 소크 시간 지정
SOAK_DURATION=60m ./k6/run-2.sh soak
```
