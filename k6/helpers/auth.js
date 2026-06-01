/**
 * 인증 헬퍼
 *
 * signup()  - 회원가입 (이미 존재하면 409 무시)
 * login()   - 로그인 후 accessToken 반환
 * authOpts() - Authorization 헤더가 포함된 k6 params 반환
 */

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function signup(email, password, nickname) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password, nickname }),
    { headers: JSON_HEADERS },
  );
  // 201(생성) 또는 409(이미 존재)는 모두 정상
  const ok = check(res, {
    '회원가입 성공(201) 또는 중복(409)': (r) => r.status === 201 || r.status === 409,
  });
  return ok;
}

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS },
  );
  check(res, { '로그인 200': (r) => r.status === 200 });

  const body = res.json();
  return body.data?.accessToken ?? null;
}

/** k6 params 형태로 Authorization 헤더 반환 */
export function authOpts(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  };
}
