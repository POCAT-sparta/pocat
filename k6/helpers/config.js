/**
 * k6 공통 설정
 *
 * 환경변수로 덮어쓸 수 있다:
 *   k6 run -e BASE_URL=http://staging.example.com scenario.js
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/** 전체 시나리오에 공통으로 적용하는 임계치 */
export const thresholds = {
  http_req_duration: ['p(95)<500', 'p(99)<1000'],
  http_req_failed:   ['rate<0.01'],
};
