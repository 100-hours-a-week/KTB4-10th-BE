#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <old-areaBasedList2.json> <new-areaBasedList2.json> <output-directory>" >&2
  exit 1
fi

OLD_FILE=$1
NEW_FILE=$2
OUTPUT_DIR=$3

for command in jq sort comm join awk; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

for file in "${OLD_FILE}" "${NEW_FILE}"; do
  [[ -f "${file}" ]] || {
    echo "Snapshot not found: ${file}" >&2
    exit 1
  }

  jq -e '
    .response.header.resultCode == "0000"
    and (.response.body.items.item | type == "array")
    and (.response.body.totalCount == (.response.body.items.item | length))
  ' "${file}" >/dev/null || {
    echo "Invalid or incomplete TourAPI snapshot: ${file}" >&2
    exit 1
  }
done

WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/tourapi-diff.XXXXXX")
trap 'rm -rf "${WORK_DIR}"' EXIT

extract_rows() {
  jq -r '
    .response.body.items.item[]
    | [
        .contentid,
        (if (.lclsSystm1 // "") == "" then "UNCLASSIFIED" else .lclsSystm1 end),
        (.modifiedtime // ""),
        (.title // "")
      ]
    | @tsv
  ' "$1" | sort -t $'\t' -k1,1
}

extract_rows "${OLD_FILE}" > "${WORK_DIR}/old.tsv"
extract_rows "${NEW_FILE}" > "${WORK_DIR}/new.tsv"

cut -f1 "${WORK_DIR}/old.tsv" > "${WORK_DIR}/old-ids.txt"
cut -f1 "${WORK_DIR}/new.tsv" > "${WORK_DIR}/new-ids.txt"

comm -13 "${WORK_DIR}/old-ids.txt" "${WORK_DIR}/new-ids.txt" \
  > "${WORK_DIR}/added-ids.txt"
comm -23 "${WORK_DIR}/old-ids.txt" "${WORK_DIR}/new-ids.txt" \
  > "${WORK_DIR}/removed-ids.txt"

join -t $'\t' \
  "${WORK_DIR}/old.tsv" \
  "${WORK_DIR}/new.tsv" \
  > "${WORK_DIR}/joined.tsv"

awk -F '\t' '$3 != $6' "${WORK_DIR}/joined.tsv" \
  > "${WORK_DIR}/modified-rows.tsv"

mkdir -p "${OUTPUT_DIR}"

{
  printf 'classification\tcontent_id\ttitle\tmodified_at\n'
  awk -F '\t' '
    NR == FNR { selected[$1] = 1; next }
    $1 in selected { print $2 "\t" $1 "\t" $4 "\t" $3 }
  ' "${WORK_DIR}/added-ids.txt" "${WORK_DIR}/new.tsv"
} > "${OUTPUT_DIR}/added.tsv"

{
  printf 'classification\tcontent_id\ttitle\tlast_modified_at\n'
  awk -F '\t' '
    NR == FNR { selected[$1] = 1; next }
    $1 in selected { print $2 "\t" $1 "\t" $4 "\t" $3 }
  ' "${WORK_DIR}/removed-ids.txt" "${WORK_DIR}/old.tsv"
} > "${OUTPUT_DIR}/removed.tsv"

{
  printf 'classification\tcontent_id\ttitle\told_modified_at\tnew_modified_at\told_classification\n'
  awk -F '\t' '{ print $5 "\t" $1 "\t" $7 "\t" $3 "\t" $6 "\t" $2 }' \
    "${WORK_DIR}/modified-rows.tsv"
} > "${OUTPUT_DIR}/modified.tsv"

awk -F '\t' '
  FILENAME == ARGV[1] { old[$2]++; categories[$2] = 1; next }
  FILENAME == ARGV[2] { current[$2]++; categories[$2] = 1; next }
  FILENAME == ARGV[3] {
    if (FNR > 1) { added[$1]++; categories[$1] = 1 }
    next
  }
  FILENAME == ARGV[4] {
    if (FNR > 1) { removed[$1]++; categories[$1] = 1 }
    next
  }
  FILENAME == ARGV[5] {
    if (FNR > 1) { modified[$1]++; categories[$1] = 1 }
    next
  }
  END {
    print "classification,old_count,new_count,net_change,added,removed,modified"
    for (category in categories) {
      printf "%s,%d,%d,%+d,%d,%d,%d\n",
        category,
        old[category],
        current[category],
        current[category] - old[category],
        added[category],
        removed[category],
        modified[category]
    }
  }
' \
  "${WORK_DIR}/old.tsv" \
  "${WORK_DIR}/new.tsv" \
  "${OUTPUT_DIR}/added.tsv" \
  "${OUTPUT_DIR}/removed.tsv" \
  "${OUTPUT_DIR}/modified.tsv" \
  | { read -r header; printf '%s\n' "${header}"; sort; } \
  > "${OUTPUT_DIR}/summary.csv"

OLD_COUNT=$(wc -l < "${WORK_DIR}/old-ids.txt" | tr -d ' ')
NEW_COUNT=$(wc -l < "${WORK_DIR}/new-ids.txt" | tr -d ' ')
ADDED_COUNT=$(wc -l < "${WORK_DIR}/added-ids.txt" | tr -d ' ')
REMOVED_COUNT=$(wc -l < "${WORK_DIR}/removed-ids.txt" | tr -d ' ')
MODIFIED_COUNT=$(wc -l < "${WORK_DIR}/modified-rows.tsv" | tr -d ' ')

cat > "${OUTPUT_DIR}/README.md" <<EOF
# TourAPI areaBasedList2 snapshot diff

- Old snapshot: \`${OLD_FILE}\`
- New snapshot: \`${NEW_FILE}\`
- Old count: ${OLD_COUNT}
- New count: ${NEW_COUNT}
- Added: ${ADDED_COUNT}
- Removed from current list: ${REMOVED_COUNT}
- Modified (\`modifiedtime\` changed): ${MODIFIED_COUNT}

\`removed\` means that the content ID is absent from the new complete snapshot. It does
not by itself prove permanent deletion, so do not physically delete referenced content.

## Files

- \`summary.csv\`: counts by every \`lclsSystm1\` classification
- \`added.tsv\`: newly observed content IDs
- \`removed.tsv\`: IDs absent from the new snapshot
- \`modified.tsv\`: common IDs whose \`modifiedtime\` changed
EOF

cat "${OUTPUT_DIR}/summary.csv"
echo
echo "Diff written to ${OUTPUT_DIR}"
