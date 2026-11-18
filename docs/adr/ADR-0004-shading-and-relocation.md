# ADR-0004: Shading and relocation strategy

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** maintainers
- **Related:** [ADR-0002](ADR-0002-java-8-bytecode-baseline.md), [ADR-0003](ADR-0003-embedded-http-websocket-server.md), [Architecture overview](../architecture/overview.md)

## Context

A Minecraft plugin runs in a shared classloader environment with the server and other
plugins. Bundling a library without relocating it can collide with another plugin's
version of the same library, with the server's own copy, or with a future DeployMC
version. The failure mode is usually a `NoSuchMethodError`, a `ClassCastException`, or a
subtle behavior change that is hard to diagnose.

DeployMC also wants a single drop-in JAR. Asking administrators to install dependencies
separately is error-prone and incompatible with a narrow, auditable deployment tool.

## Problem

How should DeployMC package third-party dependencies so that one JAR is self-contained
without breaking the server or other plugins?

## Considered alternatives

### Option A — No shading; declare dependencies

- Ship only DeployMC classes and require the server or administrator to provide
  libraries.
- Advantages: smallest artifact; no relocation.
- Disadvantages: not drop-in; version conflicts are pushed to the administrator; unsafe
  for a security-sensitive plugin; not acceptable for the product goal.

### Option B — Shade without relocation

- Bundle dependencies under their original package names.
- Advantages: simple build.
- Disadvantages: high classloader-conflict risk; violates the isolation goal; makes
  failures depend on plugin load order.

### Option C — Shade and relocate every bundled dependency

- Bundle dependencies and rewrite their packages under a DeployMC-specific prefix.
- Advantages: isolation from other plugins and the server; predictable class loading;
  one artifact.
- Disadvantages: larger JAR; reflection, service files, resource paths, and native
  libraries need care; stack traces are less familiar; relocation must be tested.

### Option D — Separate child classloader

- Load bundled dependencies in an isolated classloader at runtime.
- Advantages: strong isolation.
- Disadvantages: complex lifecycle and reflection boundaries; plugin APIs may not work
  across loaders; more failure modes; unnecessary if relocation is correct.

## Decision

Use the Gradle Shadow plugin to produce a single plugin JAR and relocate every bundled
third-party dependency under a unique DeployMC prefix, initially:

```
io.github.woxlo1.deploymc.lib.<library>
```

Rules:

- Keep the dependency set minimal. Prefer the JDK when it is sufficient.
- Every bundled dependency must support Java 8 and have a compatible license. Record the
  checks in the dependency review.
- Merge service files; do not silently drop them.
- Relocate package references in class files, resources, and reflection-sensitive
  metadata where the library supports it. Document any library that cannot be relocated
  and do not bundle it without an explicit exception.
- Do not bundle native libraries unless a platform requirement is proven and the
  packaging is tested on every supported OS.
- Exclude unused transitive modules where safe, and verify the result with a dependency
  report.
- Preserve license notices for bundled dependencies.
- The final artifact must be tested for accidental unrelocated classes and for duplicate
  classes.

The public Java API (`api/`) keeps its documented package
`io.github.woxlo1.deploymc.api`; it is not relocated. Only third-party libraries and
internal implementation details are relocated.

## Consequences

### Positive

- One drop-in JAR with predictable class loading.
- Lower risk of conflicts with other plugins and the server.
- Dependency upgrades do not depend on what else is installed.

### Negative and costs

- Larger artifact and a longer build.
- Reflection and resource lookups can break after relocation; each library needs a test.
- Stack traces contain relocated names, which can confuse users and maintainers.
- Relocation rules must be updated whenever a dependency is added.
- Some libraries cannot be relocated safely and must be replaced or avoided.

### Follow-up

- Add an automated check that fails the build if a known bundled package appears
  unrelocated.
- Add a duplicate-class and service-file check to CI.
- Document the relocation prefix in the developer guide.
- Verify the shaded JAR on a real server before any support claim.

## Evidence and validation

- Shadow plugin `8.3.5` is configured in the version catalog and applied to `plugin/`;
  `shadowJar` merges service files and produces the `DeployMC` artifact.
- WP-1.3 configured `relocate("io.netty", "io.github.woxlo1.deploymc.lib.netty")` and excluded the
  unused `META-INF/services/reactor.blockhound.integration.BlockHoundIntegration` entry. The
  resulting `DeployMC-0.1.0-SNAPSHOT.jar` was inspected on 2026-11-18: 2,164 relocated Netty
  classes, 0 unrelocated `io/netty/**` classes, 0 duplicate entries, and no service files.
- **Not verified:** the shaded artifact has not been loaded on a real Minecraft server, and the
  automated duplicate-class and unrelocated-package CI check remains a follow-up. Those checks are
  part of the compatibility harness work, not a support claim.
- Java 8 compatibility of the build itself is verified by
  [ADR-0002](ADR-0002-java-8-bytecode-baseline.md).

## References

- Master specification, section 4.1 and Part 3.3.
- `gradle/libs.versions.toml` and `plugin/build.gradle.kts`.
