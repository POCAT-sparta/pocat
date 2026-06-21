// 05. 브레이킹 포인트 테스트 (Breaking Point / Capacity Test)
// 목적: 부하(초당 요청 수)를 멈추지 않고 계속 끌어올려 시스템이 SLO를 위반하기
//       시작하는 정확한 지점(최대 처리량 RPS, 동시성)을 찾는다. = 용량 산정.
// 기대: 어느 RPS에서 p95가 무너지고/에러율이 치솟는지 곡선으로 확인.
//       그 직전 값이 "안전 운영 한계". autoscaling 임계·alert 기준 도출.
//
// 핵심: open model + abortOnFail. SLO 위반(에러율 폭증)이 명확해지면 자동 중단해
//       부하기/운영 서버를 불필요하게 더 두들기지 않는다.
//
// ⚠️ 반드시 같은 리전 EC2(또는 다중 인스턴스/Grafana Cloud k6)에서 실행.
//    가정용 회선에선 "서버 한계"가 아니라 "내 회선 한계"를 측정하게 됨.
//
// 실행:
//   k6 run -e BASE_URL=https://staging.kuromi.click 05-breakpoint.js
import { loginAllOnce } from './lib/auth.js';
import { userJourney, pickToken } from './lib/journey.js';

const MAX_RATE = parseInt(__ENV.MAX_RATE || '3000', 10); // 목표 상한 RPS

export const options = {
  scenarios: {
    breakpoint: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 5000,
      stages: [
        // 25분에 걸쳐 50 → MAX_RATE RPS까지 선형 증가
        { duration: '25m', target: MAX_RATE },
      ],
    },
  },
  thresholds: {
    // SLO가 명확히 무너지면 자동 중단(브레이킹 포인트 확정 후 더 안 두들김).
    http_req_failed: [{ threshold: 'rate<0.10', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{kind:read}': [
      { threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '30s' },
    ],
  },
};

export function setup() {
  return { tokens: loginAllOnce() };
}

export default function (data) {
  userJourney(pickToken(data));
}
