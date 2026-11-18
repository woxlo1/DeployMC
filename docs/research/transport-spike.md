# Embedded transport spike

Access date: 2026-11-18. Environment: Windows 11, JDK 21 toolchain, Gradle 8.10.2 wrapper,
Netty 4.1.141.Final, Jetty 9.4.58.v20250814. The spike source is in
[`tests/transport-spike`](../../tests/transport-spike). All measurements were local; no
Minecraft server was started.

## Question

ADR-0003 requires a measured choice for the embedded HTTP/WebSocket server. The spike compares
dependency size, relocation, Java 8 bytecode, WebSocket echo throughput, and maintenance status
for the three baseline options. Option D (Undertow) is not needed because the baseline produced a
viable choice.

## Observed external facts

| Fact | Source, accessed 2026-11-18 |
|---|---|
| Netty 4.1.141.Final was released on 2026-11-16 as a bug-fix release. | [Netty release news](https://netty.io/news/2026/11/16/4-1-141-Final.html) |
| Netty 4.1 will be end-of-life on 2027-07-01; 4.2 is the recommended upgrade line. | [Netty EOL announcement](https://netty.io/news/2026/09/09/4-1-EOL-announcement.html) |
| Netty 4.x targets Java 6 bytecode or later. | [Netty downloads](https://netty.io/downloads.html) |
| Netty is Apache-2.0. | [Netty home](https://netty.io/) |
| Jetty 9.4.58.v20250814 is marked EOL / unsupported; Jetty 12 is the community-supported line and requires Java 17. | [Jetty downloads](https://jetty.org/download.html) |
| `com.sun.net.httpserver` is a JDK-backed embedded HTTP API that provides only a partial HTTP/1.1 implementation. | [Oracle JDK 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html) |

## Commands and results

From the repository root, using the ASCII junction for Gradle:

```powershell
.\gradlew.bat -p tests\transport-spike printSpikeSizes --console=plain
.\gradlew.bat -p tests\transport-spike nettyRelocated jettyRelocated --console=plain
.\gradlew.bat -p tests\transport-spike run --args='5000 256' --console=plain --quiet
```

Dependency resolution reported:

| Candidate | Artifacts | Raw JAR bytes |
|---|---:|---:|
| Netty 4.1.141.Final, `netty-codec-http` closure | 8 | 3,377,875 |
| Jetty 9.4.58.v20250814, `websocket-server` + `jetty-server` closure | 14 | 2,937,014 |
| JDK `com.sun.net.httpserver` | 0 | 0 |

Shadow relocation reported:

| Candidate | Relocated JAR bytes | Classes | Relocated classes | Unrelocated classes | Duplicate entries |
|---|---:|---:|---:|---:|---:|
| Netty | 3,522,525 | 2,164 | 2,164 | 0 | 0 |
| Jetty | 2,879,185 | 1,584 | 1,584 | 0 | 0 |

The sample Netty class file reported bytecode major version 50 (Java 6), which satisfies the
project's Java 8 ceiling.

Netty WebSocket echo over loopback, 5,000 messages of 256 bytes per run, three runs:

```text
echo messages=5000 payloadBytes=256 elapsedMs=97.9 messagesPerSecond=51095 mibPerSecond=12.47
echo messages=5000 payloadBytes=256 elapsedMs=98.7 messagesPerSecond=50652 mibPerSecond=12.37
echo messages=5000 payloadBytes=256 elapsedMs=101.9 messagesPerSecond=49046 mibPerSecond=11.97
```

This is a same-JVM loopback smoke benchmark, not a production throughput guarantee.

## Decision

The owner selected **Netty 4.1.141.Final** on 2026-11-18. It is actively patched, supports the
Java 8 baseline, relocates cleanly in the Shadow spike, and provides HTTP and WebSocket in one
dependency closure. Jetty is smaller but unsupported upstream. The JDK-only option leaves the
WebSocket protocol implementation unresolved and would require a separate audited dependency;
the ADR does not accept an unaudited in-house parser.

## Risks and follow-up

- Netty 4.1 is scheduled for EOL on 2027-07-01. A migration to a newer Java baseline and Netty 4.2
  must be tracked before that date; it is not part of WP-1.3.
- `netty-common` contains an optional `reactor.blockhound.integration.BlockHoundIntegration`
  service entry whose text still names the unrelocated class. Reactor is not bundled. The
  production Shadow configuration excludes this unused service file and records the exclusion.
- A real Paper server with a second plugin that bundles an unrelocated Netty version was not
  started. That classloader-conflict check remains in the compatibility harness work tracked by
  [issue #48](https://github.com/woxlo1/DeployMC/issues/48). No support tier is claimed from this
  spike.
