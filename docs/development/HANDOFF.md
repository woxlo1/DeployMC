# Handoff

Last updated: 2026-11-18. Read [AGENTS.md](../../AGENTS.md) first, then this file and
[project status](project-status.md).

## What was just done

- WP-0.2 (issue #2) was completed in PR #131: `docs/research/` now contains the naming check,
  competitive analysis, and license comparison. All 15 required checks passed before merge.
- WP-0.7 (issue #7) was verified as already delivered by PR #18 and closed. Its failure probes and
  green runs are documented on the issue and PR.
- WP-0.10 (issue #10) adds this tracking document set: project, compatibility, i18n, quality,
  risks, v1 checklist, and handoff.
- WP-1.1 (issue #41) adds `CapabilityCatalog` and `CapabilityDefinition` in `core`, plus a
  `FakePlatformAdapter` test double and 16 new tests. `.\gradlew.bat build verifyJava8` passed on
  2026-11-18 through the ASCII junction: 48 Java tests, 0 failures, Java 8 bytecode check passed.
- WP-1.2 (issue #42) work is in progress on branch `feature/wp-1.2-bukkit-adapter` (commits
  `81ada82`, `cbda96b`, `a445a61`). Added `platform/bukkit`: generated `plugin.yml`
  (`api-version: '1.13'`, no `folia-supported`), `DeployMCPlugin`, `BukkitPlatformAdapter`,
  localized startup banner/messages, capability map, and task cleanup. Verified with
  `.\gradlew.bat build verifyJava8 --console=plain` through the ASCII junction: 70 Java tests,
  0 failures, Java 8 bytecode check passed. `node tools/build/verify-all.cjs` reported `PASS`
  for `java`, `web`, `cli`, `python`, `spec`, and `i18n`. Real Paper-latest load and clean-disable
  thread-dump verification were **not** performed; that is deferred to #48.

## In flight

- 17 open Dependabot PRs (#23-#39), tracked for triage by #121. Do not merge them in bulk.
- Open issues: 89. Milestones `0.1.0` through `0.10.0` exist.
- PR #134 carries the WP-1.2 work described above and is awaiting its required checks.

## Blocked

- Nothing blocks the next documentation or Phase 1 work.
- Owner decisions D-001 through D-007 in [decisions pending](decisions-pending.md) affect later
  compatibility, Cloudflare, distribution, and community work, not Phase 0.

## Exact next steps

1. Wait for all 15 required checks on PR #134 to pass.
2. Squash-merge only after CI is green, then tell #48 that real-server load and thread-dump checks remain.
3. Continue in owner order: #43-#49, then category 3 (#115-#120), category 4 (#121-#123), and
   category 5 (#124-#130).
4. Update project status, implementation status, and this handoff at the end of each session.

## Gotchas

- On this Windows host, the repository path contains non-ASCII characters. Use the ASCII junction
  for Gradle and set `CARGO_TARGET_DIR` to an ASCII path for Cargo; see [environment](environment.md).
  The one-command `node tools/build/verify-all.cjs` handles both.
- `gh milestone list` is not supported; query `gh api repos/woxlo1/DeployMC/milestones`.
- `gh` commands with `--jq` need careful quoting in PowerShell; prefer `--json` plus
  `ConvertFrom-Json` when a filter fails.
- Discussions stay enabled with default categories and zero threads. Do not create custom
  categories or a pinned post.
- The license decision is closed: Apache-2.0, ADR-0013. Do not reopen it without owner approval.
- Never claim originality from the naming check; exact-name GitHub repositories exist.
