# Implementation Status

Last updated: 2026-11-18 (Phase 0, work packages WP-0.6 and WP-0.2).

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
| Specification lint | OpenAPI and AsyncAPI validation, config example against JSON Schema, deployment transition graph checks | `npm ci && npm run lint && npm test` in `tools/spec/` |
| One-command local verification | All six toolchains above run from a single command; each reports `PASS`/`FAIL` | `node tools/build/verify-all.cjs` — verified on this Windows host: all six reported `PASS` |
| CI | Six workflows (CI, lint, CodeQL, dependency review, Scorecard, Labels), including the `Spec` job | GitHub Actions runs on `main` |
| Research documents | Naming check, competitive analysis, and license comparison with source links and access dates | `docs/research/*.md` |

The most recent verified full local run was `node tools/build/verify-all.cjs` on branch
`build/verify-all`, which reported `PASS` for `java`, `web`, `cli`, `python`, `spec`, and `i18n`.
The most recent verified full CI run on `main` was
[44251355610](https://github.com/woxlo1/DeployMC/actions/runs/44251355610) at `3ddf583`. Both cover
the skeletons and checks, not product features. The one-command tool is documented in
[`tools/build/README.md`](../../tools/build/README.md); CI remains the merge authority.

## Repository configuration, not a product feature

WP-0.8 added six issue forms with a security contact redirect, a pull request template, a CODEOWNERS
file, Dependabot update configuration, a label source-of-truth TSV, and a label sync workflow. These
are GitHub configuration files; they do not make DeployMC run. They are described in
[community configuration](community.md). The label sync workflow is configured to run on `main`
after this work is merged; its first run is not evidence in this status page.

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
| Platform SPI contract | [Platform SPI](../architecture/platform-spi.md) | Interfaces; no adapter |
| Deployment state machine | [Deployment state machine](../architecture/deployment-state-machine.md), [ADR-0008](../adr/ADR-0008-deployment-state-machine.md) | Narrative only |
| Threading model | [Threading model](../architecture/threading-model.md) | Documents only |
| API layering | [API layering](../architecture/api-layering.md) | Documents only |
| Threat model | [Threat model](../../specification/security/threat-model.md) | Draft |
| REST API | [OpenAPI skeleton](../../specification/api/openapi.yaml), [error codes](../../specification/api/errors.md) | Skeleton; no server implements it |
| WebSocket API | [AsyncAPI skeleton](../../specification/api/asyncapi.yaml) | Skeleton; no server implements it |
| Configuration | [JSON Schema](../../specification/config/config.schema.json), [example](../../specification/config/example.config.yaml) | Schema and example; no loader implements them |
| Audit log | [Audit log format](../../specification/audit/audit-log-format.md) | Format definition; no log is written |
| Deployment state machine | [Formal state machine](../../specification/deployment/state-machine.md) | Normative transition data and checks; no engine |
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
- Contract tests that check a running implementation against the API skeletons.
- Web UI screens (file tree, editor, diff, deployment view, console, audit).
- CLI commands beyond the skeleton; Python SDK client; TypeScript and Java SDKs.
- GitHub Action, examples, documentation site.
- Real-server compatibility harness and any Tier 1/2/3 entry.
- Platform adapters for Bukkit family, Folia, Velocity, BungeeCord, or standalone.
- Bundled and relocated third-party dependencies (Shadow is configured but nothing is bundled).

## Module inventory

| Path | State |
|---|---|
| `.github/` | Issue forms, PR template, CODEOWNERS, Dependabot config, labels TSV, six workflows |
| `api/` | `DeployMCService`, `ServerInfo`, `PlatformInfo`, `CapabilityState` value objects |
| `core/` | Version and capability value objects; SPI interfaces and value types |
| `transport/` | `HttpBindConfig` value object only; no server |
| `platform/bukkit/`, `platform/standalone/` | Build placeholders |
| `plugin/` | Shadow aggregator; produces an empty scaffold JAR |
| `docs/research/` | Naming, competitive, and license research documents; no hands-on tests |
| `web/` | Buildable React skeleton; no product screens |
| `cli/` | Buildable Rust workspace; no HTTP client |
| `sdk/python/` | Buildable package skeleton; no API client |
| `sdk/typescript/`, `sdk/java/` | Not created |
| `tools/build/` | One-command local verification for all six toolchains; Windows path workarounds |
| `tools/i18n/` | Working checker and tests |
| `tools/spec/` | Working specification linter and checker tests |
| `specification/` | REST, WebSocket, error, config, audit, and deployment skeletons; threat model draft |
| `tests/` | Not created |

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
