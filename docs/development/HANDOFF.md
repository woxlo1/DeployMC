# Handoff

Last updated: 2026-11-20. Read [AGENTS.md](../../AGENTS.md) first, then this file and
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
  reported `PASS` for `java`, `web`, `cli`, `python`, `spec`, and `i18n`. Real Paper load and
  clean-disable thread dumps were not performed at that stage; the pinned WP-1.8 smoke entry later
  covered both.
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
- WP-1.5 (issue #45) is complete and the issue is closed. Its first stage merged in PR #139 at
  `5fc04a1`: a hardened SnakeYAML loader (2.9 at the time, 2.7 after WP-1.7), schema validation
  with localized 1-based diagnostics, default generation, timestamped migration backups, atomic
  writes, immutable `ConfigView`, safe
  reload, and a 250-case deterministic fail-closed property test. SnakeYAML is relocated in the Shadow
  JAR. Verified on 2026-11-18 through the ASCII junction with `gradlew spotlessApply :core:test` (53
  core tests), `gradlew build verifyJava8` (121 Java tests, 0 failures, Java 8 check passed), and JAR
  inspection (254 relocated, 0 unrelocated SnakeYAML classes). All 16 required PR checks and the
  post-merge `main` CI, CodeQL, and Scorecard runs passed. The second stage merged in PR #141 at
  `f853136`: config-driven Bukkit startup, `/deploymc status|reload|doctor|token`, five operator-only
  permission nodes, localized English/Japanese command catalogs, a server-free `ConfigCommandHandler`,
  and tab completion. Verified on 2026-11-19 with `gradlew spotlessApply :platform:bukkit:test` (42
  Bukkit tests), `gradlew spotlessApply build verifyJava8` (133 Java tests, 0 failures, Java 8 check
  passed), `node tools/i18n/check`, and JAR descriptor inspection. `doctor` reports only implemented
  checks and explicitly disclaims a support-tier verdict; `token` is console-only and creates nothing.
  All required PR checks passed. Post-merge CI and Scorecard passed; the first CodeQL post-merge
  autobuild failed on plugin dependency resolution from `plugins.gradle.org`, and a rerun passed all
  three language analyses. Evidence PR #142 merged at `682e32d`, its required checks and post-merge
  CI, CodeQL, and Scorecard runs passed, and issue #45 was closed. No real server load or in-game
  command execution was performed.
- WP-1.6 (issue #46) is complete and the issue is closed. PR #144 merged at `d38217f`: a manual
  `.properties` loader with per-key sender/server-default/English fallback, named placeholders,
  catalog diagnostics, reflective sender locale detection, and legacy color translation only at the
  Bukkit send boundary. Verified on 2026-11-19 through the ASCII junction with
  `gradlew spotlessApply :platform:bukkit:test build verifyJava8` (50 Bukkit tests; 143 total Java
  tests, 0 failures, 0 errors, Java 8 check passed), `node tools/i18n/check` (0 errors, 0 warnings),
  and `git diff --check`. No new user-facing strings were added. All required PR checks passed after
  a Java CodeQL autobuild dependency-resolution failure and successful rerun. Post-merge `main` CI,
  CodeQL, and Scorecard passed. Evidence PR #145 merged at `e812be6`; its required checks and
  post-merge CI, CodeQL, and Scorecard runs passed, and issue #46 was closed. No real server load,
  in-game command execution, browser rendering, or text-component compatibility check was performed.
- WP-1.7 (issue #47) merged in PR #147 at `db3e33a` and the issue is closed. SnakeYAML moved from
  `io.github.snake-yaml:2.9` (major 53, JavaSE 9 manifest) to `org.yaml:snakeyaml:2.7` (major 52,
  JavaSE 1.8 manifest). Shadow relocates Netty and SnakeYAML under
  `io.github.woxlo1.deploymc.lib.*`, excludes `module-info.class` and `META-INF/versions/**`, and
  records relocation/exclusion inputs. `buildSrc` hosts `PluginJarVerifierTask`, `PluginJarSizeTask`,
  and `PluginJarFixturesTask`; the verifier is wired into `check` and rejects out-of-namespace
  classes, malformed multi-release entries, missing versioned bases, duplicate entries/classes,
  out-of-namespace service files/providers, and common-path bytecode above major 52. Verified with
  `gradlew spotlessApply build verifyJava8` (143 Java tests, 0 failures, Java 8 check passed),
  `gradlew :plugin:reportPluginJarSize` (4,268,456 bytes / 4.07 MiB), 12 fixture JARs (3 pass, 9
  intentional failures generated by `:plugin:generatePluginJarFixtures`), `git diff --check`, and
  `node tools/build/verify-all.cjs` (`PASS` for all six toolchains). The final JAR has 230 relocated
  SnakeYAML classes, 0 unrelocated Netty/YAML classes, 0 multi-release entries, 0 service files, and
  0 duplicate names. All 18 PR checks passed (16 required); post-merge CI, CodeQL, and Scorecard
  runs also passed. No real server, browser, conflicting-library classloader, or text-component
  check was performed.
- WP-1.9 (issue #49) merged in PR #149 at `3244680` and the issue is closed. `core/auth` now loads
  host-controlled `plugins/DeployMC/tokens.d/*.json` records with SHA-256 hashes, rejects invalid or
  duplicate records at startup, verifies bearer tokens with `MessageDigest.isEqual()`, enforces
  expiry, and provides a one-time creation API. `NettyTransportServer` authenticates every HTTP and
  WebSocket request before handling it, leaves only `/healthz` and `/readyz` public, returns
  localized RFC 9457 `401` responses with `WWW-Authenticate: Bearer`, and never logs Authorization
  values. With no authenticator supplied the gate denies access. `auth.mode: token` is the default;
  `cloudflare` and `local-onetime` refuse startup. Bukkit startup creates the authentication runtime
  with localized failure messages, and `/deploymc token create [label]` is console-only and prints
  the secret once. English and Japanese catalogs, the README admin-equivalent warning, the token
  record specification, and implemented-status markers in the API specifications were updated.
  Verified on 2026-11-19 through the ASCII junction with `gradlew :platform:bukkit:test` (51 tests),
  `gradlew spotlessApply`, `gradlew build verifyJava8` (159 Java tests, 0 failures, Java 8 check
  passed, JAR verifier passed at 4,297,968 bytes / 2,510 classes), spec lint/test (14 tests), i18n
  checks (0 errors, 0 warnings; 7 checker tests), `node tools/build/verify-all.cjs` (all six
  toolchains `PASS`), and `git diff --check`. The new route-enumeration test covers every OpenAPI
  method and path and the AsyncAPI `/api/v1/ws` bearer declaration. All 18 pull-request checks
  passed (16 required); the first Windows Java failure was a thread-termination timing assertion,
  fixed by `18eb9f7` before the passing rerun. Post-merge CI, CodeQL, and Scorecard runs passed, and
  issue #49 closed at 2026-11-20T07:17:29Z. No real server, in-game command, browser,
  conflicting-library classloader, or text-component check was performed.
- WP-1.8 (issue #48) merged in PR #151 at `839b789ae8483f83dcb4b430641b7f0eafccb303`; the issue
  closed as completed at 2026-11-20T08:42:28Z. It adds the official-API Paper harness, host-provisioned
  token smoke test against `/api/v1/server`, a conflicting Netty fixture plugin, and
  before/after-disable thread dumps. All PR checks passed after the Windows Java-path fix
  `9dd87ed`; post-merge CI [44704690836](https://github.com/woxlo1/DeployMC/actions/runs/44704690836),
  CodeQL [44704690767](https://github.com/woxlo1/DeployMC/actions/runs/44704690767), and Scorecard
  [44704690925](https://github.com/woxlo1/DeployMC/actions/runs/44704690925) passed. CI artifacts
  rendered passing Linux and Windows rows into the [support matrix](../compatibility/support-matrix.md).
  No support tier is claimed.
- WP-2.1 (issue #50) merged in PR #153 at `748bbc8adda99e1aae9eeaca600863b44ff2f2ab`; the issue
  closed as completed at 2026-11-20T10:06:27Z. It adds a pure `SandboxResolver`, resolver-only
  `SandboxPath`, host-controlled `SandboxPolicy`, Windows name rules on every host, default
  reparse-point rejection, root revalidation on each resolve, post-open file-key checks,
  protected/deny globs, and localized English/Japanese rejection codes. The deterministic corpus
  contains 2,736 reject payloads and 29 allowed paths, with seeded fuzz coverage and a scheduled
  Ubuntu/Windows fuzz workflow. On 2026-11-20,
  `python tests/security/sandbox/generate_corpus.py` reported `reject=2736 allow=29`;
  `.\gradlew.bat spotlessApply :core:test --tests '*Sandbox*' --console=plain
  --no-configuration-cache` completed 16 tests (15 passed, 1 skipped); and
  `$env:DEPLOYMC_JAVA_ROOT = "C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii";
  node tools/build/verify-all.cjs` reported `PASS` for all seven toolchains. The skipped test needs
  symlink privileges unavailable on this host. The explicit PR comment is at
  <https://github.com/woxlo1/DeployMC/pull/153#issuecomment-7036766443>. All PR checks passed at
  `9d52a85`; post-merge `main`
  CI [44716375506](https://github.com/woxlo1/DeployMC/actions/runs/44716375506),
  CodeQL [44716375542](https://github.com/woxlo1/DeployMC/actions/runs/44716375542), and Scorecard
  [44716375486](https://github.com/woxlo1/DeployMC/actions/runs/44716375486) passed. The feature
  branch was deleted locally and remotely. No running-server validation or file-API integration is
  claimed.

## In flight

- WP-1.9 merged in PR #149 at `3244680`, issue #49 is closed, and no WP-1.9 work remains in flight.
- WP-1.8 merged in PR #151 at `839b789`, issue #48 is closed, and no WP-1.8 work remains in flight.
- WP-1.7 merged in PR #147 at `db3e33a`, issue #47 is closed, and no WP-1.7 work remains in flight.
- WP-2.1 merged in PR #153 at `748bbc8`, issue #50 is closed, post-merge evidence is recorded, and
  no feature branch remains. This evidence PR records the merge and PR-check results.
- 17 open Dependabot PRs (#23-#39), tracked for triage by #121. Do not merge them in bulk.
- Open issues: 80. Milestones `0.1.0` through `0.10.0` exist.

## Blocked

- Nothing blocks the next category 2 work.
- Owner decisions D-001 through D-007 in [decisions pending](decisions-pending.md) affect later
  compatibility, Cloudflare, distribution, and community work, not Phase 0.

## Exact next steps

1. Merge this evidence PR and confirm its post-merge `main` checks.
2. Identify the next open category 2 item after #50, then continue with category 3 (#115-#120),
   category 4 (#121-#123), and category 5 (#124-#130).
3. Update project status, implementation status, and this handoff at the end of each session.

## Gotchas

- On this Windows host, the repository path contains non-ASCII characters. Use the ASCII junction
  for Gradle and set `CARGO_TARGET_DIR` to an ASCII path for Cargo; see [environment](environment.md).
  The one-command `node tools/build/verify-all.cjs` handles both.
- `gh milestone list` is not supported; query `gh api repos/woxlo1/DeployMC/milestones`.
- Conventional Commit body and footer lines must be at most 120 characters; PR #139 needed an amend
  because `Refs #45` was parsed as a footer on an over-long line.
- `gh` commands with `--jq` need careful quoting in PowerShell; prefer `--json` plus
  `ConvertFrom-Json` when a filter fails.
- Paper 26.3 requires Java 25 at runtime. Build with JDK 21 and run only the Paper process with
  Java 25. The server's console has no `disable <plugin>` command; the compatibility fixture
  registers `fixture-disable-deploymc`, which calls the Bukkit API for the harness.
- In Git Bash, prepending a Windows-style `C:\...` path to `PATH` does not make `java` resolve to
  that executable; the first Windows compatibility job silently used Java 21. Pass the native path
  with `--java` and normalize it with `cygpath -w` when `RUNNER_OS=Windows`.
- PowerShell splits a bare `-Pdeploymc.verifyJarFile=...` Gradle argument. Use
  `--project-prop=deploymc.verifyJarFile=...` before the task name when running
  `:plugin:verifyPluginJarFixture`.
- Discussions stay enabled with default categories and zero threads. Do not create custom
  categories or a pinned post.
- The license decision is closed: Apache-2.0, ADR-0013. Do not reopen it without owner approval.
- Never claim originality from the naming check; exact-name GitHub repositories exist.
