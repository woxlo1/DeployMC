# Implementation Status

Last updated: 2026-11-17 (Phase 0, work package WP-0.4).

This page states what exists in the repository and what does not. It is deliberately blunt:
no feature is listed as working unless it was built and exercised. The [roadmap](../../ROADMAP.md)
describes intended phase order; the [master specification](DeployMC_Complete_Master_Development_Specification.md)
is the normative design.

## Summary

DeployMC is **pre-alpha**. The repository contains documentation, specifications, architecture
records, and buildable skeletons for each language. **No DeployMC feature runs inside a
Minecraft server.** There is no transport server, sandbox, snapshot store, deployment engine,
authentication, authorization, audit log, or Web UI screen.

## Verified working today

These were actually run, not just written. Commands are the ones documented in
[AGENTS.md](../../AGENTS.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md).

| Area | What is verified | How |
|---|---|---|
| Java build | Multi-project Gradle build, JDK 21 toolchain, `--release 8`, class-file version check | `.\gradlew.bat build verifyJava8` |
| Java unit tests | `SemanticVersion` parsing/comparison and `PlatformCapabilities` value semantics | Gradle `test` tasks |
| Core value objects | `SemanticVersion`, `PlatformCapabilities`, `CapabilityKeys`, SPI value types | Compile and unit tests |
| SPI interfaces | `PlatformAdapter`, `PlatformScheduler`, `ConsoleBridge`, `ServerController`, `PluginInspector`, `MessageSink`, and value types | Compile; no adapter implementation |
| Web skeleton | React 18 + TypeScript 5.7 + Vite 6 build, lint, format, Vitest | `pnpm lint`, `pnpm format:check`, `pnpm test`, `pnpm build` |
| CLI skeleton | Rust 2021 workspace with `clap`; format, clippy, tests, build | `cargo fmt --check`, `cargo clippy`, `cargo test`, `cargo build` |
| Python SDK skeleton | `pyproject.toml`, ruff, format, mypy strict, pytest | `ruff check`, `ruff format --check`, `mypy`, `pytest` |
| i18n checker | ICU subset parser, placeholder/plural validation, catalog drift status | `node --test tools/i18n/test/check.test.cjs`, `tools/i18n/check` |
| CI | Five workflows (CI, lint, CodeQL, dependency review, Scorecard) | GitHub Actions runs on `main` |

The most recent verified full run before this update was CI run
[44040380569](https://github.com/woxlo1/DeployMC/actions/runs/44040380569) on `main` at
`f8d77ac`. That run covers the skeletons, not product features.

## Designed but not implemented

The following are documented as intended behavior. They are **not** code.

| Area | Where the design lives | What exists |
|---|---|---|
| Component boundaries | [Architecture overview](../architecture/overview.md) | Documents only |
| Platform SPI contract | [Platform SPI](../architecture/platform-spi.md) | Interfaces; no adapter |
| Deployment state machine | [Deployment state machine](../architecture/deployment-state-machine.md), [ADR-0008](../adr/ADR-0008-deployment-state-machine.md) | Narrative only |
| Threading model | [Threading model](../architecture/threading-model.md) | Documents only |
| API layering | [API layering](../architecture/api-layering.md) | Documents only |
| Threat model | [Threat model](../../specification/security/threat-model.md) | Draft |
| Support matrix | [Support matrix](../compatibility/support-matrix.md) | Empty; harness not built |
| ADRs 0001-0014 | [ADR index](../adr/README.md) | Decision records |

## Not started

- HTTP/WebSocket transport (the server choice is a `Proposed` ADR; no dependency is added).
- Authentication (`cloudflare`, `token`, `local-onetime`), RBAC, and the audit log.
- Sandbox path resolver, protected paths, and file read/write API.
- Snapshot store, diff, restore, retention, and garbage collection.
- Deployment validation, staging, state persistence, restart orchestration, verification, and
  rollback.
- Console capture, ring buffer, filtering, and command dispatch through an adapter.
- Plugin configuration schema, validation, migration, and reload.
- REST/OpenAPI and WebSocket/AsyncAPI specifications, error catalog, and contract tests.
- Web UI screens (file tree, editor, diff, deployment view, console, audit).
- CLI commands beyond the skeleton; Python SDK client; TypeScript and Java SDKs.
- GitHub Action, examples, documentation site.
- Real-server compatibility harness and any Tier 1/2/3 entry.
- Platform adapters for Bukkit family, Folia, Velocity, BungeeCord, or standalone.
- Bundled and relocated third-party dependencies (Shadow is configured but nothing is bundled).

## Module inventory

| Path | State |
|---|---|
| `api/` | `DeployMCService`, `ServerInfo`, `PlatformInfo`, `CapabilityState` value objects |
| `core/` | Version and capability value objects; SPI interfaces and value types |
| `transport/` | `HttpBindConfig` value object only; no server |
| `platform/bukkit/`, `platform/standalone/` | Build placeholders |
| `plugin/` | Shadow aggregator; produces an empty scaffold JAR |
| `web/` | Buildable React skeleton; no product screens |
| `cli/` | Buildable Rust workspace; no HTTP client |
| `sdk/python/` | Buildable package skeleton; no API client |
| `sdk/typescript/`, `sdk/java/` | Not created |
| `tools/i18n/` | Working checker and tests |
| `specification/` | Threat model draft only |
| `tests/` | Not created |

## How to verify this page

From the repository root, run the commands in [AGENTS.md](../../AGENTS.md). On Windows, run
Gradle through the documented ASCII path because the checkout path contains non-ASCII
characters. If a command on this page fails, this page is wrong and must be corrected in the
same change that fixes or breaks it.

## Related documents

- [Roadmap](../../ROADMAP.md)
- [CI and enforced checks](ci.md)
- [Pending decisions](decisions-pending.md)
- [Master development specification](DeployMC_Complete_Master_Development_Specification.md)
