#!/usr/bin/env bash
set -euo pipefail

repo="100-hours-a-week/KTB4-10th-BE"

gh auth status
permission="$(gh repo view "$repo" --json viewerPermission --jq .viewerPermission)"
case "$permission" in
  WRITE|MAINTAIN|ADMIN) ;;
  *) echo "Repository write permission is required; current permission: $permission" >&2; exit 1 ;;
esac

ensure_label() {
  local name="$1" color="$2" description="$3"
  if gh label list --repo "$repo" --limit 100 --json name --jq '.[].name' | grep -Fxq "$name"; then
    printf 'exists: %s\n' "$name"
  else
    gh label create "$name" --repo "$repo" --color "$color" --description "$description"
    printf 'created: %s\n' "$name"
  fi
}

ensure_label feature 1D76DB "New feature or improvement"
ensure_label bug D73A4A "Something is not working"
ensure_label refactor 5319E7 "Behavior-preserving code improvement"
ensure_label test 0E8A16 "Test coverage or test infrastructure"
ensure_label documentation 0075CA "Documentation changes"
ensure_label chore C5DEF5 "Build, configuration, dependency, or maintenance work"
ensure_label ci FBCA04 "Continuous integration workflow changes"
ensure_label domain:member 0E8A16 "Member and authentication domain"
ensure_label domain:content 1D76DB "Tourism content domain"
ensure_label domain:guidebook 8250DF "Guidebook generation and viewing domain"
ensure_label domain:rating D4C5F9 "Rating and feedback domain"
ensure_label domain:credit F9D0C4 "Credit and entitlement domain"
ensure_label domain:common C5DEF5 "Cross-cutting backend concerns"
ensure_label domain:infra 5319E7 "Infrastructure and operations"
