// 02. 지속성 테스트 (Soak / Endurance Test)
// 목적: 적당한 부하를 "장시간"(1~4시간+) 유지해 메모리 누수, 커넥션 풀 고갈,
//       캐시 증가, GC 악화, Redis/Kafka 적체 등 시간이 지나야 드러나는 문제 탐지.
// 기대: 시간이 지나도 응답시간·에러율이 평탄(drift 없음). 우상향하면 누수 의심.
//
// 실행(장시간이므로 같은 리전 EC2/배스천에서 nohup 권장):
//   k6 run -e BASE_URL=https://api.kuromi.click -e DURATION=2h 02-soak.js
import { commonThresholds } from './lib/config.js';
import { loginAllOnce } from './lib/auth.js';
import { userJourney, pickToken } from './lib/journey.js';

const DURATION = __ENV.DURATION
const VUS = parseInt(__ENV.VUS || '30', 10);

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION, // 중간 부하를 길게 유지
    },
  },
  thresholds: {
    ...commonThresholds,
    // 시간대별 drift를 보기 위해 요약 외 시계열(Grafana/Loki)과 함께 관찰.
  },
};

export function setup() {
  return { tokens: loginAllOnce() };
}

export default function (data) {
  userJourney(pickToken(data));
}
