# ADR-0013: License selection

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Related:** [ADR-0001](ADR-0001-monorepo-structure.md), [ADR-0004](ADR-0004-shading-and-relocation.md)

## Context

DeployMC is a Minecraft server plugin and developer tool. It may later be published to
Modrinth, Hangar, Maven Central, npm, PyPI, and crates.io. The license affects adoption by
server administrators and plugin developers, compatibility with dependencies, and the
project's ability to accept contributions.

Minecraft server APIs (Bukkit, Spigot, Paper) have their own licensing and distribution
terms. Bundling a dependency under an incompatible license would make the distributed JAR
undistributable. The project owner asked for a recommendation before the license was
chosen.

## Problem

Which license should the repository and distributed artifacts use?

## Considered alternatives

### Option A — MIT

- Advantages: very permissive; simple; widely understood; maximum adoption.
- Disadvantages: no explicit patent grant; weaker contributor patent protection.

### Option B — Apache License 2.0

- Advantages: permissive; explicit patent grant; trademark and notice provisions; familiar
  to Java and enterprise users; compatible with many dependencies.
- Disadvantages: longer notice obligations; some licenses are incompatible with Apache-2.0
  when combined in certain ways; not a copyleft guarantee.

### Option C — GPL-3.0

- Advantages: strong copyleft; modifications shared.
- Disadvantages: plugin ecosystems often mix licenses; distributing a JAR that links
  server APIs and other plugins raises compatibility questions; may discourage adoption
  and commercial use.

### Option D — AGPL-3.0

- Advantages: strong copyleft including network use.
- Disadvantages: the network-use clause is a poor fit for a self-hosted tool and can deter
  adoption; compatibility with the plugin ecosystem is harder.

## Decision

Use the Apache License 2.0 for the repository and the project's original code:

- Include the full `LICENSE` file and preserve copyright and attribution notices.
- Bundled third-party dependencies must have licenses compatible with Apache-2.0 and with
  distribution inside the plugin JAR. Record the check in the dependency review.
- Do not bundle a GPL-only or otherwise incompatible dependency in the distributed JAR
  without a deliberate, documented exception that the owner accepts.
- The Java API (`api/`) and all language SDKs use the same license unless a documented
  exception is needed.
- The project does not require a contributor license agreement at this stage. Contributions
  are accepted under the repository license; this is stated in `CONTRIBUTING.md`.
- This ADR is a project decision, not legal advice. A legal review has not been performed.

The owner chose Apache-2.0 on 2026-11-16 after the license comparison was presented.

## Consequences

### Positive

- A permissive license with an explicit patent grant.
- Broad compatibility with the Java, TypeScript, Rust, and Python ecosystems.
- Encourages adoption by server administrators and plugin developers.

### Negative and costs

- Notice and attribution obligations must be maintained, especially in a shaded JAR.
- Some dependencies may be incompatible and must be replaced or excluded.
- Apache-2.0 does not require downstream modifications to be shared; the project relies on
  community norms and its own development process, not copyleft.

### Follow-up

- Keep `LICENSE` and dependency notices up to date.
- Add a third-party notice file when the first dependency is bundled.
- Re-check the license of every new runtime dependency before adding it.
- Revisit the license only through a new ADR; changing it later is a significant governance
  event.

## Evidence and validation

- The repository contains `LICENSE` with Apache License 2.0.
- `sdk/python/pyproject.toml` declares `Apache-2.0`.
- `cli/Cargo.toml` declares `Apache-2.0` in the workspace package metadata.
- The owner decision is recorded in
  [docs/development/decisions-pending.md](../development/decisions-pending.md) with the
  date 2026-11-16.
- **Not verified:** no legal review has been performed, and no third-party dependency has
  been cleared for bundling because none is bundled yet. The check is a release
  requirement, not a completed review.

## References

- Master specification, section 10.2 and WP-0.2.
- [docs/development/decisions-pending.md](../development/decisions-pending.md).
- `LICENSE`, `CONTRIBUTING.md`, `sdk/python/pyproject.toml`, `cli/Cargo.toml`.
