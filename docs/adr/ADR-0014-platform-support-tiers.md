# ADR-0014: Platform support tiers and compatibility harness

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner, maintainers
- **Related:** [Platform SPI](../architecture/platform-spi.md), [Support matrix design](../compatibility/support-matrix.md), [ADR-0002](ADR-0002-java-8-bytecode-baseline.md)

## Context

The project owner wants DeployMC to support as many Minecraft versions and server
platforms as realistically possible. "Support everything" is not a testable claim.
Minecraft server software, plugin APIs, Java requirements, restart behavior, log capture,
and file locking differ across Paper, Folia, Spigot, proxies, legacy versions, and hosting
panels. A version that has never been run cannot be called supported.

The project also wants contributors and users to report compatibility results without
weakening the meaning of the support matrix.

## Problem

How should support be defined, verified, and communicated so that no version is advertised
without evidence?

## Considered alternatives

### Option A — Claim broad support based on API compatibility

- Assume that if it compiles against the Bukkit API, it works.
- Advantages: broad marketing claim; little testing.
- Disadvantages: false claims; failures on legacy versions, Folia, proxies, and hosting
  setups; erodes trust. Rejected.

### Option B — Support only the latest Paper

- Test one configuration and declare everything else unsupported.
- Advantages: simplest matrix.
- Disadvantages: ignores the owner's goal; leaves realistic deployments without guidance;
  does not use the SPI investment.

### Option C — Evidence-based tiers with a real-server harness

- Define tiers; assign entries only after automated or reported runs; generate the matrix
  from results.
- Advantages: honest claims; failures are found before release; contributors can add
  evidence; the tier label has meaning.
- Disadvantages: CI cost and flakiness; server downloads and EULA handling; maintenance
  across versions; not every platform can be automated.

## Decision

Define three tiers and assign an entry only from evidence:

| Tier | Requirement |
|---|---|
| Tier 1 | A real server of that platform/version is started in CI on every pull request or nightly, DeployMC loads, and an automated integration test exercises the API (file read/write, snapshot, deploy, console stream, shutdown). |
| Tier 2 | The same kind of test runs on a schedule (nightly/weekly) or on release candidates, not every pull request. |
| Tier 3 | Best effort or community-reported; not tested automatically; known limitations documented. |

Rules:

- Never put a version in Tier 1 or Tier 2 unless the matrix job actually runs and passes.
- Never claim support for a version that has not been run at least once.
- A failure demotes or removes an entry; it is not hidden.
- Every entry records platform, server version, Minecraft version, Java vendor/version,
  operating system, DeployMC commit, test-suite version, date, result, and a log artifact.
- The compatibility harness downloads official server software from official sources,
  verifies checksums where the provider supplies them, accepts the EULA only in the test
  sandbox, starts the server headless with a minimal world, installs the built JAR, and
  runs the shared integration suite through the public API.
- The harness runs on Linux and Windows where the server software supports it, collects
  logs as artifacts, and generates the matrix section rather than relying on hand-edited
  claims.
- Capabilities are recorded per platform and version. A missing capability is documented
  with the feature it disables, not treated as a bug in every case.
- Community compatibility reports are accepted through the compatibility issue form and
  are labeled as unverified until a maintainer reproduces them or the harness runs them.

The candidate list (Paper latest, Paper forks, Folia, Spigot, legacy Paper/Spigot down to
1.8.8, Velocity, BungeeCord/Waterfall, standalone, and research-only Sponge/Fabric/
Forge/NeoForge) is a set of hypotheses, not a support claim. See
[support-matrix.md](../compatibility/support-matrix.md).

## Consequences

### Positive

- The support matrix has a clear, defensible meaning.
- Regressions on tested platforms are caught before release.
- Contributors can add evidence without guessing.
- Users can see which features are unavailable on their platform and why.

### Negative and costs

- CI time, disk, and maintenance grow with the matrix.
- Server downloads may be unavailable, rate-limited, or licensed in ways that affect CI.
- Real-server tests can be flaky; retries must not hide genuine failures.
- Some hosting setups (Pterodactyl, Docker, Windows services) cannot be fully represented
  by a headless process test.
- Tier 3 reports need moderation and reproduction.

### Follow-up

- Build `tests/compat-matrix/` and the shared integration suite.
- Add the generated matrix output and a badge/JSON result.
- Define promotion and demotion rules in the support-matrix document.
- Start with one Tier 1 entry (Paper latest on Java 21) and expand only from green runs.
- Add the compatibility report issue form and Discussion category.
- Re-check server download APIs and checksums when implementing the harness.

## Evidence and validation (historical, 2026-11-17)

- The tier definitions and rules follow master specification sections 3.1 and 3.6.
- **Not verified at the time:** the harness did not exist, and no server had been run. The
  support matrix had zero entries in every tier.
- The Docker daemon was not running on the current development host at that time.

## Spike evidence (2026-11-23)

The PART 40.3 records in [the spike index](../research/spikes/README.md) add source and local
evidence for transport, Windows JAR replacement, update folders, log capture, Folia scheduling,
embedded UI size, and restart behavior. The existing Paper smoke rows in
[the support matrix](../compatibility/support-matrix.md) still do not earn a tier. Update-folder,
log capture, Folia scheduling, and supervisor restart behavior remain unverified on real target
servers; D-001 is still open. No ADR-0014 rule is relaxed by these spikes.

## References

- Master specification, sections 3.1, 3.2, 3.6, and Part 32.4.
- [Support matrix design](../compatibility/support-matrix.md).
- [Platform SPI](../architecture/platform-spi.md).
