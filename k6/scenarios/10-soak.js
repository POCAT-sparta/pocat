/**
 * [Soak] 장기 내구성 테스트 — 메모리 누수 · 커넥션 풀 고갈 · 응답시간 드리프트 감지
 *
 * 목적:
 *   단기 부하 테스트에서는 드러나지 않는 장시간 운영 문제를 탐지한다.
 *   - HikariCP 커넥션 풀 고갈 (DB 연결 반환 누락)
 *   - Redisson/Redis 커넥션 누수
 *   - Spring Heap 증가 (GC 압박 → 점진적 응답시간 상승)
 *   - Logback AsyncAppender 큐 포화 (로그 손실)
 *   - Kafka 컨슈머 lag 축적 (Outbox 처리 지연)
 *
 * 시나리오 구성 (75 VU 상시 유지):
 *   ① public_endurance  (50 VU) — 비인증 브라우징
 *       카드 검색, 경매 목록, 인기 경매, 평균가 등 반복
 *
 *   ② auth_endurance (25 VU) — 인증 유저 세션 유지
 *       입찰 확인, 알림 조회, 내 입찰 내역 등 반복
 *
 * 소요 시간: 기본 20분 (SOAK_DURATION 환경변수로 조정 가능)
 *   예: k6 run -e SOAK_DURATION=45m k6/scenarios/10-soak.js
 *
 * 임계치: 단기 테스트보다 관대 (p95 < 1000ms)
 *   → 20분 후에도 초반과 같은 응답시간 유지 여부를 k6 output CSV / Grafana로 확인
 *
 * 실행:
 *   ./k6/run-2.sh soak
 *   또는 직접: k6 run -e AUCTION_IDS=<ids> [-e SOAK_DURATION=30m] k6/scenarios/10-soak.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { login, authOpts } from '../helpers/auth.js';
import { BASE_URL } from '../helpers/config.js';

// ── 상수 ──────────────────────────────────────────────────────────
const BIDDER_COUNT = 5;
const PASSWORD     = 'Test1234!';
const CARD_IDS     = [1, 2, 3, 4, 5, 6, 7, 8];
const KEYWORDS     = ['Butterfree', 'Paras', 'Pansage', 'Karrablast', 'Carnivine', 'Simisage'];
const RARITIES     = ['Holo Rare V', 'Holo Rare VMAX', 'Common', 'Uncommon'];

// SOAK_DURATION: 기본 20분, 환경변수로 덮어쓰기 가능
const DURATION = __ENV.SOAK_DURATION || '20m';

// ── 커스텀 메트릭 ─────────────────────────────────────────────────
// 드리프트 탐지: 5xx 에러가 시간이 지남에 따라 늘어나는지 확인
const soak5xxCounter    = new Counter('soak_5xx_errors');
const soakPublicTrend   = new Trend('soak_public_duration_ms');
const soakAuthTrend     = new Trend('soak_auth_duration_ms');

// ── 옵션 ──────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // ① 비인증 공개 트래픽 — 일정하게 유지
    public_endurance: {
      executor: 'constant-vus',
      vus:      50,
      duration: DURATION,
      exec:     'publicTraffic',
    },

    // ② 인증 트래픽 — 세션 유지형 유저
    auth_endurance: {
      executor: 'constant-vus',
      vus:      25,
      duration: DURATION,
      exec:     'authTraffic',
    },
  },

  // 장기 테스트는 단기보다 관대한 임계치 사용
  // 실제 드리프트는 Grafana / CSV output으로 시각화해서 확인할 것
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2500'],
    http_req_failed:   ['rate<0.02'],
    soak_5xx_errors:   ['count<10'],  // 20분간 5xx 10건 미만
    'http_req_duration{scenario:public_endurance}': ['p(95)<800'],
    'http_req_duration{scenario:auth_endurance}':   ['p(95)<1200'],
  },
};

// ── Setup ─────────────────────────────────────────────────────────
export function setup() {
  const tokens = [];
  for (let i = 1; i <= BIDDER_COUNT; i++) {
    const token = login(`k6-bidder-${i}@test.com`, PASSWORD);
    if (!token) throw new Error(`로그인 실패: k6-bidder-${i}@test.com`);
    tokens.push(token);
  }

  const auctionIds = (__ENV.AUCTION_IDS || '')
    .split(',')
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !isNaN(n));

  if (auctionIds.length === 0) {
    console.warn('⚠️  AUCTION_IDS 미설정 — 입찰 없이 조회 중심으로 실행됩니다.');
  }

  console.log(`🕐 Soak 테스트 시작: ${DURATION} 동안 75 VU 상시 유지`);
  console.log('   응답시간이 초반 대비 점진 상승하면 커넥션 풀 또는 메모리 누수 의심');
  return { tokens, auctionIds };
}

// ── ① 비인증 공개 트래픽 ─────────────────────────────────────────
// 주의: GET /api/v1/auctions (목록)은 IP 기반 rate-limit (30/min).
//       로컬 k6에서 50 VU가 localhost IP를 공유 → 거의 모든 요청이 429.
//       → data.auctionIds 로 직접 상세 조회 (rate-limit 없음).
export function publicTraffic(data) {
  const rand = Math.random();
  const t0   = Date.now();
  let res;

  if (rand < 0.30) {
    // 30% — 키워드 검색 (ES 쿼리)
    const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    res = http.get(`${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(kw)}`);
    check(res, { '검색 200': (r) => r.status === 200 });

  } else if (rand < 0.50) {
    // 20% — 특정 경매 상세 (rate-limit 없는 공개 엔드포인트)
    // GET /api/v1/auctions 목록은 IP rate-limit(30/min)으로 로컬 75 VU 실행 시 거의 전부 429
    if (data && data.auctionIds && data.auctionIds.length > 0) {
      const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];
      res = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`);
      check(res, { '경매상세(공개) 200': (r) => r.status === 200 });
    } else {
      res = http.get(`${BASE_URL}/api/v1/auctions/popular`);
      check(res, { '인기경매 200': (r) => r.status === 200 });
    }

  } else if (rand < 0.65) {
    // 15% — 인기 경매
    res = http.get(`${BASE_URL}/api/v1/auctions/popular`);
    check(res, { '인기경매 200': (r) => r.status === 200 });

  } else if (rand < 0.78) {
    // 13% — 카드 상세
    const id = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
    res = http.get(`${BASE_URL}/api/v1/cards/${id}`);
    check(res, { '카드상세 200': (r) => r.status === 200 });

  } else if (rand < 0.90) {
    // 12% — 평균가 (Redis 캐시)
    const id = CARD_IDS[Math.floor(Math.random() * CARD_IDS.length)];
    res = http.get(`${BASE_URL}/api/v1/cards/${id}/average-price`);
    check(res, { '평균가 200': (r) => r.status === 200 });

  } else {
    // 10% — 희귀도 필터
    const rarity = RARITIES[Math.floor(Math.random() * RARITIES.length)];
    res = http.get(`${BASE_URL}/api/v1/cards?rarity=${encodeURIComponent(rarity)}`);
    check(res, { '필터검색 200': (r) => r.status === 200 });
  }

  soakPublicTrend.add(Date.now() - t0);

  // 5xx 에러 카운팅 (드리프트 신호)
  if (res && res.status >= 500) soak5xxCounter.add(1);

  sleep(Math.random() * 2 + 1); // 1~3s think-time
}

// ── ② 인증 유저 트래픽 ───────────────────────────────────────────
export function authTraffic(data) {
  if (!data.tokens || data.tokens.length === 0) return;

  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const opts  = authOpts(token);
  const rand  = Math.random();
  const t0    = Date.now();
  let res;

  if (rand < 0.25 && data.auctionIds.length > 0) {
    // 25% — 경매 상세 + 입찰 시도 (가벼운 버전: 입찰 성공 여부 무관)
    const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];
    http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, opts);
    sleep(0.5);

    // 입찰 가격: 2M~8M 랜덤 (buyout_price 9.99M 미만)
    // 400/409/429는 비즈니스 로직 거부 — http_req_failed 집계 제외
    const bidPrice = 2000000 + Math.floor(Math.random() * 6000000);
    res = http.post(
      `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
      JSON.stringify({ bidPrice }),
      { ...opts, responseCallback: http.expectedStatuses(201, 400, 409, 429) },
    );
    check(res, { '입찰 허용': (r) => [201, 400, 409, 429].includes(r.status) });

  } else if (rand < 0.45) {
    // 20% — 알림 조회
    res = http.get(`${BASE_URL}/api/v1/notifications`, opts);
    check(res, { '알림 200': (r) => r.status === 200 });

  } else if (rand < 0.62) {
    // 17% — 내 입찰 내역
    res = http.get(`${BASE_URL}/api/v1/bids/me`, opts);
    check(res, { '내입찰 200': (r) => r.status === 200 });

  } else if (rand < 0.77) {
    // 15% — 내 경매
    res = http.get(`${BASE_URL}/api/v1/auctions/me`, opts);
    check(res, { '내경매 200': (r) => r.status === 200 });

  } else if (rand < 0.90) {
    // 13% — 카드 검색 (인증 유저)
    const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    res = http.get(`${BASE_URL}/api/v1/cards?keyword=${encodeURIComponent(kw)}`, opts);
    check(res, { '검색 200': (r) => r.status === 200 });

  } else {
    // 10% — 내 주문 조회
    res = http.get(`${BASE_URL}/api/v1/orders/me`, opts);
    check(res, { '내주문 200': (r) => r.status === 200 });
  }

  soakAuthTrend.add(Date.now() - t0);
  if (res && res.status >= 500) soak5xxCounter.add(1);

  sleep(Math.random() * 3 + 1.5); // 1.5~4.5s think-time (장시간 세션 자연스러운 페이스)
}

// ── Teardown ──────────────────────────────────────────────────────
export function teardown() {
  console.log('');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[Soak 내구성 테스트 결과 해석]');
  console.log('  soak_5xx_errors       : 5xx 에러 건수 (< 10 목표)');
  console.log('  soak_public_duration_ms : 공개 API 응답시간 분포');
  console.log('  soak_auth_duration_ms   : 인증 API 응답시간 분포');
  console.log('');
  console.log('  ⚠️  드리프트 확인 방법:');
  console.log('     k6 run --out csv=result.csv 로 실행 후');
  console.log('     result.csv 에서 http_req_duration 를 시간순으로 그래프화');
  console.log('     초반 5분 대비 후반 5분 p95 가 50% 이상 증가 → 누수 의심');
  console.log('');
  console.log('  🔍 Grafana 연동 시: k6 run --out influxdb=http://localhost:8086/k6');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}
