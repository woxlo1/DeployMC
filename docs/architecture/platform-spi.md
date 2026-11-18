# Platform SPI

> **Status: implemented contract with no real adapter (2026-11-18).** The interfaces below
> exist in `core/src/main/java/io/github/woxlo1/deploymc/core/spi/`. The capability keys have an
> executable catalog in `core`, and an in-memory fake adapter exercises the SPI in core tests.
> No real platform adapter implements them yet. A Bukkit-family adapter is the first target; its
> behavior is not tested against a running server.

## Why a SPI exists

DeployMC must load on several server platforms and Minecraft versions without importing
server internals. The SPI is the only place where platform-specific behavior enters the
JVM:

- `core` depends on the SPI and never on Bukkit, Paper, Folia, Velocity, Bungee, or
  CraftBukkit classes.
- Each adapter translates platform APIs into the SPI and reports what the current
  runtime can actually do.
- Missing capabilities are explicit. Core and clients degrade; they do not guess.

The SPI is an internal contract. It is not the public Java API in `api/`, and it is not
a stable plugin-extension surface.

## Current interfaces

The current SPI is intentionally small. It covers information, scheduling, console,
server lifecycle, plugin inspection, and messages. File-system access is not part of the
SPI: the sandbox owns it and receives the root from the adapter at startup.

```java
public interface PlatformAdapter {
  PlatformInfo info();
  PlatformCapabilities capabilities();
  Path pluginsDirectory();
  Path dataDirectory();
  PlatformScheduler scheduler();
  ConsoleBridge console();
  ServerController server();
  PluginInspector plugins();
  MessageSink messages();
  Logger logger();
}
```

| Interface | Purpose | Notes |
|---|---|---|
| `PlatformInfo` (`api/`) | Software name/version, Minecraft version, Java version, OS | Immutable value object; shown by `/server` and doctor |
| `PlatformCapabilities` (`core/`) | Immutable map of capability key to `CapabilityState` | Missing key means `UNKNOWN`, never available |
| `PlatformScheduler` | Run on the platform/global thread, run async, run later, shut down | Region-aware scheduling is a capability, not an assumption |
| `ConsoleBridge` | Subscribe to console lines, dispatch a command | Must attach/detach without breaking server logging |
| `ServerController` | Save all, request shutdown, report whether restart is configured | Restart is a supervisor concern, not a JVM trick |
| `PluginInspector` | List loaded plugins with name, version, main class, enabled state | Platforms without enumeration report the capability missing |
| `MessageSink` | Send a localized message by key with placeholders | Never pass pre-formatted sentences |
| `ScheduledTask` | Cancel handle for delayed work | Cancellation is best effort before the task starts |

`ConsoleLine`, `LoadedPlugin`, and `ScheduledTask` are immutable or handle-only value
types. They do not expose platform objects.

## Capability keys

Capability keys are stable strings in `CapabilityKeys`. Each capability must define a
fallback behavior; a no-op that looks like success is not acceptable.

| Key | Meaning | Required fallback when unavailable |
|---|---|---|
| `log.capture` | Attach to the server log stream | Console endpoints report `CAPABILITY_UNAVAILABLE`; the UI hides live console |
| `log.capture.structured` | Provide level, logger, thread, and timestamp fields | Parse what is safe; leave unknown fields unknown, never fabricate them |
| `command.dispatch` | Execute a console command on the correct thread | Reject command requests; never spawn a local process |
| `command.output` | Capture the output of a dispatched command | Report only that the command was accepted, not its output |
| `server.save` | Trigger a world save | Deployment reports the missing step; it does not claim a safe restart |
| `server.shutdown` | Request a server shutdown | Restart mode `none`; instruct the operator to restart manually |
| `server.restart.script` | An external restart mechanism is configured | Treat restart as unsupported; never pretend a restart will happen |
| `plugins.enumerate` | List loaded plugins | Plugin list is unavailable; static JAR inspection still works |
| `plugins.enable-state` | Report enabled/disabled state | Report unknown; do not infer success from absence of errors |
| `plugins.load-errors` | Detect plugin load failures | Verification is limited and reports which check could not run |
| `update-folder` | The platform has a safe update-folder mechanism | Use a restart-time swap; never overwrite a loaded JAR on Windows |
| `scheduler.region` | Folia-style region scheduling is available | Use the global scheduler; do not claim Folia support |
| `players.locale` | Per-player locale is available | Use the server default locale |
| `players.broadcast` | Broadcast to players | Send to console only and report the limitation |

`CapabilityState` has three values: `AVAILABLE`, `UNAVAILABLE`, and `UNKNOWN`. An adapter
must not report `AVAILABLE` unless it has actually exercised the capability or can prove
it from a stable API. Clients must treat `UNKNOWN` as unavailable for safety-sensitive
operations.

### Executable capability catalog

`CapabilityCatalog` in `core` holds one `CapabilityDefinition` for each key above. A definition
has a key, description, and mandatory fallback, so a capability cannot be added without saying
what clients do when it is missing. `CapabilityKeys` holds the stable string constants; tests
assert that the catalog contains exactly those keys and that the constants still match their wire
values. The catalog is implemented in production code; clients do **not** need to read this
document to discover a key.

Tier evidence is deliberately not a field in the catalog. The empty
[support matrix](../compatibility/support-matrix.md) is the only place where a capability may be
recorded as present or absent on a named platform and version. No capability has tier evidence
today, so no adapter may claim one.

## Threading contract

The adapter is responsible for honoring the platform's threading rules. Core code calls
the SPI from its own executors and expects the SPI to marshal when required.

| SPI call | Thread expectation | Adapter responsibility |
|---|---|---|
| `scheduler.runGlobal` | Called from any thread | Marshal to the main/global thread |
| `scheduler.runAsync` | Called from any thread | Use a background executor that is never a server thread |
| `scheduler.runLater` | Called from any thread | Preserve delay semantics on the platform scheduler or a tracked executor |
| `console.dispatchCommand` | Called from any thread | Dispatch on the command thread |
| `server.saveAll` | Called from any thread | Marshal to the main/global thread |
| `server.shutdown` | Called from any thread | Request shutdown; return once accepted, not after process exit |
| `console.subscribe` | Called during startup | Attach without replacing or breaking existing log handlers |
| `plugins.loadedPlugins` | Prefer platform thread | Return an immutable snapshot; do not block on plugin internals |
| `messages.send` | Any thread | Marshal to the platform thread if the platform requires it |

The full executor and lifecycle model is in [threading-model.md](threading-model.md).

## Lifecycle and failure rules

An adapter is created by `plugin` after the server platform is available. The intended
contract is:

1. Construct the adapter with the platform plugin/logger references.
2. Report `PlatformInfo` and `capabilities()` truthfully for this exact runtime.
3. Provide stable `pluginsDirectory()` and `dataDirectory()` paths. The data directory
   must not be inside the editable sandbox root by default.
4. Attach console and lifecycle listeners only when core asks.
5. On disable, detach every listener and stop every task the adapter created.

Adapter failures must be reported, not swallowed:

- A capability that throws while being probed becomes `UNKNOWN`, with the failure
  logged once without secrets.
- An operation that cannot run returns a typed failure to core. Core maps it to a stable
  error code; the API never returns a silent success.
- Adapter code does not write audit records. Core audits the user action and the result.

## Known gaps and open questions

The following are not decided by the current interfaces and need implementation work or a
follow-up ADR:

- **Restart abstraction.** `ServerController` exposes `shutdown` and
  `restartSupported`, not a `restart` method. Restart modes (`shutdown-only`,
  `spigot-restart-script`, `none`) need a precise contract before Phase 1. See
  [ADR-0008](../adr/ADR-0008-deployment-state-machine.md).
- **Structured log capture.** The current `ConsoleLine` carries level, logger, thread,
  and message, but there is no contract for attaching a Log4j appender versus a
  `java.util.logging` handler. This is platform-specific and must be tested per adapter.
- **Command output correlation.** `dispatchCommand` returns `void`. Capturing output for
  a specific command requires a correlation mechanism that does not exist yet.
- **Region scheduling.** `PlatformScheduler` has no region parameter. Folia support
  requires an additive interface or a capability-specific path, not a silent global
  fallback.
- **Plugin load-error attribution.** `LoadedPlugin` has no error field. Verification
  needs a separate result type before it can claim that a deployed plugin loaded
  cleanly.

These gaps are deliberate: the SPI should grow only when a real adapter and a real test
require it.

## Adapter status

| Adapter | Target | Status |
|---|---|---|
| Bukkit family | Spigot, CraftBukkit, Paper, Paper forks | Not implemented; version detection value object only |
| Folia | Regionized Paper fork | Not started; requires region-aware scheduling |
| Velocity | Proxy | Not started |
| BungeeCord/Waterfall | Proxy | Not started |
| Standalone | No Minecraft platform | Not started; configuration value object only |

No adapter is listed in a support tier until the compatibility harness runs it. See
[the support matrix design](../compatibility/support-matrix.md).

## Testing contract

Every adapter needs the same three layers of tests:

1. **Unit tests with a fake platform.** `FakePlatformAdapter` in `core/src/test/java` verifies
   capability reporting, subscription cleanup, command recording, deterministic scheduling,
   cancellation, and plugin snapshots without a server. It is a test double, not an adapter;
   it does not prove that a platform honors thread expectations.
2. **Contract tests against the SPI.** The same test suite runs against every adapter's
   fake to ensure consistent semantics.
3. **Real-server tests in the compatibility harness.** Start the actual server, load the
   built JAR, and exercise file read/write, snapshot, deploy, console, and shutdown.

An adapter that cannot pass the real-server layer is reported as unsupported, not as
"probably works".

## Related decisions

- [ADR-0003: Embedded HTTP/WebSocket server selection](../adr/ADR-0003-embedded-http-websocket-server.md)
- [ADR-0006: Sandbox design and protected paths](../adr/ADR-0006-sandbox-and-protected-paths.md)
- [ADR-0008: Deployment state machine and rollback semantics](../adr/ADR-0008-deployment-state-machine.md)
- [ADR-0014: Platform support tiers and compatibility harness](../adr/ADR-0014-platform-support-tiers.md)
