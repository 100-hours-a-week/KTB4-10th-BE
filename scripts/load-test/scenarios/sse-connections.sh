#!/bin/sh

set -eu

base_url="${BASE_URL:-http://backend:8080}"
management_url="${MANAGEMENT_URL:-http://backend:8081}"
session_prefix="${SESSION_PREFIX:-k6-local-session-}"
session_mode="${SSE_SESSION_MODE:-distinct}"
connection_count="${SSE_CONNECTION_COUNT:-100}"
hold_seconds="${SSE_HOLD_SECONDS:-120}"
startup_wait_seconds="${SSE_STARTUP_WAIT_SECONDS:-5}"
recovery_wait_seconds="${SSE_RECOVERY_WAIT_SECONDS:-35}"
load_test_user_count="${LOAD_TEST_USER_COUNT:-500}"
max_connections_per_member="${SSE_MAX_CONNECTIONS_PER_MEMBER:-5}"
pids=""
cleaned_up="false"

require_positive_integer() {
  name="$1"
  value="$2"
  case "$value" in
    ''|*[!0-9]*|0)
      echo "$name must be a positive integer: $value" >&2
      exit 2
      ;;
  esac
}

read_connection_metric() {
  curl --fail --silent --show-error \
    "$management_url/actuator/prometheus" \
    | awk '/^kgb_notification_sse_connections[{ ]/ {metric=$NF} END {print metric}'
}

assert_number_at_least() {
  actual="$1"
  expected="$2"
  label="$3"
  if ! awk -v actual="$actual" -v expected="$expected" \
    'BEGIN {exit !(actual >= expected)}'; then
    cleanup_clients
    echo "$label: expected at least $expected, actual $actual" >&2
    exit 1
  fi
}

cleanup_clients() {
  if [ "$cleaned_up" = "true" ]; then
    return
  fi
  cleaned_up="true"
  echo "Disconnecting SSE load clients..."
  for pid in $pids; do
    kill "$pid" 2>/dev/null || true
  done
  for pid in $pids; do
    wait "$pid" 2>/dev/null || true
  done
}

trap 'cleanup_clients; exit 130' INT TERM HUP

require_positive_integer SSE_CONNECTION_COUNT "$connection_count"
require_positive_integer SSE_HOLD_SECONDS "$hold_seconds"
require_positive_integer SSE_STARTUP_WAIT_SECONDS "$startup_wait_seconds"
require_positive_integer SSE_RECOVERY_WAIT_SECONDS "$recovery_wait_seconds"
require_positive_integer LOAD_TEST_USER_COUNT "$load_test_user_count"
require_positive_integer SSE_MAX_CONNECTIONS_PER_MEMBER "$max_connections_per_member"

case "$session_mode" in
  distinct|same) ;;
  *)
    echo "SSE_SESSION_MODE must be 'distinct' or 'same': $session_mode" >&2
    exit 2
    ;;
esac

if [ "$session_mode" = "distinct" ] && [ "$connection_count" -gt "$load_test_user_count" ]; then
  echo "SSE_CONNECTION_COUNT cannot exceed LOAD_TEST_USER_COUNT in distinct mode." >&2
  exit 2
fi

curl --fail --silent --show-error "$management_url/actuator/health" >/dev/null
before_connections="$(read_connection_metric)"
if [ -z "$before_connections" ]; then
  echo "SSE connection Gauge is not exposed." >&2
  exit 1
fi
echo "Before load active SSE connections: $before_connections"

echo "Opening $connection_count SSE connections (session mode: $session_mode)..."
i=1
while [ "$i" -le "$connection_count" ]; do
  if [ "$session_mode" = "same" ]; then
    session_number=1
  else
    session_number="$i"
  fi
  session_id="${session_prefix}$(printf '%03d' "$session_number")"
  curl --fail --no-buffer --silent --show-error \
    --connect-timeout 5 \
    -H "Cookie: KGB_SESSION=$session_id" \
    "$base_url/api/v1/notifications/stream" \
    >/dev/null 2>&1 &
  pids="$pids $!"
  i=$((i + 1))
done

sleep "$startup_wait_seconds"

live_clients=0
for pid in $pids; do
  if kill -0 "$pid" 2>/dev/null; then
    live_clients=$((live_clients + 1))
  fi
done

if [ "$session_mode" = "same" ] && [ "$connection_count" -gt "$max_connections_per_member" ]; then
  expected_clients="$max_connections_per_member"
else
  expected_clients="$connection_count"
fi

if [ "$live_clients" -lt "$expected_clients" ]; then
  cleanup_clients
  echo "Only $live_clients clients remain; expected at least $expected_clients." >&2
  echo "Check backend authentication and SSE logs." >&2
  exit 1
fi

during_connections="$(read_connection_metric)"
expected_during_connections="$(awk \
  -v baseline="$before_connections" \
  -v expected="$expected_clients" \
  'BEGIN {print baseline + expected}')"
assert_number_at_least \
  "$during_connections" \
  "$expected_during_connections" \
  "SSE connection Gauge did not reach the expected load"
echo "During load active SSE connections: $during_connections"
echo "Holding connections for $hold_seconds seconds. Open Grafana at http://localhost:3000."
sleep "$hold_seconds"

cleanup_clients
echo "Waiting $recovery_wait_seconds seconds for heartbeat cleanup and Prometheus scrape..."
sleep "$recovery_wait_seconds"
after_connections="$(read_connection_metric)"
echo "After disconnect active SSE connections: $after_connections"
if ! awk -v actual="$after_connections" -v baseline="$before_connections" \
  'BEGIN {exit !(actual <= baseline)}'; then
  echo "SSE connections did not return to baseline ($before_connections)." >&2
  exit 1
fi
echo "SSE load scenario finished. The active connection Gauge returned to baseline."
