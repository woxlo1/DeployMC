# Configuration YAML library check

Access date: 2026-11-18. Environment: Windows 11, JDK 21 toolchain, Gradle 8.10.2 wrapper,
SnakeYAML 2.9. No Minecraft server was started for this check.

## Question

WP-1.5 needs a parser for the host-controlled `plugins/DeployMC/config.yml`. The parser must work
on Java 8, be licensed compatibly with the closed Apache-2.0 decision (ADR-0013), and avoid a
classloader collision with the older SnakeYAML that Bukkit/Spigot bundles.

## Observed external facts

| Fact | Source, accessed 2026-11-18 |
|---|---|
| SnakeYAML 2.x supports JVM 8 and later. | [SnakeYAML README](https://github.com/snakeyaml/snakeyaml) |
| SnakeYAML is Apache-2.0. | [SnakeYAML README](https://github.com/snakeyaml/snakeyaml) |
| Maven Central metadata lists `io.github.snake-yaml:snakeyaml` release `2.9`; versions `2.8`, `2.8.1`, `2.8.2`, and `2.9` are published under that group. | [Maven Central metadata](https://repo.maven.apache.org/maven2/io/github/snake-yaml/snakeyaml/maven-metadata.xml) |
| The maintained group ID is `io.github.snake-yaml`; the older `org.yaml` coordinate is a separate legacy line. | [Maven Central artifact](https://central.sonatype.com/artifact/io.github.snake-yaml/snakeyaml) |

## Commands and results

From the repository root, using the ASCII junction for Gradle:

```powershell
.\gradlew.bat :core:dependencies --configuration compileClasspath --console=plain
.\gradlew.bat build verifyJava8 --console=plain
```

Dependency resolution reported `io.github.snake-yaml:snakeyaml:2.9` on the `core` compile
classpath. The full build and Java 8 bytecode check passed. Inspection of
`plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar` reported 254 entries under
`io/github/woxlo1/deploymc/lib/snakeyaml/`, 0 entries under `org/yaml/snakeyaml/`, one
`config/config.schema.json` entry, and one `config/default-config.yml` entry.

## Decision

Use SnakeYAML 2.9 from `io.github.snake-yaml` and relocate it in the Shadow JAR to
`io.github.woxlo1.deploymc.lib.snakeyaml`. The parser uses `compose` rather than constructing
arbitrary objects, disables duplicate keys, aliases, recursive keys, and non-standard global tags,
and applies nesting and code-point limits. Configuration values are then validated against the
normative schema before use.

## Risks and follow-up

- The relocated JAR inspection is a local build check, not a real Paper server load. A real server
  with a conflicting-library plugin remains part of the #48 compatibility work.
- The Java validator implements the subset of JSON Schema keywords used by
  `specification/config/config.schema.json`; it is not a general-purpose JSON Schema validator.
- Role-reference integrity (`default-role`, user roles, approval roles) is an application-level
  check in addition to the schema.
