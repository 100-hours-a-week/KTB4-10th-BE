#!/bin/sh

set -eu

session_mode="${1:-distinct}"
connection_count="${2:-100}"
hold_seconds="${3:-120}"

if [ ! -f .env.loadtest ]; then
  echo ".env.loadtest is missing. Copy .env.loadtest.example first." >&2
  exit 2
fi

exec docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm \
  -e SSE_SESSION_MODE="$session_mode" \
  -e SSE_CONNECTION_COUNT="$connection_count" \
  -e SSE_HOLD_SECONDS="$hold_seconds" \
  sse-load
