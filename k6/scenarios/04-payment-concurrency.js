/**
 * [Concurrency] 결제 생성 동시성 테스트 — 비관적 락 + 중복 결제 방지 검증
 *
 * 시나리오:
 *   동일한 orderId에 대해 N개의 VU가 동시에 POST /api/v1/payments 를 호출.
 *   비관적 락(findByOrderIdWithLock)이 올바르게 작동한다면:
 *     → 201 Created : 1건 (PENDING 결제 1개 생성)
 *     → 4xx         : N-1건 (락 대기 후 상태 재확인 → 이미 결제 진행 중 감지)
 *
 *   ※ 현재 구현상 generatePayment()는 주문 상태를 변경하지 않으므로
 *     락 해제 후 후속 VU도 201을 받을 수 있음 (다중 PENDING 생성 가능성).
 *     이를 동시성 테스트로 실증하여 개선 여부를 판단한다.
 *
 * 사전 준비 (run.sh 참고):
 *   1) k6-buyer@test.com 계정 생성 (signup API 또는 DB 직접 삽입)
 *   2) AUTO_PAYMENT_FAILED 상태 주문 DB 삽입 (setup/seed-test-data.sql 참고)
 *   3) ORDER_ID 환경변수 설정
 *
 * 실행:
 *   k6 run -e ORDER_ID=<주문ID> k6/scenarios/04-payment-concurrency.js
 *
 * 결과 해석:
 *   - http_req_duration{status:201} ── 최초 성공 응답시간
 *   - concurrent_successes           ── 동시 201 건수 (1이어야 정상)
 *   - concurrent_failures            ── 중복 방지로 인한 실패 건수
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { login, authOpts } from '../helpers/auth.js';
import { BASE_URL } from '../helpers/config.js';

const TEST_EMAIL    = 'k6-buyer@test.com';
const TEST_PASSWORD = 'Test1234!';
const TEST_NICKNAME = 'k6buyer';

// 커스텀 메트릭 — 동시 성공/실패 건수 카운팅
const concurrentSuccesses = new Counter('concurrent_successes');
const concurrentFailures  = new Counter('concurrent_failures');

export const options = {
  scenarios: {
    concurrency: {
      executor:    'shared-iterations',
      vus:         30,
      iterations:  30,        // VU당 1번 — 30개 동시 요청
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<1'], // 이 테스트에선 4xx가 발생해도 허용
    // 기대: concurrent_successes == 1 (동시성 버그 있으면 > 1)
  },
};

// ── setup: 로그인하여 token 획득 ────────────────────────────────
// 계정 생성은 run.sh setup_payment_test()의 curl에서 처리하므로 login만 수행.
// (signup을 여기서 재호출하면 IP 기준 5회/60초 레이트리밋에 걸려 429 반환)
export function setup() {
  const orderId = __ENV.ORDER_ID;
  if (!orderId) {
    console.warn('⚠️  ORDER_ID 미설정. setup/seed-test-data.sql 실행 후 -e ORDER_ID=<id> 로 전달하세요.');
  }

  const token = login(TEST_EMAIL, TEST_PASSWORD);

  if (!token) {
    throw new Error('로그인 실패: 토큰을 받지 못했습니다.');
  }

  return { token };
}

// ── 본 테스트: 동일 orderId에 동시 결제 요청 ────────────────────
export default function (data) {
  const orderId = __ENV.ORDER_ID;
  if (!orderId) {
    console.error('ORDER_ID 가 설정되지 않아 테스트를 건너뜁니다.');
    return;
  }

  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({ orderId: Number(orderId) }),
    authOpts(data.token),
  );

  const succeeded = check(res, {
    '결제 생성 201 또는 중복차단 4xx': (r) => r.status === 201 || r.status >= 400,
  });

  if (res.status === 201) {
    concurrentSuccesses.add(1);
    console.log(`✅ [VU ${__VU}] 결제 생성 성공: ${res.json('data.paymentUid')}`);
  } else {
    concurrentFailures.add(1);
    console.log(`🚫 [VU ${__VU}] 결제 차단 (${res.status}): ${res.body}`);
  }

  sleep(0.1);
}

// ── teardown: 테스트 후 상태 출력 ───────────────────────────────
export function teardown(data) {
  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[동시성 결과 해석]');
  console.log('  concurrent_successes == 1 → 중복 결제 방지 ✅');
  console.log('  concurrent_successes  > 1 → 동시성 버그 감지 ⚠️ (다중 PENDING 결제 생성됨)');
  console.log('');
  console.log('  재실행 전 아래 SQL로 데이터 초기화:');
  console.log('  > docker exec -i pocat-db mysql -uroot -p${DB_PASSWORD} pocat < k6/setup/reset-order.sql');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}
