# Parallel work and ordered merge workflow

Status: adopted by the owner on 2026-11-23. This document is the operational reference for rules 17-22
in [AGENTS.md](../../AGENTS.md).

## Goals

- Use parallel branches to shorten the time spent waiting for an earlier issue to finish.
- Keep `main` linear and reviewable by merging implementation work in issue order.
- Keep status documents trustworthy by updating them once, with final evidence, rather than editing
  them in every intermediate PR.
- Keep GitHub's required checks as the merge authority, including for dependency updates.

## Branches and worktrees

1. Start every short-lived branch from the latest `origin/main`. Fetch first.
2. Use one branch or worktree per issue or clearly separated implementation stage.
3. Keep each stage small enough to review, with Conventional Commits in English.
4. Intermediate implementation PRs reference the issue with `Refs #NNN` and do not close it.
5. Do not edit `implementation-status.md`, `project-status.md`, or `HANDOFF.md` in an intermediate
   implementation PR. The final consolidated evidence PR for the issue updates those pages and closes
   the issue with `Closes #NNN`.

Parallel work is a scheduling decision, not permission to merge out of order. A later issue can be
developed, tested, and reviewed while an earlier issue is blocked. It waits in `OPEN` until the earlier
issue is merged or the owner explicitly approves a different merge order.

## Merge order and final evidence

The default implementation merge order is ascending issue number. For example, with issues #118, #119,
#120, and #123 open, #118 merges first. Work on the other issues can proceed in parallel, but their PRs
remain open until the order allows them to merge.

Each issue has two kinds of PR:

| PR kind | Scope | Issue state | Status documents |
|---|---|---|---|
| Intermediate implementation, specification, or test PR | One reviewable stage | Issue remains `OPEN`; use `Refs #NNN` | Do not edit status pages |
| Final consolidated evidence PR | Evidence, status updates, limitation notes, and issue closure | `Closes #NNN` | Update status pages here |

If the same PR is both the only implementation stage and the final evidence stage, it may include both
sets of changes and use `Closes #NNN`. Do not create a status-only PR before local verification,
required checks, and post-merge evidence exist.

Before merging any PR:

1. Fetch `origin/main` and rebase the branch onto it.
2. Rerun the local pre-push checks listed below after the rebase.
3. Wait for every required PR check to pass. CI, not a local run, is the merge authority.
4. Merge with GitHub's rebase merge so the Conventional Commits remain individually on `main`.
5. Do not merge if the branch is stale or a required check is pending, failing, or skipped by policy.

If a rebase creates conflict with status documents, the final evidence PR for that issue owns the
conflict resolution. Intermediate PRs should avoid those files instead.

## Local pre-push checks

Run these from the repository root in this order. `git fetch` refreshes the commitlint range base; the
four checks after it are mandatory before every push, including a fix push after review:

```powershell
git fetch origin main
npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD
node tools/build/verify-all.cjs
.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache
git diff --check
```

Record the exact command and observed result in the Japanese PR verification comment. A local pass does
not replace the required checks on the pull request. `verify-all.cjs` applies the Windows non-ASCII path
workarounds; see [local verification](../../tools/build/README.md) for details. The standalone Gradle
command is intentionally included because it checks formatting drift without the script's caching
behavior.

Do not claim that a command passed unless it was run in that exact form and its exit result was
observed. If an environment failure prevents a check from running, write `未実施` with the reason and
let CI make the merge decision.

## Japanese PR evidence

Use the templates in `tools/pr-evidence/`:

- `verification-comment.ja.md` before merge: exact local commands and results, changed behavior,
  required-check state, limitations, and merge decision.
- `post-merge-evidence.ja.md` after merge: merge commit, post-merge CI/CodeQL/Scorecard results,
  status-page changes, issue state, and remaining limitations.

Replace every `{{PLACEHOLDER}}`. Do not post a template with placeholders or invented run IDs. The
templates are localized owner-facing content; repository source comments, documentation, and commit
messages remain English unless they are explicitly localized.

## Dependabot dependency lane

Dependabot PRs are a separate dependency lane. Enable GitHub auto-merge only with branch protection and
all required checks still enforced. Do not use administrator bypass, disable a check, or merge a group
of dependency PRs without checking each PR's own result.

A dependency PR whose required checks fail stays `OPEN`. Record the failing checks on the PR in
Japanese, and fix the failure later in a follow-up commit on that PR or in a separate fix PR. If a
dependency update needs a coordinated migration, remove auto-merge until the migration is ready. This
guard is part of the security-first rule: automation speeds up green PRs and never turns a red PR green.

## Stale work and exceptions

- A branch that has not been rebased after `main` advances is stale; rebase and rerun checks.
- An owner-approved merge-order exception must be recorded on the relevant issue or PR before merge.
- A security hotfix may be prepared in parallel, but it still requires owner approval, required checks,
  and an explicit merge-order decision. No bypass flags exist.
- If an issue is blocked by a pending decision in `decisions-pending.md`, later issues can proceed only
  to ready/review state unless the owner approves merging them first.
