#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[infra] Verificando servicios docker compose..."
docker compose ps

echo "[infra] Esperando PostgreSQL listo..."
if ! docker compose exec -T postgres pg_isready -U micuota -d micuota >/dev/null; then
  echo "[infra][error] PostgreSQL no esta listo"
  exit 1
fi

declare -a CHECKS=(
  "mailpit http://localhost:8025"
  "adminer http://localhost:8081"
  "n8n http://localhost:5678"
  "metabase http://localhost:3001"
)

for check in "${CHECKS[@]}"; do
  name="${check%% *}"
  url="${check#* }"
  echo "[infra] Probando ${name} en ${url}"
  code="$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)"
  if [[ "$code" == "000" ]]; then
    echo "[infra][error] ${name} no responde"
    exit 1
  fi
  echo "[infra] ${name} OK (HTTP ${code})"
done

echo "[infra] Todo OK"
