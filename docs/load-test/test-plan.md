# POCAT 부하 테스트 계획서 (Test Plan)

각 테스트의 **목적·예상·목표·측정지표·판정기준**을 정의한다. **결과/개선** 절은 실측 후 채운다(현재 placeholder).

- 대상 흐름: 로그인 → 인기 경매/목록/카드 조회 → 경매 상세 → (선택) 입찰
- 기본 SLO(`lib/config.js`에서 조정): 읽기 p95 < 500ms · p99 < 1000ms / 쓰기 p95 < 800ms / 에러율 < 1% / check 통과율 > 99%
- 관측 페어링: k6(클라이언트) + Grafana·Loki·CloudWatch(ALB 5xx·TargetResponseTime, RDS CPU·connections, Redis, JVM heap/GC)
- 테스트별 관전 지표(보유/추가 권장)는 [monitoring.md](./monitoring.md) 참고. **현재 보유:** HTTP 요청률·5xx 에러율·JVM Heap·Batch Active Threads / **추가 필요 3종:** 서버측 p95·p99, HikariCP 커넥션풀, 앱·RDS CPU

> SLO 수치는 보수적 기본값이다. 실제 서비스 목표치로 교체할 것.

---

## 공통 측정지표 (모든 테스트)

| 지표 | 출처 | 의미 |
|------|------|------|
| `http_req_duration` p50/p95/p99 | k6 | 응답시간 분포 |
| `http_req_failed` rate | k6 | 요청 실패율(5xx·타임아웃·연결실패) |
| `checks` rate | k6 | 검증 통과율 |
| `iterations` / `vus` | k6 | 처리량·동시성 |
| ALB `TargetResponseTime`, `HTTPCode_Target_5XX` | CloudWatch | 서버측 지연·에러 |
| RDS CPU·`DatabaseConnections`, Redis CPU·메모리 | CloudWatch | 백엔드 병목 |
| JVM heap·GC pause, HikariCP active/pending | Grafana/actuator | 누수·풀 고갈 |

---

## 01. 부하 테스트 (Load Test) — 1시간 기준

**스크립트:** `load-test/k6/01-load.js` · executor `ramping-vus` (5분 ramp-up → 50분 유지 → 5분 ramp-down, 총 60분, 기본 100 VU)

### 목적
**기대 동시 사용자** 수준의 부하를 **1시간 동안 유지**해, 평상시 운영 부하에서 응답시간·에러율이 SLO를 안정적으로 만족하는지 검증. 다른 테스트의 baseline.

### 예상
- 워밍업 후 안정 구간(50분)에서 읽기 p95 < 500ms, 에러율 ~0%가 **시간 내내 평탄**.
- 캐시(인기 경매 랭킹·프로필) 히트로 목록/상세 조회는 빠름.

### 목표 / 판정기준
- ✅ `http_req_duration{kind:read}` p95 < 500ms, p99 < 1000ms
- ✅ `http_req_failed` rate < 1%
- ✅ `checks` rate > 99%
- ✅ (쓰기 포함 시) `http_req_duration{kind:write}` p95 < 800ms
- ✅ 1시간 유지 구간에서 p95·에러율 drift 없음(우상향 X)

### 결과/개선
> _테스트 후 작성._ (요약 표 + Grafana 캡처 + 발견 이슈/조치)

---

## 02. 지속성 테스트 (Soak / Endurance)

**스크립트:** `load-test/k6/02-soak.js` · executor `constant-vus` (기본 30 VU, `DURATION`=1h+)

### 목적
중간 부하를 **장시간** 유지해 시간이 지나야 드러나는 문제 탐지: 메모리 누수, 커넥션 풀 고갈, Redis/캐시 무한 증가, Kafka 컨슈머 랙 적체, GC 악화, 디스크/로그 누적.

### 예상
- 응답시간·에러율이 시간에 무관하게 **평탄(flat)**.
- JVM heap은 톱니(정상 GC), 우상향 추세 없음. 커넥션 수 안정.

### 목표 / 판정기준
- ✅ 시작 1시간 구간 대비 마지막 구간 p95 상승률 < 10% (drift 없음)
- ✅ `http_req_failed` rate < 1% 전 구간 유지
- ✅ JVM heap·DB connections·Redis 메모리 우상향 추세 없음
- ❌ 응답시간 점진 상승 / heap 지속 증가 → 누수 의심

### 결과/개선
> _테스트 후 작성._ (시간대별 p95·heap·connection 시계열 첨부)

---

## 03. 스트레스 테스트 (Stress)

**스크립트:** `load-test/k6/03-stress.js` · executor `ramping-vus` (100→200→400→600→0)

### 목적
부하를 **기대치 이상**으로 단계 상승시켜 ① 성능 저하 시작 지점, ② 과부하 시 거동(우아한 저하 vs 5xx 폭증), ③ **부하 제거 후 복구력**을 확인.

### 예상
- 400~600 VU 구간에서 p95 상승, 일부 지연. 5xx 소폭 가능.
- 부하 제거(마지막 stage) 후 빠르게 baseline 회복.

### 목표 / 판정기준
- ✅ 과부하에서도 5xx 비율이 통제됨(연쇄 장애·완전 정지 없음)
- ✅ 부하 제거 후 2~3분 내 p95·에러율 baseline 복귀(복원력)
- 📌 성능이 무너지기 시작하는 VU/RPS 지점 기록 → 04·05 입력값
- ❌ 과부하 후에도 회복 못 함 / OOM·커넥션 누수 잔존

### 결과/개선
> _테스트 후 작성._ (저하 시작 지점, 복구 시간, 병목 원인)

---

## 04. 스파이크 테스트 (Spike)

**스크립트:** `load-test/k6/04-spike.js` · executor `ramping-arrival-rate` (20→1000 RPS 10초 급증→유지→급감)

### 목적
**짧은 시간 트래픽 폭증**(인기 경매 마감 직전, 푸시 직후) 상황에서 시스템이 죽지 않고 견디는지, 큐/커넥션풀/오토스케일이 흡수하는지, 스파이크 종료 후 즉시 회복하는지 검증.

> open model(`arrival-rate`) 사용 — 응답이 느려져도 계속 도착시켜 실제 스파이크의 큐 적체를 재현.

### 예상
- 급증 순간 일시적 지연·소수 에러(허용). 시스템은 생존.
- 폭증 종료 후 빠른 회복. 카스케이딩 실패 없음.

### 목표 / 판정기준
- ✅ 스파이크 중 `http_req_failed` rate < 5% (일시 허용)
- ✅ 스파이크 종료 후 1분 내 p95·에러율 정상 복귀
- ✅ 프로세스 다운·커넥션풀 영구 고갈 없음
- ❌ 급증 시 완전 정지 / 종료 후에도 회복 불가

### 결과/개선
> _테스트 후 작성._ (스파이크 순간 p95·에러, 회복 곡선)

---

## 05. 브레이킹 포인트 테스트 (Breaking Point / Capacity)

**스크립트:** `load-test/k6/05-breakpoint.js` · executor `ramping-arrival-rate` (50→`MAX_RATE` RPS, 25분 선형, SLO 위반 시 `abortOnFail`)

> ⚠️ 반드시 같은 리전 EC2(또는 분산/Grafana Cloud k6) + 스테이징에서.

### 목적
부하를 멈추지 않고 끌어올려 **SLO가 무너지기 시작하는 정확한 RPS/동시성**을 찾는다 = **용량 산정**. 오토스케일 임계·알림 기준·인프라 사이징의 근거.

### 예상
- 어느 RPS까지 p95 평탄 유지 → 임계 RPS 부근에서 p95 급등 + 에러율 상승(무릎 곡선).
- 그 직전 값이 **안전 운영 한계(max sustainable throughput)**.

### 목표 / 판정기준
- 📌 **최대 처리량(RPS)** 측정 — p95 < SLO이면서 에러율 < 1%인 최대 지점
- 📌 무릎(knee) 지점의 RPS·동시성·병목 자원(DB CPU? 커넥션풀? 앱 CPU?) 식별
- ✅ SLO 위반 시 `abortOnFail`로 자동 중단(과도한 가격 방지)
- 산출물: "안전 한계 X RPS, 한계 자원 Y" → autoscaling/alert 임계 도출

### 결과/개선
> _테스트 후 작성._ (RPS-지연 곡선, 무릎 지점, 병목 자원, 권장 사이징)

---

## 실행 순서 권장

1. **01 부하(1시간)** → baseline 확보, 흐름·계정·SLO 검증
2. **03 스트레스** → 저하 시작 지점 파악(04·05 입력값)
3. **04 스파이크** → 급변 내성·회복력
4. **05 브레이킹포인트** → 용량 확정 (EC2/스테이징)
5. **02 지속성** → 마지막에 장시간(누수·적체) — 별도 시간대

> 각 테스트 사이 서버가 baseline으로 회복됐는지 확인 후 다음 진행.
