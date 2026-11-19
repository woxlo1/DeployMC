# Configuration YAML library check

Initial access date: 2026-11-18. Dependency reassessment: 2026-11-19. Environment: Windows 11,
JDK 21 toolchain, Gradle 8.10.2 wrapper. No Minecraft server was started for this check.

## Question

WP-1.5 needs a parser for the host-controlled `plugins/DeployMC/config.yml`. The parser must work
on Java 8, be licensed compatibly with the closed Apache-2.0 decision (ADR-0013), and avoid a
classloader collision with the older SnakeYAML that Bukkit/Spigot bundles.

## Observed external facts

| Fact | Source, accessed 2026-11-19 |
|---|---|
| SnakeYAML is a YAML 1.1 processor for JVM 8+ and is Apache-2.0. | [SnakeYAML README](https://github.com/snakeyaml/snakeyaml/blob/master/README.md) |
| Maven Central metadata lists `org.yaml:snakeyaml` release `2.7`; `io.github.snake-yaml:snakeyaml` publishes `2.8`, `2.8.1`, `2.8.2`, and `2.9`. | [`org.yaml` metadata](https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/maven-metadata.xml), [`io.github.snake-yaml` metadata](https://repo.maven.apache.org/maven2/io/github/snake-yaml/snakeyaml/maven-metadata.xml) |
| The `org.yaml:snakeyaml:2.7` POM declares `maven.compiler.source`, `target`, and `release` as `8`. | [2.7 POM](https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/2.7/snakeyaml-2.7.pom) |
| Version 2.7 was released on 2026-08-30. Its notes record fixes for #1116 (list comments), #1117 (recursive-key guard bypass), and #1115 (compact-constructor `TagInspector` bypass), plus the backwards-incompatible removal of compact object notation in #1113. | [Official changes page](https://codeberg.org/snakeyaml/snakeyaml/wiki/Changes) |
| Local inspection of `org.yaml:snakeyaml:2.7` found 230 common-path classes, all bytecode major 52, with manifest `Require-Capability: osgi.ee;filter:="(&(osgi.ee=JavaSE)(version=1.8))"` and three versioned entries. | Maven Central artifact and local ZIP/class-header inspection |
| Local inspection of `io.github.snake-yaml:snakeyaml:2.9` and `2.8.2` found common-path classes at bytecode major 53 and manifest `JavaSE 9`, even though their POMs declare release 8. | Maven Central artifacts, POMs, and local ZIP/class-header inspection |

## Commands and results

From the repository root, using the ASCII junction for Gradle:

```powershell
.\gradlew.bat :core:dependencies --configuration compileClasspath --console=plain
.\gradlew.bat spotlessApply build verifyJava8 --console=plain
.\gradlew.bat :plugin:reportPluginJarSize --console=plain
```

Dependency resolution reported `org.yaml:snakeyaml:2.7` on the `core` compile classpath. The full
build, Java 8 bytecode check, and configuration-cache-enabled plugin verification passed. The JUnit
XML reported 143 tests, 0 failures, and 0 errors. `:plugin:verifyPluginJar` reported:

```text
Plugin JAR verification passed: DeployMC-0.1.0-SNAPSHOT.jar, 4268456 bytes, 2493 classes
(0 versioned), max common bytecode 52
```

`:plugin:reportPluginJarSize` reported `4268456 bytes (4.07 MiB)`. ZIP inspection of
`plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar` found 230 relocated SnakeYAML classes under
`io/github/woxlo1/deploymc/lib/snakeyaml/`, 0 unrelocated `org/yaml/**` or `io/netty/**` class
entries, 0 multi-release entries, 0 `module-info.class` entries, 0 service files, and 0 duplicate
names.

Twelve fixture JARs were generated with `.\gradlew.bat :plugin:generatePluginJarFixtures --console=plain`
and verified with
`.\gradlew.bat --project-prop=deploymc.verifyJarFile=build/fixtures/<name>.jar :plugin:verifyPluginJarFixture --console=plain`.
Three valid fixtures passed; nine invalid fixtures failed with the expected diagnostic. These are
local packaging checks, not a real Paper server load.

## Decision

Use SnakeYAML 2.7 from `org.yaml` and relocate it in the Shadow JAR to
`io.github.woxlo1.deploymc.lib.snakeyaml`. The 2.9 group was rejected because its published
bytecode and OSGi manifest require Java 9, which would violate the Java 8 baseline even though the
source POM says release 8. The parser uses `compose` rather than constructing arbitrary objects,
disables duplicate keys, aliases, recursive keys, and non-standard global tags, and applies nesting
and code-point limits. Configuration values are then validated against the normative schema before
use.

## Risks and follow-up

- The relocated JAR inspection is a local build check, not a real Paper server load. A real server
  with a conflicting-library plugin remains part of the #48 compatibility work.
- The Java validator implements the subset of JSON Schema keywords used by
  `specification/config/config.schema.json`; it is not a general-purpose JSON Schema validator.
- Role-reference integrity (`default-role`, user roles, approval roles) is an application-level
  check in addition to the schema.
- Version 2.7 is intentionally the last `org.yaml` release used here. A later coordinate must be
  measured and recorded before adoption; POM compiler properties alone are not sufficient evidence.
