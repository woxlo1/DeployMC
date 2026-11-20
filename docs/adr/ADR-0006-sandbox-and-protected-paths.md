# ADR-0006: Sandbox design and protected paths

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** maintainers
- **Related:** [Threat model](../../specification/security/threat-model.md), [API layering](../architecture/api-layering.md), [ADR-0007](ADR-0007-snapshot-storage-format.md)

## Context

DeployMC's main feature is remote access to a server's plugin directory. Path traversal,
symlinks, junctions, Windows device names, alternate data streams, Unicode normalization,
and time-of-check/time-of-use races are direct paths to reading or overwriting files
outside the intended root. A single mistake can compromise the host or the server.

The sandbox must behave consistently on Linux, macOS, and Windows, including the case
where a repository created on one OS is served on another. The host administrator must
be able to protect DeployMC's own data and configuration even from an API administrator.

## Problem

How should user-supplied paths be resolved and enforced so that no operation can escape
the configured root or modify protected data?

## Considered alternatives

### Option A — `root.resolve(input).normalize().startsWith(root)`

- The common shortcut.
- Advantages: short and familiar.
- Disadvantages: vulnerable to symlink/junction escapes, Windows short names, alternate
  data streams, case-insensitive file systems, Unicode normalization, and TOCTOU races.
  Rejected.

### Option B — String prefix checks against an absolute path

- Compare the final path string with the root prefix.
- Advantages: simple.
- Disadvantages: `/srv/plugins-evil` starts with `/srv/plugins`; separator and case rules
  differ by platform. Rejected.

### Option C — Rely on OS permissions or a container

- Run the server in a container/chroot and let the OS enforce boundaries.
- Advantages: strong isolation when configured correctly.
- Disadvantages: not available to a plugin; the host may run a bare server; does not
  protect DeployMC's own data from API users; cannot be assumed. Useful as defense in
  depth, not as the only control.

### Option D — A single resolver with canonicalization, containment re-check, and
protected-path rules

- Resolve to a real path, reject dangerous segments and reparse points, verify
  component-wise containment, and re-check after opening.
- Advantages: works in-process on every platform; explicit rules can be tested and
  fuzzed; handles Windows-specific hazards.
- Disadvantages: complex; some legitimate symlink layouts are rejected; requires
  platform-specific tests and careful maintenance.

## Decision

Implement the sandbox as one component and make it the only way to turn a request path
into a file operation. The algorithm follows master specification section 33.1:

1. Reject null, empty (except an explicit root), NUL, control characters, over-long
   paths, and invalid Unicode.
2. Normalize to NFC for comparison keys.
3. Treat both `/` and `\` as separators on every platform.
4. Reject `..`; drop `.` and empty segments; reject Windows-invalid segments everywhere
   (reserved device names, trailing dot or space, `:`, invalid characters).
5. Join with the root's real path, computed at startup and re-validated on every resolve.
6. Inspect each existing ancestor with `lstat`. Reject symlinks, junctions, and reparse
   points by default; an explicitly configured policy may resolve and re-check
   containment instead.
7. Compute the real path of the deepest existing ancestor plus the remaining segments and
   assert component-wise containment under the real root, not a string prefix.
8. Compare the normalized, case-folded path against protected patterns
   case-insensitively.
9. Return a `SandboxPath` containing the relative normalized path, absolute real path,
   root identity, and the file key of the deepest existing ancestor. Callers can only
   perform operations through APIs that accept `SandboxPath`.
10. After opening a file, re-check the canonical path and file key; reject a changed key
    as a same-path replacement. Use `NOFOLLOW_LINKS` where available.

Protected paths, non-editable through the API even for administrators:

- the `.deploymc/` data directory (history, staging, audit, deployment records, lock);
- DeployMC's own `config.yml` and token/secret stores;
- any configured deny patterns.

Writes to DeployMC's own configuration are local host operations only. The sandbox root
is configured only by the host administrator and cannot be widened through the API.

Limits are enforced by the file layer, not only by the resolver: maximum file size,
upload size, directory entries per listing, path length, and concurrent operations.

## Consequences

### Positive

- One auditable component owns path safety.
- Windows and Linux behavior is intentionally identical for repository paths.
- Protected data cannot be edited through the API even by an admin role.
- A path traversal corpus and fuzzing can target a narrow surface.

### Negative and costs

- Legitimate symlinked plugin directories are rejected by default; administrators must
  use a real directory or an explicit policy.
- Canonicalization adds file-system calls and latency.
- Windows-specific rules reject some names that would be legal on Linux; this is
  intentional for portability.
- TOCTOU cannot be eliminated on every platform; the open-then-verify step narrows it,
  and the residual risk must be documented.

### Follow-up

- Done: build a deterministic traversal payload corpus (2,736 reject and 29 allow paths) and run it
  through unit tests and the scheduled Ubuntu/Windows fuzz workflow.
- Done: fuzz the resolver with a seeded Java test.
- Still open: expand real-host junction and reparse-point coverage; add NFC/NFD filename preservation
  cases, concurrent rename/delete race tests, and broader case-folding coverage.
- Keep the protected-path list in the configuration schema and in tests.

## Evidence and validation

- The algorithm is specified in master specification sections 9.1 and 33.1.
- WP-2.1 implements the resolver in `core/src/main/java/io/github/woxlo1/deploymc/core/sandbox/`,
  with an injectable `SandboxFileSystem`, resolver-only `SandboxPath`, stable rejection codes, and
  English/Japanese message catalogs.
- On 2026-11-20, the local corpus generator produced 2,736 reject and 29 allow payloads. The
  sandbox-only Gradle run completed 16 tests with 15 passed and 1 skipped; the skipped test needs
  symlink privileges unavailable on this host. `node tools/build/verify-all.cjs` reported `PASS` for
  all seven toolchains. Implementation PR #153 was awaiting required CI checks and merge when this
  evidence was recorded.
- The corpus and fuzz workflow have not yet run on a scheduled GitHub Actions event, and no file API
  or running server consumes `SandboxPath`. Exact-byte preservation for an existing NFD filename is
  not implemented; comparison keys are NFC.
- The threat model identifies path escape as a primary attack path and records the implemented
  controls and residual risk in A2.

## References

- Master specification, sections 9.1 and 33.1.
- [Threat model](../../specification/security/threat-model.md) draft.
- [Architecture overview](../architecture/overview.md) for data ownership.
