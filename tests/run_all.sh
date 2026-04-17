#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[tests] Iniciando pruebas MiCuota"

./tests/smoke_infra.sh

echo "[tests] Infraestructura OK"

echo "[tests] Ejecutando smoke API"
python3 ./tests/smoke_api_onboarding.py "$@"

echo "[tests] Todas las pruebas pasaron"
