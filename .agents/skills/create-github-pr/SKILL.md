---
name: create-github-pr
description: Ensure backend work has a matching GitHub Issue, then validate, test, push, and open a standardized Draft Pull Request with assignee and reviewer. Use when backend work is ready to publish as a PR, including requests where no Issue exists yet. Do not use for frontend PRs.
---

# Create a backend Draft Pull Request

Read [the shared GitHub workflow policy](../references/github-workflow.md) before acting.

## Required input

Collect only missing values. If all values are present, do not ask for confirmation.

- Issue number when one already exists; otherwise enough product intent to describe the current diff accurately
- Assignee GitHub login. Reviewer is optional; default to the other pair member and require a different login when present
- PR title in `<type>(<scope>): <Korean summary>` format
- Change summary and implementation reason
- Requirement IDs (or `해당 없음`), test scope, impact, and review points

## Resolve or create the Issue

1. Inspect `git status`, the current branch, commits, and `git diff origin/dev...HEAD` before asking for an Issue number.
2. Search open Issues for an actual match. Never attach work to a merely similar Issue.
3. If a matching Issue exists, use it and verify it is open.
4. If none exists, derive a proposed type, controlled domain, title, purpose, scope, acceptance criteria, requirement IDs, and impact from the diff and user conversation. Ask only for material intent that cannot be proven; never invent requirements or acceptance criteria.
5. Run all Issue preflight checks, then create and assign the Issue with both type and domain labels. This is an intentional Issue-only variant of `create-github-issue`: do not create a second work branch because implementation already exists.
6. If the current branch does not include the new Issue number, rename it to `<type>/<issue-number>-<slug>` only when it is local and has no upstream. If it is already published, do not delete or rename the remote branch automatically; report the naming exception and continue only when the Issue link in the PR body is unambiguous.

If Issue creation fails, stop before tests, push, or PR creation. Keep a successfully created Issue if a later branch rename or PR step fails.

## Preflight

Run all shared checks before mutation. Also verify:

- The Issue is open with `gh issue view <number> --repo 100-hours-a-week/KTB4-10th-BE --json state,url`.
- The assignee is valid. If a reviewer is present, assignee and reviewer are different and the reviewer has repository access.
- The worktree is clean before testing.
- The current branch is not `main` or `dev` and normally matches `^(feat|fix|refactor|test|docs|chore|ci)/<issue-number>-[a-z0-9]+(?:-[a-z0-9]+)*$`. Only the already-published exception above may bypass the name check.
- The PR title type matches the branch type.
- No open PR already exists for the current head branch.

Stop before tests, push, or PR creation if any preflight check fails.

## Workflow

1. Run these commands separately and in order: `./gradlew test`, `./gradlew checkstyleMain`, `./gradlew spotbugsMain`.
2. Stop at the first failure. Report that command and its relevant failure output; do not push or create a PR.
3. Build the PR body using `.github/PULL_REQUEST_TEMPLATE.md`. It must contain `Close #<issue-number>`, requirement IDs, changes and reason, all three test commands and results, API/data/feature/document impact, and review points.
4. Run `git push -u origin HEAD`.
5. Create one Draft PR targeting `dev` with `gh pr create --repo 100-hours-a-week/KTB4-10th-BE --base dev --head <branch> --draft --title ... --body-file ... --assignee ...` and add `--reviewer ...` when review is not explicitly omitted.
6. Return the PR URL, base/head, Draft status, linked Issue, assignee, reviewer, and the three successful checks.

The repository workflow `.github/workflows/close-issue-on-dev-merge.yml` closes the Issue only after the PR is actually merged into `dev`. It requires the branch Issue number and the explicit `Close #<issue-number>` in the PR body to match. Do not close the Issue merely because a Draft PR was created.

If push succeeds but PR creation fails, do not retry blindly or delete the remote branch. Report the pushed branch and exact failure so the user can safely retry.
