// 04. 스파이크 테스트 (Spike Test)
// 목적: 짧은 시간에 트래픽이 폭증(예: 인기 경매 마감 직전, 푸시 알림 직후)했다가
//       급감하는 상황에서 시스템이 견디는지, 자동확장/큐/커넥션풀이 버티는지 검증.
// 기대: 급증 순간 일시적 지연·소수 에러는 허용되나, 시스템이 죽지 않고
//       스파이크 종료 후 즉시 회복. 카스케이딩 실패(연쇄 장애) 없어야 함.
//
// 핵심: open model(arrival-rate)을 써야 한다. 응답이 느려져도 계속 도착시켜야
//       실제 스파이크처럼 큐가 쌓인다(closed VU 모델은 느려지면 도착이 줄어 왜곡).
//
// 실행(가정용 회선은 RPS 한계 → 가능하면 같은 리전 EC2):
//   k6 run -e BASE_URL=https://api.kuromi.click 04-spike.js
import { loginAllOnce } from './lib/auth.js';
import { userJourney, pickToken } from './lib/journey.js';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: 20, // 초당 20 iteration에서 시작
      timeUnit: '1s',
      preAllocatedVUs: 200, // 사전 할당(스파이크 순간 부족하지 않게)
      maxVUs: 2000,
      stages: [
        { duration: '1m', target: 20 }, // 평상시
        { duration: '10s', target: 1000 }, // 급증! 10초 만에 50배
        { duration: '1m', target: 1000 }, // 폭증 유지
        { duration: '10s', target: 20 }, // 급감
        { duration: '1m', target: 20 }, // 회복 관찰
      ],
    },
  },
  thresholds: {
    // 스파이크는 abort 없이 관찰. 회복력 판정은 시계열로.
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: false }],
  },
};

export function setup() {
  return { tokens: loginAllOnce() };
}

export default function (data) {
  userJourney(pickToken(data));
}
