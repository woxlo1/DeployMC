# Bukkit descriptor and compile baseline research

Access date: 2026-11-18. This is source-linked desk research plus local build observation.
No Minecraft server was started or loaded for this research.

## Question

WP-1.2 needs a `plugin.yml`, a `JavaPlugin` entry point, and version detection without NMS.
The repository also has a non-negotiable requirement that production Java in `platform/*`
compile with `--release 8`. The research question is which public API and descriptor values
can be selected without claiming an untested server compatibility tier.

## Observed facts

| Fact | Source |
|---|---|
| `name`, `version`, and `main` are required fields in `plugin.yml`. | [PaperMC Plugin configuration](https://docs.papermc.io/paper/dev/plugin-yml) |
| At the access date, Paper documents `api-version` values from `1.13` through `26.3`. A server older than the declared API version refuses to load the plugin. Omitting the field loads the plugin as legacy with a warning. | [PaperMC Plugin configuration](https://docs.papermc.io/paper/dev/plugin-yml) |
| `folia-supported: true` is required for a plugin to be considered for Folia, but the flag alone is not sufficient. | [PaperMC Folia support](https://docs.papermc.io/paper/dev/folia-support/) |
| `org.spigotmc:spigot-api` publishes `1.8.8-R0.1-SNAPSHOT` and newer versions. | [Spigot snapshot metadata](https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml) |
| `io.papermc.paper:paper-api` starts at `1.17-R0.1-SNAPSHOT` in the public metadata; there is no 1.8 paper-api artifact to use as an oldest-version baseline. | [Paper public metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml) |
| The Spigot 1.8.8 API POM requires `net.md-5:bungeecord-chat:1.8-SNAPSHOT`; Paper's public repository still publishes that snapshot. | [BungeeCord Chat snapshot metadata](https://repo.papermc.io/repository/maven-public/net/md-5/bungeecord-chat/1.8-SNAPSHOT/maven-metadata.xml) |
| `JavaPlugin` exposes `onLoad`, `onEnable`, `onDisable`, `getDataFolder`, `getDescription`, `getLogger`, and `getServer` in the public Bukkit API. | [Spigot JavaPlugin Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/plugin/java/JavaPlugin.html) |
| `BukkitTask.isCancelled()` is not present in the 1.8.8 API, so cancellation state must be tracked by the adapter if it needs to be reported through the SPI. | Local `javap` inspection of `spigot-api-1.8.8-R0.1-SNAPSHOT.jar` |

## Project decisions

1. **Compile against `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT` as `compileOnly`.** It is the
   oldest published Spigot API version and keeps the module compatible with Java 8 bytecode.
   It is a compile-time floor, not a claim that DeployMC runs on 1.8.8.
2. **Declare `api-version: '1.13'` deliberately.** This opts into the non-legacy API contract
   for supported servers. Consequently, servers older than 1.13 are expected to refuse the
   plugin. The generated descriptor does not claim support for those servers.
3. **Omit `folia-supported`.** Region-aware scheduling is not implemented, so the flag would
   be misleading. Folia work is tracked separately in WP-8.1.
4. **Inject `version` from Gradle and test the generated descriptor.** `plugin.yml` is produced
   from build inputs rather than hand-edited; tests read the generated resource without a
   server.
5. **Keep all server classes out of the distribution.** The final Shadow JAR contains the
   generated descriptor, entry point, and message catalog, but no `org/bukkit/**` classes.

## Local build observation

The first dependency resolution attempt with only the Spigot snapshot repository failed with
`Could not find net.md-5:bungeecord-chat:1.8-SNAPSHOT`. Adding Paper's public repository resolved
that transitive artifact. This is a build-repository fact, not evidence of runtime compatibility.

## Verification boundary

The generated descriptor, entry point, startup model, capability map, and task cleanup have unit
tests. Loading the built JAR on Paper latest and comparing thread dumps before and after a clean
disable were not performed. Those checks are deferred to the compatibility harness work in
[issue #48](https://github.com/woxlo1/DeployMC/issues/48). No support tier is claimed.
