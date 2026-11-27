# DeployMC — Complete Master Development Specification

## Role and Responsibility

You are the principal architect, lead software engineer, platform-compatibility engineer, security engineer, API designer, SDK developer, frontend engineer, localization manager, technical writer, QA engineer, release engineer, and long-term maintainer of DeployMC.

You are responsible for building DeployMC as a serious, production-quality, international open-source project.

You must understand the entire project vision before implementing individual features.

You must not optimize only for the next milestone. Every implementation decision must consider the long-term architecture and the intended final product.

This project is a **long-term development effort**. It is expected to take many working sessions, many Pull Requests, many releases, and a large codebase (on the order of tens of thousands of lines across several languages). The size of the codebase is an outcome of quality work, never a target to be inflated.

The initial repository is only the beginning.

Do not stop after creating the repository structure, README, or an initial prototype.

Do not consider an early milestone equivalent to project completion.

Follow the entire development roadmap until the project reaches its defined v1.0.0 release criteria, and then continue with the post-1.0.0 roadmap.

---

## How to Read This Specification

- Read it **completely** at the start of the project, then use the index below as a working reference in later sessions (the repository's `AGENTS.md` carries the condensed rules; this file lives in `docs/development/`).
- If two parts seem to conflict, apply Part 31 (precedence of goals and documents). Report genuine contradictions through the change process in Part 39 instead of choosing silently.
- Index: Parts 1-4 vision, scope, compatibility, stack | 5-6 architecture and repository | 7-9 features, API, security | 10-11 documentation and localization | 12-13 Git/GitHub and achievements | 14-15 QA and release | 16-18 roadmap and acceptance | 19-22 behavior, long-term protocol, kickoff, communication | 23 work packages | 24 technical reference | 25 test catalog | 26 community | 27 risks | 28 operator prompts | 29 release checklist | 30 owner questions | 31 normative rules and glossary | 32 hypotheses and limits | 33 algorithms | 34 templates | 35 fixtures | 36 diagnostics | 37 ops guides | 38 UX and i18n style | 39 change control | 40 sequencing | 41 quality gates.

---

# PART 1 — PROJECT IDENTITY AND VISION

## 1.1 Project Name

DeployMC

Repository name: `deploymc`

Before adopting the name publicly, verify there is no conflicting project name on GitHub, Modrinth, Hangar, SpigotMC, and Maven Central coordinates. Record the result in `docs/research/naming-check.md`. If a serious conflict exists, stop and report it to the project owner in Japanese instead of silently renaming.

## 1.2 Project Category

Minecraft Server Tooling / Developer Experience / Remote Deployment / Secure Collaboration Infrastructure.

## 1.3 Project Description

DeployMC is an open-source infrastructure that lets a team collaboratively edit, deploy, snapshot, roll back, and monitor the plugin directory of a running Minecraft server from a web browser or an API, **without ever handing out SSH, SFTP, or panel credentials**.

It is a single drop-in artifact for server administrators and a documented, versioned API for developers and CI systems.

## 1.4 The Problem

When several people develop a Minecraft server (for example an RPG server) together, the typical workflow is:

1. A developer builds a `.jar` or edits a `.yml` file locally.
2. The developer sends it to the person who controls the server host.
3. The host manually places it into `plugins/`, restarts the server, and reports the result.
4. If something breaks, someone manually restores the previous file.

This is slow, error-prone, and forces a trust decision: either the host becomes a bottleneck, or the host gives out SSH/SFTP access that exposes the entire machine.

## 1.5 The Solution

DeployMC provides:

- A **sandboxed** file explorer and editor restricted to the server's plugin area.
- **Safe JAR deployment** with staging, validation, controlled restart, and automatic rollback.
- **Automatic snapshots** before every destructive change, with diff and one-click restore.
- **Real-time console and log filtering** over WebSocket.
- **Identity, RBAC, and audit logging** built around Cloudflare Tunnel + Cloudflare Access (Zero Trust), with fallback authentication for local use.
- A **stable public API** (REST + WebSocket + webhooks + Java API), plus SDKs, a CLI, and a GitHub Action so deployments can also be automated from CI.
- **Broad version and platform compatibility**, so the same tool works across many Minecraft versions and server software.
- **Multi-language** documentation, README, plugin messages, and Web UI.

## 1.6 Non-Goals (Must Be Documented in the README)

- DeployMC is **not** a general-purpose remote shell, SFTP server, or hosting panel.
- DeployMC does **not** manage the host operating system, worlds, or the server's core files by default.
- DeployMC does **not** replace a proper backup system for worlds and player data.
- DeployMC does **not** claim to make a hostile team member harmless. Deploying a JAR is code execution on the server. The security model must say this plainly (see Part 9).

---

# PART 2 — PRODUCT DEFINITION

## 2.1 What DeployMC Must Become

### A. DeployMC Core (platform-independent)

Responsibilities:

- Sandboxed file system service
- Snapshot / history service
- Deployment pipeline and state machine
- Authentication and RBAC engine
- Audit log service
- Configuration management and migration
- Event bus
- Public API service layer (transport-agnostic)

### B. Platform Adapters

Thin adapters that connect Core to each server platform (Bukkit family, Folia, proxies, standalone mode, and later Sponge/Fabric/NeoForge). See Part 3.

### C. Embedded Web Server and Web UI

A lightweight embedded HTTP/WebSocket server serving the REST API, the WebSocket API, and the built Web UI bundled inside the JAR. Binds to `127.0.0.1` by default.

### D. Public API

REST API v1, WebSocket protocol, webhooks, and a Java API module for other plugins. Specified formally (OpenAPI 3.1 and AsyncAPI). See Part 8.

### E. SDKs, CLI, and CI Integration

- TypeScript SDK
- Python SDK
- Java client
- CLI (`deploymc`) as a single static binary
- GitHub Action for CI deployment

### F. Documentation and Localization

A complete documentation system and README in multiple languages. See Parts 10 and 11.

### G. Release and Distribution

Published artifacts on GitHub Releases, Maven Central (API/client), Modrinth, Hangar, SpigotMC (where permitted), and container/package registries for the CLI. See Part 15.

## 2.2 Intended User Roles

- **Server administrator**: installs the JAR, configures Cloudflare Tunnel/Access and RBAC.
- **Developer**: edits configs and deploys JARs from the browser, API, or CI.
- **Reviewer / approver** (later): approves risky deployments.
- **Observer**: read-only access to files and console.
- **Automation**: CI or scripts using scoped API tokens or Cloudflare Access service tokens.

---

# PART 3 — COMPATIBILITY STRATEGY (MULTI-VERSION, MULTI-PLATFORM)

The project owner wants DeployMC to support as many Minecraft versions and server platforms as is realistically possible.

"Support everything" is not a testable claim. You must therefore turn this goal into an explicit, honest, automatically verified **Support Matrix**.

## 3.1 Support Tiers

Define and document three tiers in `docs/compatibility/support-matrix.md` and in the README:

- **Tier 1 — Tested in CI on every PR/nightly.** A real server of that version is started in CI, DeployMC is loaded, and an automated integration test exercises the API (file read/write, snapshot, deploy, console stream, shutdown).
- **Tier 2 — Tested on a schedule (nightly/weekly) or on release candidates.**
- **Tier 3 — Best effort / community-reported.** Not tested automatically. Documented known limitations.

Never put a version in Tier 1 or Tier 2 unless the matrix job actually runs. Never claim support for a version that has not been run at least once.

## 3.2 Target Platforms (Initial Intent)

Work toward the following, in this order of priority. Adjust through ADRs when evidence justifies it.

| Priority | Platform | Notes |
|---|---|---|
| 1 | Paper (1.21.x, Java 21) | Primary target. First fully working platform. |
| 1 | Paper forks (Purpur, Pufferfish, Leaf, etc.) | Expected to work through the Paper/Bukkit adapter. Verify, do not assume. |
| 1 | Folia | Requires region-aware scheduling. Separate scheduler abstraction. Must declare Folia support correctly. |
| 2 | Spigot / CraftBukkit | Bukkit API subset only. Document missing capabilities. |
| 2 | Older Paper/Spigot versions (down to the oldest feasible, target 1.8.8) | Java 8 baseline, reflection-based feature detection. Define the actual lowest supported version from evidence. |
| 2 | Velocity | Proxy adapter. Manages the proxy's own plugin directory. |
| 2 | BungeeCord / Waterfall | Proxy adapter. |
| 3 | Standalone mode | Runs without a Minecraft platform: a sidecar/daemon or Java agent that manages a configured directory. Useful for unsupported platforms and for development. |
| 3 | Sponge, Fabric, Forge, NeoForge | Research via ADR. `mods/` management and mod loaders have different safety properties. Do not promise these before research is complete. |

## 3.3 Java Version Policy

Different Minecraft versions require different Java versions (verify exact requirements for each version when writing the matrix; do not rely on memory):

- Legacy versions run on Java 8.
- Mid-era versions require Java 16 or 17.
- Recent versions (1.20.5 and later) require Java 21.

Therefore:

- The shared code that must load on old servers must target **Java 8 bytecode** (compile with a modern JDK using `--release 8`, using Gradle toolchains).
- Do not use language features or library classes newer than the target in code that loads on legacy platforms.
- Optional newer-Java optimizations (for example virtual threads on Java 21) may be provided through Multi-Release JAR entries or runtime feature detection, never as a hard requirement for the common path.
- Every third-party dependency that ends up in the plugin JAR must itself support the baseline Java version. Record the check in the dependency ADR.

## 3.4 API Compatibility Technique

- Compile platform adapters against the **oldest** supported server API, and use runtime feature detection (reflection / `Class.forName` / capability flags) for anything newer. Cache lookups.
- Do not depend on server internals (NMS / CraftBukkit internals / remapped classes). If an unavoidable exception exists, isolate it in a version-specific module with an ADR and tests.
- Define a `PlatformCapabilities` object that reports what the current runtime can do (log capture, graceful restart, plugin enumeration, region scheduler, command dispatch, update folder support, etc.). The API and UI must degrade gracefully and tell the user exactly which capability is missing and why.
- Provide a `deploymc doctor` command / UI page that prints detected platform, Minecraft version, Java version, capabilities, and bind/auth configuration.
- Handle the plugin descriptor correctly across versions (`plugin.yml`, `api-version`, `folia-supported`, and, only if justified, `paper-plugin.yml`). Verify each against official documentation at the time of implementation.

## 3.5 Version-Specific Behaviors That Must Be Explicitly Designed

- Console/log capture: prefer a Log4j 2 appender where available; provide a fallback for platforms without it (for example `java.util.logging` handlers). Never break server logging when attaching or detaching.
- Command dispatch: console command execution must run on the correct thread for the platform (main thread vs global region scheduler on Folia).
- Restart: restart semantics differ by platform and hosting setup. Support explicit **restart modes**: `shutdown-only` (a supervisor such as systemd/Docker/Pterodactyl restarts the process), `spigot-restart-script`, and `none`. Never pretend a restart can be guaranteed when there is no supervisor.
- Plugin enumeration and enable-failure detection: use stable APIs and detect failures through log/events where API support differs.
- JAR replacement: see Part 9 (Windows file locking). Prefer the server's **update folder** mechanism and/or staged replacement at restart. Verify the update-folder semantics per platform.
- Hot reload of arbitrary plugins is **unsafe** and must not be the default. If offered at all, it is experimental, opt-in, and documented with its risks.

## 3.6 Compatibility Test Infrastructure

Build a reusable compatibility harness under `tests/compat-matrix/`:

- Download official server software for each matrix entry using official, verifiable download APIs, with checksum verification where the provider supplies it.
- Start the server headless with `eula=true` (document that CI accepts the EULA only in the test sandbox), a minimal world configuration, and the freshly built DeployMC JAR.
- Run the shared integration test suite against the running server (through the public API).
- Collect logs as CI artifacts.
- Run matrix entries in parallel with caching.
- Produce a generated `docs/compatibility/support-matrix.md` section from the results (or a badge/JSON), so the documented matrix cannot drift from reality.

---

# PART 4 — TECHNOLOGY STACK

Use multiple programming languages for meaningful technical reasons. Do not force everything into one language, and do not add a language without a reason recorded in an ADR.

## 4.1 Plugin Core and Platform Adapters: Java

- Baseline Java 8 bytecode (see 3.3), built with a Java 21 toolchain.
- Gradle (Kotlin DSL) multi-project build with version catalogs.
- ShadowJar for shading and **mandatory relocation** of every bundled dependency under a unique package prefix to avoid classloader conflicts with other plugins and with the server.
- Keep dependencies minimal. Prefer the JDK standard library where it is sufficient.

### Embedded HTTP / WebSocket server

This is a high-risk decision. Write an ADR comparing at least:

- JDK built-in `com.sun.net.httpserver` (zero dependency, Java 8) plus a small, well-tested WebSocket layer or a compact WebSocket library with Java 7/8 support
- Jetty (older versions with Java 8 support, and current versions)
- Netty
- Undertow
- Javalin

Evaluate: minimum Java version, JAR size, relocation behavior, classloader conflicts on Paper/Spigot/Folia, thread model, TLS needs (TLS is normally terminated at Cloudflare; local TLS is optional), WebSocket maturity, security track record, and maintenance health. If no single option covers Java 8 through Java 21 cleanly, an ADR may justify publishing a separate "legacy" artifact. Prefer one JAR if it can be done without sacrificing correctness.

### Other Java decisions requiring ADRs

- YAML parsing and **comment-preserving** round-trip editing for configs
- JSON library
- JWT/JWK verification (prefer a small audited implementation or a mature library; never hand-roll cryptography primitives; signature verification must use the JDK or a mature library)
- Diff algorithm library (or own implementation with tests)
- Hashing and content-addressed storage

## 4.2 Web UI: TypeScript

- React + Vite + TypeScript (strict mode) + Tailwind CSS
- Monaco Editor (lazy-loaded; evaluate CodeMirror 6 as a lighter alternative and decide through an ADR based on bundle size, mobile support, and diff viewer quality)
- Client-side i18n (see Part 11)
- Accessible (keyboard navigation, ARIA, color-contrast), responsive, dark/light themes
- Built output is embedded in the JAR resources. The built bundle must have a **size budget** enforced in CI, because it ships inside the plugin JAR. Consider compression and lazy chunks.
- State and data fetching through the generated TypeScript client from the OpenAPI spec.

## 4.3 CLI: Rust (default choice, ADR required)

- A single static binary for Linux, macOS, and Windows.
- Uses the public REST/WebSocket API only (it is also the reference consumer of the API).
- Supports Cloudflare Access service tokens and API tokens.
- Human-readable and machine-readable (JSON) output, stable exit codes, non-interactive CI mode.
- Go is an acceptable alternative if justified in the ADR.

## 4.4 SDKs

- TypeScript SDK (Node and browser)
- Python SDK (modern Python, documented support policy)
- Java client (Java 8 baseline)
- All SDKs are **generated from, or validated against,** the OpenAPI/AsyncAPI specifications. Hand-written wrappers are allowed on top of generated code. A contract test ensures every SDK matches the spec.

## 4.5 GitHub Action

- A `deploymc/deploy-action` style action (TypeScript or Docker/composite) that uploads a built JAR/config through the API using a scoped token or Cloudflare Access service token, waits for the deployment result, and fails the workflow on rollback.

## 4.6 Documentation Site

- A static documentation site (for example VitePress or Docusaurus; decide through an ADR) with built-in multi-language support, versioned docs, and search.

## 4.7 Build Tooling

- Java: Gradle (Kotlin DSL), Gradle toolchains, dependency locking/verification
- TypeScript: pnpm (or documented alternative), lockfile committed
- Rust: Cargo workspace
- Python: modern packaging (pyproject.toml)
- Pin or constrain important tool versions. Document every required development tool in `CONTRIBUTING.md`.
- Provide a devcontainer / reproducible dev environment and a `make`-style or `just`-style task runner for common tasks.

---

# PART 5 — COMPLETE SYSTEM ARCHITECTURE

## 5.1 High-Level Architecture

```
 Developer browser / CLI / CI (GitHub Action) / 3rd-party client
                    |
                    v
          Cloudflare Access (identity, policies, service tokens)
                    |
                    v
          Cloudflare Tunnel (cloudflared) --> 127.0.0.1:8080
                    |
                    v
 +-------------------------------------------------------------+
 |  DeployMC plugin (single JAR)                                |
 |                                                              |
 |  HTTP/WS transport  -->  AuthN (CF JWT / token / fallback)   |
 |         |                       |                            |
 |         v                       v                            |
 |   Public API layer  -->  RBAC / policy engine  --> Audit log |
 |         |                                                    |
 |         v                                                    |
 |  +--------------+  +-------------+  +--------------------+   |
 |  | Sandbox FS   |  | Snapshot    |  | Deployment pipeline|   |
 |  | service      |  | store       |  | (state machine)    |   |
 |  +--------------+  +-------------+  +--------------------+   |
 |         |                |                    |              |
 |         +----------------+--------------------+              |
 |                          v                                   |
 |                Platform SPI (capabilities)                   |
 |   Bukkit | Folia | Velocity | Bungee | Standalone | ...      |
 +-------------------------------------------------------------+
```

## 5.2 Core Modules

- **sandbox**: path resolution, jail enforcement, protected paths, file operations (the only code allowed to touch the file system on behalf of a user)
- **snapshot**: content-addressed history store, retention, diff, restore
- **deploy**: staging, validation, transaction state machine, restart orchestration, health check, rollback
- **auth**: authentication providers, token store, session handling
- **rbac**: roles, permissions, path scopes, policy evaluation
- **audit**: append-only audit log with integrity chaining
- **console**: log capture, ring buffer, filtering, command execution
- **config**: schema, validation, migration, hot-reload of safe options
- **events**: internal event bus and webhook dispatcher
- **api**: transport-independent service layer used by REST, WebSocket, CLI, and the Java API
- **platform-spi**: interfaces implemented by each platform adapter (Scheduler, ConsoleBridge, LogSource, ServerController, PluginInspector, DataDirectory, VersionInfo, Capabilities)
- **i18n**: message bundles for plugin messages and API error codes

## 5.3 Design Rules

- Core must not import any Minecraft/Bukkit/Velocity class. Only platform adapters may.
- Every user-visible error has a stable machine-readable **error code** and a localizable message.
- Every state-changing operation is auditable, and every destructive operation is snapshot-protected.
- All time-dependent and file-system-dependent logic must be injectable for testing.
- Prefer small, focused files. Avoid files over roughly 500 lines without a reason.
- No `static` mutable global state in Core. Support clean enable/disable/reload of the plugin (and `/reload` hostile environments) without leaking threads, sockets, or file handles.
- Every thread the plugin creates is named, tracked, and terminated on disable.

## 5.4 Threading Model

Never block the server main thread or the Folia region threads with file I/O, network I/O, hashing, or diffing. Use dedicated bounded executors. Return to the platform thread only for API calls that require it. Document the model.

---

# PART 6 — COMPLETE REPOSITORY STRUCTURE

The repository must grow into a clearly organized monorepo. Modify the structure when justified, but preserve clear separation of responsibilities.

```
deploymc/
|-- README.md                     (English, canonical)
|-- LICENSE
|-- CONTRIBUTING.md
|-- CODE_OF_CONDUCT.md
|-- SECURITY.md
|-- SUPPORT.md
|-- CHANGELOG.md
|-- ROADMAP.md
|-- GOVERNANCE.md
|-- AUTHORS.md
|-- AGENTS.md                     (condensed rules for AI coding agents, derived from this spec)
|-- CLAUDE.md                     (optional pointer to AGENTS.md)
|-- .editorconfig  .gitattributes  .gitignore
|-- .github/
|   |-- workflows/                (ci, compat-matrix, codeql, release, docs, i18n-check, scorecard)
|   |-- ISSUE_TEMPLATE/           (bug, feature, compat-report, docs, translation, security-redirect)
|   |-- PULL_REQUEST_TEMPLATE.md
|   |-- DISCUSSION_TEMPLATE/
|   |-- CODEOWNERS  dependabot.yml  labels.yml  FUNDING.yml (optional)
|
|-- core/                         (Java 8, platform-independent)
|-- api/                          (public Java API, semver-governed, published to Maven Central)
|-- transport/                    (embedded HTTP/WS server, request pipeline)
|-- platform/
|   |-- bukkit/                   (Bukkit/Spigot/Paper/forks, oldest-API compile + feature detection)
|   |-- folia/
|   |-- velocity/
|   |-- bungee/
|   |-- standalone/
|   |-- (sponge | fabric | neoforge: after research ADRs)
|-- plugin/                       (shadow-jar aggregator, final artifact, plugin descriptors)
|-- web/                          (React + Vite + TypeScript Web UI)
|-- cli/                          (Rust workspace)
|-- sdk/
|   |-- typescript/  python/  java/
|-- actions/
|   |-- deploy-action/
|-- specification/
|   |-- api/openapi.yaml
|   |-- api/asyncapi.yaml         (WebSocket protocol)
|   |-- api/errors.md             (stable error codes)
|   |-- config/config.schema.json
|   |-- audit/audit-log-format.md
|   |-- security/threat-model.md
|   |-- deployment/state-machine.md
|-- docs/
|   |-- architecture/  adr/  research/  guides/  compatibility/  development/
|   |-- i18n/                     (translated READMEs and docs, translation status)
|   |-- site/                     (documentation site source)
|-- tests/
|   |-- unit-support/  integration/  compat-matrix/  e2e/ (Playwright)  security/  fuzz/  perf/
|-- tools/                        (scripts: release, i18n checks, matrix generation)
|-- examples/                     (cloudflare-tunnel setup, docker-compose, CI workflows)
|-- assets/                       (logo, screenshots, demo GIFs)
```

---

# PART 7 — CORE FEATURE SPECIFICATION

The original requirements remain valid and are made more precise here.

## 7.1 Secure File Explorer and Editor

- Directory tree under the configured **root** (default: the server's `plugins/` directory). The root is configurable only by the server administrator through local config, never through the API.
- Create, rename, move, delete, upload, download. Directory operations must be safe and bounded.
- Editor with syntax highlighting for `.yml`, `.yaml`, `.json`, `.toml`, `.properties`, `.txt`, `.md`, and others.
- **Pre-save validation**: YAML (and a documented YAML subset policy: duplicate keys, tabs, anchors), JSON, TOML, and properties. Validation runs on the server (authoritative) and may run in the browser (convenience). A syntax error blocks the save unless the user explicitly overrides and has the permission to override.
- Optional **schema-aware validation** hooks: plugins or the community can register schemas for specific config files (later phase).
- Optimistic concurrency: every file has a revision/ETag. Saving a stale revision returns a conflict with a diff, never a silent overwrite. Multiple people editing the same file must be handled safely (presence indicator; optional soft lock).
- Binary files: show metadata and allow download, but do not open them in the text editor.
- Large files: size limits and streaming.
- Secret masking (optional per-path rules) so that read-only users do not see credentials in configs.

## 7.2 Drag-and-Drop JAR Deployment and Safe Restart

A deployment is a **transaction** with a persisted state machine, designed in `specification/deployment/state-machine.md`:

```
CREATED -> UPLOADED -> VALIDATED -> STAGED -> (APPROVED)? -> APPLYING
        -> RESTARTING -> VERIFYING -> SUCCEEDED
                              \-> FAILED -> ROLLING_BACK -> ROLLED_BACK | ROLLBACK_FAILED
```

Requirements:

- JAR validation before staging: valid ZIP, no zip-slip entries, plugin descriptor present and parseable, declared name/version/main class, API version vs server, dependency presence, size limit, optional signature/hash allowlist, optional denylist. Static validation can reduce mistakes but **cannot prove a JAR is safe**; document this.
- Safe replacement of an existing JAR (including different file names for the same plugin version, e.g. `Plugin-1.0.jar` replaced by `Plugin-1.1.jar`) with both old and new recorded.
- Windows-safe application (see Part 9.4).
- Restart orchestration per restart mode (see 3.5): countdown messages to players, configurable delay, save-all before shutdown, and a clear report when the platform cannot restart itself.
- **Post-restart verification**: after restart, DeployMC loads, finds the pending transaction, and verifies health: plugin enabled, no enable exceptions attributed to the deployed plugin, optional custom health checks (console patterns, a command that must succeed, an HTTP check), within a timeout.
- **Automatic rollback** when verification fails, restoring the previous JAR/config set from the snapshot and restarting again.
- **Boot-loop protection**: limit rollback attempts, and if DeployMC itself is healthy but the server cannot be stabilized, enter a **safe state** that keeps the Web UI reachable and clearly reports the situation.
- If DeployMC itself is the thing being updated, handle it as a special case with extra safeguards.
- Idempotency: repeated API submissions with the same idempotency key must not create duplicate deployments.
- Every step emits events (WebSocket, webhooks, audit log).

## 7.3 Automatic Snapshots, History, Diff, and Restore

- Before any overwrite, delete, rename-overwrite, or deployment, store the previous content in `plugins/.deploymc/history/` (or the configured data directory; the location must be excluded from the editable sandbox by default).
- Content-addressed, deduplicated storage with metadata (who, when, why, deployment id, original path, hashes).
- Retention policies (count, age, total size) with safe pruning that never deletes a snapshot needed by an in-progress deployment.
- Diff: text diff for text files, semantic diff for YAML/JSON where feasible, and metadata diff for JARs (plugin name, version, descriptor changes, size, hash). Do not attempt binary diffs.
- One-click restore, which itself creates a snapshot first (restore is reversible).
- Integrity verification of stored snapshots (hash check) and a repair/report command.

## 7.4 Real-Time Console and Log Filter

- WebSocket stream of server logs, with a bounded ring buffer for late joiners and backfill.
- Server-side and client-side filtering: log level (INFO/WARN/ERROR/...), logger/plugin name, regex/text search, time range.
- Command submission with permission control, an allow/deny list, per-user rate limiting, and full audit logging. Commands run on the correct platform thread.
- Backpressure: slow clients must never slow the server. Drop or coalesce with an explicit notice.
- ANSI/color handling, stack trace grouping, and safe escaping (output must never be rendered as HTML).
- Console access can be disabled entirely.

## 7.5 Audit Log

- Append-only log of every authenticated action: identity, auth method, source IP (taking Cloudflare headers into account only when trusted), action, target path, result, deployment id, hashes.
- Tamper-evident (hash chain with periodic anchors). Document honestly that tamper-evidence is not tamper-proof against someone with host file access.
- Rotation and retention.
- Queryable through the API (by users with the permission) and exportable.
- Never log secrets or file contents.

---

# PART 8 — PUBLIC API

The API is a first-class product, not an afterthought. The Web UI, the CLI, the SDKs, and the GitHub Action must all be built **on top of the same public API**.

## 8.1 API Principles

- Specification first: `specification/api/openapi.yaml` (OpenAPI 3.1) and `specification/api/asyncapi.yaml` (WebSocket) are the source of truth. Changes to behavior start with the spec.
- Versioned under `/api/v1`. Breaking changes require a new major API version. Document the deprecation policy (minimum overlap period, `Deprecation`/`Sunset` headers).
- Consistent error format with stable error codes (RFC 9457 Problem Details style), localizable messages (`Accept-Language`), and request IDs.
- Pagination, filtering, and sorting conventions. ETags/`If-Match` for concurrency. Idempotency keys for deployments.
- Rate limiting with documented headers.
- Machine-readable capability discovery: `GET /api/v1/server` returns platform, Minecraft version, Java version, DeployMC version, capabilities, and API version.
- Health and readiness endpoints that reveal no sensitive information.

## 8.2 Resource Groups (Initial)

- `server` — info, capabilities, doctor report
- `files` — tree, read, write, create, move, delete, upload, download
- `snapshots` — list, show, diff, restore, prune
- `deployments` — create, upload JAR, validate, approve, status, cancel, rollback, list
- `console` — recent logs, filtered queries, command execution
- `audit` — query, export
- `me` — identity, effective permissions
- `tokens` — create (scoped, expiring), list, revoke
- `webhooks` — register, test, deliveries, redelivery
- `config` — safe, read-mostly view of effective DeployMC configuration (never secrets)

## 8.3 WebSocket Protocol

- Authenticated at upgrade time, with Origin checks.
- Channels: `console`, `deployments`, `files` (change notifications), `presence`.
- Message envelope with type, id, sequence number, and protocol version; heartbeat; resume from sequence number where feasible; documented close codes.

## 8.4 Webhooks

- Events: `deployment.*`, `file.changed`, `snapshot.created`, `snapshot.restored`, `auth.denied`, `server.restarting`, etc.
- HMAC-SHA256 signatures with timestamps (replay protection), retry with backoff, delivery log, SSRF protections for webhook target URLs (block private/loopback targets unless explicitly allowed), per-target secret management.

## 8.5 Authentication for API Clients

- Cloudflare Access service tokens (headers or JWT assertion) for CI.
- DeployMC API tokens: random, high-entropy, shown once, stored hashed, scoped (permissions + path scopes), expiring, revocable, with last-used tracking.
- Scopes map onto the same RBAC permissions as human users (an API token can never exceed its owner's permissions).

## 8.6 Java API Module (`api/`)

- A small, stable, semver-governed Java 8 library, published to Maven Central, registered through the platform's service registry so other plugins can:
  - Register pre-deployment validators and post-deployment hooks
  - Register custom config schemas
  - Query deployment/snapshot state
  - Subscribe to events
- Public API surface is minimal, documented with Javadoc, and tested with API-compatibility checks (for example japicmp) in CI.

## 8.7 Client Artifacts

- Generated and hand-polished TypeScript, Python, and Java clients; a Rust-based CLI; a GitHub Action.
- A shared **conformance test suite** runs against a real DeployMC instance in CI for each client.
- Publish an OpenAPI-based Postman/Bruno/HTTP example collection in `examples/`.

---

# PART 9 — SECURITY AND PRIVACY

Security is mandatory. DeployMC's feature set is, by nature, a remote code deployment mechanism. Treat it accordingly.

## 9.0 Honest Threat Model

Write `specification/security/threat-model.md` before implementing authentication. It must state at least:

- Anyone who can deploy a JAR can execute arbitrary code as the server process. RBAC and approval flows reduce risk but do not remove it.
- Anyone who can edit configs of certain plugins may achieve code execution indirectly (for example via command-executing configs).
- Cloudflare is a trust anchor in `cloudflare` mode. Misconfigured Cloudflare Access policies are the most likely real-world failure.
- A person with host file-system access can read/modify DeployMC's data, including the audit log.

Document assets, actors, trust boundaries, attack paths, mitigations, residual risks, and what DeployMC explicitly does not defend against.

## 9.1 Path Traversal and Sandbox Hardening

Implement the sandbox as a single, heavily tested component. Requirements:

- Canonicalize and resolve **before** access, then re-verify containment after resolution (defeating TOCTOU races where feasible; use `NOFOLLOW_LINKS` semantics and open-by-handle patterns where the platform supports them).
- Reject: `..` segments, absolute paths, drive letters, UNC paths, null bytes, control characters, over-long paths, and invalid Unicode.
- Windows specifics: reserved device names (`CON`, `NUL`, `COM1`, ...), trailing dots/spaces, alternate data streams (`file.txt:stream`), 8.3 short names, case-insensitivity, junctions and reparse points.
- Linux/macOS specifics: symlinks (including symlinks planted inside the root pointing outside), case-insensitive file systems on macOS, Unicode normalization differences (NFC/NFD).
- **Protected paths** (non-editable by default, even for admins through the API): `.deploymc/` data directory, DeployMC's own `config.yml` and token/secret stores, and any configured deny patterns. Writes to DeployMC's own configuration are only possible locally by the host administrator.
- Archive extraction (if ever supported) must defend against zip-slip, zip bombs, and symlink entries.
- Limits: maximum file size, maximum upload size, maximum directory entries per listing, maximum path length, maximum concurrent operations.
- A dedicated security test suite (`tests/security/`) containing a corpus of traversal payloads, run on Linux **and** Windows in CI, plus fuzzing of the path resolver.

## 9.2 Cloudflare Access Integration

Defaults and rules:

- Bind to `127.0.0.1` by default. Binding to other interfaces requires an explicit setting and emits a prominent warning at startup.
- In `cloudflare` auth mode, **never trust the plain `Cf-Access-Authenticated-User-Email` header by itself**. It can be forged by anything that can reach the port directly. Always verify the signed JWT in the `Cf-Access-Jwt-Assertion` header.
- JWT verification requirements:
  - Fetch signing keys (JWKS) from the team's certs endpoint (`https://<team-domain>/cdn-cgi/access/certs`), with caching, rotation handling, and a resilient refresh strategy (do not hammer the endpoint; handle outages without failing open).
  - Verify signature (accept only the expected algorithm, reject `none`/algorithm confusion), `iss` (matches the configured team domain), `aud` (matches the configured Application AUD tag), `exp`, `nbf`, `iat` with limited clock skew.
  - Extract identity (`email`, `sub`, and service-token `common_name`) from the **verified** token only.
  - Verify behavior with a test suite using generated keys, expired tokens, wrong audience, wrong issuer, tampered payloads, and key rotation.
- Fail **closed**: if keys cannot be fetched and no cached valid key exists, deny.
- Treat `CF-Connecting-IP` as informational and only when the request came through the trusted local tunnel path. Document the spoofing implications.
- Validate `Host`/`Origin` to prevent DNS rebinding and cross-site WebSocket hijacking. CSRF protection for state-changing requests when cookie-based sessions are in use.
- Optionally require a shared secret header injected by a Cloudflare Transform Rule as an additional defense in depth.
- Provide an `examples/cloudflare-tunnel/` guide: `cloudflared` config, Access application and policy setup, service tokens for CI, and a verification checklist. Re-verify every Cloudflare product detail against current official documentation before writing it.

## 9.3 Authentication Modes

- `cloudflare`: production mode as above.
- `token`: DeployMC-native API tokens (for CI/standalone use).
- `local-onetime`: ephemeral one-time token printed to the console at startup, intended for local testing only, bound to loopback, with a clear warning, short lifetime, and rate limiting. Refuses to run if bound to a non-loopback address unless explicitly overridden.
- `disabled` is **not allowed** as a mode. There is no unauthenticated mode.
- Session security if sessions are used: `HttpOnly`, `Secure` (when applicable), `SameSite`, rotation, short TTL.
- Brute-force protection and constant-time comparison for secrets.

## 9.4 Windows File Locking and Safe JAR Replacement

A running server holds an open handle on its plugin JARs. On Windows, those files cannot be overwritten or deleted while loaded.

Design and verify:

- Never attempt to overwrite a loaded JAR in place on Windows.
- Stage new files in a staging directory, then apply the replacement at a safe moment: through the server's update-folder mechanism where supported, and/or during the restart sequence by a mechanism that runs when the old JAR is no longer loaded. Verify each platform's behavior experimentally and record results in `docs/compatibility/jar-replacement.md`.
- When replacement cannot be performed safely, report the exact reason and the manual remedy. Do not corrupt or half-write a JAR.
- Atomic writes everywhere: write to a temporary file in the same directory, `fsync`, then atomic move; handle platforms where atomic move is unavailable.
- Handle antivirus/indexer transient locks with bounded retries.
- On Linux, replacing a loaded JAR can still crash a running classloader that lazily loads classes. Treat in-place replacement of loaded JARs as unsafe everywhere unless the server is stopping.
- CI must include Windows jobs for the replacement and sandbox logic.

## 9.5 RBAC and Policy

Configuration lives in the host-controlled `config.yml`. It must be fully validated at startup with precise error messages, and invalid configuration must fail safe (deny, not allow). The normative WP-3.4 contract, including the closed permission registry, token owner mapping, reason codes, and explain-mode redaction, is `specification/security/rbac.md`. An absent `paths.allow` means the whole sandbox minus protected paths; an explicitly empty `paths.allow` grants no path scope. Tokens evaluate through their owner, never as admin-equivalent by default.

Illustrative shape (finalize through the config schema):

```yaml
auth:
  mode: cloudflare            # cloudflare | token | local-onetime
  cloudflare:
    team-domain: "example.cloudflareaccess.com"
    audience: "<application-aud-tag>"
bind:
  address: "127.0.0.1"
  port: 8080
roles:
  admin:
    permissions: ["*"]
  developer:
    permissions:
      - files.read
      - files.write
      - snapshots.read
      - snapshots.restore
      - deployments.create
      - console.read
    paths:
      allow: ["MyRpgPlugin/**", "MyRpgPlugin.jar"]
      deny:  ["**/secrets.yml"]
    limits: { max-upload-mb: 20 }
  observer:
    permissions: [files.read, console.read]
users:
  "alice@example.com":        { roles: [admin] }
  "bob@example.com":         { roles: [developer] }
  "ci-service-token-name":   { roles: [ci-deployer] }
default-role: none           # unknown identities are denied
restart:
  allowed-roles: [admin]
  mode: shutdown-only
```

- Deny by default. Deny rules win over allow rules. Path scopes use the same sandbox resolver. Role changes take effect safely on reload.
- Optional later: group claims from Cloudflare Access identity, approval workflows (two-person rule for JAR deployments), time windows, and per-path command policies.

## 9.6 Data Privacy and Secrets

- No telemetry by default. If anonymous usage statistics are ever added (for example bStats), they must be strictly opt-in, documented, and disable-able.
- Never include file contents, tokens, or identities in error reports or webhook payloads unless explicitly configured.
- Secrets in config (webhook secrets, token hashes) must not be readable through the file API.
- Log redaction for tokens and Authorization headers.

## 9.7 Supply Chain and Release Security

- Dependabot or Renovate, dependency review on PRs, CodeQL, secret scanning, OpenSSF Scorecard.
- Reproducible builds where feasible, SBOM (CycloneDX or SPDX), signed releases (Sigstore/cosign or GPG), build provenance attestations, checksum files.
- Verified Gradle dependencies (checksum/signature verification) and a lockfile policy.
- `SECURITY.md` with a private reporting path (GitHub private vulnerability reporting), supported versions table, and response targets.

## 9.8 Security Test Requirements

Tests must cover at minimum: path traversal corpus, symlink/junction escapes, Windows-specific names, JWT forgery and misconfiguration cases, header spoofing, RBAC bypass attempts, oversized uploads, malformed JARs and zip-slip, YAML bombs (alias expansion), WebSocket origin attacks, token leakage in logs, webhook SSRF, concurrency races on save/restore, and resource exhaustion.

---

# PART 10 — DOCUMENTATION SYSTEM

Documentation must be maintained throughout the entire project. The README is not the entire documentation system.

## 10.1 README.md (English, Canonical)

The README is the face of the project and must be built carefully and kept truthful. It must contain:

1. Logo / visual identity
2. Project name and tagline
3. **Language switcher** line linking to every available translation (see Part 11)
4. Badges: CI status, latest release, license, supported Minecraft versions (generated from the real matrix), Java versions, downloads (Modrinth/Hangar), docs, Discord/Discussions link, OpenSSF Scorecard
5. Short description (what it is, in two or three sentences)
6. Project status and maturity (alpha/beta/stable) and an honest **Implemented vs Planned** table
7. Key features (with GIFs/screenshots of the real UI)
8. Why DeployMC exists, and the problem it solves (the "handing around JAR files" story)
9. How it works, with an architecture diagram (Mermaid or SVG)
10. Security model summary, **including the explicit warning that JAR deployment equals code execution**
11. **Support Matrix**: Minecraft versions, platforms, Java versions, with tier labels
12. Installation: step-by-step for a server administrator (drop-in JAR, first start, first login)
13. Quick start: local test mode with one-time token
14. Production setup: Cloudflare Tunnel + Cloudflare Access walk-through (or a link to a full guide)
15. Configuration overview and RBAC example
16. Web UI tour
17. API quick start: `curl` examples, SDK examples (TypeScript, Python, Java), CLI examples
18. CI/CD example: GitHub Action workflow that deploys a plugin JAR after a build
19. Console and logging features
20. Snapshots, diff, and rollback explanation
21. Windows notes (file locking, restart)
22. Troubleshooting and `deploymc doctor`
23. Performance and resource-usage notes (measured, not guessed)
24. Compatibility and versioning policy
25. Roadmap summary
26. Contributing, translating, and community links
27. Security reporting
28. License
29. Acknowledgements and related projects

Rules:

- The README must always describe the **actual current implementation**. Never advertise a planned feature as existing.
- Every code/command example in the README must be tested (a CI job should execute or at least type-check examples where feasible).
- Keep the README readable: move deep content to `docs/`.

## 10.2 Other Required Documents

- `CONTRIBUTING.md`: development setup, required tools, repository structure, build/test commands per language, formatting, linting, coding standards, commit conventions, branch conventions, PR requirements, review procedure, documentation requirements, translation workflow, issue reporting, security reporting.
- `ARCHITECTURE` documentation under `docs/architecture/`: component responsibilities, dependency rules, deployment state machine, threading model, platform SPI, event flow, API layering, Web UI architecture, extension points.
- **ADRs** under `docs/adr/` for every significant decision. Each ADR has Context, Problem, Considered Alternatives, Decision, Consequences, Status. Minimum initial set:
  - ADR-0001: Monorepo structure
  - ADR-0002: Java 8 bytecode baseline with modern toolchain
  - ADR-0003: Embedded HTTP/WebSocket server selection
  - ADR-0004: Shading and relocation strategy
  - ADR-0005: Authentication modes and Cloudflare JWT verification
  - ADR-0006: Sandbox design and protected paths
  - ADR-0007: Snapshot storage format
  - ADR-0008: Deployment state machine and rollback semantics
  - ADR-0009: Public API versioning and spec-first workflow
  - ADR-0010: CLI language selection
  - ADR-0011: Web UI framework and editor selection
  - ADR-0012: Localization architecture
  - ADR-0013: License selection
  - ADR-0014: Platform support tiers and compatibility harness
- `CHANGELOG.md`: Keep a Changelog format (Added / Changed / Fixed / Removed / Security / Deprecated), user-facing, generated or verified from Conventional Commits.
- API reference: generated from OpenAPI/AsyncAPI and Javadoc, published on the documentation site, with hand-written guides and cookbook recipes.
- Guides: administrator guide, developer guide, CI guide, Cloudflare guide, Windows guide, Docker/Pterodactyl guide, migration guides between versions, FAQ, troubleshooting.
- Research: `docs/research/competitive-analysis.md` covering existing tools (e.g. SFTP/web panels such as Pterodactyl, Crafty, AMP, MCSManager; file manager plugins; PlugMan-style plugin managers; update/deploy tools; CI deploy approaches; Cloudflare-based access patterns). Never claim originality without evidence. Record purpose, overlap, limits, and DeployMC's intended differentiation.

## 10.3 Documentation Quality Rules

- Every documented command and config key must exist and be tested.
- Docs are versioned together with releases.
- A docs CI job checks links, spelling (English), code-example validity, and the i18n status files.
- Screenshots and GIFs are regenerated from the real UI by an automated script where possible (Playwright), so they do not rot.

---

# PART 11 — LOCALIZATION AND INTERNATIONALIZATION

The project owner wants multi-language support from the beginning. Build the **infrastructure first** (Phase 0/1), so every later feature is localizable by construction.

## 11.1 Language Policy

- **English is canonical.** All source code, comments, commit messages, PR titles/descriptions, issue templates, test names, and internal docs are in English.
- Translations are **additions**, never replacements.
- Initial target languages (in priority order, adjustable): Japanese (ja), Simplified Chinese (zh-CN), Traditional Chinese (zh-TW), Korean (ko), Spanish (es), Portuguese-Brazil (pt-BR), French (fr), German (de), Russian (ru), plus infrastructure ready for any additional language, including right-to-left languages (ar, he) and plural-rich languages.
- The owner communicates with you in Japanese; Japanese is the first translation to maintain actively.

## 11.2 What Is Localized

1. **README and key docs**: `README.md` (English) plus `docs/i18n/README.<lang>.md` (for example `README.ja.md`), each starting with a translation-status header (source commit/hash, last reviewed date, reviewer, completeness).
2. **Documentation site**: multi-language routing, language switcher, fallback to English for untranslated pages with a visible notice.
3. **Web UI**: message catalogs (for example `web/src/locales/<lang>.json`) using ICU MessageFormat (plurals, gender, dates, numbers, relative time). Language auto-detection plus manual switch persisted per user. No string concatenation for sentences. Layout must tolerate longer translations and RTL.
4. **In-game / console plugin messages**: `lang/<lang>.yml` bundles with a documented fallback chain (user locale -> server default -> English). Support per-player locale where the platform exposes it; otherwise the configured default. Avoid hard dependency on newer text-component libraries on legacy versions; provide a legacy-compatible formatter.
5. **API errors**: stable machine-readable error codes (never localized) plus localizable human-readable messages negotiated through `Accept-Language`.
6. **Issue and PR templates**: English with a short note that other languages are welcome in Discussions.

## 11.3 Translation Workflow and Quality

- Catalog keys are stable, descriptive identifiers. English source strings carry translator comments/context.
- A script `tools/i18n/check` verifies: missing keys, unused keys, placeholder/variable mismatches, plural form completeness, invalid ICU syntax, and **staleness** (translation older than the English source for that key/file).
- CI fails on structural errors (missing placeholders, broken syntax) and **warns** on missing/stale translations. Missing translations fall back to English at runtime, never to a blank string or a raw key.
- A pseudo-localization build (expanded, accented text) is used in UI tests to catch hard-coded strings and layout overflow.
- Translation status table in `docs/i18n/STATUS.md`, generated from the catalogs.
- Provide a `TRANSLATING.md` explaining how a non-developer can contribute (including a Discussions category for translators and a translation issue template).
- Machine translation may be used only to bootstrap a draft, must be **labeled as machine-translated** in the status file, and should be replaced after native review. Never present an unreviewed machine translation as verified.
- Never translate: error codes, config keys, API field names, command names, permission identifiers, file paths.

## 11.4 Localization Tests

- Unit tests for the message resolver and fallback chain.
- UI tests that run under pseudo-locale and an RTL locale.
- Tests that every user-facing string in the Web UI and plugin comes from a catalog (lint rule for hard-coded strings).

---

# PART 12 — GIT AND GITHUB DEVELOPMENT RULES

These rules are mandatory. They are modeled on the Kinetrail project's rules.

## 12.1 Main Branch

Never develop directly on `main`. `main` must contain reviewed, integrated, stable changes. Configure branch protection (required status checks, linear history or squash policy, no force-push, no deletion) as far as the repository settings and the owner's permissions allow, and document anything that cannot be configured through the tools available to you.

## 12.2 Branch Naming

```
feature/<description>
fix/<description>
docs/<description>
spec/<description>
refactor/<description>
test/<description>
build/<description>
ci/<description>
i18n/<description>
perf/<description>
security/<description>
```

## 12.3 Commit Convention

Use **Conventional Commits**:

```
<type>(<scope>): <description>
```

Allowed types: `feat`, `fix`, `docs`, `spec`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `revert`, `i18n`, `security`.

Suggested scopes: `core`, `api`, `sandbox`, `snapshot`, `deploy`, `auth`, `rbac`, `audit`, `console`, `transport`, `platform-bukkit`, `platform-folia`, `platform-velocity`, `platform-bungee`, `standalone`, `web`, `cli`, `sdk-ts`, `sdk-py`, `sdk-java`, `action`, `docs`, `compat`, `i18n`, `release`.

Examples:

```
docs: define project vision
spec(api): define deployments resource
build(core): initialize Gradle multi-project build
feat(sandbox): implement canonical path resolution
feat(auth): verify Cloudflare Access JWT signatures
fix(deploy): handle update-folder collision on Windows
test(sandbox): add traversal payload corpus
i18n(web): add Japanese message catalog
ci(compat): add Paper 1.21 integration job
```

Rules:

- All commit messages are written in **English**.
- Subject line: imperative mood, no trailing period, at most about 72 characters.
- Add a concise body whenever the change is not self-evident, describing what changed and why.
- Breaking changes use `!` and a `BREAKING CHANGE:` footer.
- Reference issues (`Closes #123`) in the body/footer where applicable.
- Enforce the convention with a CI check (for example commitlint) and, where feasible, a local hook.
- Do not combine an entire feature into one enormous commit. Create multiple meaningful commits; each is one understandable unit of work. Do not create meaningless commits to inflate counts.

## 12.4 AI Assistance Attribution

When an AI coding tool genuinely contributes to a commit, include an accurate `Co-authored-by` trailer as the **last non-empty line** of the message, using the real identity of the tool actually used. Examples (use only the one that is true for the tool in use, and only the exact value the tool/vendor documents):

```
Co-authored-by: opencode <221189863+opencode-agent@users.noreply.github.com>
```

```
Co-authored-by: Claude <noreply@anthropic.com>
```

- Verify the exact trailer format in the tool's current documentation before first use. Do not alter the email.
- Do not add trailers for contributors who did not contribute. Do not claim a person or tool contributed when that is untrue.
- Real human collaborators who contribute to a commit may be credited with their own trailers using their GitHub-associated email, with their consent.

## 12.5 Pull Requests

Every meaningful change is developed on a separate working branch and submitted through a Pull Request against `main`.

Each PR must contain:

- English title (Conventional Commits style)
- Summary
- Motivation and linked issue(s)
- Implementation details
- Testing performed (exact commands and results)
- Documentation and translation updates
- Compatibility impact (which tiers/platforms are affected)
- Security considerations when relevant
- Screenshots for UI changes

Keep PRs reasonably small and focused. Do not combine unrelated changes. Do not submit empty PRs. Do not create artificial PRs to manipulate GitHub achievements.

## 12.6 Review and Merge

Review every PR diff before merging. Check for: accidental files, debug code, secrets, incomplete implementations, missing tests, missing docs/translations, breaking changes, bundle-size regressions, and CI results.

Merge only when required checks pass and the PR is ready. Prefer squash or rebase merges consistent with the chosen history policy (decide in an ADR; commit messages must remain meaningful after merge). Do not claim a PR was merged unless the merge actually succeeded and you verified it.

Where a second human reviewer exists, use them. When working solo, the PR still serves as a documented, CI-verified change; do not bypass failing checks.

## 12.7 Issues, Discussions, Labels, Milestones, Projects

- Enable and configure GitHub Issues, **Discussions** (categories: Announcements, Q&A, Ideas, Show and tell, Translations, Compatibility reports), and a GitHub Project board for the roadmap.
- Create a label taxonomy (`type:*`, `area:*`, `platform:*`, `tier:*`, `good first issue`, `help wanted`, `i18n:*`, `security`, `breaking`).
- Create one GitHub **Milestone** per release (0.1.0, 0.2.0, ...) and keep issues attached to them.
- Use Issue Forms (YAML) with required fields: version, platform, Minecraft version, Java version, `deploymc doctor` output.
- Seed `good first issue` and `help wanted` items that are real and well-described (translations, compat reports, docs).
- Use release automation (for example release-please or git-cliff) to derive changelogs and versions from Conventional Commits.
- Enable Dependabot/Renovate, code scanning, secret scanning, private vulnerability reporting, and a `CODEOWNERS` file.

## 12.8 Language Rules for the Repository

Repository content is English by default (see Part 11 for the localized exceptions). When communicating with the repository owner outside the repository (chat), use Japanese.

---

# PART 13 — GITHUB COMMUNITY AND ACHIEVEMENTS

The owner wants to build DeployMC as an active open-source project and **legitimately** earn GitHub achievements. This is a secondary objective. Do not compromise project quality, honesty, or security for it.

## 13.1 Principles

- Achievements follow from real work. Never fabricate activity.
- Before attempting any achievement: check the **current** criteria in official GitHub documentation (they change, and some badges are retired), identify whether another person must participate, explain the limitations to the owner, and never guarantee a badge.
- Keep achievement goals strictly separate from engineering goals.
- Never purchase, trade, or solicit stars; never create sock-puppet accounts; never create meaningless issues/PRs/discussion posts; never merge broken code or bypass required checks to hit a badge.

## 13.2 Legitimate Practices Aligned With Common Badges

Treat the following as *natural outcomes of good practice*, verified against current criteria before the owner relies on them:

- **Pull Shark**: develop through many small, coherent, merged PRs (this is already the required workflow in Part 12).
- **Pair Extraordinaire**: real collaboration with other contributors, using truthful co-author trailers on merged PRs. Do not fake collaborators. Explain to the owner honestly whether AI-tool attribution trailers do or do not count; do not assume.
- **Galaxy Brain**: answer questions helpfully in the project's own Discussions (Q&A) and in relevant public communities. Draft useful answers for the owner to review and post under their own account; never auto-spam.
- **Quickdraw**: occurs naturally when a genuine issue/PR is resolved very quickly. Never open artificial issues just to close them.
- **YOLO**: concerns merging without review. This project's policy is that CI checks are required. Do not weaken protections to chase a badge; if the owner works solo and merges their own PR after CI passes, report that honestly as a fact and nothing more.
- **Starstruck**: stars come only from users finding the project useful. Invest in README quality, demos, documentation, localization, and release quality; submit the project to appropriate community lists/forums honestly.
- Other community badges: evaluate individually; never manipulate.

## 13.3 Community Building Tasks

- Welcoming `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`, and issue templates
- Clear "good first issue" backlog, including translation tasks
- Public roadmap and milestone updates
- Release notes written for humans
- Showcase page with real servers using DeployMC (with permission)
- Announcements on suitable platforms (Modrinth, Hangar, SpigotMC resources, relevant Discords/Reddit communities, Hacker News) following each community's self-promotion rules, only after the release meets quality criteria
- Respond promptly and politely to issues and Discussions

---

# PART 14 — QUALITY ASSURANCE

## 14.1 Test Layers

- **Unit tests** (JUnit 5 / Vitest / pytest / Rust test): all pure logic, with extra emphasis on sandbox, RBAC, JWT, state machine, diff, validation.
- **Property-based and fuzz tests**: path resolver, config parsers, ZIP/JAR validator, WebSocket framing.
- **Integration tests**: embedded server + real file system (including Windows), API behavior against the OpenAPI spec (contract tests).
- **Compatibility matrix tests**: real servers per Part 3.6.
- **End-to-end UI tests**: Playwright against a running test server (login, edit, save with lint error, deploy JAR, rollback, console filter, locale switch, RTL).
- **Cross-language conformance tests**: CLI, SDKs, and the GitHub Action are all validated against the same live instance.
- **Security tests**: Part 9.8.
- **Performance tests**: Part 14.3.
- **Accessibility tests**: automated axe checks plus manual keyboard review.
- **Localization tests**: Part 11.4.

## 14.2 CI Requirements

Required checks on every PR (runtime kept reasonable via caching and path filters):

- Build and test on Linux and Windows (and macOS for CLI)
- Java: compile with `--release 8`, bytecode level check, shaded-JAR content verification (no unrelocated third-party packages), `japicmp` for the `api` module
- TypeScript: typecheck, lint, unit tests, bundle-size budget
- Rust: fmt, clippy (deny warnings), tests
- Python: lint, type check, tests
- OpenAPI/AsyncAPI lint and breaking-change detection
- Generated-client drift check (regenerate and diff)
- Commit message lint, PR title lint
- Docs link check, i18n structural check
- Dependency review, secret scan, CodeQL
- Smoke test on at least one Tier 1 server (Paper latest); the full matrix runs on schedule and on release branches

## 14.3 Performance and Resource Goals

Define measurable budgets (and measure them; do not guess):

- Server main-thread impact: negligible; no blocking I/O on server/region threads
- Idle CPU and memory overhead of DeployMC
- Plugin JAR size and Web UI bundle size budgets
- Latency for file listing/reading/writing on large plugin directories
- Console streaming throughput under log floods, with bounded memory
- Behavior with many concurrent clients
- Startup time added to the server boot

Add benchmark jobs and keep results in `docs/development/performance.md`.

## 14.4 Reliability

- Crash-safe writes and recoverable deployment state after unexpected shutdown at **any** state-machine step. Test by fault injection (kill at each step).
- Clean enable/disable cycles (including repeated `reload` scenarios) without leaked threads/sockets/handles.
- Graceful behavior when disk is full, permissions are wrong, the port is in use, or Cloudflare keys are unreachable.

---

# PART 15 — VERSIONING, RELEASE, AND DISTRIBUTION

## 15.1 Semantic Versioning

Follow SemVer. Pre-1.0 releases (0.x) may change, but breaking changes must still be documented. Version the following independently and document their relationship:

- DeployMC plugin version
- Public REST/WebSocket API version (`/api/v1`)
- Java API module version
- SDK and CLI versions
- Config schema version (with automatic migration and backups)
- Snapshot/data directory format version (with migration)

## 15.2 Stable Release Requirements

A release may be labeled stable only when its functionality is implemented, tested, documented, translated at least for the declared languages, security-reviewed, and compatibility-verified for its declared tiers.

## 15.3 Distribution Channels

- GitHub Releases: plugin JAR, CLI binaries (Linux/macOS/Windows, x64/arm64), checksums, signatures, SBOM, provenance
- Maven Central: `api` module and Java client
- npm: TypeScript SDK
- PyPI: Python SDK
- crates.io and/or package managers (Homebrew, Scoop, winget, container image) for the CLI
- GitHub Marketplace: the GitHub Action
- Modrinth and Hangar (and SpigotMC where terms allow): plugin pages with the localized descriptions
- Documentation site

Follow each platform's rules for listing and licensing. Do not publish anywhere without the owner's confirmation of accounts and credentials; explain exactly what is needed.

## 15.4 Upgrade and Migration

- DeployMC must upgrade in place without losing config, history, or audit data. Config and data migrations are versioned, tested, backed up automatically, and reversible where possible.
- Provide `deploymc` CLI/doctor checks for incompatibilities.

---

# PART 16 — COMPLETE DEVELOPMENT ROADMAP

The following roadmap defines the intended development progression. It is **not** a deadline schedule. Do not skip major milestones. Do not mark a milestone complete without meeting its acceptance criteria. Each phase is broken into many small, reviewable PRs.

Compatibility and localization are **cross-cutting**: they start in Phase 0/1 and are verified in every phase. They are not "last-minute" phases.

## Phase 0 — Project Foundation (target 0.1.0)

Objective: establish identity, architecture, governance, tooling, documentation, localization infrastructure, and the spec skeleton.

Tasks:

- Verify repository, name, and license (ADR-0013); research competitors; write `competitive-analysis.md` and `naming-check.md`.
- Write README (honest, "pre-alpha, nothing implemented yet" where true), CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, SUPPORT, ROADMAP, GOVERNANCE, AUTHORS, AGENTS.md.
- Create the monorepo skeleton: empty-but-buildable Gradle multi-project (Java 8 target), `web/`, `cli/`, `specification/`, `docs/`, `tests/` with real minimal build/test wiring (no fake placeholders).
- Initial ADRs (see 10.2), architecture overview, support-matrix design, threat model draft.
- Draft `openapi.yaml` and `asyncapi.yaml` skeletons for the first resources.
- Localization infrastructure: translation status files, i18n check script, README language switcher, first translation (Japanese README) labeled with its status.
- GitHub setup: issue forms, PR template, labels, milestones, Discussions categories, Dependabot, CODEOWNERS, CI (build, lint, commitlint, docs link check, i18n check).
- Project tracking documents (Part 20).

Acceptance: repository is understandable; architecture, security stance, compatibility strategy, and API direction are documented; CI validates basics; contribution process is reproducible; README does not claim features that do not exist.

## Phase 1 — Core Foundation and Embedded Server (0.2.0)

- Platform SPI and `PlatformCapabilities`.
- Bukkit adapter (Paper first, compiled against the oldest feasible API with feature detection), plugin descriptor, enable/disable lifecycle, thread and resource management.
- Embedded HTTP server (per ADR-0003) bound to `127.0.0.1`, a minimal but real static page served from JAR resources, unauthenticated `/healthz` and `/readyz` (minimal information only), and an **authenticated** `GET /api/v1/server`. No API endpoint other than the health probes is ever reachable without authentication, in any phase (see WP-1.9 for the minimal auth that makes this possible from the start).
- Shadow/relocation pipeline with automated verification.
- Config loading, schema, validation, migration framework; i18n runtime for plugin messages.
- Minimal authentication foundation (token mode with host-provisioned tokens) so that every endpoint is authenticated from the first release.
- Compatibility harness v1: start Paper latest in CI, load the JAR, call `/api/v1/server` with a provisioned test token.
- Early Windows CI job.

## Phase 2 — Sandboxed File System, Snapshots, and File API (0.3.0)

- Sandbox resolver with the full traversal/Windows/symlink hardening and test corpus (Part 9.1).
- File operations service with atomic writes, ETags, limits.
- Snapshot store (content-addressed), retention, restore, diff.
- File and snapshot REST endpoints, spec-first, with contract tests.
- Validators for YAML/JSON/TOML/properties.
- Fuzzing of the resolver; Windows and Linux CI for these modules.

## Phase 3 — Authentication, RBAC, and Audit (0.4.0)

- Cloudflare Access JWT verification with JWKS caching, rotation, and failure-closed behavior.
- `token` and `local-onetime` modes; API token store (hashed, scoped, expiring).
- RBAC engine with path scopes; `me` and `tokens` endpoints.
- Audit log (hash-chained), query endpoint.
- Host/Origin validation, CSRF, rate limiting, brute-force protection.
- Complete the threat model; add the Cloudflare setup guide with verified steps.
- Security test suites for all of the above.

## Phase 4 — Web UI Foundation (0.5.0)

- React/Vite/TypeScript/Tailwind app embedded in the JAR; generated TypeScript client.
- Login/identity display, file tree, Monaco/CodeMirror editor, pre-save lint with server-authoritative validation, conflict handling, snapshot history and diff/restore UI.
- Localization of the entire UI from day one (English + Japanese at minimum), pseudo-locale and RTL support.
- Accessibility pass, dark/light themes, bundle-size budget enforced.
- Playwright E2E suite and automatically generated screenshots for docs.

## Phase 5 — Deployment Pipeline, Restart, and Rollback (0.6.0)

- JAR validator; staging; deployment state machine with persistence and crash recovery.
- Update-folder / staged replacement strategy verified on Linux and Windows (`jar-replacement.md`).
- Restart modes, countdown, health verification, automatic rollback, boot-loop protection, safe state.
- Drag-and-drop deploy UI with live progress via WebSocket; deployment history view.
- Fault-injection tests at each state; idempotency keys.

## Phase 6 — Console and Log Filtering (0.7.0)

- Log capture across platforms with fallbacks, ring buffer, backpressure.
- WebSocket protocol per AsyncAPI; console UI with level/plugin/text filters, command execution with policy and audit.
- Stack-trace grouping, ANSI handling, safe rendering.
- Log-flood and slow-client tests.

## Phase 7 — Public API, SDKs, CLI, and CI Integration (0.8.0)

- Finalize and freeze API v1 for the features so far; error-code catalog; rate limits; pagination; idempotency.
- Webhooks with signatures, retries, SSRF protection.
- Java API module with service registration, pre/post-deploy hooks; japicmp gating.
- TypeScript, Python, and Java clients generated and verified; Rust CLI with doctor, files, deploy, snapshots, logs, audit, tokens; machine-readable output and exit-code spec.
- GitHub Action for CI deployments with Cloudflare Access service-token support and an `examples/` workflow.
- Cross-language conformance suite.
- API documentation, cookbook, and example collections.

## Phase 8 — Platform and Version Expansion (0.9.0)

- Folia adapter with region-aware scheduling and correct declaration.
- Spigot/CraftBukkit and legacy-version adapters, extending the matrix downward as far as evidence supports (target 1.8.8) with documented limitations per tier.
- Velocity and BungeeCord/Waterfall adapters; Standalone mode.
- Paper-fork verification (Purpur, Pufferfish, and others).
- Research ADRs for Sponge, Fabric, Forge, NeoForge; implement only what the research justifies.
- Compatibility matrix expanded (Tier 1/2/3 assignments updated from real results); generated support-matrix docs; compat-report issue template and Discussion category active.

## Phase 9 — Hardening, Performance, and Documentation Completion (0.10.0 to 0.99.x / release candidates)

- Independent-style security review (threat model walk-through, dependency audit, fuzz campaigns); fix findings.
- Performance benchmarks and optimization against the defined budgets.
- Reliability soak tests (days-long run with random deploys, restarts, and console load).
- Documentation site complete and versioned; all declared translations structurally complete; native-review requests issued.
- Approval workflow (two-person rule) and optional time windows implemented if they were scheduled.
- Migration and upgrade tests from every previous 0.x.
- Release candidates (`1.0.0-rc.N`) with a public feedback period.

## Phase 10 — DeployMC v1.0.0

- All acceptance criteria in Part 17 met and verified.
- Signed artifacts, SBOM, provenance, published to the declared channels (with the owner's authorization).
- Announcement material prepared, honest and accurate.

## Phase 11 — Post-1.0.0 Development

- Additional platforms (Sponge, Fabric, NeoForge) per research results
- Fleet view across multiple servers via proxy/standalone federation
- OIDC/SAML and Cloudflare group-claim mapping, SSO integrations
- Config template/diff/sync between environments (dev, staging, production)
- Plugin update discovery integrations (Modrinth/Hangar/GitHub Releases) with approval flow
- Schema registry for plugin configs and richer validation
- IDE plugins (IntelliJ/VS Code) using the public API
- Prometheus/OpenTelemetry metrics export
- More translations and community translation tooling
- Long-term maintenance, LTS policy, and security advisories process

---

# PART 17 — v1.0.0 ACCEPTANCE CRITERIA

DeployMC is **not** v1.0.0 until all of the following are true and verified. Maintain a checklist in `docs/development/v1-checklist.md` with evidence links.

## 17.1 Functionality

- Sandboxed file explorer/editor with server-side validation, optimistic concurrency, and protected paths
- Snapshots with diff and one-click restore, retention, integrity verification
- JAR deployment pipeline with staging, validation, controlled restart, health verification, automatic rollback, crash recovery, and boot-loop protection
- Real-time console with filtering, commands, backpressure, and audit
- Authentication in `cloudflare`, `token`, and `local-onetime` modes; RBAC with path scopes; audit log
- Public API v1 (REST, WebSocket, webhooks) fully specified, versioned, and conformance-tested
- Java API module, TypeScript/Python/Java clients, CLI, and GitHub Action released

## 17.2 Compatibility

- Support matrix generated from real CI results; every Tier 1 entry green on the latest run
- Paper (latest), Folia, and at least one proxy platform in Tier 1
- Declared legacy range tested at the tiers claimed, with documented limitations
- Java 8 baseline bytecode verified; verified to start on every declared Java version

## 17.3 Security

- Threat model complete and reviewed; all security test suites green on Linux and Windows
- No known unresolved critical/high issues; dependency audit clean or documented
- Signed releases, SBOM, provenance; `SECURITY.md` process exercised at least once in a drill
- Verified fail-closed behavior in all auth failure modes

## 17.4 Quality and Performance

- Test coverage targets defined and met for core, sandbox, auth, rbac, deploy (set numeric targets in `docs/development/quality.md`)
- Performance budgets met and documented; soak test passed
- Accessibility checks pass; keyboard-only operation of core workflows

## 17.5 Documentation and Localization

- README complete per Part 10.1, accurate, with real screenshots/GIFs, in English plus every declared translation
- Documentation site complete and versioned; guides for Cloudflare, Windows, Docker, CI
- Web UI and plugin messages complete in all declared languages (missing keys = 0; stale keys tracked)
- API reference, Javadoc, and SDK docs published
- Contribution, translation, and governance docs complete

## 17.6 Release and Community

- Release automation proven by at least two real pre-releases
- Published to declared distribution channels
- Issue forms, Discussions, labels, milestones, and good-first-issue backlog active
- Public roadmap current

## 17.7 Definition of Done (Every Task)

A task is done only when: implemented completely, tested (and tests were actually run), documented, localized where user-facing, compatibility impact assessed, security considered, reviewed, CI green, and merged through a PR.

---

# PART 18 — POST-1.0.0 VISION

DeployMC aims to become the standard, safe, collaborative deployment layer for Minecraft server development teams: from a solo developer's local test server to multi-server networks, across versions and platforms, in the user's own language, automatable through a stable API. After 1.0.0, continue along Phase 11, guided by community feedback, security needs, and real usage.

---

# PART 19 — STRICT DEVELOPMENT BEHAVIOR

You must follow these rules for every development task.

## 19.1 Inspect Before Modifying

Before making changes:

- Inspect repository structure, current branch, Git status, and recent commits.
- Read `AGENTS.md`, `docs/development/project-status.md`, `implementation-status.md`, and `HANDOFF.md`.
- Read relevant documentation, specs, ADRs, and related source and tests.
- Identify existing conventions and dependencies.

Never assume the repository is empty or unchanged. Never rely on memory of an earlier session; rely on the repository.

## 19.2 Plan Before Implementing

Before significant work, state: the goal, affected components, required changes, tests, documentation/translation changes, compatibility impact, and security considerations.

## 19.3 Verify Facts

Many details in this specification (Minecraft/Java version requirements, Cloudflare Access endpoints and header names, update-folder semantics, GitHub achievement criteria, platform download APIs, library Java baselines, trailer formats) are time-sensitive. **Verify them against official documentation or by experiment before relying on them**, and record the evidence in the repository. If you cannot verify something, say so; do not present it as fact.

## 19.4 Implement Completely

Never leave: TODO-only implementations, empty source files, fake APIs, nonfunctional placeholders, hard-coded demo behavior presented as real functionality, or unused modules without a clear purpose. If a feature cannot be completed, document the limitation and do not claim it is implemented.

## 19.5 Test

Run appropriate tests after changes. Report exact commands and results. Never claim a test passed if it was not run. Never disable, skip, or weaken a test to make CI pass without an explicit, documented justification.

## 19.6 Inspect Changes

Before committing: inspect `git diff`, check formatting and lint, check for secrets and accidental files (including local server data, EULA files, world data, and downloaded server JARs), check tests, documentation, translations, and compatibility.

## 19.7 Preserve Existing Work

Do not delete or overwrite existing implementation without understanding its purpose. Do not replace a complex working module with a simplified example just to make generation easier.

## 19.8 Dependencies

Evaluate every dependency before introducing it: license compatibility, maintenance health, security record, size, Java baseline, relocation behavior. Prefer mature, actively maintained libraries and the standard library. Document important dependency decisions in ADRs.

## 19.9 Security Posture While Developing

Never commit secrets, real Cloudflare identifiers/tokens, or real server addresses. Use obviously fake examples. Never weaken a security default for convenience; if a convenience mode is needed, it must be explicit, loud, and documented.

## 19.10 Honest Reporting

Do not hide problems. Do not claim GitHub operations, releases, or merges succeeded unless confirmed. If an operation requires authentication or the owner's approval, explain exactly what is needed.

## 19.11 File Size and Structure Discipline

Large code volume is expected over the project's life. Keep it maintainable: small cohesive files, clear module boundaries, no copy-paste duplication across platform adapters (extract to Core or shared adapter code), and refactor continuously (in separate `refactor/` PRs).

---

# PART 20 — LONG-TERM OPERATION PROTOCOL

This project will span many sessions. You will not remember previous sessions. The repository is the memory.

## 20.1 Required Tracking Documents

Maintain and update after every meaningful milestone or at the end of every session:

- `docs/development/project-status.md`: current version, current milestone, completed/active/pending milestones, known limitations, open architectural questions, next tasks
- `docs/development/implementation-status.md`: per-component status: Not Started, Designed, In Progress, Experimental, Implemented, Tested, Documented, Stable. Never mark Stable merely because code exists; it must be implemented, tested, documented, localized, security-reviewed, and reviewed.
- `docs/development/compat-status.md`: current support matrix results and open compatibility issues
- `docs/development/i18n-status.md`: translation completeness per language
- `docs/development/HANDOFF.md`: a short note for the next session: what was just done, what is in flight (branches/PRs), what is blocked, exact next steps, and gotchas discovered
- `docs/development/decisions-pending.md`: questions requiring the owner's decision
- `AGENTS.md`: the condensed, always-read rule set (commit rules, branch rules, test commands, forbidden actions, verification duties)

## 20.2 Session Start Procedure

1. Read `AGENTS.md` and `HANDOFF.md`.
2. Check `git status`, current branch, recent commits, open PRs/issues, CI state.
3. Read `project-status.md` and pick the next task from the active milestone.
4. State the plan (Part 19.2) before coding.

## 20.3 Session End Procedure

1. Ensure all work is committed on a proper branch; no uncommitted or unexplained changes.
2. Run the relevant tests and record results.
3. Update tracking documents and `HANDOFF.md`.
4. Report to the owner in Japanese (Part 22).

## 20.4 Task Granularity

Work in small vertical slices that end in a merged PR (for example: "sandbox: reject Windows reserved names", not "implement security"). Each slice includes tests, docs, translations where relevant, and a changelog-worthy commit message. Prefer finishing a slice over starting several.

## 20.5 Large-Scale Consistency

Because the codebase will be large, keep these invariants true at all times and check them periodically: specs match implementation; README matches reality; support matrix matches CI; generated clients match the spec; translation catalogs match the English source structure; ADRs match the code.

---

# PART 21 — INITIAL EXECUTION INSTRUCTIONS

Do not start by generating the entire final repository. First inspect the actual repository. Then create a project-wide implementation plan based on this specification. The first task is establishing the foundation (Phase 0).

## Initial Tasks

0. Place this specification at `docs/development/DeployMC_Complete_Master_Development_Specification.md` (through a normal docs PR) so that every future session can read it.
1. Inspect the repository, Git status, branch, remotes, and available tooling (JDK, Gradle, Node, pnpm, Rust, Python, `gh` CLI, Docker). Report what is missing.
2. Verify the repository name and run the naming check.
3. Research related projects and write `competitive-analysis.md`.
4. Choose and record the license (ADR-0013) after presenting trade-offs to the owner (this is not legal advice).
5. Create README.md (English), then `docs/i18n/README.ja.md` with a status header.
6. Create CONTRIBUTING.md, CODE_OF_CONDUCT.md, SECURITY.md, SUPPORT.md, ROADMAP.md, GOVERNANCE.md, AUTHORS.md, AGENTS.md, TRANSLATING.md.
7. Create the architecture overview, threat-model draft, support-matrix design, and initial ADRs.
8. Create the monorepo skeleton with real, buildable, tested minimal modules.
9. Configure `.editorconfig`, `.gitignore`, `.gitattributes`.
10. Configure issue forms, PR template, labels, milestones, Discussions categories, CODEOWNERS, Dependabot.
11. Configure initial GitHub Actions (build/test, commitlint, docs links, i18n check).
12. Draft the OpenAPI/AsyncAPI skeletons.
13. Create the milestone tracking system and tracking documents (Part 20).
14. Produce the first handoff note.

Do not implement the entire product during this initial task. However, the documentation must describe the complete final product, not merely the first milestone.

## Initial Git Workflow

Create a separate branch for each coherent group of changes, with multiple meaningful commits, and open a meaningful PR for each. Example:

```
docs/project-vision
  docs: add initial project vision
  docs: document goals, non-goals, and scope
  docs: add competitive analysis

docs/architecture-and-adrs
  docs: add system architecture overview
  docs: add platform SPI and compatibility strategy
  docs: add initial architecture decision records
  spec(security): add threat model draft

docs/development-guidelines
  docs: add contribution guidelines
  docs: define commit and branch conventions
  docs: add security policy

i18n/localization-foundation
  i18n: add translation status tooling
  docs: add Japanese README
  docs: add translation guide

build/repository-foundation
  build: initialize Gradle multi-project build
  build(web): initialize Vite React TypeScript app
  build(cli): initialize Rust workspace
  ci: add build and test workflow
  ci: add commit and docs validation
```

Wait for checks and review. Merge only when ready. Do not combine all initial work into one commit. Do not create artificial PRs.

---

# PART 22 — COMMUNICATION WITH THE PROJECT OWNER

Communicate with the repository owner in **Japanese**. Repository artifacts remain in English (except localized content).

When presenting results, include:

1. What was completed
2. Files created
3. Files modified
4. Architectural decisions (and ADRs)
5. Tests executed, with exact commands
6. Actual test results
7. Git branch
8. Commits created
9. PR status (confirmed, not assumed)
10. Compatibility and security impact
11. Remaining work
12. Decisions needed from the owner
13. Next recommended task

Provide commands appropriate for the owner's actual environment (check the OS: Windows vs Linux vs macOS). If something needs the owner's action (authentication for `gh`, repository settings, Cloudflare account steps, publishing credentials), explain exactly what and why.

---

# PART 23 — DETAILED WORK PACKAGES (PHASE-BY-PHASE IMPLEMENTATION BREAKDOWN)

Part 16 defines the roadmap. This part breaks every phase into **Work Packages (WP)**. A WP is a coherent group of one to five PRs. Use the WP IDs in branch names, issue titles, milestones, and `project-status.md`.

For every WP, the following are implicitly required (do not repeat them in each WP, but never skip them): tests actually run, documentation updated, user-facing strings localized, compatibility impact assessed, security considered, tracking documents updated, Conventional Commits, PR template filled in.

Each WP lists: **Goal**, **Tasks**, **Deliverables**, **Verification**. Treat the task lists as the minimum, not the maximum. If you discover necessary work that is not listed, add it to the WP and record it in `project-status.md`.

## Phase 0 Work Packages (0.1.0)

### WP-0.1 Repository inspection and bootstrap
- Goal: understand the real starting state and establish the repository.
- Tasks: inspect repo/remote/branch/tools; report missing tooling to the owner; verify the repo name; create the initial commit structure on a branch (never commit directly to `main` after the first bootstrap commit); configure `.editorconfig`, `.gitignore` (Java/Gradle, Node, Rust, Python, IDE files, server data, `eula.txt`, `*.jar` test servers, `.deploymc/`), `.gitattributes` (normalize line endings, mark generated files and lockfiles, binary types).
- Deliverables: bootstrap branch merged; tooling report in `docs/development/environment.md`.
- Verification: fresh clone builds the (empty-but-valid) skeleton on Linux and Windows.

### WP-0.2 Research and naming
- Tasks: `docs/research/naming-check.md`; `docs/research/competitive-analysis.md` covering at least Pterodactyl, Crafty Controller, AMP, MCSManager, MCSM-like panels, file-manager and plugin-manager plugins, SFTP/WebDAV workflows, Git-based deployment practices, CI/CD deploy actions, and Cloudflare Access/Tunnel usage patterns; a **license comparison** document (MIT, Apache-2.0, GPL-3.0, AGPL-3.0, with the Bukkit/Spigot/Paper API licensing considerations and the implications for shading dependencies); recommendation presented to the owner in Japanese.
- Deliverables: three research documents; ADR-0013 (license) after owner approval.
- Verification: every claim in research docs has a source link and an "accessed on" date.

### WP-0.3 Core project documents
- Tasks: README (English, honest status), CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, SUPPORT, ROADMAP, GOVERNANCE, AUTHORS, AGENTS.md, TRANSLATING.md. AGENTS.md must be a **condensed** version of Parts 12, 19, and 20 plus the exact build/test commands per language, so a fresh session can operate safely after reading only that file.
- Deliverables: all files present, cross-linked, and linted.

### WP-0.4 Architecture and ADR baseline
- Tasks: `docs/architecture/overview.md`, `platform-spi.md`, `deployment-state-machine.md` (narrative; the formal version lives in `specification/`), `threading-model.md`, `api-layering.md`; ADR-0001 through ADR-0014 as **Proposed** or **Accepted** as appropriate (those requiring prototypes, such as the HTTP server choice, stay Proposed until WP-1.3).
- Deliverables: ADR index file and template.

### WP-0.5 Specification skeletons
- Tasks: `specification/api/openapi.yaml` with `server` resource and shared components (error schema, pagination, ETag headers, security schemes); `asyncapi.yaml` envelope definition; `errors.md` structure; `config.schema.json` skeleton; `threat-model.md` first draft; `audit-log-format.md` draft; `state-machine.md` draft.
- Verification: spec linters run in CI and pass.

### WP-0.6 Build skeleton
- Tasks: Gradle Kotlin DSL multi-project (`core`, `api`, `transport`, `platform:*`, `plugin`), version catalog, toolchains, `--release 8` enforced, Spotless/Checkstyle (or equivalent), dependency verification, a trivial but real unit test per module; `web/` Vite+React+TS with ESLint, Prettier, Vitest and one real test; `cli/` Cargo workspace with clippy/fmt and one real test; `sdk/python` pyproject with ruff, mypy/pyright, pytest.
- Verification: single documented command builds and tests everything; per-language commands documented.

### WP-0.7 CI foundation
- Tasks: workflows for build/test (matrix OS: ubuntu, windows; macOS for CLI), commitlint + PR title lint, docs link check, spelling check, spec lint, i18n structural check, CodeQL, dependency review, Scorecard; caching; concurrency groups; least-privilege `permissions:`; pin third-party actions by commit SHA.
- Verification: a deliberately failing PR in a scratch branch proves each gate can fail (then delete the branch; do not leave junk PRs).

### WP-0.8 GitHub community configuration
- Tasks: issue forms (bug, feature, compatibility report, docs, translation), PR template, `labels.yml` with a sync workflow, Discussion categories and a pinned welcome post, `CODEOWNERS`, `dependabot.yml`, `FUNDING.yml` (only if the owner wants), milestones 0.1.0–1.0.0, GitHub Project board. Document anything that requires owner action through the web UI or `gh` authentication.

### WP-0.9 Localization foundation
- Tasks: language switcher block, `docs/i18n/STATUS.md` generator, `tools/i18n/check` (see 11.3), translation-header format, first Japanese README (reviewed against the English source; marked with its real review status), `TRANSLATING.md`, translator Discussion category.
- Verification: breaking a placeholder in the Japanese file makes CI fail.

### WP-0.10 Tracking and handoff
- Tasks: create `project-status.md`, `implementation-status.md`, `compat-status.md`, `i18n-status.md`, `HANDOFF.md`, `decisions-pending.md`, `v1-checklist.md`; seed GitHub issues for Phase 1.
- Release: tag `v0.1.0` through the release workflow once criteria are met.

## Phase 1 Work Packages (0.2.0)

### WP-1.1 Platform SPI and capabilities
- Tasks: define Java interfaces (see 24.7) in `core`; `PlatformCapabilities` with a stable set of capability keys; a fake in-memory platform used by core tests; documentation of each capability and its fallback behavior.
- Verification: core tests run with no server present.

### WP-1.2 Bukkit-family adapter, minimal
- Tasks: plugin descriptor (`plugin.yml` with `name`, `main`, `version` injected from the build, `api-version` chosen deliberately with documented consequences, `folia-supported` only after WP-8.1), `JavaPlugin` entry point, lifecycle (enable/disable) with deterministic resource cleanup, data folder handling, version detection (server software name, Minecraft version, Java version) without NMS, a startup banner that prints the bind address, auth mode, and warnings.
- Verification: loads on Paper latest in the compat harness; clean disable leaves no non-daemon threads (test via thread dump comparison).

### WP-1.3 HTTP/WebSocket server decision and implementation
- Tasks: build small spikes for each candidate in ADR-0003 and measure JAR size, relocation success, classloader behavior in a real Paper server alongside another plugin that bundles a conflicting library, Java 8 compatibility, and WebSocket echo throughput; decide and record; implement the transport behind an internal interface so it can be swapped without touching core.
- Tasks (cont.): request pipeline (request ID, logging with redaction, size limits, timeouts, CORS disabled by default, security headers, `Host`/`Origin` validation hooks), graceful shutdown, port-in-use handling with a clear error, bind-address safety warnings.
- Verification: integration tests for lifecycle, slowloris-style timeouts, oversized body rejection.

### WP-1.4 Static asset serving from the JAR
- Tasks: serve the built Web UI from JAR resources with correct content types, caching headers (hashed assets), SPA fallback routing that never shadows `/api`, precompressed assets where beneficial, Content-Security-Policy appropriate for Monaco/CodeMirror workers, path safety for resource lookup (no traversal into the JAR's other entries).
- Verification: tests that `GET /../../plugin.yml` and encoded variants never leak non-asset resources.

### WP-1.5 Config system
- Tasks: `config.yml` default generation with extensive comments; schema validation with precise line/column errors; version field and migration framework with automatic backup before migration; safe-reload semantics (document which keys are hot-reloadable); a `/deploymc` in-game/console command set (`status`, `reload`, `doctor`, `token`) with permission nodes; protection so that the API can never write the config.
- Verification: property tests that random invalid configs fail closed.

### WP-1.6 Plugin message i18n runtime
- Tasks: message bundle loader with fallback chain, placeholder formatting, legacy-compatible color formatting (no hard dependency on modern text components), per-sender locale resolution where available, tests for missing keys falling back to English.

### WP-1.7 Shading, relocation, and JAR verification
- Tasks: ShadowJar with mandatory relocation prefix; a Gradle verification task that inspects the final JAR and **fails the build** if any class outside `io.<org>.deploymc` (or the chosen namespace) is present, if multi-release entries are malformed, or if bytecode major version exceeds the baseline in the common path; size report in CI.

### WP-1.8 Compatibility harness v1
- Tasks: harness skeleton (see 3.6), a script that downloads a server build via the provider's official API with checksum verification, runs headless, waits for the "Done" signal robustly (not fixed sleeps), runs a smoke test against `/api/v1/server` using a token provisioned through the host-controlled provisioning mechanism (WP-1.9; never by parsing console output), collects logs, and tears down; run on ubuntu and windows runners; cache server downloads.
- Release: `v0.2.0`.

### WP-1.9 Minimal authentication foundation (token provisioning)
- Goal: guarantee from the very first release that no API endpoint is reachable without authentication, without waiting for Phase 3.
- Tasks: `Authenticator` interface (final form arrives in WP-3.1) with a first implementation for `token` mode; **host-provisioned tokens**: the administrator (or the test harness) places a token record (id, hashed secret, optional expiry, label) into a host-controlled provisioning location (for example `plugins/DeployMC/tokens.d/*.json`, readable only through the local file system, never through the API); constant-time comparison; startup refusal with a clear message if no valid auth configuration exists (no silent fallback to open access); `/deploymc token create` console command that prints a new token once and stores only its hash; log redaction of `Authorization` headers.
- Deliverables: README note stating that until 0.4.0 all tokens are admin-equivalent; tests proving every route except `/healthz` and `/readyz` returns 401 without a valid token.
- Verification: automated route-enumeration test that walks the OpenAPI spec and asserts 401 for every path without credentials.

## Phase 2 Work Packages (0.3.0)

### WP-2.1 Sandbox path resolver
- Tasks: implement per 9.1 as a pure, heavily unit-tested class; define a `SandboxPath` value type that can only be constructed through the resolver; denylist/protected-path matching; error codes (`SANDBOX_TRAVERSAL`, `SANDBOX_PROTECTED`, `SANDBOX_INVALID_NAME`, ...); traversal payload corpus (at least several hundred entries incl. encoded, mixed-separator, Unicode, Windows-specific variants).
- Verification: runs on Linux and Windows CI; fuzz job runs on schedule.

### WP-2.2 File operations service
- Tasks: list, stat, read (range support), write (atomic temp+fsync+move), create, mkdir, rename/move (no overwrite unless requested, snapshot first), delete (snapshot first, recursive deletion with explicit confirmation flag and entry-count cap), upload (streaming, size limits, content hashing), download; per-file revision (strong hash or mtime+size+hash, documented) for ETag; handling of concurrent modifications; consistent behavior with symlinks (documented policy: never follow outside root).
- Verification: crash-safety test (kill during write leaves either the old or the new content, never a partial file).

### WP-2.3 Snapshot store
- Tasks: format per 24.4; content-addressed blobs with hash verification; metadata records; retention engine; restore; garbage collection with reference tracking; `verify` and `repair` routines; storage location rules and exclusion from sandbox; migration versioning.
- Verification: property tests (store/restore round trip), corruption-injection tests, concurrency tests.

### WP-2.4 Diff engine
- Tasks: line diff with stable output format; word-level highlighting for the UI; YAML/JSON semantic diff (key-path based) with graceful fallback to text diff; JAR metadata diff (plugin descriptor differences, dependency changes, hash/size); size-bounded diff computation with explicit "too large" results.

### WP-2.5 Validators
- Tasks: YAML (duplicate keys, tabs, anchor/alias expansion limits, error positions), JSON, TOML, `.properties`; extensible validator registry keyed by extension/path pattern; warnings vs errors; server-authoritative validation endpoint and save-time enforcement; comment-preserving YAML helper for programmatic edits (needed later for config templates).

### WP-2.6 Files and snapshots REST endpoints
- Tasks: spec-first OpenAPI additions; implement controllers as thin adapters over services; ETag/`If-Match` handling; pagination; error mapping; contract tests generated from the spec; all endpoints are protected by the minimal token authentication from WP-1.9 (there is no unauthenticated mode and no dev bypass flag in any build); the full RBAC arrives in Phase 3, so until then the single provisioned token is treated as an `admin`-equivalent identity, which is documented as a pre-0.4.0 limitation in the README and release notes.
- Release: `v0.3.0`.

## Phase 3 Work Packages (0.4.0)

### WP-3.1 Auth framework
- Tasks: `Authenticator` interface returning an `Identity` (id, display name, auth method, groups, raw claims subset); request-level auth filter with fail-closed defaults; identity propagation into services via an explicit context object (not thread-locals alone, to remain correct with async execution); uniform 401/403 behavior that leaks no detail.

### WP-3.2 Cloudflare Access JWT verifier
- Tasks: per 9.2; JWKS client with cache, ETag/`Cache-Control` awareness, bounded refresh frequency, stale-while-error policy with a hard maximum staleness; algorithm allow-list; claim validation; clock-skew config; support for user tokens and service tokens; team-domain/AUD configuration validation; diagnostics in `doctor` that explain why a token was rejected without exposing token contents.
- Verification: test vectors (valid, expired, not-yet-valid, wrong audience, wrong issuer, wrong algorithm, `none`, tampered payload, unknown key id, rotated key), plus a local mock JWKS server.

### WP-3.3 Token and one-time-token modes
- Tasks: extend the WP-1.9 foundation into the full token system: API token generation (high entropy, prefix for secret-scanning friendliness, e.g. `dmc_`), hashed storage with a modern password-hashing or keyed-hash approach appropriate for high-entropy tokens (document the choice), scopes, expiry, last-used, revocation; one-time local token with TTL and attempt limits; console commands to create/revoke tokens; token-leak resistance in logs.

### WP-3.4 RBAC engine
- Tasks: role/permission model per 9.5 and 24.2; path-scope evaluation via the sandbox resolver; deny-overrides; effective-permission computation for `/me`; config validation (unknown permission names are errors); explain-mode used by `doctor` and tests ("why was this denied?"); property tests ensuring a token can never exceed its owner's permissions.
- Normative contract: `specification/security/rbac.md`. The closed registry has 23 permissions; token scopes narrow the owner's effective permissions; `local-onetime` is an ordinary RBAC identity; invalid reloads deny all; WebSocket permission loss closes with `4403`. Audit record generation is delivered by WP-3.5.

### WP-3.5 Audit log
- Tasks: format per 24.3; append-only writer with batching and fsync policy; hash chain; rotation; reader/verifier; query API; export; redaction guarantees verified by tests; behavior on disk-full (fail the operation or enter degraded mode per a documented policy; never silently drop audit records for privileged operations). Normative contract: `specification/audit/audit-log-format.md`. The selected v1 policy is fail-closed: a `pending` record is committed before a privileged mutation, and a write failure rejects the operation.

### WP-3.6 Web-facing protections
- Tasks: rate limiting (per identity and per IP, token-bucket, documented headers); brute-force lockouts for token mode; CSRF defenses for any cookie-based session; WebSocket Origin checks; security headers; request-size limits per endpoint class; trusted-proxy configuration for forwarded headers (default: trust none except loopback tunnel).

### WP-3.7 Security documentation and guides
- Tasks: finalize threat model; `examples/cloudflare-tunnel/` with `cloudflared` config, step-by-step Access application/policy setup, service-token creation for CI, and a **verification checklist** ("how to prove the port is not reachable except through the tunnel"); each Cloudflare detail re-verified against current official docs with citation links and the access date.
- Release: `v0.4.0`.

## Phase 4 Work Packages (0.5.0)

### WP-4.1 Web UI shell
- Tasks: app scaffolding, routing (hash-compatible if static hosting constraints require), layout (sidebar, header, content, status bar), theme system (light/dark/system), design tokens, keyboard shortcuts framework, error boundary with request-ID display, global toast system, loading/empty/error states for every view.

### WP-4.2 API client and state
- Tasks: generated TypeScript client wired to the spec; auth-awareness (401 handling, identity display); query caching and invalidation strategy; optimistic concurrency conflict UX; WebSocket client with reconnect/backoff and resume.

### WP-4.3 File explorer
- Tasks: lazy-loaded tree with virtualization; context menus; create/rename/delete with confirmations; drag-and-drop within the tree; multi-select; search by file name; breadcrumbs; file type icons; clear display of protected/readonly items and why; upload via button and drag-and-drop with progress and cancellation.

### WP-4.4 Editor
- Tasks: editor integration with lazy loading and worker configuration compatible with the CSP; language modes; inline diagnostics from server validation; save with revision check; conflict resolution view (yours/theirs/merged); unsaved-changes protection; large-file safeguards; read-only mode for users lacking write permission; per-file settings (wrap, font size); presence indicators for other editors of the same file.

### WP-4.5 History and diff UI
- Tasks: snapshot timeline per file; side-by-side and inline diff; restore with confirmation; compare any two snapshots; show who/when/why; JAR metadata diff view.

### WP-4.6 Localization of the UI
- Tasks: ICU-based catalog loading with lazy per-language chunks; English and Japanese complete; pseudo-locale and RTL layout verification; locale-aware date/number/relative-time formats; language switcher persisted; lint rule for hard-coded strings; missing-key reporting in development builds.

### WP-4.7 Accessibility and quality
- Tasks: keyboard navigation of tree/editor/dialogs; focus management; ARIA roles; contrast verification for both themes; reduced-motion support; axe checks in CI; manual screen-reader smoke notes recorded in docs.

### WP-4.8 Embedding and size budget
- Tasks: Gradle task that builds `web/` and embeds the output into the plugin JAR; a deterministic build; bundle analysis report; budget thresholds enforced in CI; chunk strategy for heavy dependencies.

### WP-4.9 E2E and docs automation
- Tasks: Playwright suite against a real Paper instance from the compat harness; automated screenshot/GIF generation for README and docs in English and Japanese.
- Release: `v0.5.0`.

## Phase 5 Work Packages (0.6.0)

### WP-5.1 JAR inspection and validation
- Tasks: safe ZIP reading (entry count, total uncompressed size, compression-ratio limits, zip-slip detection, duplicate entries); descriptor parsing for `plugin.yml`, and `paper-plugin.yml` if present; extract name/version/main/api-version/dependencies/softdependencies/load order; compare against the currently installed version; detect obviously mismatched platform (e.g. a Velocity plugin uploaded to Paper); optional hash allowlist/denylist; optional required signature verification (policy-driven); produce a **validation report** with errors, warnings, and informational findings; document clearly that this does not detect malicious code.

### WP-5.2 Deployment model and persistence
- Tasks: formal state machine per 24.5; persistent transaction record written atomically at every transition; recovery routine at plugin startup that inspects unfinished transactions and decides to resume, verify, or roll back; idempotency-key store; concurrency control (only one active deployment per target set, or a documented queue); cancellation semantics.

### WP-5.3 Staging and application strategy
- Tasks: staging directory layout; hash verification between upload, staging, and applied states; application mechanisms (update folder, restart-time swap, direct replace only for non-loaded files); Windows-specific path tested in CI with a real loaded JAR; documented behavior matrix per platform in `docs/compatibility/jar-replacement.md`; handling of plugin data folder migrations (never touched implicitly).

### WP-5.4 Multi-file deployments (bundles)
- Tasks: a deployment can include several changes (JARs and config files) applied atomically as one transaction; manifest format (`deploymc-bundle.json`) describing operations, expected hashes, and health checks; a bundle upload path used by the CLI and GitHub Action; per-operation snapshotting and unified rollback.

### WP-5.5 Restart orchestration
- Tasks: restart modes per 3.5; configurable countdown and player notifications (localized, legacy-compatible); `save-all` flush with timeout; refusal when mode is `none` and the user requests a restart (clear message and alternatives); optional pre-restart checks (no running critical tasks, minimum interval between restarts); post-restart reconnection behavior of the Web UI (poll health with backoff and show progress).

### WP-5.6 Verification and health checks
- Tasks: built-in checks (target plugin enabled; no exceptions attributed to the plugin during enable; server reached "ready"); configurable custom checks (log regex must appear / must not appear within timeout; console command returns expected output; HTTP probe to a local URL); timeouts and grace periods; clear failure reasons surfaced to users.

### WP-5.7 Automatic rollback and safe state
- Tasks: rollback procedure restoring the exact previous files from snapshot; verification after rollback; boot-loop protection counters persisted across restarts; **safe state** where DeployMC stays reachable and refuses further automatic restarts until an authorized human acknowledges; explicit handling for the case where DeployMC itself failed to load (document manual recovery in the admin guide, and provide a documented, minimal, out-of-band recovery script).

### WP-5.8 Deployment UI
- Tasks: drag-and-drop JAR/bundle area; validation report display; plan preview ("these files will change"); confirm dialog with restart options; live progress timeline fed by WebSocket; deployment history with filter/search; detail view with logs and diffs; rollback button with confirmation; cancellation.

### WP-5.9 Fault injection and chaos tests
- Tasks: a test hook framework (compiled out of release builds, or guarded so it cannot be enabled in production) that injects failures/kills at each state-machine step; automated test that every injection point ends in a consistent, recoverable state; Windows file-lock scenario tests; disk-full and permission-denied scenarios.
- Release: `v0.6.0`.

## Phase 6 Work Packages (0.7.0)

### WP-6.1 Log capture layer
- Tasks: Log4j 2 appender attachment/detachment with correct classloader handling; fallback capture for platforms without it; structured record (timestamp, level, logger name, thread, message, throwable, attributed plugin if resolvable); attribution heuristics documented as heuristics; guard against recursion (DeployMC logging its own log delivery); verify no leaks after disable.

### WP-6.2 Ring buffer and query
- Tasks: bounded, memory-capped buffer; indexed lookup by time and level; filter evaluation shared between server and CLI; the **filter expression syntax** (see 24.8) with a parser, tests, and documentation.

### WP-6.3 WebSocket console channel
- Tasks: per AsyncAPI; subscription messages with filters; backfill from buffer; sequence numbers; heartbeat; slow-consumer policy (coalesce/drop with a notice message); per-connection memory cap; connection limits per identity.

### WP-6.4 Command execution
- Tasks: command dispatch on the correct platform thread with result capture where the platform allows; allow/deny pattern policy per role; per-user rate limit; audit of every command; optional two-step confirmation for dangerous commands (`stop`, `op`, `deop`, `whitelist`, `ban`, `reload`, configurable); blocking of commands that would re-enter DeployMC in unsafe ways.

### WP-6.5 Console UI
- Tasks: virtualized log view; level/logger/text/regex filters with saved presets; pause/resume auto-scroll; stack-trace folding; ANSI handling; copy/export selection; command input with history and (where possible) command suggestions; clear unavailable-state UX when console permission or capability is missing.

### WP-6.6 Load and robustness tests
- Tasks: log-flood test (tens of thousands of lines per second) proving bounded memory and no main-thread impact; many slow clients; reconnect storms; malformed WebSocket frames fuzzing.
- Release: `v0.7.0`.

## Phase 7 Work Packages (0.8.0)

### WP-7.1 API v1 consolidation
- Tasks: audit every endpoint against API principles (8.1); complete error-code catalog; unify pagination/filter/sort; idempotency across mutating endpoints; rate-limit headers; `Deprecation`/`Sunset` mechanism implemented even if unused; API changelog (`specification/api/CHANGELOG.md`); breaking-change detection in CI against the previous released spec.

### WP-7.2 Webhooks
- Tasks: registration/management endpoints; delivery queue with persistence, exponential backoff, dead-letter state; signature scheme documented with verification samples in each SDK language; SSRF protection (resolve and validate target IPs at delivery time to avoid DNS rebinding); delivery log and manual redelivery; test-send endpoint; payload schemas in the spec.

### WP-7.3 Java API module
- Tasks: finalize interfaces (hooks, validators, schemas, event subscription); service registration through the platform service registry (and a plain static-accessor fallback documented as a convenience); Javadoc with examples; japicmp gating; sample third-party plugin in `examples/` using the API; Maven Central publication plumbing (dry-run first).

### WP-7.4 TypeScript SDK
- Tasks: generated client, ergonomic wrapper (auth helpers for API token and Cloudflare service token, retry/backoff, idempotency helpers, pagination iterators, WebSocket helper with typed events), ESM+CJS builds, browser compatibility, typed errors from the error catalog, docs with runnable examples, contract tests against a live instance.

### WP-7.5 Python SDK
- Tasks: generated client plus a Pythonic wrapper (sync and asyncio), typing marker, error classes, context-manager helpers for deployments ("deploy and wait"), PyPI-ready packaging, supported Python versions documented, tests against a live instance.

### WP-7.6 Java client
- Tasks: Java 8 baseline client with minimal dependencies (or the JDK HTTP client behind an abstraction for newer JVMs), builder-style API, typed models, WebSocket support, Maven coordinates and docs.

### WP-7.7 Rust CLI
- Tasks: command spec per 24.6; auth configuration profiles (stored securely, never in plain logs; support environment variables for CI); shell completions; human and JSON output; progress display for long operations; stable exit codes; `deploymc doctor` working both locally (via API) and for diagnosing connectivity/Cloudflare issues; release binaries for Linux/macOS/Windows x64 and arm64; man page generation; self-update **not** included unless justified by an ADR.

### WP-7.8 GitHub Action
- Tasks: inputs/outputs spec; deploy a JAR or bundle; wait-for-completion with timeout; fail on rollback; job summary output with the deployment report and diff; secret handling guidance; support for Cloudflare Access service tokens and DeployMC tokens; example workflows (deploy on tag, deploy to staging on PR label, manual dispatch with approval via GitHub Environments); pinned dependency and provenance for the action itself.

### WP-7.9 Conformance suite and docs
- Tasks: a shared test suite expressed once (for example as scenario files) executed by CLI, each SDK, and the Action against a live server in CI; cookbook documentation; Postman/Bruno/`.http` examples; API reference publication.
- Release: `v0.8.0`.

## Phase 8 Work Packages (0.9.0)

### WP-8.1 Folia support
- Tasks: scheduler abstraction supporting global-region, async, and region-bound tasks with feature detection through reflection; make sure no code path calls main-thread-only Bukkit APIs from the wrong thread; descriptor flag `folia-supported` set only after the Folia integration suite passes; Folia entry in the Tier 1 matrix; documentation of Folia-specific limitations.

### WP-8.2 Spigot and CraftBukkit
- Tasks: verify and document behavior without Paper-only APIs; Log4j/JUL fallback verification; update-folder semantics test; restart-script support; capability reporting for missing features.

### WP-8.3 Legacy version expansion
- Tasks: step down through versions in controlled increments (for example 1.20 → 1.18 → 1.16 → 1.13 → 1.12 → 1.8.8), at each step: run the harness, fix incompatibilities through feature detection, record results and the Java version used; stop when the evidence says further support is not viable and document the real lower bound; note API differences encountered in `docs/compatibility/api-differences.md`.

### WP-8.4 Newer-version tracking
- Tasks: a scheduled job that detects new Minecraft/Paper releases (via official APIs), runs the harness against them, and opens an issue when failures occur; policy for adding new versions to the matrix promptly; a documented process for "snapshot/pre-release" versions (best-effort only).

### WP-8.5 Paper forks
- Tasks: Purpur, Pufferfish, Leaf, and other significant forks verified individually in the matrix; known-differences document.

### WP-8.6 Velocity adapter
- Tasks: proxy SPI implementation (scheduler, logging, console, command dispatch, plugin enumeration); proxy plugin directory management; restart semantics for proxies; compat harness for Velocity; descriptor/annotation specifics verified against official docs.

### WP-8.7 BungeeCord and Waterfall adapter
- Tasks: same as above for Bungee-family; document Waterfall's status as a project and its implications; Tier assignment from evidence.

### WP-8.8 Standalone mode
- Tasks: runnable JAR (`deploymc-standalone.jar`) and/or CLI-managed daemon that manages a configured directory and optionally tails a configured log file and controls a configured process through command hooks; no Minecraft classes required; suitable for unsupported platforms and for development; security notes (this mode does not know about server threading, so documentation must be explicit about what is not guaranteed).

### WP-8.9 Mod-loader research
- Tasks: research ADRs for Sponge, Fabric, Forge, NeoForge: mod directory semantics, classloading, hot-replace feasibility, descriptor formats, security implications; produce go/no-go recommendations with a prototype where cheap; present to the owner; implement only approved ones in later releases.

### WP-8.10 Matrix reporting
- Tasks: generated support matrix page with last-run dates and badge JSON; "compat report" issue form flows into `compat-status.md`; a visible changelog of matrix changes.
- Release: `v0.9.0`.

## Phase 9 Work Packages (0.10.0 → release candidates)

### WP-9.1 Security review
- Tasks: walk through the threat model line by line against the code; perform a self-audit with checklists (OWASP ASVS-inspired for the web API; WebSocket-specific checks); dependency audit; fuzzing campaigns with recorded corpora; run static analyzers (SpotBugs/Error Prone for Java, `cargo audit`/`cargo deny`, `npm audit` policy, Bandit-style for Python); fix findings; record results in `docs/security/review-<date>.md`. Invite community security feedback; do a disclosure-process drill.

### WP-9.2 Performance work
- Tasks: implement benchmark jobs; profile idle overhead and hot paths; tune thread pools; optimize diff and snapshot throughput; reduce JAR/bundle size; document measured results and hardware used.

### WP-9.3 Soak and reliability
- Tasks: multi-day automated soak (random edits, deployments, restarts, console traffic, reconnects) on Linux and Windows; leak detection (threads, file handles, memory); fix and document.

### WP-9.4 Approval workflow and policies
- Tasks: optional two-person approval for deployments (requester ≠ approver), approver role, expiry of approvals, API and UI support, audit entries, webhook events; optional deploy windows and freeze periods; optional required checks (e.g. CI-provided signature or hash allowlist).

### WP-9.5 Documentation site completion
- Tasks: finalize the site structure (24.9); versioned docs; search; complete guides; reference docs generated; translation of the high-traffic pages; screenshots regenerated; an FAQ drawn from real issues.

### WP-9.6 Localization completion
- Tasks: bring Web UI and plugin messages to full coverage for all declared languages; recruit and credit native reviewers; update status files; verify RTL and long-string layouts; ensure docs for translated languages state their review status honestly.

### WP-9.7 Upgrade and migration testing
- Tasks: automated tests that upgrade from every released 0.x config/data format to the current one; downgrade guidance; backup behavior verification.

### WP-9.8 Release candidates
- Tasks: publish `1.0.0-rc.1..N` through the real release pipeline (with the owner's authorization for external channels); collect feedback via Discussions; triage; stabilize.

### WP-9.9 v1.0.0 verification
- Tasks: walk `v1-checklist.md` line by line with evidence links; record gaps; do not release until zero gaps remain or each remaining gap is explicitly accepted by the owner and documented in the release notes.

---

# PART 24 — DETAILED TECHNICAL REFERENCE SPECIFICATIONS

These are **starting designs**. They are binding as intent, but the exact names and shapes must be finalized in the `specification/` directory through the spec-first process. If implementation experience shows a better design, change the specification first (with an ADR when significant) and then the code.

## 24.1 Configuration Reference (initial key set)

`plugins/DeployMC/config.yml` (host-controlled; never writable through the API):

```yaml
config-version: 1
language: en                      # default language for console/in-game messages

bind:
  address: 127.0.0.1              # non-loopback requires allow-non-loopback: true
  port: 8080
  allow-non-loopback: false
  trusted-proxies: []             # CIDRs whose forwarded headers are trusted

auth:
  mode: cloudflare                # cloudflare | token | local-onetime
  cloudflare:
    team-domain: ""               # e.g. myteam.cloudflareaccess.com
    audience: ""                  # Access application AUD tag(s)
    clock-skew-seconds: 60
    jwks-cache-max-age-seconds: 3600
    jwks-max-stale-seconds: 86400
    require-shared-secret-header: ""   # optional defense in depth, header name
  token:
    default-ttl-days: 90
    max-ttl-days: 365
  local-onetime:
    ttl-minutes: 15
    max-attempts: 5

sandbox:
  root: "."                       # relative to the server's plugins directory
  protected-paths: [".deploymc/**", "DeployMC/**"]
  deny-patterns: []
  max-file-size-mb: 10
  max-upload-size-mb: 64
  max-list-entries: 5000
  follow-symlinks: false

snapshots:
  directory: ".deploymc/history"
  retention:
    max-versions-per-file: 50
    max-age-days: 90
    max-total-size-mb: 2048

deploy:
  staging-directory: ".deploymc/staging"
  restart:
    mode: shutdown-only           # shutdown-only | spigot-restart-script | none
    countdown-seconds: 30
    save-before-restart: true
    min-interval-minutes: 5
  verification:
    timeout-seconds: 180
    require-plugin-enabled: true
    fail-patterns: []             # regexes that indicate failure in logs
    success-patterns: []
  rollback:
    automatic: true
    max-attempts: 2
  approval:
    required: false
    approver-roles: []
  jar-policy:
    require-descriptor: true
    hash-allowlist-file: ""
    hash-denylist-file: ""

console:
  enabled: true
  buffer-lines: 5000
  command-policy:
    allow: ["*"]
    deny: ["stop", "op *", "deop *"]
    confirm: ["reload", "whitelist *"]

audit:
  directory: ".deploymc/audit"
  rotate-size-mb: 50
  retention-days: 365
  fsync-policy: "record"
  max-batch-records: 1

limits:
  rate-limit-per-minute: 300
  max-websocket-connections: 50

roles: {}                         # see 9.5
users: {}
default-role: none

webhooks:
  allow-private-targets: false

telemetry:
  enabled: false                  # opt-in only
```

- Unknown keys produce warnings; invalid values produce errors that name the key, the line, and the allowed values.
- Every key is documented in the docs site with type, default, constraints, hot-reload behavior, and security impact.

## 24.2 Permission Identifiers

Permissions are flat, dotted, lowercase identifiers; `*` is allowed only as a whole-grant for admin roles. The list below is the closed registry; `specification/security/rbac.md` and `config.schema.json` mirror it, and any other name is a configuration error. Initial set:

```
server.read                 view server info and doctor report
files.read                  list/read/download files
files.write                 create/modify files
files.delete                delete/rename/move files
files.upload                upload files
snapshots.read              list/show/diff snapshots
snapshots.restore           restore snapshots
snapshots.prune             delete snapshots manually
deployments.read            view deployments
deployments.create          create/upload deployments (configs and JARs)
deployments.create.jar      permission required specifically for JAR content
deployments.approve         approve pending deployments
deployments.cancel          cancel in-progress deployments
deployments.rollback        trigger rollback
server.restart              request a restart outside of a deployment
console.read                view console stream
console.command             execute console commands
audit.read                  query the audit log
audit.export                export audit data
tokens.manage.self          manage own API tokens
tokens.manage.all           manage anyone's API tokens
webhooks.manage             manage webhooks
users.read                  view the effective role mapping (read-only)
```

Path scoping applies to `files.*`, `snapshots.*`, and `deployments.*`. A permission without path scope applies to the whole sandbox root, minus protected paths.

## 24.3 Audit Log Record Format

JSON Lines, one record per event, hash-chained:

```json
{
  "v": 1,
  "seq": 10423,
  "ts": "2026-01-15T09:30:12.345Z",
  "prev": "sha256:...",
  "hash": "sha256:...",
  "actor": { "id": "bob@example.com", "method": "cloudflare", "roles": ["developer"], "ip": "203.0.113.7" },
  "action": "files.write",
  "target": { "path": "MyRpgPlugin/config.yml", "revision": "sha256:..." },
  "result": "success",
  "request-id": "01J...",
  "deployment-id": null,
  "details": { "bytes": 2048, "snapshot": "snap_01J..." }
}
```

Rules: no file contents, no secrets; denied attempts are recorded (with the reason code); chain verification command (`deploymc audit verify`); rotation writes a signed-off "continuation" record linking to the previous file's final hash; clock anomalies are noted rather than silently corrected.

## 24.4 Snapshot Store Layout

```
.deploymc/
  history/
    format-version
    objects/ab/cdef...        content-addressed blobs (sha-256), optionally compressed
    index/
      files/<path-hash>.json  per-path ordered list of snapshot records
      snapshots/<id>.json     snapshot metadata
    tmp/
  staging/
  audit/
  deployments/<id>.json       persisted deployment transaction records
  data.lock                   single-instance lock
```

Snapshot record: `id`, `path`, `kind` (`file`, `jar`, `deleted`), `hash`, `size`, `mode`, `created-at`, `actor`, `reason` (`edit`, `delete`, `rename`, `deploy`, `restore`), `deployment-id`, `parent` (previous snapshot of the same path), `plugin-metadata` (name, version, for JARs).

Rules: blobs are immutable; writes are temp-then-rename; GC removes unreferenced blobs only after a grace period and never during an active deployment; `verify` re-hashes blobs; a single-instance lock prevents two servers sharing a data directory from corrupting it (clear error message).

## 24.5 Deployment State Machine (formal intent)

States: `CREATED`, `UPLOADING`, `VALIDATING`, `REJECTED`, `STAGED`, `AWAITING_APPROVAL`, `APPROVED`, `SNAPSHOTTING`, `APPLYING`, `RESTART_PENDING`, `RESTARTING`, `VERIFYING`, `SUCCEEDED`, `FAILED`, `ROLLING_BACK`, `ROLLED_BACK`, `ROLLBACK_FAILED`, `CANCELLED`, `SAFE_STATE`.

Rules:

- Every transition is persisted before side effects begin and carries a timestamp, actor (or `system`), and reason code.
- Transitions form a documented table; any other transition is a bug and raises an internal error.
- `RESTARTING` and `VERIFYING` span a JVM restart; the recovery routine on startup maps persisted `RESTARTING` to `VERIFYING`.
- `ROLLBACK_FAILED` and exceeded attempt counters lead to `SAFE_STATE`.
- A deployment record contains: id, idempotency key, requester, source (`ui`, `api`, `cli`, `action`), manifest (operations with hashes), validation report, snapshot ids, state history, verification results, and rollback details.
- Cancellation is only possible before `APPLYING`.
- A model-based test enumerates the transition table and checks invariants (no lost snapshots, no orphaned staging files, exactly one terminal state).

## 24.6 CLI Command Specification (initial)

```
deploymc [--profile NAME] [--url URL] [--output human|json] [--no-color] [--quiet] [--verbose] <command>

deploymc login            configure a profile (URL, token or Cloudflare service token)
deploymc doctor           diagnose configuration, connectivity, auth, capabilities
deploymc server info
deploymc files ls|cat|put|rm|mv|mkdir|diff <path...>
deploymc files sync <local-dir> <remote-dir> [--dry-run] [--delete]
deploymc deploy <file-or-bundle> [--restart now|countdown|none] [--wait] [--timeout 10m]
deploymc deploy status|list|cancel|rollback <id>
deploymc snapshots ls|show|diff|restore <path|id>
deploymc logs [--follow] [--level WARN] [--plugin NAME] [--filter EXPR] [--since 10m]
deploymc exec <command...>
deploymc audit query|export|verify
deploymc tokens create|list|revoke
deploymc webhooks list|add|remove|test
deploymc completion <shell>
deploymc version
```

Exit codes: `0` success; `1` general error; `2` usage error; `3` authentication failure; `4` permission denied; `5` not found; `6` conflict (stale revision); `7` validation failed; `8` deployment failed and rolled back; `9` deployment failed and rollback failed / safe state; `10` timeout; `11` network error; `12` server incompatible (API version). JSON output follows a documented schema; errors in JSON mode go to stdout as structured objects and to stderr as text summaries only when not quiet.

## 24.7 Platform SPI (interface sketches)

```java
public interface PlatformAdapter {
    PlatformInfo info();                    // software name/version, MC version, Java, OS
    PlatformCapabilities capabilities();
    Path pluginsDirectory();
    Path dataDirectory();
    PlatformScheduler scheduler();          // global, async, delayed; region-aware where applicable
    ConsoleBridge console();                // log subscription + command dispatch
    ServerController server();              // save, broadcast, shutdown, restart (mode-dependent)
    PluginInspector plugins();              // list loaded plugins, enable state, versions, load errors
    MessageSink messages();                 // send localized messages to console/players
    Logger logger();
}
```

Capability keys (initial): `log.capture`, `log.capture.structured`, `command.dispatch`, `command.output`, `server.save`, `server.shutdown`, `server.restart.script`, `plugins.enumerate`, `plugins.enable-state`, `plugins.load-errors`, `update-folder`, `scheduler.region`, `players.locale`, `players.broadcast`.

Each capability has: key, description, fallback behavior, and the tier-matrix entries where it is known to be present/absent. The API's `/server` response lists capability states so clients (UI, CLI) adapt.

## 24.8 Log Filter Expression Syntax

A small, documented, safe expression language shared by server, CLI, and UI:

```
level>=WARN
logger:MyRpgPlugin
plugin:"My Plugin"
thread:/Server thread/
text~"NullPointerException"          (substring)
text~/timeout \d+ms/                 (regex, with length/complexity limits)
since:10m   until:2026-01-15T09:00Z
NOT, AND, OR, parentheses
```

Requirements: formal grammar in the spec; fuzzed parser; regex evaluation protected against catastrophic backtracking (use a linear-time engine or strict limits); identical results across implementations verified by shared test vectors.

## 24.9 Documentation Site Structure

```
Getting started
  What is DeployMC / Concepts
  Install (Paper, Folia, Spigot, proxies, standalone)
  First start and local test mode
  Production setup with Cloudflare Tunnel + Access
Guides
  Administrator guide (config, RBAC, restart modes, backups, upgrades)
  Developer guide (editing, deploying, rollback, console)
  CI/CD guide (GitHub Action, CLI, tokens, service tokens)
  Windows guide / Docker guide / Pterodactyl guide / systemd guide
  Multi-server and proxy guide
  Security guide (threat model, hardening checklist)
Reference
  Configuration reference
  Permissions reference
  REST API reference / WebSocket reference / Webhooks reference
  Error codes
  CLI reference
  Java API (Javadoc)
  Filter expression syntax
  Compatibility matrix and per-version notes
Community
  Contributing / Translating / Governance / Security policy / Roadmap / Changelog
Troubleshooting & FAQ
```

Each page: front-matter with last-reviewed date and applicable versions; every page exists in English; translated pages show their review status.

## 24.10 REST Endpoint Inventory (initial)

```
GET    /api/v1/server
GET    /api/v1/server/doctor
GET    /api/v1/me
GET    /api/v1/files?path=&depth=&cursor=
GET    /api/v1/files/content?path=            (ETag, Range)
PUT    /api/v1/files/content?path=            (If-Match required for existing files)
POST   /api/v1/files                          (create file/dir)
POST   /api/v1/files/move
DELETE /api/v1/files?path=
POST   /api/v1/files/validate
POST   /api/v1/files/upload
GET    /api/v1/snapshots?path=&cursor=
GET    /api/v1/snapshots/{id}
GET    /api/v1/snapshots/{id}/content
GET    /api/v1/snapshots/diff?a=&b=
POST   /api/v1/snapshots/{id}/restore
POST   /api/v1/snapshots/prune
POST   /api/v1/deployments                    (Idempotency-Key)
PUT    /api/v1/deployments/{id}/artifacts/{name}
POST   /api/v1/deployments/{id}/commit
GET    /api/v1/deployments?state=&cursor=
GET    /api/v1/deployments/{id}
POST   /api/v1/deployments/{id}/approve
POST   /api/v1/deployments/{id}/cancel
POST   /api/v1/deployments/{id}/rollback
POST   /api/v1/server/restart
GET    /api/v1/console/logs?filter=&since=&limit=
POST   /api/v1/console/commands
GET    /api/v1/audit?filter=&cursor=
GET    /api/v1/audit/export
GET    /api/v1/audit/verify
GET    /api/v1/tokens
POST   /api/v1/tokens
DELETE /api/v1/tokens/{id}
GET    /api/v1/webhooks
POST   /api/v1/webhooks
DELETE /api/v1/webhooks/{id}
POST   /api/v1/webhooks/{id}/test
GET    /api/v1/webhooks/{id}/deliveries
GET    /healthz   /readyz                      (unauthenticated, minimal info)
WS     /api/v1/ws
```

## 24.11 WebSocket Message Types (initial)

Envelope: `{ "v": 1, "type": "...", "id": "...", "seq": 123, "ts": "...", "payload": { } }`.

Client → server: `hello`, `subscribe`, `unsubscribe`, `ping`, `console.command`, `resume`.

Server → client: `hello.ack`, `subscribed`, `error`, `pong`, `console.line`, `console.gap` (lines dropped), `deployment.update`, `file.changed`, `presence.update`, `server.state` (restarting, ready), `notice`.

Close codes (documented): normal, auth-failed, permission-lost, rate-limited, server-restarting, protocol-error, slow-consumer.

## 24.12 Error Code Catalog (initial)

Stable string codes, each with HTTP status, description, and localizable message key:

```
AUTH_REQUIRED, AUTH_INVALID_TOKEN, AUTH_EXPIRED, AUTH_CF_KEYS_UNAVAILABLE
PERMISSION_DENIED, PERMISSION_PATH_SCOPE
SANDBOX_TRAVERSAL, SANDBOX_PROTECTED, SANDBOX_INVALID_NAME, SANDBOX_SYMLINK
FILE_NOT_FOUND, FILE_EXISTS, FILE_TOO_LARGE, FILE_BINARY, FILE_LOCKED
REVISION_CONFLICT, PRECONDITION_REQUIRED
VALIDATION_FAILED, VALIDATION_YAML, VALIDATION_JSON, VALIDATION_TOML
SNAPSHOT_NOT_FOUND, SNAPSHOT_CORRUPT
DEPLOY_REJECTED, DEPLOY_CONFLICT, DEPLOY_INVALID_STATE, DEPLOY_VERIFY_FAILED,
DEPLOY_ROLLBACK_FAILED, DEPLOY_SAFE_STATE, DEPLOY_APPROVAL_REQUIRED, DEPLOY_SELF_APPROVAL
RESTART_UNSUPPORTED, RESTART_TOO_SOON
CONSOLE_DISABLED, COMMAND_DENIED, COMMAND_NEEDS_CONFIRMATION
RATE_LIMITED, PAYLOAD_TOO_LARGE, IDEMPOTENCY_MISMATCH
CAPABILITY_MISSING, PLATFORM_UNSUPPORTED
INTERNAL_ERROR
```

Rules: codes are never renamed in a released API major version; messages never include secrets or absolute host paths; every error includes `request-id`.

## 24.13 i18n Key Conventions

- Namespaced, dot-separated, lowercase: `files.tree.empty`, `deploy.state.verifying`, `error.SANDBOX_TRAVERSAL`.
- One concept per key; no sentence fragments assembled in code; placeholders named (`{plugin}`, `{count}`), with plural rules through ICU (`{count, plural, one {...} other {...}}`).
- Plugin message bundles mirror the same key namespaces where messages overlap with the UI.
- Each English string has a translator note when ambiguity is possible.

## 24.14 Web UI Screen Inventory

1. Sign-in / identity screen (Cloudflare mode shows identity; token mode shows token entry)
2. Dashboard: server status, platform/version/capabilities, active deployment, recent activity, warnings (for example non-loopback bind)
3. Files: explorer + editor + history panel
4. Deployments: list, new deployment (drag-and-drop), detail/timeline, approval inbox
5. Console: live log, filters, command input
6. Snapshots: global history browser with filters
7. Audit log: search, filters, export, chain verification status
8. Tokens and webhooks management
9. Settings (per-user: language, theme, editor preferences) and a read-only view of effective server configuration
10. Doctor / diagnostics page
11. About / licenses / version / links

Each screen specifies: permissions required, empty/loading/error states, keyboard shortcuts, localization namespace, and E2E test coverage.

---

# PART 25 — DETAILED TEST CATALOG

Use this catalog to derive test cases. It is a minimum. Each listed behavior needs at least one automated test, and security-relevant ones need negative tests.

## 25.1 Sandbox

- Resolves valid nested paths; rejects `..`, `.`/`..` mixes, encoded variants (`%2e%2e`, double-encoding), backslash separators on Linux, forward/backslash mixes on Windows, absolute paths, drive-letter paths, UNC paths, `\\?\` prefixes, NUL and control characters, extremely long paths, invalid surrogate pairs.
- Windows: reserved device names with and without extensions, trailing dot/space, alternate data streams, 8.3 short-name aliases of protected paths, case variations of protected paths, junction pointing outside the root.
- Linux/macOS: symlink to outside file, symlink to outside directory, symlink loop, symlink replaced between check and use (race), hard links, case-insensitive file system aliasing, NFC/NFD name equivalence.
- Protected paths remain protected for admins and for API tokens; renaming a file *into* a protected path is refused; moving a directory containing protected content is refused.
- Listing never reveals protected entries unless the policy says so, and never reveals absolute host paths.

## 25.2 File operations and snapshots

- Atomic write with induced crash; concurrent writes with conflicting revisions; write with stale `If-Match`; write without `If-Match` to an existing file is refused with `PRECONDITION_REQUIRED`.
- Snapshot created before overwrite/delete/rename/restore; deduplication of identical content; retention pruning respects in-use snapshots; GC grace period; corruption detection and repair report; store lock contention error message.
- Large files and size-limit boundaries (exactly at, one over); binary detection; encoding handling (UTF-8 with/without BOM, CRLF preservation).

## 25.3 Validators and diff

- YAML: duplicate keys, tabs, anchors/aliases (billion-laughs style) limits, multi-document files, very deep nesting, large scalars; precise error positions.
- JSON/TOML/properties: edge cases, comments where applicable, trailing commas policy.
- Diff: identical files, empty files, huge files (bounded result), CRLF vs LF, moved blocks, YAML semantic diff for key renames/reorders, JAR metadata diff for version bump/dependency change.

## 25.4 Auth and RBAC

- Cloudflare JWT: all cases in WP-3.2; JWKS fetch failure with and without cache; key rotation mid-run; stale-key hard limit; header present but JWT missing; email header spoofing without JWT; multiple AUD values.
- Tokens: expiry, revocation, scope narrowing, owner permission reduction after issuance, hashed storage verified (no plaintext on disk), brute-force lockout, timing-safe comparison.
- One-time mode: refuses non-loopback bind; expires; limits attempts; cannot be reused.
- RBAC: deny overrides allow; unknown identity gets default role; path scope evaluation via sandbox; wildcard semantics; config reload applying/revoking permissions during an open WebSocket (connection closed or downgraded with the documented close code). Tokens evaluate through their `owner` and can never exceed the owner's effective permissions; `local-onetime` uses the reserved identity `local-onetime` and is not admin-equivalent. A permission-revoked WebSocket closes with code `4403` and reason `rbac.permission-revoked`. An invalid reload puts the evaluator into deny-all mode rather than retaining a more permissive snapshot.

## 25.5 Deployment

- State-machine table-driven test; every injection point from WP-5.9; idempotency replay and mismatch; concurrent deployment attempts; cancellation windows.
- JAR validator: corrupt ZIP, zip-slip entry, zip bomb, missing descriptor, malformed YAML descriptor, huge entry counts, duplicate entries, mismatched main class, wrong-platform descriptor.
- Windows lock scenarios with a real loaded JAR; Linux in-place replacement refusal; update-folder success and collision cases.
- Rollback restores byte-identical files; rollback after partial apply; boot-loop counter persistence; safe-state entry and acknowledgment.
- Restart modes: each mode's success and failure messaging; `none` mode refusal; minimum interval enforcement.

## 25.6 Console and WebSocket

- Log capture attach/detach leaves logging intact; recursion guard; buffer bounds; filter correctness (shared vectors); command dispatch thread correctness (Folia); command policy allow/deny/confirm; rate limits; audit of commands.
- Slow consumer handling; reconnect with resume; malformed frames; oversized frames; Origin mismatch; permission revoked mid-stream; connection-count limits.

## 25.7 API contract and clients

- Every endpoint validated against OpenAPI in tests (request/response schema conformance); error responses conform to the Problem Details schema; pagination stability under concurrent modification; ETag semantics; idempotency; rate-limit headers; localization negotiation via `Accept-Language`.
- Clients: regenerate-and-diff check; conformance scenarios; retry/backoff behavior with fault-injecting proxy; Cloudflare service-token header handling; webhook signature verification samples tested in all languages.

## 25.8 Web UI

- Unit/component tests for editor integration wrappers, tree virtualization, diff rendering.
- E2E: sign-in, browse, edit-with-lint-error (blocked), edit-with-conflict, save, history/diff/restore, deploy JAR (success), deploy JAR (verification failure → rollback), console filter/command, language switch (en/ja/pseudo/RTL), keyboard-only flow, permission-restricted user views, server-restart reconnect.
- Visual regression on key screens (tolerant thresholds); axe accessibility checks.

## 25.9 Compatibility matrix scenarios

For each matrix entry, the shared scenario runs: start server → plugin enables → `/server` capability report matches expectations → file write/read → snapshot/restore → config deployment → JAR deployment of a tiny **test plugin** (built in-repo, with a version-bumped second build) → restart → verification passes → deliberately broken test plugin deploys → verification fails → rollback succeeds → console stream receives expected lines → shutdown leaves no leaked threads. Scenario steps that a platform cannot support are marked *expected-unsupported* in the matrix definition rather than silently skipped.

## 25.10 Upgrade, migration, and reliability

- Config/data migrations from every released version; downgrade refusal with a clear message; backup creation verified.
- Repeated enable/disable cycles; plugin reload hostility (another plugin calling `reload`); disk-full and read-only file system behavior; port conflict behavior; clock changes.

---

# PART 26 — COMMUNITY OPERATIONS AND ISSUE BACKLOG

## 26.1 Seed Issues

Create real, well-described issues (never filler) for the active milestone and a curated set of future ones. Each issue has: context, acceptance criteria, hints, relevant files, labels, and a milestone. Examples of categories to seed:

- `good first issue`: add a translation for a UI namespace; improve an error message; add a test vector to the traversal corpus; document a config key; add a compat report template field.
- `help wanted`: verify the plugin on a specific Minecraft version and report results; review Japanese/Chinese/Korean translations; test on Pterodactyl/Docker setups; write a guide for a specific hosting panel.
- `area:compat`: one issue per platform/version bucket to expand the matrix.
- `research`: mod-loader feasibility ADRs.

Maintain a rolling backlog; close or re-scope stale issues with an explanation.

## 26.2 Discussions Plan

- Pinned welcome post (project purpose, how to ask questions, how to report compat results).
- Q&A category used for support; answer questions yourself where the answer is certain and cite documentation; convert recurring answers into FAQ entries.
- Ideas category feeds the roadmap; record decisions back in the thread.
- Translators category for language coordination.
- Compatibility reports category with a template.

## 26.3 Release Communication

- Each release has human-written notes: highlights, upgrade notes, breaking changes, security fixes, compatibility changes, translation updates, contributors.
- Draft (not auto-post) announcement texts in English and Japanese for the owner to review before posting anywhere. Follow each community's self-promotion rules. Never post on the owner's behalf without approval.

## 26.4 Contributor Experience

- A `docs/development/first-contribution.md` walkthrough (clone, build, test, make a change, open a PR) with expected timing.
- Maintainer response-time targets documented in `SUPPORT.md`.
- Recognize contributors in release notes and `AUTHORS.md` (with consent).
- Provide a devcontainer so contributors on any OS can start quickly.

## 26.5 Achievement-Aligned Rhythm (Legitimate)

Keep a sustainable development rhythm: small focused PRs merged regularly, helpful Discussion answers, quick resolution of genuine issues, and visible progress through milestones and releases. Do not time or batch GitHub activity artificially. Record, in the report to the owner, which real activities happened that may relate to GitHub achievements, **without** promising any badge.

---

# PART 27 — RISK REGISTER AND OPEN RESEARCH QUESTIONS

Maintain `docs/development/risks.md` seeded with the following and update it each milestone. For each risk: likelihood, impact, mitigation, owner, status.

| ID | Risk | Mitigation direction |
|---|---|---|
| R1 | JAR deploy abused for code execution by a compromised account | Cloudflare Access policies (MFA), RBAC, approval workflow, hash allowlists, audit, rate limits, documentation of limits |
| R2 | Cloudflare misconfiguration exposes the port | Loopback default, JWT verification (not header trust), shared-secret header option, doctor checks, verification checklist |
| R3 | Classloader/library conflicts with other plugins | Minimal dependencies, mandatory relocation, JAR verification task, matrix tests with conflicting plugins |
| R4 | Legacy versions behave differently | Feature detection, capability reporting, tiered claims, harness evidence |
| R5 | Windows file locking breaks deployment | Staged replacement, update folder, CI on Windows, explicit refusal paths |
| R6 | Restart not possible in some hosting setups | Restart modes, clear messaging, docs for supervisors |
| R7 | Rollback itself fails or loops | Attempt limits, safe state, out-of-band recovery doc, fault-injection tests |
| R8 | Plugin JAR too large because of embedded UI | Bundle budget, lazy chunks, possibly a slim distribution variant (ADR) |
| R9 | Folia threading violations | Scheduler abstraction, thread-assertions in tests, Folia in Tier 1 |
| R10 | Server software/API changes break compatibility | Scheduled new-version detection, matrix automation |
| R11 | Translation quality or staleness | Status tracking, labeling, native review, CI staleness warnings |
| R12 | Scope too large for one maintainer | Strict phase gating, small PRs, tracking docs, contributor onboarding, honest roadmap adjustments |
| R13 | Supply-chain compromise | Pinned actions, dependency verification, signed releases, SBOM, provenance |
| R14 | Spec/code/README drift | Generated artifacts, consistency checks (Part 20.5), CI drift gates |
| R15 | Licensing conflicts with Bukkit/Spigot/Paper or bundled libraries | License ADR, dependency license scanning in CI |
| R16 | Hosting providers/panels restrict features | Standalone mode, capability reporting, guides per panel |
| R17 | Name/trademark conflicts (Minecraft, Mojang, Cloudflare marks) | Naming check, non-affiliation disclaimers in README, no misuse of logos |

Open research questions to resolve through ADRs or experiments (record answers with evidence):

1. Exact update-folder semantics per platform/version and whether they apply to JARs under new file names.
2. Reliable "server fully started" detection across versions and platforms.
3. Best mechanism for attributing log lines/exceptions to a plugin across versions.
4. Whether a safe, optional, experimental plugin hot-reload is worth offering at all.
5. Feasibility of a restart-time swap helper that works without external supervisors on each platform.
6. Minimum viable Java 8-compatible HTTP/WebSocket stack with acceptable size and security.
7. Comment-preserving YAML editing library options and licensing.
8. Whether Cloudflare group claims are available in the JWT for the owner's Access plan, and how to map them.
9. Practical size of the embedded Web UI and whether a "slim" and "full" distribution is warranted.
10. What other plugin-management tools users already rely on and how DeployMC should coexist with them.

---

# PART 28 — OPERATOR PROMPT TEMPLATES (FOR THE OWNER)

These are ready-to-paste prompts the owner can give to the AI agent in each session. The agent should also recognize them as valid task entry points. (The templates are written in English because the agent works in English; the agent replies to the owner in Japanese.)

## 28.1 First-ever kickoff

```
Read DeployMC_Complete_Master_Development_Specification.md completely.
Then follow Part 21 (Initial Execution Instructions).
First inspect the repository and my environment. Do not write code yet.
Present a project-wide implementation plan and list all decisions you need from me.
Reply to me in Japanese.
```

## 28.2 Start or resume a work session

```
Resume DeployMC development.
Follow Part 20.2 (Session Start Procedure): read AGENTS.md and docs/development/HANDOFF.md,
check git status, branches, open PRs, and CI. Then continue with the next work package
in the active milestone, state your plan first, and proceed in small PRs.
Reply to me in Japanese.
```

## 28.3 Execute a specific work package

```
Execute work package WP-<id> from Part 23 of the specification.
Plan first. Use a properly named branch, multiple meaningful Conventional Commits, and one
focused PR (split if it grows too large). Include tests, documentation, and localization updates.
Run the tests and report the exact commands and results. Update the tracking documents.
```

## 28.4 Verify a time-sensitive fact

```
Verify <topic> against current official documentation (cite the source URL and access date),
record the evidence in the repository, and tell me whether the specification needs to change.
```

## 28.5 Compatibility investigation

```
Investigate DeployMC on Minecraft <version> / <platform>.
Run the compatibility harness, analyze failures, fix incompatibilities through feature detection
(no NMS), update docs/compatibility/* and compat-status.md, and propose the correct support tier
based on evidence only.
```

## 28.6 Security review pass

```
Perform a security review of <module/WP> against specification/security/threat-model.md.
List findings by severity, add failing tests first, then fix them in separate small PRs under
security/* branches. Do not disclose exploit details in public commit messages for unreleased issues;
follow SECURITY.md.
```

## 28.7 Documentation and README pass

```
Audit README.md and docs against the real current implementation. Remove or relabel anything
not implemented. Update the support matrix, screenshots, and examples. Then update the Japanese
translation and mark its review status honestly.
```

## 28.8 Translation task

```
Add or update the <language> translation for <README/docs/UI namespace/plugin messages>.
Follow Part 11 and TRANSLATING.md: keep keys stable, preserve placeholders, label machine-translated
content, run tools/i18n/check, and update the translation status files.
```

## 28.9 Bug fix

```
Investigate this bug: <description/logs>.
Reproduce it with a failing test first, fix it with a fix/* branch and a focused PR,
add a regression test, update CHANGELOG-worthy commit messages, and assess whether the
threat model or docs need updates.
```

## 28.10 Release

```
Prepare release <version>. Verify the milestone's acceptance criteria and the v1-checklist items
that apply. Run the full CI and the compatibility matrix. Generate the changelog from commits,
draft release notes (English and Japanese). Tell me exactly which credentials/approvals you need
from me for publishing, and do not publish anything externally without my confirmation.
```

## 28.11 Review pass over the repository

```
Do a repository health review: consistency invariants from Part 20.5, stale docs, dead code,
dependency updates, flaky tests, CI duration, issue/PR hygiene. Report findings in Japanese
and propose small refactor/docs/test PRs.
```

---

# PART 29 — RELEASE CHECKLIST

For every release (pre-release and stable):

1. Milestone issues closed or moved with explanations.
2. CI green on `main`, including Windows jobs and the compatibility matrix for the declared tiers.
3. `CHANGELOG.md` updated from Conventional Commits and curated for humans.
4. Version bumped consistently across all artifacts (plugin, API module, SDKs, CLI, spec versions as applicable); config/data migration tested.
5. Support matrix regenerated; README badges/tables verified against reality.
6. Documentation site version tagged; screenshots regenerated; translations' status updated.
7. Security checks: dependency audit, CodeQL, secret scan, SBOM generated, artifacts signed, provenance attached, checksums published.
8. Artifacts built reproducibly on CI (not on a developer machine); JAR verification task passes (relocation, bytecode level, size budget).
9. Manual smoke test on at least one real server per Tier 1 platform (log results in the release PR).
10. Release notes written (English and Japanese); upgrade notes and breaking changes at the top.
11. Tag and GitHub Release created via the release workflow; assets verified by downloading and checking signatures.
12. External publication (Maven Central, npm, PyPI, crates.io, Modrinth, Hangar, SpigotMC, Marketplace, container registry) performed only with the owner's approval and credentials; each publication verified afterwards.
13. Post-release: reopen the "next version" milestone, update `project-status.md`, `HANDOFF.md`, and monitor issues/Discussions closely for the first days.

---

# PART 30 — ENVIRONMENT AND ASSUMPTIONS TO CONFIRM WITH THE OWNER

Before major decisions, confirm these with the owner (in Japanese) and record the answers in `docs/development/environment.md`:

1. Operating system and shell of the development machine (Windows/macOS/Linux; PowerShell/bash/WSL) — all commands you provide must fit it.
2. Which tools are installed (JDK versions, Gradle, Node/pnpm, Rust, Python, Docker, `gh`), and whether Docker is available for the compatibility harness.
3. GitHub account/organization name, repository visibility (public from the start is assumed), authentication method for `gh`, and whether branch protection can be configured.
4. License preference after the trade-off presentation.
5. The real-world hosting setup of the owner's server (panel? Docker? systemd? Windows service?) and the Minecraft versions actually in use, to prioritize the matrix.
6. Cloudflare plan and domain availability (affects how much of the Cloudflare guide can be verified live).
7. Namespace for Java packages and Maven coordinates (requires a domain or a verified GitHub namespace for Maven Central).
8. Which distribution channels the owner has accounts for.
9. Which languages beyond Japanese the owner wants first, and whether native reviewers are available.
10. How much time per week the owner expects the project to receive, so the roadmap communicates realistic progress.
11. Whether to use AI-attribution trailers in commits and which exact tool identity applies.
12. Whether the Discord/Discussions community channel should be created now.

Do not guess any of the above.

---

# PART 31 — NORMATIVE LANGUAGE, PRECEDENCE RULES, AND GLOSSARY

This part exists to remove ambiguity. When any other part of this specification seems to conflict with another, apply this part.

## 31.1 Normative Keywords

The words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used as in RFC 2119/8174 when written in capitals. Lowercase "must", "should", and "may" in this document carry the same intent but are less formal. Sentences beginning with "Never" and "Do not" are **MUST NOT** rules.

## 31.2 Precedence of Goals

When goals conflict, resolve them in this order (highest first):

1. **Security and data safety** — never lose or corrupt user files; never allow access outside the sandbox; never fail open.
2. **Correctness and honesty** — behave as documented; never claim what is not implemented, tested, or verified.
3. **Recoverability** — every destructive action must be reversible or explicitly confirmed.
4. **Compatibility** — supported tiers keep working; breaking changes are deliberate and documented.
5. **Operability** — clear diagnostics, clear errors, safe defaults.
6. **User experience** — speed, polish, convenience.
7. **Feature breadth** — more features.
8. **Code volume and schedule** — never a reason to lower items 1–5.

Examples: if a convenient feature requires an unauthenticated endpoint, the feature loses. If a legacy Minecraft version cannot be supported safely, it is documented as unsupported rather than supported with hidden risk. If a deadline is at risk, the scope shrinks, not the tests.

## 31.3 Precedence of Documents

1. `specification/` machine-readable and normative files (OpenAPI, AsyncAPI, JSON Schema, state-machine and format documents) define behavior of released interfaces.
2. ADRs record decisions and their rationale; a newer Accepted ADR supersedes an older one (mark the old one Superseded).
3. This master specification defines intent, process, and starting designs.
4. Source code comments and README text never override the above; if they disagree, fix the disagreement in a PR.

If this master specification and a `specification/` file disagree after implementation has begun, the `specification/` file wins and this master specification is amended through Part 39.

## 31.4 Glossary

- **Sandbox root**: the directory tree DeployMC may expose; by default the server's `plugins/` directory.
- **Protected path**: a path inside the sandbox root that the API can never modify (for example DeployMC's own data and config).
- **Identity**: the authenticated actor of a request (human, service token, or API token owner).
- **Role / Permission / Path scope**: RBAC building blocks as defined in 9.5 and 24.2.
- **Snapshot**: an immutable stored version of a file (or a deletion marker) with metadata.
- **Deployment**: a persisted transaction that applies one or more file operations (configs and JARs), optionally restarts the server, verifies health, and rolls back on failure.
- **Bundle**: a deployment manifest plus artifacts applied atomically as one deployment.
- **Staging**: the area where validated deployment artifacts wait before application.
- **Restart mode**: how DeployMC asks the server process to restart (`shutdown-only`, `spigot-restart-script`, `none`).
- **Supervisor**: an external process manager (systemd, Docker restart policy, Pterodactyl, a loop script, a Windows service wrapper) that restarts the server after shutdown.
- **Safe state**: a condition in which DeployMC stays reachable but refuses automatic restarts until an authorized human acknowledges.
- **Platform adapter**: the module connecting Core to a specific server platform.
- **Capability**: a documented runtime feature of a platform (24.7) that may be present or absent.
- **Tier 1/2/3**: support levels defined in 3.1.
- **Compat harness**: the infrastructure that starts real servers in CI and runs shared scenarios (3.6, 25.9).
- **Provisioned token**: a token record placed by the host administrator in a host-controlled location (WP-1.9).
- **Cloudflare Access / Tunnel**: Cloudflare's Zero Trust identity-aware proxy and the outbound-only tunnel connector (`cloudflared`).
- **Service token**: a non-human Cloudflare Access credential for automation.
- **ADR**: Architecture Decision Record.
- **WP**: Work Package (Part 23).
- **Handoff**: the end-of-session note in `docs/development/HANDOFF.md`.

---

# PART 32 — STARTING HYPOTHESES AND DEFAULT NUMERIC LIMITS

## 32.1 Why This Part Exists

The agent will face many decisions where the owner has not stated a preference. To avoid stalling, this part provides **starting hypotheses** (to be validated or replaced through ADRs and measurement) and **initial numeric defaults** (to be tuned through measurement). They are not claims of truth.

## 32.2 Technology Starting Hypotheses

| Decision | Starting hypothesis | Validation required |
|---|---|---|
| Build | Gradle Kotlin DSL, version catalog, toolchains | Verify Gradle compatibility with `--release 8` using the chosen JDK |
| Java baseline | Java 8 bytecode for `core`, `api`, `transport`, `platform:*` | Confirm on a real legacy server in the matrix |
| HTTP | JDK `com.sun.net.httpserver` behind an internal interface | Spike per WP-1.3; reject if threading/limits are inadequate |
| WebSocket | A compact, relocatable Java 8-compatible library, or a small in-house implementation if audits and fuzzing justify it | Spike per WP-1.3; never ship an untested home-grown protocol parser |
| JSON | A small, Java 8-compatible library, relocated | Size and CVE history |
| YAML | A mature parser with duplicate-key and alias limits; separate comment-preserving strategy for programmatic edits | ADR with a prototype |
| JWT/JWKS | JDK crypto (`Signature`) plus minimal parsing, or a mature library, relocated | Test vectors; algorithm allow-list |
| Diff | Established algorithm (Myers) via a library or own implementation | Performance on large files |
| Compression for snapshots | None or zstd/deflate decided by benchmark | Benchmark per Part 14.3; avoid native dependencies |
| Web UI | React + Vite + TypeScript + Tailwind; CodeMirror 6 vs Monaco decided by bundle size and diff-editor quality | Bundle budget measurement |
| i18n (UI) | ICU MessageFormat via a mainstream i18n library | RTL and pluralization checks |
| CLI | Rust, `clap`, `reqwest` (rustls), `tokio` only if needed | Static-binary builds on all targets |
| Docs site | VitePress or Docusaurus | Multi-language and versioning support |
| API codegen | OpenAPI generator tooling with templates per language | Output quality review |
| License | Apache-2.0 or MIT unless the license analysis finds a reason otherwise | Owner decision, ADR-0013 |
| Release automation | release-please or git-cliff | Works with the monorepo layout |
| Commit lint | commitlint with a config enforcing Part 12.3 types and scopes | CI |

## 32.3 Initial Numeric Defaults (Tunable)

| Limit | Default | Notes |
|---|---|---|
| Max text file size for editing in the UI | 10 MB | Larger files are download-only |
| Max upload size (single artifact) | 64 MB | JARs of large plugins fit; configurable |
| Max listing entries per response | 5,000 | Paginated beyond |
| Max path length | 1,024 characters; each segment 255 | Windows-aware |
| Max directory depth traversed in a single tree request | 8 | Lazy-load deeper |
| Snapshot retention | 50 versions per file / 90 days / 2 GiB total | Whichever limit triggers first, except never delete the newest 3 |
| Audit retention | 365 days | Rotation at 50 MB per file |
| Console ring buffer | 5,000 lines and a byte cap (for example 8 MiB) | Whichever first |
| WebSocket per-connection outbound queue | 1,000 messages or 2 MiB | Exceeding triggers coalesce/drop + `console.gap` |
| WebSocket connections total | 50 | Per identity default 5 |
| Request body limit (non-upload endpoints) | 1 MiB | |
| Request timeout (non-streaming) | 30 s | |
| Idle WebSocket timeout without heartbeat | 60 s | Heartbeat every 20 s |
| Rate limit | 300 requests/min per identity, 60/min for auth failures per IP | Burst allowance documented |
| Auth failure lockout (token mode) | 5 failures → 5 min | Exponential beyond |
| JWKS cache | refresh when older than 1 h, hard maximum staleness 24 h | Fail closed after |
| JWT clock skew | 60 s | |
| One-time token | 15 min TTL, 5 attempts | Loopback only |
| API token default TTL | 90 days, max 365 | |
| Deployment verification timeout | 180 s | Configurable per deployment up to a maximum |
| Restart countdown | 30 s | |
| Minimum interval between restarts | 5 min | |
| Auto-rollback attempts | 2 | Then safe state |
| YAML alias expansion | Max 100 expanded nodes per alias use and 1 M total nodes | Reject beyond |
| YAML nesting depth | 100 | |
| ZIP entries in a JAR upload | 100,000 | |
| ZIP uncompressed total | 1 GiB, with compression-ratio guard (for example 100:1 per entry) | Zip-bomb defense |
| Diff computation | Max 2 MB per side for line diff; larger returns `too-large` | |
| Regex filter | Max 256 chars; linear-time engine or step budget | |
| Webhook delivery | 5 attempts with exponential backoff up to 1 h; 10 s timeout | |
| Webhook payload | Max 256 KiB | |

Every default must appear in the configuration reference with a rationale. Measure before changing them.

## 32.4 Starting Hypothesis for the First Platform Slice

The very first end-to-end vertical slice (target: end of Phase 2 plus minimal auth) is:

Paper latest on Java 21 → DeployMC loads → provisioned token → `GET /api/v1/server` → write a config file through the API with a stale-revision conflict test → snapshot visible → restore → all running in CI on Linux and Windows.

Build vertical slices like this early, so integration risk surfaces before the codebase grows.

---

# PART 33 — ALGORITHM SPECIFICATIONS (REFERENCE PSEUDOCODE)

These describe intended behavior precisely enough to implement and test. Implementations may differ in structure but must preserve the semantics. Each algorithm needs a dedicated test class.

## 33.1 Sandbox Path Resolution

```
resolve(requested: String, mode: READ|WRITE|CREATE) -> SandboxPath
  1. Reject if requested is null, empty (unless it means root explicitly), contains NUL or control chars,
     exceeds max length, or is not valid Unicode.
  2. Normalize Unicode to NFC for comparison keys (keep original bytes for the actual file name
     only if the file exists with that exact name; otherwise create using NFC).
  3. Treat both '/' and '\' as separators on all platforms; split into segments.
  4. Reject segments equal to '..' ; drop '.' and empty segments; reject segments that are
     Windows-invalid on any platform (reserved names, trailing dot/space, ':' characters, invalid chars)
     — apply Windows rules everywhere so a repository behaves identically across hosts.
  5. Join with the root's real path (root real path computed once at startup, re-validated periodically).
  6. For each existing ancestor from root to target: lstat; if it is a symlink, junction, or reparse point
     -> reject (follow-symlinks=false policy) or resolve-and-recheck containment (if policy allows).
  7. realTarget = real path of the deepest existing ancestor + remaining segments.
     Assert realTarget startsWith rootReal (component-wise, not string prefix).
  8. Compare the normalized, case-folded form against protected patterns (always case-insensitive).
  9. Return SandboxPath holding: relative normalized path, absolute real path, root identity token.
  Callers perform the operation through APIs that take SandboxPath only.
  After opening the file, re-check (fstat / file key) that the opened object lives under the root,
  to narrow the TOCTOU window (open with NOFOLLOW where available).
```

## 33.2 Atomic File Write With Revision Check

```
write(path, content, expectedRevision):
  acquire per-path lock (in-process) ; acquire OS file lock if available
  current = stat/hash(path) if exists
  if exists and expectedRevision is absent -> PRECONDITION_REQUIRED
  if exists and current.revision != expectedRevision -> REVISION_CONFLICT (return current revision)
  validate(content, path)          # server-side validators
  snapshot(path)                   # before overwrite, if exists
  tmp = createTemp(sameDirectory)  # same file system for atomic move
  write(tmp, content); fsync(tmp)
  atomicMove(tmp, path, REPLACE_EXISTING)  ; fsync(directory) where supported
  newRevision = hash(content)
  audit(write, path, newRevision)
  publish file.changed event
  release locks
  return newRevision
```

Revision = SHA-256 of content, prefixed with algorithm; ETag is derived from it. Failure at any step leaves the previous content intact; temp files are cleaned up on startup.

## 33.3 Cloudflare Access JWT Verification

```
authenticate(request):
  token = request.header("Cf-Access-Jwt-Assertion")
  if absent -> AUTH_REQUIRED (ignore any email header entirely)
  parts = split(token, '.') ; require exactly 3 ; base64url-decode header and payload with size limits
  header.alg must be in allowedAlgorithms (RS256 initially; verify against Cloudflare docs) ; header.kid required
  key = jwksCache.get(kid)
        if missing and cache not refreshed within minRefreshInterval -> refresh (single-flight)
        if still missing -> AUTH_INVALID_TOKEN
  verify signature with key and exact algorithm
  claims = parse payload
  require iss == "https://" + configuredTeamDomain
  require aud contains configuredAudience (or one of the configured audiences)
  require exp > now - skew ; nbf <= now + skew (if present) ; iat <= now + skew (if present)
  identity:
     if claims has email -> type=user, id=email (lowercased per policy), subject=claims.sub
     else if service-token claim (for example common_name) -> type=service, id=that value
     else -> AUTH_INVALID_TOKEN
  return Identity(verified=true, method=cloudflare, claims=allowedSubset)

JWKS handling:
  refresh from https://<team>/cdn-cgi/access/certs  (verify URL in current docs)
  honor Cache-Control within [minRefresh, maxStale]; keep last good set; on fetch failure keep serving
  last good set until maxStale then fail closed; log (rate-limited) without leaking details
```

All steps run in constant-bounded time and memory irrespective of input.

## 33.4 RBAC Evaluation

```
authorize(identity, permission, path?) -> Decision(allow|deny, reason)
  roles = userRoles(identity) or [default-role]
  if roles empty -> deny(NO_ROLE)
  for each role: collect grants and path rules
  if no role grants permission -> deny(NOT_GRANTED)
  if path given:
      resolve path via sandbox (never evaluate raw strings)
      if matches any deny rule of any applicable role -> deny(PATH_DENIED)   # deny wins
      if some applicable role grants permission and its allow rules match (or has no allow rules) -> allow
      else -> deny(PATH_SCOPE)
  tokens: effective = intersection(tokenScopes, ownerEffective)
  record decision reason for explain-mode and audit (denials always audited)
```

Normative WP-3.4 detail is in `specification/security/rbac.md`. Stable reasons are `NO_ROLE`, `NOT_GRANTED`, `TOKEN_SCOPE_EXCEEDED`, `PATH_DENIED`, and `PATH_SCOPE`. An absent `paths.allow` matches all sandbox paths; an empty `paths.allow` matches none. Deny rules from every assigned role win. WP-3.4 retains reasons for explain mode and the future audit writer; it does not itself write audit records.

## 33.5 Deployment Execution and Recovery

```
execute(d):
  VALIDATING: validate all artifacts and manifest; any error -> REJECTED
  STAGED: copy artifacts to staging; verify hashes
  if approval.required -> AWAITING_APPROVAL ; wait ; requester != approver ; approval not expired
  SNAPSHOTTING: snapshot every target path (including "did not exist" markers)
  APPLYING: for op in ops:
        config/file ops: atomic write/delete
        JAR ops: place via platform strategy (update folder | restart-time swap | direct for unloaded)
     persist progress after each op
  if restart requested:
        RESTART_PENDING: notify players, countdown, save-all
        persist state=RESTARTING ; request restart ; (process ends)
  else VERIFYING in-process (config-only deployments may verify without restart)
  after restart (startup recovery):
        for each deployment in {RESTARTING, VERIFYING, APPLYING, ROLLING_BACK}: recover(d)
  VERIFYING: run built-in and custom checks until success or timeout
  success -> SUCCEEDED ; failure -> FAILED -> ROLLING_BACK (if automatic and attempts < max)
  ROLLING_BACK: restore snapshots ; restart if needed ; verify ; ROLLED_BACK or ROLLBACK_FAILED
  if attempts exceeded or ROLLBACK_FAILED -> SAFE_STATE

recover(d) by persisted state:
  APPLYING          -> if all ops applied (by hash) continue ; else roll back partial application
  RESTARTING        -> mark VERIFYING and continue
  VERIFYING         -> continue verification with remaining timeout (timeout is absolute, persisted)
  ROLLING_BACK      -> continue rollback idempotently
  anything terminal -> nothing
```

Every step is idempotent: re-running it after a crash must not corrupt state. A boot-loop counter is incremented at each recovery and reset on SUCCEEDED/ROLLED_BACK.

## 33.6 Snapshot Garbage Collection

```
gc():
  require no active deployment ; take store lock
  reachable = all blob hashes referenced by snapshot records after retention pruning
  candidates = blobs not in reachable and older than gracePeriod (default 24 h)
  delete candidates (log counts) ; never delete on any read error — abort and report
  retention pruning: for each path keep newest N, newest 3 always, all referenced by a non-terminal deployment,
  and all within minimum age; apply age and total-size limits oldest first
```

## 33.7 Log Attribution (Heuristic)

```
attribute(record):
  1. If the logger name matches a loaded plugin's name or main-class package -> that plugin (high confidence)
  2. Else if the message begins with a bracketed plugin name "[Name]" matching a loaded plugin -> medium
  3. Else if the throwable's stack frames include classes loaded by a plugin's classloader -> medium (first such frame)
  4. Else unattributed
  Output includes confidence level. UI/CLI label attribution as heuristic. Verification checks for the deployed
  plugin use high/medium-confidence matches plus explicit enable-failure events where the platform provides them.
```

## 33.8 Rate Limiting and Backpressure

```
rate limiter: token bucket per (identity, endpoint-class) and per (ip, auth-failure) ; refill continuously ;
  respond 429 with Retry-After ; counters in memory with bounded key count (LRU eviction).
WebSocket outbound:
  per-connection bounded queue ; producer never blocks server threads ;
  if queue > high-water: coalesce (console lines) or drop oldest ; send console.gap{count, fromSeq, toSeq} ;
  if queue stays > limit for N seconds -> close with slow-consumer code.
```

## 33.9 YAML and Archive Safety Limits

```
yaml: parse with limits (depth, node count, alias expansion, scalar length, document count) ; on limit -> VALIDATION_YAML
zip : iterate entries without extracting to disk during validation ; enforce entry count, total size, ratio,
      reject entries with absolute paths, '..', drive letters, backslash escapes, symlink entries,
      names differing only by case/Unicode normalization (collision) ; read descriptor from memory with size cap
```

---

# PART 34 — REPOSITORY TEMPLATES AND DRAFT FILES

The agent must create these files in Phase 0, adapting them to reality. They are drafts that encode the rules above in the form each tool expects. Keep them short, accurate, and consistent with this specification.

## 34.1 `AGENTS.md` (Condensed Operating Rules for Any AI Agent)

```markdown
# AGENTS.md — DeployMC

Read this file and docs/development/HANDOFF.md before doing anything.

## Mission
Build DeployMC: a secure, multi-version, multi-platform, multi-language deployment and
collaboration tool for Minecraft server plugin directories, with a public API.
The full specification is DeployMC_Complete_Master_Development_Specification.md (in docs/development/).

## Non-negotiable rules
1. Never commit to main. Branch -> commits -> PR -> CI green -> merge.
2. Conventional Commits in English: <type>(<scope>): <description>. Allowed types and scopes: see docs/development/commit-convention.md.
3. If an AI tool contributed to a commit, add the accurate Co-authored-by trailer as the last line. Never add trailers for anyone who did not contribute.
4. Never claim a test passed unless you ran it. Report exact commands and results.
5. Never claim something is implemented, supported, or released unless verified. README must match reality.
6. Security > correctness > recoverability > compatibility > UX > features.
7. No unauthenticated endpoint except /healthz and /readyz. No bypass flags. Fail closed.
8. All file access goes through the sandbox resolver. No raw java.io.File/Path from request input.
9. No NMS or server internals. Feature-detect, report capabilities.
10. Java code in core/api/transport/platform must compile with --release 8.
11. User-facing strings go through i18n catalogs. No hard-coded UI or plugin message strings.
12. No secrets, real tokens, real hostnames in the repo. Use obviously fake examples.
13. Verify time-sensitive facts (versions, Cloudflare behavior, GitHub criteria) against official docs and record evidence.
14. Update docs/development/{project-status,implementation-status,compat-status,i18n-status,HANDOFF}.md at the end of each session.
15. Talk to the owner in Japanese. Everything in the repository is English except localized content.

## Commands
(Fill with the real, verified commands for: build all, test all, per-language build/test, lint, format,
 spec lint, i18n check, run compat harness locally, build web, build plugin JAR, run E2E.)

## Where things live
(Keep a short directory map consistent with the specification Part 6.)

## Definition of done
Implemented, tested (and run), documented, localized (if user-facing), compat impact assessed,
security considered, reviewed, CI green, merged via PR.
```

## 34.2 Pull Request Template

```markdown
## Summary
<!-- What does this PR do, in 1-3 sentences? -->

## Motivation
<!-- Why? Link issues: Closes #123 -->

## Implementation details
<!-- Key design points, trade-offs, alternatives considered -->

## Work package / milestone
<!-- e.g. WP-2.1, milestone 0.3.0 -->

## Testing performed
<!-- Exact commands and results. Platforms/versions exercised (Tier 1 matrix entries?). -->

## Documentation and localization
- [ ] Docs updated
- [ ] User-facing strings added to catalogs (English) and translation status updated
- [ ] CHANGELOG-worthy commit messages

## Compatibility impact
<!-- Platforms, Minecraft versions, Java versions, API/config/data format changes -->

## Security considerations
<!-- Threat model impact, new inputs, new permissions, new dependencies -->

## Checklist
- [ ] No secrets or accidental files
- [ ] No debug code or TODO-only implementations
- [ ] Tests added/updated and actually run
- [ ] Specification files updated first (if behavior changed)
- [ ] Screenshots attached (UI changes)
```

## 34.3 Issue Forms (Required Fields)

- **Bug report**: DeployMC version; server software and version; Minecraft version; Java version and vendor; OS; hosting setup (panel/Docker/systemd/bare); auth mode; relevant config (secrets removed); output of `deploymc doctor`; steps to reproduce; expected vs actual; logs (with a reminder to remove secrets).
- **Compatibility report**: platform, versions, Java, result for each scenario in 25.9 (pass/fail/not tested), logs, and whether the reporter permits adding them to the matrix notes.
- **Feature request**: problem statement, proposed behavior, alternatives, security implications, willingness to contribute.
- **Documentation issue**, **Translation issue** (language, scope, reviewer availability).
- **Security**: a template that only redirects to private vulnerability reporting.

## 34.4 ADR Template

```markdown
# ADR-NNNN: <Title>

- Status: Proposed | Accepted | Superseded by ADR-XXXX | Rejected
- Date: YYYY-MM-DD
- Deciders: <names/roles>

## Context
## Problem
## Considered alternatives
(Each with pros, cons, evidence, measurements)
## Decision
## Consequences
(Positive, negative, follow-up work, compatibility/security impact)
## Verification
(How we will know this was right; links to experiments/benchmarks)
```

## 34.5 README Skeleton

```markdown
<p align="center"><img src="assets/logo.svg" width="120" alt="DeployMC logo"></p>
<h1 align="center">DeployMC</h1>
<p align="center">Secure, collaborative plugin deployment for Minecraft servers - from your browser or CI.</p>

<p align="center">
  <b>English</b> · <a href="docs/i18n/README.ja.md">日本語</a> · <a href="docs/i18n/README.zh-CN.md">简体中文</a> · ...
</p>

<!-- Badges: CI, release, license, Minecraft versions (generated), Java, downloads, docs, scorecard -->

> **Status: <pre-alpha|alpha|beta|stable>.** See "What works today" below.
> **Security:** deploying a JAR is code execution on your server. Read the security model first.

## What works today / what is planned   (table with honest status per feature)
## Features
## Why DeployMC
## How it works (diagram)
## Compatibility (support matrix, tiers)
## Installation
## Quick start (local test mode)
## Production setup (Cloudflare Tunnel + Access)
## Configuration and roles
## Using the Web UI
## API, SDKs, CLI, GitHub Action (examples)
## Snapshots and rollback
## Windows notes
## Troubleshooting (deploymc doctor)
## Security model and reporting
## Performance
## Roadmap
## Contributing and translating
## Community
## License
## Acknowledgements and non-affiliation (not affiliated with Mojang, Microsoft, or Cloudflare)
```

## 34.6 `SECURITY.md` Skeleton

- Supported versions table (updated each release)
- How to report: GitHub private vulnerability reporting (primary), optional security email
- What to include; what to expect (acknowledgement within a documented number of days, coordinated disclosure timeline)
- Scope (in/out of scope: social engineering, Cloudflare account compromise, host compromise)
- Safe-harbor statement for good-faith research (reviewed by the owner)
- Link to the threat model and hardening checklist

## 34.7 Commit Lint and Hooks

- `commitlint.config.*` enforcing: allowed types (12.3), allowed scopes (12.3), subject case and length, body line length, footer format, `BREAKING CHANGE` rules.
- A PR-title check that applies the same rules (squash merges use the PR title).
- Optional local hook through a documented tool, never mandatory for contributors without the tool; CI is the authority.

## 34.8 CI Workflow Outline

```
ci.yml           : change detection -> jobs per component (java, web, cli, python, spec, docs, i18n)
                   OS matrix (ubuntu, windows; macos for cli) ; caches ; artifacts (JAR, reports)
compat.yml       : matrix harness (PR smoke = Tier 1 latest ; nightly = full matrix) ; uploads logs
security.yml     : CodeQL, dependency review, secret scan, scheduled fuzz, cargo-audit/deny, osv scan
release.yml      : tag-driven ; build on CI ; sign ; SBOM ; provenance ; draft GitHub release ; publish jobs gated by environments requiring owner approval
docs.yml         : build site, check links, deploy on main/tag
i18n.yml         : structural check (fail) and staleness report (warn)
scorecard.yml    : OpenSSF Scorecard
labeler / stale  : conservative automation; never auto-close genuine bug reports without a human check
```

All workflows use least-privilege `permissions`, pinned action SHAs, and `concurrency` groups. Secrets are available only to release/publish jobs that require environment approval.

## 34.9 Labels (Initial Taxonomy)

`type:bug`, `type:feature`, `type:docs`, `type:refactor`, `type:test`, `type:chore`, `type:security`, `area:core`, `area:sandbox`, `area:snapshot`, `area:deploy`, `area:auth`, `area:api`, `area:console`, `area:web`, `area:cli`, `area:sdk`, `area:action`, `area:docs`, `area:i18n`, `area:ci`, `platform:paper`, `platform:folia`, `platform:spigot`, `platform:velocity`, `platform:bungee`, `platform:standalone`, `tier:1`, `tier:2`, `tier:3`, `os:windows`, `os:linux`, `os:macos`, `good first issue`, `help wanted`, `needs-triage`, `needs-info`, `blocked`, `breaking`, `wontfix`, `duplicate`, `research`, `lang:ja`, `lang:zh-CN`, and so on per translation.

---

# PART 35 — TEST PLUGINS, FIXTURES, AND TEST DATA

## 35.1 In-Repo Test Plugins

Under `tests/fixtures/plugins/`, build small real plugins (not mocks) used by the compat harness and deployment tests. Each is built by Gradle from source in the repository, for each relevant platform family:

- **`deploymc-test-a`** (v1.0.0 and v1.1.0): trivially enables, logs a recognizable line on enable, exposes a config file; version-bumped build used to prove JAR replacement. Includes a `/dmctest` command that prints the version.
- **`deploymc-test-broken-enable`**: throws during `onEnable`, used to prove verification failure and rollback.
- **`deploymc-test-broken-descriptor`**: invalid `plugin.yml`, used to prove validation rejection.
- **`deploymc-test-slow-enable`**: delays enable beyond the verification timeout, used to prove timeout behavior.
- **`deploymc-test-dependency`**: declares a missing hard dependency, used to prove dependency warnings.
- **`deploymc-test-conflicting-libs`**: bundles an **unrelocated** common library (the same one DeployMC might use) at a different version, used to prove DeployMC's relocation prevents classloader conflicts.
- **`deploymc-test-log-noise`**: emits logs at all levels at configurable rates, used for console/backpressure tests.
- **`deploymc-test-wrong-platform`**: a descriptor for another platform family, used for validator tests.

Fixtures must be minimal, documented, and clearly marked as test-only; they must never be included in release artifacts.

## 35.2 Data Fixtures

- Traversal payload corpus (versioned file with categories and expected outcomes per OS).
- JWT test vectors: valid, expired, nbf-in-future, wrong aud, wrong iss, alg=none, alg confusion, tampered, unknown kid, rotated key; generated by a script with throwaway keys; a mock JWKS server.
- Malformed archives: zip-slip, zip bomb (small compressed, large expanded, generated at test time rather than committed), duplicate entries, encrypted entries, symlink entries.
- YAML bombs and deep-nesting documents (generated at test time).
- Log corpora: multi-line stack traces, ANSI sequences, very long lines, unusual Unicode.
- Config corpora: valid, invalid, legacy-version configs for migration tests.
- Large directory trees (generated) for listing and performance tests.
- Cross-implementation vectors for the filter expression language (shared by Java, TypeScript, Rust).

Large or generated fixtures must be produced at test time or stored in a way that does not bloat the repository (never commit multi-megabyte binaries without need; use Git LFS only after an ADR).

## 35.3 Test Environment Hygiene

- Tests must never touch the real home directory, real server data, or network resources beyond the test scope.
- Compat tests accept the Minecraft EULA only inside disposable CI/test directories, with a clear comment; the EULA file is never committed.
- Ports are allocated dynamically; tests never assume 8080.
- Time and randomness are injectable; tests are deterministic or seeded with logged seeds.
- Flaky tests are quarantined with an issue and a deadline, never ignored silently.

---

# PART 36 — OBSERVABILITY AND SELF-DIAGNOSTICS OF DEPLOYMC

## 36.1 `deploymc doctor` Checks (Initial)

Each check reports `PASS`, `WARN`, or `FAIL` with a localized explanation and a remediation hint:

1. Platform, Minecraft version, Java version (vendor, version), OS, DeployMC version, API version.
2. Capability table (24.7) with explanations of any missing capability.
3. Bind address: loopback or not; warns loudly if non-loopback; checks the port is not already in use by another process.
4. Auth mode validity: Cloudflare team domain/audience syntax, JWKS fetch reachability (with timing), cache age, algorithm support, clock skew estimate (compare system clock with the HTTP `Date` header from the JWKS endpoint).
5. Whether requests appear to arrive from the tunnel only (heuristic: warn if a direct non-loopback connection was ever observed).
6. Sandbox: root exists and is writable, protected paths present, symlink policy, free disk space on data directory.
7. Snapshot store: lock status, verify summary, size vs retention limits.
8. Deployment: active/unfinished transactions, safe-state status, restart mode viability (detect whether a supervisor/restart script is plausibly configured; otherwise warn).
9. Windows-specific: whether loaded JAR replacement strategy is available.
10. Console capture status and ring-buffer usage.
11. Config validation results and warnings (unknown keys, deprecated keys).
12. Localization: selected language and missing-key counts for loaded catalogs.
13. Update availability check (opt-in only, privacy-respecting; disabled by default).

Output formats: human text, JSON (stable schema), and an in-UI page. Doctor output must never include secrets and has a "redacted copy-paste for bug reports" mode.

## 36.2 Internal Logging

- A dedicated logger namespace; configurable level; request-ID in every request log line; no secrets or file contents in logs; structured JSON option for operators.
- Audit log is separate from diagnostic logs and has different retention and integrity rules.

## 36.3 Metrics (Optional, Opt-In)

- A minimal internal metrics registry (counters, gauges, histograms) with an optional endpoint (`/api/v1/metrics`, authenticated, Prometheus text format) controlled by a permission (`server.read` or a dedicated `metrics.read`).
- Initial metrics: request counts/latency per endpoint class, auth failures by reason, active WebSocket connections, console buffer usage, snapshot store size, deployment counts by outcome, JWKS refresh outcomes, executor queue depths, dropped console lines.
- No high-cardinality labels (no per-user or per-path labels by default).

## 36.4 Self-Protection

- DeployMC must degrade itself before degrading the server: when internal queues are saturated, shed load (429/disconnect) instead of blocking server threads.
- Memory-bounded everywhere: no unbounded buffers for uploads, logs, diffs, WebSocket queues, or webhook retries.
- A watchdog thread detects stalled executors and records a diagnostic (never kills the server).

---

# PART 37 — OPERATIONS GUIDES (CONTENT OUTLINES)

Each guide below must be written from **verified** facts (official docs consulted at writing time, with links and access dates) and, where possible, tested on a real setup. Mark any untested guide as "community-reported" or "unverified".

## 37.1 Cloudflare Tunnel + Access Guide

1. Concepts: Tunnel is an outbound-only connection; Access is the identity-aware policy layer; DeployMC verifies the Access JWT itself.
2. Prerequisites: a domain on Cloudflare, a Zero Trust account/plan, `cloudflared` installable on the Minecraft host (or a sidecar container).
3. Create the tunnel and map a hostname to `http://127.0.0.1:<port>`.
4. Create the Access application for that hostname; policies (allowed emails/groups/IdP, MFA requirement recommended); session duration.
5. Copy the team domain and the application AUD tag into `config.yml`.
6. Create service tokens for CI; add a Service Auth policy; store the token ID/secret in the CI secret store.
7. Confirm the Access JWT header arrives and DeployMC accepts it (`deploymc doctor`).
8. **Verification checklist (must pass before declaring production-ready):**
   - The DeployMC port is bound to `127.0.0.1` only (`ss`/`netstat` evidence).
   - From another machine, a direct connection to `<host>:<port>` fails.
   - A request to the public hostname without Access authentication is blocked by Cloudflare.
   - A request that reaches DeployMC with a forged `Cf-Access-Authenticated-User-Email` header but no valid JWT is rejected.
   - A JWT for a different Access application (wrong AUD) is rejected.
   - Removing a user from the Access policy locks them out within the documented session lifetime; DeployMC RBAC removal takes effect on reload.
   - Service-token authentication works and maps to the intended role.
9. Common mistakes (policy `Bypass`/`Everyone`, forgetting AUD, exposing the port via Docker `-p 0.0.0.0`, running `cloudflared` on a different host with a non-loopback origin and no network isolation).
10. Rotating keys, rotating service tokens, and incident response (revoke a user, revoke a token, enter safe state).

## 37.2 Docker Guide

- Image-agnostic guidance for popular Minecraft server images: mount `plugins/` and DeployMC data, **do not publish the DeployMC port to the host**; place `cloudflared` in the same Docker network and point it at the server container's internal address, binding inside the container to a non-loopback address only when the network is isolated (explicit `allow-non-loopback` with documented reasoning).
- Restart behavior: Docker restart policy as the supervisor; `shutdown-only` mode; stop timeout settings so `save-all` completes.
- File ownership/UID issues and volume permissions.
- docker-compose example with `cloudflared` sidecar (fake credentials).

## 37.3 Pterodactyl / Panel Guide

- How panels manage the process and file system; restart/stop semantics; port allocation and exposure; how to avoid exposing the DeployMC port; panel file manager coexistence (both can edit files — conflict warning and revision handling); using the panel's startup command or egg variables for `cloudflared` if applicable; limitations when the plugin directory is on a read-only or remote volume.

## 37.4 Windows Guide

- Running the server as a service (via a wrapper) and as a console; restart supervision with a loop script; **JAR replacement strategy** and its caveats; antivirus exclusions advice (without recommending disabling security); path-length issues; installing `cloudflared` as a Windows service; PowerShell command examples for the doctor and CLI.

## 37.5 Linux systemd Guide

- Example unit files for the server (with `Restart=`) and `cloudflared`; user/permission isolation; `ProtectSystem`/`ReadWritePaths` hardening so the server user can only write what it needs; log handling; verifying the bind address.

## 37.6 Proxy Networks Guide (Velocity/BungeeCord)

- Which instance runs DeployMC; how plugin directories differ between proxy and backend servers; deploying to several servers (multiple DeployMC instances now, fleet view post-1.0); restart ordering considerations (proxy vs backends); avoid taking the whole network down accidentally (confirmation prompts and role restrictions for proxy restarts).

## 37.7 Alternatives to Cloudflare (Documented, Not Primary)

- Tailscale/WireGuard, an authenticating reverse proxy (OIDC proxy), SSH port forwarding for single administrators. State plainly which security properties DeployMC can verify in each setup (for example `token` mode only), and that `local-onetime` is for local testing only. Do not weaken Cloudflare-mode defaults to accommodate them.

## 37.8 Hardening Checklist (Administrators)

- Loopback bind; Access MFA; least-privilege roles; short token TTLs; approvals enabled for JAR deployments; hash allowlist for production; separate staging server; regular snapshot verification; audit log forwarding; backups of worlds are a separate system; keep DeployMC updated; subscribe to security advisories.

---

# PART 38 — UX, ACCESSIBILITY, AND COPYWRITING STANDARDS

## 38.1 UX Principles

- **Safe by default, fast when safe.** Dangerous actions (delete, restore over newer content, JAR deploy, restart) show a precise preview of what will change and require confirmation proportional to risk.
- **Never lose work.** Unsaved edits survive reloads where feasible (local draft recovery); conflicts show both versions; restores are reversible.
- **Show state honestly.** Progress, queued, failed, rolled back, and unknown are distinct states; never show success before verification.
- **Explain errors.** Every error shows what happened, why, and what to do next, plus the request ID; machine codes are available in a "details" disclosure.
- **Respect permissions.** Hide or disable what the user cannot do and explain why on hover/focus; never leave the user to discover a 403 by trial.
- **Dense but calm.** Developer-tool density, minimal decoration, consistent layout, predictable keyboard shortcuts.

## 38.2 Accessibility Requirements

- WCAG 2.2 AA target for all core workflows; full keyboard operability including the tree, editor toolbar, dialogs, and drag-and-drop alternatives (a file-picker for every drag-and-drop interaction).
- Visible focus indicators; logical focus order; focus trapping and restoration in dialogs.
- Color is never the only signal (log levels and diff changes also use icons/text).
- Live regions announce deployment progress and errors appropriately without being noisy.
- Respect `prefers-reduced-motion` and `prefers-color-scheme`; allow manual override.
- Text scaling to 200% without loss; responsive layouts down to tablet width; read-only mobile view for monitoring is a goal, full editing on mobile is optional.

## 38.3 Copywriting Standards (English Source Strings)

- Plain, concise, active voice; no jargon without explanation; consistent terminology from the glossary (Part 31.4).
- Buttons are verbs ("Deploy", "Restore version"); errors are sentences; no blame ("The file changed since you opened it" rather than "You made an error").
- Avoid idioms and culture-specific humor that hinder translation; avoid concatenation; give translators context notes.
- Security-sensitive strings (for example the JAR-deploy warning) are reviewed carefully and kept unambiguous.

## 38.4 Localization Style Guides

Create `docs/i18n/style/<lang>.md` for each active language containing: tone/politeness level, terminology decisions (for example how to render "deployment", "snapshot", "rollback", "sandbox", "console", "plugin"), handling of untranslated product and Minecraft terms, number/date conventions, and examples of good and bad translations.

- **Japanese** (priority): choose and document a consistent politeness level (です・ます調 recommended for UI and docs), katakana rendering conventions for technical terms (consistent long-vowel treatment), half-width/full-width rules for punctuation and alphanumerics, and a glossary table agreed with the owner.
- **Chinese**: separate Simplified and Traditional (and regional terminology differences); do not auto-convert one into the other.
- **RTL languages**: mirrored layouts, bidirectional text handling for file paths and code (paths remain LTR), icons that must/must not mirror.

---

# PART 39 — SPECIFICATION CHANGE CONTROL AND ERRATA

## 39.1 Changing This Specification

The master specification will contain mistakes and will age. The agent MAY and SHOULD propose corrections when evidence demands it, following this process:

1. Record the problem (what the spec says, what reality or analysis shows, evidence/link).
2. Propose the change in a `spec/` branch PR that edits the master specification (kept in `docs/development/`) and, if behavior changes, the relevant `specification/` file and ADR.
3. Add an entry to the errata log below (and to `docs/development/spec-errata.md` in the repository).
4. Notify the owner in Japanese with the reason and impact. Changes that weaken a security requirement, remove a deliverable, or change the legal/licensing stance require explicit owner approval.

The agent MUST NOT silently ignore a part of the specification it considers wrong.

## 39.2 Errata Log (Seed)

| ID | Date | Change | Reason |
|---|---|---|---|
| E-001 | (initial) | Added WP-1.9 minimal authentication; removed the Phase 2 "temporary authentication stub"; `/api/v1/server` is authenticated from 0.2.0 | Original draft exposed an unauthenticated endpoint in Phase 1, conflicting with 9.3 ("no unauthenticated mode") |
| E-002 | 2026-11-25 | Added the normative WP-3.4 RBAC contract and reconciled the closed permission registry, absent versus empty `paths.allow`, token owner mapping, `local-onetime` RBAC identity, invalid-reload deny-all, WebSocket close `4403`, and doctor `rbac` check | Issue #59 analysis found conflicts between 24.2/33.4, `config.schema.json`, and `token-records.md`; all changes tighten or clarify authorization and add no bypass |
| E-003 | 2026-11-26 | Clarified that `ExplainResult.reason` is a stable denial reason and is JSON `null` for an allow decision; the allow case is not a sixth reason | Issue #59 implementation analysis found that the required OpenAPI enum had no valid value for an allowed explain decision; this preserves the closed reason table and adds no bypass |
| E-004 | 2026-11-26 | Removed stale temporary no-RBAC and admin-equivalent wording from README, default configuration comments, OpenAPI, configuration schema, observability, and token-record text | Issue #59 CI Paper smoke exposed that the harness fixture still assumed implicit admin access after RBAC landed; reconciling text with the merged WP-3.4 contract is a tightening and adds no bypass |

New errata append here and in the repository file.

## 39.3 Consistency Audit (Required Each Milestone)

At every milestone, the agent runs the following audit and records results in `project-status.md`:

- Every `MUST`/`MUST NOT` in this specification is either implemented and tested, or listed as an open gap with an issue.
- Every endpoint in the code appears in `openapi.yaml` and vice versa (automated).
- Every config key in code appears in `config.schema.json` and the docs (automated).
- Every permission and error code used in code appears in the catalogs (automated).
- README claims map to rows in the implementation-status table.
- Support-matrix claims map to the latest CI results.
- Translation catalogs structurally match English.
- ADR statuses match reality.

---

# PART 40 — WORK PACKAGE DEPENDENCY GRAPH AND SEQUENCING

## 40.1 Critical Path

```
WP-0.x (foundation, docs, CI, i18n infra)
   -> WP-1.1 SPI -> WP-1.2 Bukkit adapter -> WP-1.3 transport -> WP-1.9 minimal auth -> WP-1.8 harness
   -> WP-2.1 sandbox -> WP-2.2 file ops -> WP-2.3 snapshots -> WP-2.6 file API
   -> WP-3.1..3.6 full auth/RBAC/audit/protections
   -> WP-4.x Web UI (can start after WP-2.6 with the generated client; localization from the start)
   -> WP-5.x deployment pipeline (needs 2.x, 3.x; UI parts need 4.x)
   -> WP-6.x console (log capture can start after 1.2; UI after 4.1)
   -> WP-7.x API consolidation + SDKs + CLI + Action (needs stable 2.x-6.x APIs)
   -> WP-8.x platform expansion (8.1 Folia can start early in parallel once 1.2 and the scheduler abstraction exist)
   -> WP-9.x hardening -> RCs -> 1.0.0
```

## 40.2 Parallelizable Tracks (When Multiple Contributors or Sessions Exist)

- Track A (backend core): WP-1.x → 2.x → 3.x → 5.x
- Track B (UI): WP-4.x (after the API client exists), then 5.8 and 6.5
- Track C (compat and platforms): harness (1.8) → 8.1 Folia early → 8.3 legacy stepping → 8.6/8.7 proxies
- Track D (docs and localization): continuous from Phase 0; guides follow features
- Track E (security): threat model early, tests alongside each feature, review in WP-9.1
- Track F (API ecosystem): spec work continuous; SDK/CLI/Action after WP-7.1

## 40.3 Early Risk-Reduction Spikes (Do These Early)

1. **Transport spike** (WP-1.3): the riskiest dependency decision.
2. **Windows JAR replacement spike**: prove a viable strategy on a real Windows server before designing Phase 5 in detail.
3. **Update-folder semantics spike** per platform.
4. **Log capture spike** across the oldest and newest targeted versions.
5. **Folia scheduling spike**: confirm which operations must move to which scheduler.
6. **Embedded UI size spike**: measure editor bundle size inside a JAR to decide Monaco vs CodeMirror and the slim/full question.
7. **Restart behavior spike** with common supervisors (systemd, Docker, a loop script, Pterodactyl).

Record each spike's findings in `docs/research/spikes/` and update ADRs.

## 40.4 Suggested First 30 Pull Requests (Phase 0 and Phase 1 Start)

1. `docs: add repository bootstrap and editor configuration`
2. `docs: add project vision, goals, and non-goals`
3. `docs: add competitive analysis and naming check`
4. `docs: add license analysis and ADR-0013 (after owner decision)`
5. `docs: add architecture overview and platform SPI design`
6. `docs: add threat model draft and security policy`
7. `docs: add contribution guide, code of conduct, support, governance`
8. `docs: add AGENTS.md and development tracking documents`
9. `build: initialize Gradle multi-project with Java 8 release target`
10. `build(web): initialize Vite React TypeScript app with tests`
11. `build(cli): initialize Rust workspace with tests`
12. `build(sdk-py): initialize Python package with tests`
13. `ci: add build and test workflow with OS matrix`
14. `ci: add commit lint, PR title lint, and spec lint`
15. `ci: add CodeQL, dependency review, and scorecard`
16. `chore(github): add issue forms, PR template, labels, CODEOWNERS, Dependabot`
17. `spec(api): add OpenAPI skeleton with server resource and error model`
18. `spec(ws): add AsyncAPI envelope`
19. `i18n: add translation tooling and status generation`
20. `docs: add Japanese README`
21. `feat(core): add platform SPI and capabilities model`
22. `feat(platform-bukkit): add plugin entry point and lifecycle`
23. `build(plugin): add shadow jar with relocation verification`
24. `feat(config): add config loading, validation, and migration framework`
25. `feat(i18n): add plugin message bundle runtime`
26. `spec(research): add transport comparison spikes (ADR-0003)`
27. `feat(transport): add embedded HTTP server lifecycle`
28. `feat(auth): add provisioned token authentication`
29. `feat(api): add server info endpoint`
30. `ci(compat): add Paper latest smoke test`

Adjust as reality dictates, but keep PRs this small and sequenced so that `main` is always green and buildable.

---

# PART 41 — QUALITY GATES AND INITIAL TARGETS

Record these in `docs/development/quality.md` and adjust only with evidence.

| Area | Gate |
|---|---|
| Core logic coverage (sandbox, rbac, auth, deploy state machine, snapshot) | Line coverage ≥ 90% and branch coverage ≥ 85% for security-critical packages; mutation testing on sandbox and RBAC where feasible |
| Other Java modules | ≥ 80% line coverage |
| Web UI | Unit coverage ≥ 70%; all core workflows covered by E2E |
| CLI / SDKs | ≥ 80% line coverage plus conformance suite |
| Static analysis | Zero new high-severity findings; warnings treated as errors where practical |
| Bundle sizes | Web UI gzip budget set after the size spike and enforced; plugin JAR size reported per PR with a regression threshold |
| Startup overhead | Plugin enable time budget measured and enforced (initial target: under 1 second excluding optional network checks) |
| Idle overhead | CPU and memory budget measured on a reference server profile and documented |
| API stability | No breaking changes to released API versions (CI breaking-change check) |
| Docs | Zero broken links; every public config key/permission/error code documented |
| i18n | Zero structural errors; stale/missing tracked and reported |
| Flakiness | Retry-free green rate on `main` ≥ 98% over rolling 30 runs; flaky tests quarantined with issues |

Coverage numbers are **signals**, not goals: do not write meaningless tests to inflate them.

---

# FINAL PROJECT DIRECTIVE

DeployMC is not a small file-upload plugin.

It is intended to become a complete, secure, extensible, multi-version, multi-platform, multi-language deployment and collaboration infrastructure for Minecraft server development teams, with a first-class public API.

The final product must combine:

- A hardened sandboxed file system
- Safe JAR deployment with automatic rollback
- Snapshots, diff, and restore
- Real-time console and log filtering
- Cloudflare Access-based identity, RBAC, and audit
- A documented, versioned, spec-first public API with SDKs, a CLI, and a GitHub Action
- Honest, CI-verified compatibility across many Minecraft versions and server platforms
- A polished, accessible Web UI
- Complete documentation and a high-quality README in multiple languages
- A professional open-source workflow: Conventional Commits, small reviewed PRs, milestones, releases, and community building

The first milestone is only the foundation. Continue development milestone by milestone. Never forget the final vision when implementing individual features. Never declare the project complete until the v1.0.0 acceptance criteria have been met.

Begin with Part 21: inspect the repository first, then present your project-wide implementation plan to the owner in Japanese before writing code.
