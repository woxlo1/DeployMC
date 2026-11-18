# Compatibility Status

Last updated: 2026-11-18.

The working matrix is [docs/compatibility/support-matrix.md](../compatibility/support-matrix.md).
This page records its current status for session handoff.

| Item | Current state |
|---|---|
| Tier 1 entries | 0 |
| Tier 2 entries | 0 |
| Tier 3 entries | 0 |
| Matrix | Empty; no DeployMC build has been loaded by a Minecraft server |
| Compatibility harness | Not implemented; tracked in #48 (WP-1.8) |
| Last matrix run | None; no run date can be reported |
| Platform adapters | None implemented; Bukkit-family adapter tracked in #42, Folia in #96, proxy adapters in #101 and #102, standalone in #103 |
| Paper forks | Not verified; #100 |
| Legacy versions | Not verified; #98, and research on update-folder semantics is open |
| Java 8 bytecode | Verified for production class files by `verifyJava8`; this is not a server compatibility claim |

No entry may be added by hand to advertise intent. Rows come from harness results or a reviewed
Tier 3 report, as defined in the support matrix. A failing run demotes or removes an entry.

When the harness lands, update this page with the exact run link, tested commit, platform and
server versions, operating system, checks passed/failed, and UTC date for every matrix row.
