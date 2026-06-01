/**
 * [Concurrency] 경매 입찰 동시성 테스트 — Redisson 분산 락 검증
 *
 * 시나리오:
 *   동일한 auctionId에 대해 N개의 VU가 동시에 POST /api/v1/auctions/{id}/bids 를 호출.
 *   Redisson tryLock(waitTime=0)이 올바르게 작동한다면:
 *     → 201 Created : 1건 (LEADING 입찰 1개 생성)
 *     → 409 BID_LOCK_FAILED : N-1건 (락 획득 실패, 즉시 반환)
 *
 *   결제 동시성(비관적 락)과의 차이:
 *     - 비관적 락: 락 대기 → 순서대로 처리 (느림)
 *     - Redisson tryLock(0): 락 못 잡으면 즉시 실패 (빠름)
 *
 * 사전 준비 (run.sh 참고):
 *   1) k6-bidder-1~5@test.com 계정 생성 + billing_key DB 삽입
 *   2) ACTIVE 상태 경매 DB 삽입 (setup/seed-bid-test-data.sql)
 *   3) AUCTION_ID 환경변수 설정
 *
 * 실행:
 *   k6 run -e AUCTION_ID=<경매ID> k6/scenarios/05-bid-concurrency.js
 *
 * 결과 해석:
 *   - concurrent_bid_successes == 1 → 동시성 제어 정상 ✅
 *   - concurrent_bid_successes  > 1 → 동시성 버그 ⚠️
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { login, authOpts } from '../helpers/auth.js';
import { BASE_URL } from '../helpers/config.js';

const BIDDER_COUNT   = 5;
const EMAIL_PREFIX   = 'k6-bidder-';
const PASSWORD       = 'Test1234!';
const BASE_BID_PRICE = 10000;

// 커스텀 메트릭
const concurrentBidSuccesses   = new Counter('concurrent_bid_successes');
const concurrentBidLockFailed  = new Counter('concurrent_bid_lock_failed');
const concurrentBidOtherFailed = new Counter('concurrent_bid_other_failed');

export const options = {
  scenarios: {
    bid_concurrency: {
      executor:    'shared-iterations',
      vus:         30,
      iterations:  30,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<1'],
  },
};

// ── setup: 5명 입찰자 로그인 → 토큰 배열 반환 ───────────────────
// 계정 생성은 run.sh setup_bid_test()의 curl에서 처리하므로 login만 수행.
// (signup을 여기서 재호출하면 IP 기준 5회/60초 레이트리밋에 걸려 429 반환)
export function setup() {
  const auctionId = __ENV.AUCTION_ID;
  if (!auctionId) {
    console.warn('⚠️  AUCTION_ID 미설정. setup/seed-bid-test-data.sql 실행 후 -e AUCTION_ID=<id> 로 전달하세요.');
  }

  const tokens = [];
  for (let i = 1; i <= BIDDER_COUNT; i++) {
    const email = `${EMAIL_PREFIX}${i}@test.com`;
    const token = login(email, PASSWORD);
    if (!token) {
      throw new Error(`로그인 실패: ${email} — run.sh로 실행했는지 확인하세요.`);
    }
    tokens.push(token);
  }

  return { tokens };
}

// ── 본 테스트: 30 VU가 동일 경매에 동시 입찰 ────────────────────
export default function (data) {
  const auctionId = __ENV.AUCTION_ID;
  if (!auctionId) {
    console.error('AUCTION_ID 가 설정되지 않아 테스트를 건너뜁니다.');
    return;
  }

  // VU마다 다른 입찰자 토큰 사용 (순환)
  const token    = data.tokens[(__VU - 1) % BIDDER_COUNT];
  // VU마다 다른 입찰가 (락 직렬화 검증 후 가격 충돌 방지)
  const bidPrice = BASE_BID_PRICE + __VU * 100;

  const res = http.post(
    `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
    JSON.stringify({ bidPrice }),
    authOpts(token),
  );

  check(res, {
    '입찰 성공(201) 또는 락실패(409) 또는 레이트리밋(429)': (r) =>
      r.status === 201 || r.status === 409 || r.status === 429,
  });

  if (res.status === 201) {
    concurrentBidSuccesses.add(1);
    console.log(`✅ [VU ${__VU}] 입찰 성공: bidPrice=${bidPrice}`);
  } else if (res.status === 409) {
    concurrentBidLockFailed.add(1);
    console.log(`🔒 [VU ${__VU}] 락 차단 (409): ${res.json('code')}`);
  } else {
    concurrentBidOtherFailed.add(1);
    console.log(`🚫 [VU ${__VU}] 기타 차단 (${res.status}): ${res.body}`);
  }

  sleep(0.1);
}

// ── teardown: 결과 출력 ──────────────────────────────────────────
export function teardown(data) {
  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[입찰 동시성 결과 해석]');
  console.log('  concurrent_bid_successes == 1 → Redisson 락 정상 ✅');
  console.log('  concurrent_bid_successes  > 1 → 동시성 버그 ⚠️');
  console.log('  concurrent_bid_lock_failed     → tryLock(0) 즉시 실패 건수');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}
