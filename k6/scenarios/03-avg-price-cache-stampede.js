/**
 * [Concurrency] 평균가 Redis 캐시 스탬피드 테스트
 *
 * 검증 포인트:
 *   1. 캐시가 비어있을 때 동시 50 VU가 몰려도 DB 쿼리는 1회(또는 최소 횟수)만 실행되는가?
 *      → 스탬피드 보호 없으면: 초기 요청이 모두 DB를 직접 조회 (느림)
 *      → 스탬피드 보호 있으면: 1개만 DB 조회, 나머지는 캐시 히트 (빠름)
 *   2. 캐시 워밍 후 p(95) 응답시간이 뚜렷하게 낮아지는가?
 *
 * 테스트 전 캐시 초기화 방법 (선택):
 *   docker exec pocat-redis redis-cli DEL card:avgprice:1
 *
 * Grafana에서 확인:
 *   - JVM Heap, DB Connection Pool 사용량 (스탬피드 시 급등)
 *   - http_req_duration p50/p95 추이 (캐시 히트 후 급감)
 *
 * 실행:
 *   k6 run k6/scenarios/03-avg-price-cache-stampede.js
 */

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL } from '../helpers/config.js';

// 커스텀 메트릭
const firstBatchDuration = new Trend('first_batch_duration_ms', true);
const steadyDuration     = new Trend('steady_duration_ms', true);
const requestCount       = new Counter('total_requests');

export const options = {
  scenarios: {
    // Phase 1: 50 VU 동시 출발로 캐시 콜드 스탬피드 재현
    stampede: {
      executor:    'shared-iterations',
      vus:         50,
      iterations:  50,
      maxDuration: '10s',
    },
    // Phase 2: 캐시 워밍 후 안정 구간 측정
    steady: {
      executor:        'constant-arrival-rate',
      rate:            50,
      timeUnit:        '1s',
      duration:        '30s',
      preAllocatedVUs: 10,
      maxVUs:          20,
      startTime:       '12s',
    },
  },
  thresholds: {
    http_req_failed:       ['rate<0.01'],
    http_req_duration:     ['p(95)<300'],
    // 스탬피드 구간: 락 대기(최대 500ms) 안에서 처리되어야 함
    'first_batch_duration_ms': ['p(95)<500'],
    // 캐시 워밍 후 구간: 빠르게 응답해야 함
    'steady_duration_ms':  ['p(95)<100'],
  },
};

// 동일 카드에 집중해 스탬피드 유발
const CARD_ID = 1;

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/cards/${CARD_ID}/average-price`);

  check(res, { '평균가 200': (r) => r.status === 200 });
  requestCount.add(1);

  if (exec.scenario.name === 'stampede') {
    firstBatchDuration.add(res.timings.duration);
  } else {
    steadyDuration.add(res.timings.duration);
  }
}
