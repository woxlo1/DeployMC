# Implementation Status

Last updated: 2026-11-23 (Phase 0 WP-0.6/WP-0.2, Phase 1 WP-1.1-WP-1.9, Phase 2 WP-2.1-WP-2.5,
all merged WP-2.6 specification and adapter stages; issues #47-#55 closed. PR #168 merged the
file/validation contract at `8970728`, PR #169 merged its adapters at `08fe424`, PR #170 merged the
snapshot-route contract at `bf82e26`, PR #171 merged its evidence at `88a6541`, and PR #172 merged
the authenticated snapshot adapters at `136ce00`. PART 35 stage 1 merged in PR #174 at `bdcd64a`,
the broken-enable fixture in PR #176 at `7c2100e`, the broken-descriptor fixture in PR #177 at
`2508043`, the slow-enable fixture in PR #178 at `a03435`, the dependency fixture in PR #180 at
`4bcf20f`, the conflicting-library alignment in PR #181 at `fd92cd0`, and the log-noise fixture in
PR #182 at `9b20b36`. PR #183 recorded catch-up evidence at `9fac5f1`, PR #184 added the
source-built wrong-platform fixture at `4819195`, PR #185 added the versioned traversal-corpus
manifest at `3b85de8`, PR #186 recorded catch-up evidence at `cfd47b6`, PR #187 added JWT vectors
with a localhost mock JWKS server at `ce4fdc7`, PR #189 added hostile archive and YAML generators
at `49f300a`, PR #190 recorded catch-up evidence at `49a9375`, PR #191 added log corpora at
`2a253a1`, PR #192 recorded catch-up evidence at `211e57f`, PR #193 added config corpora at
`2b6d0f5`, PR #194 recorded catch-up evidence at `706adff`, PR #195 added generated large trees at
`3087abc`, PR #196 recorded catch-up evidence at `289b9a4`, and PR #197 added cross-implementation
filter vectors at `9b7b5ee`, all with passing post-merge `main` workflows. All PART 35 fixture/data
stages are merged with evidence; issue #115 is complete. PART 33 algorithm regression suites
merged in PR #199 at `0b83150`, PR #200 at `98b6b79`, and PR #201 at `5d279c6`, adding 60
dedicated tests; issue #116 records the final evidence. PART 36 observability baseline stages merged
in PR #203 at `072feda`, PR #204 at `2f3abfc`, PR #205 at `7555c3d`, PR #206 at `d42f5d2`, and PR
#207 at `b882f95`, all with passing post-merge `main` workflows. Consolidated evidence is recorded
here; issue #117 closed at `83b9107`. PART 40.3 early risk-reduction spikes merged in PR #210 at
`a998f7e` with a reproducible Windows replacement probe; issue #118 closed at
`2026-11-23T11:04:06Z`. PART 41 quality reporting merged in PR #211 at `e0f6ddb`; issue #119 closed
at `2026-11-23T11:25:53Z`. PART 39 specification change control merged in PR #212 at `d068990`;
issue #120 closed at `2026-11-23T11:43:16Z`. PART 40.4 adds a dependency selection and advisory
response policy; its final evidence is recorded here and issue #123 closes after this PR merges.

This page states what exists in the repository and what does not. It is deliberately blunt:
no feature is listed as working unless it was built and exercised. The [roadmap](../../ROADMAP.md)
describes intended phase order; the [master specification](DeployMC_Complete_Master_Development_Specification.md)
is the normative design.

## PART 40.3 early risk-reduction spikes (issue #118)

The seven spikes required by PART 40.3 are recorded in
[`docs/research/spikes/`](../../docs/research/spikes/README.md). Each record separates observed local
output, source-verified external facts, and work that was not run. Existing ADRs link to the relevant
records without changing their decisions. No spike promotes a platform to a support tier.

Observed local evidence on 2026-11-23:

| Command | Result |
|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -File tests/spikes/windows-jar-replacement.ps1` | `replace-open-without-share-delete=fail: MethodInvocationException inner=IOException hresult=0x80070020`; `replace-open-with-share-delete=pass`; target and backup content confirmed; `dotnet=4.0.30319.42000` |
| `python -m unittest discover -s tests/fixtures/log-corpora -p "test_*.py"` | `Ran 6 tests`, `OK` |
| `.\gradlew.bat :transport:test --console=plain --no-configuration-cache` | `BUILD SUCCESSFUL in 4s`; the `FROM-CACHE` marker is recorded as a cached pass, not a newly executed test run |
| `node tools/build/verify-all.cjs --only=web` | `PASS web`; main JS 155,810 bytes, gzip 50,632 bytes, Brotli 44,342 bytes, source map 380,370 bytes |
| `node tools/build/verify-all.cjs --only=java` | `PASS java`; final JAR 4,721,584 bytes, 2,706 classes, max common bytecode 52 |
| `node tools/build/verify-all.cjs` | `PASS web`, `PASS java`, `PASS cli`, `PASS python`, `PASS spec`, `PASS i18n`, `PASS compat` |
| `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | `BUILD SUCCESSFUL` |
| `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD` | exit 0 |
| `git diff --check` | exit 0 |

Untested claims remain explicit: no real server opened a JAR, no update-folder swap or proxy install
was run, no Folia scheduler call was compiled or executed, no browser loaded the Web assets, no
Monaco/CodeMirror benchmark ran, and D-001 blocks the supervisor restart test. Official Paper,
Microsoft, and Velocity sources were read with a 2026-11-23 access date in the spike records.

## PART 41 quality reporting (issue #119)

Issue #119 adds [`tools/quality/check.cjs`](../../tools/quality/check.cjs) and
[`docs/development/quality.md`](quality.md). The tool reads existing `web/dist/assets/*.js.gz` and
`plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar` artifacts only. It does not build, test, call the
network, or read CI results. All twelve PART 41 areas appear in the deterministic Markdown report;
unmeasured areas are always `NOT MEASURED`. Missing, ambiguous, unreadable, invalid, or over-budget
artifacts fail closed, and `--report <path>` only writes the generated report.

| Command | Result |
|---|---|
| `node --test tools/quality/test/report.test.cjs` | `tests 5`, `pass 5`, `fail 0` |
| `node tools/quality/check.cjs` | exit 0; Web main JS gzip `50632 bytes <= 60000 bytes` PASS; plugin JAR `4721584 bytes <= 5500000 bytes` PASS; `Unmeasured PART 41 areas: 11` |
| `node tools/build/verify-all.cjs` | `PASS web`, `PASS java`, `PASS cli`, `PASS python`, `PASS spec`, `PASS i18n`, `PASS compat` |
| `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | `BUILD SUCCESSFUL` |
| `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD` | exit 0 |
| `git diff --check` | exit 0 |

Only the two budgets derived from the #118 local measurements are enforced. No coverage report, E2E
result, static-analysis baseline, startup or idle profile, API breaking-change check, docs link/key
checker, i18n gate result, flakiness history, CI regression gate, product code, user-facing string,
or required-check change was added by this issue.

## PART 39 specification change control (issue #120)

Issue #120 adds [`docs/development/spec-errata.md`](spec-errata.md). It seeds `E-001` for the
WP-1.9 authentication correction, defines the reviewed `spec/<description>` branch workflow, and
requires recorded owner approval for changes that weaken security, remove deliverables, or alter
legal or licensing terms. `project-status.md` now makes the eight-check milestone consistency audit
mandatory and requires `NOT RUN`, `NOT IMPLEMENTED`, issue links, or a `spec/` PR for every gap.
No requirement may be silently ignored or waived by assumption.

| Command | Result |
|---|---|
| `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD` | exit 0 |
| `node tools/build/verify-all.cjs` | `PASS web`, `PASS java`, `PASS cli`, `PASS python`, `PASS spec`, `PASS i18n`, `PASS compat` |
| `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | `BUILD SUCCESSFUL` |
| `git diff --check` | exit 0 |

This is a documentation-only change. No product code, CI behavior, user-facing string, or required
check changed, and no specification requirement was weakened or removed.

## PART 40.4 dependency policy (issue #123)

Issue #123 adds [`docs/development/dependency-policy.md`](dependency-policy.md) and links it from
[`docs/development/ci.md`](ci.md). The policy defines dependency selection criteria, pinning sources
of truth, review gates, routine updates with guarded auto-merge, time-limited exemptions, advisory
verification against official sources, severity-based response expectations, containment through
disclosure, and security fixes on release branches. It stays consistent with `SECURITY.md`,
`GOVERNANCE.md`, the fail-closed CI dependency review, and the authentication model. Official GitHub
sources were checked with a 2026-11-23 access date and are listed in the document.

The policy explicitly records current gaps instead of claiming controls that do not exist: no Gradle
dependency verification metadata, no Python development lockfile, no SBOM/signing/provenance
pipeline, no exercised release branch, and no custom auto-merge enforcement workflow. No Dependabot
pull request, advisory, or repository setting was changed by this issue; #121 and #122 remain the
issues for individual Dependabot triage and advisory remediation.

| Command | Result |
|---|---|
| `git fetch origin main` | exit 0; `origin/main` at `d0689901fdde1b7045dceba8348683398c5ece30` |
| `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD` | exit 0 |
| `node tools/build/verify-all.cjs` | `PASS web`, `PASS java`, `PASS cli`, `PASS python`, `PASS spec`, `PASS i18n`, `PASS compat` |
| `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | `BUILD SUCCESSFUL`, `75 actionable tasks: 75 up-to-date` |
| `git diff --check` | exit 0 |

## Summary

DeployMC is **pre-alpha**. The repository contains documentation, specifications, architecture
records, buildable skeletons for each language, a source-complete Bukkit-family adapter, an
embedded Netty HTTP/WebSocket transport with server-free integration tests, a classpath-only
static asset handler for the built Web UI, a Java configuration loader with schema validation,
migrations, safe reload, and fail-closed property tests, a server-side plugin message i18n
runtime, an automated final-JAR packaging verifier, and host-provisioned bearer tokens enforced by
a fail-closed HTTP/WebSocket gate.
A WP-1.8 smoke run has now observed the plugin enabling on a real Paper server, the localized
startup banner, authenticated `/api/v1/server`, a conflicting-library fixture loading alongside
DeployMC, and a clean explicit disable with Netty threads released. This is smoke evidence, not a
support tier or a full feature validation. The transport rejects every route except `/healthz`,
`/readyz`, and authenticated handlers without a valid token. Implemented handlers are
`GET /api/v1/server`, the WP-2.6 file/validation routes, the six WP-2.6 snapshot routes, the
authenticated doctor route, and the opt-in authenticated metrics route; deployment, console, and
audit resources still have no handlers. `/api/v1/server/doctor` serves a redacted report from a
server-free `DoctorService`. Structured request logging is selected by configuration, and metrics
are dispatched only when `observability.metrics.enabled` is true. No running server, browser, E2E
run, or in-game command has exercised these PART 36 routes; the Web doctor panel is build-verified
only. Other PART 36 areas remain specification or fixture contracts. The first PART 35 fixture stage adds
a source-built `DeployMCTestA` plugin with 1.0.0 and 1.1.0 JARs, but no harness or server run consumes
it yet. PR #174 squash-merged that stage at
`bdcd64a6a329ce9c3c37a541ee850137d44b30d9` on `2026-11-22T02:56:30Z`. The verification comment is at
<https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073079151>, and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073263008>. Post-merge `main`
runs passed: CI [44978349816](https://github.com/woxlo1/DeployMC/actions/runs/44978349816), CodeQL
[44978349824](https://github.com/woxlo1/DeployMC/actions/runs/44978349824), and Scorecard
[44978349870](https://github.com/woxlo1/DeployMC/actions/runs/44978349870). No running server,
harness, full integration suite, E2E run, Web UI, or in-game command consumed the fixtures.
The WP-2.1 sandbox resolver, protected-path rules, and security
corpus are implemented, and WP-2.2 `core.files` resolves every request path through that resolver.
The WP-2.6 file/validation adapter consumes the resolver and file service behind the token gate, but
has only server-free tests; no running server or UI consumes it. The merged WP-2.3 snapshot store
contract has a Java 8 `core.snapshot` implementation with content-addressed blobs, atomic metadata,
restore-first reversibility, retention, verify/repair, conservative GC, and a single-instance lock.
The WP-2.6 snapshot adapter consumes that store behind the same token gate, with server-free tests
only. The WP-2.4 contract has a Java 8 `core.diff` implementation for bounded line, word, YAML/JSON
semantic, and JAR metadata results; the snapshot diff route consumes it. No running server, UI, or
in-game command consumes these routes, and there is no application-level one-click restore. The WP-2.5
contract now has a Java 8 `core.validation` implementation with bounded YAML, JSON, TOML, and
properties validators, a deterministic registry, and a fail-closed save-time gate wired into
`core.files`. The WP-2.6 file/validation adapter calls the registry for the validate endpoint and the
save gate on writes, but no running server or UI consumes it. There is no deployment engine, authorization, audit
log, or Web UI screen, and the packaged Web UI has not been rendered in a browser.
The WP-1.5 command surface, WP-1.6
message runtime, and WP-1.9 token command still have only server-free tests beyond the smoke path.

## Verified working today

These were actually run, not just written. Commands are the ones documented in
[AGENTS.md](../../AGENTS.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md).

| Area | What is verified | How |
|---|---|---|
| Java build | Multi-project Gradle build, JDK 21 toolchain, `--release 8`, class-file version check | `.\gradlew.bat build verifyJava8` |
| Java unit tests | 473 tests across all modules, including fake SPI, Bukkit, standalone, transport, static asset, configuration, command, i18n runtime, token store, authenticator, provisioner, auth gate, sandbox resolver/corpus/fuzz/manifest, file operation, snapshot store, diff engine, validation, file/validation REST adapter, snapshot REST adapter, doctor, observability, and self-protection cases | Gradle `test` tasks; JUnit XML: 473 tests, 0 failures, 0 errors, 1 skipped |
| Core value objects | `SemanticVersion`, `PlatformCapabilities`, `CapabilityKeys`, `CapabilityDefinition`, `CapabilityCatalog`, SPI value types | Compile and unit tests |
| SPI interfaces | `PlatformAdapter`, `PlatformScheduler`, `ConsoleBridge`, `ServerController`, `PluginInspector`, `MessageSink`, and value types | Compile and fake-platform tests |
| Bukkit adapter | Generated `plugin.yml` with `/deploymc` and operator-only permissions, `JavaPlugin` entry point, config-driven startup, SPI bridge, version detection, startup banner, capability map, task cleanup, localized command handler, tab completion, per-sender i18n, console-only token creation, and transport start/stop wiring | `.\gradlew.bat :platform:bukkit:test`; 73 Bukkit tests, 0 failures, plus the WP-1.8 real-server smoke below. In-game commands are still not exercised |
| Distribution JAR | Shadow JAR contains the generated descriptor, entry point, message catalogs, 230 relocated SnakeYAML classes, relocated Netty, configuration schema and default template, and `deploymc-web/` assets with precompressed variants; no Spigot, unrelocated Netty/YAML, or multi-release entries | ZIP entry inspection of `plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar` |
| Plugin JAR verification | `:plugin:verifyPluginJar` fails on out-of-namespace classes, malformed or manifest-less multi-release entries, missing versioned bases, duplicate entries/classes, service entries outside the namespace, or common-path bytecode above major 52; CI reports the size | Production JAR plus 12 positive/negative fixture JARs |
| Embedded transport | Netty 4.1.141.Final HTTP/WebSocket lifecycle, limits, Host/Origin hooks, request IDs, redacted logging, security headers, graceful shutdown, bounded WebSocket queues, static asset delivery, and a fail-closed token gate with health exceptions | `NettyTransportServerTest`: 11 tests; `StaticAssetHandlerTest`: 12 tests; `AuthenticationGateTest`: 5 tests; 0 failures |
| Web build | React 18 + TypeScript 5.7 + Vite 6 build, lint, format, Vitest, and gzip/Brotli precompression | `pnpm lint`, `pnpm format:check`, `pnpm test` (14 tests), `pnpm build` |
| CLI skeleton | Rust 2021 workspace with `clap`; format, clippy, tests, build | `cargo fmt --check`, `cargo clippy`, `cargo test`, `cargo build` |
| Python SDK skeleton | `pyproject.toml`, ruff, format, mypy strict, pytest | `ruff check`, `ruff format --check`, `mypy`, `pytest` |
| i18n checker | ICU subset parser, placeholder/plural validation, catalog drift status | `node --test tools/i18n/test/check.test.cjs`, `tools/i18n/check` |
| Specification lint | OpenAPI and AsyncAPI validation, config example against JSON Schema, deployment transition graph checks | `npm ci && npm run lint && npm test` in `tools/spec/` |
| One-command local verification | All seven toolchains above run from a single command; each reports `PASS`/`FAIL` | `node tools/build/verify-all.cjs` — verified on this Windows host: all seven reported `PASS` |
| CI | Seven workflows (CI, lint, CodeQL, dependency review, Scorecard, Labels, Fuzz), including the `Spec` job, plugin JAR size report, compatibility matrix jobs on Ubuntu and Windows, and scheduled sandbox fuzzing on both hosts | GitHub Actions runs on `main` |
| Research documents | Naming check, competitive analysis, license comparison, Bukkit descriptor baseline, measured transport spike, and SnakeYAML selection with source links and access dates | `docs/research/*.md` |
| Configuration loader | Safe YAML parsing, schema validation, timestamped migration backup, atomic writes, immutable `ConfigView`, safe reload, localized diagnostics, and 250 deterministic fail-closed mutations | 53 `core` tests, full build, Java 8 check, and JAR inspection |
| WP-2.1 sandbox resolver | Canonical resolver with an injectable file-system boundary, resolver-only `SandboxPath`, host-controlled policy, Windows name rules on every host, default reparse-point rejection, root revalidation, post-open file-key checks, protected/deny globs, and localized rejections | 16 sandbox tests (15 passed, 1 skipped locally without symlink privileges), full Gradle build, Java 8 check, all-toolchain verification, and the 2,736-payload corpus plus seeded fuzz test |
| WP-2.2 file operations | Sandboxed list/stat/read/range/download/create/mkdir/write/upload/move/delete, SHA-256 full-file revisions, same-directory temporary writes with one atomic commit, revision and file-identity conflict checks, host-controlled limits, explicit recursive-delete confirmation, and a fail-closed snapshot boundary | 24 new file-operation tests, full Gradle build (205 Java tests), Java 8 check, all-toolchain verification, spec lint, and i18n check |
| WP-2.3 snapshot specification | Normative `.deploymc/history/` layout, format 1, immutable blobs, atomic metadata/index writes, stable snapshot ids, restore-first reversibility, retention, GC preconditions, verify/repair, and single-instance lock | `npm run lint` and `npm test` in `tools/spec` (14 tests); post-merge CI, CodeQL, and Scorecard at `879bdc5` |
| WP-2.3 snapshot store | Java 8 `core.snapshot` implementation of format-1 JSON records, SHA-256 blob deduplication, sandbox-root validation, restore-first reversibility, retention, verify/repair, conservative GC, and `data.lock`; 46 localized diagnostic keys in English/Japanese | 25 focused snapshot tests in a 230-test Gradle build, Java 8 bytecode check, all-toolchain verification, spec lint, and i18n check |
| WP-2.4 diff specification | Normative 2 MiB textual cap, stable line/word/semantic/JAR result envelope, deterministic ordering, fallback rules, and `diff.*` error codes | `npm run lint` and `npm test` in `tools/spec` at the merged contract stage |
| WP-2.4 diff engine | Java 8 bounded Myers line diff with word segments, hardened YAML/JSON key-path diff with text fallback, metadata-only ZIP/JAR descriptor diff, sorted stable notes, and exact 2 MiB boundary handling | 31 focused `DiffEngineTest` cases, full Gradle build (261 Java tests), Java 8 bytecode check, all-toolchain verification (`PASS` for all seven toolchains), spec lint, and i18n check |
| WP-2.5 validation specification | Normative validator result model, stable validation codes, format profiles, registry precedence, save-time gate, endpoint result shape, and byte-preserving YAML scalar edit contract | `npm run lint` and `npm test` in `tools/spec`; ADR-0016 and `specification/validation/content-validation.md` |
| WP-2.5 validation engine | Java 8 `core.validation` with bounded YAML/JSON/TOML/properties validators, strict UTF-8 decoding, stable issue codes and pointers, deterministic registry precedence, fail-closed save-time gate in `core.files`, and byte-preserving YAML scalar edits | 27 focused validation tests, full Gradle build, Java 8 bytecode check, all-toolchain verification, spec lint, and i18n check |
| WP-2.6 file/validation adapters | Eight authenticated file/validation routes with Problem Details `errors[]`, strict bounded request JSON, localized findings, ETag/`If-Match`, byte ranges, cursor pagination, and a host-wired `FileApiRuntime` that opens/closes the snapshot store | 14 focused transport tests, 8 focused Bukkit tests, full Gradle `build verifyJava8`, i18n, and `git diff --check`; no running-server smoke or UI integration |
| WP-2.6 snapshot adapters | Six authenticated snapshot routes for history listing, metadata, exact content with ranges, bounded diff, reversible restore, and host-retention prune; opaque ids, quoted ETags, authenticated restore actors, stable localized codes, and no host paths in errors | 12 focused transport tests, 3 core snapshot cases, 1 gate identity test, 2 Bukkit routing/runtime assertions, full Gradle `build verifyJava8`, all-toolchain verification, i18n, and `git diff --check`; no running-server smoke or UI integration |
| WP-2.6 snapshot specification | Six authenticated snapshot routes for history listing, metadata, exact content, bounded diff, reversible restore, and host-retention prune, with opaque ids, ETags, ranges, path-scope notes, and stable error mapping | Merged in PR #170 at `bf82e26`; pre-merge `npm run lint` and `npm test` in `tools/spec` passed, as did post-merge CI, CodeQL, and Scorecard; adapters implemented afterward |
| WP-1.5 commands | `/deploymc status|reload|doctor|token`, permission nodes, config-driven startup, console-only token stub, limited doctor, and English/Japanese command catalogs | 42 server-free Bukkit tests, full build, Java 8 check, generated descriptor and JAR inspection |
| WP-1.6 i18n runtime | Per-key sender/server/English fallback, named placeholders, diagnostics, reflective sender locale detection, and legacy color formatting | 50 server-free Bukkit tests, full build, Java 8 check, and `node tools/i18n/check` |
| WP-1.9 authentication foundation | Host-provisioned SHA-256 token records, constant-time verification, expiry, fail-closed HTTP/WebSocket gate, health exceptions, localized 401 Problem Details, and one-time token creation | 11 core auth tests, 5 route-enumeration and gate tests, 63 Bukkit tests, full build, Java 8 check, and `node tools/build/verify-all.cjs` |
| WP-1.8 compatibility harness | Official pinned Paper download with checksum verification, Java 25 headless start, host-provisioned token, authenticated `/api/v1/server`, conflicting Netty fixture, before/after-disable thread dumps, logs, and clean teardown | 14 offline harness tests, `node tools/build/verify-all.cjs`, a real local Paper 26.3 build 256 smoke run, and passing CI smoke entries on Ubuntu and Windows at `839b789`; no support tier |
| PART 35 fixture stage 1 | Test-only `DeployMCTestA` plugin with stable identity, generated 1.0.0/1.1.0 descriptors and configs, enable/config/`/dmctest` signals, and Java 8 bytecode | Targeted clean build produced both JARs; ZIP descriptor/config and class major 52 inspected; CI-style `build verifyJava8` stored a configuration cache entry after the descriptor tasks were made cache-safe; PR #174 merged at `bdcd64a` with post-merge CI/CodeQL/Scorecard pass; no running server or harness use |
| PART 35 broken-enable fixture | Test-only `DeployMCTestBrokenEnable` plugin that saves its config and throws during `onEnable` with stable test signals | Targeted clean build produced the JAR; ZIP descriptor/config and class major 52 inspected; PR #176 merged at `7c2100e` with post-merge CI/CodeQL/Scorecard pass; no running server, harness, or rollback execution |
| PART 35 broken-descriptor fixture | Test-only `DeployMCTestBrokenDescriptor` JAR with a generated `plugin.yml` that omits the required `main` entry | Targeted clean build produced the JAR and compiled test class; descriptor omission and class major 52 inspected; PR #177 merged at `2508043` with post-merge CI/CodeQL/Scorecard pass; no validation run, server, or harness use |
| PART 35 slow-enable fixture | Test-only `DeployMCTestSlowEnable` plugin that sleeps during `onEnable`, with config/system-property delay injection and a 30s default | Targeted clean build produced the JAR; descriptor/config and class major 52 inspected; PR #178 merged at `a03435` with post-merge CI/CodeQL/Scorecard pass; no timeout test, server, or harness execution |
| PART 35 dependency fixture | Test-only `DeployMCTestDependency` plugin whose descriptor declares the absent hard dependency `DeployMCTestMissingDependency` | Targeted clean build produced the JAR; descriptor dependency entry and class major 52 inspected; PR #180 merged at `4bcf20f` with post-merge CI/CodeQL/Scorecard pass; no dependency warning test, server, or harness execution |
| PART 35 conflicting-library alignment | `DeployMCTestConflictingLibs` shadow JAR with unrelocated Netty 4.1.100.Final and canonical harness registry constants | Targeted clean shadow build produced the canonical JAR; ZIP descriptor, canonical class, 2,988 unrelocated Netty classes, and class major 52 inspected; offline harness suite grew to 15 passing tests; PR #181 merged at `fd92cd0` with post-merge CI/CodeQL/Scorecard pass; no running server or full harness execution |
| PART 35 log-noise fixture | Test-only `DeployMCTestLogNoise` plugin that emits all Java logging levels at config/system-property tick rates and cancels its tasks on disable | Targeted clean build produced the JAR; ZIP descriptor, seven config rates, and class major 52 inspected; PR #182 merged at `9b20b36` with post-merge CI/CodeQL/Scorecard pass; no backpressure test, server, or harness execution |
| PART 35 wrong-platform fixture | Test-only JAR with a Velocity `velocity-plugin.json` descriptor and no Bukkit `plugin.yml` | Targeted clean build produced the JAR; Velocity descriptor, absence of `plugin.yml`, and class major 52 inspected; PR #184 merged at `4819195` with post-merge CI/CodeQL/Scorecard pass; no validator run, server, or harness execution |
| PART 35 traversal per-OS expectations | Versioned `corpus-manifest.properties` with schema/corpus versions, SHA-256 digests, nine categories, and a per-OS override mechanism; all current outcomes are `os=all` because the resolver applies Windows rules on every host | Generator regenerated byte-identically with 2,736 reject and 29 allow payloads decoded identically to the prior corpus; 19 sandbox tests (18 passed, 1 skipped), 3 Python determinism tests, and the full local toolchain check passed; PR CI corpus tests passed on Ubuntu and Windows; PR #185 merged at `3b85de8` with post-merge CI/CodeQL/Scorecard pass; no scheduled fuzz run, server, or harness execution |
| PART 35 JWT vectors and mock JWKS | Versioned 11-vector corpus with fixed throwaway RSA keys generated only in memory, two public JWKS documents, a SHA-256 manifest, and a localhost-only dynamic-port mock server with rotation and 503 states | Generator regenerated byte-identically; 5 Python tests passed locally and in PR CI on Ubuntu and Windows; full local toolchain check passed; PR #187 merged at `ce4fdc7` with post-merge CI and Scorecard pass and CodeQL pass after a Maven Central 429 retry; no product JWT verifier, server, or external JWKS endpoint exercised |
| PART 35 hostile archive and YAML inputs | Versioned manifest-pinned corpus with six generated archives (zip-slip, 8 MiB fixed-Huffman zip bomb, duplicate entries, duplicate descriptors, real ZipCrypto entry, symlink entry) and four generated YAML documents (depth, aliases, large scalar, many nodes); binaries stay in ignored/temporary output | Generator regenerated byte-identically; 4 Python tests passed locally and in PR CI on Ubuntu and Windows; full local toolchain check passed; PR #189 merged at `49f300a` with post-merge CI/CodeQL/Scorecard pass; ZIP current-reader values are static source review and YAML product checks were not executed; no product validator, server, or integration consumer exercised |
| PART 35 log corpora | Versioned manifest-pinned UTF-8 corpora for multi-line stack traces, ANSI/OSC control sequences, boundary and 65,536-code-point lines, and unusual Unicode; generated at test time with no committed log text | Generator regenerated byte-identically; 6 Python tests passed locally and in PR CI on Ubuntu and Windows; full local toolchain check passed; PR #191 merged at `2a253a1` with post-merge CI/CodeQL/Scorecard pass; `current.consumer=not-implemented` reflects that no product console log consumer exists; no server, console endpoint, Web UI, or rendering code exercised |
| PART 35 config corpora | Seven small committed YAML fixtures for current valid, warning-only valid, invalid enum, invalid maximum, legacy version 0, future version 2, and missing version | Generator regenerated byte-identically; 4 Python tests and one Java test covering all seven fixtures passed locally and in PR CI on Ubuntu and Windows; full local toolchain check passed; `ConfigCorpusTest` exercises `ConfigValidator` and `ConfigManager` with no server or host configuration; PR #193 merged at `2b6d0f5` with post-merge CI/CodeQL/Scorecard pass |
| PART 35 large directory trees | Generated 388-file ASCII tree with 27 directories, a 12-level chain, a 209-character filename, and two 64 KiB binary files; generated output stays in ignored/temporary directories | Generator regenerated byte-identically; 4 Python tests passed locally and in PR CI on Ubuntu and Windows; full local toolchain check passed; manifest pins counts, depth, total bytes, and content digest; PR #195 merged at `3087abc` with post-merge CI/CodeQL/Scorecard pass; `current.listing.consumer=not-implemented` reflects that no product listing API or performance benchmark consumed the tree |
| PART 35 cross-implementation filter vectors | Versioned ASCII JSON manifest with 24 accepted/rejected expressions, canonical ASTs, fixed evaluation context, stable rejection codes, and declared limits | Generator regenerates byte-identically; 7 Python tests passed locally and in PR CI on Ubuntu and Windows; full local toolchain check passed; PR #197 merged at `9b7b5ee` with post-merge CI/CodeQL/Scorecard pass; `consumerStatus=not-implemented` for Java, TypeScript, and Rust because no product filter parser or evaluator exists; no parity claim |
| PART 33 file-safety suites | 24 tests for `SandboxResolver`, `FileOperationsService`, `YamlValidator`/`ValidatorRegistry`, `DiffEngine`/`DiffLimits`, and the hostile-input manifest | PR #199 merged at `0b83150` with post-merge CI/CodeQL/Scorecard pass; 24 `:core:test --tests '*Part33*'` tests PASSED locally; documented gaps recorded as `NOT IMPLEMENTED`; no server, UI, or E2E consumer |
| PART 33 runtime-limits suites | 16 tests for snapshot GC against the real store, log-attribution fixture contracts, and rate-limit/backpressure specs plus the bounded WebSocket queue | PR #200 merged at `98b6b79` with post-merge CI/CodeQL/Scorecard pass; rebased onto the #199 merge after the first merge attempt was blocked by a stale base; 35 `:core:test` and 5 `:transport:test` Part33 tests PASSED after rebase; no product rate limiter added |
| PART 33 auth/RBAC suites | 20 contract tests for JWT vectors, RBAC configuration and normative rules, and the deployment state-machine artifact | PR #201 merged at `5d279c6` after rebasing onto the #199/#200 merges; all required checks passed on the rebased head with post-merge CI/CodeQL/Scorecard pass; 55 `:core:test` and 5 `:transport:test` Part33 tests PASSED after rebase; no product verifier, evaluator, engine, or parity claim |
| PART 36 observability specification | Normative doctor report and problem codes, redaction rules, structured log and metric contracts, self-protection limits, and authenticated `/api/v1/server/doctor` and `/api/v1/metrics` routes | PR #203 merged at `072feda` with post-merge CI/CodeQL/Scorecard pass; `npm run lint` and `npm test` in `tools/spec` passed locally; no server, UI, or E2E consumer |
| PART 36 doctor core | Java 8 `DoctorService`, `DoctorInputs`, `DoctorReport.redacted()` transformation and `isRedacted()` flag, with deterministic environment and configuration checks | PR #205 merged at `7555c3d` with post-merge CI/CodeQL/Scorecard pass; focused core tests passed; no running server, command, or UI consumed the service at that stage |
| PART 36 transport observability | Configurable plain/JSON request logging, bounded metrics registry, opt-in authenticated metrics dispatch, authenticated doctor dispatch hook, and executor watchdog with server-free regression tests | PR #206 merged at `d42f5d2` with post-merge CI/CodeQL/Scorecard pass; 102 `:transport:test` tests passed, including a 10/10 watchdog flake recheck; no running server or browser |
| PART 36 Web doctor panel | Localized doctor status, check detail, redaction notice, and bearer-token request in the packaged Web UI | PR #204 merged at `2f3abfc` with post-merge CI/CodeQL/Scorecard pass; `pnpm lint`, `pnpm format:check`, `pnpm test` (14 tests), and `pnpm build` passed; no browser rendering |
| PART 36 Bukkit host wiring | Fail-closed host observability config, `bindPortOwnedByHost(true)`, authenticated doctor JSON, redacted 13-check `/deploymc doctor`, JSON/plain logger selection, and opt-in metrics handoff | PR #207 rebase-merged at `b882f95` as five separate Conventional Commits with post-merge CI/CodeQL/Scorecard pass; 73 Bukkit tests, `spotlessCheck`, `verifyJava8`, and `node tools/i18n/check` passed; no running server, mock Bukkit, browser, E2E, or in-game command |
| PART 40.3 early spikes | Seven records under `docs/research/spikes/` and a reproducible Windows file-replacement probe | PR #210 records the observed, source-verified, and `NOT RUN` results; Windows probe share-mode failure `0x80070020` observed; 6 log corpus tests, transport suite, Web/Java size subsets, and full `verify-all.cjs` passed locally; no real server, supervisor, Folia, browser, or editor benchmark |
| PART 41 quality reporting | Deterministic report over existing Web gzip and plugin JAR artifacts, with fail-closed handling and explicit `NOT MEASURED` for the other areas | PR #211 merged at `e0f6ddb`; 5 quality tests passed, check exit 0 with both budgets PASS, and full `verify-all.cjs`, commitlint, Spotless, and `git diff --check` passed; no other PART 41 gate was implemented |
| PART 39 spec change control | `spec-errata.md` with seed `E-001`, reviewed `spec/` PR workflow, owner approval rules, and mandatory milestone consistency audit | PR #212 merged at `d068990`; documentation-only change with full `verify-all.cjs`, commitlint, Spotless, and `git diff --check` passed; no requirement weakened or removed |
| PART 40.4 dependency policy | Selection, pinning, review, update, exemption, advisory verification, severity response, and release-branch security policy with recorded gaps | Final evidence PR for #123; documentation-only change with full `verify-all.cjs`, commitlint, Spotless, and `git diff --check` passed; no Dependabot PR, advisory, or setting changed |

The WP-2.3 implementation all-toolchain run was `node tools/build/verify-all.cjs` on the merged tree
after PR #159, with `DEPLOYMC_JAVA_ROOT` set to the existing ASCII junction; it reported `PASS` for
`web`, `java`, `cli`, `python`, `spec`, `i18n`, and `compat`. Spotless had been applied with
`.\gradlew.bat spotlessApply --console=plain --no-configuration-cache` from that same junction before
the implementation PR was opened. The full Gradle build ran 230 Java tests with 0 failures, 0 errors,
and 1 skipped symlink
test, which this unprivileged Windows host cannot run; the 25 snapshot-store tests are included in
that total. The earlier WP-2.2 focused file command `.\gradlew.bat spotlessApply :core:test --tests
'*File*' --console=plain --no-configuration-cache` completed 26 tests: 24 new WP-2.2 tests and 2
existing tests whose names matched the filter; all passed. The earlier WP-2.1 sandbox suite completed
16 tests (15 passed, 1 skipped), and `python tests/security/sandbox/generate_corpus.py` produced
`reject=2736 allow=29`. Web runs before Java in the all-toolchain script and in the CI Java job
because the plugin build packages `web/dist` into the JAR. WP-1.8 was merged in
[PR #151](https://github.com/woxlo1/DeployMC/pull/151) at
`839b789ae8483f83dcb4b430641b7f0eafccb303`; the PR head run
[44704128125](https://github.com/woxlo1/DeployMC/actions/runs/44704128125) passed every required
check, including `Compatibility (ubuntu-latest)` and `Compatibility (windows-latest)`. The
post-merge `main` CI run [44704690836](https://github.com/woxlo1/DeployMC/actions/runs/44704690836)
passed every job, CodeQL
[44704690767](https://github.com/woxlo1/DeployMC/actions/runs/44704690767) passed all three
languages, and Scorecard
[44704690925](https://github.com/woxlo1/DeployMC/actions/runs/44704690925) passed. The two CI
compatibility artifacts contain passing Paper 26.3 build 256 results on Linux and Windows; they were
rendered into the [support matrix](../compatibility/support-matrix.md). Issue #48 closed as
completed at 2026-11-20T08:42:28Z. These runs cover the smoke suite and build/unit checks, not the
full integration suite or a support tier. The one-command tool is documented in
[`tools/build/README.md`](../../tools/build/README.md); CI remains the merge authority.

WP-2.1 merged in [PR #153](https://github.com/woxlo1/DeployMC/pull/153) at
`748bbc8adda99e1aae9eeaca600863b44ff2f2ab`; issue #50 closed as completed at
2026-11-20T10:06:27Z. An explicit PR comment recorded the local commands, CI investigation, and
review notes at
<https://github.com/woxlo1/DeployMC/pull/153#issuecomment-7036766443>. All PR checks passed on
`9d52a85`, including `Java (windows-latest)` after the canonical-root assertion fix; PR runs were
CI [44715858946](https://github.com/woxlo1/DeployMC/actions/runs/44715858946), lint
[44715858928](https://github.com/woxlo1/DeployMC/actions/runs/44715858928), CodeQL
[44715858945](https://github.com/woxlo1/DeployMC/actions/runs/44715858945), and dependency review
[44715858883](https://github.com/woxlo1/DeployMC/actions/runs/44715858883). Post-merge `main` runs
at `748bbc8` passed: CI [44716375506](https://github.com/woxlo1/DeployMC/actions/runs/44716375506),
CodeQL [44716375542](https://github.com/woxlo1/DeployMC/actions/runs/44716375542), and Scorecard
[44716375486](https://github.com/woxlo1/DeployMC/actions/runs/44716375486). The local and remote
`feature/wp-2.1-sandbox-resolver` branches were deleted after the merge. These runs cover unit,
corpus, fuzz, build, and compatibility evidence; they do not claim running-server or file-API use of
`SandboxPath`.

WP-2.2 merged in [PR #155](https://github.com/woxlo1/DeployMC/pull/155) at
`017c2abd1549e6c3aa8f947f6324df9da97ccb59`; issue #51 closed as completed at
2026-11-20T10:59:33Z. Implementation notes and the Linux CI failure investigation were recorded in
PR comments at <https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7037691314> and
<https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7037772338>; the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7054409687>. All required
checks passed at PR head `595fde0` after the conflict-reason fix. Post-merge `main` runs at
`017c2ab` passed: CI [44723836623](https://github.com/woxlo1/DeployMC/actions/runs/44723836623),
CodeQL [44723836580](https://github.com/woxlo1/DeployMC/actions/runs/44723836580), and Scorecard
[44723836649](https://github.com/woxlo1/DeployMC/actions/runs/44723836649). The local and remote
`feature/wp-2.2-file-operations` branches were deleted after the merge.

WP-2.2 (issue #51) adds the implemented `core.files` service described in
[file operations](../../specification/files/file-operations.md). It resolves every request path
through `SandboxResolver`, returns relative paths only, and uses `sha256:` revisions computed from
full file bytes as ETag sources. Writes hash a same-directory temporary file, force its content,
recheck the target, and commit with one `ATOMIC_MOVE`; there is no non-atomic fallback. In-process
writes use 256 fixed stripes, while cross-process writers are detected by revision and file-identity
rechecks rather than serialized. Overwrite, move, and delete fail closed without a `SnapshotHook`,
which is the boundary for the later snapshot store. Recursive deletion requires confirmation of the
current entry count and obeys a host-controlled cap. A deterministic test injects termination
immediately before and after the atomic commit; it does not launch or kill an operating-system
process. No REST endpoint, transport handler, running server, or UI consumes the service yet.

WP-2.3 first stage added the normative contract in
[snapshot store](../../specification/snapshot/snapshot-store.md), merged in
[PR #158](https://github.com/woxlo1/DeployMC/pull/158) at
`879bdc5acd2abea4af9a14dd3ea9bd2b79d374ef`. On 2026-11-21, `npm run lint` passed the OpenAPI,
AsyncAPI, configuration, and state-machine checks in `tools/spec`, and `npm test` ran 14 tests with
0 failures. Required PR checks and post-merge CI
[44849766505](https://github.com/woxlo1/DeployMC/actions/runs/44849766505), CodeQL
[44849766469](https://github.com/woxlo1/DeployMC/actions/runs/44849766469), and Scorecard
[44849766325](https://github.com/woxlo1/DeployMC/actions/runs/44849766325) passed. The superseded
specification PR #157 and its branch were closed and removed; the feature branch for the
implementation stage is separate. That stage defines the contract only.

WP-2.3 implementation merged in [PR #159](https://github.com/woxlo1/DeployMC/pull/159) at
`0425c814018091543929444042dcd6d885bd75d5`; issue #52 closed as completed at
2026-11-21T02:11:54Z. It adds format-1 JSON records, immutable SHA-256 blobs under `objects/ab/`,
snapshot and per-path indexes, root-validated capture through `SnapshotHook`, restore-first
reversibility for files and deletion markers, host-controlled retention, verify/repair reports, GC
guarded by verify plus an active-deployment check and a 24 h grace period, and an OS data-directory
lock with an in-process fair lock. `SnapshotRequest` carries actor, deployment id, and deterministic
plugin metadata; `SnapshotEntry.deleted(...)` preserves an absent target so a restore remains
reversible. `FileOperationsService` passes the canonical sandbox root to the hook so a foreign-root
request fails closed. On 2026-11-21, `.\gradlew.bat spotlessApply --console=plain
--no-configuration-cache` and `node tools/build/verify-all.cjs` passed locally; the Java build ran
230 tests (0 failures, 0 errors, 1 skipped symlink test), including 25 snapshot tests. Explicit PR
comments are at <https://github.com/woxlo1/DeployMC/pull/159#issuecomment-7055673480> and
<https://github.com/woxlo1/DeployMC/pull/159#issuecomment-7055770391>. All PR checks passed at head
`f0fd908`. Post-merge `main` runs at `0425c814` passed: CI
[44854531337](https://github.com/woxlo1/DeployMC/actions/runs/44854531337), CodeQL
[44854531422](https://github.com/woxlo1/DeployMC/actions/runs/44854531422), and Scorecard
[44854531350](https://github.com/woxlo1/DeployMC/actions/runs/44854531350). The local and remote
`feature/wp-2.3-snapshot-store` branches were deleted after the merge. At that stage no REST endpoint
or transport handler consumed the store; WP-2.6 later added the snapshot REST adapters, still without
a running server, UI, or in-game command.

WP-2.4 implementation (issue #53) adds Java 8 `core.diff`: a pure byte-array engine with bounded
Myers line and word diffs, hardened YAML/JSON key-path diffs with stable text fallback, metadata-only
ZIP central-directory and plugin-descriptor diffs, sorted machine-code notes, and inclusive 2 MiB
textual limits. It never opens a file, resolves a path, executes a process, or reads host
configuration. On 2026-11-21, `.\gradlew.bat :core:test --tests '*Diff*' --console=plain
--no-configuration-cache` passed all 31 `DiffEngineTest` cases, and `.\gradlew.bat spotlessApply
--console=plain --no-configuration-cache` passed. With `DEPLOYMC_JAVA_ROOT` set to the ASCII junction,
`node tools/build/verify-all.cjs` reported `PASS web`, `PASS java`, `PASS cli`, `PASS python`,
`PASS spec`, `PASS i18n`, and `PASS compat`. Its Java step ran `build verifyJava8 --console=plain
--no-configuration-cache`; JUnit XML totals were 261 tests, 0 failures, 0 errors, and 1 skipped
symlink test. Plugin JAR verification reported `4492483 bytes, 2619 classes (0 versioned), max common
bytecode 52`. The 31 tests cover identical and empty inputs, both sides of the 2 MiB boundary,
lowered limits, CR/LF/CRLF and EOF endings, BOM handling, invalid UTF-8, deterministic moved-block
output, YAML rename and pure reorder, pointer escaping, parse fallback, word segmentation, JAR
version and dependency changes, ZIP edge cases, truncated or invalid central directories, ZIP64
rejection, descriptor BOM and duplicate handling, invalid entry names, JSON escaping, and repeated-run
stability. At that stage no REST endpoint or transport handler consumed the engine; the WP-2.6
snapshot diff route now does, with no running server, UI, or in-game command.

WP-2.4 implementation merged in [PR #163](https://github.com/woxlo1/DeployMC/pull/163) at
`80a3f0b7eb50718d85ccca3d4d5a4f71b1a97633`; issue #53 closed as completed at
2026-11-21T09:06:23Z. Explicit PR comments are at
<https://github.com/woxlo1/DeployMC/pull/163#issuecomment-7060316351> and
<https://github.com/woxlo1/DeployMC/pull/163#issuecomment-7060391484>. All PR checks passed at head
`1a01f54`, including Java on both hosts, both compatibility jobs, Spec, Web, CLI on three hosts,
Python on both hosts, i18n, CodeQL, and semantic title validation. Post-merge `main` runs at
`80a3f0b` passed: CI
[44887980566](https://github.com/woxlo1/DeployMC/actions/runs/44887980566), CodeQL
[44887980567](https://github.com/woxlo1/DeployMC/actions/runs/44887980567), and Scorecard
[44887980590](https://github.com/woxlo1/DeployMC/actions/runs/44887980590). The local and remote
`feature/wp-2.4-diff-engine` branches were deleted after the merge.

WP-2.5 first stage defines the [content validation contract](../../specification/validation/content-validation.md)
and ADR-0016 before implementation. It specifies pure, bounded validators for YAML, JSON, TOML, and
`.properties`; deterministic registry precedence; warning/error separation; a fail-closed save-time
gate; the authoritative validation endpoint result; and a byte-preserving YAML scalar editor. No
validator code exists yet, and no REST endpoint consumes validators. The implementation stage must
add `core.validation`, wire the gate into `core.files`, and record the evidence listed in the
contract. On 2026-11-21, `npm run lint` in `tools/spec` passed the OpenAPI, AsyncAPI, configuration,
and state-machine checks, and `npm test` ran 14 tests with 0 failures. The specification merged in
[PR #165](https://github.com/woxlo1/DeployMC/pull/165) at
`6cd6015a141d8a108b7507a9f801b646679698cc`; post-merge `main` runs were CI
[44890406029](https://github.com/woxlo1/DeployMC/actions/runs/44890406029), CodeQL
[44890406052](https://github.com/woxlo1/DeployMC/actions/runs/44890406052), and Scorecard
[44890406037](https://github.com/woxlo1/DeployMC/actions/runs/44890406037), all successful. The
post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/165#issuecomment-7060731358>.

WP-2.5 implementation adds `core.validation`: strict UTF-8 decoding with byte offsets, bounded
validators for YAML, JSON, TOML, and `.properties`, stable issue codes and RFC 6901 pointers,
localized English/Japanese details, deterministic finding order, a registry with exact-path,
pattern, and extension precedence, and a byte-preserving single-scalar YAML editor. A host-wired
`ValidationGate` runs after path resolution and preconditions and before snapshots, writes, moves,
and deletes; errors, limits, and unexpected validator failures abort the operation, while warnings
are exposed through `ValidatedFileResult`. No request parameter can change the gate or its limits,
and no REST endpoint consumes the validators yet.

On 2026-11-21, `git diff --check`, `node tools/i18n/check` (0 errors, 0 warnings), `npm run lint` in
`tools/spec`, `.\\gradlew.bat spotlessApply --console=plain --no-configuration-cache`,
`.\\gradlew.bat build --console=plain --no-configuration-cache`, and
`.\\gradlew.bat verifyJava8 --console=plain --no-configuration-cache` passed from the ASCII junction.
JUnit XML totals were 288 tests, 0 failures, 0 errors, and 1 skipped symlink test, including 27 new
validation tests; the skipped test requires privileges unavailable on this Windows host. Plugin JAR
verification reported `4567069 bytes, 2657 classes (0 versioned), max common bytecode 52`. The
one-command `node tools/build/verify-all.cjs` then reported `PASS` for `web`, `java`, `cli`, `python`,
`spec`, `i18n`, and `compat`. D-008 was resolved by the owner on 2026-11-21: every structural
budget failure is top-level `validation.limit-exceeded` with a format-specific issue code in
`errors[]`. YAML and TOML node/depth budgets already abort with `validation.limit-exceeded`; the
JSON depth path still returns a `validation.json-depth` ERROR finding and must move to that shape in
WP-2.6.

The implementation merged in [PR #166](https://github.com/woxlo1/DeployMC/pull/166) at `9363e0c` with
issue #54 closed. All required PR checks passed before merge. Post-merge workflows on `main` succeeded:
CI [44893521743](https://github.com/woxlo1/DeployMC/actions/runs/44893521743), CodeQL
[44893521769](https://github.com/woxlo1/DeployMC/actions/runs/44893521769), and Scorecard
[44893521898](https://github.com/woxlo1/DeployMC/actions/runs/44893521898). The post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/166#issuecomment-7061152266>.

WP-2.6 first stage merged the file/validation REST contract in
[PR #168](https://github.com/woxlo1/DeployMC/pull/168) at `8970728b7872ebe844c510144533485ba4b197ce`.
The explicit verification comment is at
<https://github.com/woxlo1/DeployMC/pull/168#issuecomment-7061469819> and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/168#issuecomment-7061542061>. Post-merge
`main` runs passed: CI
[44896183648](https://github.com/woxlo1/DeployMC/actions/runs/44896183648), CodeQL
[44896183641](https://github.com/woxlo1/DeployMC/actions/runs/44896183641), and Scorecard
[44896183615](https://github.com/woxlo1/DeployMC/actions/runs/44896183615). The local and remote
`spec/wp-2.6-file-endpoints` branches were deleted after the merge evidence was posted.

The WP-2.6 adapter stage adds a transport-level `FileApiHandler` over `SandboxResolver`,
`FileOperationsService`, `ValidatorRegistry`, and `SnapshotStore`, plus a Bukkit `FileApiRuntime`
that builds those services from host configuration and closes the single-instance store on disable or
startup failure. The token gate runs before the handler; there is no development bypass. Revisions are
`sha256:<hex>`, ETags are quoted, `Range` is start-inclusive/end-exclusive as specified, and every
failure uses a stable code with localized RFC 9457 Problem Details. Absolute host paths, tokens, and
file contents are never placed in error details. `FileApiRuntime` currently accepts only the default
`snapshots.directory: .deploymc/history` and fails closed for other values. A regression test caught
and fixed a budget mismatch where the runtime rejected schema-valid 2048 MiB snapshot and 4096 MiB
upload settings.

On 2026-11-21, the stale Spotless configuration-cache entry was removed and
`.\\gradlew.bat spotlessApply --no-configuration-cache --console=plain` passed through the ASCII
junction. Focused transport tests (`--tests '*ProblemDetailsTest' --tests '*FileApiHandlerTest'`)
passed 14 tests; focused Bukkit tests (`--tests '*FileApiRuntimeTest' --tests
'*DeployMCHttpHandlerTest'`) passed 8 tests. The full `.\\gradlew.bat build verifyJava8
--no-configuration-cache --console=plain` passed with 311 tests, 0 failures, 0 errors, and 1 skipped
symlink test; JAR verification reported max common bytecode 52. `node tools/i18n/check` reported
0 errors and 0 warnings, and `git diff --check` produced no output. No running-server smoke test,
end-to-end run, in-game `/deploymc` execution, or Web UI browser check was performed after this
wiring, and the full integration suite has not run. Until 0.4.0 the token remains admin-equivalent;
the snapshot-route adapters were added by the following WP-2.6 stage, and exact-byte preservation
for existing NFD filenames is still not implemented.

The adapter stage merged in [PR #169](https://github.com/woxlo1/DeployMC/pull/169) at
`08fe4245af2d2de474d644e71ca79339cc57dfd3`. The explicit verification comment is at
<https://github.com/woxlo1/DeployMC/pull/169#issuecomment-7062245147> and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/169#issuecomment-7062327345>. Post-merge
`main` runs passed: CI
[44901920780](https://github.com/woxlo1/DeployMC/actions/runs/44901920780), CodeQL
[44901920707](https://github.com/woxlo1/DeployMC/actions/runs/44901920707), and Scorecard
[44901920664](https://github.com/woxlo1/DeployMC/actions/runs/44901920664). The local and remote
`feature/wp-2.6-file-validation-endpoints` branches were deleted after the evidence was posted.

The WP-2.6 snapshot follow-up adds six normative `/api/v1/snapshots` operations. `GET` list uses a
required sandbox path and opaque cursor; `GET {id}` and `GET {id}/content` use an opaque id and
quoted ETags; content ranges are start-inclusive/end-exclusive; `GET diff` compares two ids through
the bounded `core.diff` result envelope; `POST {id}/restore` restores only the record's own path and
captures the current state first; and `POST prune` accepts exactly one path or id and never starts
GC. The contract requires authentication, forbids host paths, derives the restore actor from the
authenticated identity, and reserves path-scope checks for RBAC. On 2026-11-21, `npm run lint` and
`npm test` in `tools/spec` passed before merge. The contract merged in
[PR #170](https://github.com/woxlo1/DeployMC/pull/170) at
`bf82e265742a36914423ff2f3d62612a4326945d`. The explicit verification comment is at
<https://github.com/woxlo1/DeployMC/pull/170#issuecomment-7062418966> and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/170#issuecomment-7062501576>. Post-merge
`main` runs passed: CI
[44903156530](https://github.com/woxlo1/DeployMC/actions/runs/44903156530), CodeQL
[44903156565](https://github.com/woxlo1/DeployMC/actions/runs/44903156565), and Scorecard
[44903156579](https://github.com/woxlo1/DeployMC/actions/runs/44903156579). The local and remote
`spec/wp-2.6-snapshot-endpoints` branches were deleted after the evidence was posted.

The snapshot adapter stage adds `transport.SnapshotApiHandler` for all six routes and wires it
through `FileApiRuntime`, `DeployMCPlugin`, and `DeployMCHttpHandler` behind the bearer-token gate.
The handler resolves every path through `SandboxResolver`, accepts only opaque `snap_` ids, uses
quoted `sha256:<hex>` ETags for records and content, treats ranges as start-inclusive and
end-exclusive, and returns localized RFC 9457 codes. Restore takes its actor only from the
authenticated `Identity`, uses the record's own path, requires `If-Match` when the target exists, and
captures the current state first. Prune accepts exactly one path or id, preserves protected records,
and never starts GC. The diff route projects the bounded result through the OpenAPI
`SnapshotDiff` schema, excluding internal diagnostic fields. `SnapshotStore.readContent` verifies the
blob hash before returning bytes; deletion and directory markers fail closed. No absolute host path,
token, or content is placed in an error detail.

On 2026-11-22, focused `SnapshotApiHandlerTest` (12 tests), `SnapshotStoreTest` (28 tests), the added
`AuthenticationGateTest` identity case, and the Bukkit routing/runtime assertions passed. The full
`.\\gradlew.bat build verifyJava8 --no-configuration-cache --console=plain` reported 328 JUnit tests,
0 failures, 0 errors, and 1 skipped symlink test. Review fixes pin absence under the store lock during
restore, protect records younger than `minAgeDays` from every prune path, clamp oversized `limit`
values, honour `Accept-Language` q-values, bound diff input before blobs are read, and mark internal
failures retryable. With `DEPLOYMC_JAVA_ROOT` set to the ASCII junction,
`node tools/build/verify-all.cjs` reported `PASS` for `web`, `java`, `cli`, `python`, `spec`, `i18n`,
and `compat`; `node tools/i18n/check` reported 0 errors and 0 warnings, and `git diff --check`
produced no output. No running-server smoke, full integration suite, E2E run, in-game `/deploymc`
execution, or Web UI browser check was performed for these routes.

The explicit verification comment for head commit `8763082` is at
<https://github.com/woxlo1/DeployMC/pull/172#issuecomment-7072665699>. PR #172 squash-merged to
`main` at `136ce00ffc67b472872d8966306e29e1e5f36b39` on `2026-11-22T02:08:39Z`, and issue #55 closed
as completed at `2026-11-22T02:08:41Z`. The post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/172#issuecomment-7072756119>. Post-merge `main` runs passed:
CI [44974693821](https://github.com/woxlo1/DeployMC/actions/runs/44974693821), CodeQL
[44974693864](https://github.com/woxlo1/DeployMC/actions/runs/44974693864), and Scorecard
[44974693855](https://github.com/woxlo1/DeployMC/actions/runs/44974693855). The local and remote
`feature/wp-2.6-snapshot-adapters` branches were deleted after the evidence was posted.

## Repository configuration, not a product feature

WP-0.8 added six issue forms with a security contact redirect, a pull request template, a CODEOWNERS
file, Dependabot update configuration, a label source-of-truth TSV, and a label sync workflow. These
are GitHub configuration files; they do not make DeployMC run. They are described in
[community configuration](community.md). The label sync workflow is configured to run on `main`
after this work is merged; its first run is not evidence in this status page.

WP-1.1 added the executable [capability catalog](../architecture/platform-spi.md#executable-capability-catalog)
and a `FakePlatformAdapter` test double in `core/src/test/java`. The tests run with no server and
verify capability keys, fallback metadata, and the SPI contract. WP-1.2 then added the first real
adapter described below.

WP-1.2 adds `platform/bukkit` with a generated descriptor, a `JavaPlugin` entry point, an SPI
adapter using only public Bukkit 1.8.8 API, no-NMS version detection, a localized startup banner,
explicit capability states, and tracked task cleanup. The generated Shadow JAR was inspected and
contains `plugin.yml`, the entry point, and the message catalog, with no `org/bukkit/**` classes.
`.\gradlew.bat build verifyJava8 --console=plain` passed on 2026-11-18; the JUnit XML reports 70
tests, 0 failures, and 0 errors across all Java modules. The descriptor choice is documented in
[Bukkit descriptor baseline research](../research/bukkit-descriptor-baseline.md). Loading on Paper
latest and comparing thread dumps before and after a clean disable were not performed and remain
in [issue #48](https://github.com/woxlo1/DeployMC/issues/48). No support tier is claimed.

WP-1.3 adds the embedded transport in `transport/`. The owner accepted ADR-0003 for Netty
4.1.141.Final after the measured [transport spike](../research/transport-spike.md). The implementation
keeps Netty types behind internal interfaces and provides request IDs, redacted logging through a
Java catalog, header/body/connection/idle limits, Host/Origin validation hooks, CORS disabled by
default, security headers, port-in-use errors, graceful shutdown, and bounded WebSocket sessions.
`.\gradlew.bat build verifyJava8 --console=plain` passed on 2026-11-18 through the ASCII junction;
the JUnit XML reports 79 tests, 0 failures, and 0 errors across all Java modules. The nine
`NettyTransportServerTest` cases cover lifecycle, HTTP security headers, oversized body, idle
connection close, port-in-use, Host/Origin rejection, WebSocket echo, queue bound, and no surviving
`DeployMC-netty-*` threads. The Shadow JAR inspection reported 2,164 relocated Netty classes, 0
unrelocated Netty classes, 0 duplicate entries, and no service files. No Minecraft server was
started, no application routes are registered, and the real Paper conflicting-library classloader
check remains in the compatibility harness work; the exact deferred steps were recorded on
[issue #48](https://github.com/woxlo1/DeployMC/issues/48#issuecomment-6975215752). No support tier
is claimed.

WP-1.4 adds `StaticAssetHandler` in `transport/`. It resolves classpath names only below the fixed
`deploymc-web/` root, decodes paths once, rejects raw and encoded separators, dot segments, NUL,
malformed percent escapes, and double-encoded traversals, and returns 404 without SPA fallback for
missing extensioned assets. Unknown extensionless routes fall back to `index.html`; paths under
`/api` are delegated to an application handler or return 404 and are never served as the SPA. HTML
receives a worker-capable CSP, hashed assets receive one-year immutable caching, other assets receive
a one-hour cache, and `.br`/`.gz` variants generated by the Web build are selected by
`Accept-Encoding`. Netty security headers now preserve handler-supplied `Content-Security-Policy`
and `Cache-Control` values while still defaulting to restrictive values for JSON responses.
`.\gradlew.bat build verifyJava8 --console=plain` passed on 2026-11-18 through the ASCII junction;
the JUnit XML reports 93 tests, 0 failures, and 0 errors. `node tools/build/verify-all.cjs` reported
`PASS` for all six toolchains. JAR entry inspection listed `deploymc-web/index.html`, hashed JS/CSS,
source map, and gzip/Brotli variants. No browser rendering, real server load, or authenticated
route was exercised; no support tier is claimed. No new user-facing strings were introduced.

The first WP-1.5 stage adds the configuration loader in `core` and the documentation in
[configuration](../configuration.md). SnakeYAML 2.7 is parsed only through a hardened wrapper that
rejects duplicate keys, aliases, recursive and non-scalar keys, non-standard tags, excessive
nesting, and oversized input. The packaged JSON Schema is validated with 1-based line and column
findings; role references are checked separately. Startup writes a commented `config.yml` with
`auth.mode: local-onetime`, backs up and migrates older versions before an atomic write, and fails
closed on any parse, version, migration, validation, or role-reference error. `ConfigManager.reload()`
revalidates before swapping an immutable `ConfigView`, so an invalid file preserves the last known
good configuration. English and Japanese diagnostic catalogs localize messages. The second stage was
not part of PR #139, which merged at `5fc04a1` with all 16 required checks passing.

`.\gradlew.bat spotlessApply :core:test --console=plain`, `.\gradlew.bat build verifyJava8
--console=plain`, and JAR inspection were run on 2026-11-18 through the ASCII junction. The JUnit XML
reports 121 tests, 0 failures, and 0 errors across all modules, including 53 `core` tests; the Java 8
bytecode check passed. The property test generated 250 deterministic invalid mutations and verified
that all failed validation and never became active. JAR inspection reported 254 relocated and 0
unrelocated SnakeYAML classes, one schema resource, and one default template. No plugin startup,
command execution, real server load, or conflicting-library classloader test was performed; no
support tier is claimed. Post-merge `main` workflows for PR #139 all passed: CI
[44481755600](https://github.com/woxlo1/DeployMC/actions/runs/44481755600), CodeQL
[44481755589](https://github.com/woxlo1/DeployMC/actions/runs/44481755589), and Scorecard
[44481755610](https://github.com/woxlo1/DeployMC/actions/runs/44481755610).

The second WP-1.5 stage wires the loader into Bukkit startup and adds `/deploymc status|reload|doctor|token`.
The generated descriptor registers the command behind `deploymc.command.use` and declares five
operator-only permission nodes. Command logic lives in a server-independent `ConfigCommandHandler` with
a thin Bukkit executor, so permissions, tab completion, status output, safe reload, limited doctor
coverage, and console-only token behavior are covered without a server. `doctor` reports only active
validated configuration, loopback bind, selected auth mode, and catalog completeness, and always states
that it is not a support-tier verdict. `token` creates nothing because provisioning belongs to WP-1.9.
English and Japanese command catalogs use the same `ResourceBundle` convention as the startup banner;
the Japanese text is a machine-translated draft awaiting native review. `LocalizedMessages` now disables
default-locale fallback and falls back only to the English base catalog.

`.\gradlew.bat spotlessApply :platform:bukkit:test --console=plain` and
`.\gradlew.bat spotlessApply build verifyJava8 --console=plain` were run on 2026-11-19 through the ASCII
junction. The JUnit XML reports 133 tests, 0 failures, and 0 errors across all modules, including 42
Bukkit tests; the Java 8 bytecode check passed. JAR inspection confirmed the generated command and
permission blocks. `node tools/i18n/check` reported 0 errors and 0 warnings. No plugin startup, real
command execution on a server, browser rendering, or conflicting-library classloader check was
performed; no support tier is claimed. PR #141 merged at `f853136` with all required checks passing.
Post-merge `main` CI [44485350410](https://github.com/woxlo1/DeployMC/actions/runs/44485350410) and
Scorecard [44485350476](https://github.com/woxlo1/DeployMC/actions/runs/44485350476) passed. The first
post-merge CodeQL attempt failed during autobuild because Gradle could not resolve plugin dependencies
from `plugins.gradle.org`; a rerun of
[44485350346](https://github.com/woxlo1/DeployMC/actions/runs/44485350346) passed all three language
analyses. The pull-request CodeQL analysis had already passed for the same commit.

WP-1.6 adds the Bukkit-family plugin message runtime documented in
[plugin messages](../i18n/plugin-messages.md). `LocalizedMessages` reads `.properties` resources
directly and resolves each key through the sender locale, configured server default, and English
base catalog. It never consults the JVM default locale. A partial translation can override one key;
absent keys fall through one at a time. Named `{placeholder}` values are inserted literally, and
missing keys or placeholders return catalog diagnostics. `SenderLocaleResolver` feature-detects
public `locale()` or `getLocale()` accessors returning `Locale` or a language tag; unavailable
accessors fall back to the configured default. `LegacyColorFormatter` translates valid `&` codes
only in the Bukkit send path and has no `ChatColor` or text-component dependency.

`.\gradlew.bat spotlessApply :platform:bukkit:test build verifyJava8 --console=plain` and
`node tools/i18n/check` were run on 2026-11-19 through the ASCII junction. The Bukkit task reported
50 tests with 0 failures; all Java modules reported 143 tests, 0 failures, and 0 errors in the JUnit
XML, and the Java 8 bytecode check passed. The i18n checker reported 0 errors and 0 warnings for the
Web catalogs. No new user-facing strings were added; the Japanese catalog remains a machine-translated
draft awaiting native review. No real server load, in-game command execution, browser rendering,
conflicting-library classloader check, or text-component compatibility check was performed; no
support tier is claimed. PR #144 merged at `d38217f` with all required checks passing after a Java
CodeQL autobuild dependency-resolution failure and successful rerun. Post-merge `main` CI
[44490811820](https://github.com/woxlo1/DeployMC/actions/runs/44490811820), CodeQL
[44490811804](https://github.com/woxlo1/DeployMC/actions/runs/44490811804), and Scorecard
[44490811934](https://github.com/woxlo1/DeployMC/actions/runs/44490811934) passed. Evidence PR #145
merged at `e812be6` with all required checks and post-merge CI, CodeQL, and Scorecard runs passing,
and issue #46 was closed.

WP-1.7 adds mandatory relocation and a final-JAR verification gate. `plugin/build.gradle.kts` keeps
Shadow relocations for `io.netty` and `org.yaml.snakeyaml` under
`io.github.woxlo1.deploymc.lib.*`, excludes `module-info.class` and all `META-INF/versions/**`, and
declares the relocation and exclusion inputs so a local configuration change invalidates the Shadow
task. The `buildSrc` task `PluginJarVerifierTask` is wired into `check` and fails on classes outside
`io/github/woxlo1/deploymc/`, malformed or manifest-less multi-release entries, versioned classes
without a base entry, duplicate ZIP entries or logical classes, service files or providers outside
the namespace, and common-path bytecode above major 52. `PluginJarSizeTask` writes a
locale-independent size line for CI.

The YAML dependency moved from `io.github.snake-yaml:snakeyaml:2.9` to
`org.yaml:snakeyaml:2.7`. The 2.9 artifact's common classes were major 53 and its manifest required
JavaSE 9 despite a release-8 POM; 2.7 common classes are major 52 with manifest JavaSE 1.8. The
official 2.7 notes and artifact evidence are recorded in
[configuration YAML library check](../research/config-yaml-library.md).

The following commands were run on 2026-11-19 through the ASCII junction:

```text
.\gradlew.bat spotlessApply build verifyJava8 --console=plain
  BUILD SUCCESSFUL; verifyPluginJar passed; Java 8 bytecode check passed
  JUnit XML aggregate: 143 tests, 0 failures, 0 errors
  Plugin JAR verification passed: DeployMC-0.1.0-SNAPSHOT.jar, 4268456 bytes,
    2493 classes (0 versioned), max common bytecode 52
.\gradlew.bat :plugin:reportPluginJarSize --console=plain
  Plugin JAR size: 4268456 bytes (4.07 MiB): DeployMC-0.1.0-SNAPSHOT.jar
git diff --check
  pass
node tools/build/verify-all.cjs
  PASS web, java, cli, python, spec, i18n
```

A forced `.\gradlew.bat :plugin:shadowJar --rerun-tasks --console=plain` was run after changing the
exclusion rules because Shadow 8.3.5 did not invalidate the task from those rules; explicit task
inputs were added. Final ZIP inspection reported 2,681 entries, 2,493 classes, 230 relocated
SnakeYAML classes, 0 unrelocated `org/yaml/**` or `io/netty/**` class entries, 0 multi-release
entries, 0 `module-info.class` entries, 0 service files, and 0 duplicate names.

Twelve fixture JARs are generated by a committed task and were checked with the non-build task:

```powershell
.\gradlew.bat :plugin:generatePluginJarFixtures --console=plain
.\gradlew.bat --project-prop=deploymc.verifyJarFile=build/fixtures/<name>.jar :plugin:verifyPluginJarFixture --console=plain
```

`good.jar`, `valid-mr.jar`, and `good-service.jar` exited 0. The nine bad fixtures exited 1 with the
expected messages: `outside.jar` (class outside namespace), `new-bytecode.jar` (major 53 above
baseline), `malformed-mr.jar` and `old-mr.jar` (malformed version segment),
`mr-without-manifest.jar` (missing `Multi-Release: true`), `missing-base.jar` (no base entry),
`duplicate.jar` (duplicate entry), `bad-service.jar` (service file outside namespace), and
`bad-service-provider.jar` (provider outside namespace).

PR #147 merged at `db3e33a` on 2026-11-19T09:49:21Z; issue #47 closed at
2026-11-19T09:49:23Z. All 18 pull-request checks passed (16 required). Post-merge `main` runs
passed: CI [44499929954](https://github.com/woxlo1/DeployMC/actions/runs/44499929954), CodeQL
[44499932471](https://github.com/woxlo1/DeployMC/actions/runs/44499932471), and Scorecard
[44499929986](https://github.com/woxlo1/DeployMC/actions/runs/44499929986). These runs cover build
and unit checks, not a real server.

No real Minecraft server load, in-game command execution, browser rendering, conflicting-library
classloader test, or text-component compatibility check was performed; no support tier is claimed.
No user-facing strings or i18n catalogs changed.

WP-1.9 adds the minimal authentication foundation required from the first release. `core/auth`
defines `Authenticator`, `AuthenticationResult`, `Identity`, and a host-provisioned `TokenStore`.
Records live only under `plugins/DeployMC/tokens.d/*.json`: the directory is created when absent,
an empty directory is valid, and invalid, unreadable, duplicate, or unexpected files refuse startup
with a localized message. Only `id`, `label`, a 64-character lowercase SHA-256 `secretHash`, and an
optional quoted RFC 3339 `expiresAt` are accepted. Verification uses `MessageDigest.isEqual()`.
`TokenProvisioner` generates `dmc_` plus 32 random bytes encoded as unpadded Base64URL, prints the
secret once, and stores only its digest.

`NettyTransportServer` now authenticates every HTTP and WebSocket request before application
handling. `/healthz` and `/readyz` remain the only unauthenticated routes. Failure responses use
RFC 9457 Problem Details with `auth.required`, `auth.invalid`, or `auth.expired`, the
`WWW-Authenticate: Bearer` header, and localized English/Japanese text. When no authenticator is
supplied the gate denies access, so a future handler cannot accidentally open the API. The
Authorization header and token values are never logged. An automated test enumerates every method
and path in `specification/api/openapi.yaml`, asserts 401 for each non-health route, and separately
asserts that the AsyncAPI document declares bearer security for `/api/v1/ws`.

`auth.mode: token` is now the default in the generated configuration and example. `cloudflare` and
`local-onetime` remain reserved for 0.4.0 and refuse startup. Bukkit startup creates the token
store, authenticator, and provisioner from the validated host configuration and maps store failures
to localized refusal messages. `/deploymc token create [label]` is console-only, rejects an unknown
action, and prints the token once together with the temporary admin-equivalent warning. The
format is normative in [token records](../../specification/security/token-records.md); the README
states that every token is admin-equivalent until 0.4.0.

The following commands were run on 2026-11-19 through the ASCII junction. All passed:

```text
.\gradlew.bat :platform:bukkit:test --console=plain
  BUILD SUCCESSFUL; 51 Bukkit tests, 0 failures, 0 errors
.\gradlew.bat spotlessApply --console=plain
  BUILD SUCCESSFUL
.\gradlew.bat build verifyJava8 --console=plain
  BUILD SUCCESSFUL; Java 8 bytecode check passed
  JUnit XML aggregate: 159 tests, 0 failures, 0 errors
  Plugin JAR verification passed: DeployMC-0.1.0-SNAPSHOT.jar, 4297968 bytes,
    2510 classes (0 versioned), max common bytecode 52
npm test --prefix tools/spec
  14 tests, 14 passed, 0 failed
npm run lint --prefix tools/spec
  openapi, asyncapi, config, and state-machine validation passed
node tools/i18n/check
  0 error(s), 0 warning(s)
node --test tools/i18n/test/check.test.cjs
  7 tests, 7 passed, 0 failed
node tools/i18n/check --write-status
  0 error(s), 0 warning(s)
node tools/build/verify-all.cjs
  PASS web, java, cli, python, spec, i18n
git diff --check
  pass
```

Module test counts were core 64, transport 32, api 8, Bukkit 51, and standalone 4. The 16 added
tests comprise 11 token store/authenticator/provisioner cases, 4 route-enumeration and gate cases,
and 1 token-command case. Compatibility impact is limited to the embedded transport and Bukkit
startup: no NMS or server internals are used, no new API route is exposed, no dependency or
relocation rule changed, and the generated descriptor is unchanged. Security impact is a net
reduction in reachable surface because the transport now fails closed without a token; the residual
risk is that every token is admin-equivalent until RBAC arrives in 0.4.0. No real Minecraft server
load, in-game command execution, browser rendering, conflicting-library classloader check, or
text-component compatibility check was performed; no support tier is claimed.

PR #149 merged at `3244680` on 2026-11-20T07:17:28Z; issue #49 closed at 2026-11-20T07:17:29Z.
All 18 pull-request checks passed (16 required). The first Windows Java check failed only on the
immediate Netty thread-termination assertion; `18eb9f7` bounded that wait, and the rerun passed.
Post-merge `main` runs passed: CI
[44693881031](https://github.com/woxlo1/DeployMC/actions/runs/44693881031), CodeQL
[44693880985](https://github.com/woxlo1/DeployMC/actions/runs/44693880985), and Scorecard
[44693881125](https://github.com/woxlo1/DeployMC/actions/runs/44693881125). These runs cover build
and unit checks, not a real server.

WP-0.2 added three source-linked research documents:
[naming check](../research/naming-check.md), [competitive analysis](../research/competitive-analysis.md),
and [license comparison](../research/license-comparison.md). They record desk research with
access date 2026-11-18. They do not claim product originality, hands-on competitor testing, or
legal review. The license decision itself was already accepted in [ADR-0013](../adr/ADR-0013-license.md).

## Designed but not implemented

The following are documented as intended behavior. They are **not** code.

| Area | Where the design lives | What exists |
|---|---|---|
| Component boundaries | [Architecture overview](../architecture/overview.md) | Documents only |
| Platform SPI contract | [Platform SPI](../architecture/platform-spi.md) | Interfaces, capability catalog, fake tests, and a Bukkit adapter with server-free tests; no real-server validation |
| Deployment state machine | [Deployment state machine](../architecture/deployment-state-machine.md), [ADR-0008](../adr/ADR-0008-deployment-state-machine.md) | Narrative only |
| Threading model | [Threading model](../architecture/threading-model.md) | Documents only |
| API layering | [API layering](../architecture/api-layering.md) | Documents only |
| Threat model | [Threat model](../../specification/security/threat-model.md) | Draft |
| REST API | [OpenAPI skeleton](../../specification/api/openapi.yaml), [error codes](../../specification/api/errors.md) | Auth gate, health, file/validation, snapshot, doctor, and opt-in metrics handlers implemented; deployment, console, and audit resource handlers are not |
| WebSocket API | [AsyncAPI skeleton](../../specification/api/asyncapi.yaml) | Upgrade authentication and frame limits implemented; the AsyncAPI event protocol and application handlers are not |
| RBAC and audit | [Threat model](../../specification/security/threat-model.md), [audit format](../../specification/audit/audit-log-format.md) | Drafts; every current token is admin-equivalent and no audit record is written |
| Configuration | [JSON Schema](../../specification/config/config.schema.json), [example](../../specification/config/example.config.yaml), [loader guide](../configuration.md) | Schema, example, tested `core` loader, Bukkit startup wiring, fail-closed observability adapter, and `/deploymc` commands |
| Audit log | [Audit log format](../../specification/audit/audit-log-format.md) | Format definition; no log is written |
| Deployment state machine | [Formal state machine](../../specification/deployment/state-machine.md) | Normative transition data and checks; no engine |
| Support matrix | [Support matrix](../compatibility/support-matrix.md) | Tier matrix empty; v1 smoke harness built, including conflict and thread-dump checks |
| ADRs 0001-0014 | [ADR index](../adr/README.md) | Decision records |

## Not started

- Remaining authenticated REST and WebSocket application endpoints bound to the OpenAPI and
  AsyncAPI skeletons. The gate, `GET /api/v1/server`, file/validation, snapshot, doctor, and opt-in
  metrics routes are implemented; deployment, console, and audit routes are not.
- Cloudflare Access and `local-onetime` authentication modes, RBAC, and the audit log. The minimal
  `token` mode is implemented and refuses startup for the other two modes.
- Running-server use of `FileOperationsService`. The core service consumes `SandboxPath`, and the
  REST adapter calls it, but no running server or UI uses those routes yet.
- Application-level one-click restore and running-server use of `core.snapshot`. The store,
  retention, verify/repair, GC, reversible restore, and the snapshot REST routes are implemented, but
  no UI or command consumes them.
- Running-server use of the REST validation endpoint and `ValidationGate`; the registry, gate, and
  HTTP route exist, but no running server uses them.
- Deployment validation, staging, state persistence, restart orchestration, verification, and
  rollback.
- Console capture, ring buffer, filtering, and command dispatch through an adapter.
- Real-server execution of in-game `/deploymc` commands; the WP-1.8 smoke uses the HTTP API and a fixture disable command, not the product command surface.
- Native review of the Japanese command and authentication catalogs.
- Contract tests that check a running implementation against the API skeletons.
- Web UI screens (file tree, editor, diff, deployment view, console, audit); the PART 36 doctor
  panel exists and is build-verified only.
- CLI commands beyond the skeleton; Python SDK client; TypeScript and Java SDKs.
- GitHub Action, examples, documentation site.
- Tier 1/2/3 entries and the full integration suite; the v1 smoke harness exists but intentionally earns no tier.
- Platform adapters for Folia, Velocity, BungeeCord, or standalone. The Bukkit adapter is
  server-validated only by the WP-1.8 smoke path; its command surface and remaining behavior are not.
- Additional bundled third-party dependencies beyond Netty and SnakeYAML; each addition needs a new
  relocation rule and verifier evidence. The real-server conflicting-library check is covered by the
  v1 smoke entry, but a numeric artifact size budget remains open; only the size report is implemented.

## Module inventory

| Path | State |
|---|---|
| `.github/` | Issue forms, PR template, CODEOWNERS, Dependabot config, labels TSV, seven workflows including scheduled sandbox fuzzing |
| `api/` | `DeployMCService`, `ServerInfo`, `PlatformInfo`, `CapabilityState` value objects |
| `core/` | Version and capability value objects, capability catalog, SPI interfaces and value types, validated configuration loader and migration manager, host-provisioned token authentication, canonical sandbox resolver with localized rejection catalogs, sandboxed file operations service with revisions, atomic commits, and a host-wired validation gate, bounded content validators, and a byte-preserving YAML scalar editor; fake adapter in test sources |
| `transport/` | `HttpBindConfig`, internal transport interfaces, limits/config, policy, logging, Netty HTTP/WebSocket server with 11 integration tests, classpath-only static asset handler with 12 tests, authentication/health gate with 4 tests, and localized 401 responses |
| `platform/bukkit/` | Compile-only Bukkit adapter, generated descriptor, catalog-backed startup, token-aware startup and console token creation, and server-free tests |
| `platform/standalone/` | Build placeholder with configuration value object |
| `plugin/` | Shadow aggregator with mandatory relocations, final-JAR verifier, CI size report, descriptor, adapter classes, message catalog, relocated Netty/SnakeYAML, and `deploymc-web/` assets |
| `buildSrc/` | Java implementations of `PluginJarVerifierTask`, `PluginJarSizeTask`, and `PluginJarFixturesTask` for configuration-cache compatibility and reproducible fixtures |
| `docs/research/` | Naming, competitive, license, Bukkit descriptor, transport spike, and SnakeYAML 2.7 evidence; no hands-on server tests |
| `web/` | Buildable React skeleton with gzip/Brotli precompression; no product screens |
| `cli/` | Buildable Rust workspace; no HTTP client |
| `sdk/python/` | Buildable package skeleton; no API client |
| `sdk/typescript/`, `sdk/java/` | Not created |
| `tools/build/` | One-command local verification for all seven toolchains; Windows path workarounds |
| `tools/i18n/` | Working checker and tests |
| `tools/spec/` | Working specification linter and checker tests |
| `specification/` | REST, WebSocket, error, config, audit, deployment, and content-validation contracts; threat model draft |
| `tests/` | Standalone ADR-0003 transport spike build, v1 compatibility harness with 15 offline tests, conflicting-library and wrong-platform fixture plugins, deterministic sandbox corpus generator with 2,736 reject and 29 allow payloads plus a versioned per-OS manifest, deterministic JWT vector generator with a localhost mock JWKS server, manifest-pinned hostile archive, YAML, and log corpus generators, PART 35 coverage map, and source-built versioned healthy plugin emitting 1.0.0/1.1.0 JARs |

## How to verify this page

From the repository root, run `node tools/build/verify-all.cjs` to check every toolchain at once, or
the individual commands in [AGENTS.md](../../AGENTS.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md).
On Windows the script handles the ASCII path and Cargo target workarounds described in
[environment.md](environment.md). If a command on this page fails, this page is wrong and must be
corrected in the same change that fixes or breaks it.

For PART 36, the final evidence branch ran `node tools/build/verify-all.cjs` with `PASS` for `web`,
`java`, `cli`, `python`, `spec`, `i18n`, and `compat`; current Gradle JUnit XML reports 473 tests,
0 failures, 0 errors, and 1 skipped symlink test. `git diff --check` produced no output and exited 0.
PRs #203-#207 each have passing post-merge CI, CodeQL, and Scorecard runs on `main`.

## Related documents

- [Roadmap](../../ROADMAP.md)
- [Project status](project-status.md)
- [Handoff](HANDOFF.md)
- [Research index](../research/README.md)
- [CI and enforced checks](ci.md)
- [Community configuration](community.md)
- [Pending decisions](decisions-pending.md)
- [Master development specification](DeployMC_Complete_Master_Development_Specification.md)
