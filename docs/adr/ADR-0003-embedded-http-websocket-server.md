# ADR-0003: Embedded HTTP/WebSocket server selection

- **Status:** Proposed
- **Date:** 2026-11-17
- **Deciders:** maintainers; owner for the final choice
- **Related:** [Threading model](../architecture/threading-model.md), [API layering](../architecture/api-layering.md), [ADR-0004](ADR-0004-shading-and-relocation.md)

## Context

DeployMC ships as a single plugin JAR that must run inside a Minecraft server on Java 8
and later. It needs an HTTP/1.1 server for the REST API and a WebSocket endpoint for
console and deployment events. The server binds to loopback by default; TLS is normally
terminated by Cloudflare Tunnel/Access, so the plugin does not need to be a public TLS
terminator in the default topology.

Constraints:

- Java 8 bytecode compatibility for the shared path.
- Shading and relocation of every bundled dependency.
- Small artifact size and a low chance of classloader conflicts with other plugins.
- Strict request limits, timeouts, backpressure, and predictable shutdown.
- No use of `com.sun.*` internals beyond what is explicitly supported, unless the choice
  is accepted with its risks.
- WebSocket support with origin checks, close codes, and bounded per-connection queues.
- Testability without a real server.

## Problem

Which HTTP and WebSocket implementation should `transport` use?

## Considered alternatives

### Option A — JDK `com.sun.net.httpserver` plus a WebSocket layer

- Use the JDK's built-in HTTP server and add a compact WebSocket implementation or
  library.
- Advantages: no HTTP dependency; small artifact; Java 8 available; simple programming
  model.
- Disadvantages: HTTP/1.1 only; limited production hardening; the JDK server's threading
  and connection behavior need measurement; a WebSocket implementation is still a
  dependency or a risky in-house component; `com.sun.net.httpserver` is a JDK-supported
  API but not a full application server.
- A home-grown WebSocket protocol parser is not acceptable unless audits and fuzzing
  justify it.

### Option B — Jetty 9.4.x

- Use the last Jetty line that supports Java 8, with its WebSocket module.
- Advantages: mature HTTP and WebSocket implementation; well-documented threading and
  lifecycle; broad deployment history.
- Disadvantages: the Java 8 line is older and receives limited fixes; several modules
  and a larger dependency graph; relocation and service-file merging need care; a future
  move to a supported Jetty line would require a Java 11+ baseline.

### Option C — Netty 4.1.x

- Use Netty for HTTP and WebSocket.
- Advantages: high performance; fine-grained control over event loops, buffers, and
  backpressure; widely used.
- Disadvantages: significant complexity; a large dependency surface; native transports
  are unnecessary and must be avoided; incorrect event-loop use is easy; relocation and
  debugging are harder.

### Option D — Undertow 2.x

- Use Undertow's HTTP and WebSocket support.
- Advantages: a smaller alternative to a full application server; blocking and
  non-blocking handlers.
- Disadvantages: dependency graph and maintenance status must be checked against the
  Java 8 line; less common in the Minecraft plugin ecosystem; relocation complexity.

### Option E — NanoHTTPD or another minimal server

- Use a very small HTTP server with a WebSocket add-on.
- Advantages: tiny footprint and simple code.
- Disadvantages: limited hardening, unclear maintenance, and WebSocket support that may
  not meet the protocol and backpressure requirements.

## Decision

**Proposed:** keep `transport` behind internal `HttpServer` and `WebSocketServer`
interfaces and run a WP-1.3 spike before choosing an implementation. The spike must
compare at least Option A (JDK HTTP server plus a WebSocket layer), Option B (Jetty
9.4.x), and Option C (Netty 4.1.x). Option D may be added if the first three fail a
criterion.

Evaluation criteria, in priority order:

1. Java 8 bytecode compatibility and a dependency set that can be relocated.
2. Security posture: maintained release line, CVE history, safe defaults, request
   smuggling and header-limit behavior.
3. Bounded resource use: connection limits, request size limits, timeouts, slow-client
   handling, and backpressure.
4. WebSocket correctness: upgrade, origin checks, fragmentation, close codes, heartbeat,
   and per-connection queue limits.
5. Threading: named/configurable threads, no hidden unbounded pools, clean shutdown.
6. Artifact size and classloader-conflict risk after shading.
7. Testability and operability: deterministic tests, metrics/log hooks, graceful drain.
8. License compatibility with Apache-2.0 and the Minecraft plugin ecosystem.

The spike must publish measured results and a recommendation. Until then, no HTTP server
dependency is added to the production build.

## Consequences

### Positive

- The transport contract is defined before a library choice locks in its API.
- A measured decision avoids choosing a server by familiarity alone.
- Tests can use a fake transport without starting a real server.

### Negative and costs

- Transport work is blocked behind the spike; only the interfaces and value objects can
  proceed.
- The spike itself costs time and must build small prototypes, including a WebSocket
  echo test and a slow-client test.
- Whichever option wins will have relocation and lifecycle work.

### Follow-up

- Define `HttpServer`/`WebSocketServer` interfaces and request/response abstractions.
- Build the spike under `tests/` or a throwaway branch, not in the production dependency
  graph.
- Record the winner in a new ADR that supersedes this one, or change this ADR's status to
  Accepted with the measured evidence.
- Re-check the chosen library's supported Java line before every major release.

## Evidence and validation

- **Not verified:** no spike has been run, no benchmark exists, and no HTTP/WebSocket
  library is in the production build. The current `transport` module contains only
  `HttpBindConfig`.
- This ADR is intentionally `Proposed`. It must not be cited as a decision that a
  particular server was selected or tested.

## References

- Master specification, section 4.1 and Part 32.2 (starting hypotheses).
- [Threading model](../architecture/threading-model.md) for the resource limits the
  implementation must honor.
- [ADR-0004](ADR-0004-shading-and-relocation.md) for relocation requirements.
