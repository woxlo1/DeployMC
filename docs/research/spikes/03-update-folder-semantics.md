# Spike 3: Update-folder semantics by platform

Status: `SOURCE-VERIFIED` for Paper; `NOT RUN` for runtime behavior on every platform.

## Question

Can DeployMC rely on a server-managed update folder to install a plugin JAR safely across the target
platforms?

## External evidence

Sources accessed 2026-11-23:

| Platform | Source | Observation |
|---|---|---|
| Paper/Bukkit | [Updating](https://docs.papermc.io/paper/updating/) | Put replacement JARs in `plugins/update/`, then restart; do not remove or modify plugins outside that folder. Paper says this avoids replacing plugin JARs while the server is running. |
| Paper/Bukkit | [bukkit.yml reference](https://docs.papermc.io/paper/reference/bukkit-configuration) | `settings.update-folder: update` is a documented server setting with default `update`. |
| Paper plugin descriptor | [plugin.yml reference](https://docs.papermc.io/paper/dev/plugin-yml) | The descriptor fields describe plugin metadata and dependencies; the update folder is documented as a server setting, not a `plugin.yml` field. |
| Velocity | [velocity-plugin.json reference](https://docs.papermc.io/velocity/dev/velocity-plugin-json) | The documented descriptor fields are metadata, main class, dependencies, and provides. No update-folder field is documented. |
| BungeeCord | [BungeeCord configuration guide](https://www.spigotmc.org/wiki/bungeecord-configuration-guide/) | The checked configuration guide does not document an update-folder setting. Absence from that page is not proof that every BungeeCord/Waterfall build lacks one. |

## Per-platform result

| Platform class | Update-folder status | Evidence limit |
|---|---|---|
| Paper/Bukkit family | Source-documented; runtime behavior on our JAR not tested | Need a real restart with a versioned JAR and a load-error fixture |
| Folia | Not tested | ADR-0014 requires evidence; Paper's Bukkit-family documentation was not assumed to cover Folia |
| Velocity | Descriptor has no documented update-folder field; server behavior not tested | Do not report `update-folder=UNAVAILABLE` from descriptor inspection alone |
| BungeeCord/Waterfall | Not verified | Need official or reproduced runtime evidence |
| Standalone | Product design, not an external server behavior | Any swap semantics must be implemented and tested by DeployMC itself |

## Result

The [support matrix](../../compatibility/support-matrix.md) currently lists `update-folder` as
`Version-dependent` for Bukkit family and Folia and `No` for proxies. Those entries remain
hypotheses. The spike supports one design constraint only: update-folder support must be detected or
configured per platform and reported as a capability. It must not be inferred from a descriptor field
or from Paper documentation alone.

## Not verified

No restart, versioned JAR swap, rollback, or proxy plugin installation was executed. The older and
newest target versions are still undecided in D-001. No platform capability value is promoted from
`UNKNOWN` by this spike.
