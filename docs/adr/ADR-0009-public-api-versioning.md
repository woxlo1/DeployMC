# ADR-0009: Public API versioning and spec-first workflow

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** maintainers
- **Related:** [API layering](../architecture/api-layering.md), [ADR-0005](ADR-0005-authentication-and-cloudflare-jwt.md)

## Context

The Web UI, CLI, SDKs, GitHub Action, and third-party integrations must all use the same
API. If each client grows against a different interpretation of the server, behavior
drifts and compatibility claims become meaningless. The API is also a product surface:
users will write scripts and integrations against it.

At the same time, the project is pre-alpha and the API must be able to evolve. Freezing
everything now would encode mistakes; changing silently would break clients.

## Problem

How should the public API be specified, versioned, and evolved without breaking clients
or letting the specification drift from the implementation?

## Considered alternatives

### Option A — Code-first with generated documentation

- Write server handlers and generate a schema later.
- Advantages: fast initial development.
- Disadvantages: the schema becomes a description of accidents; clients cannot be
  reviewed against a contract; breaking changes are discovered late.

### Option B — GraphQL

- One schema with client-selected fields.
- Advantages: flexible queries.
- Disadvantages: a poor fit for file upload/download and long-running deployment
  transactions; more complex authorization and rate limiting; tooling for all target
  languages is heavier.

### Option C — gRPC

- Binary RPC with generated clients.
- Advantages: strong contracts and streaming.
- Disadvantages: browsers need a proxy; raw file transfer and simple curl-based
  integration are harder; the ecosystem for Minecraft server administrators is more
  familiar with HTTP/JSON.

### Option D — Versioned REST plus WebSocket, specified first

- OpenAPI 3.1 for REST, AsyncAPI for WebSocket, stable error codes, and contract tests.
- Advantages: broad client support; easy manual testing; explicit contracts; good fit for
  files, deployments, and console streams.
- Disadvantages: more specification maintenance; code generation quality varies; HTTP
  semantics must be applied carefully.

## Decision

Use a versioned REST API under `/api/v1` plus a versioned WebSocket protocol, specified
before implementation:

- `specification/api/openapi.yaml` (OpenAPI 3.1) is the source of truth for REST.
- `specification/api/asyncapi.yaml` is the source of truth for WebSocket messages.
- `specification/api/errors.md` defines stable error codes.
- Changes to behavior start with the specification, then the server, then the clients
  and tests.
- Additive fields, endpoints, and event types are allowed within v1. Removing or changing
  the meaning of an existing field, status code, error code, or event requires a new major
  API version.
- Deprecated surfaces carry `Deprecation` and `Sunset` headers and a documented migration
  path.
- Errors use RFC 9457 Problem Details with a stable `code`; human text is localized.
- Existing resources use ETags/`If-Match`; deployment creation uses `Idempotency-Key`.
- List endpoints use cursor pagination with documented limits and stable ordering.
- `GET /api/v1/server` reports the platform, versions, capabilities, and API version.
- Contract tests validate implementations against the schemas, and a shared conformance
  suite runs for each client.
- SDKs may be generated, but generated output is reviewed and polished; it is not shipped
  as an unreviewed dump.

## Consequences

### Positive

- Clients and server share one tested contract.
- Breaking changes are deliberate and visible.
- API examples and SDKs can be generated from a reliable source.
- Error handling is consistent across languages.

### Negative and costs

- The specification is another artifact to maintain and review.
- Code generation may not cover every language idiom; hand-written wrappers remain.
- Contract tests and a conformance suite add CI time.
- Versioning discipline limits quick fixes that would break clients.

### Follow-up

- Write the WP-0.5 specification skeletons and enable spec lint in CI.
- Add breaking-change detection once a released version exists.
- Define the deprecation overlap period before the first stable release.
- Build contract tests and the cross-language conformance suite.
- Keep the Java API (`api/`) versioned independently; it is not the REST API.

## Evidence and validation

- The principles follow master specification Part 8 and the project's spec-first rule.
- **Not verified:** no OpenAPI, AsyncAPI, error catalog, contract test, or conformance
  suite exists yet. The `api/` module currently contains value objects only.
- The API is not frozen and no compatibility promise is in effect before `v1.0.0`.

## References

- Master specification, Part 8 and section 24.10.
- [API layering](../architecture/api-layering.md).
- [ADR-0005](ADR-0005-authentication-and-cloudflare-jwt.md) for authentication and token
  scopes.
