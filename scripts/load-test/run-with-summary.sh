#!/bin/sh

set -eu

scenario="$1"
script="$2"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
result_file="/results/${scenario}-${timestamp}.json"

echo "k6 summary will be saved to ${result_file}"
exec k6 run --summary-export "${result_file}" "${script}"
