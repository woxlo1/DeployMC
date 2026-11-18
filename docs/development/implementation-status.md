# Implementation Status

Last updated: 2026-11-18 (Phase 0 WP-0.6/WP-0.2 and Phase 1 WP-1.1/WP-1.2/WP-1.3).

This page states what exists in the repository and what does not. It is deliberately blunt:
no feature is listed as working unless it was built and exercised. The [roadmap](../../ROADMAP.md)
describes intended phase order; the [master specification](DeployMC_Complete_Master_Development_Specification.md)
is the normative design.

## Summary

DeployMC is **pre-alpha**. The repository contains documentation, specifications, architecture
records, buildable skeletons for each language, a source-complete Bukkit-family adapter, and an
embedded Netty HTTP/WebSocket transport with server-free integration tests.
**No DeployMC feature has been observed running inside a Minecraft server.** The adapter has only
server-free tests, and the transport has no authenticated routes or application handlers beyond
the internal injection points. There is no sandbox, snapshot store, deployment engine,
authentication, authorization, audit log, or Web UI screen.

## Verified working today

These were actually run, not just written. Commands are the ones documented in
[AGENTS.md](../../AGENTS.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md).

| Area | What is verified | How |
|---|---|---|
| Java build | Multi-project Gradle build, JDK 21 toolchain, `--release 8`, class-file version check | `.\gradlew.bat build verifyJava8` |
| Java unit tests | `SemanticVersion`, `PlatformCapabilities`, capability catalog, and fake SPI contract tests | Gradle `test` tasks |
| Core value objects | `SemanticVersion`, `PlatformCapabilities`, `CapabilityKeys`, `CapabilityDefinition`, `CapabilityCatalog`, SPI value types | Compile and unit tests |
| SPI interfaces | `PlatformAdapter`, `PlatformScheduler`, `ConsoleBridge`, `ServerController`, `PluginInspector`, `MessageSink`, and value types | Compile and fake-platform tests |
| Bukkit adapter | Generated `plugin.yml`, `JavaPlugin` entry point, SPI bridge, version detection, startup banner, capability map, and task cleanup | `.\gradlew.bat :platform:bukkit:build`; 28 Bukkit tests, 0 failures. No server loaded |
| Distribution JAR | Shadow JAR contains the generated descriptor, entry point, message catalog, and relocated Netty; no Spigot or unrelocated Netty classes | ZIP entry inspection of `plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar` |
| Embedded transport | Netty 4.1.141.Final HTTP/WebSocket lifecycle, limits, Host/Origin hooks, request IDs, redacted logging, security headers, graceful shutdown, and bounded WebSocket queues | `NettyTransportServerTest`: 9 tests, 0 failures |
| Web skeleton | React 18 + TypeScript 5.7 + Vite 6 build, lint, format, Vitest | `pnpm lint`, `pnpm format:check`, `pnpm test`, `pnpm build` |
| CLI skeleton | Rust 2021 workspace with `clap`; format, clippy, tests, build | `cargo fmt --check`, `cargo clippy`, `cargo test`, `cargo build` |
| Python SDK skeleton | `pyproject.toml`, ruff, format, mypy strict, pytest | `ruff check`, `ruff format --check`, `mypy`, `pytest` |
| i18n checker | ICU subset parser, placeholder/plural validation, catalog drift status | `node --test tools/i18n/test/check.test.cjs`, `tools/i18n/check` |
| Specification lint | OpenAPI and AsyncAPI validation, config example against JSON Schema, deployment transition graph checks | `npm ci && npm run lint && npm test` in `tools/spec/` |
| One-command local verification | All six toolchains above run from a single command; each reports `PASS`/`FAIL` | `node tools/build/verify-all.cjs` — verified on this Windows host: all six reported `PASS` |
| CI | Six workflows (CI, lint, CodeQL, dependency review, Scorecard, Labels), including the `Spec` job | GitHub Actions runs on `main` |
| Research documents | Naming check, competitive analysis, license comparison, Bukkit descriptor baseline, and measured transport spike with source links and access dates | `docs/research/*.md` |

The most recent verified full local run was `node tools/build/verify-all.cjs` on branch
`feature/wp-1.3-transport`, which reported `PASS` for `java`, `web`, `cli`, `python`, `spec`,
and `i18n`, including the new transport tests. After PR #135 was squash-merged at `dbb32ae`, the
post-merge `main` CI run [44281229147](https://github.com/woxlo1/DeployMC/actions/runs/44281229147)
passed every job, CodeQL
[44281228983](https://github.com/woxlo1/DeployMC/actions/runs/44281228983) passed all three
languages, and Scorecard [44281229021](https://github.com/woxlo1/DeployMC/actions/runs/44281229021)
passed. These runs cover build and unit checks, not a real server. The one-command tool is
documented in [`tools/build/README.md`](../../tools/build/README.md); CI remains the merge authority.

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
| REST API | [OpenAPI skeleton](../../specification/api/openapi.yaml), [error codes](../../specification/api/errors.md) | Skeleton; transport carries HTTP requests but registers no authenticated routes |
| WebSocket API | [AsyncAPI skeleton](../../specification/api/asyncapi.yaml) | Skeleton; transport carries frames but registers no AsyncAPI application handlers |
| Configuration | [JSON Schema](../../specification/config/config.schema.json), [example](../../specification/config/example.config.yaml) | Schema and example; no loader implements them |
| Audit log | [Audit log format](../../specification/audit/audit-log-format.md) | Format definition; no log is written |
| Deployment state machine | [Formal state machine](../../specification/deployment/state-machine.md) | Normative transition data and checks; no engine |
| Support matrix | [Support matrix](../compatibility/support-matrix.md) | Empty; harness not built |
| ADRs 0001-0014 | [ADR index](../adr/README.md) | Decision records |

## Not started

- Authenticated REST and WebSocket endpoints bound to the OpenAPI and AsyncAPI skeletons; the
  transport machinery exists, but no product routes or application handlers are registered.
- Authentication (`cloudflare`, `token`, `local-onetime`), RBAC, and the audit log.
- Sandbox path resolver, protected paths, and file read/write API.
- Snapshot store, diff, restore, retention, and garbage collection.
- Deployment validation, staging, state persistence, restart orchestration, verification, and
  rollback.
- Console capture, ring buffer, filtering, and command dispatch through an adapter.
- Plugin configuration schema, validation, migration, and reload.
- Contract tests that check a running implementation against the API skeletons.
- Web UI screens (file tree, editor, diff, deployment view, console, audit).
- CLI commands beyond the skeleton; Python SDK client; TypeScript and Java SDKs.
- GitHub Action, examples, documentation site.
- Real-server compatibility harness and any Tier 1/2/3 entry.
- Platform adapters for Folia, Velocity, BungeeCord, or standalone. The Bukkit adapter source exists but is not server-validated.
- Additional bundled third-party dependencies beyond Netty; the automated duplicate-class and
  unrelocated-package CI check from ADR-0004 is still open.

## Module inventory

| Path | State |
|---|---|
| `.github/` | Issue forms, PR template, CODEOWNERS, Dependabot config, labels TSV, six workflows |
| `api/` | `DeployMCService`, `ServerInfo`, `PlatformInfo`, `CapabilityState` value objects |
| `core/` | Version and capability value objects, capability catalog, SPI interfaces and value types; fake adapter in test sources |
| `transport/` | `HttpBindConfig`, internal transport interfaces, limits/config, policy, logging, and Netty HTTP/WebSocket server with 9 integration tests |
| `platform/bukkit/` | Compile-only Bukkit adapter, generated descriptor, catalog-backed startup, and server-free tests |
| `platform/standalone/` | Build placeholder with configuration value object |
| `plugin/` | Shadow aggregator; produced JAR contains descriptor, adapter classes, message catalog, and relocated Netty |
| `docs/research/` | Naming, competitive, license, Bukkit descriptor, and transport spike research; no hands-on server tests |
| `web/` | Buildable React skeleton; no product screens |
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
