# Audit Log Format

> **Status: specification only (2026-11-17).** No audit log is written by any component
> yet. This document defines the record format, the hash chain, rotation, and
> verification so the implementation and the API query shape can be written against it.
> Where this document and the master specification disagree, this document wins and the
> master specification is corrected.

## Scope

Every authenticated state-changing operation and every denied attempt is recorded:

- file create, write, upload, delete, rename, move, and restore;
- snapshot creation, restore, and manual prune;
- deployment creation, upload, validation, approval, cancellation, rollback, and every
  state transition;
- console command dispatch, including a rejected command;
- token creation, revocation, and scope changes;
- webhook registration, delivery failure, and redelivery;
- configuration reload and audit export.

Read-only requests are not recorded by default; access to the audit log itself is
recorded. A request that fails authentication before an identity is established cannot
name an actor, so it is counted as a rate-limit/security metric rather than written as an
audit record.

The audit log is not a debug log. It answers "who did what, to what, when, and with what
result", not "why did this stack trace happen".

## File layout

```
plugins/DeployMC/.deploymc/audit/      # audit.directory
  audit.jsonl                          # active file
  audit-0000010423-2026-01-15T09-30-12Z.jsonl
  audit-0000010520-2026-01-15T17-02-41Z.jsonl
  format-version                       # "1\n"
  anchors/                             # optional exported anchors, see below
```

- `audit.jsonl` is the only file appended to. It is opened with append semantics and
  flushed after each record or small batch. A crash may lose the last unflushed records;
  the chain makes a partial line detectable.
- A rotated file is renamed to `audit-<seq>-<timestamp>.jsonl`, where `<seq>` is the
  record number of its last record and `<timestamp>` is the UTC rotation time in
  `YYYY-MM-DDTHH-MM-SSZ` form (colons replaced for Windows compatibility).
- `format-version` contains the integer format version and a newline. A reader that does
  not know the version refuses to verify and says so.
- Each line is one UTF-8 JSON object, no indentation, terminated by `\n`. A line longer
  than 1 MiB is a format error; a well-formed record is far smaller.

## Record

```json
{
  "v": 1,
  "seq": 10423,
  "ts": "2026-01-15T09:30:12.345Z",
  "prev": "sha256:9f1c...",
  "hash": "sha256:4d2a...",
  "actor": {
    "id": "bob@example.com",
    "method": "cloudflare",
    "roles": ["developer"],
    "ip": "203.0.113.7"
  },
  "action": "files.write",
  "target": {
    "path": "MyRpgPlugin/config.yml",
    "revision": "sha256:a1b2..."
  },
  "result": "success",
  "reason": null,
  "request-id": "01J00000000000000000000000",
  "deployment-id": null,
  "details": {
    "bytes": 2048,
    "snapshot": "snap_01J00000000000000000000001"
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `v` | integer | yes | Record format version. Currently `1`. |
| `seq` | integer | yes | Global record number, starting at `1` for a fresh log and increasing by exactly one. It continues across rotation. |
| `ts` | string | yes | RFC 3339 UTC timestamp with milliseconds and an explicit `Z`. |
| `prev` | string | yes | `hash` of the previous record, or `sha256:` + 64 zeros for the first record of a fresh log. |
| `hash` | string | yes | Chain hash of this record; see below. |
| `actor` | object | yes | Verified identity at the time of the action. |
| `actor.id` | string | yes | Verified identity: email, service-token common name, token owner, or `system` for an automatic action. Never taken from an unverified header. |
| `actor.method` | string | yes | `cloudflare`, `token`, `local-onetime`, or `system`. |
| `actor.roles` | array of string | yes | Effective role names, sorted. Empty when the identity has no role. |
| `actor.ip` | string | no | Source address after trusted-proxy resolution. Omitted for `system` transitions. |
| `action` | string | yes | Dotted action identifier from the catalog in this document. |
| `target` | object | no | What was acted on. Omitted for actions without a single target. |
| `target.path` | string | no | Sandbox-relative path. Never an absolute host path. |
| `target.revision` | string | no | Content revision before or after the action, as documented per action. |
| `result` | string | yes | `success`, `denied`, `failed`, or `pending`. |
| `reason` | string or null | yes | Error or denial code such as `rbac.path-denied` when `result` is not `success`; `null` on success. |
| `request-id` | string or null | yes | Correlation id, matching `X-Request-Id` when the action came from an API request. |
| `deployment-id` | string or null | yes | Deployment transaction id when the action belongs to one. |
| `details` | object | yes | Action-specific, non-sensitive data. May be empty. |

Additional fields may be added within `v: 1`; a reader must ignore unknown fields. A
field listed above is never removed or re-typed within `v: 1`.

### `details` rules

`details` is where action-specific facts go. Allowed: byte counts, hashes, snapshot ids,
deployment ids, approval actors, command text, capability names, duration, attempt
counts, rotated file names, and other metadata.

Never allowed, in any field or in `details`:

- file contents or a body excerpt;
- tokens, token hashes, JWTs, cookies, shared secrets, webhook signing secrets;
- `Authorization`, `Cookie`, or `Cf-Access-Jwt-Assertion` header values;
- absolute host paths outside the configured sandbox root;
- another identity's email address when it was not already visible to the actor
  (approval records are the exception: the approver is named).

Console command text is recorded because command dispatch must be attributable. The
command policy must reject a command containing a secret before dispatch; DeployMC cannot
prevent an operator from typing a secret into a console command.

## Hash chain

The chain is a SHA-256 sequence:

```text
hash(record) = "sha256:" + lowercase_hex(
    SHA-256( JCS( record without the "hash" field ) )
)
```

- `JCS` is the JSON Canonicalization Scheme (RFC 8785), applied to the record object with
  the `hash` key removed. Canonicalization makes the digest independent of key order,
  whitespace, and number formatting.
- `prev` of record `n` is the `hash` of record `n - 1`. The first record in a fresh log
  uses 64 zeros.
- `seq` increases by exactly one. A gap is a chain error even when every `prev` link
  happens to match.
- `ts` is not allowed to decrease by more than 1 second between consecutive records
  without a `clock.anomaly` record. The value is recorded as observed and is never
  corrected silently.

Tamper evidence is not tamper proof. Anyone with write access to the host file system can
rewrite the whole file and recompute every hash, or truncate the tail. Anchors and
external export are what make such edits detectable; the chain alone cannot.

## Actions

Action identifiers are lowercase and dotted, in the same shape as API error codes. The
initial set:

| Action | target | Recorded for |
|---|---|---|
| `files.list` | `path` | Optional; recorded when a listing is denied. |
| `files.read` | `path` | Optional; recorded for download of a JAR or a protected path, and for every denial. |
| `files.write` | `path`, revision | Create or modify a text file. |
| `files.upload` | `path` | Upload a binary file or JAR. |
| `files.delete` | `path`, revision | Delete a file or directory. |
| `files.rename` | `path`, revision | Rename or move; the previous path is in `details.previous-path`. |
| `files.restore` | `path` | Restore a file from a snapshot. |
| `snapshots.create` | `path` | Automatic or manual snapshot creation. |
| `snapshots.restore` | `path` | Restore a snapshot set. |
| `snapshots.prune` | none | Manual or automatic pruning, with counts in `details`. |
| `deployments.create` | none | Deployment transaction created; `deployment-id` is set. |
| `deployments.upload` | none | Artifact uploaded to staging, with hashes. |
| `deployments.validate` | none | Validation result and report reference. |
| `deployments.approve` | none | Approval recorded, with approver. |
| `deployments.cancel` | none | Cancellation and the state it happened in. |
| `deployments.transition` | none | Every state transition, with `details.from`, `details.to`, and `details.reason-code`. |
| `deployments.rollback` | none | Rollback requested or started. |
| `deployments.verify` | none | Verification result and the checks that ran or were skipped. |
| `console.command` | none | Command dispatch, allowed or denied, with the command text. |
| `tokens.create` | none | Token created, with scope and expiry; never the token value. |
| `tokens.revoke` | none | Token revoked. |
| `webhooks.register` | none | Webhook registered or changed. |
| `webhooks.deliver` | none | Delivery failure or redelivery; success is optional. |
| `config.reload` | none | Configuration reload and changed key names, never values. |
| `audit.read` | none | Audit query or export, with the filter and row count. |
| `audit.rotate` | none | Continuation record; see below. |
| `audit.anchor` | none | Anchor record; see below. |
| `clock.anomaly` | none | Timestamp regression, with observed values. |

A new action is added with the feature that emits it. Renaming an action is a breaking
change for log consumers and requires a format-version change or a documented alias.

## Rotation and continuation

Rotation happens when the active file would exceed `audit.rotate-size-mb`. The writer:

1. finishes and flushes the current record;
2. reads the last `seq` and `hash` it wrote;
3. renames `audit.jsonl` to `audit-<seq>-<timestamp>.jsonl`;
4. creates a new `audit.jsonl`;
5. appends a continuation record as the first line of the new file.

The continuation record has `action: "audit.rotate"`, `actor` `system`, `result`
`success`, `seq` = previous last `seq` + 1, `prev` = previous file's final `hash`, and
`details`:

```json
{
  "previous-file": "audit-0000010423-2026-01-15T09-30-12Z.jsonl",
  "previous-last-seq": 10423,
  "previous-last-hash": "sha256:4d2a...",
  "format-version": 1
}
```

Because the continuation record chains to the previous file's last hash, verification
runs across files in `seq` order and treats a missing file as a chain break. A rotated
file is never edited; retention deletes whole files only after their records are older
than `audit.retention-days` and only if an anchor covers them or the operator accepts the
loss of that range.

## Anchors

An anchor is a record summarizing the chain up to a point, intended for export:

```json
{
  "v": 1,
  "seq": 10520,
  "ts": "2026-01-16T00:00:00.000Z",
  "prev": "sha256:...",
  "hash": "sha256:...",
  "actor": { "id": "system", "method": "system", "roles": [] },
  "action": "audit.anchor",
  "target": null,
  "result": "success",
  "reason": null,
  "request-id": null,
  "deployment-id": null,
  "details": {
    "first-seq": 1,
    "last-seq": 10519,
    "last-hash": "sha256:...",
    "record-count": 10519,
    "previous-anchor": null
  }
}
```

The initial design writes an anchor daily and on clean shutdown, and exposes the latest
anchor through the audit API so an operator or CI job can store it elsewhere. Publishing
anchors to an external system is a later work package; until then the documentation must
not claim that an off-host anchor exists.

## Verification

`deploymc audit verify` reads files in `seq` order and checks:

1. `format-version` is known.
2. Every line is valid UTF-8 JSON and matches the required fields.
3. `seq` increases by exactly one, with no duplicate and no gap.
4. Each `prev` equals the previous record's `hash`.
5. Each recomputed `hash` equals the stored `hash`.
6. Rotation continuity: the first record of a rotated file chains to the last record of
   the previous file and is an `audit.rotate` record.
7. Anchors match the records they claim to cover.
8. Timestamp regressions are reported as `clock.anomaly` warnings, not failures.

The command reports the first failing record by file, line, and `seq`, and exits non-zero
on a chain or hash failure. A truncated final line is reported as a partial write, not as
tampering, unless an anchor covers the missing region.

Limitations are stated in the output: without an off-host anchor, the last records can be
deleted or the whole log can be replaced by a self-consistent forgery by someone with host
file access.

## Query and export

The audit API and `audit.export` permission serve records from this log. Query filters are
`from`, `to`, `actor`, `action`, `result`, `deployment-id`, and `path`. Results are
cursor-paginated and read-only. Export streams the same JSON Lines format with the same
records, not a reformatted variant, so exported data remains verifiable.

The API never exposes `details` fields that were later classified as sensitive. If that
happens, the fix is to stop writing the field, not to filter it inconsistently at read
time.

## Privacy

The log contains identities and source addresses because it is the accountability record.
Host operators must:

- restrict file-system access to the audit directory;
- set `audit.retention-days` to a period consistent with their policy and law;
- tell users that authenticated actions are audited;
- treat an export as personal data.

## Related documents

- [Configuration schema](../config/config.schema.json) (`audit.*` keys)
- [Error code catalog](../api/errors.md) (reason codes)
- [Threat model](../security/threat-model.md)
- [API layering](../../docs/architecture/api-layering.md)
- Master specification, Part 7.5 and section 24.3.
