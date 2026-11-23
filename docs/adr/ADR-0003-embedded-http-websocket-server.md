# ADR-0003: Embedded HTTP/WebSocket server selection

- **Status:** Accepted
- **Date:** 2026-11-17
- **Accepted:** 2026-11-18
- **Deciders:** maintainers; owner approved the final choice on 2026-11-18
- **Related:** [Threading model](../architecture/threading-model.md), [API layering](../architecture/api-layering.md), [ADR-0004](ADR-0004-shading-and-relocation.md), [transport spike](../research/transport-spike.md)

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

Use **Netty 4.1.141.Final** for HTTP and WebSocket, behind internal transport interfaces.
The owner approved this choice on 2026-11-18 after the WP-1.3 spike recorded in
[transport spike research](../research/transport-spike.md).

The spike compared Option A (JDK HTTP server plus a WebSocket layer), Option B (Jetty
9.4.x), and Option C (Netty 4.1.x) against the criteria below. Netty is not the smallest
closure, but it is the only measured baseline that combines an actively patched release line,
the Java 8 baseline, clean relocation, and a single HTTP/WebSocket dependency set. Jetty 9.4 is
officially EOL and unsupported. The JDK option leaves the WebSocket protocol implementation
unresolved, and this ADR does not accept an unaudited in-house parser.

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

Netty 4.1 is scheduled for EOL on 2027-07-01. Moving to Netty 4.2 requires a higher Java
baseline and is a separate future decision. Until that migration is accepted, production
updates must stay on the latest patched 4.1 release.

## Consequences

### Positive

- The transport contract is defined before a library choice locks in its API.
- A measured decision avoids choosing a server by familiarity alone.
- Tests can use a fake transport without starting a real server.

### Negative and costs

- Netty adds a 3.3 MB uncompressed dependency closure before relocation.
- Netty 4.1 reaches upstream EOL on 2027-07-01 and will need a planned migration.
- Relocation, named threads, graceful shutdown, and an unused service-file exclusion must be
  implemented and tested rather than assumed.

### Follow-up

- Keep the internal transport interfaces independent of Netty types.
- Verify the final shaded artifact for unrelocated Netty classes and duplicate entries.
- Validate classloader isolation on a real Paper server with a conflicting plugin in the
  compatibility harness work tracked by issue forty-eight.
- Re-check the chosen library's supported Java line before every major release and before the
  2027-07-01 EOL date.

## Evidence and validation

- The standalone spike, dependency sizes, relocation output, and three WebSocket echo runs are
  recorded in [transport spike research](../research/transport-spike.md).
- No Minecraft server was started. Real Paper loading and conflicting-library classloader
  behavior remain unverified and are not a support claim.
- This ADR is `Accepted` for the Netty 4.1.141.Final choice only. The production implementation
  and its integration tests are recorded separately in the implementation status page.

## Spike evidence (2026-11-23)

The PART 40.3 re-check in [spike 1](../research/spikes/01-transport.md) records a passing
`:transport:test` invocation and cites the existing Paper smoke run. It does not change the accepted
library or claim a support tier. Netty 4.1 EOL tracking remains a follow-up.

## References

- Master specification, section 4.1 and Part 32.2 (starting hypotheses).
- [Threading model](../architecture/threading-model.md) for the resource limits the
  implementation must honor.
- [ADR-0004](ADR-0004-shading-and-relocation.md) for relocation requirements.
