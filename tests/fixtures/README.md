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
| Traversal corpus with per-OS expectations | `../security/sandbox/` | Available (versioned manifest, categories, digests, and per-OS override mechanism) |
| JWT vectors, throwaway keys, mock JWKS | `../security/jwt/` | Available (deterministic vectors, in-memory keys, localhost mock JWKS) |
| Malformed archive and YAML-bomb generators | `../security/hostile-inputs/` | Available (generated at test time, manifest-pinned, no committed binaries) |
| Log corpora with control and Unicode signals | `log-corpora/` | Available (generated at test time, manifest-pinned) |
| Config corpora for valid, invalid, and legacy configurations | `config-corpora/` | Available (committed YAML, loader/validator tested) |
| Generated large directory trees | `large-trees/` | Available (generated at test time, manifest-pinned) |
| Cross-implementation filter vectors | Planned under `./` | Not started |

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

## Security corpora

`../security/sandbox/generate_corpus.py` regenerates `traversal-corpus.txt`, `allowed-paths.txt`,
and `corpus-manifest.properties` deterministically with LF endings. The manifest records schema
version 1, corpus version `2026-11-22.1`, source digests and counts, nine payload categories, and a
`category.<id>.os.<osId>` override mechanism. Schema 1 declares no overrides: `SandboxResolver`
applies Windows name rules on every host, so every category expectation is `os=all`. Protected-path
seeds are benign strings and are not part of the bulk reject corpus; `SandboxResolverTest` covers
that behavior directly.

```powershell
python tests/security/sandbox/generate_corpus.py
python -m unittest discover -s tests/security/sandbox -p 'test_*.py'
```

The corpus currently contains 2,736 reject and 29 allow payloads. No scheduled fuzz run or
cross-OS corpus execution has been performed from these fixtures.

`../security/jwt/generate_jwt_vectors.py` regenerates 11 versioned vectors, two public JWKS
documents, and a SHA-256 manifest. Three throwaway 2048-bit RSA keys are regenerated in memory from
fixed seeds; no private key is written or committed. The vectors cover valid, expired,
nbf-in-future, wrong audience, wrong issuer, `alg=none`, algorithm confusion, tampered signature,
unknown kid, rotated key, and retired key after rotation. `../security/jwt/mock_jwks_server.py`
serves those public keys from a dynamic localhost port with rotation and 503 states.

```powershell
python tests/security/jwt/generate_jwt_vectors.py
python -m unittest discover -s tests/security/jwt -p 'test_*.py'
```

No product JWT verifier, running server, or external JWKS endpoint consumes these fixtures yet.

`../security/hostile-inputs/generate_hostile_inputs.py` regenerates six archive fixtures and four
YAML fixtures from fixed deterministic writers. Dangerous and large outputs stay in an ignored
`generated/` directory or a test `TemporaryDirectory`; only the manifest, generator, tests, and
README are committed. The archives cover zip-slip, an 8 MiB fixed-Huffman zip bomb, duplicate
entries, duplicate descriptors, a real ZipCrypto entry, and a symlink entry. The YAML corpus covers
deep nesting, alias expansion, a large scalar, and many nodes. ZIP `current.reader` values are from
static source review; YAML product checks have not been executed.

```powershell
python tests/security/hostile-inputs/generate_hostile_inputs.py
python -m unittest discover -s tests/security/hostile-inputs -p 'test_*.py'
```

No product validator, running server, or integration consumer uses these fixtures yet.

`log-corpora/generate_log_corpora.py` regenerates four UTF-8 corpora: multi-line stack traces, ANSI
and OSC control sequences, boundary and oversized lines, and unusual Unicode. Outputs stay in an
ignored `generated/` directory or a test `TemporaryDirectory`; the manifest pins digests, byte and
code-point counts, and `current.consumer=not-implemented`. The manifest and six tests are committed;
no generated log text is committed.

```powershell
python tests/fixtures/log-corpora/generate_log_corpora.py
python -m unittest discover -s tests/fixtures/log-corpora -p 'test_*.py'
```

No console consumer, server, Web UI, or rendering code uses these corpora yet.

`config-corpora/generate_config_corpora.py` regenerates seven small ASCII YAML files byte-identically:
current valid, warning-only valid, two invalid values, legacy version 0, future version 2, and
missing version. The manifest pins digests, expected loader outcomes, and expected validator finding
keys. Unlike the other security corpora, these files are committed because they are small and are
fed directly to Java tests.

```powershell
python tests/fixtures/config-corpora/generate_config_corpora.py
python -m unittest discover -s tests/fixtures/config-corpora -p 'test_*.py'
```

`core.config.ConfigCorpusTest` reads the same files from the test classpath through
`ConfigValidator` and `ConfigManager`. It confirms the two valid files load, the warning file keeps
its root-key warning, and the five bad files fail closed with the expected localized message keys.
No server, host configuration, network resource, or real secret is touched.

`large-trees/generate_large_trees.py` regenerates 388 ASCII files in 27 directories, including a
12-level chain, a 209-character filename, and two 64 KiB binary files. Output stays in an ignored
`generated/` directory or a test `TemporaryDirectory`; the manifest pins counts, depth, total
bytes, and a content digest. Four tests verify determinism, manifest agreement, path safety, and
boundary shapes.

```powershell
python tests/fixtures/large-trees/generate_large_trees.py
python -m unittest discover -s tests/fixtures/large-trees -p 'test_*.py'
```

No listing API, performance benchmark, server, or UI consumes the tree yet.

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
