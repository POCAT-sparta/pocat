// 03. 스트레스 테스트 (Stress Test)
// 목적: 부하를 "기대치 이상"으로 단계적으로 끌어올려 시스템이 어느 지점부터
//       성능 저하(p95 상승, 에러 증가)되는지, 그리고 부하 제거 후 정상 복구되는지 확인.
// 기대: 한계 부근에서 응답시간 증가는 자연스러우나 5xx가 급증하면 안 됨.
//       부하 제거 후 빠르게 baseline 회복(복원력).
//
// 실행:
//   k6 run -e BASE_URL=https://api.kuromi.click 03-stress.js
import { SLO } from './lib/config.js';
import { loginAllOnce } from './lib/auth.js';
import { userJourney, pickToken } from './lib/journey.js';

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 }, // 정상 수준
        { duration: '3m', target: 200 }, // 정상의 2배
        { duration: '3m', target: 400 }, // 4배 — 한계 탐색
        { duration: '3m', target: 600 }, // 6배 — 과부하
        { duration: '3m', target: 0 }, // 부하 제거 → 복구 관찰
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // 스트레스는 "깨지는 걸 보는" 테스트라 임계는 abort가 아닌 관찰용.
    http_req_failed: [{ threshold: `rate<${SLO.error_rate}`, abortOnFail: false }],
    'http_req_duration{kind:read}': [{ threshold: 'p(95)<2000', abortOnFail: false }],
  },
};

export function setup() {
  return { tokens: loginAllOnce() };
}

export default function (data) {
  userJourney(pickToken(data));
}
