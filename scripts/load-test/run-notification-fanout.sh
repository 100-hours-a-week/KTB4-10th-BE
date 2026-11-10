#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
BASE_URL=${BASE_URL:-http://localhost:8080}
SESSION_PREFIX=${LOAD_TEST_SESSION_PREFIX:-k6-local-session-}
SESSION_ID="${SESSION_PREFIX}001"

usage() {
    cat <<'EOF'
Usage:
  ./scripts/load-test/run-notification-fanout.sh prepare <scenario> <member-count> [push-subscriptions]
  ./scripts/load-test/run-notification-fanout.sh run <scenario> <initial|reconciliation>
  ./scripts/load-test/run-notification-fanout.sh status
  ./scripts/load-test/run-notification-fanout.sh cleanup

Scenarios:
  new-event | interested-change | guidebook-change | trip-starting | mixed

Examples:
  ./scripts/load-test/run-notification-fanout.sh prepare mixed 100 false
  ./scripts/load-test/run-notification-fanout.sh run mixed initial
  ./scripts/load-test/run-notification-fanout.sh run mixed reconciliation
  ./scripts/load-test/run-notification-fanout.sh status
EOF
}

scenario_value() {
    case "$1" in
        new-event) echo "NEW_EVENT_DIGEST" ;;
        interested-change) echo "INTERESTED_EVENT_CHANGE" ;;
        guidebook-change) echo "GUIDEBOOK_EVENT_CHANGE" ;;
        trip-starting) echo "TRIP_STARTING" ;;
        mixed) echo "MIXED" ;;
        *) echo "Unknown scenario: $1" >&2; usage >&2; exit 2 ;;
    esac
}

csrf_token() {
    curl -fsS -D - -o /dev/null \
        -H "Cookie: KGB_SESSION=$SESSION_ID" \
        "$BASE_URL/api/v1/auth/csrf" \
        | sed -n 's/^[Ss]et-[Cc]ookie: XSRF-TOKEN=\([^;]*\).*/\1/p' \
        | tr -d '\r' \
        | tail -n 1
}

mutate() {
    method=$1
    path=$2
    body=${3:-}
    token=$(csrf_token)
    if [ -z "$token" ]; then
        echo "Failed to issue XSRF-TOKEN. Check backend and fixture session." >&2
        exit 1
    fi
    if [ -n "$body" ]; then
        curl -fsS -X "$method" \
            -H "Cookie: KGB_SESSION=$SESSION_ID; XSRF-TOKEN=$token" \
            -H "X-XSRF-TOKEN: $token" \
            -H "Content-Type: application/json" \
            --data "$body" \
            "$BASE_URL$path"
    else
        curl -fsS -X "$method" \
            -H "Cookie: KGB_SESSION=$SESSION_ID; XSRF-TOKEN=$token" \
            -H "X-XSRF-TOKEN: $token" \
            "$BASE_URL$path"
    fi
    echo
}

cd "$PROJECT_ROOT"
command=${1:-}
case "$command" in
    prepare)
        [ "$#" -ge 3 ] || { usage >&2; exit 2; }
        scenario=$(scenario_value "$2")
        member_count=$3
        push_subscriptions=${4:-false}
        mutate POST "/api/v1/load-tests/notification-fanout/fixtures" \
            "{\"scenario\":\"$scenario\",\"memberCount\":$member_count,\"pushSubscriptions\":$push_subscriptions}"
        ;;
    run)
        [ "$#" -eq 3 ] || { usage >&2; exit 2; }
        scenario=$(scenario_value "$2")
        case "$3" in
            initial) phase="INITIAL" ;;
            reconciliation) phase="RECONCILIATION" ;;
            *) echo "Phase must be initial or reconciliation." >&2; exit 2 ;;
        esac
        mutate POST "/api/v1/load-tests/notification-fanout/runs" \
            "{\"scenario\":\"$scenario\",\"phase\":\"$phase\"}"
        ;;
    status)
        curl -fsS \
            -H "Cookie: KGB_SESSION=$SESSION_ID" \
            "$BASE_URL/api/v1/load-tests/notification-fanout/status"
        echo
        ;;
    cleanup)
        mutate DELETE "/api/v1/load-tests/notification-fanout/fixtures"
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
