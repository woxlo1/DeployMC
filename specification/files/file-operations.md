# File operations contract

> Status: 2026-11-20. The `core.files` service described here is implemented and unit-tested.
> No REST endpoint, running server, or UI consumes it yet; WP-2.6 will add the thin HTTP adapter.

This document is normative for the service boundary introduced by WP-2.2. It does not claim that
snapshots, validators, or file endpoints are implemented.

## Trust boundary

- Every request path is a sandbox-relative string. The service resolves it with `SandboxResolver`
  before any file-system access. Raw host paths are never accepted.
- `SandboxPath` values minted by another resolver instance are rejected by the resolver.
- Listing omits children rejected by the resolver, so protected entries, deny-pattern entries, and
  reparse points are not revealed. Returned metadata contains only sandbox-relative paths.
- Symlinks and junctions are rejected by default on every host. A host policy may allow symlinks that
  stay inside the root; the resolver re-checks containment and the opened file key before use.

## Revisions and ranges

- A file revision is `sha256:` followed by the lowercase hexadecimal SHA-256 digest of the complete
  file bytes. Directories have no revision. The HTTP layer can use the revision as the ETag source.
- `stat` and `read` compute the revision from current bytes. A range read returns the requested byte
  window but the revision of the whole file, so a stale `If-Match` is still detected.
- Range offsets are inclusive, ends are exclusive, negative values are rejected, and a bounded range
  beyond the file is rejected with `files.invalid-range`.
- A write to an existing file requires the current revision. A missing revision returns
  `request.precondition-required`; a mismatched revision returns `files.revision-conflict` and reports
  the current revision. A write to a missing file may create it without an expected revision.

## Atomic write

1. Resolve and validate the sandbox path, acquire the in-process stripe lock, and read the current
   file state.
2. Reject an existing target when no expected revision was supplied; reject a stale revision.
3. Capture a snapshot of the old file when the target exists.
4. Create a temporary file in the target's own directory, with owner-only POSIX permissions where
   supported.
5. Stream bytes into the temporary file while hashing them. Enforce the host-controlled byte limit
   before writing each chunk.
6. Force the temporary file to stable storage. Re-check the target's revision and file identity.
7. Commit with one `ATOMIC_MOVE` (plus `REPLACE_EXISTING` for an overwrite). If the platform cannot
   perform an atomic move, fail with `files.atomic-move-unsupported`; never fall back to a
   non-atomic replacement.
8. Force the parent directory where supported, then return the new revision from a fresh `stat`.

A process termination before step 7 leaves the old file; after step 7 it leaves the complete new
file. Temporary files are cleaned up in a `finally` block. Directory fsync is best effort because
the JDK does not expose it portably; the content fsync and single atomic commit are mandatory.

## Concurrency

- In-process operations use 256 fixed stripes keyed by the normalized relative path. A move locks
  both paths in sorted stripe order.
- Cross-process writers are not serialized. Revision and file identity are re-checked immediately
  before the atomic commit, so a conflicting external overwrite is detected with
  `files.revision-conflict` or `files.path-rejected`. This is detection, not a cross-process lock.
- A deterministic crash-injection test covers termination immediately before and after the atomic
  commit; it does not launch and kill an operating-system process.

## Uploads, listing, and limits

- Uploads stream into the same temporary-file path as writes; content is hashed while streaming and
  the caller's declared size, when supplied, is checked up front. The effective upload maximum is
  the smaller of `max-upload-size` and `max-file-size`.
- Host-controlled defaults are 10 MiB per file, 64 MiB per upload, 5,000 listing entries, and 1,000
  deletion entries. Request data can never raise these values.
- Listing is sorted by sandbox-relative path and caps accepted entries at `max-list-entries`.

## Destructive operations and snapshots

- Overwrite, move, and delete require a `SnapshotHook`. If none is configured, the operation fails
  with `files.snapshot-required` before any file is changed. The hook must return successfully
  before the mutation starts; a failure returns `files.snapshot-failed` and the files remain intact.
- WP-2.3 defines the durable implementation in
  [snapshot-store.md](../snapshot/snapshot-store.md). It is deliberately not a fake or in-memory
  snapshot store.
- A move refuses an existing destination unless overwriting is explicitly requested. Both the source
  tree and an overwritten destination tree are captured first.
- Recursive deletion requires the caller to supply the current recursive entry count, including the
  root and every descendant file or directory. A mismatch returns
  `files.delete-confirmation-required` with the actual count. A tree larger than the deletion cap
  returns `files.delete-limit-exceeded` before any snapshot or deletion.
- Every captured entry is re-validated immediately before deletion or move. Entries are deleted
  deepest-first, then the parent directory.

## Error mapping

See [api/errors.md](../api/errors.md). Sandbox rejections map to `files.path-rejected`, except
protected paths, which map to `files.protected`. Localized English and Japanese diagnostic catalogs
live beside the Java implementation; stable codes are the API-facing values.
