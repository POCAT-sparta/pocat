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
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL } from '../helpers/config.js';

// 커스텀 메트릭
const firstBatchDuration = new Trend('first_batch_duration_ms', true);
const steadyDuration     = new Trend('steady_duration_ms', true);
const requestCount       = new Counter('total_requests');

export const options = {
  scenarios: {
    stampede: {
      executor:         'ramping-arrival-rate',
      startRate:        1,
      timeUnit:         '1s',
      preAllocatedVUs:  70,
      maxVUs:           100,
      stages: [
        { duration: '5s',  target: 50 }, // 급격히 올려 캐시 미스 유발
        { duration: '30s', target: 50 }, // 캐시 warming 후 hit 안정화
        { duration: '5s',  target:  0 },
      ],
    },
  },
  thresholds: {
    http_req_failed:           ['rate<0.01'],
    http_req_duration:         ['p(95)<300'],
    // 캐시가 warming된 이후 구간 — 100ms 이내를 기대
    'steady_duration_ms':      ['p(95)<100'],
  },
};

// 테스트 시작 시각 기준으로 구간 분리
const START_TS = Date.now();
const WARM_AFTER_MS = 8000; // 8초 후부터 "캐시 히트 안정 구간"으로 간주

// 동일 카드에 집중해 스탬피드 유발
const CARD_ID = 1;

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/cards/${CARD_ID}/average-price`);

  check(res, { '평균가 200': (r) => r.status === 200 });
  requestCount.add(1);

  const elapsed = Date.now() - START_TS;
  if (elapsed < WARM_AFTER_MS) {
    firstBatchDuration.add(res.timings.duration);
  } else {
    steadyDuration.add(res.timings.duration);
  }
}
