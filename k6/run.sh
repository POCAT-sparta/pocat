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
#   all      — 전체 순차 실행 (기본값)
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
if [ -z "$DB_PASSWORD" ] && [ -f "$SCRIPT_DIR/../.env" ]; then
  export $(grep -E '^DB_PASSWORD=' "$SCRIPT_DIR/../.env" | xargs)
fi

# ── 헬퍼 ──────────────────────────────────────────────────────────
run_scenario() {
  local script="$1"
  local extra="${2:-}"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶  $script"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  k6 run $extra \
    -e BASE_URL="$BASE_URL" \
    "$SCRIPT_DIR/scenarios/$script"
}

# ── 결제 동시성 테스트 사전 준비 ──────────────────────────────────
setup_payment_test() {
  if [ -z "$DB_PASSWORD" ]; then
    echo "❌ DB_PASSWORD 가 설정되지 않았습니다."
    echo "   export DB_PASSWORD=<비밀번호>  또는 .env 파일을 확인하세요."
    exit 1
  fi

  echo "▶ [결제 동시성] 테스트 사용자 생성 (k6-buyer@test.com)..."
  curl -sf -X POST "$BASE_URL/api/v1/auth/signup" \
    -H "Content-Type: application/json" \
    -d '{"email":"k6-buyer@test.com","password":"Test1234!","nickname":"k6테스트구매자"}' \
    > /dev/null 2>&1 || true  # 409(중복) 무시

  echo "▶ [결제 동시성] 테스트 주문 생성 (seed-test-data.sql)..."
  ORDER_ID=$(docker exec -i pocat-db \
    mysql -uroot -p"$DB_PASSWORD" -N pocat 2>/dev/null \
    < "$SCRIPT_DIR/setup/seed-test-data.sql" \
    | tail -1)

  if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" = "NULL" ]; then
    echo "❌ 주문 생성 실패: k6-buyer@test.com 사용자가 DB에 없거나 카드(id=1)가 없습니다."
    exit 1
  fi

  echo "   → ORDER_ID = $ORDER_ID"
  echo "$ORDER_ID"
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
    ;;
esac

echo ""
echo "✅ 테스트 완료"
echo "   Grafana: http://localhost:3000"
echo "   Prometheus: http://localhost:9090"
