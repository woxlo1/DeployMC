# Spike 5: Folia scheduling

Status: `SOURCE-VERIFIED` against official Paper documentation; `NOT RUN` on Folia.

## Question

Which operations must move to which Folia scheduler, and what must the SPI expose before a Folia
adapter can claim support?

## External evidence

Sources accessed 2026-11-23:

- [Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/) states that
  `folia-supported: true` in the descriptor is not enough. It documents global, region, async, and
  entity schedulers, says the region scheduler runs work for the region owning a location, and says
  entity work must use the entity scheduler because it follows the entity across regions. It also
  shows a `ServerBuildInfo` feature check for Folia.
- [Scheduling](https://docs.papermc.io/paper/dev/scheduler/) describes the non-Folia
  `BukkitScheduler` and warns that large parts of the Bukkit API are not safe from asynchronous tasks
  when world state is accessed or changed.

## Current implementation gap

[platform-spi.md](../../architecture/platform-spi.md) records that `PlatformScheduler` has no region
parameter and that region-aware scheduling needs an additive interface or capability-specific path.
[threading-model.md](../../architecture/threading-model.md) forbids a silent global fallback for work
that requires a region. The Bukkit adapter does not declare Folia support.

## Required mapping

| Operation class | Expected Folia path | Current status |
|---|---|---|
| Plugin enable/disable, config reload, transport lifecycle | Global scheduler or owned executor; no world access | Design expectation, not Folia-tested |
| File I/O, hashing, snapshot, diff, validation | DeployMC async executors; never a region thread | Implemented server-free; scheduler binding not Folia-tested |
| HTTP/WebSocket event delivery | Async transport threads marshalled by the SPI | Implemented server-free; not Folia-tested |
| Location-based world or block work | Region scheduler with explicit location | SPI gap; no region context |
| Entity-based work | Entity scheduler | SPI gap; no entity context |
| Structured log handler attach/detach | Adapter lifecycle with no world-state assumption | SPI gap; not Folia-tested |

This mapping is a design inference from the official scheduler descriptions. It is not a claim that
the current adapter is Folia-safe.

## Result

Before a Folia adapter is started, the SPI needs a region/owner-aware scheduling extension and a test
that fails when region-required work is routed to the global path. ADR-0014 support tiers remain
unchanged.

## Not verified

No Folia server was started, no `folia-supported` flag was tested, and no region or entity scheduler
call was compiled or executed. No capability value was promoted to `AVAILABLE`.
