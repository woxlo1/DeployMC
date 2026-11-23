# Spike 1: Embedded transport

Status: `OBSERVED` locally; existing real-server smoke evidence cited but not rerun here.

## Question

Confirm that the embedded HTTP/WebSocket transport decision still has recorded evidence and that the
current implementation builds and tests without a Minecraft server.

## Evidence

The original measured comparison is in [the transport spike](../transport-spike.md) and
[ADR-0003](../../adr/ADR-0003-embedded-http-websocket-server.md). It compared JDK
`com.sun.net.httpserver`, Jetty 9.4, and Netty 4.1 on Java 8 bytecode, relocation, dependency size,
and loopback WebSocket echo throughput. The owner accepted Netty 4.1.141.Final on 2026-11-18.

This spike reran the current server-free transport suite on 2026-11-23:

```powershell
.\gradlew.bat :transport:test --console=plain --no-configuration-cache
```

Result: `BUILD SUCCESSFUL in 4s`. The `:transport:test` task reported `FROM-CACHE` because the same
test inputs had already been verified in this worktree; this is a cached pass, not a newly executed
test run. The full `verify-all.cjs` run for this branch executes the task without the subset flag.

The pinned Paper smoke entry `paper-26.3` has passing Linux and Windows results in
[the support matrix](../../compatibility/support-matrix.md), CI run
[44704690836](https://github.com/woxlo1/DeployMC/actions/runs/44704690836), recorded on
2026-11-20 at commit `839b789`. That evidence covers plugin load, authenticated `/api/v1/server`,
a conflicting-library fixture, and clean disable. It does not cover the full API or earn a support
tier.

## Result

No change to ADR-0003. Netty remains the accepted transport for the Java 8 baseline. The next
transport risk is the scheduled 2027-07-01 EOL of Netty 4.1, already recorded in the ADR; this spike
does not migrate or re-choose the library.
