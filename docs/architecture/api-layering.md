# API Layering

> **Status: design baseline (2026-11-17).** No REST endpoint, WebSocket channel, or
> application service exists yet. The OpenAPI and AsyncAPI specifications are scheduled
> for WP-0.5. This document fixes the layering rules before those specifications are
> written.

## Principles

1. **One product API.** The Web UI, CLI, SDKs, GitHub Action, and third-party clients use
   the same versioned HTTP/WebSocket API. No client gets a privileged internal path.
2. **Specification first.** `specification/api/openapi.yaml` (OpenAPI 3.1) and
   `specification/api/asyncapi.yaml` are the source of truth. A behavior change starts
   with the specification and contract tests.
3. **Authentication at the edge, authorization at the use case.** The transport proves
   who the caller is; the application layer decides whether this identity may perform
   this action on this resource.
4. **Stable codes, localizable prose.** Clients branch on machine-readable error codes.
   Human messages are negotiated through `Accept-Language`.
5. **No transport types in core.** Domain services do not depend on HTTP, JSON, or
   WebSocket envelopes.

## Layers

```
+--------------------------------------------------------------+
| Clients: Web UI | CLI | SDKs | GitHub Action | 3rd party      |
+--------------------------------------------------------------+
                          | HTTPS / WSS
                          v
+--------------------------------------------------------------+
| transport                                                    |
|  HTTP routing, WebSocket upgrade, request IDs, size limits,  |
|  authentication, rate limiting, response rendering           |
+--------------------------------------------------------------+
                          |
                          v
+--------------------------------------------------------------+
| application services                                         |
|  use cases, transaction boundaries, authorization checks,    |
|  idempotency, pagination, ETag preconditions                 |
+--------------------------------------------------------------+
                          |
                          v
+--------------------------------------------------------------+
| domain services                                              |
|  sandbox | snapshot | deploy | auth | rbac | audit |         |
|  console | config | events                                   |
+--------------------------------------------------------------+
                          |
                          v
+--------------------------------------------------------------+
| platform SPI -> platform adapters -> server software         |
+--------------------------------------------------------------+
```

The public Java API (`api/` module) is a separate, narrow extension surface. It calls
application services through an internal facade; it is not another transport and does not
bypass authorization.

## Layer responsibilities

### Transport

- Parse HTTP requests and WebSocket frames with strict limits.
- Assign a request id and propagate it through logs and responses.
- Authenticate using the configured provider (Cloudflare JWT, API token, or one-time
  local token). Authentication produces a verified identity or a failure.
- Enforce coarse rate limits and connection limits.
- Map application results to HTTP status codes, headers, and Problem Details JSON.
- Never contain business rules such as path scopes, deployment preconditions, or
  protected-path checks.

### Application services

- Implement one use case per operation (for example, "write file content", "create
  deployment", "restore snapshot").
- Load current state, check preconditions, and define the transaction boundary.
- Call authorization with the resolved resource, not a raw string.
- Coordinate domain services in the correct order: validate, snapshot, write, audit,
  publish event.
- Translate domain failures into typed application errors with stable codes.
- Own pagination, filtering, sorting, idempotency, and ETag behavior.

Application services are Java 8 code in `core` (planned package
`io.github.woxlo1.deploymc.core.application`). They are callable from the transport and
from the Java API facade, but they are not public API by default.

### Domain services

- `sandbox`: the only component allowed to turn a user-supplied path into a file-system
  operation. It returns a `SandboxPath` token; callers do not pass raw strings to file
  APIs.
- `snapshot`: content-addressed history. It records before-images and supports diff,
  restore, retention, and verification.
- `deploy`: validation, staging, the persisted state machine, restart orchestration, and
  rollback.
- `auth`: providers, token storage, sessions, and identity types.
- `rbac`: roles, permissions, path scopes, and decision reasons.
- `audit`: append-only, hash-chained records.
- `console`: bounded log buffer, filtering, command policy.
- `config`: schema, validation, migration, and safe reload.
- `events`: internal event bus and outbound webhook delivery.

Domain services do not know about HTTP status codes or JSON field names.

### Platform SPI

The SPI is described in [platform-spi.md](platform-spi.md). Only platform adapters import
server APIs. Core receives capabilities and typed results.

## Dependency rules

| Layer | May depend on | Must not depend on |
|---|---|---|
| Clients | Public HTTP/WebSocket contract, generated SDKs | Internal Java packages, server APIs |
| `transport` | Application services, `api` value objects | Domain internals that bypass the application layer |
| Application services | Domain services, `api` value objects | `transport`, platform adapters, server APIs |
| Domain services | SPI, `api` value objects, JDK | `transport`, platform adapters, server APIs |
| Adapters | SPI, `api`, their server API | `transport`, application services |

The `api` module depends only on the JDK. It may expose immutable value objects shared
with the HTTP representation, but it must not expose persistence records or SPI types.

## Authentication and authorization placement

Authentication is a transport concern because it depends on request headers and the
WebSocket upgrade. It produces an immutable `Identity` with the verification method and a
minimal claim subset.

Authorization is an application concern because it needs the resource and the resolved
path:

```
transport: authenticate(request) -> Identity | AUTH_FAILURE
application: authorize(identity, permission, sandboxPath?) -> Decision
application: execute use case only after allow
```

- The RBAC decision records a reason code for explain mode and audit.
- Denials are audited.
- A path is resolved by the sandbox before authorization. Raw request strings are never
  compared against role patterns.
- API tokens carry scopes; effective permission is the intersection of token scopes and
  the owner's current permissions. A token can never exceed its owner.
- There is no `disabled` authentication mode.

## Representations and DTOs

- HTTP JSON types are explicit classes, not domain objects serialized directly.
- Field names use `camelCase` in JSON, matching the OpenAPI schema.
- Timestamps are RFC 3339 in UTC with an explicit offset.
- IDs are opaque strings; clients must not parse them.
- File content is bytes, not a JSON string, for download/upload endpoints. Metadata
  endpoints may return text previews with an explicit encoding and truncation flag.
- Secrets, token hashes, and file contents never appear in error payloads, events, or
  webhook bodies.
- Unknown response fields must be ignored by clients; additive changes are not breaking.

## Error model

Errors follow RFC 9457 Problem Details with DeployMC extensions:

```json
{
  "type": "https://deploymc.example/errors/revision-conflict",
  "title": "Revision conflict",
  "status": 412,
  "code": "files.revision-conflict",
  "detail": "The file changed since the supplied revision.",
  "requestId": "01J...",
  "conflicts": [{ "path": "Example/config.yml", "currentRevision": "sha256-..." }]
}
```

- `code` is stable and machine-readable. It is never localized.
- `title` and `detail` are localized according to `Accept-Language`; clients must not
  branch on them.
- A request id is always present and appears in logs.
- Validation failures may include a list of field/path errors.
- The error catalog will live in `specification/api/errors.md`.

## Versioning and deprecation

- The base path is `/api/v1`. Additive fields, endpoints, and event types are allowed
  within v1.
- Removing or changing the meaning of an existing field, status code, error code, or
  event requires a new major API version.
- Deprecated endpoints and fields carry `Deprecation` and `Sunset` headers and are
  documented with a migration path. The minimum overlap period will be documented before
  the first stable release.
- The server reports its API version and capabilities through `GET /api/v1/server`.
- Clients must check the reported API version and fail with a clear message on an
  incompatible major version.

## Concurrency and idempotency

- Existing files require `If-Match` with the current ETag/revision. A missing precondition
  is an error, not a blind overwrite.
- A stale revision returns `412` with the current revision and, when useful, a diff.
- Deployment creation accepts an `Idempotency-Key`. Repeating the same key returns the
  existing deployment; a different payload with the same key is a conflict.
- Snapshots and restores follow the same precondition rules where they target a path.
- Long-running operations return a resource with a state; clients poll or subscribe
  rather than holding a request open indefinitely.

## Pagination, filtering, and sorting

- List endpoints use cursor pagination with an opaque `cursor` and a documented limit.
  The initial maximum listing size is 5,000 entries; larger results are paginated.
- Filters are explicit query parameters, not an ad-hoc expression language, until a
  documented filter grammar exists.
- Sorting is limited to documented fields. Unknown fields return a validation error.
- Responses include a stable ordering for equal sort keys (for example, path or id).

## WebSocket protocol

- Authentication happens at upgrade time, with `Origin` and `Host` validation.
- Channels are `console`, `deployments`, `files` (change notifications), and `presence`.
- Every message uses an envelope with `type`, `id`, `sequence`, `protocolVersion`, and a
  payload. The exact schema is an AsyncAPI deliverable.
- Heartbeat and idle timeout are documented. Slow consumers get bounded queues and an
  explicit gap notice; the server never blocks on a client.
- Close codes distinguish normal shutdown, authentication failure, protocol error,
  policy violation, and server overload.
- Resume from a sequence number is best effort and documented as such.

## Webhooks

- Events are named with a stable namespace: `deployment.*`, `file.changed`,
  `snapshot.created`, `snapshot.restored`, `auth.denied`, `server.restarting`.
- Deliveries are signed with HMAC-SHA256 over a timestamp and body. Receivers must reject
  stale timestamps.
- Retries use bounded exponential backoff and a delivery log.
- Target URLs are checked for SSRF (no loopback, link-local, or private ranges unless the
  host administrator explicitly allows them).
- Webhook payloads contain references and hashes, not file contents or secrets.

## Public Java API versus the HTTP API

`api/` is for other plugins running in the same server. It is semver-governed and
published to Maven Central. It exposes:

- server information and capability states;
- registration of pre-deployment validators and post-deployment hooks;
- custom configuration schemas;
- deployment and snapshot state queries;
- event subscriptions.

It does not expose:

- raw file-system access;
- an authorization bypass;
- internal service implementations;
- transport DTOs;
- platform-specific classes.

Registrations are local administrator trust: installing a plugin that calls the Java API
is already code execution on the host. The Java API still enforces limits and never
returns secrets that the HTTP API would hide.

## Testing and conformance

- OpenAPI and AsyncAPI lint in CI, with breaking-change detection once a released version
  exists.
- Contract tests validate responses against the schemas.
- A shared conformance suite runs against a real DeployMC instance for each client
  implementation.
- Security tests cover authentication bypass, authorization confusion, path traversal,
  header spoofing, oversized bodies, and WebSocket origin attacks.
- Examples in the README and docs are executable or type-checked where feasible.

## Current state

- `transport` contains only `HttpBindConfig`; there is no router or server.
- `api` contains `DeployMCService`, `ServerInfo`, `PlatformInfo`, and `CapabilityState`.
- No OpenAPI, AsyncAPI, error catalog, or configuration schema exists yet.
- The application and domain layers are planned packages, not code.

## Related decisions

- [ADR-0005: Authentication modes and Cloudflare JWT verification](../adr/ADR-0005-authentication-and-cloudflare-jwt.md)
- [ADR-0009: Public API versioning and spec-first workflow](../adr/ADR-0009-public-api-versioning.md)
- [ADR-0006: Sandbox design and protected paths](../adr/ADR-0006-sandbox-and-protected-paths.md)
