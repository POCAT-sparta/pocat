#!/usr/bin/env bash
# ============================================================
# pocat k6 중부하 / 실사용 테스트 실행 스크립트 (run-2.sh)
#
# 사전 조건:
#   - Docker Compose 실행 중 (docker compose up -d)
#   - k6 설치됨 (brew install k6 / choco install k6)  ※ 최소 v0.31.0 필요 (responseCallback/http.expectedStatuses)
#   - .env 파일에 DB_PASSWORD 설정
#
# 사용법:
#   ./k6/run-2.sh [scenario] [options]
#
# scenario 옵션:
#   mixed   — 08 혼합 실사용 (익명+인증+폴링, ~10분)
#   peak    — 09 경매 피크 타임 (다중 경매 동시 입찰, ~9분)
#   soak    — 10 장기 내구성 (메모리·커넥션 누수 탐지, 기본 20분)
#   bp      — 11 Breaking Point 탐색 (HikariCP 한계점, ~12분)
#   all     — mixed → peak → soak 순차 실행 (기본값, bp 제외)
#
# 환경변수:
#   BASE_URL       서버 주소 (기본: http://localhost:8080)
#   DB_PASSWORD    MySQL root 비밀번호 (.env 에서 자동 로드)
#   SOAK_DURATION  soak 테스트 시간 (기본: 20m, 예: 45m)
#
# 예시:
#   ./k6/run-2.sh mixed
#   ./k6/run-2.sh soak
#   ./k6/run-2.sh bp
#   SOAK_DURATION=45m ./k6/run-2.sh soak
#   BASE_URL=http://staging.example.com ./k6/run-2.sh all
# ============================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCENARIO="${1:-all}"
SOAK_DURATION="${SOAK_DURATION:-20m}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── DB_PASSWORD 로드 ──────────────────────────────────────────────
if [ -z "$DB_PASSWORD" ] && [ -f "$SCRIPT_DIR/../.env" ]; then
  DB_PASSWORD=$(grep -E '^DB_PASSWORD=' "$SCRIPT_DIR/../.env" | cut -d '=' -f2-)
  export DB_PASSWORD
fi

# ── 색상 출력 헬퍼 ───────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}ℹ  $*${NC}"; }
success() { echo -e "${GREEN}✅ $*${NC}"; }
warn()    { echo -e "${YELLOW}⚠️  $*${NC}" >&2; }
error()   { echo -e "${RED}❌ $*${NC}" >&2; exit 1; }

# ── 서버 헬스체크 ────────────────────────────────────────────────
check_server_health() {
  info "서버 헬스체크: $BASE_URL/actuator/health"
  local status
  status=$(curl -sf "$BASE_URL/actuator/health" | grep -o '"status":"[^"]*"' | head -1 || echo '')
  if echo "$status" | grep -q '"UP"'; then
    success "서버 UP"
  else
    warn "서버 응답 이상 ($status) — 테스트를 강행합니다."
  fi
}

# ── 시나리오 실행 헬퍼 ───────────────────────────────────────────
run_scenario() {
  local script="$1"
  local extra="${2:-}"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  info "▶  $script  $extra"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  # threshold 실패(exit 99)도 다음 시나리오 계속 실행
  k6 run $extra \
    -e BASE_URL="$BASE_URL" \
    -e SOAK_DURATION="$SOAK_DURATION" \
    "$SCRIPT_DIR/scenarios/$script" || true
}

# ── 중부하 테스트 시드 데이터 세팅 ──────────────────────────────
# stdout: 콤마 구분 경매 ID 목록만 출력 / 진단 메시지: stderr
setup_heavy_test() {
  if [ -z "$DB_PASSWORD" ]; then
    error "DB_PASSWORD 가 설정되지 않았습니다. .env 파일을 확인하세요."
  fi

  info "[시드] 중부하 테스트 데이터 생성 (seed-heavy-test-data.sql)..." >&2

  local raw_output
  raw_output=$(docker exec -i pocat-db \
    mysql -uroot -p"$DB_PASSWORD" -N pocat 2>/dev/null \
    < "$SCRIPT_DIR/setup/seed-heavy-test-data.sql")

  # GROUP_CONCAT 결과 파싱 (마지막 줄: 콤마 구분 경매 ID)
  local AUCTION_IDS
  AUCTION_IDS=$(echo "$raw_output" | tail -1 | tr -d ' \r\n')

  if [ -z "$AUCTION_IDS" ] || [ "$AUCTION_IDS" = "NULL" ]; then
    error "경매 생성 실패. DB 출력: $raw_output"
  fi

  local count
  count=$(echo "$AUCTION_IDS" | tr ',' '\n' | wc -l | tr -d ' ')
  info "→ 경매 ${count}개 생성 완료: [${AUCTION_IDS}]" >&2

  echo "$AUCTION_IDS"  # stdout: 숫자만
}

# ── Redis 캐시 초기화 (스탬피드 재현을 위해 평균가 캐시 비우기) ──
flush_avg_price_cache() {
  info "[Redis] 평균가 캐시 초기화 중..."
  for id in 1 2 3 4 5 6 7 8; do
    docker exec pocat-redis redis-cli DEL "card:avgprice:${id}" > /dev/null 2>&1 || true
  done
  success "평균가 캐시 초기화 완료"
}

# ── 메인 ─────────────────────────────────────────────────────────
echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║        pocat k6 중부하 테스트 — run-2.sh                 ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo "  BASE_URL      : $BASE_URL"
echo "  SCENARIO      : $SCENARIO"
echo "  SOAK_DURATION : $SOAK_DURATION (soak 시나리오 적용)"
echo ""

check_server_health

case "$SCENARIO" in
  # ── 08: 혼합 실사용 ────────────────────────────────────────────
  mixed)
    AUCTION_IDS=$(setup_heavy_test)
    flush_avg_price_cache
    run_scenario "08-realistic-mixed-load.js" "-e AUCTION_IDS=$AUCTION_IDS"
    ;;

  # ── 09: 경매 피크 타임 ─────────────────────────────────────────
  peak)
    AUCTION_IDS=$(setup_heavy_test)
    run_scenario "09-auction-peak-load.js" "-e AUCTION_IDS=$AUCTION_IDS"
    ;;

  # ── 10: 장기 내구성 ────────────────────────────────────────────
  soak)
    AUCTION_IDS=$(setup_heavy_test)
    flush_avg_price_cache
    echo ""
    warn "Soak 테스트 시작 — ${SOAK_DURATION} 동안 75 VU 유지"
    warn "드리프트 분석: k6 run --out csv=soak-result.csv ..."
    warn "Grafana 연동: k6 run --out influxdb=http://localhost:8086/k6 ..."
    echo ""
    run_scenario "10-soak.js" "-e AUCTION_IDS=$AUCTION_IDS"
    ;;

  # ── 11: Breaking Point 탐색 ────────────────────────────────────
  bp)
    AUCTION_IDS=$(setup_heavy_test)
    echo ""
    warn "Breaking Point 탐색 시작 (~12분)"
    warn "임계치 실패 = BP 발견 (오류 아님) — 단계별 p(95) 추이와 5xx 첫 발생 VU 확인"
    warn "HikariCP 기본 풀(10) 고갈 예상 구간: readers 150~300 VU"
    echo ""
    run_scenario "11-bid-breaking-point.js" "-e AUCTION_IDS=$AUCTION_IDS"
    ;;

  # ── all: 순차 실행 (bp 별도 실행 권장) ─────────────────────────
  all|*)
    AUCTION_IDS=$(setup_heavy_test)
    flush_avg_price_cache

    # 1. 혼합 실사용 (~10분)
    echo ""
    info "=== [1/3] 혼합 실사용 시나리오 (~10분) ==="
    run_scenario "08-realistic-mixed-load.js" "-e AUCTION_IDS=$AUCTION_IDS"

    # 2. 경매 피크 타임 (~9분)
    echo ""
    info "=== [2/3] 경매 피크 타임 시나리오 (~9분) ==="
    # 피크 테스트 전 시드 재실행: 입찰 데이터 초기화 후 신선한 경매 재생성
    AUCTION_IDS=$(setup_heavy_test)
    run_scenario "09-auction-peak-load.js" "-e AUCTION_IDS=$AUCTION_IDS"

    # 3. 장기 내구성 (기본 20분)
    echo ""
    info "=== [3/3] 장기 내구성 시나리오 (${SOAK_DURATION}) ==="
    AUCTION_IDS=$(setup_heavy_test)
    flush_avg_price_cache
    run_scenario "10-soak.js" "-e AUCTION_IDS=$AUCTION_IDS"
    ;;
esac

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
success "중부하 테스트 완료"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""
echo "  Kibana (로그)     : http://localhost:5601"
echo "  Kafka UI          : http://localhost:8085"
echo "  Actuator 메트릭   : $BASE_URL/actuator/metrics"
echo ""
echo "  💡 결과 분석 팁:"
echo "     k6 run --out csv=heavy-result.csv ... 로 재실행 후 CSV 분석"
echo "     혹은 docker compose 에 Grafana + InfluxDB 추가 후 실시간 시각화"
