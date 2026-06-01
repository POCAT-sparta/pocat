/**
 * [Spike] 인기 카드 트래픽 스파이크 테스트
 *
 * 시나리오:
 *   카드가 이슈화되거나 인기 경매가 시작될 때 발생하는 급격한 트래픽 증가를 시뮬레이션.
 *   0 VU → 100 VU (5초 내 급증) → 30초 유지 → 종료
 *
 * 02 카드 검색 부하 테스트와의 차이:
 *   - 02: 점진적 램프업 (10→30→50 VU, 4분) — "일반 부하 수용 능력" 검증
 *   - 06: 급격한 스파이크 (0→100 VU, 5초) — "갑작스러운 폭증에 버티는지" 검증
 *
 * 검증:
 *   - 급격한 유입 시에도 p(95) 2000ms 이내 유지 여부
 *   - 에러율 1% 미만 유지 여부
 *
 * 실행:
 *   k6 run k6/scenarios/06-auction-spike.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../helpers/config.js';

const CARD_IDS = [1, 2, 3, 4, 5, 6, 7, 8];
const KEYWORDS  = ['Butterfree', 'Paras', 'Pansage', 'Karrablast', 'Carnivine'];

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      stages: [
        { duration: '5s',  target: 100 },  // 급격한 유입
        { duration: '30s', target: 100 },  // 피크 유지
        { duration: '5s',  target:   0 },  // 종료
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const rand = Math.random();

  if (rand < 0.60) {
    // 60% — 카드 키워드 검색 (ES fulltext)
    const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(keyword)}`,
    );
    check(res, { '키워드검색 200': (r) => r.status === 200 });

  } else {
    // 40% — 카드 평균가 조회 (Redis 캐시)
    const cardId = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
    const res = http.get(`${BASE_URL}/api/v1/cards/${cardId}/average-price`);
    check(res, { '평균가 200': (r) => r.status === 200 });
  }

  // 스파이크 특성상 짧은 think-time (실제 사용자가 페이지를 새로고침하는 상황)
  sleep(Math.random() * 0.5 + 0.1);
}
