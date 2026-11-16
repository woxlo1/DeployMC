# Roadmap

This roadmap describes the intended order of work and the milestone version that would mark each
phase complete. It is **not a schedule** and contains no delivery dates. A milestone is complete only
when its acceptance criteria are met and verified; features listed here are not promises.

The normative detail lives in the
[master development specification](docs/development/DeployMC_Complete_Master_Development_Specification.md).
Current implementation status is tracked in [`docs/development/`](docs/development/).

## Current state: Phase 0 — Project foundation (0.1.0)

Phase 0 is in progress. Verified today:

- Gradle multi-project skeleton targeting Java 8, with JUnit 5, Spotless, and a bytecode check.
- Web UI skeleton with React, TypeScript, Vite, ESLint, Prettier, and Vitest.
- Rust CLI skeleton and Python SDK skeleton with their own lint and test gates.
- Message-catalog checker, generated translation status, and an AI-assisted Japanese README.
- Core project documents in this repository.

Not yet done in Phase 0: CI workflows, issue forms and community automation, architecture documents
and ADRs, specification skeletons, and the tracking and handoff documents.

## Phase 0 — Project foundation (0.1.0)

Identity, governance, research, architecture, tooling, localization infrastructure, and the first
specification skeletons. The repository becomes understandable, buildable, and honest about what does
not exist.

## Phase 1 — Core foundation and embedded server (0.2.0)

Platform SPI and capabilities, a Bukkit-family adapter, an embedded HTTP server bound to loopback,
minimal token authentication so every endpoint is authenticated from the first release, configuration
loading and validation, the plugin i18n runtime, the shading and relocation pipeline, and a
compatibility harness that starts a real Paper server in CI.

## Phase 2 — Sandboxed files, snapshots, and file API (0.3.0)

Sandbox path resolver with traversal, symlink, and Windows hardening; atomic file operations with
ETags and limits; a content-addressed snapshot store with restore, retention, and diff; file and
snapshot REST endpoints with contract tests; validators for structured configuration files.

## Phase 3 — Authentication, RBAC, and audit (0.4.0)

Cloudflare Access JWT verification with JWKS rotation, token and one-time local modes, API tokens,
role-based access with path scopes, a hash-chained audit log, rate limiting, and the complete threat
model with security test suites.

## Phase 4 — Web UI foundation (0.5.0)

The embedded React application: identity display, file tree, editor with server-authoritative
validation, conflict handling, snapshot and diff UI, full localization with pseudo-locale and RTL
support, accessibility, themes, and Playwright end-to-end tests.

## Phase 5 — Deployment pipeline, restart, and rollback (0.6.0)

JAR validation, staging, a persistent deployment state machine, restart modes, health verification,
automatic rollback, boot-loop protection, crash recovery, and fault-injection tests at every state.

## Phase 6 — Console and log filtering (0.7.0)

Log capture with platform fallbacks, bounded buffers and backpressure, an AsyncAPI WebSocket stream, a
console UI with filters, policy-controlled command execution, stack-trace grouping, and ANSI-safe
rendering.

## Phase 7 — Public API, SDKs, CLI, and CI integration (0.8.0)

Frozen API v1 for implemented features, error-code catalog, webhooks with signatures, the public Java
API with compatibility gating, generated TypeScript and Java clients, a complete Rust CLI, and a
GitHub Action with cross-language conformance tests.

## Phase 8 — Platform and version expansion (0.9.0)

Folia, Spigot/CraftBukkit, legacy Minecraft versions where evidence supports them, Velocity,
BungeeCord/Waterfall, standalone mode, Paper-fork verification, and an evidence-based compatibility
matrix with support tiers. Research-only evaluation of Sponge, Fabric, Forge, and NeoForge.

## Phase 9 — Hardening, performance, and documentation (0.10.0 to 0.99.x)

Security review and fixes, performance work against measured budgets, long-running soak tests,
versioned documentation, completed translations with native reviews, migration tests from every 0.x,
and public release candidates.

## Phase 10 — DeployMC 1.0.0

All v1.0.0 acceptance criteria are met and verified, artifacts are signed with an SBOM and
provenance, and declared distribution channels are authorized by the owner.

## Phase 11 — After 1.0.0

Additional platforms justified by research, fleet management, enterprise identity integrations,
configuration promotion between environments, plugin update discovery, editor integrations, metrics
export, more translations, and a long-term support policy.

## Cross-cutting commitments

- **Security and data safety** take priority over feature breadth. Deployment is code execution, and
  the project says so plainly.
- **Compatibility claims** require evidence from a real server in the compatibility matrix. Tiers are
  assigned from test results, never from assumptions.
- **Localization** is built in from the start. English is canonical; Japanese is the first maintained
  translation; other languages follow contributor availability.
- **Honesty** about status is a release requirement. The README and status documents describe what
  works, not what is planned.
