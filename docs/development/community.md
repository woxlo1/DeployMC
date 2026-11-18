# GitHub community configuration

Last updated: 2026-11-18 (Phase 0, work package WP-0.8).

This page describes the issue, pull request, and automation files in this repository together with
the GitHub settings the owner has already applied. Anything not listed here is not configured.

## Issue forms

Plain issues remain enabled for cases no form fits. The forms live in `.github/ISSUE_TEMPLATE/`;
see [bug.yml](../../.github/ISSUE_TEMPLATE/bug.yml) for an example.

| Form | File | What it collects | Labels applied |
|---|---|---|---|
| Bug report | `bug.yml` | Version/commit, affected area, environment, installation, expected vs. observed behavior, redacted diagnostics | `type:bug`, `needs-triage` |
| Feature request | `feature.yml` | Problem and who is blocked, proposed user-visible behavior, area, alternatives and non-goals | `type:feature`, `needs-triage` |
| Compatibility report | `compatibility.yml` | Platform and exact version, Java, OS, features exercised, observed result and evidence | `needs-triage` |
| Documentation issue | `docs.yml` | Page, kind of problem, current text, concrete correction, consequences | `type:docs`, `needs-triage` |
| Translation help or review | `translation.yml` | Language, kind of request, files or keys, source and proposed text, checks run | `area:i18n`, `needs-triage` |
| Contact links | `config.yml` | Redirects questions, support, translations, and security reports to the right place | none |

The bug and compatibility forms mention `deploymc doctor`. That command is **planned but not
implemented**; both forms accept "not available" and do not ask for invented output. No form claims
that DeployMC is supported on any platform: the [support matrix](../compatibility/support-matrix.md)
is empty and no adapter exists.

## Security redirect

The first contact link points to
[private vulnerability reporting](https://github.com/woxlo1/DeployMC/security/advisories/new).
Security details must not be posted in a public issue, pull request, or discussion. See
[SECURITY.md](../../SECURITY.md). The fallback is a private GitHub message to `@woxlo1`.

## Pull requests

[`.github/PULL_REQUEST_TEMPLATE.md`](../../.github/PULL_REQUEST_TEMPLATE.md) asks for a summary, the
issue closed, the type of change, exact verification commands and results, scope/honesty checks, and
Conventional Commit compliance. It deliberately requires a writer to state what they actually ran.

[`.github/CODEOWNERS`](../../.github/CODEOWNERS) assigns every file to `@woxlo1`. This is
informational: branch protection requires **0 approving reviews**, so CODEOWNERS does not add a
review gate.

## Labels

[`.github/labels.tsv`](../../.github/labels.tsv) is the source of truth for label name, description,
and color. [`.github/workflows/labels.yml`](../../.github/workflows/labels.yml) applies it on a push
to `main` that changes the TSV or the workflow, and on manual dispatch. The job uses the `gh` CLI
preinstalled on `ubuntu-latest`, a SHA-pinned `actions/checkout`, `contents: read` plus
`issues: write`, and a cancellation group. Each row is applied with `gh label create --force`, so the
sync is idempotent.

The workflow **creates and updates only**. It never deletes a label; removing one is a deliberate
owner action taken after updating the TSV. The TSV currently mirrors the 53 labels already present
in the repository.

## Dependabot

[`.github/dependabot.yml`](../../.github/dependabot.yml) configures weekly update pull requests:

| Ecosystem | Directory | Day (Asia/Tokyo) |
|---|---|---|
| Gradle | `/` | Monday |
| npm (pnpm lockfile) | `/web` | Monday |
| npm (`package-lock.json`) | `/tools/spec` | Monday |
| Cargo | `/cli` | Tuesday |
| pip | `/sdk/python` | Tuesday |
| GitHub Actions | `/` | Wednesday |

Each entry allows at most 5 open pull requests, applies `type:chore`, and uses the
`chore(deps)` commit prefix so commitlint accepts Dependabot commits. Dependabot pull request
titles come from its own generator; if the semantic-title check rejects one, the title must be
edited manually. Dependabot alerts and the dependency graph were already enabled before this work.

GitHub reports known advisories on `main`. The count changed during this work (48, then 57, as the
dependency graph refreshed), so the [Security tab](https://github.com/woxlo1/DeployMC/security/dependabot)
is authoritative. Enabling update pull requests does not remediate them, and this work package does
not claim otherwise. Remediation is separate work.

## Settings that require owner action

These are GitHub settings, not repository files. Their current state is recorded here so it can be
checked later.

| Setting | State |
|---|---|
| Milestone `0.1.0` | Created |
| Initial label taxonomy | Created; mirrored in `.github/labels.tsv` |
| GitHub Discussions | Enabled with default categories, zero threads |
| Custom discussion categories and pinned welcome post | **Not done by owner decision** (2026-11-18) |
| Private vulnerability reporting | Enabled |
| Secret scanning and push protection | Enabled |
| Dependabot alerts and dependency graph | Enabled |
| Branch protection on `main` | Linear history, no force push or deletion, conversation resolution, admins included, 16 strict required checks |

Creating a discussion as a one-person project was considered and deliberately declined. It is not a
missing step in this work package. See [SUPPORT.md](../../SUPPORT.md) for where questions go today.

## Not in place

- No pinned discussion post or custom discussion categories.
- No compatibility evidence, so no Tier 1/2/3 entry.
- No `deploymc doctor` command.
- No automatic label deletion.
- No sponsor or funding configuration.

## Related documents

- [Support](../../SUPPORT.md)
- [Contributing](../../CONTRIBUTING.md)
- [CI and enforced checks](ci.md)
- [Implementation status](implementation-status.md)
