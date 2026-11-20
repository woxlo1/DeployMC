# Host-provisioned token records

## Status

Implemented in 0.1.0 for `auth.mode: token`. This is the minimum authentication
foundation: it keeps every API and WebSocket endpoint authenticated from the first
release. Permission and path scoping are not implemented yet.

## Location and access

Token records live only under `plugins/DeployMC/tokens.d/*.json` on the host file
system. The directory is created on first start when it does not exist. An empty
directory is valid: it means token authentication is configured but no token has
been created yet.

The API can never read, list, write, or delete this directory. There is no bypass
flag and no unauthenticated endpoint except `/healthz` and `/readyz`. If the path
exists but is not a directory, cannot be listed, or contains an invalid or
unreadable record, startup is refused with a localized message.

On POSIX systems DeployMC restricts the directory and record files to the owner.
The administrator remains responsible for host-level file permissions and backups.

## Record format

Each file is named `<id>.json` and contains a JSON object with exactly these
fields:

| Field | Type | Rules |
|---|---|---|
| `id` | string | `^[A-Za-z0-9_-]{1,64}$`; must equal the file name without `.json` |
| `label` | string | Human-readable label, at most 80 characters; never used as an identity |
| `secretHash` | string | 64 lowercase hexadecimal digits: the SHA-256 digest of the complete bearer token |
| `expiresAt` | string, optional | Quoted RFC 3339 UTC instant; the token is expired at this instant |

Unknown fields are rejected. An unquoted timestamp is rejected. Duplicate ids or
duplicate hashes are rejected. Example with obviously fake values:

```json
{
  "id": "t-example01",
  "label": "ci-deployer",
  "secretHash": "0000000000000000000000000000000000000000000000000000000000000000"
}
```

## Creation and use

`/deploymc token create [label]` is a console-only command. It generates 32
cryptographically random bytes, prints `dmc_` plus unpadded Base64URL once, and
persists only the SHA-256 digest of the complete token. The secret is never
written to the record, the configuration, or a log message.

Clients send the token as `Authorization: Bearer <token>`. Comparison uses
`MessageDigest.isEqual()` against stored digests. A malformed, unknown, or
expired credential returns `401` with a stable `auth.required`, `auth.invalid`,
or `auth.expired` code. `Authorization` values and token secrets are never logged.

Until 0.4.0 every valid token is admin-equivalent. This is a deliberate temporary
compatibility rule, not a role assignment. Do not share tokens between principals
that need different permissions.
