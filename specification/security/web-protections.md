# Web-facing protections contract

> **Status: normative contract for WP-3.6 (2026-11-27).** The product enforcement is not claimed
> until the implementation and final evidence PRs merge. This document is normative for design,
> configuration, API, and test work; it is not evidence that a running server enforces the rules.

## 1. Authority and scope

Where this file and the master specification disagree, this file is authoritative for WP-3.6 and the
conflict is recorded as errata in `docs/development/spec-errata.md`.

The contract applies to the embedded HTTP/WebSocket transport, every REST route, the WebSocket
upgrade, static assets, `/healthz`, and `/readyz`. The last two remain the only unauthenticated
endpoints; they are still subject to per-IP rate limiting and request-size limits. There is no bypass
flag, no unverified identity source, and no route that skips the transport checks.

DeployMC v1 is bearer-token-only for REST and WebSocket access. Cookie-based sessions do not exist in
v1. In `auth.mode: cloudflare`, the only accepted credential is `Cf-Access-Jwt-Assertion`; forwarded
identity headers never establish or narrow an identity. Deny is the default outcome whenever a
required input is missing, malformed, or cannot be resolved.

## 2. Effective peer address and trusted proxies

The transport socket peer is authoritative unless it is trusted. A socket peer is trusted when it is
a loopback address (`127.0.0.0/8` or `::1`) or when it matches an entry in `bind.trusted-proxies`. The
loopback exception exists for a same-host tunnel terminator such as `cloudflared`; a non-loopback
bind address does not make arbitrary remote peers trusted.

`bind.trusted-proxies` accepts literal IPv4/IPv6 addresses and CIDR blocks only. DNS names,
wildcards, and hostnames are configuration errors and refuse startup. Entries are bounded to 64 and
are restart-only.

Only a trusted peer may contribute an effective client address from forwarded headers. The parser
considers, in fixed precedence, `Forwarded`, then `X-Forwarded-For`, then `CF-Connecting-IP`. It
accepts one valid IP literal from the first header that is present, bounded to 16 chain entries and
the existing 8 KiB total header budget. A malformed value, an unsupported obfuscated form, or more
than 16 entries causes the header to be ignored and the socket peer to remain the effective address.
Forwarded headers from any other peer are ignored without being parsed or logged.

The effective peer address is used only for per-IP rate limiting, authentication-failure counting,
and token-mode lockout. It is never used to authorize a role, accept a `Host` or `Origin` value, set
`actor.ip` in the audit log, or produce a diagnostic containing a raw address. WP-3.5's
`actor.ip = null` policy is unchanged by WP-3.6.

## 3. Rate limiting

Three independent bounded token buckets are evaluated:

| Bucket | Key | Default budget | Applies to |
|---|---|---|---|
| Per-IP request | Effective peer address | 300/minute | Every HTTP request, including `/healthz` and `/readyz` |
| Per-identity request | Authenticated identity id and closed endpoint class | 300/minute | Authenticated REST and WebSocket upgrade requests |
| Authentication failure | Effective peer address | 60/minute | Failed credential verifications only |

Each bucket starts full, has a capacity equal to its budget, and refills continuously at
`budget / 60` tokens per second. A request is allowed only when both applicable request buckets have
at least one token. A denied request consumes no token from another bucket. Unauthenticated requests
consume the per-IP bucket; the identity bucket is evaluated only after authentication succeeds.
`/healthz` and `/readyz` are exempt from the identity bucket.

Bucket keys are SHA-256 hashes with domain-separated inputs. Raw identities and addresses are never
logged, exported, or returned in a response. Store state is bounded to 4,096 keys per bucket family
with least-recently-used eviction and idle pruning after 60 seconds. Missing or malformed key inputs
deny closed.

Every response for which the limiter was evaluated carries `RateLimit-Limit`,
`RateLimit-Remaining`, and `RateLimit-Reset`. When more than one request bucket was evaluated, the
headers describe the most constraining bucket: lowest remaining tokens first, then earliest reset.
`RateLimit-Reset` is an epoch second at which the reported bucket returns to full. A `429` also
carries `Retry-After`, an integer number of seconds until one token is available.

A bucket denial returns Problem Details with code `auth.rate-limited`, HTTP 429, and `retryable:
true`. The detail never names the caller, address, route, or remaining attempts.

## 4. Token-mode brute-force lockout

Lockout applies only when `auth.mode: token`. It is keyed by effective peer address and counts failed
credential verifications, including malformed, revoked, expired, and otherwise invalid presented
credentials. Requests rejected by the per-IP bucket or an active lockout do not increment the
counter.

After five consecutive failures from one effective peer, further token-mode authentication from that
peer is blocked for five minutes. Failures during an active lockout do not extend it. After a lockout
expires, the next failed verification re-locks the peer for twice the previous duration, capped at 24
hours. A successful authentication clears the failure count and backoff for that peer. Lockout state
is in-memory, bounded to 4,096 keys with least-recently-used eviction, and is cleared by a restart; it
is never persisted.

An active lockout returns Problem Details with code `auth.locked-out`, HTTP 429, and `retryable:
true`, without checking the supplied credential and without disclosing the failure count. The
stable code is also the audit denial reason code. `local-onetime` keeps its own WP-3.3 five-attempt
budget and is not subject to this lockout; `cloudflare` mode is not subject to it either.

## 5. WebSocket Origin checks

Every WebSocket upgrade must contain exactly one well-formed `Origin` header. The value is parsed as
an absolute origin and compared against the allowlist by exact scheme, case-insensitive host, and
effective port; default ports 80 and 443 are normalized. Wildcards, suffix matching, and userinfo are
not accepted.

The default allowlist contains the loopback origins for the configured bind port:
`http://127.0.0.1:<port>`, `http://localhost:<port>`, and `http://[::1]:<port>`, plus the same origins
with `https` when a TLS terminator is used. Additional browser origins are configured explicitly in
`bind.allowed-origins`, bounded to 64 entries and restart-only. A non-loopback deployment reached
through a tunnel must list its public browser origin explicitly; loopback within a CIDR such as
`127.0.0.2` is not implicitly allowed as an Origin.

A missing, duplicated, malformed, or non-allowlisted Origin rejects the upgrade before authentication
with Problem Details code `request.origin-rejected` and HTTP 403. The upgrade must not fall back to
allowing an absent Origin. For non-WebSocket HTTP requests, an `Origin` header, when present, must
match the same allowlist; an absent Origin is allowed because bearer clients do not depend on ambient
credentials. `Host` validation remains independent and never reads a forwarded `Host` value.

## 6. CSRF policy

v1 has no cookie-based session, so no CSRF token is implemented or claimed. Bearer credentials are
supplied explicitly on every request and are not attached automatically by the browser.

If a cookie-based session is ever introduced, the product must refuse to start unless all of the
following hold: session cookies are `HttpOnly`, `SameSite=Strict` except for top-level safe methods,
and `Secure` whenever TLS is terminated; every state-changing request requires both an Origin check
and an unpredictable per-session CSRF token bound to that session; and a missing or invalid token
returns Problem Details code `request.csrf-required` with HTTP 403. That code is reserved and must not
be emitted by v1.

## 7. Security headers

Every HTTP response, including error, health, and static-asset responses, carries these headers:

| Header | Value |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Cache-Control` | `no-store` unless a handler explicitly sets a safer value |
| `Cross-Origin-Opener-Policy` | `same-origin` |
| `Cross-Origin-Resource-Policy` | `same-origin` |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` unless a static-asset handler supplies a stricter allowlist-based policy |

A static-asset policy may allow only same-origin scripts, styles, fonts, images, and workers. It must
not use `unsafe-inline`, `unsafe-eval`, or remote script sources. DeployMC does not set
`Strict-Transport-Security`; TLS and HSTS are the responsibility of the trusted terminator. DeployMC
does not emit permissive CORS headers by default.

## 8. Request-size limits and endpoint classes

Endpoint classification is derived from the closed transport route table, never from client input.
Unrecognized paths are non-upload requests.

| Class | Limit | Configuration |
|---|---|---|
| Upload: `POST /api/v1/files/upload` and future deployment upload routes | 64 MiB | `sandbox.max-upload-size-mb` |
| All other REST and static requests | 1 MiB | `limits.request-body-limit-kb` |
| All request headers, including WebSocket upgrade | 8 KiB | Fixed transport bound |
| WebSocket text frame | 64 KiB | Fixed transport bound |
| WebSocket outbound queue | 1,000 messages or 2 MiB | Fixed transport bound |

A body or header over its limit is rejected before the handler runs and before any state-changing
operation is committed. The response is Problem Details code `request.too-large` with HTTP 413.
Non-streaming requests have a 30-second deadline. WebSocket connections require a heartbeat at least
every 20 seconds and close after 60 seconds without one.

## 9. Diagnostics and audit

WP-3.6 does not add a doctor check. Rate-limit, lockout, and forwarded-header counters may appear only
as redacted counts in existing doctor or metrics output; they never include raw addresses,
identities, request paths, or credential material. Rejected authentication and rate-limit paths use
the existing `AuditActions.AUTH_DENIED` seam with stable reason codes such as `auth.rate-limited` and
`auth.locked-out`.

## 10. Configuration validation

The following keys are added to the normative schema:

| Key | Default | Hot reload |
|---|---|---|
| `bind.allowed-origins` | `[]` | No |
| `limits.auth-failure-rate-limit-per-minute` | `60` | Yes |
| `limits.request-body-limit-kb` | `1024` | No |

Existing `bind.trusted-proxies` remains restart-only with an empty default. A role limit may lower
`limits.rate-limit-per-minute` but never raise it. Invalid values, unsupported address forms, and
unknown keys refuse startup according to the configuration contract. A hot reload with an invalid
limit leaves the last valid limiter installed and reports the failure; it never falls back to an
unlimited state.

## 11. Compatibility and evidence boundary

Existing bearer clients are unaffected for HTTP requests that do not send `Origin`. WebSocket clients
must send an allowlisted Origin. There is no cookie session, no CORS grant, and no new unauthenticated
endpoint. CI compatibility and Paper smoke evidence remain separate from this contract; no running
server, browser, E2E run, full integration suite, or in-game command is claimed by this document.
