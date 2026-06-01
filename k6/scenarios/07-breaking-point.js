/**
 * [Stress] 카드 검색 한계 처리량 탐색 (Breaking Point Test)
 *
 * 시나리오:
 *   VU를 250 → 500 → 750 → 1000 → 1500 단계적으로 증가시켜
 *   에러율이 1%를 초과하거나 p(95)가 1000ms를 초과하는 한계 처리량을 찾는다.
 *
 *   이전(250 VU)과의 차이:
 *     - VU 6배 확대 (250 → 1500)
 *     - think-time 단축 (0.3~1.3s → 0.05~0.3s)
 *       → 실제 동시 처리 요청 수 대폭 증가
 *
 * 결과 해석:
 *   ✅ 에러율 0% + p(95) < 1000ms 구간 → 안정 운영 범위
 *   ⚠️  p(95) 급증 시작 구간             → 성능 저하 시작점 (임계값)
 *   ❌ 에러율 > 1% 구간                  → 한계 초과 (Breaking Point)
 *
 * ⚠️  주의:
 *   이 테스트는 서버를 한계까지 밀어붙이므로 단독 실행 권장 (run.sh all 제외).
 *   Threshold 실패는 예상된 결과. Breaking Point가 낮게 나올수록 개선 여지가 있음.
 *
 * 실행:
 *   k6 run k6/scenarios/07-breaking-point.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../helpers/config.js';

const CARD_IDS = [1, 2, 3, 4, 5, 6, 7, 8];
const KEYWORDS  = ['Butterfree', 'Paras', 'Pansage', 'Karrablast', 'Carnivine', 'Simisage'];
const RARITIES  = ['Holo Rare V', 'Holo Rare VMAX', 'Common', 'Uncommon'];

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m',  target:  1500 },  // 이전 최대치 — 기준선 재확인 (p95=744ms)
        { duration: '1m',  target:  2000 },
        { duration: '1m',  target:  2500 },
        { duration: '1m',  target:  3000 },
        { duration: '30s', target:     0 },  // ramp-down
      ],
    },
  },
  thresholds: {
    // 한계 탐색 목적 — 높은 VU 단계에서 초과 예상
    http_req_duration: ['p(95)<1000'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const rand = Math.random();

  if (rand < 0.45) {
    // 45% — 키워드 검색 (ES fulltext)
    const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(keyword)}`,
    );
    check(res, { '키워드검색 200': (r) => r.status === 200 });

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
    const page = Math.floor(Math.random() * 3);
    const res = http.get(`${BASE_URL}/api/v1/cards?page=${page}&size=20`);
    check(res, { '목록 200': (r) => r.status === 200 });
  }

  // think-time 단축: 0.05~0.3s
  // (이전 0.3~1.3s → 실제 동시 처리가 ~1개에 불과했음)
  // 250 VU × (3ms 응답 / 175ms 평균 주기) ≈ 4 req 동시처리
  // 1500 VU × (3ms 응답 / 175ms 평균 주기) ≈ 26 req 동시처리
  sleep(Math.random() * 0.25 + 0.05);
}
