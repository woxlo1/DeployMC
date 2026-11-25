# ADR-0005: Authentication modes and Cloudflare JWT verification

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner, maintainers
- **Related:** [Threat model](../../specification/security/threat-model.md), [API layering](../architecture/api-layering.md)

## Context

DeployMC exposes a remote deployment and file-management surface. Deploying a JAR is code
execution on the server. Authentication is therefore a primary security boundary, not an
optional feature.

The default production topology puts Cloudflare Access in front of a Cloudflare Tunnel
that forwards to `127.0.0.1:8080`. Cloudflare authenticates users and service tokens at
the edge. A request that reaches the plugin may still be forged if the port is reachable
by another local process, by a misconfigured tunnel, or by a direct connection.

CI and local development need authentication modes that do not require an interactive
Cloudflare session.

## Problem

Which authentication modes should DeployMC support, and how should it trust identity from
Cloudflare?

## Considered alternatives

### Option A — Trust the `Cf-Access-Authenticated-User-Email` header

- Read the email header that Cloudflare Access adds.
- Advantages: simple.
- Disadvantages: the header can be forged by anything that can reach the port; there is
  no signature, audience, or expiry check. This is unsafe and rejected.

### Option B — Verify the Cloudflare Access JWT

- Require `Cf-Access-Jwt-Assertion` and verify it against the team's JWKS.
- Advantages: cryptographic identity, audience and issuer binding, expiry, key rotation;
  works with the standard Cloudflare Access deployment.
- Disadvantages: JWKS availability and rotation must be handled; clock skew; requires
  correct team domain and application audience configuration; fail-closed behavior can
  deny requests during a Cloudflare outage.

### Option C — HTTP Basic authentication

- Username/password against a local store.
- Advantages: simple clients.
- Disadvantages: password management and brute-force risk; poor fit for service
  identities; no central policy; easy to misconfigure.

### Option D — Mutual TLS

- Client certificates terminated by the plugin or a local proxy.
- Advantages: strong machine identity.
- Disadvantages: certificate distribution and rotation; not friendly to browsers;
  Cloudflare Tunnel already provides an authenticated edge; unnecessary complexity for
  the initial product.

### Option E — Disable authentication for local use

- No authentication when bound to loopback.
- Advantages: convenient.
- Disadvantages: any local process or user can deploy code; a single misconfiguration
  exposes the server. Rejected; there is no unauthenticated mode.

### Option F — One-time local token

- Print a short-lived token to the console at startup for local testing.
- Advantages: usable before Cloudflare is configured; no permanent secret.
- Disadvantages: console access is powerful; token can leak in logs; must be loopback-only
  and short-lived.

## Decision

Support exactly three modes:

| Mode | Intended use | Identity source |
|---|---|---|
| `cloudflare` | Production behind Cloudflare Access and Tunnel | Verified Access JWT |
| `token` | CI, standalone, and non-Cloudflare deployments | DeployMC API tokens |
| `local-onetime` | Local testing only | Ephemeral token printed at startup |

`disabled` is not a mode. There is no unauthenticated endpoint except health/readiness,
and those reveal no sensitive information.

### Cloudflare mode requirements

- Bind to `127.0.0.1` by default. Binding elsewhere requires an explicit setting and a
  prominent warning.
- Never trust the plain email header. Verify `Cf-Access-Jwt-Assertion`.
- Verify the signature with an algorithm allow-list (initially RS256; confirm against
  current Cloudflare documentation), a required key id, and the team's JWKS.
- Verify `iss`, `aud`, `exp`, `nbf`, and `iat` with a small clock skew.
- Extract identity only from the verified token: user email/subject, or service-token
  `common_name`.
- Cache JWKS with a minimum refresh interval and a hard maximum staleness. Fail closed
  when no valid cached key exists.
- Validate `Host` and `Origin`; use CSRF protection for cookie-based sessions.
- Treat `CF-Connecting-IP` as informational and only on the trusted tunnel path.

### Token mode requirements

- Generate high-entropy tokens, show them once, store only a hash. For the 256-bit
  random secret, SHA-256 over the complete token is the documented storage choice;
  a salted password KDF or separate HMAC key adds no meaningful benefit here.
- Store and propagate scopes by WP-3.3, but do not enforce them until the WP-3.4 RBAC
  engine exists. Until then a valid token remains admin-equivalent.
- Support expiry, revocation, and last-used tracking.
- Compare secrets in constant time and rate-limit authentication failures.

### Local one-time mode requirements

- Loopback only unless explicitly overridden with a warning.
- Short lifetime (initial default: 15 minutes) and a small attempt limit.
- Clearly labeled as a local testing mode, not a production mode.

## Consequences

### Positive

- A forged identity header is not enough to reach the API.
- CI and local development have explicit, auditable alternatives.
- Token scopes reuse the same RBAC model as human users.

### Negative and costs

- Cloudflare mode depends on JWKS availability; a long outage can deny access by design.
- Key rotation, clock skew, and audience misconfiguration are new operational concerns.
- Token storage and revocation need careful implementation.
- The one-time token is inherently sensitive; its console output must be handled
  carefully.

### Follow-up

- Choose the JWT/JWKS implementation (JDK crypto plus minimal parsing, or a relocated
  library) with test vectors and an algorithm allow-list.
- Write the threat model before implementing authentication.
- Build tests for expired tokens, wrong audience, wrong issuer, tampered payloads, `none`
  algorithm, key rotation, and JWKS outage.
- Document Cloudflare setup and verification in `examples/cloudflare-tunnel/`.

## Evidence and validation

- The mode set, fail-closed rules, and verification steps are design decisions from the
  master specification (sections 9.2, 9.3, and 33.3).
- **Not verified:** no authentication code exists, no JWT dependency is chosen, and no
  test vector has been run. The current code base has no transport server.
- Current Cloudflare product details must be re-checked against official documentation
  before implementation; this ADR does not claim they were verified.

## References

- Master specification, sections 9.2, 9.3, and 33.3.
- [Threat model](../../specification/security/threat-model.md) draft.
- [ADR-0009](ADR-0009-public-api-versioning.md) for API token scopes and client behavior.
