// 공통 설정 — 모든 k6 스크립트가 공유한다.
// 환경변수로 주입: BASE_URL, TEST_EMAIL, TEST_PASSWORD, ALLOW_WRITE, INTERNAL_BASE_URL
//
// 예) k6 run -e BASE_URL=https://api.kuromi.click 01-load.js

// ── 대상 ──────────────────────────────────────────────
// ALB 퍼블릭 도메인(프론트가 통신하는 그 도메인). 끝 슬래시 없이.
export const BASE_URL = __ENV.BASE_URL || 'https://api.kuromi.click';

// 내부 테스트 시나리오 엔드포인트(/internal/test/*)는 보통 외부 미노출.
// 같은 VPC/배스천에서 돌릴 때만 사용. 미설정 시 BASE_URL 재사용.
export const INTERNAL_BASE_URL = __ENV.INTERNAL_BASE_URL || BASE_URL;

// 쓰기(write) 경로 활성화 플래그. 운영(prod) 직격 시 기본 OFF로 두어
// 실제 결제/입찰/DB 오염을 막는다. 스테이징에서만 -e ALLOW_WRITE=true.
export const ALLOW_WRITE = (__ENV.ALLOW_WRITE || 'false').toLowerCase() === 'true';

// ── 테스트 계정 ────────────────────────────────────────
// 사전 시드된 테스트 계정. 여러 VU가 같은 토큰을 써도 읽기엔 무방하나,
// 입찰 경합을 보려면 계정 풀(USERS_JSON)을 권장.
export const TEST_EMAIL = __ENV.TEST_EMAIL || 'test11@test.com';
export const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Chlwoals123!';

// 선택: 계정 풀을 JSON 배열로 주입 → VU별로 분산 로그인
//   -e USERS_JSON='[{"email":"u1@x.t","password":"P!1"},...]'
export function loadUserPool() {
  if (!__ENV.USERS_JSON) return null;
  try {
    const arr = JSON.parse(__ENV.USERS_JSON);
    return Array.isArray(arr) && arr.length ? arr : null;
  } catch (_) {
    return null;
  }
}

// ── SLO / Threshold ───────────────────────────────────
// 합격선. 실제 서비스 목표에 맞춰 조정할 것(아래는 보수적 기본값).
export const SLO = {
  read_p95_ms: 500, // 읽기 p95 목표
  read_p99_ms: 1000,
  write_p95_ms: 800, // 쓰기 p95 목표
  error_rate: 0.01, // 전체 에러율 1% 미만
  checks_rate: 0.99, // check 통과율 99% 이상
};

// 공통 threshold 블록(스크립트별로 펼쳐 사용)
export const commonThresholds = {
  http_req_failed: [`rate<${SLO.error_rate}`],
  checks: [`rate>${SLO.checks_rate}`],
  'http_req_duration{kind:read}': [`p(95)<${SLO.read_p95_ms}`, `p(99)<${SLO.read_p99_ms}`],
};

// HTTP 공통 파라미터(인증 헤더 + 태그)
export function authParams(token, tags = {}) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    tags,
  };
}

export function jsonHeaders() {
  return { headers: { 'Content-Type': 'application/json' } };
}
