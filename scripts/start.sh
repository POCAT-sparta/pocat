#!/usr/bin/env bash
# ================================================================
# POCAT 로컬 환경 기동 스크립트
# 사용법: ./scripts/start.sh [--kafka]
#         --kafka  Kafka 클러스터(3 broker) + Kafka UI 함께 기동
# ================================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KAFKA_PROFILE=""

for arg in "$@"; do
  case $arg in
    --kafka) KAFKA_PROFILE="--profile kafka" ;;
  esac
done

if [ ! -f "$PROJECT_ROOT/.env" ]; then
  echo "[ERR] .env 파일이 없습니다. .env.example을 복사하여 .env를 생성하세요."
  exit 1
fi

if ! docker info > /dev/null 2>&1; then
  echo "[ERR] Docker가 실행 중이지 않습니다. Docker Desktop을 실행 후 다시 시도하세요."
  exit 1
fi

echo "=== [1/3] Gradle bootJar 빌드 ==="
cd "$PROJECT_ROOT"
./gradlew clean bootJar -x test

echo "=== [2/3] Docker Compose 기동 ==="
cd "$PROJECT_ROOT"
docker compose $KAFKA_PROFILE up -d --build

echo "=== [3/3] 백엔드 기동 대기 ==="
for i in $(seq 1 30); do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' pocat-backend 2>/dev/null || echo "starting")
  echo "  backend 상태: $STATUS ($i/30)"
  [ "$STATUS" = "healthy" ] && break
  sleep 3
done

echo ""
echo "=== 기동 완료 ==="
echo "  백엔드:      http://localhost:8080"
echo "  Prometheus:  http://localhost:9090"
echo "  Grafana:     http://localhost:3000  (admin / admin)"
if [ -n "$KAFKA_PROFILE" ]; then
  echo "  Kafka UI:    http://localhost:8085"
fi
echo ""
echo "=== 백엔드 로그 출력 (중지: Ctrl+C — 컨테이너는 유지됨) ==="

trap "echo ''; echo '중지됨. 컨테이너를 종료하려면: ./scripts/stop.sh'; exit 0" INT TERM

docker compose logs -f backend
