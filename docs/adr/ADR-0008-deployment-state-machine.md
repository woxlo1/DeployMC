# ADR-0008: Deployment state machine and rollback semantics

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** maintainers
- **Related:** [Deployment state machine](../architecture/deployment-state-machine.md), [ADR-0007](ADR-0007-snapshot-storage-format.md), [Platform SPI](../architecture/platform-spi.md)

## Context

A deployment replaces plugin files and may restart the server. Failures can occur before
any file changes, during application, during restart, or during post-restart
verification. A crash can happen at any point. The server may be unable to roll back
automatically if the new state prevents DeployMC from starting or if the platform cannot
restart itself.

The product promise is not "deployments never fail". It is "a failure is detected,
reported, and either rolled back or leaves the system in a reachable safe state".

## Problem

How should a deployment be represented and recovered so that partial changes are never
silently accepted and rollback is possible across a JVM restart?

## Considered alternatives

### Option A — Best-effort copy and restart

- Copy files, request restart, and report success if the copy succeeded.
- Advantages: simple.
- Disadvantages: no verification, no recovery, no rollback; a half-applied set or a
  plugin that fails to enable is left in place. Rejected.

### Option B — External orchestrator owns the transaction

- A separate daemon or supervisor performs file swaps and restarts.
- Advantages: can act while the server is down; strong process control.
- Disadvantages: not available in the plugin-only deployment model; adds an installation
  and security surface; conflicts with panels that already supervise the process. Could
  be an optional integration later, not the core design.

### Option C — Database transaction spanning the restart

- Use a transactional store for the whole operation.
- Advantages: familiar semantics.
- Disadvantages: a JVM restart and file-system changes cannot be one database
  transaction; a database does not make external side effects atomic.

### Option D — Persisted state machine with snapshots and startup recovery

- Persist each state before its side effect, snapshot targets, verify after restart, and
  roll back from snapshots when verification fails.
- Advantages: survives crashes; explicit transitions can be tested; recovery is
  deterministic; rollback is reversible.
- Disadvantages: more states and tests; operator action may still be required in
  `SAFE_STATE`; one active deployment per data directory.

## Decision

Adopt the persisted state machine and rollback semantics described in
[deployment-state-machine.md](../architecture/deployment-state-machine.md):

- States: `CREATED`, `UPLOADING`, `VALIDATING`, `REJECTED`, `STAGED`,
  `AWAITING_APPROVAL`, `APPROVED`, `SNAPSHOTTING`, `APPLYING`, `RESTART_PENDING`,
  `RESTARTING`, `VERIFYING`, `SUCCEEDED`, `FAILED`, `ROLLING_BACK`, `ROLLED_BACK`,
  `ROLLBACK_FAILED`, `CANCELLED`, `SAFE_STATE`.
- Persist every transition, with timestamp, actor, and reason, before the next side
  effect.
- Snapshot every target path, including "did not exist" markers, before applying.
- Map a persisted `RESTARTING` to `VERIFYING` on startup.
- Verify health with bounded checks and a timeout after restart.
- Roll back automatically on verification failure, using snapshots; snapshot the failed
  state before rollback so the rollback is itself reversible.
- Bound automatic rollback attempts (initial default: 2). Exhaustion or an impossible
  rollback enters `SAFE_STATE`, keeping the UI reachable.
- Allow cancellation only before `APPLYING`.
- Allow one active deployment per data directory.
- Require an approver other than the requester when approval is enabled.
- Use restart modes (`shutdown-only`, `spigot-restart-script`, `none`) instead of
  claiming a restart the platform cannot perform.

The formal transition table will live in `specification/deployment/state-machine.md`.
This ADR accepts the design; it does not claim the engine is implemented.

## Consequences

### Positive

- Failures are explicit and recoverable.
- A restart halfway through does not lose the transaction.
- Rollback has a documented, testable definition.
- `SAFE_STATE` gives the operator a reachable system instead of a silent broken server.

### Negative and costs

- Significant implementation and test complexity.
- Verification depends on platform capabilities; missing capabilities must be reported
  honestly, not treated as success.
- `SAFE_STATE` requires a documented operator procedure and UI.
- One active deployment per data directory limits concurrency, which is acceptable
  initially.

### Follow-up

- Write the formal transition table and model-based tests.
- Define the operation manifest and forward-resume rules for `APPLYING`.
- Implement fault injection at every state, including process death.
- Define the approval policy shape and expiry.
- Document `SAFE_STATE` resolution.
- Test on a real Paper server on Linux and Windows.

## Evidence and validation

- The state set and rules follow master specification sections 7.2, 24.5, and 33.5.
- **Not verified:** no deployment engine, state store, verification check, or rollback
  test exists. No real server has run a deployment. The ADR records a design decision.
- The current `ServerController` SPI can request shutdown and report whether restart is
  supported, but it has no restart method; the contract needs to be completed before
  implementation.

## Spike evidence (2026-11-23)

[Windows JAR replacement spike 2](../research/spikes/02-windows-jar-replacement.md) confirms that
in-place replacement is share-mode dependent, and [restart behavior spike 7](../research/spikes/07-restart-behavior.md)
records systemd, Docker, loop-script, and Pterodactyl evidence requirements without claiming a run.
D-001 remains open, so the restart-mode contract is unchanged: shutdown-only is the default until a
real supervisor run justifies another mode.

## References

- Master specification, sections 7.2, 24.5, and 33.5.
- [Deployment state machine](../architecture/deployment-state-machine.md).
- [Platform SPI](../architecture/platform-spi.md) known gaps.
