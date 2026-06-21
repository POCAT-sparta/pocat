// 공유 유저 여정 — 부하/스트레스/스파이크/지속성이 모두 재사용한다.
//
// 설계 원칙(rate-limit 회피 + 토큰 수명 관리):
//   1) 로그인은 setup()에서 "1회"만 수행하고 토큰을 전 VU가 재활용한다.
//   2) 호출 API는 rate-limit이 걸리지 않는 "읽기" 엔드포인트만 사용.
//      - 회피: GET /v1/auctions(검색), POST 계열(auction/bid/card)
//      - 사용: popular / 경매상세 / 입찰이력 / 카드목록 / 카드상세 / 평균가
//   3) access token TTL은 1시간. 만료(401) 시 reissue로 토큰을 재설정 후 1회 재시도.
//      ⚠️ reissue는 1회용 회전 토큰 + IP 5회/60초 제한 → "1 계정 = 1 VU"일 때만 안전
//         (USERS_JSON 풀, VU ≤ 계정 수). 단일 계정 공유 + 1시간 초과 테스트는 주의.
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL, ALLOW_WRITE, authParams } from './config.js';
import { reissue } from './auth.js';

// 커스텀 지표
export const bidTrend = new Trend('bid_duration', true);
export const bidConflicts = new Counter('bid_conflicts'); // 동시 입찰 경합(409 등)
export const reissues = new Counter('token_reissues'); // 토큰 재발급 횟수

// 401 시 자동 reissue 사용 여부. 끄려면 -e AUTO_REISSUE=false
const AUTO_REISSUE = (__ENV.AUTO_REISSUE || 'true') !== 'false';

// VU별 현재 토큰 쌍(module 스코프 = VU마다 독립, iteration 간 유지).
// reissue로 갱신되면 같은 VU의 다음 요청부터 새 토큰을 쓴다.
let vuAuth = null;

// setup()이 만든 토큰 쌍 배열에서 이 VU가 쓸 쌍을 고른다.
export function pickToken(data) {
  const tokens = (data && data.tokens) || [];
  if (!tokens.length) return null;
  return tokens[(__VU - 1) % tokens.length];
}

// 인증 GET — 401이면 reissue로 토큰을 재설정하고 1회 재시도.
function authedGet(url, name) {
  let res = http.get(url, { ...authParams(vuAuth.accessToken, { name, kind: 'read' }) });
  if (res.status === 401 && AUTO_REISSUE && vuAuth.refreshToken) {
    const next = reissue(vuAuth.refreshToken);
    if (next) {
      vuAuth = next; // 토큰 재설정(같은 VU의 이후 요청도 새 토큰 사용)
      reissues.add(1);
      res = http.get(url, { ...authParams(vuAuth.accessToken, { name, kind: 'read' }) });
    }
  }
  return res;
}

// 인기 경매(rate-limit 없음)에서 auctionId / cardId 후보를 뽑는다.
function pickIdsFromPopular() {
  const res = authedGet(`${BASE_URL}/api/v1/auctions/popular?size=20`, 'GET /auctions/popular');
  check(res, { 'popular 200': (r) => r.status === 200 });
  try {
    const list = res.json('data');
    if (Array.isArray(list) && list.length) {
      const item = list[Math.floor(Math.random() * list.length)];
      return { auctionId: item.auctionId || null, cardId: item.cardId || null };
    }
  } catch (_) {
    /* noop */
  }
  return { auctionId: null, cardId: null };
}

// 카드 목록(rate-limit 없음)에서 cardId 후보를 뽑는다.
function pickCardId() {
  const res = authedGet(`${BASE_URL}/api/v1/cards?page=0&size=20`, 'GET /cards');
  check(res, { 'cards list 200': (r) => r.status === 200 });
  try {
    const content = res.json('data.content');
    if (Array.isArray(content) && content.length) {
      return content[Math.floor(Math.random() * content.length)].id || null;
    }
  } catch (_) {
    /* noop */
  }
  return null;
}

// 한 번의 사용자 여정 — 전부 rate-limit 없는 읽기 API.
//   auth: setup()에서 발급한 { accessToken, refreshToken } 쌍.
export function userJourney(auth) {
  if (!vuAuth) vuAuth = auth; // VU 최초 진입 시 초기화(이후엔 reissue 갱신분 유지)
  if (!vuAuth || !vuAuth.accessToken) {
    sleep(1);
    return;
  }

  let auctionId = null;
  let cardId = null;

  group('auctions (read)', () => {
    const ids = pickIdsFromPopular(); // GET /auctions/popular
    auctionId = ids.auctionId;
    cardId = ids.cardId;

    if (auctionId) {
      const detail = authedGet(`${BASE_URL}/api/v1/auctions/${auctionId}`, 'GET /auctions/{id}');
      check(detail, { 'auction detail 200': (r) => r.status === 200 });

      const bids = authedGet(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        'GET /auctions/{id}/bids'
      );
      check(bids, { 'bid history 200': (r) => r.status === 200 });
    }
  });

  group('cards (read)', () => {
    if (!cardId) cardId = pickCardId(); // GET /cards

    if (cardId) {
      const card = authedGet(`${BASE_URL}/api/v1/cards/${cardId}`, 'GET /cards/{id}');
      check(card, { 'card detail 200': (r) => r.status === 200 });

      const avg = authedGet(
        `${BASE_URL}/api/v1/cards/${cardId}/average-price`,
        'GET /cards/{id}/average-price'
      );
      check(avg, { 'avg price 200': (r) => r.status === 200 });
    }
  });

  // 쓰기(입찰)는 서버에서 rate-limit(rate:user:bid)이 걸린다. 기본 비활성.
  if (ALLOW_WRITE && auctionId) {
    group('bid (write, rate-limited)', () => {
      const bidPrice = 1000 + Math.floor(Math.random() * 100000);
      const res = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        JSON.stringify({ bidPrice }),
        { ...authParams(vuAuth.accessToken, { name: 'POST /auctions/{id}/bids', kind: 'write' }) }
      );
      bidTrend.add(res.timings.duration);
      check(res, {
        'bid handled (200/201/400/409/429)': (r) => [200, 201, 400, 409, 429].includes(r.status),
      });
      if (res.status === 409) bidConflicts.add(1);
    });
  }

  // think time — 실제 유저처럼 1~3초 휴식
  sleep(Math.random() * 2 + 1);
}
