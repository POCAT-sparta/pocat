/**
 * [Load] 카드 검색 부하 테스트 — ES + Redis
 *
 * 목표:
 *   - p(95) 응답시간 < 500ms (50 VU 피크 기준)
 *   - 에러율 < 1%
 *
 * 트래픽 패턴:
 *   30s  ramp-up  → 10 VU
 *   2min steady   → 30 VU  (일반 부하)
 *   1min peak     → 50 VU  (피크 부하)
 *   30s  ramp-down→  0 VU
 *
 * 실행:
 *   k6 run k6/scenarios/02-card-search-load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, thresholds } from '../helpers/config.js';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '2m',  target: 30 },
    { duration: '1m',  target: 50 },
    { duration: '30s', target:  0 },
  ],
  thresholds,
};

// 시드 데이터 기반 (docker/mysql/init/cards.sql)
const CARD_IDS = [1, 2, 3, 4, 5, 6, 7, 8];
const KEYWORDS = ['Butterfree', 'Paras', 'Pansage', 'Karrablast', 'Carnivine', 'Simisage'];
const RARITIES = ['Holo Rare V', 'Holo Rare VMAX', 'Common', 'Uncommon'];

export default function () {
  const rand = Math.random();

  if (rand < 0.45) {
    // 45% — 키워드 검색 (ES fulltext)
    const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(keyword)}`,
    );
    check(res, {
      '키워드검색 200':       (r) => r.status === 200,
      '키워드검색 data 존재': (r) => r.json('data') !== null,
    });

  } else if (rand < 0.70) {
    // 25% — 카드 상세 조회
    const cardId = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
    const res = http.get(`${BASE_URL}/api/v1/cards/${cardId}`);
    check(res, { '상세조회 200': (r) => r.status === 200 });

  } else if (rand < 0.85) {
    // 15% — 희귀도 필터 검색
    const rarity = RARITIES[Math.floor(Math.random() * RARITIES.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/cards?rarity=${encodeURIComponent(rarity)}`,
    );
    check(res, { '필터검색 200': (r) => r.status === 200 });

  } else {
    // 15% — 목록 (페이지 조회)
    const page = Math.floor(Math.random() * 3); // 0~2 페이지
    const res = http.get(`${BASE_URL}/api/v1/cards?page=${page}&size=20`);
    check(res, { '목록 200': (r) => r.status === 200 });
  }

  sleep(Math.random() * 1 + 0.3); // 0.3~1.3s 랜덤 think-time
}
