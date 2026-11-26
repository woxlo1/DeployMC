# Compatibility matrix harness (v1)

This directory contains the WP-1.8 smoke harness. It is a developer tool written with the
Python standard library; it has no third-party dependencies. CLI output is technical tooling,
not product UI, so it is not routed through the Web/Java message catalogs.

## Safety

The harness writes `eula=true` **only** into a temporary sandbox server directory. This is CI
test infrastructure and is not a production recommendation. It uses two distinct loopback ports,
binds only to `127.0.0.1`, uses `online-mode=false`, accepts no production credentials, and
generates a fresh bearer token per run. The token record is written directly to the
host-controlled `plugins/DeployMC/tokens.d/` directory; the harness never parses a token from
console output. The generated record has an owner mapped in the host-controlled test `config.yml`
to a role with only `server.read`; unknown identities still receive `default-role: none`.

The conflicting plugin is the `DeployMCTestConflictingLibs` test fixture, not a DeployMC dependency.
It bundles unrelocated Netty 4.1.100.Final and registers a console-only command that disables
DeployMC through the Bukkit API so the harness can take a before/after-disable thread dump pair.

Generated tokens, server logs, and work directories are not committed. `.gitignore` excludes
`.cache/`, `results/`, and `work/`.

## Entry definition

`entries.json` pins Paper 26.3 build 256. See
[Paper downloads API evidence](../../docs/research/paper-download-api.md) for the official API,
User-Agent requirement, checksum field, and the observed response recorded on 2026-11-20. Paper
26.3 requires Java 25 at runtime; Gradle still builds with JDK 21 and only the server process uses
Java 25.

## Commands

From the repository root:

```powershell
python -m unittest discover -s tests/compat-matrix -p 'test_*.py'
python tests/compat-matrix/harness.py cache-key --entry paper-26.3
.\gradlew.bat :plugin:shadowJar :plugin:verifyPluginJar `
  :tests:fixtures:conflicting-plugin:shadowJar --console=plain
python tests/compat-matrix/harness.py run `
  --entry paper-26.3 `
  --plugin-jar plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar `
  --conflict-plugin-jar tests/fixtures/conflicting-plugin/build/libs/deploymc-test-conflicting-libs-1.0.0.jar `
  --result tests/compat-matrix/results/compat-v1-local.json `
  --java 'path\to\jdk-25\bin\java.exe'
```

`--conflict-plugin-jar` defaults to the fixture path shown above. `--java` defaults to `java`;
pass a Java 25 executable when the process on `PATH` is older.

`run` downloads through the official API, verifies SHA-256, creates an isolated server, waits for
a real `Done (` log line, polls `/healthz`, calls `/api/v1/server` with and without the
provisioned token, verifies the two JARs' Netty namespaces, captures a thread dump, disables
DeployMC through the fixture command, verifies the adapter release log and that the
`DeployMC-netty-*` threads are gone, writes a JSON result, stops the server, and force-kills only
after the shutdown timeout. It exits non-zero unless every required check passes.

Render a generated evidence section from one or more result files:

```powershell
python tests/compat-matrix/harness.py render `
  tests/compat-matrix/results/compat-v1-Windows.json `
  tests/compat-matrix/results/compat-v1-Linux.json
```

The renderer replaces the marked section in `docs/compatibility/support-matrix.md`. `--check`
fails without writing when the section is stale.

## CI

The `Compatibility (ubuntu-latest)` and `Compatibility (windows-latest)` jobs in
`.github/workflows/ci.yml` build both plugin JARs, run the offline unit tests, and run the pinned
Paper smoke entry. Paper downloads are cached by a key derived from the entry and runner OS; the
harness also re-hashes cached files. Logs and JSON results are uploaded as artifacts even when the
run fails.

## Scope and limits

v1 proves real server load, localized startup banner lines, an authenticated
`/api/v1/server` response, simultaneous load with an older unrelocated Netty library, relocation
isolation, before/after-disable thread dumps, log collection, and clean shutdown. It does **not**
earn a support tier and does not claim:

- the full shared integration suite (file read/write, snapshot, deploy, console stream, and
  restart semantics) required for Tier 1 or Tier 2;
- support claims for any platform or version beyond the recorded smoke result.

Those gaps are recorded in [support matrix](../../docs/compatibility/support-matrix.md).
