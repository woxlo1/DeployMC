# ADR-0002: Java 8 bytecode baseline with a modern toolchain

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner, maintainers
- **Related:** [Platform SPI](../architecture/platform-spi.md), [ADR-0014](ADR-0014-platform-support-tiers.md)

## Context

Minecraft server software spans Java versions. Legacy servers run on Java 8, mid-era
versions require Java 16 or 17, and recent versions (1.20.5 and later) require Java 21.
DeployMC wants to support as many versions as the evidence allows, starting with modern
Paper but not excluding older servers without a decision.

A plugin compiled for a newer class-file version cannot load on an older JVM, even if the
source uses no newer language features. Building on a modern JDK is desirable for
tooling, but the produced bytecode must remain loadable on the oldest supported runtime.

## Problem

Which Java bytecode baseline and build toolchain should the shared modules use?

## Considered alternatives

### Option A — Java 8 bytecode, built with a JDK 21 toolchain

- Compile `core`, `api`, `transport`, and `platform/*` with `--release 8`.
- Advantages: one artifact can load on legacy and modern servers; the compiler rejects
  newer JDK APIs; modern build tooling and tests remain available.
- Disadvantages: no records, `var`, `List.of`, text blocks, sealed types, or virtual
  threads on the shared path; some dependencies no longer support Java 8; more care is
  needed in code review.

### Option B — Java 17 or 21 baseline

- Compile for a modern runtime only.
- Advantages: modern language and library features; easier dependency choices.
- Disadvantages: excludes legacy servers entirely; contradicts the multi-version goal;
  would require a separate legacy artifact later anyway.

### Option C — Multi-release JAR with a modern base

- Ship Java 21 classes plus Java 8 variants where needed.
- Advantages: can use newer APIs on newer servers.
- Disadvantages: the base classes still need a legacy-compatible design; multi-release
  packaging, testing, and shading complexity; easy to accidentally load the wrong class;
  not needed before there is a measured benefit.

### Option D — Separate legacy and modern branches

- Maintain two source lines.
- Advantages: each line can use its natural Java level.
- Disadvantages: duplicate fixes, divergent behavior, twice the testing, and a high
  maintenance cost for a small team.

## Decision

Use Java 8 bytecode as the baseline for `core`, `api`, `transport`, and `platform/*`:

- Build with a JDK 21 toolchain using `--release 8`.
- Enforce a class-file major version of at most 52 in the build.
- Do not use JDK APIs newer than Java 8 in these modules.
- Compile platform adapters against the oldest supported server API and use runtime
  feature detection for newer behavior.
- Optional newer-Java optimizations may be added only through isolated, optional paths
  (for example, runtime detection), never as a hard requirement for the common path.
- Every bundled dependency must support Java 8; this is a review criterion for
  [ADR-0004](ADR-0004-shading-and-relocation.md).

Multi-release JARs and separate branches remain possible future options, but neither is
chosen now.

## Consequences

### Positive

- One plugin JAR can be attempted on a wide range of servers.
- The compiler prevents accidental use of newer JDK APIs.
- Modern tests and tooling still run on JDK 21.

### Negative and costs

- Contributors cannot use modern Java syntax in shared modules.
- Some libraries must be pinned to older, still-maintained versions.
- Java 8 itself is end-of-public-updates; security fixes depend on vendors. This is a
  server-runtime constraint, not a preference.
- Newer-Java performance features are unavailable on the common path.

### Follow-up

- Keep `verifyJava8` in CI on Linux and Windows.
- Add a dependency Java-version check before bundling any library.
- Validate the baseline on a real legacy server before claiming support; bytecode
  compatibility alone is not a support claim.

## Evidence and validation

- `.\gradlew.bat build verifyJava8` passed locally on 2026-11-16 and 2026-11-17 through
  the documented ASCII junction. The build compiles all Java modules with `--release 8`.
- CI run
  [44039215763](https://github.com/woxlo1/DeployMC/actions/runs/44039215763) passed the
  Java jobs on `ubuntu-latest` and `windows-latest`, including `verifyJava8`.
- **Not verified:** no legacy Minecraft server has loaded the plugin. The baseline is a
  build policy, not a compatibility claim. See
  [the support matrix design](../compatibility/support-matrix.md).

## References

- Master specification, sections 3.3 and 4.1.
- `build.gradle.kts` (`options.release.set(8)` and the `verifyJava8` task).
