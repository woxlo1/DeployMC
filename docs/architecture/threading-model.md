# Threading Model

> **Status: design baseline (2026-11-17).** No production executor, HTTP server, or
> deployment engine exists yet. This document defines the rules that implementation
> must follow; it is not evidence that the rules are enforced.

## Goals

Minecraft servers have strict threading rules. On Bukkit-family servers, world and API
work generally belongs on the main thread. On Folia, work belongs to the owning region
thread. Blocking either kind of thread with file I/O, network I/O, hashing, diffing, or
waiting on a lock causes visible lag, watchdog kills, or region stalls.

The threading model therefore has four rules:

1. Never perform blocking I/O, hashing, diffing, or network calls on a server thread.
2. Use dedicated, bounded, named executors for background work.
3. Return to the platform thread only when an API requires it, through the SPI scheduler.
4. Every thread and task is owned by a lifecycle object and terminated on disable.

## Execution domains

| Domain | Work | Thread ownership | Bounded? |
|---|---|---|---|
| Platform thread(s) | Server API calls, command dispatch, player messages, save, shutdown | Server software / adapter | N/A |
| HTTP acceptor/worker pool | Request parsing, authentication, response writing | Transport implementation | Yes: max connections, body size, timeouts |
| WebSocket I/O | Frame handling, heartbeats, per-connection queues | Transport implementation | Yes: per-connection and global limits |
| I/O executor | File reads/writes, snapshot blobs, audit append, config persistence | DeployMC | Yes: fixed workers, bounded queue |
| CPU executor | Hashing, diff, validation, compression | DeployMC | Yes: fixed workers, bounded queue |
| Deployment coordinator | State-machine progression, persistence ordering, restart sequencing | DeployMC | One active transaction per data directory |
| Scheduler | Delayed tasks, countdowns, timeouts | Platform scheduler or a tracked DeployMC executor | Yes: cancellation and shutdown |
| Event/webhook dispatcher | Fan-out to WebSocket clients and HTTP webhooks | DeployMC | Yes: per-target queues and drop policy |

The chosen HTTP/WebSocket library adds its own threads. [ADR-0003](../adr/ADR-0003-embedded-http-websocket-server.md)
selects Netty 4.1; the transport implementation configures named event-loop threads and joins
them during shutdown.

## Naming and tracking

Every DeployMC-created thread has a prefix and a purpose, for example:

```
DeployMC-io-1
DeployMC-cpu-1
DeployMC-deploy-1
DeployMC-ws-<id>
DeployMC-webhook-<id>
```

Threads created inside a third-party library are configured with a recognizable name
where the library allows it. A lifecycle registry keeps the executors, schedulers, open
sockets, subscriptions, and file watchers created by one plugin instance. Disable closes
them in reverse order.

No `static` mutable executor or singleton service is allowed. Tests must be able to
create and destroy an instance without leaving threads behind.

## Work placement

### HTTP requests

- Parsing, authentication, authorization, and small reads may run on transport worker
  threads.
- File I/O, snapshot work, hashing, diff, and deployment progression are submitted to the
  appropriate executor. The request thread waits with a timeout or completes
  asynchronously; it never runs the work itself.
- A request that exceeds its deadline returns a stable timeout error. It does not hold a
  platform thread or an unbounded queue slot.
- Request bodies are streamed with hard limits. Uploads do not buffer the whole artifact
  in memory.

### File operations

- All path resolution and file access run off the platform thread.
- Atomic writes use a temporary file in the same directory, fsync, and rename. Directory
  fsync is attempted where supported.
- Per-path locks serialize conflicting writes inside one DeployMC instance. The lock is
  held only for the file operation, never across a network call or a user think-time.
- ETag checks and snapshot creation happen under the same logical operation so a stale
  write cannot silently overwrite a newer revision.
- Windows transient locks from antivirus or indexing are retried with a bounded backoff.

### Hashing, diff, and validation

- SHA-256 hashing and text diff run on the CPU executor, with size limits (initial:
  2 MB per side for line diff; larger returns `too-large`).
- YAML/JSON/TOML validation runs on the CPU executor with parser limits (alias
  expansion, nesting depth, input size). A malicious document must not be able to consume
  unbounded CPU or memory.
- Streams are closed with try-with-resources, including on cancellation.

### Deployment

- One deployment coordinator advances a transaction. It persists each state before
  asking the SPI to perform a side effect.
- Countdown messages, save-all, and shutdown are scheduled through the platform
  scheduler. The coordinator does not sleep on a platform thread.
- Verification uses a deadline and a cancellation handle. A timeout transitions the
  transaction to failure; it does not leave a daemon thread polling forever.
- After a restart, a new coordinator instance performs startup recovery before accepting
  new deployment requests.

### Console and logs

- A log appender/handler enqueues into a bounded ring buffer and returns immediately.
  Logging must never block on a WebSocket client.
- The initial defaults are 5,000 lines and a byte cap (for example 8 MiB), whichever is
  reached first.
- Slow WebSocket clients get a per-connection bounded queue (initial: 1,000 messages or
  2 MiB). When it overflows, messages are coalesced or dropped with an explicit
  `console.gap` notice. The server is never slowed to preserve a client's stream.
- Command execution is dispatched to the correct platform thread. Output correlation, if
  implemented, must not rely on a shared mutable buffer without a correlation id.

### Webhooks

- Each delivery runs on a bounded dispatcher with a 10-second timeout, five attempts, and
  exponential backoff up to one hour.
- Target URLs are validated against SSRF rules before every attempt, including redirects.
- A failing target cannot exhaust the dispatcher or block other events.

## Returning to the platform

Core code never calls a server API directly. When work must run on the platform thread,
it goes through `PlatformScheduler.runGlobal` or the relevant SPI method. The adapter is
responsible for marshalling and for reporting failure if the platform is shutting down.

A future region-aware extension must accept an explicit region/owner context. It must
not silently fall back to the global scheduler for Folia work that requires a region,
because that can violate Folia's threading model.

## Backpressure and rejection

Every queue has a documented policy:

| Queue | Full behavior |
|---|---|
| HTTP worker pool | Reject with 503 and `server.busy`; do not queue without bound |
| Upload queue | Reject with 429/503 before reading the body; enforce per-identity limits |
| I/O executor | Reject with a retryable error; never run inline on the caller |
| CPU executor | Reject with `server.busy`; never block the transport thread |
| Console ring buffer | Drop oldest with a gap marker; never block logging |
| WebSocket outbound | Coalesce/drop with `console.gap` or a channel-specific notice |
| Webhook dispatcher | Retry with backoff; drop after the attempt limit and record the delivery |

Rate limits are per identity, with stricter limits for authentication failures per source
IP. The numeric defaults are in the master specification and will be documented in the
configuration reference.

## Shutdown

Disable is not a best-effort suggestion. The sequence is:

1. Mark the instance as stopping and reject new requests and deployments.
2. Stop the transport acceptor; close idle connections.
3. Notify WebSocket clients with a documented close code and stop accepting new frames.
4. Drain or cancel bounded work with a timeout.
5. Flush the audit log and persist in-flight transaction state.
6. Detach console/log handlers and platform listeners.
7. Shut down owned executors and join their threads with a deadline.
8. Release the data-directory lock.

If a thread does not stop within the deadline, the failure is logged with its name and
stack trace. The plugin must not leave a thread that keeps a classloader, socket, or file
handle alive after disable.

## Testing

- A fake scheduler makes delayed work deterministic; tests do not call `Thread.sleep` to
  wait for a state transition.
- A thread-leak test creates, starts, and disables an instance, then asserts that
  DeployMC threads are gone.
- Concurrency tests cover concurrent saves to one path, snapshot versus delete, deployment
  versus file edit, and transport shutdown during a request.
- A slow-client test proves console and deployment events do not slow the producer.
- Windows and Linux both run file-locking and atomic-write tests.

## Open questions

- ADR-0003 selected Netty 4.1. Its exact production thread counts and shutdown timing are
  verified by the WP-1.3 lifecycle tests, not assumed from the spike.
- Whether deployment coordination uses one thread or a small pool per data directory is
  an implementation detail, but the one-active-transaction invariant is fixed.
- Virtual threads on Java 21 are not used on the Java 8 shared path. Any future use must
  be optional and must not change the threading contract for legacy servers.
