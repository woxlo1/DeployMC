# Test fixtures

Everything under `tests/fixtures` is test-only instrumentation. Fixtures are built from source in
this repository, are never dependencies of `:plugin:shadowJar`, and must never be shipped in a release
artifact. Generated JARs stay under each project's ignored `build/` directory.

## PART 35 coverage

| Requirement | Location | Status |
|---|---|---|
| `deploymc-test-a` v1.0.0 and v1.1.0 | `plugins/deploymc-test-a/` | Available (source-built JARs) |
| `deploymc-test-broken-enable` | `plugins/deploymc-test-broken-enable/` | Available (source-built JAR; deliberate `onEnable` failure) |
| `deploymc-test-broken-descriptor` | `plugins/` | Planned |
| `deploymc-test-slow-enable` | `plugins/` | Planned |
| `deploymc-test-dependency` | `plugins/` | Planned |
| `deploymc-test-conflicting-libs` | `conflicting-plugin/` (`LegacyNettyFixture`) | Partial; name and registry alignment remain |
| `deploymc-test-log-noise` | `plugins/` | Planned |
| `deploymc-test-wrong-platform` | `plugins/` | Planned |
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
.\gradlew.bat :tests:fixtures:conflicting-plugin:shadowJar
```

The versioned plugin produces `deploymc-test-a-1.0.0.jar` and `deploymc-test-a-1.1.0.jar`. Both use a
stable plugin name so a deployment test can replace the JAR without changing the plugin identity.
The broken-enable plugin produces `deploymc-test-broken-enable-1.0.0.jar`; it saves its config and
then throws during `onEnable` to exercise failure and rollback signals.

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
