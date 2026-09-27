#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${PROJECT_ROOT}/.env}"
DUMP_FILE="${1:-${PROJECT_ROOT}/data/tourism/tourism-data.sql.gz}"

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

if [[ ! -f "${DUMP_FILE}" ]]; then
  echo "Dump file not found: ${DUMP_FILE}" >&2
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

EXISTING_ROWS="$({
  MYSQL_PWD="${DB_PASSWORD}" mysql \
    --protocol=TCP \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USERNAME}" \
    --database="${DB_NAME}" \
    --batch \
    --skip-column-names \
    --execute="
      SELECT
        (SELECT COUNT(*) FROM regions)
        + (SELECT COUNT(*) FROM tourism_contents)
        + (SELECT COUNT(*) FROM event_details);
    "
} 2>/dev/null)"

if [[ "${EXISTING_ROWS}" != "0" ]]; then
  echo "Restore aborted: target tables are not empty (${EXISTING_ROWS} rows)." >&2
  echo "Use a fresh Flyway schema or clear the target data explicitly." >&2
  exit 1
fi

gzip -dc "${DUMP_FILE}" | MYSQL_PWD="${DB_PASSWORD}" mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --database="${DB_NAME}"

echo "Restored ${DUMP_FILE} into ${DB_NAME}."
MYSQL_PWD="${DB_PASSWORD}" mysql \
  --protocol=TCP \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --database="${DB_NAME}" \
  < "${PROJECT_ROOT}/scripts/database/verify-tourism-data.sql"

