# Dependabot Pull Request Triage

Last updated: 2026-11-24 (UTC).

This page records the individual triage of the open Dependabot pull requests #23-#39 against
`main` at `671a702e5cb8ee3c965f59461df1a43dcabfe53e`. It implements issue #121 and follows the
[dependency policy](dependency-policy.md). It is evidence and a decision record, not a claim that a
dependency was upgraded, that an advisory was remediated, or that a support tier was earned.
Issue #122 remains responsible for advisory remediation.

## Method

For each pull request, the following commands were run from the repository root. The exact outputs
are quoted in the per-pull-request sections below.

```text
gh pr view <number> --json number,title,state,mergeable,headRefName,commits
gh pr diff --stat <number>
gh pr checks <number> --json name,state,link
gh run view <run-id> --log-failed                 (only for failing checks)
gh api repos/woxlo1/DeployMC/pulls/<number> --jq .auto_merge
```

Official upstream release notes or documentation were checked for every version change. Each source
is listed with an access date in `YYYY-MM-DD`. A green check alone was not treated as approval.

## Classification definitions

| Classification | Meaning |
|---|---|
| SAFE_TO_MERGE | All checks green, low-risk patch or minor update, no breaking change or new permission. Still requires an individual reviewed PR merge; never a bulk merge. |
| BREAKING_CHANGE_REVIEW | Major version, migration, baseline change, or possible breaking behavior. Requires explicit owner review before merge. |
| SUPERSEDED | Another open pull request or the current `main` already carries the same change. |
| CLOSE_WITH_REASON | Stale, invalid, or conflicting change with a recorded, justified reason. |
| KEEP_OPEN_FAILING | At least one required check failed. The pull request stays open for a later fix; no gate is disabled. |

## Repository controls observed

`gh api repos/woxlo1/DeployMC/branches/main/protection` on 2026-11-24 returned `strict: true` for
required status checks, `enforce_admins: true`, `required_conversation_resolution: true`,
`allow_force_pushes: false`, and `allow_deletions: false`. Eighteen required status-check contexts
were listed. `gh api repos/woxlo1/DeployMC` returned `allow_auto_merge: true` and
`allow_rebase_merge: true`. Repository auto-merge is therefore guarded by branch protection, not a
blanket approval; no setting was changed by this triage.

## Triage summary

<!-- TRIAGE_SUMMARY -->

## Per-pull-request evidence

<!-- PER_PR_EVIDENCE -->

## Decisions and owner actions

<!-- DECISIONS -->

## Limitations

- No pull request was merged, closed, rebased, or approved as part of this evidence. No required
  check, branch-protection rule, auto-merge guard, dependency manifest, or lockfile was changed.
- A failing pull request remains open by policy. `NOT RUN` items are identified explicitly in the
  per-pull-request sections rather than inferred from a green check.
- The GitHub Security tab is authoritative for current advisories; this page does not freeze a
  vulnerability count or claim that an advisory is fixed.
- No running server, browser, E2E run, full integration suite, or in-game command was used for this
  documentation-only triage.
