#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PELIAS_URL:-http://localhost:4000}"

echo "[smoke] Checking Pelias at ${BASE_URL}"

check_endpoint() {
  local endpoint=$1
  echo "[smoke] GET ${endpoint}"
  http_code=$(curl -fsS -o /tmp/pelias_smoke_response.json -w "%{http_code}" "${BASE_URL}${endpoint}") || {
    echo "[smoke] Request failed for ${endpoint}" >&2
    exit 1
  }
  if [[ "${http_code}" != "200" ]]; then
    echo "[smoke] Unexpected HTTP ${http_code} for ${endpoint}" >&2
    exit 1
  fi
  cat /tmp/pelias_smoke_response.json >/dev/null
}

check_endpoint "/v1/health"
check_endpoint "/v1/search?text=Berlin"
check_endpoint "/v1/search?text=Distribution+Center"

echo "[smoke] All checks passed."
