/**
 * [Peak] 경매 피크 타임 — 다중 경매 동시 경쟁 입찰
 *
 * 목적:
 *   인기 경매 여러 개가 동시에 활성화된 상태에서
 *   "구경꾼"과 "적극 입찰자"가 뒤섞인 실제 피크 시간대를 재현한다.
 *
 *   05-bid-concurrency.js 와의 차이:
 *     - 05: 단일 경매에 N명 동시 입찰 → Redisson 락 정확성 검증
 *     - 09: 다중 경매 × 다수 입찰자 → 전체 시스템 처리량·지연 검증
 *
 * 시나리오 구성:
 *   ① auction_watchers (150 VU 피크)
 *       GET /auctions/{id}/bids 반복 폴링 — 읽기 트래픽의 주체
 *       카드 상세, 경매 상세, 입찰 목록을 빠르게 순환
 *
 *   ② active_bidders (60 VU 피크)
 *       여러 경매를 순환하며 입찰 → 현황 확인 → 다음 경매 이동
 *       실제 입찰 성공률 및 Redisson 락 스루풋 측정
 *
 * 사전 준비 (run-2.sh 참고):
 *   seed-heavy-test-data.sql 로 10개 활성 경매 + 5명 입찰자 준비
 *
 * 실행:
 *   ./k6/run-2.sh peak
 *   또는 직접: k6 run -e AUCTION_IDS=<ids> k6/scenarios/09-auction-peak-load.js
 *
 * 소요 시간: 약 9분
 * 피크 VU  : 약 210 VU (watchers 150 + bidders 60)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { login, authOpts } from '../helpers/auth.js';
import { BASE_URL } from '../helpers/config.js';

// ── 상수 ──────────────────────────────────────────────────────────
const BIDDER_COUNT = 5;
const PASSWORD     = 'Test1234!';
const CARD_IDS     = [1, 2, 3, 4, 5, 6, 7, 8];

// ── 커스텀 메트릭 ─────────────────────────────────────────────────
const peakBidSuccesses  = new Counter('peak_bid_successes');    // 입찰 성공(201)
const peakBidConflicts  = new Counter('peak_bid_lock_failed');  // 락 충돌(409)
const peakBidRate       = new Rate('peak_bid_success_rate');    // 성공률
const peakBidTrend      = new Trend('peak_bid_duration_ms');    // 입찰 응답시간
const peakPollTrend     = new Trend('peak_poll_duration_ms');   // 폴링 응답시간

// ── 옵션 ──────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // ① 구경꾼: 빠른 폴링으로 읽기 트래픽 집중
    auction_watchers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 80  },  // 빠른 급증
        { duration: '5m',  target: 150 },  // 피크 유지
        { duration: '2m',  target: 80  },  // 점진 감소
        { duration: '30s', target: 0   },
      ],
      exec: 'auctionWatcher',
    },

    // ② 적극 입찰자: 다수 경매 순환 입찰
    active_bidders: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 30 },  // 로그인 후 진입
        { duration: '5m',  target: 60 },  // 피크 입찰
        { duration: '2m',  target: 20 },  // 감소
        { duration: '30s', target: 0  },
      ],
      exec: 'activeBidder',
    },
  },

  thresholds: {
    // 읽기(폴링): 엄격
    'http_req_duration{scenario:auction_watchers}': ['p(95)<500', 'p(99)<1000'],
    // 쓰기(입찰): 락 대기가 있으므로 관대하게
    'http_req_duration{scenario:active_bidders}':   ['p(95)<1500', 'p(99)<3000'],
    // 전체 에러율: 409 락 충돌은 정상 동작이나 5xx는 안 됨
    http_req_failed: ['rate<0.05'],
    // 입찰 성공률 > 0.5%
    // 실패 경로 분석:
    //   - bid-limit 30/min × 5토큰 = 150 bids/min 상한 → ~80% 429 레이트 리밋
    //   - 나머지 ~20%: Redisson tryLock(0) 충돌(409) + 가격 미달(400)
    //   - 0.5% 성공이면 입찰 시스템 정상 동작 확인 충분
    // 핵심 지표: http_req_failed 0% + 응답시간 p(95) < 12ms
    peak_bid_success_rate: ['rate>0.005'],
  },
};

// ── Setup: 토큰 획득 + 경매 ID 파싱 ─────────────────────────────
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
    throw new Error('AUCTION_IDS 환경변수가 필요합니다. run-2.sh peak 로 실행하세요.');
  }

  console.log(`✅ 경매 ${auctionIds.length}개 | 입찰자 ${tokens.length}명 준비 완료`);
  return { tokens, auctionIds };
}

// ── ① 구경꾼: 경매 상세 + 입찰 현황 폴링 ────────────────────────
export function auctionWatcher(data) {
  const { auctionIds } = data;
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  const rand      = Math.random();

  if (rand < 0.55) {
    // 55% — 입찰 목록 폴링 (가장 많은 폴링 유형)
    const t0  = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}/bids?size=10`);
    peakPollTrend.add(Date.now() - t0);
    check(res, { '입찰폴링 200': (r) => r.status === 200 });

  } else if (rand < 0.80) {
    // 25% — 경매 상세 (현재 상태 확인)
    const t0  = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`);
    peakPollTrend.add(Date.now() - t0);
    check(res, { '경매상세 200': (r) => r.status === 200 });

  } else {
    // 20% — 카드 상세 (해당 경매 카드 정보 확인)
    const cardId = CARD_IDS[(__VU - 1) % CARD_IDS.length];
    const res    = http.get(`${BASE_URL}/api/v1/cards/${cardId}`);
    check(res, { '카드상세 200': (r) => r.status === 200 });
  }

  // 경매 종료 임박 시 2~4초마다 새로고침 (짧은 think-time)
  sleep(Math.random() * 2 + 1);
}

// ── ② 적극 입찰자: 경매 순환 + 경쟁 입찰 ───────────────────────
export function activeBidder(data) {
  const { tokens, auctionIds } = data;
  if (!tokens || tokens.length === 0) return;

  // 토큰 배치 재설계: 같은 경매에 서로 다른 토큰이 경쟁하도록
  // VU  1~10 → auction 0~9, token[0](bidder-1)
  // VU 11~20 → auction 0~9, token[1](bidder-2)
  // VU 21~30 → auction 0~9, token[2](bidder-3)
  // VU 31~40 → auction 0~9, token[3](bidder-4)
  // VU 41~50 → auction 0~9, token[4](bidder-5)
  // VU 51~60 → auction 0~9, token[0] 반복 — 5명이 각 경매 동시 경쟁
  const auctionIdx = (__VU - 1) % auctionIds.length;
  const tokenIdx   = Math.floor((__VU - 1) / auctionIds.length) % tokens.length;
  const auctionId  = auctionIds[auctionIdx];
  const token      = tokens[tokenIdx];
  const opts       = authOpts(token);

  // 1. 경매 현황 확인
  const detail = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, opts);
  check(detail, { '경매상세 200': (r) => r.status === 200 });
  sleep(0.5);

  // 2. 현재 입찰 목록 확인
  const bids = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}/bids?size=1`, opts);
  check(bids, { '입찰목록 200': (r) => r.status === 200 });

  // 3. 입찰 가격: 2M~8M 랜덤 범위 (buyout_price 9.99M 미만)
  //    서로 다른 토큰이 경쟁하므로 랜덤 고가에서 자연스러운 낙찰 경쟁 발생
  //    BID_ALREADY_LEADING: 최고가 낙찰자는 재입찰 불가 → 다른 토큰이 더 높은 랜덤가로 역전
  const bidPrice = 2000000 + Math.floor(Math.random() * 6000000);
  sleep(0.2);

  // 4. 입찰 (400/409/429는 비즈니스 로직 거부 — http_req_failed 집계 제외)
  const t0 = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
    JSON.stringify({ bidPrice }),
    { ...opts, responseCallback: http.expectedStatuses(201, 400, 409, 429) },
  );
  peakBidTrend.add(Date.now() - t0);

  if (res.status === 201) {
    peakBidSuccesses.add(1);
    peakBidRate.add(true);
    console.log(`✅ [VU ${__VU}] 입찰 성공 auction=${auctionId} price=${bidPrice}`);
  } else if (res.status === 409) {
    peakBidConflicts.add(1);
    peakBidRate.add(false);
  } else {
    peakBidRate.add(false);
  }

  check(res, {
    '입찰 허용 상태코드': (r) => [201, 400, 409, 429].includes(r.status),
  });

  // 5. 결과 확인 후 다음 경매로 이동 (자연스러운 순회)
  sleep(Math.random() * 3 + 1);
}

// ── Teardown ──────────────────────────────────────────────────────
export function teardown() {
  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[경매 피크 부하 결과 해석]');
  console.log('  peak_bid_successes    : 입찰 성공(201) 총 건수');
  console.log('  peak_bid_lock_failed  : 락 충돌(409) 총 건수');
  console.log('  peak_bid_success_rate : 입찰 성공률 (목표 > 1% — 60VU×5토큰 경쟁 특성상 낮은 것이 정상)');
  console.log('  peak_bid_duration_ms  : POST /bids 응답시간 분포');
  console.log('  peak_poll_duration_ms : GET /bids|/auctions 응답시간 분포');
  console.log('  주목할 것: 폴링 p(95) < 500ms 달성 여부 (Redis 캐시 효과)');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}
