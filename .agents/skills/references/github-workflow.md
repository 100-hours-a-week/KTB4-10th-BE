# GitHub workflow policy

## Repository

- Repository: `100-hours-a-week/KTB4-10th-BE`
- Default PR base: `dev`
- Merge method: Squash Merge
- Issue title: `[<Korean type>][<area>] <summary>`
- Branch: `<branch type>/<issue number>-<lowercase kebab-case slug>`
- PR title: `<commit type>(<scope>): <Korean summary>`
- Pair: `dwkim0512` and `Yonduss`

## Type mapping

| Korean type | Label | Branch and commit type |
| --- | --- | --- |
| 기능 | `feature` | `feat` |
| 버그 | `bug` | `fix` |
| 리팩터링 | `refactor` | `refactor` |
| 테스트 | `test` | `test` |
| 문서 | `documentation` | `docs` |
| 작업 | `chore` | `chore` |
| CI | `ci` | `ci` |

## Domain mapping

Every Issue has exactly one controlled domain label in addition to its type label.

| Korean area | Domain label |
| --- | --- |
| 회원 | `domain:member` |
| 관광 콘텐츠 | `domain:content` |
| 가이드북 | `domain:guidebook` |
| 평가 | `domain:rating` |
| 생성권 | `domain:credit` |
| 공통 | `domain:common` |
| 인프라 | `domain:infra` |

Do not invent a new domain during Issue creation. If none fits, ask the user whether the controlled list should change.

## Pair review defaults

- For assignee `dwkim0512`, default the reviewer to `Yonduss`.
- For assignee `Yonduss`, default the reviewer to `dwkim0512`.
- A reviewer request is the normal default, but reviewer approval is not a merge prerequisite.
- An explicitly requested valid reviewer or an explicit request to omit review overrides the default.

## Shared safety rules

Run GitHub operations from this repository. Before any mutation, verify all applicable prerequisites; do not guess identifiers or leave avoidable partial state.

1. `gh auth status` succeeds.
2. `gh repo view 100-hours-a-week/KTB4-10th-BE --json viewerPermission --jq .viewerPermission` returns `WRITE`, `MAINTAIN`, or `ADMIN`.
3. Every assignee and reviewer exists. Validate an assignee with `gh api repos/100-hours-a-week/KTB4-10th-BE/assignees/<login>` and a reviewer with `gh api repos/100-hours-a-week/KTB4-10th-BE/collaborators/<login>/permission`.
4. Every required type and domain label exists in `gh label list --repo 100-hours-a-week/KTB4-10th-BE --limit 100 --json name --jq '.[].name'`.

If authentication, permission, user, reviewer, or label validation fails, stop before creating or changing GitHub state. Report the failed prerequisite and the command the user can use to resolve it. Do not create labels during an ordinary Issue or PR request.

Use temporary files created with `mktemp` for multiline bodies and remove only those exact temporary files when done. Never place tokens, secrets, raw OAuth data, or personal information in an Issue or PR body.
