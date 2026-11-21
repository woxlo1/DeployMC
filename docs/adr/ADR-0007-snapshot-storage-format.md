# ADR-0007: Snapshot storage format

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** maintainers
- **Related:** [Deployment state machine](../architecture/deployment-state-machine.md), [ADR-0006](ADR-0006-sandbox-and-protected-paths.md), [ADR-0008](ADR-0008-deployment-state-machine.md)

## Context

DeployMC must preserve the previous content of a file before an edit, delete, rename,
restore, or deployment, and it must be able to restore that content later. The history
needs to be durable across restarts, space-efficient enough for repeated deployments of
large JARs, verifiable for corruption, and safe to prune while a deployment is in
progress.

The data directory lives on the host file system and may be on Windows, where file
locking and atomic rename behavior differ from Linux. It may be backed up by the
administrator, but DeployMC cannot assume snapshots are a substitute for world backups.

## Problem

How should file history be stored so that it is deduplicated, restorable, integrity
checkable, and safe under concurrent operations?

## Considered alternatives

### Option A — Full copies per version

- Copy the whole file for every change.
- Advantages: simple; easy to inspect.
- Disadvantages: large JARs and frequent deployments consume disk quickly; no
  deduplication; slow to copy.

### Option B — A Git repository per data directory

- Commit each change with Git objects.
- Advantages: mature object store, diff, and integrity checks.
- Disadvantages: Git process/dependency overhead; awkward concurrent access from a
  plugin; commit semantics do not match deployment transactions; Windows file locks and
  index corruption are new failure modes; harder to bound and audit precisely.

### Option C — Content-addressed blobs plus JSON metadata

- Store immutable blobs keyed by SHA-256, with per-path indexes and snapshot records.
- Advantages: automatic deduplication; simple integrity verification; portable; no
  database dependency; can be inspected and backed up.
- Disadvantages: garbage collection and index concurrency must be designed; many small
  files; metadata can become inconsistent if writes are not careful.

### Option D — A relational database

- Store blobs or paths in an embedded database.
- Advantages: transactions and queries.
- Disadvantages: a new dependency and native-library risk in a Minecraft plugin;
  database file locking; less transparent to administrators; overkill for the initial
  scale.

## Decision

Use a content-addressed store under `.deploymc/history/`:

```
.deploymc/
  history/
    format-version
    objects/ab/cdef...        immutable SHA-256 blobs, optionally compressed
    index/
      files/<path-hash>.json  ordered snapshot records for one path
      snapshots/<id>.json     snapshot metadata
    tmp/
  staging/
  audit/
  deployments/<id>.json
  data.lock
```

A snapshot record contains:

- id, path, kind (`file`, `jar`, `deleted`), hash, size, and mode;
- creation time, actor, reason (`edit`, `delete`, `rename`, `deploy`, `restore`);
- deployment id when applicable;
- parent snapshot id for the same path;
- plugin metadata (name, version, descriptor summary) for JARs.

Rules:

- Blobs are immutable and addressed by the SHA-256 of their content.
- Writes are temp-then-rename in the same directory; the store never exposes a partial
  blob or index entry.
- `format-version` is checked at startup. An unknown newer version fails closed with a
  clear error; a migration path is documented before changing it.
- A single-instance lock prevents two servers from sharing a data directory.
- Garbage collection removes unreferenced blobs only after a grace period and never while
  a deployment or restore references them.
- Retention defaults are 50 versions per file, 90 days, and 2 GiB total, whichever
  triggers first, but the newest three versions are never pruned automatically.
- `verify` re-hashes stored blobs and reports corruption; a repair/report command is
  planned.
- Restoring a snapshot first snapshots the current state, so a restore is reversible.
- Format version 1 is `1\n` in `format-version`. An absent file in an empty store is initialized;
  a higher or unparseable version fails closed. Future changes require a documented migration.
- Snapshot ids match `snap_[0-9a-z]{26}`. Blobs are exact uncompressed bytes addressed by a
  `sha256:<64 lowercase hex>` hash; metadata files are atomic JSON records.
- Garbage collection requires verify to pass, no active deployment, the data-directory lock, a
  24-hour grace period for unreferenced blobs, and a complete reachability scan before deletion.
- Snapshots may contain secrets from configuration files. They are protected by the same
  RBAC path scopes as the original file and are never exposed through an unauthenticated
  route. Encryption at rest is not provided by default; the host file system is trusted.

Compression is deliberately excluded from format 1. It will be chosen by a benchmark in a
later work package and requires a format migration; no native dependency is acceptable in the
shared Java 8 path.

## Consequences

### Positive

- Deduplication makes repeated deployments and large JAR histories affordable.
- Integrity checks are simple and local.
- The format is inspectable and backup-friendly.
- No database or native dependency is required.

### Negative and costs

- Garbage collection must be conservative and tested; a bug can lose history.
- Per-path indexes need per-path locking and atomic updates.
- Many small files can be slow on some file systems; the layout may need sharding or
  packing later.
- Metadata and blobs can drift after a crash; startup verification and a repair command
  are required.
- Compression, if added, changes the blob format and needs a migration.

### Follow-up

- The store contract in
  [`specification/snapshot/snapshot-store.md`](../../specification/snapshot/snapshot-store.md) is
  implemented in `core.snapshot`; integrate it with a host-controlled deployment engine next.
- Tests cover deduplication, retention, GC during an active deployment, corruption detection,
  restore reversibility, and the single-instance lock; add server-backed integration coverage.
- Benchmark compression options before changing the format.
- Document that snapshots are not a backup of worlds or player data.

## Evidence and validation

- The layout and rules follow master specification section 24.4. Format 1, ids, metadata fields,
  retention invariants, GC preconditions, and verify/repair behavior are normative in
  [`specification/snapshot/snapshot-store.md`](../../specification/snapshot/snapshot-store.md).
- The format-1 store was implemented in Java 8 `core.snapshot` and verified locally on 2026-11-21:
  25 snapshot tests passed inside a full Gradle build of 230 tests (0 failures, 0 errors, 1 skipped
  symlink test), `verifyJava8` passed, and `node tools/build/verify-all.cjs` reported `PASS` for all
  seven toolchains. Spec lint and i18n checks passed.
- **Not yet verified:** no REST endpoint, transport handler, running server, UI, or in-game command
  consumes the store; no live server backup/restore was exercised. Compression stays out of format 1.
- The single-instance lock is also relevant to deployment recovery; see
  [ADR-0008](ADR-0008-deployment-state-machine.md).

## References

- Master specification, sections 7.3 and 24.4.
- [Deployment state machine](../architecture/deployment-state-machine.md).
- [Threat model](../../specification/security/threat-model.md) for residual host-access
  risk.
