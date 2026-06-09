/**
 * [Realistic Load] 혼합 실사용 시나리오 — 익명 브라우징 + 인증 입찰 + 경매 폴링
 *
 * 목적:
 *   실제 경매 사이트 피크 타임(저녁 시간대)을 재현한다.
 *   3가지 유저 유형이 동시에 서버를 사용할 때의 종합 성능을 측정한다.
 *
 * 유저 유형:
 *   ① anonymous_traffic (100 VU 피크) — 비인증 브라우저
 *       카드 검색(ES), 경매 목록, 카드 상세, 인기 경매, 평균가 조회
 *
 *   ② auth_users (40 VU 피크) — 로그인한 활성 유저
 *       경매 상세 → 입찰 시도 → 내 입찰 조회 → 알림 확인
 *
 *   ③ auction_polling (80 req/s 고정) — 경매 종료 임박 새로고침 폭증 시뮬레이션
 *       POST /bids 없이 GET /bids 만 반복 (입찰 현황 실시간 확인)
 *
 * 사전 준비 (run-2.sh 참고):
 *   1) k6-bidder-1~5@test.com 계정 + ACTIVE 경매 10개 (seed-heavy-test-data.sql)
 *   2) AUCTION_IDS 환경변수 (콤마 구분): -e AUCTION_IDS=101,102,...
 *
 * 실행:
 *   ./k6/run-2.sh mixed
 *   또는 직접: k6 run -e AUCTION_IDS=<ids> k6/scenarios/08-realistic-mixed-load.js
 *
 * 소요 시간: 약 10분
 * 피크 VU  : 약 140 VU + 80 req/s arrival-rate
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { login, authOpts } from '../helpers/auth.js';
import { BASE_URL } from '../helpers/config.js';

// ── 시드 데이터 상수 ──────────────────────────────────────────────
const BIDDER_COUNT = 5;
const PASSWORD     = 'Test1234!';
const CARD_IDS     = [1, 2, 3, 4, 5, 6, 7, 8];
const KEYWORDS     = ['Butterfree', 'Paras', 'Pansage', 'Karrablast', 'Carnivine', 'Simisage'];
const RARITIES     = ['Holo Rare V', 'Holo Rare VMAX', 'Common', 'Uncommon'];

// ── 커스텀 메트릭 ─────────────────────────────────────────────────
const bidSuccessCounter  = new Counter('mixed_bid_successes');
const bidFailedCounter   = new Counter('mixed_bid_failures');
const bidDurationTrend   = new Trend('mixed_bid_duration_ms');
const authCallTrend      = new Trend('mixed_auth_call_duration_ms');

// ── 옵션 ──────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // ① 비인증 브라우징: 점진 증가 → 피크 → 유지 → 감소
    anonymous_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 60  },  // ramp-up
        { duration: '5m',  target: 100 },  // peak
        { duration: '3m',  target: 80  },  // sustain
        { duration: '1m',  target: 0   },  // cool-down
      ],
      exec: 'anonymousBrowse',
    },

    // ② 인증 유저: 로그인 후 입찰·알림·내역 조회
    auth_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m',  target: 20 },  // 로그인 워밍업
        { duration: '5m',  target: 40 },  // peak
        { duration: '2m',  target: 20 },  // cool-down
        { duration: '1m',  target: 0  },
      ],
      exec: 'authenticatedUser',
    },

    // ③ 경매 폴링: 종료 임박 시 새로고침 폭증 (고정 arrival-rate)
    auction_polling: {
      executor:        'constant-arrival-rate',
      rate:            80,        // 80 req/s
      timeUnit:        '1s',
      duration:        '8m',
      preAllocatedVUs: 30,
      maxVUs:          100,
      exec:            'auctionPoll',
    },
  },

  thresholds: {
    http_req_duration:                              ['p(95)<800',  'p(99)<2000'],
    http_req_failed:                                ['rate<0.02'],
    // 인증 경로는 DB·Redis 모두 경유하므로 별도 임계치
    'http_req_duration{scenario:auth_users}':       ['p(95)<1000'],
    // 폴링 경로는 가볍게 (DB 쿼리 → Redis 캐시 기대)
    'http_req_duration{scenario:auction_polling}':  ['p(95)<400'],
  },
};

// ── Setup: 입찰자 5명 로그인 + 경매 ID 파싱 ──────────────────────
export function setup() {
  const tokens = [];
  for (let i = 1; i <= BIDDER_COUNT; i++) {
    const email = `k6-bidder-${i}@test.com`;
    const token = login(email, PASSWORD);
    if (!token) throw new Error(`로그인 실패: ${email} — run-2.sh 로 실행했는지 확인하세요.`);
    tokens.push(token);
  }

  // AUCTION_IDS="101,102,103,..." 형태로 전달
  const raw = __ENV.AUCTION_IDS || '';
  const auctionIds = raw
    .split(',')
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !isNaN(n));

  if (auctionIds.length === 0) {
    console.warn('⚠️  AUCTION_IDS 미설정 — 입찰·폴링 시나리오는 경매 목록 fallback으로 실행됩니다.');
  } else {
    console.log(`✅ 경매 ${auctionIds.length}개 로드: [${auctionIds.join(', ')}]`);
  }

  return { tokens, auctionIds };
}

// ── ① 비인증 브라우징 ─────────────────────────────────────────────
// data 파라미터: auctionIds (특정 경매 상세 조회에 사용)
// 주의: GET /api/v1/auctions (목록)은 IP 기반 rate-limit (30/min) 때문에
//       로컬 k6에서 100 VU 동시 실행 시 ~97% 429 응답.
//       → 인기 경매(popular) 또는 specific auction detail로 대체.
export function anonymousBrowse(data) {
  const rand = Math.random();

  if (rand < 0.35) {
    // 35% — 카드 키워드 검색 (ES fulltext)
    const kw  = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    const res = http.get(`${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(kw)}`);
    check(res, { '검색 200': (r) => r.status === 200 });

  } else if (rand < 0.55) {
    // 20% — 특정 경매 상세 (rate-limit 없는 공개 엔드포인트)
    // GET /api/v1/auctions는 IP 기반 rate-limit(30/min)으로 100 VU 동시 실행 시 97% 429
    // → seed로 생성된 경매 ID로 직접 조회 (인기경매 popular과 함께 대체)
    if (data.auctionIds && data.auctionIds.length > 0) {
      const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];
      const res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`);
      check(res, { '경매상세(익명) 200': (r) => r.status === 200 });
    } else {
      const res = http.get(`${BASE_URL}/api/v1/auctions/popular`);
      check(res, { '인기경매 200': (r) => r.status === 200 });
    }

  } else if (rand < 0.70) {
    // 15% — 카드 상세
    const id  = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
    const res = http.get(`${BASE_URL}/api/v1/cards/${id}`);
    check(res, { '카드상세 200': (r) => r.status === 200 });

  } else if (rand < 0.82) {
    // 12% — 인기 경매
    const res = http.get(`${BASE_URL}/api/v1/auctions/popular`);
    check(res, { '인기경매 200': (r) => r.status === 200 });

  } else if (rand < 0.92) {
    // 10% — 평균가 조회 (Redis 캐시)
    const id  = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
    const res = http.get(`${BASE_URL}/api/v1/cards/${id}/average-price`);
    check(res, { '평균가 200': (r) => r.status === 200 });

  } else {
    // 8% — 희귀도 필터 검색
    const rarity = RARITIES[Math.floor(Math.random() * RARITIES.length)];
    const res    = http.get(`${BASE_URL}/api/v1/cards?rarity=${encodeURIComponent(rarity)}`);
    check(res, { '필터검색 200': (r) => r.status === 200 });
  }

  sleep(Math.random() * 1.5 + 0.5); // 0.5~2.0s think-time
}

// ── ② 인증 유저 행동 ─────────────────────────────────────────────
export function authenticatedUser(data) {
  if (!data.tokens || data.tokens.length === 0) return;

  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const opts  = authOpts(token);
  const rand  = Math.random();

  if (rand < 0.30 && data.auctionIds.length > 0) {
    // 30% — 경매 상세 확인 → 입찰 시도
    const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];

    // 1. 경매 상세
    const detail = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, opts);
    check(detail, { '경매상세 200': (r) => r.status === 200 });
    sleep(Math.random() * 1 + 0.5);

    // 2. 현재 입찰 현황 확인
    const t0   = Date.now();
    const bids = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}/bids?size=5`, opts);
    authCallTrend.add(Date.now() - t0);
    check(bids, { '입찰목록 200': (r) => r.status === 200 });

    // 현재 최고가 파악 → +offset 입찰가 산정
    // __VU * 10000 + __ITER * 500: VU별 독립 가격 구간으로 최고가 경쟁 최소화
    const bidPrice = 100000 + __VU * 10000 + (__ITER || 0) * 500;
    sleep(0.3);

    // 3. 입찰 (400/409/429는 비즈니스 로직 거부 — http_req_failed 집계 제외)
    const start  = Date.now();
    const bidRes = http.post(
      `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
      JSON.stringify({ bidPrice }),
      { ...opts, responseCallback: http.expectedStatuses(201, 400, 409, 429) },
    );
    bidDurationTrend.add(Date.now() - start);

    if (bidRes.status === 201) {
      bidSuccessCounter.add(1);
    } else {
      bidFailedCounter.add(1);
    }
    // 201=성공, 400=가격 오류, 409=락 충돌, 429=레이트리밋 모두 허용
    check(bidRes, {
      '입찰 허용 상태코드': (r) => [201, 400, 409, 429].includes(r.status),
    });

  } else if (rand < 0.55) {
    // 25% — 알림 조회
    const res = http.get(`${BASE_URL}/api/v1/notifications`, opts);
    check(res, { '알림 200': (r) => r.status === 200 });

  } else if (rand < 0.72) {
    // 17% — 내 입찰 내역
    const res = http.get(`${BASE_URL}/api/v1/bids/me`, opts);
    check(res, { '내입찰 200': (r) => r.status === 200 });

  } else if (rand < 0.87) {
    // 15% — 카드 검색 (인증 유저도 검색함)
    const kw  = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    const res = http.get(`${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(kw)}`, opts);
    check(res, { '검색 200': (r) => r.status === 200 });

  } else {
    // 13% — 내 경매 / 주문 확인
    const res = Math.random() < 0.5
      ? http.get(`${BASE_URL}/api/v1/auctions/me`, opts)
      : http.get(`${BASE_URL}/api/v1/orders/me`,   opts);
    check(res, { '내정보 200': (r) => r.status === 200 });
  }

  sleep(Math.random() * 2 + 1); // 1~3s think-time (신중한 유저)
}

// ── ③ 경매 폴링 ───────────────────────────────────────────────────
export function auctionPoll(data) {
  if (data.auctionIds && data.auctionIds.length > 0) {
    // 특정 경매 입찰 현황 폴링 (새로고침 패턴)
    const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];
    const res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}/bids?size=10`);
    check(res, { '입찰폴링 200': (r) => r.status === 200 });
  } else {
    // fallback: 인기 경매 (GET /api/v1/auctions는 IP rate-limit으로 100 VU 공유 시 429)
    const res = http.get(`${BASE_URL}/api/v1/auctions/popular`);
    check(res, { '인기경매(폴링폴백) 200': (r) => r.status === 200 });
  }
  // arrival-rate executor이므로 sleep 없음 — 도착률이 VU 수를 조절함
}

// ── Teardown ──────────────────────────────────────────────────────
export function teardown() {
  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[혼합 실사용 부하 결과 해석]');
  console.log('  mixed_bid_successes    : 인증 입찰 성공(201) 건수');
  console.log('  mixed_bid_failures     : 400/409/429 건수 (정상 동작 포함)');
  console.log('  mixed_bid_duration_ms  : POST /bids 응답시간 분포');
  console.log('  mixed_auth_call_duration_ms : GET /bids 응답시간 분포');
  console.log('  http_req_duration{scenario:auction_polling} : 폴링 경로 응답시간');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}
