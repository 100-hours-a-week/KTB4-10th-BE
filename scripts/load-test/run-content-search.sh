#!/bin/sh

set -eu

allowed_source_codes="'NA01','NA02','NA03','NA04','VE03','NA05','HS01','HS02','HS03','HS04','VE01','VE02','VE04','VE07','EX01','EX02','EX03','EX04','EX05','EX06','EX07','LS01','LS02','LS03','LS04'"

compose() {
  docker compose --env-file .env.loadtest -f compose.loadtest.yml "$@"
}

mysql_value() {
  compose exec -T mysql mysql \
    --batch --skip-column-names \
    -ukgb -pkgb-local-password kgb_local \
    -e "$1" | head -n 1
}

require_environment() {
  if [ ! -f .env.loadtest ]; then
    echo ".env.loadtest is missing. Copy .env.loadtest.example first." >&2
    exit 2
  fi
  running_services=$(compose ps --status running --services)
  for required_service in mysql backend; do
    if ! printf '%s\n' "$running_services" | grep -qx "$required_service"; then
      echo "Service '$required_service' is not running. Start the load-test environment first:" >&2
      echo "docker compose --env-file .env.loadtest -f compose.loadtest.yml up -d --build --wait" >&2
      exit 2
    fi
  done
}

discover_inputs() {
  common_query="${SEARCH_COMMON_QUERY:-$(mysql_value "
    SELECT LEFT(TRIM(content.title), 1)
    FROM tourism_contents content
    LEFT JOIN event_details event ON event.content_id = content.id
    WHERE content.status = 'ACTIVE'
      AND content.deleted_at IS NULL
      AND content.location IS NOT NULL
      AND TRIM(content.title) REGEXP '^[가-힣]'
      AND (
        (content.classification_code_1 = 'EV'
         AND event.start_date IS NOT NULL
         AND event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR))
        OR ((content.classification_code_1 IS NULL OR content.classification_code_1 <> 'EV')
            AND content.classification_code_2 IN ($allowed_source_codes))
      )
    GROUP BY LEFT(TRIM(content.title), 1)
    ORDER BY COUNT(*) DESC, LEFT(TRIM(content.title), 1)
    LIMIT 1;
  ")}"
  rare_query="${SEARCH_RARE_QUERY:-$(mysql_value "
    SELECT LEFT(TRIM(content.title), 10)
    FROM tourism_contents content
    LEFT JOIN event_details event ON event.content_id = content.id
    WHERE content.status = 'ACTIVE'
      AND content.deleted_at IS NULL
      AND content.location IS NOT NULL
      AND CHAR_LENGTH(TRIM(content.title)) BETWEEN 2 AND 10
      AND (
        (content.classification_code_1 = 'EV'
         AND event.start_date IS NOT NULL
         AND event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR))
        OR ((content.classification_code_1 IS NULL OR content.classification_code_1 <> 'EV')
            AND content.classification_code_2 IN ($allowed_source_codes))
      )
    ORDER BY content.created_at DESC, content.id DESC
    LIMIT 1;
  ")}"
  region_code="${SEARCH_REGION_CODE:-$(mysql_value "
    SELECT province.administrative_code
    FROM tourism_contents content
    JOIN regions region ON region.id = content.region_id
    JOIN regions province ON province.id = COALESCE(region.parent_id, region.id)
    LEFT JOIN event_details event ON event.content_id = content.id
    WHERE content.status = 'ACTIVE'
      AND content.deleted_at IS NULL
      AND content.location IS NOT NULL
      AND (
        (content.classification_code_1 = 'EV'
         AND event.start_date IS NOT NULL
         AND event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR))
        OR ((content.classification_code_1 IS NULL OR content.classification_code_1 <> 'EV')
            AND content.classification_code_2 IN ($allowed_source_codes))
      )
    GROUP BY province.administrative_code
    ORDER BY COUNT(*) DESC, province.administrative_code
    LIMIT 1;
  ")}"
  month="${SEARCH_MONTH:-$(mysql_value "SELECT DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 9 HOUR, '%Y-%m');")}"
  no_result_query="${SEARCH_NO_RESULT_QUERY:-결과없음}"

  if [ -z "$common_query" ] || [ -z "$rare_query" ] || [ -z "$region_code" ]; then
    echo "Could not discover search inputs. Confirm that tourism_contents and regions contain load-test data." >&2
    exit 2
  fi
}

show_inputs() {
  printf '%s\n' \
    "SEARCH_COMMON_QUERY=$common_query" \
    "SEARCH_RARE_QUERY=$rare_query" \
    "SEARCH_NO_RESULT_QUERY=$no_result_query" \
    "SEARCH_REGION_CODE=$region_code" \
    "SEARCH_MONTH=$month"
}

run_scenario() {
  mode="$1"
  vus="$2"
  duration="$3"
  smoke="${4:-false}"
  result_name="content-search-${mode}-vu${vus}"

  compose --profile loadtest run --rm \
    -e K6_SCRIPT=scenarios/search-contents.js \
    -e K6_RESULT_NAME="$result_name" \
    -e K6_TAGS="environment:local-loadtest,testid:$result_name" \
    -e VUS="$vus" \
    -e SEARCH_MODE="$mode" \
    -e SEARCH_DURATION="$duration" \
    -e SMOKE="$smoke" \
    -e SEARCH_COMMON_QUERY="$common_query" \
    -e SEARCH_RARE_QUERY="$rare_query" \
    -e SEARCH_NO_RESULT_QUERY="$no_result_query" \
    -e SEARCH_REGION_CODE="$region_code" \
    -e SEARCH_MONTH="$month" \
    -e SEARCH_DEEP_PAGE="${SEARCH_DEEP_PAGE:-50}" \
    k6-custom
}

explain_queries() {
  escaped_query=$(printf '%s' "$common_query" | sed "s/'/''/g")
  escaped_region=$(printf '%s' "$region_code" | sed "s/'/''/g")
  compose exec -T mysql mysql -ukgb -pkgb-local-password kgb_local -e "
    SET @query = '$escaped_query';
    SET @region_code = '$escaped_region';
    SET @month_start = STR_TO_DATE(CONCAT('$month', '-01'), '%Y-%m-%d');
    SET @month_end = LAST_DAY(@month_start);

    EXPLAIN ANALYZE
    SELECT content.id
    FROM tourism_contents content
    JOIN regions region ON region.id = content.region_id
    JOIN regions province ON province.id = COALESCE(region.parent_id, region.id)
    LEFT JOIN event_details event ON event.content_id = content.id
    WHERE content.status = 'ACTIVE'
      AND content.deleted_at IS NULL
      AND content.location IS NOT NULL
      AND (
        (content.classification_code_1 = 'EV'
         AND event.start_date IS NOT NULL
         AND event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR))
        OR ((content.classification_code_1 IS NULL OR content.classification_code_1 <> 'EV')
            AND content.classification_code_2 IN ($allowed_source_codes))
      )
      AND LOCATE(@query, content.title) > 0
      AND province.administrative_code = @region_code
      AND (
        @month_start IS NULL
        OR content.classification_code_1 <> 'EV'
        OR (event.start_date <= @month_end AND event.end_date >= @month_start)
      )
    ORDER BY content.created_at DESC, content.id DESC
    LIMIT 20 OFFSET 0;

    EXPLAIN ANALYZE
    SELECT COUNT(*)
    FROM tourism_contents content
    JOIN regions region ON region.id = content.region_id
    JOIN regions province ON province.id = COALESCE(region.parent_id, region.id)
    LEFT JOIN event_details event ON event.content_id = content.id
    WHERE content.status = 'ACTIVE'
      AND content.deleted_at IS NULL
      AND content.location IS NOT NULL
      AND (
        (content.classification_code_1 = 'EV'
         AND event.start_date IS NOT NULL
         AND event.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR))
        OR ((content.classification_code_1 IS NULL OR content.classification_code_1 <> 'EV')
            AND content.classification_code_2 IN ($allowed_source_codes))
      )
      AND LOCATE(@query, content.title) > 0
      AND province.administrative_code = @region_code
      AND (
        @month_start IS NULL
        OR content.classification_code_1 <> 'EV'
        OR (event.start_date <= @month_end AND event.end_date >= @month_start)
      );
  "
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/load-test/run-content-search.sh discover
  ./scripts/load-test/run-content-search.sh smoke
  ./scripts/load-test/run-content-search.sh run <baseline|filter|deep|mixed|validation> <vus> [duration]
  ./scripts/load-test/run-content-search.sh explain

Examples:
  ./scripts/load-test/run-content-search.sh smoke
  ./scripts/load-test/run-content-search.sh run baseline 10 1m
  ./scripts/load-test/run-content-search.sh run mixed 50 2m
EOF
}

require_environment
discover_inputs

case "${1:-}" in
  discover)
    show_inputs
    ;;
  smoke)
    show_inputs
    run_scenario baseline 1 10s true
    ;;
  run)
    if [ "$#" -lt 3 ]; then
      usage
      exit 2
    fi
    show_inputs
    run_scenario "$2" "$3" "${4:-1m}" false
    ;;
  explain)
    show_inputs
    explain_queries
    ;;
  *)
    usage
    exit 2
    ;;
esac
