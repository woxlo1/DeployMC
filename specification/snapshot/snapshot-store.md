# Snapshot store contract

> Status: 2026-11-21. This document defines the normative WP-2.3 snapshot store contract.
> It does not claim that an implementation exists.

This document specifies the durable, content-addressed history store used by
`core.files.SnapshotHook`. The store is host-controlled infrastructure: it never accepts raw host
paths from a request, and it is outside the editable sandbox. Snapshots may contain secrets from
configuration files; they are never served by an unauthenticated route and are subject to the same
authorization as the source path when REST endpoints are added.

## Storage location and sandbox exclusion

- The data directory is supplied by the host as `dataRoot`, never by a request. The history store is
  `dataRoot/.deploymc/history/`.
- `.deploymc` is protected by `SandboxResolver` on every host. No file-operation request can read,
  write, list, move, or delete the store.
- The store stores only sandbox-relative paths from `SnapshotEntry`. A host path from
  `SnapshotRequest.rootPath()` is used solely to identify the owning sandbox root. An entry with an
  absolute path, a drive prefix, a backslash separator, a dot segment, a `%` escape, or a path
  outside that root is rejected before any store write.
- A store instance owns the sandbox root it was opened with. `capture` rejects a request whose root
  differs from that root.
- The complete set of snapshot files is not a substitute for world, player-data, or system backups.

## Format version

- `history/format-version` contains the ASCII line `1\n` for the contract in this document.
- A missing `format-version` is accepted only when the history directory contains no other files;
  the store creates it before any other write. A missing file in a non-empty store is corruption.
- Version `1` is accepted. A higher or unparseable version fails closed with
  `snapshots.store-unavailable`; the store performs no reads, writes, retention, restore, GC, verify,
  or repair after that failure.
- A future format change must add a migration specification and bump the version. Silent upgrades
  are prohibited.

## Layout

```
.deploymc/
  history/
    format-version
    objects/ab/cdef...        immutable SHA-256 blobs; 64 lowercase hex characters
    index/
      files/<path-hash>.json  per-path ordered snapshot records
      snapshots/<id>.json     snapshot metadata records
    tmp/                      same-directory staging files for atomic commits
  data.lock                   single-instance lock for the whole data directory
```

- `path-hash` is the lowercase hexadecimal SHA-256 of the UTF-8 bytes of the normalized
  sandbox-relative path. NFC normalization is applied before hashing. Paths use `/` separators in
  metadata and hashes; host-specific separators are never persisted.
- `objects/<first two hex characters>/<remaining 62 hex characters>` is the only blob location. A
  blob is the exact uncompressed file bytes; content hashing is over those bytes, not over a
  compressed representation. Compression is not part of format 1.
- A snapshot id matches `^snap_[0-9a-z]{26}$` and is unique within the store. The id is opaque to
  clients and is not derived from a host path.
- `tmp/` holds staging files only. A file in `tmp/` is never referenced by an index or served as a
  snapshot. Startup removes abandoned staging files after the store lock is held.

## Content-addressed writes

1. Validate the format version and take the data-directory lock before any operation.
2. Normalize and validate every entry path.
3. Compute SHA-256 over the exact bytes and compare it with the supplied `sha256:` revision when one
   is present. A mismatch rejects the whole capture before any metadata is committed.
4. Write a staging file in `objects/ab/`, force its content to stable storage, and commit with one
   `ATOMIC_MOVE` into the final blob path. A platform without atomic move fails the capture;
   no non-atomic fallback is allowed.
5. If the final blob already exists, verify that its content hashes to the expected value and reuse
   it. Existing blobs are never overwritten, truncated, or renamed. A mismatch is corruption.
6. Write each metadata file to `tmp/`, force its content, then atomically move it into its final
   directory. A crash before the move leaves an unreferenced staging file; a crash after the move
   leaves a complete record.
7. Update the per-path index last. The index is an ordered array of snapshot ids, oldest first. It is
   written atomically as a whole file, never appended in place.
8. `capture` returns a capture id. Every record created by one `SnapshotRequest` shares that capture
   id and deployment id but has its own snapshot id and parent link.

## Metadata records

`index/snapshots/<id>.json` is the authoritative record for one snapshot. `index/files/<path-hash>.json`
contains the ids for that path in capture order plus the path hash, and is a rebuildable lookup index.
A mismatch between the two is corruption.

Each snapshot record contains:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Snapshot id; must equal the metadata file name. |
| `capture` | string | Id shared by all records captured by one request. |
| `path` | string | Normalized sandbox-relative path; never an absolute host path. |
| `kind` | enum | `file`, `jar`, `deleted`, or `directory`. |
| `hash` | string or null | `sha256:<64 lowercase hex>` for file and jar content; null otherwise. |
| `size` | integer | Byte length of the content; zero for non-content entries. |
| `mode` | string or null | POSIX mode as four octal digits when available; otherwise null. |
| `created-at` | string | UTC RFC 3339 instant with an explicit `Z`. |
| `actor` | string | Host-supplied actor id or `system`. |
| `reason` | enum | `edit`, `delete`, `rename`, `deploy`, or `restore`. |
| `deployment-id` | string or null | Owning deployment id when applicable. |
| `parent` | string or null | Previous snapshot id for the same path, or null. |
| `plugin-metadata` | object or null | `name`, `version`, and `descriptor-summary` for JARs. |

- The store does not infer a public URL, filesystem path, or file content from a record.
- A deleted snapshot has `kind: deleted`, `hash: null`, and `size: 0`. A directory marker has
  `kind: directory`, `hash: null`, and `size: 0`.
- `reason` maps from the internal hook reason: `before-overwrite`/`before-edit` to `edit`,
  `before-delete` to `delete`, `before-move` to `rename`, deployment-supplied reasons to `deploy`,
  and `before-restore` to `restore`. Unknown internal reasons are rejected rather than guessed.
- Records are immutable once committed. Repair may remove inconsistent records but never rewrites
  their meaning.

## Restore

`restore(snapshotId, targetPath, expectedRevision, actor)`:

1. Require an open store, a valid format version, and a sandbox resolver for the owning root.
2. Resolve `targetPath` through `SandboxResolver`; raw host paths and protected paths are rejected.
   The resolved path must match the record path.
3. Load the record and metadata. A missing record is `snapshots.not-found`; an unreadable or
   inconsistent record is `snapshots.corrupt`.
4. Verify the blob hash and size before changing the target. A mismatch is `snapshots.corrupt`.
5. Read the current target state. If `expectedRevision` is supplied and differs, return
   `snapshots.restore-conflict` without changing files.
6. Snapshot the current target state with reason `restore` before mutation. A missing target is
   captured as a `deleted` marker so the restore remains reversible.
7. Write the restored bytes with the same same-directory staging, content fsync, identity recheck,
   and single `ATOMIC_MOVE` rules as `core.files`. Restore of a deletion marker deletes only the
   target path after its current state has been captured.
8. Return the restored snapshot id and the new pre-restore snapshot id.
9. A restore never changes index records or blobs belonging to other paths.

## Retention

Host-controlled defaults are 50 versions per path, 90 days minimum age, and 2 GiB total store size.
Whichever limit triggers first applies, with these invariants:

- The newest three versions of every path are never pruned automatically.
- Records referenced by a non-terminal deployment are never pruned.
- Records newer than the minimum age are never pruned by age.
- Per-path count pruning removes the oldest records beyond the configured count.
- Total-size pruning removes eligible records oldest first until the store is under the limit.
- Pruning removes metadata records and their per-path index entries. Blobs remain until GC, so a
  prune can be reversed by a still-present index or repaired from a backup before GC runs.
- Manual prune may target a path or snapshot id but obeys the same protected-record invariants.
- A retention run reports counts of considered, protected, pruned, and failed records. Any read
  error aborts the run before deleting further records.

## Garbage collection

`gc()` is conservative and fail-closed:

```
require an open store, valid format version, and no active deployment
take the data-directory lock
prune according to the retention rules
reachable = every blob hash referenced by every remaining metadata record
candidates = blobs not in reachable whose last-modified time is older than 24 h
delete candidates and report counts
abort and report on any read or hash error; never delete on an incomplete reachability scan
```

- An active deployment is any id registered with the store from creation until a terminal state.
  Restore also registers an active operation while it writes; `gc()` fails with
  `snapshots.store-unavailable` rather than waiting indefinitely.
- The grace period is host-controlled with a 24-hour default. It prevents a just-pruned record from
  having its blob removed before administrators or repair can recover it.
- `verify` runs before a destructive GC pass. If verification finds any error, GC aborts and returns
  that report; it does not delete blobs.
- GC never deletes `format-version`, index files, or the lock file.

## Verify and repair

`verify()` returns a report with counts and stable issue codes. It checks:

- `format-version` is exactly the supported value;
- every metadata file parses and has the required fields with valid types and enumerations;
- each metadata id matches its file name and each index entry resolves to a metadata file;
- each metadata path hashes to the file name of its per-path index, and the index order is stable;
- every referenced blob exists and hashes to the record's expected value;
- every blob file name is valid and hashes to its own path;
- no `tmp/` staging file is referenced by any record.

The issue codes are `format.unsupported`, `metadata.missing`, `metadata.invalid`, `index.missing`,
`index.invalid`, `blob.missing`, `blob.corrupt`, `blob.orphan`, and `staging.unreferenced`.
Details are sandbox-relative or store-relative; no host path is exposed.

`repair()` is report-first and conservative:

- It removes unreferenced `tmp/` staging files and rebuilds a missing or inconsistent per-path
  index from valid metadata records.
- It reports, but does not delete, missing blobs, corrupt blobs, orphan blobs, invalid metadata, and
  unsupported format versions. Those conditions require an administrator decision because blind
  deletion can destroy the only remaining copy.
- It never invents content, rewrites metadata fields, or promotes a staging file to a blob.
- Every repair action and unresolved issue is included in the returned report.

## Concurrency and the single-instance lock

- `data.lock` is acquired with an OS-level exclusive file lock for the lifetime of an open store.
  A second instance in any process fails with `snapshots.store-unavailable` and a localized clear
  message. No bypass flag exists.
- Within one process, capture, restore, retention, GC, verify, and repair are serialized by a store
  lock. Per-path indexes are keyed by path hash; writes to different paths may prepare staging files
  concurrently but commit their indexes under the store lock.
- An operation that cannot acquire a lock fails closed. It never waits indefinitely and never
  performs a partial write after a lock failure.
- A data-directory lock is not a substitute for authorization. Transport handlers must still
  authenticate and authorize every snapshot operation.

## Error mapping

| Reason | Stable code |
|---|---|
| Snapshot id or metadata record is missing | `snapshots.not-found` |
| Blob, metadata, or index integrity check fails | `snapshots.corrupt` |
| Restore target changed unexpectedly | `snapshots.restore-conflict` |
| Store is closed, locked, busy with an active deployment, or has an unsupported format | `snapshots.store-unavailable` |
| Underlying host I/O fails | `files.io` |

See [api/errors.md](../api/errors.md) for HTTP status and retryability. Localized English and
Japanese diagnostic catalogs live beside the Java implementation; stable codes never change with
language.

## Evidence required before claiming implementation

The implementation is not complete until tests demonstrate store/restore round trips for file and
deletion snapshots, deduplication, corruption injection, metadata/blob drift, retention limits with
newest-three protection, GC grace and active-deployment refusal, concurrency, format-version
rejection, restore-first behavior, sandbox exclusion, and localized error diagnostics.
