# Implementation Status

Last updated: 2026-11-20 (Phase 0 WP-0.6/WP-0.2 and Phase 1 WP-1.1-WP-1.7 and WP-1.9 merged;
issues #47 and #49 closed).

This page states what exists in the repository and what does not. It is deliberately blunt:
no feature is listed as working unless it was built and exercised. The [roadmap](../../ROADMAP.md)
describes intended phase order; the [master specification](DeployMC_Complete_Master_Development_Specification.md)
is the normative design.

## Summary

DeployMC is **pre-alpha**. The repository contains documentation, specifications, architecture
records, buildable skeletons for each language, a source-complete Bukkit-family adapter, an
embedded Netty HTTP/WebSocket transport with server-free integration tests, a classpath-only
static asset handler for the built Web UI, a Java configuration loader with schema validation,
migrations, safe reload, and fail-closed property tests, a server-side plugin message i18n
runtime, an automated final-JAR packaging verifier, and host-provisioned bearer tokens enforced by
a fail-closed HTTP/WebSocket gate.
**No DeployMC feature has been observed running inside a Minecraft server.** The adapter has only
server-free tests. The transport rejects every route except `/healthz` and `/readyz` without a valid
token, but it still has no application resource handlers. There is no sandbox, snapshot store,
deployment engine, authorization, audit log, or Web UI screen; the packaged Web UI has not been
rendered in a browser. The WP-1.5 command surface, WP-1.6 message runtime, and WP-1.9 token command
have only server-free tests; none has been executed on a real Minecraft server.

## Verified working today

These were actually run, not just written. Commands are the ones documented in
[AGENTS.md](../../AGENTS.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md).

| Area | What is verified | How |
|---|---|---|
| Java build | Multi-project Gradle build, JDK 21 toolchain, `--release 8`, class-file version check | `.\gradlew.bat build verifyJava8` |
| Java unit tests | 159 tests across all modules, including fake SPI, Bukkit, standalone, transport, static asset, configuration, command, i18n runtime, token store, authenticator, provisioner, and auth gate cases | Gradle `test` tasks; JUnit XML: 159 tests, 0 failures, 0 errors |
| Core value objects | `SemanticVersion`, `PlatformCapabilities`, `CapabilityKeys`, `CapabilityDefinition`, `CapabilityCatalog`, SPI value types | Compile and unit tests |
| SPI interfaces | `PlatformAdapter`, `PlatformScheduler`, `ConsoleBridge`, `ServerController`, `PluginInspector`, `MessageSink`, and value types | Compile and fake-platform tests |
| Bukkit adapter | Generated `plugin.yml` with `/deploymc` and operator-only permissions, `JavaPlugin` entry point, config-driven startup, SPI bridge, version detection, startup banner, capability map, task cleanup, localized command handler, tab completion, per-sender i18n, and console-only token creation | `.\gradlew.bat :platform:bukkit:test`; 51 Bukkit tests, 0 failures. No server loaded |
| Distribution JAR | Shadow JAR contains the generated descriptor, entry point, message catalogs, 230 relocated SnakeYAML classes, relocated Netty, configuration schema and default template, and `deploymc-web/` assets with precompressed variants; no Spigot, unrelocated Netty/YAML, or multi-release entries | ZIP entry inspection of `plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar` |
| Plugin JAR verification | `:plugin:verifyPluginJar` fails on out-of-namespace classes, malformed or manifest-less multi-release entries, missing versioned bases, duplicate entries/classes, service entries outside the namespace, or common-path bytecode above major 52; CI reports the size | Production JAR plus 12 positive/negative fixture JARs |
| Embedded transport | Netty 4.1.141.Final HTTP/WebSocket lifecycle, limits, Host/Origin hooks, request IDs, redacted logging, security headers, graceful shutdown, bounded WebSocket queues, static asset delivery, and a fail-closed token gate with health exceptions | `NettyTransportServerTest`: 11 tests; `StaticAssetHandlerTest`: 12 tests; `AuthenticationGateTest`: 4 tests; 0 failures |
| Web build | React 18 + TypeScript 5.7 + Vite 6 build, lint, format, Vitest, and gzip/Brotli precompression | `pnpm lint`, `pnpm format:check`, `pnpm test` (14 tests), `pnpm build` |
| CLI skeleton | Rust 2021 workspace with `clap`; format, clippy, tests, build | `cargo fmt --check`, `cargo clippy`, `cargo test`, `cargo build` |
| Python SDK skeleton | `pyproject.toml`, ruff, format, mypy strict, pytest | `ruff check`, `ruff format --check`, `mypy`, `pytest` |
| i18n checker | ICU subset parser, placeholder/plural validation, catalog drift status | `node --test tools/i18n/test/check.test.cjs`, `tools/i18n/check` |
| Specification lint | OpenAPI and AsyncAPI validation, config example against JSON Schema, deployment transition graph checks | `npm ci && npm run lint && npm test` in `tools/spec/` |
| One-command local verification | All six toolchains above run from a single command; each reports `PASS`/`FAIL` | `node tools/build/verify-all.cjs` — verified on this Windows host: all six reported `PASS` |
| CI | Six workflows (CI, lint, CodeQL, dependency review, Scorecard, Labels), including the `Spec` job and plugin JAR size report | GitHub Actions runs on `main` |
| Research documents | Naming check, competitive analysis, license comparison, Bukkit descriptor baseline, measured transport spike, and SnakeYAML selection with source links and access dates | `docs/research/*.md` |
| Configuration loader | Safe YAML parsing, schema validation, timestamped migration backup, atomic writes, immutable `ConfigView`, safe reload, localized diagnostics, and 250 deterministic fail-closed mutations | 53 `core` tests, full build, Java 8 check, and JAR inspection |
| WP-1.5 commands | `/deploymc status|reload|doctor|token`, permission nodes, config-driven startup, console-only token stub, limited doctor, and English/Japanese command catalogs | 42 server-free Bukkit tests, full build, Java 8 check, generated descriptor and JAR inspection |
| WP-1.6 i18n runtime | Per-key sender/server/English fallback, named placeholders, diagnostics, reflective sender locale detection, and legacy color formatting | 50 server-free Bukkit tests, full build, Java 8 check, and `node tools/i18n/check` |
| WP-1.9 authentication foundation | Host-provisioned SHA-256 token records, constant-time verification, expiry, fail-closed HTTP/WebSocket gate, health exceptions, localized 401 Problem Details, and one-time token creation | 11 core auth tests, 4 route-enumeration and gate tests, 51 Bukkit tests, full build, Java 8 check, and `node tools/build/verify-all.cjs` |

The most recent all-toolchain run was `node tools/build/verify-all.cjs` on the merged
`feat/wp-1.9-token-authentication` branch, which reported `PASS` for `web`, `java`, `cli`, `python`,
`spec`, and `i18n`. Web runs before Java in that script and in the CI Java job because the plugin
build packages `web/dist` into the JAR. WP-1.4 was merged in PR #137 at `4548b4b`; the post-merge `main`
CI run [44285715524](https://github.com/woxlo1/DeployMC/actions/runs/44285715524) passed every job,
CodeQL [44285715587](https://github.com/woxlo1/DeployMC/actions/runs/44285715587) passed all three
languages, and Scorecard [44285715509](https://github.com/woxlo1/DeployMC/actions/runs/44285715509)
passed. These runs cover build and unit checks, not a real server. The one-command tool is documented
in [`tools/build/README.md`](../../tools/build/README.md); CI remains the merge authority.

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
| REST API | [OpenAPI skeleton](../../specification/api/openapi.yaml), [error codes](../../specification/api/errors.md) | Auth gate, health endpoints, and auth error codes implemented; application resource handlers are not |
| WebSocket API | [AsyncAPI skeleton](../../specification/api/asyncapi.yaml) | Upgrade authentication and frame limits implemented; the AsyncAPI event protocol and application handlers are not |
| RBAC and audit | [Threat model](../../specification/security/threat-model.md), [audit format](../../specification/audit/audit-log-format.md) | Drafts; every current token is admin-equivalent and no audit record is written |
| Configuration | [JSON Schema](../../specification/config/config.schema.json), [example](../../specification/config/example.config.yaml), [loader guide](../configuration.md) | Schema, example, tested `core` loader, Bukkit startup wiring, and `/deploymc` commands |
| Audit log | [Audit log format](../../specification/audit/audit-log-format.md) | Format definition; no log is written |
| Deployment state machine | [Formal state machine](../../specification/deployment/state-machine.md) | Normative transition data and checks; no engine |
| Support matrix | [Support matrix](../compatibility/support-matrix.md) | Empty; harness not built |
| ADRs 0001-0014 | [ADR index](../adr/README.md) | Decision records |

## Not started

- Authenticated REST and WebSocket application endpoints bound to the OpenAPI and AsyncAPI
  skeletons; the authentication gate exists, but no product routes or application handlers are
  registered.
- Cloudflare Access and `local-onetime` authentication modes, RBAC, and the audit log. The minimal
  `token` mode is implemented and refuses startup for the other two modes.
- Sandbox path resolver, protected paths, and file read/write API.
- Snapshot store, diff, restore, retention, and garbage collection.
- Deployment validation, staging, state persistence, restart orchestration, verification, and
  rollback.
- Console capture, ring buffer, filtering, and command dispatch through an adapter.
- Real-server load, in-game command execution, clean-disable thread dumps, and conflicting-library classloader checks for #48.
- Native review of the Japanese command and authentication catalogs.
- Contract tests that check a running implementation against the API skeletons.
- Web UI screens (file tree, editor, diff, deployment view, console, audit).
- CLI commands beyond the skeleton; Python SDK client; TypeScript and Java SDKs.
- GitHub Action, examples, documentation site.
- Real-server compatibility harness and any Tier 1/2/3 entry.
- Platform adapters for Folia, Velocity, BungeeCord, or standalone. The Bukkit adapter source exists but is not server-validated.
- Additional bundled third-party dependencies beyond Netty and SnakeYAML; each addition needs a new
  relocation rule and verifier evidence. The real-server conflicting-library classloader check and a
  numeric artifact size budget remain open; only the size report is implemented.

## Module inventory

| Path | State |
|---|---|
| `.github/` | Issue forms, PR template, CODEOWNERS, Dependabot config, labels TSV, six workflows |
| `api/` | `DeployMCService`, `ServerInfo`, `PlatformInfo`, `CapabilityState` value objects |
| `core/` | Version and capability value objects, capability catalog, SPI interfaces and value types, validated configuration loader and migration manager, host-provisioned token authentication; fake adapter in test sources |
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
| `tools/build/` | One-command local verification for all six toolchains; Windows path workarounds |
| `tools/i18n/` | Working checker and tests |
| `tools/spec/` | Working specification linter and checker tests |
| `specification/` | REST, WebSocket, error, config, audit, and deployment skeletons; threat model draft |
| `tests/` | Standalone ADR-0003 transport spike build; no compatibility harness |

## How to verify this page

From the repository root, run `node tools/build/verify-all.cjs` to check every toolchain at once, or
the individual commands in [AGENTS.md](../../AGENTS.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md).
On Windows the script handles the ASCII path and Cargo target workarounds described in
[environment.md](environment.md). If a command on this page fails, this page is wrong and must be
corrected in the same change that fixes or breaks it.

## Related documents

- [Roadmap](../../ROADMAP.md)
- [Project status](project-status.md)
- [Handoff](HANDOFF.md)
- [Research index](../research/README.md)
- [CI and enforced checks](ci.md)
- [Community configuration](community.md)
- [Pending decisions](decisions-pending.md)
- [Master development specification](DeployMC_Complete_Master_Development_Specification.md)
