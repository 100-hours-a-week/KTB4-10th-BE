#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
ENV_FILE=${ENV_FILE:-"$PROJECT_ROOT/.env.loadtest"}
COMPOSE_FILE=${COMPOSE_FILE:-"$PROJECT_ROOT/compose.loadtest.yml"}
TEST_TITLE='[LOAD_TEST_OUTBOX] Web Push 부하테스트'
EXTRA_WORKER_PREFIX='kgb-outbox-loadtest-worker'

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

usage() {
    cat <<'USAGE'
Usage:
  ./scripts/load-test/run-web-push-outbox.sh prepare [count]
  ./scripts/load-test/run-web-push-outbox.sh run <accepted|slow|mixed|timeout|multi|lease> [count] [workers]
  ./scripts/load-test/run-web-push-outbox.sh status
  ./scripts/load-test/run-web-push-outbox.sh cleanup

Examples:
  ./scripts/load-test/run-web-push-outbox.sh run accepted 100
  ./scripts/load-test/run-web-push-outbox.sh run mixed 1000
  ./scripts/load-test/run-web-push-outbox.sh run multi 5000 2
  ./scripts/load-test/run-web-push-outbox.sh run lease 100
USAGE
}

require_files() {
    test -f "$ENV_FILE" || {
        echo "Missing $ENV_FILE. Copy .env.loadtest.example first." >&2
        exit 2
    }
}

validate_count() {
    local value=$1
    [[ "$value" =~ ^[0-9]+$ ]] && ((value >= 1 && value <= 100000)) || {
        echo "count must be between 1 and 100000" >&2
        exit 2
    }
}

mysql_exec() {
    compose exec -T mysql mysql \
        -ukgb -pkgb-local-password -Dkgb_local --batch --skip-column-names "$@"
}

cleanup_extra_workers() {
    local container
    while read -r container; do
        test -z "$container" || docker rm -f "$container" >/dev/null
    done < <(docker ps -a --format '{{.Names}}' --filter "name=^${EXTRA_WORKER_PREFIX}-")
}

cleanup_fixture() {
    mysql_exec <<SQL
DELETE outbox
FROM web_push_outbox outbox
JOIN notifications notification ON notification.id = outbox.notification_id
WHERE notification.title = '${TEST_TITLE}';
DELETE FROM notifications WHERE title = '${TEST_TITLE}';
DELETE FROM web_push_subscriptions
WHERE endpoint LIKE 'https://fcm.googleapis.com/load-test/%';
SQL
}

prepare_fixture() {
    local count=$1
    validate_count "$count"
    compose up -d mysql redis >/dev/null
    export WEB_PUSH_OUTBOX_WORKER_ENABLED=false
    compose up -d --build --wait backend >/dev/null
    compose stop backend >/dev/null
    cleanup_extra_workers
    cleanup_fixture
    mysql_exec <<SQL
DROP PROCEDURE IF EXISTS seed_web_push_outbox_load_test;
DELIMITER //
CREATE PROCEDURE seed_web_push_outbox_load_test(IN requested_count INT)
BEGIN
    DECLARE sequence_no INT DEFAULT 1;
    DECLARE fixture_member_count INT;
    DECLARE fixture_offset INT;
    DECLARE fixture_member_id BIGINT;
    DECLARE fixture_subscription_id BIGINT;
    DECLARE fixture_notification_id BIGINT;
    DECLARE fixture_endpoint VARCHAR(500);

    SELECT COUNT(*) INTO fixture_member_count
    FROM members
    WHERE oauth_subject LIKE 'load-test-user-%'
      AND deleted_at IS NULL;

    IF fixture_member_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Load-test members are missing. Start backend once before preparing outbox fixtures.';
    END IF;

    WHILE sequence_no <= requested_count DO
        SET fixture_offset = MOD(sequence_no - 1, fixture_member_count);
        SELECT id INTO fixture_member_id
        FROM members
        WHERE oauth_subject LIKE 'load-test-user-%'
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT fixture_offset, 1;

        SET fixture_endpoint = CONCAT(
            'https://fcm.googleapis.com/load-test/', fixture_member_id);
        INSERT INTO web_push_subscriptions (
            member_id, session_hash, endpoint, endpoint_hash,
            p256dh, auth_secret, expiration_at, status,
            failure_count, created_at, updated_at
        ) VALUES (
            fixture_member_id,
            SHA2(CONCAT('load-test-session-', fixture_member_id), 256),
            fixture_endpoint,
            UNHEX(SHA2(fixture_endpoint, 256)),
            'load-test-p256dh', 'load-test-auth', NULL, 'ACTIVE',
            0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
        ) ON DUPLICATE KEY UPDATE
            member_id = VALUES(member_id),
            status = 'ACTIVE',
            failure_count = 0,
            expiration_at = NULL,
            updated_at = UTC_TIMESTAMP(6);

        SELECT id INTO fixture_subscription_id
        FROM web_push_subscriptions
        WHERE endpoint_hash = UNHEX(SHA2(fixture_endpoint, 256));

        INSERT INTO notifications (
            recipient_member_id, type, title, body,
            reference_type, reference_id, created_at
        ) VALUES (
            fixture_member_id, 'GUIDEBOOK_COMPLETED', '${TEST_TITLE}',
            CONCAT('outbox fixture ', sequence_no),
            NULL, NULL, UTC_TIMESTAMP(6)
        );
        SET fixture_notification_id = LAST_INSERT_ID();

        INSERT INTO web_push_outbox (
            notification_id, recipient_member_id, subscription_id,
            payload_json, status, attempt_count, next_attempt_at,
            created_at, updated_at
        ) VALUES (
            fixture_notification_id, fixture_member_id, fixture_subscription_id,
            JSON_OBJECT(
                'notification_id', fixture_notification_id,
                'title', '${TEST_TITLE}',
                'body', CONCAT('outbox fixture ', sequence_no)
            ),
            'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
        );
        SET sequence_no = sequence_no + 1;
    END WHILE;
END//
DELIMITER ;
CALL seed_web_push_outbox_load_test(${count});
DROP PROCEDURE seed_web_push_outbox_load_test;
SQL
    export WEB_PUSH_OUTBOX_WORKER_ENABLED=true
    echo "Prepared $count Web Push Outbox tasks."
}

print_status() {
    mysql_exec --table --execute="
SELECT status, COUNT(*) AS task_count,
       MIN(created_at) AS oldest_created_at,
       MAX(attempt_count) AS max_attempt_count
FROM web_push_outbox
WHERE notification_id IN (
    SELECT id FROM notifications WHERE title = '${TEST_TITLE}'
)
GROUP BY status
ORDER BY status;
"
}

waiting_count() {
    mysql_exec --execute="
SELECT COUNT(*)
FROM web_push_outbox
WHERE notification_id IN (
    SELECT id FROM notifications WHERE title = '${TEST_TITLE}'
)
AND status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING');
"
}

wait_for_drain() {
    local timeout_seconds=${1:-300}
    local started_at=$SECONDS
    local remaining
    while ((SECONDS - started_at < timeout_seconds)); do
        remaining=$(waiting_count)
        if [[ "$remaining" == "0" ]]; then
            echo "Drain completed in $((SECONDS - started_at)) seconds."
            print_status
            return 0
        fi
        printf 'waiting=%s elapsed=%ss\n' "$remaining" "$((SECONDS - started_at))"
        sleep 2
    done
    echo "Drain timeout after ${timeout_seconds}s." >&2
    print_status
    return 1
}

configure_scenario() {
    local scenario=$1
    export WEB_PUSH_LOAD_TEST_DELAYED_PERCENT=0
    export WEB_PUSH_LOAD_TEST_RETRY_429_PERCENT=0
    export WEB_PUSH_LOAD_TEST_RETRY_503_PERCENT=0
    export WEB_PUSH_LOAD_TEST_TIMEOUT_PERCENT=0
    export WEB_PUSH_LOAD_TEST_DELAY=200ms
    export WEB_PUSH_LOAD_TEST_TIMEOUT=2s
    export WEB_PUSH_OUTBOX_LEASE=1m
    case "$scenario" in
        accepted) ;;
        slow)
            export WEB_PUSH_LOAD_TEST_DELAYED_PERCENT=100
            export WEB_PUSH_LOAD_TEST_DELAY=200ms
            ;;
        mixed)
            export WEB_PUSH_LOAD_TEST_RETRY_429_PERCENT=10
            export WEB_PUSH_LOAD_TEST_RETRY_503_PERCENT=10
            export WEB_PUSH_LOAD_TEST_TIMEOUT_PERCENT=10
            ;;
        timeout)
            export WEB_PUSH_LOAD_TEST_TIMEOUT_PERCENT=100
            ;;
        multi) ;;
        lease)
            export WEB_PUSH_LOAD_TEST_DELAYED_PERCENT=100
            export WEB_PUSH_LOAD_TEST_DELAY=30s
            export WEB_PUSH_OUTBOX_LEASE=10s
            ;;
        *)
            echo "Unsupported scenario: $scenario" >&2
            usage
            exit 2
            ;;
    esac
}

start_backend() {
    compose up -d --force-recreate --wait backend >/dev/null
}

start_extra_workers() {
    local workers=$1
    local sequence_no
    for ((sequence_no = 2; sequence_no <= workers; sequence_no++)); do
        docker rm -f "${EXTRA_WORKER_PREFIX}-${sequence_no}" >/dev/null 2>&1 || true
        compose run -d --no-deps \
            --name "${EXTRA_WORKER_PREFIX}-${sequence_no}" backend >/dev/null
    done
}

run_lease_recovery() {
    local count=$1
    start_backend
    local attempts=0
    until [[ "$(mysql_exec --execute="SELECT COUNT(*) FROM web_push_outbox WHERE status = 'PROCESSING';")" != "0" ]]; do
        attempts=$((attempts + 1))
        ((attempts < 30)) || {
            echo "No PROCESSING task observed." >&2
            return 1
        }
        sleep 1
    done
    compose kill backend >/dev/null
    echo "Worker killed while tasks were PROCESSING. Waiting for 10s lease expiry."
    sleep 12
    export WEB_PUSH_LOAD_TEST_DELAYED_PERCENT=0
    export WEB_PUSH_LOAD_TEST_DELAY=0ms
    start_backend
    wait_for_drain 180
}

run_scenario() {
    local scenario=$1
    local count=$2
    local workers=$3
    validate_count "$count"
    [[ "$workers" =~ ^[0-9]+$ ]] && ((workers >= 1 && workers <= 4)) || {
        echo "workers must be between 1 and 4" >&2
        exit 2
    }
    configure_scenario "$scenario"
    prepare_fixture "$count"
    if [[ "$scenario" == "lease" ]]; then
        run_lease_recovery "$count"
        return
    fi
    start_backend
    start_extra_workers "$workers"
    wait_for_drain 600
    cleanup_extra_workers
}

main() {
    require_files
    cd "$PROJECT_ROOT"
    local command=${1:-}
    case "$command" in
        prepare)
            prepare_fixture "${2:-100}"
            ;;
        run)
            test -n "${2:-}" || {
                usage
                exit 2
            }
            run_scenario "$2" "${3:-100}" "${4:-1}"
            ;;
        status)
            print_status
            ;;
        cleanup)
            compose stop backend >/dev/null 2>&1 || true
            cleanup_extra_workers
            cleanup_fixture
            echo "Web Push Outbox load-test fixtures removed."
            ;;
        *)
            usage
            exit 2
            ;;
    esac
}

main "$@"
