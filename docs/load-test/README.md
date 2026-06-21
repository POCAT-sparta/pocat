# POCAT 부하 테스트 (Load Testing)

운영/스테이징 환경 대상 k6 부하 테스트. 5종(부하·지속성·스트레스·스파이크·브레이킹포인트)을 단일 도구(k6)로 수행한다.

> 이 문서는 **목적·예상·목표·실행 방법**까지를 다룬다. 실측 결과/개선은 [test-plan.md](./test-plan.md)의 각 테스트 "결과/개선" 절에 테스트 후 채운다.
> 테스트 중 **어떤 모니터링 지표를 봐야 하는지**(보유 지표 + 추가 권장)는 [monitoring.md](./monitoring.md) 참고.

---

## 1. 지금 인프라로 테스트가 되는가?

**된다.** 구조가 `클라이언트 → ALB(퍼블릭, internet-facing) → EC2(프라이빗 서브넷)` 이므로, **EC2가 프라이빗인 것은 무관**하다. 외부 노출은 ALB뿐이고 트래픽은 ALB가 받아 타깃그룹으로 내부 전달한다. 프론트가 쓰는 그 도메인을 그대로 k6로 찌르면 된다.

### 주의할 점

| 요소 | 영향 | 대응 |
|------|------|------|
| **CORS** | 영향 없음 (브라우저 정책, k6는 비브라우저) | — |
| **AWS WAF rate-based rule** | 단일 IP 고RPS → `403/429` 차단 가능 | WAF 룰 확인, 테스트 출발 IP allowlist |
| **단일 PC + 가정용 회선** | 업로드 대역폭·NAT·RTT가 먼저 한계 → 부하기가 먼저 죽음 | 스파이크/브레이킹포인트는 **같은 리전(ap-northeast-2) EC2**에서 |
| **운영 데이터/결제 오염** | 실 PortOne 결제, DB write, Kafka, 알림 발송 | 읽기 위주 + `/internal/test/*` + `ALLOW_WRITE=false`(기본) |
| **운영 지표 오염** | CloudWatch/Loki에 부하 흔적 | 저트래픽 시간대, 태깅, 사후 필터 |

### 권고

- **부하·지속성·스트레스** → PC에서 수행 가능.
- **스파이크·브레이킹포인트** → 반드시 **같은 리전 EC2**에서 부하 생성(회선 한계가 결과를 왜곡).
- 가능하면 **prod 미러 스테이징**에서. prod 직격은 결제·데이터 오염 리스크. 불가피하면 읽기 위주 + 저트래픽 시간대 + `/internal/test/*`로 부수효과 격리.

---

## 2. k6 한 도구로 5종 전부 충분한가?

**충분하다.** 5종 모두 k6 `scenarios` executor로 표현된다.

| 테스트 | k6 executor | 비고 |
|--------|-------------|------|
| 부하(1시간) | `ramping-vus` 1h 유지 + 유저 여정 | ✅ |
| 지속성(soak) | `constant-vus` 장시간 | ✅ 장시간은 EC2 권장 |
| 스트레스 | `ramping-vus` 초과 부하 | ✅ |
| 스파이크 | `ramping-arrival-rate` 급증 | ✅ open model 필수 |
| 브레이킹포인트 | `ramping-arrival-rate` 무한 증가 | ✅ |

**보완이 필요한 2가지:**
1. **부하 생성 용량** — 스파이크/브레이킹포인트는 단일 노트북+가정회선이 한계. → 같은 리전 EC2 / 다중 인스턴스 / **Grafana Cloud k6**로 분산.
2. **서버측 관측(필수 페어링)** — k6는 클라이언트 지표만 준다. 병목(DB/CPU/커넥션풀)을 짚으려면 **Grafana + Loki + ELK + CloudWatch**(ALB 5xx, TargetResponseTime, RDS CPU·connections, Redis)와 나란히 봐야 한다.

> SSE(`/api/ai/assistant/stream`)·WebSocket 입찰은 k6 experimental 모듈로 가능하나 별도 검증 권장(이 세트엔 미포함).

---

## 3. 도구 선택

- **부하 생성:** k6 (기본). 대규모는 Grafana Cloud k6 / EC2 다중.
- **서버 관측:** Grafana·Loki·Logstash(ELK) + CloudWatch.
- **대안:** Gatling(JVM 친화), Locust(Python).

---

## 4. 사전 준비

1. **k6 설치**
   ```bash
   brew install k6          # macOS
   # 또는 docker: grafana/k6
   ```
2. **테스트 계정 시드** — 운영 유저 테이블 오염을 피하려고 전용 계정 사용.
   - 사전 생성한 계정을 `TEST_EMAIL`/`TEST_PASSWORD`로 주입, 또는
   - 계정 풀 `USERS_JSON='[{"email":"u1@x.t","password":"P!1"}, ...]'`
3. **대상 도메인** — `BASE_URL`(끝 슬래시 없이).

---

## 5. 실행 방법

```bash
cd load-test/k6

# 01. 부하 테스트 — 1시간 기준 (읽기 위주, 운영 안전)
k6 run -e BASE_URL=https://api.kuromi.click 01-load.js

# 동접 수 조정
k6 run -e BASE_URL=https://api.kuromi.click -e TARGET_VUS=150 01-load.js

# 스테이징에서 쓰기(입찰)까지
k6 run -e BASE_URL=https://staging.kuromi.click -e ALLOW_WRITE=true 01-load.js

# 02. 지속성 (장시간 — EC2에서 nohup 권장)
k6 run -e BASE_URL=... -e DURATION=2h -e VUS=30 02-soak.js

# 03. 스트레스
k6 run -e BASE_URL=... 03-stress.js

# 04. 스파이크 (같은 리전 EC2 권장)
k6 run -e BASE_URL=... 04-spike.js

# 05. 브레이킹포인트 (반드시 같은 리전 EC2 / 스테이징)
k6 run -e BASE_URL=... -e MAX_RATE=3000 05-breakpoint.js
```

### 결과를 Grafana로 보내기 (권장)

```bash
# k6 → InfluxDB/Prometheus remote write → Grafana 대시보드
K6_PROMETHEUS_RW_SERVER_URL=http://<prom>:9090/api/v1/write \
  k6 run -o experimental-prometheus-rw 01-load.js
```

또는 JSON 요약 저장:
```bash
k6 run --summary-export=results/01-load.json 01-load.js
```

---

## 6. 환경변수 레퍼런스

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `https://api.kuromi.click` | 대상 ALB 도메인 |
| `INTERNAL_BASE_URL` | `BASE_URL` | `/internal/test/*` 접근용(같은 VPC일 때) |
| `ALLOW_WRITE` | `false` | 입찰 등 쓰기 경로 활성화(운영 보호용 기본 OFF) |
| `TEST_EMAIL` / `TEST_PASSWORD` | `loadtest@pocat.test` / `Loadtest!1` | 단일 테스트 계정 |
| `USERS_JSON` | 없음 | 계정 풀(JSON 배열) — VU별 분산 로그인 |
| `TARGET_VUS` | `100` | 부하 테스트(01) 기대 동접 수 |
| `DURATION` / `VUS` | `1h` / `30` | soak 전용 |
| `MAX_RATE` | `3000` | breakpoint 목표 상한 RPS |

---

## 7. 안전 체크리스트 (운영 대상 시)

- [ ] WAF rate-limit에 테스트 IP 예외 또는 룰 인지
- [ ] `ALLOW_WRITE=false` 확인(또는 스테이징에서만 true)
- [ ] 결제(PortOne)·알림 발송 경로 미포함 확인
- [ ] 저트래픽 시간대 합의
- [ ] 관계자(인프라/백엔드) 공지 + 롤백/중단 기준 합의
- [ ] CloudWatch/Grafana 대시보드 동시 모니터링 준비
- [ ] 브레이킹포인트는 `abortOnFail`로 자동 중단되게 설정됨(확인)
