#!/usr/bin/env bash
# ============================================================
# pocat k6 테스트 실행 스크립트
#
# 사전 조건:
#   - Docker Compose 실행 중 (docker compose up -d)
#   - k6 설치됨 (brew install k6 / choco install k6)
#
# 사용법:
#   ./k6/run.sh [scenario]
#
# scenario 옵션:
#   smoke    — 01 스모크 테스트
#   search   — 02 카드 검색 부하
#   cache    — 03 평균가 캐시 스탬피드
#   payment  — 04 결제 동시성 (자동 DB 세팅 포함)
#   bid      — 05 입찰 동시성 (자동 DB 세팅 포함)
#   spike    — 06 트래픽 스파이크 (0→100 VU 급증)
#   stress   — 07 한계 처리량 탐색 (Breaking Point, 단독 실행 권장)
#   all      — 전체 순차 실행 (stress 제외, 기본값)
#
# 환경변수:
#   BASE_URL    서버 주소 (기본: http://localhost:8080)
#   DB_PASSWORD MySQL root 비밀번호 (.env 파일 참고)
# ============================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCENARIO="${1:-all}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# DB_PASSWORD 필요 시 .env에서 로드
# xargs 대신 직접 파싱 — 비밀번호에 공백·특수문자 포함 시에도 안전
if [ -z "$DB_PASSWORD" ] && [ -f "$SCRIPT_DIR/../.env" ]; then
  DB_PASSWORD=$(grep -E '^DB_PASSWORD=' "$SCRIPT_DIR/../.env" | cut -d '=' -f2-)
  export DB_PASSWORD
fi

# ── 헬퍼 ──────────────────────────────────────────────────────────
run_scenario() {
  local script="$1"
  local extra="${2:-}"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶  $script"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  # k6가 executor rate-drop 등으로 exit 1을 반환해도 다음 시나리오 계속 실행
  # (threshold 실패와 executor 문제를 구분하기 위해 결과는 출력 상에서 확인)
  k6 run $extra \
    -e BASE_URL="$BASE_URL" \
    "$SCRIPT_DIR/scenarios/$script" || true
}

# ── 입찰 동시성 테스트 사전 준비 ──────────────────────────────────
# stdout 에는 AUCTION_ID 숫자만, 진단 메시지는 stderr(>&2)로 출력
setup_bid_test() {
  if [ -z "$DB_PASSWORD" ]; then
    echo "❌ DB_PASSWORD 가 설정되지 않았습니다." >&2
    exit 1
  fi

  # 입찰자 생성은 seed SQL에서 INSERT IGNORE로 처리 (signup API rate limit 우회)
  echo "▶ [입찰 동시성] 입찰자 생성 + 경매 생성 (seed-bid-test-data.sql)..." >&2
  local raw_output
  raw_output=$(docker exec -i pocat-db \
    mysql -uroot -p"$DB_PASSWORD" -N pocat 2>/dev/null \
    < "$SCRIPT_DIR/setup/seed-bid-test-data.sql")

  local AUCTION_ID
  AUCTION_ID=$(echo "$raw_output" | tail -1)

  if [ -z "$AUCTION_ID" ] || [ "$AUCTION_ID" = "NULL" ]; then
    echo "❌ 경매 생성 실패: billing_key 설정 또는 카드(id=1) 확인 필요" >&2
    echo "   DB 출력: $raw_output" >&2
    exit 1
  fi

  echo "   → AUCTION_ID = $AUCTION_ID" >&2
  echo "$AUCTION_ID"
}

# ── 결제 동시성 테스트 사전 준비 ──────────────────────────────────
# 주의: 이 함수는 ORDER_ID=$(setup_payment_test) 형태로 호출되므로
#       stdout 에는 ORDER_ID 숫자만 출력하고, 진단 메시지는 반드시 stderr(>&2)로 보낸다.
setup_payment_test() {
  if [ -z "$DB_PASSWORD" ]; then
    echo "❌ DB_PASSWORD 가 설정되지 않았습니다." >&2
    echo "   export DB_PASSWORD=<비밀번호>  또는 .env 파일을 확인하세요." >&2
    exit 1
  fi

  echo "▶ [결제 동시성] 테스트 사용자 생성 (k6-buyer@test.com)..." >&2
  # nickname 한글 사용 시 Git Bash curl에서 UTF-8 인코딩 오류 발생 → ASCII 사용
  curl -sf -X POST "$BASE_URL/api/v1/auth/signup" \
    -H "Content-Type: application/json" \
    -d '{"email":"k6-buyer@test.com","password":"Test1234!","nickname":"k6buyer"}' \
    > /dev/null 2>&1 || true  # 409(중복) 무시

  echo "▶ [결제 동시성] 테스트 주문 생성 (seed-test-data.sql)..." >&2
  local raw_output
  raw_output=$(docker exec -i pocat-db \
    mysql -uroot -p"$DB_PASSWORD" -N pocat 2>/dev/null \
    < "$SCRIPT_DIR/setup/seed-test-data.sql")

  local ORDER_ID
  ORDER_ID=$(echo "$raw_output" | tail -1)

  if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" = "NULL" ]; then
    echo "❌ 주문 생성 실패: k6-buyer@test.com 사용자가 DB에 없거나 카드(id=1)가 없습니다." >&2
    echo "   DB 출력: $raw_output" >&2
    exit 1
  fi

  echo "   → ORDER_ID = $ORDER_ID" >&2
  echo "$ORDER_ID"  # stdout 에는 숫자만
}

# ── 실행 ──────────────────────────────────────────────────────────
case "$SCENARIO" in
  smoke)
    run_scenario "01-smoke.js"
    ;;
  search)
    run_scenario "02-card-search-load.js"
    ;;
  cache)
    echo "▶ [캐시 스탬피드] 캐시 초기화 중..."
    docker exec pocat-redis redis-cli DEL card:avgprice:1 > /dev/null 2>&1 || true
    run_scenario "03-avg-price-cache-stampede.js"
    ;;
  payment)
    ORDER_ID=$(setup_payment_test)
    run_scenario "04-payment-concurrency.js" "-e ORDER_ID=$ORDER_ID"
    ;;
  bid)
    AUCTION_ID=$(setup_bid_test)
    run_scenario "05-bid-concurrency.js" "-e AUCTION_ID=$AUCTION_ID"
    ;;
  spike)
    run_scenario "06-auction-spike.js"
    ;;
  stress)
    # 서버를 한계까지 밀어붙이는 테스트 — all에서 제외, 단독 실행 권장
    run_scenario "07-breaking-point.js"
    ;;
  all|*)
    # 1. 스모크
    run_scenario "01-smoke.js"

    # 2. 카드 검색 부하
    run_scenario "02-card-search-load.js"

    # 3. 캐시 스탬피드 (캐시 먼저 비우기)
    echo "▶ [캐시 스탬피드] 캐시 초기화 중..."
    docker exec pocat-redis redis-cli DEL card:avgprice:1 > /dev/null 2>&1 || true
    run_scenario "03-avg-price-cache-stampede.js"

    # 4. 결제 동시성
    ORDER_ID=$(setup_payment_test)
    run_scenario "04-payment-concurrency.js" "-e ORDER_ID=$ORDER_ID"

    # 5. 입찰 동시성
    AUCTION_ID=$(setup_bid_test)
    run_scenario "05-bid-concurrency.js" "-e AUCTION_ID=$AUCTION_ID"

    # 6. 트래픽 스파이크 (stress는 서버 한계 탐색 목적으로 단독 실행 권장 → all 제외)
    run_scenario "06-auction-spike.js"
    ;;
esac

echo ""
echo "✅ 테스트 완료"
echo "   Grafana: http://localhost:3000"
echo "   Prometheus: http://localhost:9090"
