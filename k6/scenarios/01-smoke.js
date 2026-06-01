/**
 * [Smoke] 전체 주요 엔드포인트 기본 동작 확인
 *
 * - VU 1개, 30s
 * - 에러 0%: 서버 기동 상태 확인용
 * - 대상: 카드 목록(ES), 카드 상세, 평균가(Redis)
 *
 * 실행:
 *   k6 run k6/scenarios/01-smoke.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../helpers/config.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed:   ['rate==0'],          // 에러 0%
    http_req_duration: ['p(95)<2000'],       // 2초 이내 (cold start 감안)
  },
};

const CARD_IDS = [1, 2, 3, 4, 5];
const KEYWORDS = ['Butterfree', 'Paras', 'Pansage'];

export default function () {
  const cardId  = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
  const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];

  // ── 카드 목록 (ES 검색) ─────────────────────────────────────────
  let res = http.get(`${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(keyword)}`);
  check(res, {
    '카드 목록 200':          (r) => r.status === 200,
    '카드 목록 data 존재':    (r) => r.json('data') !== null,
  });
  sleep(1);

  // ── 카드 상세 ───────────────────────────────────────────────────
  res = http.get(`${BASE_URL}/api/v1/cards/${cardId}`);
  check(res, {
    '카드 상세 200':    (r) => r.status === 200,
    '카드 id 일치':     (r) => r.json('data.id') === cardId,
  });
  sleep(0.5);

  // ── 평균가 (Redis 캐시) ─────────────────────────────────────────
  res = http.get(`${BASE_URL}/api/v1/cards/${cardId}/average-price`);
  check(res, {
    '평균가 200':    (r) => r.status === 200,
  });
  sleep(1);
}
