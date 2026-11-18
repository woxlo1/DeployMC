# Transport spike

This standalone Gradle build preserves the ADR-0003 transport spike. It is not part of the
production dependency graph and is not built by the repository verification script.

The dependency-size, relocation, and WebSocket echo commands and their recorded results are in
[transport spike research](../../docs/research/transport-spike.md).

Run the build with the repository's Gradle wrapper:

```powershell
.\gradlew.bat -p tests\transport-spike printSpikeSizes --console=plain
.\gradlew.bat -p tests\transport-spike nettyRelocated jettyRelocated --console=plain
.\gradlew.bat -p tests\transport-spike run --args='5000 256' --console=plain --quiet
```

The echo program starts a local Netty WebSocket server and client, sends the requested number of
256-byte text frames by default, and reports elapsed time and throughput. No Minecraft server is
started.
