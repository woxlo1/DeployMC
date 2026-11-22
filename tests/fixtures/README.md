# Test fixtures

Everything under `tests/fixtures` is test-only instrumentation. Fixtures are built from source in
this repository, are never dependencies of `:plugin:shadowJar`, and must never be shipped in a release
artifact. Generated JARs stay under each project's ignored `build/` directory.

## PART 35 coverage

| Requirement | Location | Status |
|---|---|---|
| `deploymc-test-a` v1.0.0 and v1.1.0 | `plugins/deploymc-test-a/` | Available (source-built JARs) |
| `deploymc-test-broken-enable` | `plugins/deploymc-test-broken-enable/` | Available (source-built JAR; deliberate `onEnable` failure) |
| `deploymc-test-broken-descriptor` | `plugins/deploymc-test-broken-descriptor/` | Available (source-built JAR; descriptor omits required `main`) |
| `deploymc-test-slow-enable` | `plugins/deploymc-test-slow-enable/` | Available (source-built JAR; injectable 30s default delay) |
| `deploymc-test-dependency` | `plugins/deploymc-test-dependency/` | Available (source-built JAR; missing hard dependency) |
| `deploymc-test-conflicting-libs` | `conflicting-plugin/` (`DeployMCTestConflictingLibs`) | Available (source-built shadow JAR; canonical name and harness registry) |
| `deploymc-test-log-noise` | `plugins/deploymc-test-log-noise/` | Available (source-built JAR; configurable all-level rates) |
| `deploymc-test-wrong-platform` | `plugins/deploymc-test-wrong-platform/` | Available (source-built JAR; Velocity descriptor, no `plugin.yml`) |
| Traversal corpus with per-OS expectations | `../security/sandbox/` | Base corpus exists; per-OS metadata remains |
| JWT vectors, throwaway keys, mock JWKS | Planned under `../security/` | Not started |
| Malformed archive and YAML-bomb generators | Planned under `../security/` | Not started |
| Log, config, large-tree, and filter vectors | Planned under `../fixtures/` | Not started |

"Planned" is not evidence of support. This table is updated as each reviewable stage lands.

## Build

From the repository root:

```powershell
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-a:build
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-broken-enable:build
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-broken-descriptor:build
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-slow-enable:build
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-dependency:build
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-log-noise:build
.\gradlew.bat :tests:fixtures:plugins:deploymc-test-wrong-platform:build
.\gradlew.bat :tests:fixtures:conflicting-plugin:shadowJar
```

The versioned plugin produces `deploymc-test-a-1.0.0.jar` and `deploymc-test-a-1.1.0.jar`. Both use a
stable plugin name so a deployment test can replace the JAR without changing the plugin identity.
The broken-enable plugin produces `deploymc-test-broken-enable-1.0.0.jar`; it saves its config and
then throws during `onEnable` to exercise failure and rollback signals. The broken-descriptor plugin
produces `deploymc-test-broken-descriptor-1.0.0.jar`; its generated `plugin.yml` intentionally omits
the required `main` entry so validation can be observed rejecting the JAR. The slow-enable plugin
produces `deploymc-test-slow-enable-1.0.0.jar`; it sleeps for `slow-enable-millis` (default 30000) or
the `deploymc.fixture.slow-enable.millis` system property to exercise verification timeout behavior.
The dependency plugin produces `deploymc-test-dependency-1.0.0.jar`; its generated `plugin.yml`
declares the absent hard dependency `DeployMCTestMissingDependency` to exercise dependency warnings.
The conflicting-library plugin produces `deploymc-test-conflicting-libs-1.0.0.jar`; it bundles
unrelocated Netty 4.1.100.Final under the canonical `DeployMCTestConflictingLibs` identity used by the
compatibility harness registry. The log-noise plugin produces `deploymc-test-log-noise-1.0.0.jar`;
`config.yml` sets server-tick rates for `finest`, `finer`, `fine`, `config`, `info`, `warning`, and
`severe`. Each rate can be overridden with `deploymc.fixture.log-noise.<level>.ticks`, and a
non-positive value disables that level. The wrong-platform plugin produces
`deploymc-test-wrong-platform-1.0.0.jar`; it contains a Velocity `velocity-plugin.json` descriptor
and no Bukkit `plugin.yml`, so a Bukkit descriptor validator should reject it. No validator test
consumes it yet.

## Test environment hygiene

- Never point a fixture at the real home directory or real server data.
- Accept the Minecraft EULA only in a disposable CI/test directory; never commit `eula.txt`.
- Allocate ports dynamically. Do not assume port 8080 is free.
- Keep time and randomness injectable, or record the seed for a deterministic run.
- Generate large or dangerous payloads at test time. Do not commit multi-megabyte binaries or real
  credentials.
- Quarantine a flaky test with an issue and a deadline; never silently ignore it.

## Localization

Fixture log lines and command replies are intentionally hard-coded because they are test signals,
not product UI. Do not copy fixture strings into product message catalogs.
