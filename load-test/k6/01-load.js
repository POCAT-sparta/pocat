// 01. 부하 테스트 (Load Test) — 1시간 기준
// 목적: "기대 동시 사용자" 수준의 부하를 1시간 동안 유지해, 평상시 운영 부하에서
//       응답시간·에러율이 SLO를 안정적으로 만족하는지 검증. 다른 테스트의 baseline.
// 기대: 워밍업 후 안정 구간에서 읽기 p95 < 500ms, 에러율 < 1%가 1시간 내내 평탄.
//
// 구성(총 60분): 5분 ramp-up → 50분 유지 → 5분 ramp-down
//
// 실행:
//   k6 run -e BASE_URL=https://api.kuromi.click 01-load.js
//   (동접 조정) k6 run -e BASE_URL=... -e TARGET_VUS=150 01-load.js
//   (쓰기 포함 스테이징) k6 run -e BASE_URL=... -e ALLOW_WRITE=true 01-load.js
import { commonThresholds, SLO } from './lib/config.js';
import { loginAllOnce } from './lib/auth.js';
import { userJourney, pickToken } from './lib/journey.js';

const TARGET_VUS = parseInt(__ENV.TARGET_VUS || '100', 10); // 기대 동시 사용자

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5m', target: TARGET_VUS }, // 워밍업
        { duration: '50m', target: TARGET_VUS }, // 기대 부하 유지 (1시간 기준)
        { duration: '5m', target: 0 }, // 쿨다운
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    ...commonThresholds,
    'http_req_duration{kind:write}': [`p(95)<${SLO.write_p95_ms}`],
  },
};

// 부하 시작 전 1회만 로그인 → 토큰을 전 VU가 재활용.
export function setup() {
  return { tokens: loginAllOnce() };
}

export default function (data) {
  userJourney(pickToken(data));
}
