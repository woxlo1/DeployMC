# Handoff

Last updated: 2026-11-19. Read [AGENTS.md](../../AGENTS.md) first, then this file and
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
- WP-1.2 (issue #42) was completed in PR #134 and merged at `34e94f5`. Added `platform/bukkit`:
  generated `plugin.yml` (`api-version: '1.13'`, no `folia-supported`), `DeployMCPlugin`,
  `BukkitPlatformAdapter`, localized startup banner/messages, capability map, and task cleanup.
  Verified with `.\gradlew.bat build verifyJava8 --console=plain` through the ASCII junction:
  70 Java tests, 0 failures, Java 8 bytecode check passed. `node tools/build/verify-all.cjs`
  reported `PASS` for `java`, `web`, `cli`, `python`, `spec`, and `i18n`. Real Paper-latest load
  and clean-disable thread-dump verification were **not** performed; they are recorded on the
  compatibility harness issue.
- WP-1.3 (issue #43) was completed in PR #135 and merged at `dbb32ae`; the issue closed. ADR-0003
  accepted Netty 4.1.141.Final after the recorded spike, and `transport/` now has internal
  interfaces, `NettyTransportServer`, config/policy/logging types, and 9 integration tests. The
  Shadow JAR relocates Netty and excludes the unused BlockHound service file. Verified with
  `.\gradlew.bat build verifyJava8 --console=plain` through the ASCII junction: 79 Java tests,
  0 failures, Java 8 bytecode check passed. `node tools/build/verify-all.cjs` reported `PASS`
  for all six toolchains. Post-merge `main` CI, CodeQL, and Scorecard runs all passed. No Minecraft
  server was started and no product routes are registered.
- WP-1.4 (issue #44) was completed in PR #137 and merged at `4548b4b`; the issue closed. `StaticAssetHandler`
  serves only `deploymc-web/` classpath resources with safe decoding, traversal rejection, SPA fallback
  that never shadows `/api`, correct content types, hashed-asset immutable caching, worker-capable HTML
  CSP, and precompressed `.br`/`.gz` variants. The Web build now generates those variants, the plugin
  build packages `web/dist` under `deploymc-web/`, and CI plus `verify-all.cjs` build Web before Java.
  Verified locally with `gradlew build verifyJava8 --console=plain` (93 Java tests, 0 failures),
  `pnpm test` (14 tests), `node tools/build/verify-all.cjs` (all six toolchains `PASS`), and JAR entry
  inspection. All 17 pull-request checks and the post-merge `main` CI, CodeQL, and Scorecard runs
  passed. No browser, real server, or product route was tested.
- WP-1.5 (issue #45) is in progress. Its first stage merged in PR #139 at `5fc04a1`: a hardened
  SnakeYAML 2.9 loader, schema validation with localized 1-based diagnostics, default generation,
  timestamped migration backups, atomic writes, immutable `ConfigView`, safe reload, and a 250-case
  deterministic fail-closed property test. SnakeYAML is relocated in the Shadow JAR. Verified on
  2026-11-18 through the ASCII junction with `gradlew spotlessApply :core:test` (53 core tests),
  `gradlew build verifyJava8` (121 Java tests, 0 failures, Java 8 check passed), and JAR inspection
  (254 relocated, 0 unrelocated SnakeYAML classes). All 16 required PR checks and the post-merge
  `main` CI, CodeQL, and Scorecard runs passed. Bukkit startup wiring and `/deploymc` commands are
  deliberately deferred to a second PR.
- The real Paper conflicting-library classloader check for #48 was recorded on
  [issue #48](https://github.com/woxlo1/DeployMC/issues/48#issuecomment-6975215752). The transport
  spike and JAR inspection are not a support claim.

## In flight

- This documentation PR records the PR #139 merge evidence. The command-stage branch has not been
  created yet.
- 17 open Dependabot PRs (#23-#39), tracked for triage by #121. Do not merge them in bulk.
- Open issues: 86. Milestones `0.1.0` through `0.10.0` exist.

## Blocked

- Nothing blocks the next documentation or Phase 1 work.
- Owner decisions D-001 through D-007 in [decisions pending](decisions-pending.md) affect later
  compatibility, Cloudflare, distribution, and community work, not Phase 0.

## Exact next steps

1. Merge this documentation PR after the required checks pass.
2. Create the second WP-1.5 branch from the updated `main` and implement
   `/deploymc status|reload|doctor|token`, permission nodes, localized command messages, and Bukkit
   startup/reload wiring.
3. Verify, open a PR, merge after required checks pass, and only then close #45.
4. Continue in owner order with #46-#49, category 3 (#115-#120), category 4 (#121-#123), and
   category 5 (#124-#130).
5. Update project status, implementation status, and this handoff at the end of each session.

## Gotchas

- On this Windows host, the repository path contains non-ASCII characters. Use the ASCII junction
  for Gradle and set `CARGO_TARGET_DIR` to an ASCII path for Cargo; see [environment](environment.md).
  The one-command `node tools/build/verify-all.cjs` handles both.
- `gh milestone list` is not supported; query `gh api repos/woxlo1/DeployMC/milestones`.
- Conventional Commit body and footer lines must be at most 120 characters; PR #139 needed an amend
  because `Refs #45` was parsed as a footer on an over-long line.
- `gh` commands with `--jq` need careful quoting in PowerShell; prefer `--json` plus
  `ConvertFrom-Json` when a filter fails.
- Discussions stay enabled with default categories and zero threads. Do not create custom
  categories or a pinned post.
- The license decision is closed: Apache-2.0, ADR-0013. Do not reopen it without owner approval.
- Never claim originality from the naming check; exact-name GitHub repositories exist.
