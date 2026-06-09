/**
 * [Breaking Point] 입찰 시스템 한계점 탐색
 *
 * 목적:
 *   VU를 단계적으로 증가시키며 서버가 정상 응답을 유지하는 한계 지점을 탐색한다.
 *   임계치 실패는 오류가 아니라 Breaking Point 발견을 의미한다.
 *
 * 탐색 대상 (우선순위 순):
 *   1. HikariCP 커넥션 풀 (기본값 10)
 *      — 동시 DB 요청이 10개 초과 시 커넥션 대기 → 타임아웃 → 500
 *   2. Redisson tryLock(0) 경쟁 처리량
 *      — 대기 없이 즉시 반환 → 409 폭증 시점
 *   3. Tomcat 스레드 풀 (기본값 200)
 *      — DB 슬로우쿼리가 스레드를 오래 점유 시 포화
 *
 * 시나리오 구성:
 *   ① bp_readers  — GET /bids 반복 (DB 직접 읽기, rate-limit 없음)
 *       응답시간 p(95) 급등 시점 = HikariCP 큐 누적 시작
 *
 *   ② bp_writers  — POST /bids 반복 (DB 쓰기 + Redis 락 + Kafka)
 *       5xx 첫 발생 시점 = 실질적 Breaking Point
 *
 * 단계별 부하 계획:
 *   readers  50 → 150 → 300 → 500 VU  (2분 유지 → 급등 관찰)
 *   writers  10 →  30 →  60 VU         (writers는 rate-limit 상한이 있어 소규모)
 *
 * 결과 해석 방법:
 *   - p(95) < 500ms 유지 구간  = 정상 운영 가능 범위
 *   - p(95) 500ms 초과 단계    = Soft Limit (응답 지연 시작)
 *   - 5xx 첫 발생 단계         = Hard Limit (실질 Breaking Point)
 *   - 임계치 실패 = 정상 — 목표가 BP 탐색이므로 threshold crossing이 곧 결과값
 *
 * 소요 시간: 약 12분
 * 피크 VU  : 최대 560 VU (readers 500 + writers 60)
 *
 * 실행:
 *   ./k6/run-2.sh bp
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { login, authOpts } from '../helpers/auth.js';
import { BASE_URL } from '../helpers/config.js';

// ── 상수 ──────────────────────────────────────────────────────────────────
const BIDDER_COUNT = 5;
const PASSWORD     = 'Test1234!';

// ── 커스텀 메트릭 ─────────────────────────────────────────────────────────
// Breaking Point 탐지용 핵심 메트릭
const bp5xxCounter   = new Counter('bp_5xx_errors');       // 5xx + timeout(status=0) 누적 건수 — 0이면 아직 BP 미도달
const bpErrorRate    = new Rate('bp_error_rate');           // 에러율 추이
const bpReadTrend    = new Trend('bp_read_duration_ms');    // GET 응답시간 — 급등 = 커넥션 풀 포화
const bpWriteTrend   = new Trend('bp_write_duration_ms');  // POST 응답시간 — 급등 = DB 쓰기 병목
const bpBidSuccesses = new Counter('bp_bid_successes');     // 입찰 성공(201) 건수

// ── 옵션 ──────────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // ① 읽기 집중: 단계적 VU 증가로 HikariCP 커넥션 고갈 탐색
    // GET /bids는 DB를 직접 읽으므로 커넥션 풀을 지속적으로 소비
    // think-time 0.1s: 최대한 빠른 요청으로 풀 포화 유도
    bp_readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50  },  // 워밍업
        { duration: '2m',  target: 50  },  // 단계 1: 기준선 측정
        { duration: '30s', target: 150 },  // 단계 2: 증가
        { duration: '2m',  target: 150 },  // 단계 2: 측정
        { duration: '30s', target: 300 },  // 단계 3: 증가
        { duration: '2m',  target: 300 },  // 단계 3: 측정 — HikariCP 포화 예상 구간
        { duration: '30s', target: 500 },  // 단계 4: 증가
        { duration: '2m',  target: 500 },  // 단계 4: 측정 — Hard Limit 예상
        { duration: '30s', target: 0   },  // 쿨다운
      ],
      exec: 'bpReader',
    },

    // ② 쓰기 집중: DB 쓰기 + Redis 락 + Kafka 복합 경로 압박
    // bid-limit 30/min × 5토큰 = 150/min 상한이 있어 소규모로 유지
    // 읽기 부하와 겹쳐 커넥션 경쟁을 심화시키는 역할
    bp_writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 10 },
        { duration: '2m',  target: 30 },
        { duration: '4m',  target: 60 },  // readers 피크와 동시 압박
        { duration: '3m',  target: 60 },
        { duration: '30s', target: 0  },
      ],
      exec: 'bpWriter',
    },
  },

  thresholds: {
    // ─ 이 임계치들이 실패하는 구간 = Breaking Point ─
    // abortOnFail: false → BP 도달 후에도 계속 측정하여 전체 곡선 파악

    // 읽기 경로: p(95) 500ms 초과 = HikariCP 큐 적체 시작
    'http_req_duration{scenario:bp_readers}': [
      { threshold: 'p(95)<500',  abortOnFail: false },
      { threshold: 'p(99)<2000', abortOnFail: false },
    ],

    // 쓰기 경로: p(95) 2000ms 초과 = DB 쓰기 + 락 대기 병목
    'http_req_duration{scenario:bp_writers}': [
      { threshold: 'p(95)<2000', abortOnFail: false },
    ],

    // 5xx 에러율: 1% 초과 = 실질적 Breaking Point 도달
    http_req_failed: [
      { threshold: 'rate<0.01', abortOnFail: false },
    ],

    // 5xx 절대 건수: 10건 이상 발생 시 = Hard Limit 확인
    bp_5xx_errors: [
      { threshold: 'count<10', abortOnFail: false },
    ],
  },
};

// ── Setup ─────────────────────────────────────────────────────────────────
export function setup() {
  const tokens = [];
  for (let i = 1; i <= BIDDER_COUNT; i++) {
    const token = login(`k6-bidder-${i}@test.com`, PASSWORD);
    if (!token) throw new Error(`로그인 실패: k6-bidder-${i}@test.com`);
    tokens.push(token);
  }

  const auctionIds = (__ENV.AUCTION_IDS || '')
    .split(',')
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !isNaN(n));

  if (auctionIds.length === 0) {
    throw new Error('AUCTION_IDS 환경변수가 필요합니다. run-2.sh bp 로 실행하세요.');
  }

  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[Breaking Point 탐색 시작]');
  console.log(`  경매 ${auctionIds.length}개 | 입찰자 ${tokens.length}명`);
  console.log('  HikariCP 기본 풀: 10 커넥션');
  console.log('  Tomcat 기본 스레드: 200');
  console.log('  예상 Soft Limit: readers ~150~300 VU (p95 상승 시작)');
  console.log('  예상 Hard Limit: readers ~300~500 VU (5xx 발생)');
  console.log('  임계치 실패 = Breaking Point 발견 (오류 아님)');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

  return { tokens, auctionIds };
}

// ── ① 읽기 집중 VU ────────────────────────────────────────────────────────
// GET /auctions/{id}/bids: DB 직접 조회 (캐시 없음 — 매 요청 HikariCP 소비)
// think-time 0.1s: 최소화하여 커넥션 풀에 지속적 압박
export function bpReader(data) {
  const auctionId = data.auctionIds[(__VU - 1) % data.auctionIds.length];
  const rand = Math.random();

  const t0 = Date.now();
  let res;

  if (rand < 0.60) {
    // 60% — 입찰 목록 조회 (DB 페이지네이션 쿼리)
    res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}/bids?size=20`);
    check(res, { '입찰목록': (r) => r.status === 200 });

  } else if (rand < 0.85) {
    // 25% — 경매 상세 조회
    res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`);
    check(res, { '경매상세': (r) => r.status === 200 });

  } else {
    // 15% — 카드 상세 (캐시 없는 추가 DB 읽기)
    const cardId = ((__VU - 1) % 8) + 1;
    res = http.get(`${BASE_URL}/api/v1/cards/${cardId}`);
    check(res, { '카드상세': (r) => r.status === 200 });
  }

  bpReadTrend.add(Date.now() - t0);

  if (res.status === 0 || res.status >= 500) {
    bp5xxCounter.add(1);
    bpErrorRate.add(true);
    const label = res.status === 0 ? 'timeout(BP 신호)' : `5xx ${res.status}`;
    console.log(`🔴 [${__VU}] ${label} → HikariCP 고갈 의심`);
  } else {
    bpErrorRate.add(false);
  }

  sleep(0.1); // 최소 think-time — 지속적 커넥션 압박 유지
}

// ── ② 쓰기 집중 VU ────────────────────────────────────────────────────────
// POST /bids: DB 읽기 + 쓰기 + Redis 락 + Kafka — 가장 무거운 경로
// 읽기 부하와 겹쳐 커넥션 경쟁을 심화
export function bpWriter(data) {
  if (!data.tokens || data.tokens.length === 0) return;

  const token     = data.tokens[(__VU - 1) % data.tokens.length];
  const opts      = authOpts(token);
  const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];
  const bidPrice  = 2000000 + Math.floor(Math.random() * 6000000);

  const t0  = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
    JSON.stringify({ bidPrice }),
    // 400/409/429는 비즈니스 로직 거부 — http_req_failed 제외
    { ...opts, responseCallback: http.expectedStatuses(201, 400, 409, 429) },
  );
  bpWriteTrend.add(Date.now() - t0);

  if (res.status === 201) {
    bpBidSuccesses.add(1);
  }

  if (res.status === 0 || res.status >= 500) {
    bp5xxCounter.add(1);
    bpErrorRate.add(true);
    const label = res.status === 0 ? 'timeout' : `5xx ${res.status}`;
    console.log(`🔴 [VU ${__VU}] 입찰 ${label} — BP 도달`);
  } else {
    bpErrorRate.add(false);
  }

  check(res, { '쓰기 5xx 없음': (r) => r.status < 500 });

  sleep(0.2);
}

// ── Teardown ───────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[Breaking Point 탐색 결과 해석 가이드]');
  console.log('');
  console.log('  bp_5xx_errors = 0        → 테스트 범위 내 BP 미도달 (VU 상향 필요)');
  console.log('  bp_5xx_errors > 0        → BP 도달. timeout(status=0) 또는 5xx 첫 등장 VU 단계 = Hard Limit');
  console.log('  ※ timeout = k6가 60초 내 응답 못 받음 → HikariCP 큐 포화가 5xx보다 먼저 나타나는 패턴');
  console.log('');
  console.log('  bp_read_duration_ms      → 단계별 p(95) 추이로 Soft Limit 파악');
  console.log('    p(95) 100ms 미만       → 정상 (HikariCP 여유)');
  console.log('    p(95) 500ms 돌파       → Soft Limit (커넥션 큐 적체 시작)');
  console.log('    p(95) 급등 후 수렴     → 커넥션 타임아웃으로 요청 실패 전환');
  console.log('');
  console.log('  http_req_failed 임계치 실패 단계 = 해당 VU 수가 Breaking Point');
  console.log('');
  console.log('  HikariCP 튜닝 포인트:');
  console.log('    spring.datasource.hikari.maximum-pool-size 조정 후 재실행');
  console.log('    → BP가 높아지면 DB 커넥션이 병목임을 확인');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}
