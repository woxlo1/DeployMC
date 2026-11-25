# RBAC policy contract

> **Status: normative contract for WP-3.4.** The product evaluator is not implemented until the
> implementation PR is merged. This document is normative for design, configuration, API, and test
> work; it is not evidence that a running server enforces the rules.

## 1. Authority and scope

Where this file and the master specification disagree, this file is authoritative for WP-3.4 and the
conflict is recorded as errata in `docs/development/spec-errata.md`.

The policy applies to every authenticated REST route, the doctor and metrics routes, and every
WebSocket event delivery. `/healthz` and `/readyz` remain the only unauthenticated endpoints. There
is no bypass flag, no unverified identity source, and no implicit admin equivalence. Deny is the
default outcome whenever a required input is missing, malformed, or cannot be resolved.

RBAC decisions are made against an immutable policy snapshot derived from host-controlled
`config.yml`, token records, and the authenticated identity. The snapshot never comes from request
input.

## 2. Canonical permission registry

The registry is closed and contains exactly these 23 identifiers. `config.schema.json`, the product
configuration validator, `/api/v1/me`, and the spec checker must mirror this list.

| Identifier | Meaning |
|---|---|
| `server.read` | View server information and the redacted doctor report. |
| `files.read` | List, read, and download files. |
| `files.write` | Create or modify files. |
| `files.delete` | Delete, rename, or move files. |
| `files.upload` | Upload files. |
| `snapshots.read` | List, show, and diff snapshots. |
| `snapshots.restore` | Restore snapshots. |
| `snapshots.prune` | Delete snapshots manually. |
| `deployments.read` | View deployments. |
| `deployments.create` | Create or upload deployments. |
| `deployments.create.jar` | Specifically authorize JAR content in a deployment; required in addition to `deployments.create`. |
| `deployments.approve` | Approve a deployment. Self-approval remains a separate deployment-engine check. |
| `deployments.cancel` | Cancel a deployment. |
| `deployments.rollback` | Roll back a deployment. |
| `server.restart` | Restart the Minecraft server through a supported platform capability. |
| `console.read` | Read the console ring buffer. |
| `console.command` | Submit console commands. |
| `audit.read` | Query audit records. |
| `audit.export` | Export audit records. |
| `tokens.manage.self` | Manage the caller's own tokens. |
| `tokens.manage.all` | Manage all host-provisioned tokens. |
| `webhooks.manage` | Configure webhook targets. |
| `users.read` | Read identity-to-role mappings. |

`*` is not a permission and is never returned by `/api/v1/me`. It is a whole-grant shorthand allowed
only in `roles.<name>.permissions`, where it grants every registry permission. A wildcard grant does
not bypass path rules, sandbox protected paths, approval separation, or audit requirements. Token
scopes must not contain `*`.

## 3. Configuration validation

Startup validates the complete configuration. An invalid configuration refuses startup. A hot reload
with an invalid configuration puts the evaluator into deny-all mode until a valid snapshot is
installed; it never keeps a more permissive old snapshot alive.

The following are errors, not warnings:

- a role permission that is not in the registry and is not the role-only `*` shorthand;
- an unknown permission in a token record scope;
- a role named `none`, which is reserved as the implicit deny-only role;
- a `default-role` other than `none` that names an undefined role;
- a role reference in `users` or `deploy.approval.approver-roles` that names an undefined role;
- a role limit greater than the corresponding global limit.

Error messages name the exact key, identity, role, or token id and the allowed values. They never
include a token secret, digest, or raw request path.

`paths.allow` semantics are deliberately distinct:

- **Absent** `paths.allow`: the role's granted permissions apply to the whole sandbox root minus
  protected paths.
- **Empty** `paths.allow`: the role grants no path-scoped access.
- **Present and non-empty**: only matching resolved paths are in scope.

`paths.deny` is always evaluated first and wins over `paths.allow` and the sandbox default. Role
limits are validated here but their enforcement remains a later work package and is not claimed by
WP-3.4.

## 4. Identity and token owner mapping

| Identity source | Role lookup key | Notes |
|---|---|---|
| Cloudflare Access JWT | Verified identity id from the WP-3.2 verifier | Cloudflare identities are never admin-equivalent. |
| Host-provisioned bearer token | `owner` field on the token record | `label` is never an identity. |
| `local-onetime` | Reserved identity id `local-onetime` | Loopback-only transport rules still apply. |

Token records gain an optional `owner` string for compatibility with existing files. New records
created by console commands must include it and it must match a verified identity key. When `owner`
is absent, the token uses `default-role`; it does not inherit admin equivalence from WP-3.3. An
owner who is absent from `users` also uses `default-role`.

Token scopes are a narrowing filter over the owner's effective permissions. An absent or empty
`scopes` array means no narrowing. A scope that is not in the registry is rejected when the token
record is loaded. After issuance, any reduction in the owner's permissions is reflected on the next
policy evaluation; a token can never exceed its owner's current effective permissions.

## 5. Evaluation algorithm

`authorize(identity, permission, requestedPath?)` returns
`Decision(allow|deny, reason, diagnostics)`. Evaluation is constant-bounded in the number of roles,
grants, and path rules.

1. Determine the lookup identity. For a token, use its `owner` to obtain roles; for all other
   identities use the verified identity id.
2. `roles = userRoles(lookupIdentity)`; if the identity is not listed, `roles = [default-role]`.
   Because `none` is deny-only, an empty effective role set or `none` alone denies with `NO_ROLE`.
3. Collect grants across roles. A role grants `permission` when it lists the exact identifier or
   holds the role-only `*`. If no role grants it, deny with `NOT_GRANTED`.
4. For a token, compute `effective = ownerEffective ∩ tokenScopes` (an absent scope set is treated
   as the full registry for this intersection). If the requested permission is no longer present,
   deny with `TOKEN_SCOPE_EXCEEDED`.
5. When `requestedPath` is present, resolve it through the sandbox resolver first. Never evaluate a
   raw string from the request. A resolver rejection, an absolute path, a traversal, a symlink
   escape, or a protected path denies with `PATH_DENIED`.
6. Deny with `PATH_DENIED` if any deny glob from any assigned role matches the resolved path.
7. Allow the path when at least one role that grants the permission has no `paths.allow` or has a
   matching allow glob. Otherwise deny with `PATH_SCOPE`.
8. When no path is supplied, allow after step 4. `deployments.create.jar` must be checked separately
   after `deployments.create` for JAR content.

Deny rules from all assigned roles participate in step 6, including roles that do not grant the
requested permission. This is stricter than considering only granting roles and prevents a role
from being used to nullify another role's path restriction.

Stable reasons and their REST error mapping:

| Reason | Error code | Meaning |
|---|---|---|
| `NO_ROLE` | `rbac.denied` | No usable role was assigned. |
| `NOT_GRANTED` | `rbac.denied` | No assigned role grants the permission. |
| `TOKEN_SCOPE_EXCEEDED` | `rbac.token-scope-exceeded` | Token scope narrowed away the permission or declared a permission outside the registry or owner. |
| `PATH_DENIED` | `rbac.path-denied` | Sandbox resolution failed, a protected path was requested, or a deny rule matched. |
| `PATH_SCOPE` | `rbac.path-denied` | No granting role's `paths.allow` matched the resolved path. |

`rbac.self-approval-denied` remains a deployment-engine decision and is not produced by this
evaluator.

## 6. Explain mode and `/api/v1/me`

`GET /api/v1/me` is authenticated and returns only the caller's own identity kind, opaque identity
id, sorted assigned roles, and effective permissions in canonical registry order. `*` is expanded.
The response never contains a token secret or digest, raw token scope strings, raw path globs, or
another identity's data.

Optional query parameters `explain=<permission>` and `path=<sandbox-relative path>` request a
deterministic explanation of one decision. The `explain` object contains only:

- `permission` and `decision`;
- `reason`, using the stable reason table above;
- `path.requested`, `path.resolved`, and `path.withinScope` booleans;
- `token.present` and `token.containsPermission` booleans when the caller is a token;
- `roles.assigned`, `roles.granting`, `roles.denyRuleMatches`, and `roles.allowRuleMatches` counts.

It never contains a raw requested path, raw glob, identity email, token label, or secret. The same
reason codes are used by tests and by the doctor's redacted synthetic probes.

The doctor report gains one stable check, `rbac`, whose evidence keys are bounded counts and fixed
values only: `roles`, `users`, `default-role`, and `registry-version`. It is `PASS` only when a
product evaluator is wired and the policy snapshot is valid; otherwise it is `WARN` with
`implemented: false`. Doctor probes use synthetic identities and are never logged with real
identity or path data.

## 7. Reload and WebSocket behavior

A valid config reload atomically installs a new immutable policy snapshot. Requests that already
completed authorization may finish. New requests and every new WebSocket event evaluation use the
new snapshot. If a reload removes a permission required by a subscribed channel, the server removes
that subscription. When no authorized subscription remains, it closes the connection with code
`4403` and reason `rbac.permission-revoked`, without sending further events. Invalid reloads follow
the deny-all rule in section 3.

## 8. Audit boundary

Every decision carries its reason through the request lifecycle so that the WP-3.5 audit writer can
record it. WP-3.4 does not write audit records and must not claim that denials are persisted.
"Denials always audited" is a requirement on the completed audit subsystem, not an implemented
behavior of this work package.

## 9. Verification requirements

- Deterministic, seeded generated cases without adding a property-testing dependency: at least 500
  generated role/token configurations assert that effective token permissions are always a subset
  of the owner's effective permissions, including owner permission reduction after issuance.
- Table tests cover deny precedence, absent versus empty `paths.allow`, sandbox rejection, protected
  paths, wildcard grants, unknown permissions, token owner absence, and all reason-to-error mappings.
- Config checker and product validator tests both reject unknown permission names with a key-specific
  error.
- Explain and doctor tests assert that no raw path, glob, identity, label, token, or digest appears
  in the response.
- WebSocket reload tests assert subscription removal and close code `4403`.

These are server-free, deterministic tests unless a later evidence PR explicitly records a running
server, Bukkit mock, browser, E2E, full-integration, or in-game-command run.
