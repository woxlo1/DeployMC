# Deployment State Machine

> **Status: design narrative (2026-11-17).** This is the intended behavior of a
> deployment transaction. No deployment engine exists yet, and nothing here has been
> executed against a real server. The formal, machine-checkable version will live in
> `specification/deployment/state-machine.md` during WP-0.5; if the two disagree, the
> specification wins and this document is corrected.

## Why a state machine

A deployment can replace files, restart the JVM, fail after the restart, and need to
undo itself. A best-effort sequence of copy-and-restart steps cannot recover safely when
the process dies halfway through. DeployMC therefore treats a deployment as a persisted
transaction:

- every state is written before the side effect it authorizes;
- every transition has one documented cause;
- on startup, unfinished transactions are recovered deliberately;
- a failed verification rolls back from snapshots instead of leaving a mixed state.

## States

| State | Kind | Meaning | Target file side effects allowed |
|---|---|---|---|
| `CREATED` | Initial | Record exists with an idempotency key and requester; artifacts not uploaded | No |
| `UPLOADING` | Active | Artifacts are being uploaded to staging | No |
| `VALIDATING` | Active | Static validation of JARs, manifests, paths, and limits | No |
| `REJECTED` | Terminal | Validation failed; the report explains why | No |
| `STAGED` | Active | Artifacts are in staging and hashes match the manifest | No |
| `AWAITING_APPROVAL` | Waiting | Approval is required and has not been granted | No |
| `APPROVED` | Transient | Approval was recorded; the transaction is about to continue | No |
| `SNAPSHOTTING` | Active | Previous content of every target path is being recorded | No target writes; data-directory writes |
| `APPLYING` | Active | Manifest operations are being applied | Yes |
| `RESTART_PENDING` | Active | A restart was requested; notifications/countdown may be running | Files already applied |
| `RESTARTING` | Across process | Persisted immediately before the restart request; the JVM may exit | Files already applied |
| `VERIFYING` | Active | After startup, health checks confirm the new state | Files already applied |
| `SUCCEEDED` | Terminal | Verification passed and the transaction is complete | Final state |
| `FAILED` | Failure | A failure was detected; rollback is required if target files changed | Depends on origin |
| `ROLLING_BACK` | Active | Previous snapshots are being restored | Yes |
| `ROLLED_BACK` | Terminal | Rollback completed and was verified | Previous state restored |
| `ROLLBACK_FAILED` | Failure | Rollback could not complete; safe state is being entered | Unknown |
| `CANCELLED` | Terminal | The requester cancelled before `APPLYING` | No |
| `SAFE_STATE` | Terminal until operator action | DeployMC is reachable but the deployment cannot be stabilized automatically | Unknown |

`FAILED` is terminal only when no target path was modified (for example, validation or
staging failure). When it follows `APPLYING`, `RESTARTING`, or `VERIFYING`, it must
transition to `ROLLING_BACK`.

`SAFE_STATE` is terminal for the transaction. Resolving it requires an operator action
that creates a new transaction or explicitly marks the old one resolved; the machine
does not silently resume.

## Transitions

Any transition not listed here is a bug. The implementation must reject it and enter
`SAFE_STATE` if the persisted record cannot be trusted.

| From | To | Trigger | Notes |
|---|---|---|---|
| `CREATED` | `UPLOADING` | Client starts upload | Record created with `Idempotency-Key` |
| `CREATED` | `CANCELLED` | Requester cancels | No side effects |
| `UPLOADING` | `VALIDATING` | All artifacts committed | Hashes recorded |
| `UPLOADING` | `CANCELLED` | Requester cancels | Staging cleaned up |
| `UPLOADING` | `FAILED` | Upload/storage error | Terminal: no target writes |
| `VALIDATING` | `STAGED` | Validation passed | Report persisted |
| `VALIDATING` | `REJECTED` | Validation failed | Terminal |
| `VALIDATING` | `FAILED` | Validator internal error | Terminal: no target writes |
| `VALIDATING` | `CANCELLED` | Requester cancels | Terminal |
| `STAGED` | `AWAITING_APPROVAL` | Policy requires approval | Requester cannot approve their own deployment |
| `STAGED` | `SNAPSHOTTING` | No approval required | |
| `STAGED` | `CANCELLED` | Requester cancels | Staging cleaned up |
| `AWAITING_APPROVAL` | `APPROVED` | Authorized approver approves | Approval actor and time persisted |
| `AWAITING_APPROVAL` | `CANCELLED` | Requester or approver cancels | |
| `APPROVED` | `SNAPSHOTTING` | Engine continues | Transient; persisted for audit |
| `SNAPSHOTTING` | `APPLYING` | All snapshots committed | Snapshot ids recorded |
| `SNAPSHOTTING` | `FAILED` | Snapshot failure | Terminal: target files untouched |
| `APPLYING` | `RESTART_PENDING` | Manifest applied and restart requested | |
| `APPLYING` | `VERIFYING` | Manifest applied, no restart needed | In-process verification |
| `APPLYING` | `FAILED` | Operation failure | Then `ROLLING_BACK` |
| `RESTART_PENDING` | `RESTARTING` | Countdown finished and shutdown accepted | Persist `RESTARTING` before requesting restart |
| `RESTART_PENDING` | `FAILED` | Restart request rejected | Then `ROLLING_BACK` |
| `RESTARTING` | `VERIFYING` | Startup recovery finds the transaction | Required mapping on JVM start |
| `VERIFYING` | `SUCCEEDED` | All required checks passed | Terminal |
| `VERIFYING` | `FAILED` | Timeout or failed check | Then `ROLLING_BACK` |
| `FAILED` | `ROLLING_BACK` | Any target file was modified | Automatic |
| `ROLLING_BACK` | `ROLLED_BACK` | Previous state restored and verified | Terminal |
| `ROLLING_BACK` | `ROLLBACK_FAILED` | Restore/verification failed | |
| `ROLLBACK_FAILED` | `SAFE_STATE` | Attempt limit reached or recovery impossible | UI remains reachable |
| `CREATED`..`STAGED`, `AWAITING_APPROVAL` | `CANCELLED` | Requester cancels | Only before `APPLYING` |

Cancellation after `APPLYING` begins is not allowed. A client that wants to undo an
applied deployment must create a rollback request, which is a new transaction that
restores a chosen snapshot set.

## Persistence record

Every transition is appended to the deployment record before the next side effect. A
record contains at least:

- deployment id, idempotency key, requester, source (`ui`, `api`, `cli`, `action`);
- manifest: ordered operations with target path, operation type, expected hash, and
  artifact id;
- validation report: checks run, results, warnings, and rejected reasons;
- snapshot ids for every target path, including "did not exist" markers;
- approval actor, role, and time when approval is required;
- restart mode and the exact restart request that was made;
- state history: state, timestamp, actor (`system` for automatic transitions), reason
  code, and attempt counters;
- verification results: each check, its outcome, and the timeout used;
- rollback details: snapshots used, operations attempted, and the final result.

Records are written atomically (temp file, fsync, rename). A corrupt or unreadable record
is never guessed at: the deployment enters `SAFE_STATE` and the error is reported.

## Invariants

1. Exactly one deployment may be active per data directory at a time. A second request
   is rejected with a busy/conflict error rather than queued indefinitely. Parallel
   deployments by disjoint path may be considered later; they are not a Phase 5 goal.
2. Every state-changing step is persisted before its side effect starts.
3. No destructive target operation occurs before its snapshot is committed.
4. Every terminal transaction has exactly one terminal state.
5. `RESTARTING` and `VERIFYING` may span a JVM restart; recovery maps a persisted
   `RESTARTING` to `VERIFYING` before any other deployment work is accepted.
6. Snapshots needed by an in-progress deployment are never garbage-collected.
7. A repeated request with the same idempotency key returns the existing deployment; it
   does not create a second one.
8. Approval, when required, cannot be given by the requester.
9. The automatic rollback attempt counter is bounded (initial default: 2). Exceeding it
   enters `SAFE_STATE`.
10. No state transition may be inferred from log text alone. Log observation can inform
    a check, but the persisted record is authoritative.

## Startup recovery

Recovery runs after the data lock is acquired, the snapshot store is open, and the
platform is available, but before the transport accepts deployment requests.

| Persisted state | Recovery action |
|---|---|
| `UPLOADING`, `VALIDATING`, `STAGED`, `AWAITING_APPROVAL` | Keep the record; clean stale staging after a grace period; no target changes |
| `APPROVED`, `SNAPSHOTTING` | Re-verify snapshots; if incomplete, mark `FAILED` (no target writes) |
| `APPLYING` | Verify each operation's hash; resume forward only if every operation is idempotent and the observed state matches; otherwise `FAILED` -> `ROLLING_BACK` |
| `RESTART_PENDING` | If the process is still alive, resume the countdown; otherwise map to `VERIFYING` |
| `RESTARTING` | Map to `VERIFYING` and run checks |
| `VERIFYING` | Re-run checks with the remaining timeout |
| `ROLLING_BACK` | Resume rollback from the recorded progress |
| `ROLLBACK_FAILED` | Enter `SAFE_STATE` |
| Terminal states | Report only; no action |

If the server never comes back after `RESTARTING`, no DeployMC code runs. The record and
the last known state remain on disk for the operator. This is why restart
`shutdown-only` depends on an external supervisor (systemd, Docker, Pterodactyl, or
similar) and why DeployMC must not claim a restart it cannot observe.

## Restart modes

Restart is platform and hosting dependent. The initial modes are:

| Mode | Behavior | Required condition |
|---|---|---|
| `shutdown-only` | Request shutdown; an external supervisor starts the process again | Supervisor configured; `server.restart.script` or documented external mechanism |
| `spigot-restart-script` | Invoke the platform's restart script/mechanism | Verified per platform and hosting setup |
| `none` | Apply files and verify what can be verified without restarting | No claim that a plugin reload occurred |

Hot reload of arbitrary plugins is not a restart mode. It is unsafe, not a default, and
would require a separate experimental feature with its own documentation.

The initial numeric defaults are a 30-second countdown, a 5-minute minimum interval
between restarts, and a 180-second verification timeout. They are configuration values,
not guarantees, and are documented with their rationale in the configuration reference.

## Verification and rollback

Verification runs after the restart (or in-process when no restart is required). It
checks:

- the process is alive and DeployMC is serving requests;
- the deployed plugin is present with the expected hash and (when the platform supports
  enumeration) is enabled;
- no new enable exception is attributable to the deployed plugin;
- optional administrator-defined checks (console pattern, command success, HTTP probe)
  pass within the timeout.

A check that cannot run because the capability is missing is reported as not run. It is
not counted as a pass for a safety-critical claim.

When verification fails:

1. Persist `FAILED`, then `ROLLING_BACK`.
2. Snapshot the current (failed) state before changing it, so rollback is reversible.
3. Restore the previous artifact set from snapshots, including deletions and renames.
4. Restart if the original deployment restarted.
5. Verify the restored state.
6. Persist `ROLLED_BACK` on success.

If rollback fails, or the attempt limit is exceeded, persist `ROLLBACK_FAILED` and enter
`SAFE_STATE`. The Web UI stays reachable, the failure report names the exact files and
hashes involved, and automatic retries stop.

## JAR replacement rules

- A loaded JAR is never overwritten in place on Windows.
- Prefer the platform update folder when the capability is available.
- Otherwise stage the new file and swap it at restart time, before the old JAR is loaded
  again, or while the JAR is confirmed unloaded.
- Record both the old and new file names and hashes, including the case where a version
  change also changes the file name.
- If no safe strategy exists, fail the deployment with a manual remedy. Never write a
  partial JAR.

See [ADR-0008](../adr/ADR-0008-deployment-state-machine.md) and the Windows notes in the
master specification (Part 9.4).

## Events and observability

Every transition emits an event and an audit entry. WebSocket channels and webhooks
receive `deployment.*` events with the deployment id, from/to state, timestamp, and
reason code. A client that reconnects can fetch the current record and state history; it
does not need to replay every event to know the truth.

Sensitive details (tokens, file contents, full host paths outside the configured root)
are not included in events or webhook payloads.

## Testing plan

The implementation is not complete until these tests exist and run:

- A model-based test that enumerates the transition table and asserts the invariants:
  no lost snapshots, no orphaned staging files, exactly one terminal state.
- Fault injection at every state, including process death during `APPLYING`,
  `RESTARTING`, `VERIFYING`, and `ROLLING_BACK`.
- Restart recovery tests with a persisted record and a fake platform that simulates a
  new JVM.
- Idempotency, approval, cancellation, and concurrency tests.
- Windows and Linux runs for file replacement and rollback.
- A real-server test in the compatibility harness before any platform is called
  supported.

## Open questions

- The exact forward-resume rule for `APPLYING` needs the operation manifest and
  idempotency metadata defined in the formal specification.
- The approval policy shape (roles, expiry, two-person rule) is not finalized.
- The graceful behavior when the data directory is on a file system without atomic
  rename needs an explicit fallback and test.
- `SAFE_STATE` operator actions need a documented API and UI before Phase 5 ships.
