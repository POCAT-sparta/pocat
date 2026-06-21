// 인증 헬퍼 — POCAT 로그인 후 accessToken 획득.
// 응답 래퍼: { success, status, data: { accessToken, refreshToken } }
//
// ⚠️ 로그인은 setup()에서 loginAllOnce()로 "1회"만 수행한다.
//    (매 iteration/VU 로그인은 로그인 rate-limit에 걸림 → 모든 VU가 토큰 재활용)
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, TEST_EMAIL, TEST_PASSWORD, loadUserPool, jsonHeaders } from './config.js';

// 로그인 → { accessToken, refreshToken }. 실패 시 null.
export function login(user) {
  const u = user || { email: TEST_EMAIL, password: TEST_PASSWORD };
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: u.email, password: u.password }),
    { ...jsonHeaders(), tags: { name: 'POST /auth/login', kind: 'auth' } }
  );
  const ok = check(res, {
    'login 200': (r) => r.status === 200,
    'login has accessToken': (r) => {
      try {
        return !!r.json('data.accessToken');
      } catch (_) {
        return false;
      }
    },
  });
  if (!ok) {
    // 진단용: 실제 status/본문을 찍는다. 끄려면 -e LOGIN_DEBUG=false
    if ((__ENV.LOGIN_DEBUG || 'true') !== 'false') {
      console.error(
        `[login FAIL] email=${u.email} status=${res.status} body=${String(res.body).slice(0, 200)}`
      );
    }
    return null;
  }
  return { accessToken: res.json('data.accessToken'), refreshToken: res.json('data.refreshToken') };
}

// refresh 토큰으로 새 토큰 쌍 발급 → { accessToken, refreshToken }. 실패 시 null.
//
// ⚠️ 서버 정책 주의(AuthService.reissue):
//   - refresh 토큰은 1회용 + 회전(rotation): reissue 시 옛 토큰 삭제 후 새로 발급.
//     → 같은 refresh 토큰을 두 번 쓰면 INVALID_REFRESH_TOKEN(+탈취 감지로 체인 삭제).
//   - reissue는 IP당 5회/60초 제한(rate:ip:reissue).
//   따라서 "1 계정 = 1 VU"(USERS_JSON 풀, VU ≤ 계정 수)일 때만 안전하게 동작한다.
//   단일 계정을 여러 VU가 공유하면 만료 시 한 VU만 성공하고 나머지는 실패한다.
export function reissue(refreshToken) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/reissue`,
    JSON.stringify({ refreshToken }),
    { ...jsonHeaders(), tags: { name: 'POST /auth/reissue', kind: 'auth' } }
  );
  if (res.status === 200) {
    try {
      const next = {
        accessToken: res.json('data.accessToken'),
        refreshToken: res.json('data.refreshToken'),
      };
      if (next.accessToken) return next;
    } catch (_) {
      /* fallthrough */
    }
  }
  if ((__ENV.LOGIN_DEBUG || 'true') !== 'false') {
    console.error(`[reissue FAIL] status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
  return null;
}

// 부하 시작 전 setup()에서 "1회"만 호출 — 토큰 쌍 배열을 만든다.
// 계정 풀(USERS_JSON)이 있으면 각 계정을 1회씩 로그인, 없으면 단일 계정 1회.
// 모든 VU는 이 토큰들을 재활용하므로 로그인은 풀 크기만큼만 발생한다.
export function loginAllOnce() {
  const pool = loadUserPool() || [{ email: TEST_EMAIL, password: TEST_PASSWORD }];
  const tokens = [];
  for (const u of pool) {
    const pair = login(u);
    if (pair) tokens.push(pair);
  }
  if (!tokens.length) {
    throw new Error(
      '[setup] 로그인 실패 — 발급된 토큰 0개. TEST_EMAIL/TEST_PASSWORD 또는 USERS_JSON, BASE_URL을 확인하세요.'
    );
  }
  console.log(`[setup] 로그인 완료 — 토큰 ${tokens.length}개 발급(전 VU 재활용).`);
  return tokens;
}

// 선택: 부하 전 테스트 계정 자동 생성(스테이징 전용).
// 운영에선 절대 쓰지 말 것 — 실제 유저 테이블 오염.
export function signup(email, password, nickname) {
  return http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password, nickname, phone: '01000000000' }),
    { ...jsonHeaders(), tags: { name: 'POST /auth/signup', kind: 'auth' } }
  );
}
