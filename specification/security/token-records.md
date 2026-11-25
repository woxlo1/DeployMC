# Host-provisioned token records

## Status

This is the normative contract for WP-3.3. The 0.1.0 four-field record remains
readable; the lifecycle fields added here are optional so existing installations can
be upgraded without rewriting host-managed files. Scope storage and propagation are
part of this contract, but permission and path-scope enforcement belong to WP-3.4
(RBAC engine) and must not be claimed before that work is implemented.

## Location and access

Token records live only under `plugins/DeployMC/tokens.d/*.json` on the host file
system. The directory is created on first start when it does not exist. An empty
directory is valid: it means token authentication is configured but no token has been
created yet.

The API can never read, list, write, or delete this directory. There is no bypass
flag and no unauthenticated endpoint except `/healthz` and `/readyz`. If the path
exists but is not a directory, cannot be listed, or contains an invalid or
unreadable record, startup is refused with a localized message.

On POSIX systems DeployMC restricts the directory and record files to the owner.
The administrator remains responsible for host-level file permissions and backups.

## Secret hashing choice

DeployMC stores `SHA-256` over the complete bearer token. Tokens contain 32 bytes
(256 bits) from `SecureRandom` before encoding, so the token itself is the high-entropy
secret. A salted password KDF adds no meaningful offline-resistance for a uniformly
random 256-bit value, while a separate HMAC key would introduce key generation, backup,
and rotation failure modes without increasing preimage resistance. The digest is
compared with `MessageDigest.isEqual()`. This is a deliberate choice for high-entropy
API tokens, not password storage guidance.

Existing 0.1.0 SHA-256 records therefore remain valid. There is no silent downgrade,
legacy marker, or acceptance of a shorter digest.

## Record format

Each file is named `<id>.json` and contains a JSON object. Unknown fields are
rejected. An unquoted timestamp is rejected. Duplicate ids or duplicate hashes are
rejected.

| Field | Type | Rules |
|---|---|---|
| `id` | string | `^[A-Za-z0-9_-]{1,64}$`; must equal the file name without `.json` |
| `label` | string | Human-readable label, at most 80 characters; never used as an identity or log key |
| `secretHash` | string | 64 lowercase hexadecimal digits: the SHA-256 digest of the complete bearer token |
| `expiresAt` | string, optional | Quoted RFC 3339 UTC instant; the token is expired at this instant |
| `scopes` | array of strings, optional | At most 16 unique strings of at most 128 characters matching `^(?:\*|[a-z][a-z0-9]*(?:\.[a-z][a-z0-9-]*)+)$`; absent or empty means no narrowing |
| `revokedAt` | string, optional | Quoted RFC 3339 UTC instant; a revoked token can never authenticate again |
| `lastUsedAt` | string, optional | Quoted RFC 3339 UTC instant of the last successful authentication that could be persisted |

Example with obviously fake values:

```json
{
  "id": "t-example01",
  "label": "ci-deployer",
  "secretHash": "0000000000000000000000000000000000000000000000000000000000000000",
  "expiresAt": "2099-01-01T00:00:00Z",
  "scopes": ["files.read", "server.status"],
  "lastUsedAt": "2098-12-31T23:59:00Z"
}
```

### Scope semantics

`scopes` are stored on the record and exposed in the identity claims as
`token.scopes`, comma-separated in declaration order; the claim is omitted when the
array is absent or empty. In WP-3.3 they are intentionally **not enforced** because no
RBAC engine exists. Every valid token remains admin-equivalent until WP-3.4. A scope
name that is not recognized by the later permission registry is rejected then, not in
WP-3.3. Do not interpret a stored scope as an enforced permission.

## Lifecycle

`/deploymc token` is console-only and uses the following forms:

- `create [label] [--ttl-days N] [--scopes a,b]`
- `revoke <id>`
- `list`

`create` generates 32 cryptographically random bytes, prints `dmc_` plus unpadded
Base64URL once, and persists only the digest. `--ttl-days` is clamped to
`1..auth.token.max-ttl-days`; `list` shows id, label, expiry, scopes, and revocation
state only. No command prints a secret, digest, or raw exception message.

`revoke` writes `revokedAt` durably. Revocation is checked before expiry; when both
apply, the closed `auth.revoked` code wins. `lastUsedAt` is persisted at most once per
minute per token to bound writes; a failure to persist it fails the request closed with
the retryable `internal.error` code. Metadata writes use a temporary file, a re-read of
the target record, and an atomic replace. A collision or any I/O failure fails closed
rather than overwriting a concurrent change.

Clients send the token as `Authorization: Bearer <token>`. Deterministic failure
order is: missing credential (`auth.required`), malformed credential (`auth.invalid`),
matching revoked token (`auth.revoked`), matching expired token (`auth.expired`), then
success. Authorization values, token secrets, digests, and identity claims are never
written to logs, doctor output, metrics labels, or API error details.

## Local one-time mode

`auth.mode: local-onetime` issues one ephemeral token at startup for local testing.
The server must bind to loopback unless `bind.allow-non-loopback: true` explicitly
overrides that rule; the startup banner prints a localized warning whenever the
override is active. The token is printed once to the console and is never written to
`tokens.d`, configuration, logs, or API responses. Restarting the process invalidates
it and creates a new one.

The token uses the same 256-bit random `dmc_` format. Its lifetime is
`auth.local-onetime.ttl-minutes` (1-60, default 15). It permits exactly one successful
redemption. A wrong or malformed credential consumes one attempt; reaching
`auth.local-onetime.max-attempts` (1-20, default 5) burns it. A burned or redeemed
token returns `auth.one-time-consumed`; an expired one returns
`auth.one-time-expired`. The existing per-source authentication-failure rate limit
still applies. One-time identities use method `local-onetime`, carry no token value in
claims, and are admin-equivalent only because this is an explicitly local testing
mode.

Host API tokens are not provisionable in `cloudflare` or `local-onetime` mode. The
one-time credential is not accepted in `token` or `cloudflare` mode. There is no
fallback between authentication modes and no shared-secret-header mode: the reserved
`auth.cloudflare.require-shared-secret-header` setting must remain empty.
