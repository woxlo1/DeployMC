# Architecture Overview

> **Status: design baseline (2026-11-17), implementation notes added 2026-11-18.** This document
> describes the intended architecture and the boundaries that later implementation must respect.
> The Java modules now contain value objects, capability reporting, SPI interfaces, a Bukkit
> adapter, and an embedded Netty transport. The sandbox, snapshot store, deployment pipeline,
> authentication, application routes, and Web UI features do not exist yet.

## Purpose

DeployMC is a single Minecraft plugin JAR that provides a narrow, authenticated,
auditable layer over a server's plugin directory and deployment lifecycle. The
architecture is shaped by four constraints:

1. **The plugin runs inside someone else's server process.** It must never block the
   server main thread or Folia region threads, must not leak threads or file handles
   across enable/disable cycles, and must not depend on server internals.
2. **Remote file access and JAR deployment are dangerous.** Every operation goes through
   a sandbox, authorization, and an audit log. There is no unauthenticated mode.
3. **The public API is the product.** The Web UI, CLI, SDKs, and GitHub Action use the
   same versioned HTTP/WebSocket API. No client gets a private shortcut.
4. **Compatibility claims require evidence.** Platform-specific behavior is isolated
   behind a SPI, reported as capabilities, and tested by a real-server matrix.

The normative behavior lives in the
[master development specification](../development/DeployMC_Complete_Master_Development_Specification.md).
This document explains how the pieces fit together.

## System context

```
Developer browser / CLI / CI (GitHub Action) / third-party client
                       |
                       | HTTPS / WSS
                       v
          Cloudflare Access (identity, policies, service tokens)
                       |
                       v
          Cloudflare Tunnel (cloudflared) -> 127.0.0.1:8080
                       |
                       v
+---------------------- DeployMC plugin (single JAR) ----------------------+
|                                                                          |
|  transport: HTTP + WebSocket                                             |
|       |                                                                  |
|       v                                                                  |
|  authentication -> authorization (RBAC, path scopes) -> audit            |
|       |                                                                  |
|       v                                                                  |
|  application services (use cases, transactions, idempotency)             |
|       |                                                                  |
|       +--> sandbox (files)   +--> snapshot store   +--> deployment       |
|       +--> console           +--> configuration    +--> events/webhooks  |
|       |                                                                  |
|       v                                                                  |
|  platform SPI -> Bukkit/Paper | Folia | Velocity | Bungee | Standalone  |
+--------------------------------------------------------------------------+
                       |
                       v
        plugins/ directory        .deploymc/ data directory
```

The Cloudflare hop is the default production topology, not a hard requirement. Token
authentication exists for CI and for deployments that do not use Cloudflare. The plugin
binds to loopback by default in every mode.

## Runtime components

| Component | Responsibility | Current state |
|---|---|---|
| `transport` | HTTP/WebSocket listeners, request parsing, limits, request IDs, upgrade handling | Netty 4.1.141.Final server behind internal interfaces; no registered product routes |
| `core` | Platform-independent domain and application services | `SemanticVersion`, capability reporting, SPI interfaces |
| `api` | Small, stable Java API for other plugins; shared public value objects | `DeployMCService`, `ServerInfo`, `PlatformInfo`, `CapabilityState` |
| `platform/*` | Adapters that translate SPI calls to one server platform | Version/config value objects only |
| `plugin` | Shadow JAR aggregation, plugin descriptors, wiring | Relocated Shadow JAR with automated packaging verification and CI size report; no server load |
| `web` | React single-page UI served by the plugin | Buildable skeleton |
| `cli` | Rust command-line client over the public API | Buildable skeleton |
| `sdk/*` | Generated and hand-polished clients | Python skeleton; TypeScript and Java planned |
| `actions/*` | GitHub Action wrapper over the CLI or HTTP API | Not started |

Core services are planned as separate packages rather than separate Gradle modules:

| Package (planned) | Responsibility |
|---|---|
| `sandbox` | Path resolution, jail enforcement, protected paths, bounded file operations |
| `snapshot` | Content-addressed history, retention, diff, restore, integrity verification |
| `deploy` | Staging, validation, persisted state machine, restart orchestration, rollback |
| `auth` | Cloudflare JWT verification, token store, one-time local tokens, sessions |
| `rbac` | Roles, permissions, path scopes, explain mode |
| `audit` | Append-only hash-chained audit log, query, export |
| `console` | Bounded log ring buffer, filtering, command dispatch policy |
| `config` | Schema, validation, migration, safe hot reload (see [configuration reference](../configuration.md)) |
| `events` | Internal event bus and webhook delivery |
| `application` | Use cases that compose the services for transport and the Java API |
| `spi` | Platform interfaces (already present) |

Keeping these as packages in one module is a starting point. A later refactor may split
them if build times, visibility rules, or API compatibility checks justify it.

## Module boundaries

Dependencies point inward. The following rules are mandatory:

| Module | May depend on | Must not depend on |
|---|---|---|
| `api` | JDK only | `core`, `transport`, platform adapters, server APIs |
| `core` | `api`, JDK | Bukkit/Velocity/Bungee/Folia classes, `transport`, platform adapters |
| `transport` | `core`, `api`, JDK, Netty 4.1.141.Final | Platform adapters, server APIs |
| `platform/*` | `core` (SPI), `api`, its own server API | `transport`, other platform adapters |
| `plugin` | All Java modules | Domain logic of its own |

`plugin` is wiring and packaging only. If behavior is needed by more than one platform,
it belongs in `core`, exposed through the SPI when it is platform-specific.

The public Java API (`api/`) is not a backdoor into internal services. It exposes a
small, semver-governed extension surface. Other plugins register validators, schemas,
and event listeners through it; they do not receive raw file-system access or bypass
RBAC. See [api-layering.md](api-layering.md).

### Java baseline

`core`, `api`, `transport`, and `platform/*` compile with `--release 8` on a JDK 21
toolchain. The build enforces a class-file major version of at most 52. Newer-Java
optimizations, if ever needed, must be optional and isolated; they cannot become a hard
requirement for the shared path. See
[ADR-0002](../adr/ADR-0002-java-8-bytecode-baseline.md).

## Request and data flow

A state-changing HTTP request follows this path:

1. `transport` accepts the connection, enforces size/time limits, assigns a request ID,
   and rejects malformed input.
2. `auth` establishes a verified identity. In Cloudflare mode only a verified JWT is
   trusted; plain identity headers are never sufficient.
3. `rbac` evaluates the required permission and, when a path is involved, a path scope
   resolved through the sandbox. Deny rules win.
4. The application service loads the current resource revision, validates preconditions
   (ETag/`If-Match`, idempotency key), and calls domain services.
5. `sandbox` resolves and checks every path. `snapshot` records the previous content
   before an overwrite, delete, or deployment.
6. `deploy` persists the transaction state before each side effect and asks the platform
   SPI to save, restart, or broadcast as needed.
7. `audit` records the action, identity, target, result, and hashes. `events` publishes
   change notifications to WebSocket clients and webhooks.
8. `transport` renders a response with a stable error code and a localized message.

A read-only request follows the same authentication and authorization path but performs
no snapshot or audit entry unless the operation is sensitive (for example, reading a
file covered by a secret-masking rule or querying the audit log).

## Data ownership

| Location | Owner | Rules |
|---|---|---|
| Configured plugin root (default `plugins/`) | Server administrator and authorized users | Only accessible through the sandbox; bounded operations; snapshots before destructive changes |
| `.deploymc/` | DeployMC | Protected from API access; contains history, staging, audit, deployment records, and `data.lock` |
| DeployMC `config.yml` and secret stores | Host administrator | Never editable through the API; local file access only |
| Server configuration, worlds, server core files | Server software / administrator | Outside the default root; DeployMC does not manage them unless a future feature explicitly adds a root |
| Web UI assets | Build output packaged in the JAR | Read-only at runtime |

The sandbox root is configured only by the host administrator in local configuration.
No API request can widen it.

## Cross-cutting rules

- **Stable error codes.** Every user-visible failure has a machine-readable code, an HTTP
  status, and a localizable message. Clients branch on codes, never on prose.
- **Audit before and after.** Destructive operations are recorded with enough detail to
  reconstruct what changed, without logging secrets or file contents.
- **Capability honesty.** A missing platform capability is reported as unavailable or
  unknown. The UI and CLI degrade; they never pretend an operation succeeded.
- **No static mutable state.** Services are created and owned by a lifecycle object, so
  enable/disable and hostile `/reload` environments do not leak state.
- **Bounded resources.** Every queue, buffer, upload, listing, and regex has a documented
  limit and a defined behavior when the limit is reached.
- **Injectable time, file system, and scheduler.** Tests use fakes rather than sleeps,
  real clocks, or the host file system where possible.
- **Specification first.** Behavior changes start in `specification/`, then the code and
  tests. This repository is still before that stage for most features.

## Lifecycle

The intended enable sequence is:

1. Load and validate the host configuration. Invalid configuration fails closed.
2. Acquire the single-instance lock on the data directory.
3. Open the snapshot store and verify its format version; recover unfinished
   deployment records.
4. Initialize authentication, RBAC, audit, and the console buffer.
5. Bind the transport to the configured address (loopback by default).
6. Register platform listeners and the Java API service.
7. Report readiness. No endpoint other than health/readiness answers before this point.

The disable sequence reverses it: stop accepting requests, drain bounded work with a
timeout, close WebSocket connections, stop the transport, flush audit records, detach
platform listeners, shut down owned executors, and release the lock. Every created
thread is named and joined or terminated.

## Failure and trust posture

- Fail closed on authentication, authorization, configuration, and path-resolution
  errors.
- Never leave a half-written target file. Writes are temp-then-rename in the same
  directory.
- Never overwrite a loaded JAR in place on Windows. Use the platform update folder or a
  restart-time swap.
- A failed deployment rolls back from snapshots. If rollback fails, enter `SAFE_STATE`
  with the UI reachable and an explicit report.
- The host administrator and anyone with host file-system access are outside the threat
  model's protection boundary. DeployMC cannot defend the data directory or audit log
  against them.

## Related documents

- [Platform SPI](platform-spi.md)
- [Deployment state machine](deployment-state-machine.md)
- [Threading model](threading-model.md)
- [API layering](api-layering.md)
- [Architecture decision records](../adr/README.md)
- [Threat model](../../specification/security/threat-model.md)
- [Support matrix design](../compatibility/support-matrix.md)

## Change process

A change to a component boundary, a trust boundary, a persisted format, a public API
shape, or a platform contract requires an ADR or an update to an existing one. Small
implementation choices within an accepted boundary do not.
