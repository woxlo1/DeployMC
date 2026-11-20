# Support Matrix

> **Status (2026-11-20): v1 smoke harness implemented; no support tier earned.** This document
> defines how support is claimed and verified. **The Tier 1/2/3 matrix is empty.** The v1 harness
> loads a pinned Paper build in CI with a conflicting-library fixture, checks an authenticated
> `/api/v1/server` response, compares thread dumps before and after an explicit plugin disable,
> and records real smoke evidence in a separate generated section. It does not run the full
> integration suite, so it does not claim support. Nothing in the "candidate" section is a support
> claim.

The normative definitions are in the master specification (Part 3). This document is the
working matrix and the rules for changing it.

## The problem this document solves

DeployMC wants to support as many Minecraft versions and server platforms as is realistic.
"Works on Paper, Spigot, Folia, and everything from 1.8 to 1.21" is not a testable claim.
Server software differs in plugin API, Java requirement, threading, log capture, restart
behavior, plugin descriptors, and file locking. An untested version may fail in ways that
are invisible until someone's production server breaks.

Support is therefore **evidence**, not intent. A version is listed only after a test ran,
and the tier tells the reader what kind of evidence exists.

## Tiers

| Tier | Requirement | What the reader may assume |
|---|---|---|
| **Tier 1** | A real server of that platform/version starts in CI on every pull request or nightly. DeployMC loads, and an automated integration test exercises the public API: file read/write, snapshot, deploy, console stream, and shutdown. | The listed version passed the current integration suite on the recorded operating systems. It may still fail in an untested hosting setup. |
| **Tier 2** | The same kind of test runs on a schedule (nightly/weekly) or on release candidates, not every pull request. | The listed version passed recently, not necessarily on the exact commit a user is running. |
| **Tier 3** | Best effort or community-reported. Not tested automatically. Known limitations documented. | No automated evidence. It may work; it may not. Limitations are listed. |
| **Unlisted** | No evidence. | **No claim.** Do not assume it works. |

Tier 1 and Tier 2 are earned only by a green harness run. A single manual success is not
enough for Tier 1 or Tier 2; it may become a Tier 3 entry with a note.

## Hard rules

1. Never put a version in Tier 1 or Tier 2 unless the matrix job actually runs and passes.
2. Never claim support for a version that has not been run at least once.
3. A failing run demotes or removes the entry. It is not hidden or explained away.
4. Every entry records the evidence fields below, including a link to the run and its logs.
5. The matrix is generated from results where possible. A hand-edited claim that contradicts
   the harness is a bug.
6. Capabilities are recorded per entry. A missing capability is documented with the feature
   it disables; it does not silently count as a pass for a safety-critical claim.
7. A "latest" label is not a version. Entries name exact versions.
8. An entry describes one platform + server version + Minecraft version + Java combination.
   Mixing them in one row is not allowed.

## Current matrix

**Empty.** The v1 harness has loaded DeployMC on Paper, but the full integration suite has not
run, so no Tier 1/2/3 row is justified.

| Tier | Platform | Server version | Minecraft | Java | OS | DeployMC commit | Run | Result | Date |
|---|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | — | — |

Do not add a row here by hand to advertise intent. Rows come from harness results or from a
reviewed Tier 3 report.

<!-- BEGIN GENERATED SMOKE MATRIX -->
### Generated compatibility smoke results (not a support tier)

These rows record only the v1 smoke suite: concurrent load with an older Netty fixture, authenticated `/api/v1/server`, before/after-disable thread dumps, logs, and clean shutdown. They do not earn Tier 1 or Tier 2.

| Suite | Entry | Minecraft | Build | OS | DeployMC commit | Result | Run | Date |
|---|---|---|---|---|---|---|---|---|
| compat-v1 | paper-26.3 | 26.3 | 256 | Linux-7.0.0-1019-azure-x86_64-with-glibc2.43 | 839b789ae8483f83dcb4b430641b7f0eafccb303 | pass | [run](https://github.com/woxlo1/DeployMC/actions/runs/44704690836) | 2026-11-20T08:43:32Z |
| compat-v1 | paper-26.3 | 26.3 | 256 | Windows-2025Server-10.0.26100-SP0 | 839b789ae8483f83dcb4b430641b7f0eafccb303 | pass | [run](https://github.com/woxlo1/DeployMC/actions/runs/44704690836) | 2026-11-20T08:44:23Z |
<!-- END GENERATED SMOKE MATRIX -->

## Evidence fields

Every entry, generated or reported, includes:

| Field | Example | Notes |
|---|---|---|
| Platform and exact server version | Paper 1.21.4 build 232 | Not "Paper latest" |
| Minecraft version | 1.21.4 | Exact |
| Java vendor and version | Eclipse Temurin 21.0.5 | Runtime actually used |
| Operating system | Ubuntu 24.04, Windows Server 2022 | One per run |
| DeployMC commit | `f8d77ac` | The tested code |
| Integration suite version | `compat-v1` | The test definition |
| Capability results | `log.capture=AVAILABLE`, `update-folder=UNAVAILABLE`, ... | Per capability key |
| Checks passed/failed | file read/write, snapshot, deploy, console, shutdown, restart mode | Explicit list |
| Result | pass / fail / flaky | Flaky is a failure until explained |
| Date | 2026-11-17 | UTC |
| Log artifact | link to CI artifact | Required for failures and Tier 1/2 |

## Capability expectations per platform class

This table is a hypothesis about what each platform *may* expose. It is not verified.
Capabilities are reported by the adapter at runtime and recorded per entry.

| Capability | Bukkit family | Folia | Velocity | BungeeCord/Waterfall | Standalone |
|---|---|---|---|---|---|
| `log.capture` | Expected | Expected | Expected | Expected | Expected (own process) |
| `log.capture.structured` | Likely (Log4j on modern Paper) | Likely | Uncertain | Uncertain | Available (own logging) |
| `command.dispatch` | Yes | Yes (global/region rules) | Yes (proxy command) | Yes | Not a server command |
| `server.save` | Yes | Yes | Not applicable | Not applicable | Not applicable |
| `server.shutdown` | Yes | Yes | Yes | Yes | Yes (own process) |
| `server.restart.script` | Depends on hosting | Depends on hosting | Depends on hosting | Depends on hosting | Depends on supervisor |
| `plugins.enumerate` | Usually | Usually | Usually | Usually | Yes (directory scan) |
| `plugins.enable-state` | Usually | Usually | Usually | Usually | Not applicable |
| `plugins.load-errors` | Partial (log-based) | Partial | Partial | Partial | Yes (self-managed) |
| `update-folder` | Version-dependent | Version-dependent | No | No | Yes (restart-time swap) |
| `scheduler.region` | No | Yes | No | No | No |
| `players.locale` | Usually | Usually | Usually | Usually | No |
| `players.broadcast` | Yes | Yes | Yes | Yes | No |

"Expected" and "Likely" are not evidence. Nothing in this table may be copied into a support
tier.

## Candidate targets and priority

These are hypotheses from the master specification (section 3.2), in intended order. None
are supported yet.

| Priority | Target | Why | First blocker |
|---|---|---|---|
| 1 | Paper 1.21.x on Java 21 | Primary target; best-documented plugin API | No adapter implementation, no harness |
| 1 | Paper forks (Purpur, Pufferfish, Leaf) | Usually compatible through the Paper/Bukkit adapter | Must be verified per fork, not assumed |
| 1 | Folia | Requires region-aware scheduling | `PlatformScheduler` has no region context |
| 2 | Spigot / CraftBukkit | Bukkit API subset | Adapter and capability documentation |
| 2 | Older Paper/Spigot down to 1.8.8 | Java 8 baseline exists for this | Which versions actually load the JAR is unmeasured |
| 2 | Velocity | Proxy plugin directory | Proxy adapter, command model |
| 2 | BungeeCord / Waterfall | Proxy plugin directory | Proxy adapter |
| 3 | Standalone mode | Works without a Minecraft platform | Process/supervisor model, config |
| 3 | Sponge, Fabric, Forge, NeoForge | Different mod-loading safety properties | Research ADR required |

The lowest supported Minecraft version will be whatever the evidence shows, not necessarily
1.8.8. Java 8 bytecode is necessary but not sufficient; the server API, descriptor, and
behavior must also be compatible.

## Harness design

The v1 harness lives in `tests/compat-matrix/`. It is implemented with the Python standard
library and runs on Ubuntu and Windows. Entry definitions are pinned in `entries.json`; the
current entry is Paper 26.3 build 256, selected through PaperMC's official Fill API and verified
by the SHA-256 digest that the API supplies. The Gradle build uses JDK 21 while the server process
uses Java 25, which Paper 26.3 requires. Evidence and API research are recorded in
[Paper download API](../research/paper-download-api.md).

### v1 scope

The v1 smoke entry does the following:

1. Resolve the pinned build through the official API and verify its SHA-256 digest.
2. Cache the download by digest; a cached file is re-hashed before use.
3. Create an isolated sandbox, accept the EULA only there, and write a minimal flat world config.
4. Provision a one-run token record directly in `plugins/DeployMC/tokens.d/`.
5. Install a fixture plugin that bundles unrelocated Netty 4.1.100.Final and compare its shaded
   classes with DeployMC's relocated Netty 4.1.141.Final namespace before starting the server.
6. Start the server headless with distinct loopback ports for Minecraft and the API, wait for a
   real `Done (...)` log signal, then poll `/healthz`. It never sleeps a fixed time to guess
   readiness.
7. Call `/api/v1/server` twice: without credentials (expect `401`) and with the provisioned token
   (expect documented `ServerInfo` plus `ETag` and `X-Request-Id`).
8. Capture a thread dump, disable DeployMC through the fixture's Bukkit-API command, verify the
   adapter release log, and capture a second thread dump to confirm `DeployMC-netty-*` threads are
   gone.
9. Record the server log, both dumps, and a JSON result, then send `stop` and force-kill only after
   a timeout. Upload logs and results as CI artifacts and render a generated smoke-result section.

The harness never parses a token from console output and never writes the secret to a result or
log. The token is host-provisioned through the same WP-1.9 mechanism used by administrators.

### Deferred verification

The conflicting-library and before/after-disable checks from issue #48 comments are now part of
v1. The following remain explicitly **not** claimed:

- The full shared integration suite (file read/write, snapshot, deploy, console stream, and
  restart semantics) required for a Tier 1 or Tier 2 row.
- Support claims for any platform or version. The generated smoke table is evidence only.
- Other Bukkit-family forks, older versions, proxies, and standalone mode; each needs its own
  entry and evidence.

### Flow per entry

1. Resolve the exact server artifact from an official, verifiable download source (for
   example, Paper's download API). Verify a checksum where the provider supplies one.
2. Create an isolated directory per entry with a minimal server configuration and a minimal
   world. Accept the EULA **only inside the test sandbox**; this is documented in the harness
   README so it is never mistaken for a production recommendation.
3. Start the server headless with the exact Java runtime for that entry.
4. Install the freshly built DeployMC JAR into `plugins/`.
5. Wait for readiness by polling the health endpoint, not by sleeping a fixed time.
6. Run the shared integration suite through the public API:
   - read a file, write it, read it back, verify the revision;
   - snapshot, modify, restore, verify content;
   - deploy a small valid test JAR, observe the state machine to a terminal state;
   - open a console stream, receive at least one line;
   - request shutdown and verify the process exits (or records the restart mode it used).
7. Collect the server log, DeployMC log, capability report, and state history as artifacts.
8. Tear down: stop the process with a timeout, then force-kill and mark the run failed if it
   does not exit.
9. Emit a machine-readable result record and regenerate the matrix section.

### Isolation and safety

- Every entry runs in its own directory and port range. No shared server.
- Network is outbound-only where possible; webhook and update checks are disabled.
- No production credentials. Tokens are generated per run.
- Downloads are cached by URL and checksum; a checksum mismatch fails the run.
- Test JARs are built in-repo from fixed sources; borrowed third-party JARs are not used.

### Flakiness policy

- A failed run is a failure. One automatic retry is allowed only for infrastructure errors
  (download failure, port collision), and the retry is recorded.
- A test that passes only after retry is marked `flaky`, not `pass`.
- A flaky entry is demoted from Tier 1 until it has a stable history.

### Generated output

The harness writes a JSON result set and regenerates the "Current matrix" section. A CI job
fails if the committed matrix differs from the generated one, so documentation cannot drift
from results. The same JSON feeds a status badge and a README table.

## Promotion and demotion

| Change | Requirement |
|---|---|
| Unlisted -> Tier 3 | A reviewed report with the evidence fields, or a documented manual test by a maintainer |
| Tier 3 -> Tier 2 | The harness runs the entry on schedule and passes consistently |
| Tier 2 -> Tier 1 | The entry runs on pull requests and nightly and passes |
| Any -> lower tier | A failing run, a capability regression, or a harness change that invalidates prior evidence |
| Any -> removed | The server version is no longer reasonably obtainable or the platform is dropped by an ADR |

Demotion is recorded in the matrix history, not just the current table.

## Reporting a result

Until the full integration suite exists, compatibility reports can be accepted through a
compatibility issue form (planned) and are labeled unverified. A report must include the
evidence fields, the server log, and the exact DeployMC commit. A maintainer reproduces it
before it affects the matrix. A community report alone is Tier 3 at most.

## What is not a support claim

- Compiling against a server API.
- Loading the plugin once without an error in a single manual test.
- A "works for me" comment.
- Inheriting Paper's compatibility for a fork without running it.
- Passing Java bytecode verification.

None of these create a Tier 1 or Tier 2 entry.

## Related documents

- [Platform SPI](../architecture/platform-spi.md)
- [ADR-0014: Platform support tiers and compatibility harness](../adr/ADR-0014-platform-support-tiers.md)
- [ADR-0002: Java 8 bytecode baseline](../adr/ADR-0002-java-8-bytecode-baseline.md)
- Master specification, Part 3.
