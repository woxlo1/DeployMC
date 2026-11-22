# Handoff

Last updated: 2026-11-22. Read [AGENTS.md](../../AGENTS.md) first, then this file and
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
- WP-2.2 (issue #51) merged in PR #155 at `017c2abd1549e6c3aa8f947f6324df9da97ccb59`; the issue
  closed as completed at 2026-11-20T10:59:33Z. It adds `core.files`: sandboxed
  list/stat/read/range/download/create/mkdir/write/upload/move/delete with `sha256:` full-file
  revisions, same-directory temporary writes and a single atomic commit, revision and file-identity
  conflict detection, host-controlled limits, explicit recursive-delete confirmation, and
  fail-closed overwrite/move/delete behind `SnapshotHook`. English and Japanese diagnostic catalogs,
  stable error codes, and a normative file-operations specification were added. On 2026-11-20,
  `.\gradlew.bat spotlessApply :core:test --tests '*File*' --console=plain
  --no-configuration-cache` completed 26 tests (24 new WP-2.2 tests plus 2 existing matching tests)
  with no failures; `.\gradlew.bat build verifyJava8` ran 205 Java tests with 0 failures, 0 errors,
  and 1 skipped symlink test; `node tools/i18n/check`, `git diff --check`, and
  `node tools/build/verify-all.cjs` passed with all seven toolchains reporting `PASS`. PR comments
  recorded implementation, CI investigation, and post-merge evidence at
  <https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7037691314>,
  <https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7037772338>, and
  <https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7054409687>. All required checks passed
  at PR head `595fde0`; post-merge `main` CI
  [44723836623](https://github.com/woxlo1/DeployMC/actions/runs/44723836623), CodeQL
  [44723836580](https://github.com/woxlo1/DeployMC/actions/runs/44723836580), and Scorecard
  [44723836649](https://github.com/woxlo1/DeployMC/actions/runs/44723836649) passed. The local and
  remote `feature/wp-2.2-file-operations` branches were deleted. No REST endpoint, transport
  handler, running server, or UI consumes the service; WP-2.3 later added the snapshot store.
- WP-2.3 first stage merged in PR #158 at `879bdc5acd2abea4af9a14dd3ea9bd2b79d374ef` on
  2026-11-21; issue #52 was later closed by the implementation stage. It adds the normative
  [`specification/snapshot/snapshot-store.md`](../../specification/snapshot/snapshot-store.md):
  `.deploymc/history/` layout, format 1, immutable uncompressed SHA-256 blobs, atomic JSON
  metadata/index writes, `snap_[0-9a-z]{26}` ids, restore-first reversibility, 50/90-day/2 GiB
  retention with the newest three protected, a 24 h GC grace period, verify-before-GC, conservative
  repair, and a single-instance `data.lock`. Superseded PR #157 was closed with an explanation; no
  force push was used. On 2026-11-21, `git diff --check` was clean, `npm run lint` passed in
  `tools/spec`, `npm test` in `tools/spec` ran 14 tests with 0 failures, and post-merge CI
  [44849766505](https://github.com/woxlo1/DeployMC/actions/runs/44849766505), CodeQL
  [44849766469](https://github.com/woxlo1/DeployMC/actions/runs/44849766469), and Scorecard
  [44849766325](https://github.com/woxlo1/DeployMC/actions/runs/44849766325) passed. PR comments
  are at <https://github.com/woxlo1/DeployMC/pull/158#issuecomment-7054994035> and
  <https://github.com/woxlo1/DeployMC/pull/158#issuecomment-7055098742>. The temporary spec branch
  was deleted locally and remotely. That stage added no store code.
- WP-2.3 implementation merged in PR #159 at `0425c814018091543929444042dcd6d885bd75d5` on
  2026-11-21; issue #52 closed as completed at 2026-11-21T02:11:54Z. It adds Java 8 `core.snapshot`:
  format-1 JSON records, immutable SHA-256 blobs under `objects/ab/`, snapshot and per-path indexes,
  root-validated `SnapshotHook.capture`, restore-first reversibility for files and deletion markers,
  retention, verify/repair, conservative GC, and a single-instance `data.lock`. `SnapshotRequest`
  carries actor, deployment id, and deterministic plugin metadata; `FileOperationsService` passes the
  canonical sandbox root to the store. On 2026-11-21, `.\gradlew.bat spotlessApply --console=plain
  --no-configuration-cache` and `node tools/build/verify-all.cjs` passed with all seven toolchains
  reporting `PASS`. The Java build ran 230 tests (0 failures, 0 errors, 1 skipped symlink test),
  including 25 snapshot-store tests. All PR checks passed at head `f0fd908`; post-merge `main`
  CI [44854531337](https://github.com/woxlo1/DeployMC/actions/runs/44854531337), CodeQL
  [44854531422](https://github.com/woxlo1/DeployMC/actions/runs/44854531422), and Scorecard
  [44854531350](https://github.com/woxlo1/DeployMC/actions/runs/44854531350) passed. PR comments are
  at <https://github.com/woxlo1/DeployMC/pull/159#issuecomment-7055673480> and
  <https://github.com/woxlo1/DeployMC/pull/159#issuecomment-7055770391>. The local and remote
  `feature/wp-2.3-snapshot-store` branches were deleted. At that stage no REST endpoint or transport
  handler consumed the store; WP-2.6 later added the snapshot REST adapters, still without a running
  server, UI, or in-game command.
- WP-2.4 first stage merged in PR #162 at `2a4e7f8d0d038a7e4175dc6912df460a1a1a5b22`; it adds the
  normative diff contract, ADR-0015, and `diff.*` error codes. The implementation stage adds Java 8
  `core.diff`: bounded Myers line/word diffs, hardened YAML/JSON semantic diffs with text fallback,
  metadata-only ZIP/JAR descriptor diffs, stable note codes, and inclusive 2 MiB boundary handling.
  On 2026-11-21, the 31 focused `DiffEngineTest` cases passed, `spotlessApply` passed, and
  `node tools/build/verify-all.cjs` reported `PASS` for all seven toolchains with 261 Java tests
  (0 failures, 0 errors, 1 skipped symlink test). The implementation merged in PR #163 at
  `80a3f0b` with issue #53 closed; all PR checks and post-merge CI, CodeQL, and Scorecard passed.
  At that stage no REST endpoint consumed the engine; the WP-2.6 snapshot diff route now does.
- WP-2.5 first stage merged in PR #165 at `6cd6015a141d8a108b7507a9f801b646679698cc`; it defines the
  content validation contract and ADR-0016. The Java 8 implementation merged in PR #166 at
  `9363e0c70c4666558959560fe8ca5fe58f80e920` with issue #54 closed. It adds `core.validation` with
  bounded YAML, JSON, TOML, and properties validators, strict UTF-8 decoding, stable codes and
  pointers, a deterministic registry, a fail-closed `core.files` gate, and a byte-preserving YAML
  scalar editor. On 2026-11-21, 27 focused validation tests passed, JUnit XML totals were 288 tests
  (0 failures, 0 errors, 1 skipped symlink test), and `node tools/build/verify-all.cjs` reported
  `PASS` for all seven toolchains. All required PR checks and post-merge CI, CodeQL, and Scorecard
  passed. D-008 was resolved by the owner on 2026-11-21 in favour of one
  `validation.limit-exceeded` shape for every structural budget. No REST endpoint or running server
  consumes the validators yet.
- WP-2.6 first stage merged the file/validation REST specification in PR #168 at `8970728`, with
  verification and post-merge evidence comments and successful CI, CodeQL, and Scorecard runs; the
  `spec/wp-2.6-file-endpoints` branch was deleted. The adapter stage adds `transport.FileApiHandler`,
  `transport.RequestJson`, RFC 9457 `errors[]` serialization, and Bukkit `FileApiRuntime` wiring
  behind the existing token gate. On 2026-11-21, focused transport (14 tests) and Bukkit (8 tests)
  runs passed, and `build verifyJava8` ran 311 tests with 0 failures, 0 errors, and 1 skipped
  symlink test; i18n and `git diff --check` also passed. A regression test caught and fixed the
  runtime rejecting schema-valid 2048 MiB snapshot and 4096 MiB upload budgets. No running-server
  smoke, E2E, in-game command, or UI check was performed. The adapter merged in PR #169 at `08fe424`
  with verification and post-merge evidence comments, passing CI, CodeQL, and Scorecard, and its
  branch was deleted.
- The WP-2.6 snapshot follow-up defines six authenticated routes in OpenAPI: list, metadata,
  content, bounded diff, reversible restore, and retention prune. It pins opaque ids, quoted ETags,
  inclusive-start/exclusive-end ranges, host-derived restore actors, path-scope notes, and stable
  snapshot/diff error mapping. `npm run lint` and `npm test` in `tools/spec` passed locally on
  2026-11-21. The contract merged in PR #170 at `bf82e26` with verification and post-merge evidence
  comments, passing CI, CodeQL, and Scorecard, and its branch was deleted. The evidence PR #171
  merged at `88a6541`; its branch was also deleted.
- The snapshot adapter stage adds `transport.SnapshotApiHandler` over `SandboxResolver`,
  `SnapshotStore`, and `core.diff`, wired through `FileApiRuntime`, `DeployMCPlugin`, and
  `DeployMCHttpHandler`. It adds a verified `SnapshotStore.readContent` method and a public
  `DiffJson.toSnapshotDiffJson` projection that matches the OpenAPI schema. `HttpRequest` now carries
  the authenticated `Identity` from the Netty channel, never from a client header. Review fixes pin
  absence under the store lock during restore, protect records younger than the minimum age from
  every prune path, clamp oversized `limit` values, honour `Accept-Language` q-values, cap diff input
  before blobs are read, and mark internal failures retryable. On 2026-11-22, focused transport
  (12 tests), snapshot-store (28 tests), gate identity (1 new case), and Bukkit routing/runtime
  assertions passed; the full build ran 328 tests with 0 failures, 0 errors, and 1 skipped symlink
  test. All seven toolchains reported `PASS` through `verify-all.cjs`, i18n reported 0 errors and 0
  warnings, and `git diff --check` was clean. No running-server smoke, full integration suite, E2E
  run, in-game command, or UI check was performed. It merged in PR #172 at `136ce00` with issue #55
  closed as completed. The verification comment is at
  <https://github.com/woxlo1/DeployMC/pull/172#issuecomment-7072665699> and the post-merge evidence
  comment is at <https://github.com/woxlo1/DeployMC/pull/172#issuecomment-7072756119>; post-merge CI,
  CodeQL, and Scorecard passed, and its local and remote feature branches were deleted.

## Recently added fixtures

Stage 1 of issue #115 adds the PART 35 coverage map at `tests/fixtures/README.md` and a source-built
test-only `DeployMCTestA` plugin with stable identity and 1.0.0/1.1.0 JARs. A targeted Gradle clean
build produced both JARs; descriptor/config ZIP entries and Java 8 class major 52 were inspected. No
harness, running server, full integration suite, E2E run, Web UI, or in-game command consumes the
fixtures yet. PR #174 squash-merged this stage at `bdcd64a6a329ce9c3c37a541ee850137d44b30d9` on
`2026-11-22T02:56:30Z`. The verification comment is at
<https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073079151> and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073263008>.

Four follow-up PRs were squash-merged with explicit verification and evidence comments:

- PR #175 recorded the stage-1 evidence at `a5ca87345b213838f0aa7f39135390f1d4ac4d81` on
  `2026-11-22T03:12:22Z`; [verification](https://github.com/woxlo1/DeployMC/pull/175#issuecomment-7073361203)
  and [evidence](https://github.com/woxlo1/DeployMC/pull/175#issuecomment-7073504322).
- PR #176 added the source-built `DeployMCTestBrokenEnable` fixture at
  `7c2100e5663c68becc31110d1f4c645939241e60` on `2026-11-22T03:33:06Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/176#issuecomment-7073565824) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/176#issuecomment-7073646436).
- PR #177 added the source-built `DeployMCTestBrokenDescriptor` fixture at
  `250804358a3c99a8a623bc339423201ccfa7d09c` on `2026-11-22T03:44:09Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/177#issuecomment-7073702671) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/177#issuecomment-7073790173).
- PR #178 added the source-built `DeployMCTestSlowEnable` fixture at
  `a034352bed3b8a47d4daf5f15aaf896223ad10d0` on `2026-11-22T03:56:16Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/178#issuecomment-7073841525) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/178#issuecomment-7073916898).

Four more follow-up PRs were squash-merged with explicit verification and evidence comments:

- PR #179 recorded merged-stage evidence at `a8868d4bb5e0d9496e9bc2569cc2964da8d04e57` on
  `2026-11-22T04:07:25Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/179#issuecomment-7073956949) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/179#issuecomment-7074018596).
- PR #180 added the source-built `DeployMCTestDependency` fixture at
  `4bcf20f47e0fc886f9a1177ee0d11346d0ca6774` on `2026-11-22T04:16:51Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/180#issuecomment-7074056587) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/180#issuecomment-7074122174).
- PR #181 aligned the conflicting-library fixture identity at
  `fd92cd015cd5dcf80e0d1feaa9f5b3bf939a40a6` on `2026-11-22T04:38:11Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074199434) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074321603). The initial
  commit-message length failure and a temporary Maven Central 429 during CodeQL were resolved and
  recorded in [CI fix](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074235950) and
  [retry](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074255497) comments.
- PR #182 added the source-built `DeployMCTestLogNoise` fixture at
  `9b20b366ebf29f361d106d1fa056dd901f344b11` on `2026-11-22T04:50:09Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/182#issuecomment-7074362365) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/182#issuecomment-7074443419).

All eight follow-ups had passing pre-merge checks and passing post-merge CI, CodeQL, and Scorecard
runs; the local and remote branches were deleted after evidence was posted. Issue #115 remains open
until the remaining PART 35 plugins and corpora land.

## In flight

- No WP-2.6 implementation PRs remain in flight. Snapshot routes stay behind the bearer-token gate
  with no bypass; the single provisioned token remains admin-equivalent until Phase 3 RBAC.
- No WP-2.1-WP-2.5 branches remain, and WP-2.6 and all merged PART 35 feature/evidence branches were
  deleted after evidence was posted. Evidence is recorded in the status pages and pull-request
  comments.
- 17 open Dependabot PRs (#23-#39), tracked for triage by #121. Do not merge them in bulk.
- Open issues: 75; issue #55 is closed. Milestones `0.1.0` through `0.10.0` exist.

## Blocked

- Nothing blocks the next category 2 work.
- Owner decisions D-001 through D-007 in [decisions pending](decisions-pending.md) affect later
  compatibility, Cloudflare, distribution, and community work.

## Exact next steps

1. Continue with category 3 (#115-#120), category 4 (#121-#123), and category 5 (#124-#130) in issue
   order.
2. Update project status, implementation status, this handoff, and AGENTS.md at the end of each
   session when repository state changes.

## Gotchas

- On this Windows host, the repository path contains non-ASCII characters. Use the ASCII junction
  for Gradle and set `CARGO_TARGET_DIR` to an ASCII path for Cargo; see [environment](environment.md).
  The one-command `node tools/build/verify-all.cjs` handles both. If Spotless reports that a target
  is outside the project dir, stop Gradle and delete the generated `core/build/spotless/spotlessJava`
  and `spotlessMisc` caches created under the previous junction.
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
- Discussions had zero threads on 2026-11-21. The owner asked for genuine design discussion, not
  activity for its own sake; do not create custom categories, a pinned post, or fake threads.
- The license decision is closed: Apache-2.0, ADR-0013. Do not reopen it without owner approval.
- Never claim originality from the naming check; exact-name GitHub repositories exist.
