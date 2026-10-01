---
name: create-github-issue
description: Create and assign a standardized KGB backend GitHub Issue, then create its local work branch. Use for requests to register backend features, bugs, refactors, tests, docs, chores, or CI work in GitHub. Do not use for Pull Requests or frontend Issues.
---

# Create a backend GitHub Issue

Read [the shared GitHub workflow policy](../references/github-workflow.md) before acting.

## Required input

Collect only missing values. If all values are present, do not ask for confirmation.

- One of the seven Korean Issue types in the mapping
- One controlled area from the domain mapping and a concise Korean summary
- GitHub assignee login
- Lowercase ASCII kebab-case branch slug
- Purpose, scope, acceptance criteria, requirement IDs (or `해당 없음`), and impact
- Type-specific information required by the matching file in `.github/ISSUE_TEMPLATE/`

## Workflow

1. Confirm the worktree root is this backend repository and run every shared preflight check. Validate both the type label and domain label plus the assignee before creating anything.
2. Build the exact title `[<type>][<area>] <summary>` and a Markdown body with headings matching the selected Issue Form. Preserve meaningful user wording; never invent requirement IDs or acceptance criteria.
3. Create and assign the Issue in one command with `gh issue create --repo 100-hours-a-week/KTB4-10th-BE --title ... --body-file ... --label <type-label> --label <domain-label> --assignee ...`. Capture the returned URL and parse its numeric Issue number.
4. After Issue creation, check `git status --porcelain`. If it is nonempty, keep the Issue, do not fetch or switch branches, and report the Issue URL plus the dirty-worktree reason.
5. Run `git fetch origin dev`. Form `<branch-type>/<issue-number>-<slug>`. If that local branch already exists, keep the Issue and stop without switching. Otherwise run `git switch -c <branch> origin/dev`.
6. Return the Issue URL, assignee, both labels, and created local branch. Clearly distinguish full success from an Issue-created/branch-not-created partial result.

Never push the branch in this workflow. Never delete or close an Issue to compensate for a branch creation failure.
