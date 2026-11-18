# Deployment State Machine (Formal Specification)

> **Status: specification only (2026-11-17).** No deployment engine exists. The state
> names and transitions below are the normative target. The narrative design is
> [`docs/architecture/deployment-state-machine.md`](../../docs/architecture/deployment-state-machine.md);
> when the two disagree, this document wins and the narrative is corrected.

## Scope

This specification defines the persisted deployment transaction: its states, the only
allowed transitions, the guards that apply, what is written before each side effect, and
how startup recovery maps interrupted states. It does not define the REST resource shape
(that is added with the deployment API) or the file-replacement strategies (see the
architecture document and ADR-0008).

The transition data block below is normative and machine-readable. A model-based test
must parse it and check the invariants in this document; if a transition is missing from
the block it does not exist, and an implementation that performs it is wrong.

## Semantics

- A deployment is a transaction identified by an id. Exactly one deployment may be
  active per data directory.
- The state is part of the persisted record. A transition is written before the side
  effect it authorizes. A record is written atomically (temporary file, flush, rename).
- Every transition appends a state-history entry with state, UTC timestamp, actor
  (`system` for automatic transitions), reason code, and attempt counters.
- The reason code of a transition is `deploy.` plus its trigger id, for example
  `deploy.start-upload`. It is stable and machine-readable.
- `actor` is the requester for a client-initiated transition, the approver for an
  approval, the operator for a safe-state resolution, and `system` otherwise.
- A transition attempted from a state where it is not defined is rejected with
  `deployments.invalid-state`. If the persisted record is unreadable or contradicts
  itself, the transaction enters `SAFE_STATE` and is not guessed at.
- Cancellation is allowed only before the first target-file write. There is no
  cancellation from `APPLYING` onward; undoing an applied deployment is a new rollback
  transaction.
- Approval, when required, can never be given by the requester.

## State classes

| Class | Meaning | May modify target files |
|---|---|---|
| Initial | The record exists; nothing has been staged | No |
| Active | Work is in progress | Only `APPLYING`, `ROLLING_BACK` |
| Waiting | Blocked on a human decision | No |
| Transient | Persisted, but the engine continues immediately | No |
| Across process | The JVM may exit while the state is persisted | No |
| Terminal | The transaction is complete and never changes again | Final state only |
| Failure | A failure was detected; rollback may follow | Depends on origin |
| Safe | DeployMC is reachable but cannot stabilize automatically | Unknown; operator action required |

## Target-file write rule

Only `APPLYING` (forward) and `ROLLING_BACK` (restore) may write target files. Every
write is preceded by a committed snapshot of the content it replaces. A state that
carries `target-writes: no` must never cause a write to a target path; data-directory
writes (records, staging, snapshots, audit) are allowed in every state.

## Normative transition data

The block between the markers is parsed by `tools/spec/check-state-machine.cjs`. Keep it
valid YAML.

<!-- state-machine-data:begin -->
```yaml
version: 1
initial: CREATED

states:
  - name: CREATED
    class: initial
    terminal: false
    target-writes: no
  - name: UPLOADING
    class: active
    terminal: false
    target-writes: no
  - name: VALIDATING
    class: active
    terminal: false
    target-writes: no
  - name: REJECTED
    class: terminal
    terminal: true
    target-writes: no
  - name: STAGED
    class: active
    terminal: false
    target-writes: no
  - name: AWAITING_APPROVAL
    class: waiting
    terminal: false
    target-writes: no
  - name: APPROVED
    class: transient
    terminal: false
    target-writes: no
  - name: SNAPSHOTTING
    class: active
    terminal: false
    target-writes: no
  - name: APPLYING
    class: active
    terminal: false
    target-writes: yes
  - name: RESTART_PENDING
    class: active
    terminal: false
    target-writes: no
  - name: RESTARTING
    class: across-process
    terminal: false
    target-writes: no
  - name: VERIFYING
    class: active
    terminal: false
    target-writes: no
  - name: SUCCEEDED
    class: terminal
    terminal: true
    target-writes: no
  - name: FAILED
    class: failure
    terminal: false
    target-writes: no
  - name: ROLLING_BACK
    class: active
    terminal: false
    target-writes: yes
  - name: ROLLED_BACK
    class: terminal
    terminal: true
    target-writes: no
  - name: ROLLBACK_FAILED
    class: failure
    terminal: false
    target-writes: no
  - name: CANCELLED
    class: terminal
    terminal: true
    target-writes: no
  - name: SAFE_STATE
    class: safe
    terminal: true
    target-writes: unknown

transitions:
  - from: CREATED
    to: UPLOADING
    trigger: start-upload
    actor: client
    guard: idempotency-key-present
    emits: deployment.state
  - from: CREATED
    to: CANCELLED
    trigger: requester-cancel
    actor: client
    guard: requester-or-admin
    emits: deployment.state
  - from: UPLOADING
    to: VALIDATING
    trigger: artifacts-committed
    actor: system
    guard: all-artifact-hashes-recorded
    emits: deployment.state
  - from: UPLOADING
    to: CANCELLED
    trigger: requester-cancel
    actor: client
    guard: requester-or-admin
    emits: deployment.state
  - from: UPLOADING
    to: FAILED
    trigger: upload-error
    actor: system
    guard: none
    emits: deployment.state
  - from: VALIDATING
    to: STAGED
    trigger: validation-passed
    actor: system
    guard: validation-report-persisted
    emits: deployment.state
  - from: VALIDATING
    to: REJECTED
    trigger: validation-failed
    actor: system
    guard: validation-report-persisted
    emits: deployment.state
  - from: VALIDATING
    to: FAILED
    trigger: validator-error
    actor: system
    guard: no-target-writes
    emits: deployment.state
  - from: VALIDATING
    to: CANCELLED
    trigger: requester-cancel
    actor: client
    guard: requester-or-admin
    emits: deployment.state
  - from: STAGED
    to: AWAITING_APPROVAL
    trigger: approval-required
    actor: system
    guard: policy-requires-approval
    emits: deployment.state
  - from: STAGED
    to: SNAPSHOTTING
    trigger: approval-not-required
    actor: system
    guard: policy-does-not-require-approval
    emits: deployment.state
  - from: STAGED
    to: CANCELLED
    trigger: requester-cancel
    actor: client
    guard: requester-or-admin
    emits: deployment.state
  - from: AWAITING_APPROVAL
    to: APPROVED
    trigger: approve
    actor: approver
    guard: approver-authorized-and-not-requester
    emits: deployment.state
  - from: AWAITING_APPROVAL
    to: CANCELLED
    trigger: requester-or-approver-cancel
    actor: client
    guard: requester-or-approver
    emits: deployment.state
  - from: APPROVED
    to: SNAPSHOTTING
    trigger: engine-continues
    actor: system
    guard: none
    emits: deployment.state
  - from: SNAPSHOTTING
    to: APPLYING
    trigger: snapshots-committed
    actor: system
    guard: every-target-snapshot-recorded
    emits: deployment.state
  - from: SNAPSHOTTING
    to: FAILED
    trigger: snapshot-error
    actor: system
    guard: no-target-writes
    emits: deployment.state
  - from: APPLYING
    to: RESTART_PENDING
    trigger: manifest-applied-restart-needed
    actor: system
    guard: every-operation-verified
    emits: deployment.state
  - from: APPLYING
    to: VERIFYING
    trigger: manifest-applied-no-restart
    actor: system
    guard: every-operation-verified
    emits: deployment.state
  - from: APPLYING
    to: FAILED
    trigger: operation-error
    actor: system
    guard: none
    emits: deployment.state
  - from: RESTART_PENDING
    to: RESTARTING
    trigger: shutdown-accepted
    actor: system
    guard: countdown-finished-and-save-succeeded
    emits: deployment.state
  - from: RESTART_PENDING
    to: FAILED
    trigger: restart-rejected
    actor: system
    guard: none
    emits: deployment.state
  - from: RESTARTING
    to: VERIFYING
    trigger: startup-recovery
    actor: system
    guard: new-process-observes-persisted-restarting
    emits: deployment.state
  - from: VERIFYING
    to: SUCCEEDED
    trigger: checks-passed
    actor: system
    guard: every-required-check-passed
    emits: deployment.state
  - from: VERIFYING
    to: FAILED
    trigger: check-failed
    actor: system
    guard: check-failed-or-timeout
    emits: deployment.state
  - from: FAILED
    to: ROLLING_BACK
    trigger: target-modified
    actor: system
    guard: target-writes-occurred
    emits: deployment.state
  - from: ROLLING_BACK
    to: ROLLED_BACK
    trigger: restore-verified
    actor: system
    guard: previous-state-restored-and-verified
    emits: deployment.state
  - from: ROLLING_BACK
    to: ROLLBACK_FAILED
    trigger: restore-error
    actor: system
    guard: restore-or-verification-failed
    emits: deployment.state
  - from: ROLLBACK_FAILED
    to: SAFE_STATE
    trigger: safe-state-entered
    actor: system
    guard: attempt-limit-reached-or-unrecoverable
    emits: deployment.state

recovery:
  - from: UPLOADING
    action: keep-record-clean-stale-staging-after-grace
  - from: VALIDATING
    action: keep-record-clean-stale-staging-after-grace
  - from: STAGED
    action: keep-record
  - from: AWAITING_APPROVAL
    action: keep-record
  - from: APPROVED
    action: reverify-snapshots-or-fail
  - from: SNAPSHOTTING
    action: reverify-snapshots-or-fail
  - from: APPLYING
    action: verify-each-operation-resume-forward-only-if-idempotent-and-matching-else-fail
  - from: RESTART_PENDING
    action: resume-countdown-if-alive-else-verify
  - from: RESTARTING
    action: map-to-verifying
  - from: VERIFYING
    action: rerun-checks-with-remaining-timeout
  - from: ROLLING_BACK
    action: resume-from-recorded-progress
  - from: ROLLBACK_FAILED
    action: enter-safe-state
```
<!-- state-machine-data:end -->

## Invariants

These are checked by the model-based test in addition to the schema checks on the data
block:

1. Exactly one initial state, and it has no incoming transition.
2. Every state is reachable from the initial state through runtime transitions or is
   entered by recovery.
3. No transition starts from a state marked `terminal: true`.
4. Every non-terminal state has at least one outgoing transition or recovery action.
5. Only `APPLYING` and `ROLLING_BACK` have `target-writes: yes`.
6. Every path from `APPLYING` reaches `SUCCEEDED`, `ROLLED_BACK`, or `SAFE_STATE`; no path
   loops forever.
7. `RESTARTING` and `VERIFYING` can span a process restart, and `RESTARTING` has
   `VERIFYING` as its only runtime successor.
8. `ROLLBACK_FAILED` reaches `SAFE_STATE` and nowhere else.
9. Cancellation is reachable only from `CREATED`, `UPLOADING`, `VALIDATING`, `STAGED`,
   and `AWAITING_APPROVAL`; there is no cancellation transition from `APPLYING` onward.
10. A transition into a target-writing state requires that every snapshot it depends on
    is committed first. This is a guard, not an ordering convention.
11. The unique `(from, to, trigger)` triples have no duplicate.
12. `APPLYING` is entered only from `SNAPSHOTTING`, and `ROLLING_BACK` only from
    `FAILED`; the rollback path snapshots the failed on-disk state before restoring, so
    the snapshot-before-write rule has no exception.
13. The automatic rollback attempt counter is bounded by
    `deploy.rollback.max-attempts`; exceeding it enters `SAFE_STATE`.
14. An idempotency key maps to exactly one transaction; a repeat returns the existing one
    and never creates a second.
15. `FAILED` is terminal only when no target write occurred. When a target write
    occurred, its single allowed successor is `ROLLING_BACK`.

## Failure and rollback

`FAILED` records the failing step and the reason code. If no target file was modified
(failure during upload, validation, staging, or snapshotting), the transaction is over;
staging is cleaned up after the retention grace period and no rollback is attempted.

If a target file was modified, `ROLLING_BACK` is mandatory:

1. Snapshot the failed on-disk state so the rollback itself can be reversed.
2. Restore every target path to its recorded pre-deployment snapshot, including
   deletions and renames.
3. Restart when the original deployment restarted, using the same restart mode.
4. Verify the restored state with the same checks, under the same timeout.
5. Persist `ROLLED_BACK` only when the restored hash set matches.

If any step fails, persist `ROLLBACK_FAILED` and enter `SAFE_STATE`. The API stays
reachable and reports the exact files and hashes involved. A safe-state transaction never
resumes automatically.

## Startup recovery

Recovery runs after the data-directory lock is acquired, the snapshot store is open, and
the platform is available, but before the transport accepts new deployment work. For each
unfinished record:

- Terminal states are reported only. They are never replayed.
- `APPROVED` and `SNAPSHOTTING` re-verify snapshot completeness; incomplete snapshots
  fail the transaction without touching target files.
- `APPLYING` compares each operation's recorded hash with the observed file. It resumes
  forward only when every remaining operation is idempotent and the observed state
  matches; otherwise it fails and rolls back.
- `RESTART_PENDING` resumes the countdown when the original process is still alive. If
  the process is new, the restart already happened and the state maps to `VERIFYING`.
- `RESTARTING` maps to `VERIFYING` unconditionally. Persisting `RESTARTING` before
  requesting shutdown is what makes this mapping trustworthy.
- `VERIFYING` re-runs the checks with the remaining timeout.
- `ROLLING_BACK` resumes from the recorded progress; it does not start over.
- A corrupt or unreadable record enters `SAFE_STATE`. It is never repaired by guesswork.

If the process never returns after `RESTARTING`, no DeployMC code runs. The record and
the last state stay on disk for the operator. This is why `shutdown-only` requires an
external supervisor and why DeployMC never reports a restart it cannot observe.

## Conformance tests required before the engine is called implemented

- Model-based enumeration of the transition data, asserting every invariant above.
- A test that rejects an undefined transition with `deployments.invalid-state`.
- Fault injection at every state, including process death during `APPLYING`,
  `RESTARTING`, `VERIFYING`, and `ROLLING_BACK`.
- Recovery tests with a persisted record and a fake platform that simulates a new JVM.
- Idempotency, approval, cancellation, concurrency, and attempt-limit tests.
- Windows and Linux runs of replacement and rollback.
- A real-server run in the compatibility harness before any platform is called supported.

## Open questions

- The forward-resume rule for `APPLYING` needs the operation manifest and per-operation
  idempotency metadata defined with the deployment API.
- The approval policy shape (role list, expiry, two-person rule) is not final.
- The fallback when a data directory lacks atomic rename is not designed.
- `SAFE_STATE` operator actions need a documented API and UI.

## Related documents

- [Deployment state machine narrative](../../docs/architecture/deployment-state-machine.md)
- [ADR-0008: Deployment state machine and persistence](../../docs/adr/ADR-0008-deployment-state-machine.md)
- [Configuration schema](../config/config.schema.json) (`deploy.*` keys)
- [Error code catalog](../api/errors.md) (`deployments.*` codes)
- Master specification, Part 7 and section 24.5.
