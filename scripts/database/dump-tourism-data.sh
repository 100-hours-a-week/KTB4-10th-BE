#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${PROJECT_ROOT}/.env}"
OUTPUT_FILE="${1:-${PROJECT_ROOT}/data/tourism/tourism-data.sql.gz}"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

if [[ "${DB_URL}" != jdbc:mysql://* ]]; then
  echo "DB_URL must start with jdbc:mysql://" >&2
  exit 1
fi

DB_TARGET="${DB_URL#jdbc:mysql://}"
DB_HOST_PORT="${DB_TARGET%%/*}"
DB_NAME_QUERY="${DB_TARGET#*/}"
DB_NAME="${DB_NAME_QUERY%%\?*}"
DB_HOST="${DB_HOST_PORT%%:*}"
DB_PORT="${DB_HOST_PORT##*:}"

if [[ "${DB_HOST}" == "${DB_PORT}" ]]; then
  DB_PORT="3306"
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"
TEMP_FILE="$(mktemp "${TMPDIR:-/tmp}/kgb-tourism-data.XXXXXX.sql")"
trap 'rm -f "${TEMP_FILE}"' EXIT

LEGACY_CATEGORY_COUNT="$(MYSQL_PWD="${DB_PASSWORD}" mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --database="${DB_NAME}" \
  --batch \
  --skip-column-names \
  --execute="
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tourism_contents'
      AND column_name = 'category';
  ")"

if [[ "${LEGACY_CATEGORY_COUNT}" != "0" ]]; then
  echo "Dump aborted: the source schema still contains tourism_contents.category." >&2
  echo "Apply the latest Flyway migration before creating a dump." >&2
  exit 1
fi

MYSQL_PWD="${DB_PASSWORD}" mysqldump \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --single-transaction \
  --quick \
  --no-create-info \
  --skip-triggers \
  --skip-lock-tables \
  --no-tablespaces \
  --set-gtid-purged=OFF \
  --column-statistics=0 \
  --hex-blob \
  --complete-insert \
  "${DB_NAME}" \
  regions tourism_contents event_details > "${TEMP_FILE}"

gzip -9 -c "${TEMP_FILE}" > "${OUTPUT_FILE}"

echo "Created ${OUTPUT_FILE}"
echo "Source row counts:"
MYSQL_PWD="${DB_PASSWORD}" mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --database="${DB_NAME}" \
  --batch \
  --skip-column-names \
  --execute="
    SELECT 'regions', COUNT(*) FROM regions
    UNION ALL
    SELECT 'tourism_contents', COUNT(*) FROM tourism_contents
    UNION ALL
    SELECT 'event_details', COUNT(*) FROM event_details;
  "
