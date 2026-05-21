#!/usr/bin/env bash
# ================================================================
# POCAT 로컬 환경 종료 스크립트
# 사용법: ./scripts/stop.sh          컨테이너 종료 (볼륨 유지)
#         ./scripts/stop.sh --clean  컨테이너 + 볼륨 삭제 (데이터 초기화)
# ================================================================
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLEAN_FLAG=""

for arg in "$@"; do
  case $arg in
    --clean) CLEAN_FLAG="-v" ;;
  esac
done

cd "$PROJECT_ROOT"

if [ -n "$CLEAN_FLAG" ]; then
  echo "================================================================"
  echo "[경고] 볼륨 삭제 모드: DB, Redis, Kafka 데이터가 모두 삭제됩니다."
  echo "================================================================"
  read -rp "계속할까요? (y/N) " confirm
  [[ "$confirm" =~ ^[Yy]$ ]] || { echo "취소됨"; exit 0; }
fi

echo "=== Docker Compose 종료 ==="
docker compose --profile kafka down $CLEAN_FLAG

echo ""
echo "=== 종료 완료 — 컨테이너 상태 ==="
docker compose ps
