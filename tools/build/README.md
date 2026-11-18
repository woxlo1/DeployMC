# Local verification

`verify-all.cjs` runs the local equivalent of every language job in
[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) from one command. CI on GitHub Actions
remains the merge authority; this script is for checking a change before opening a pull request.

## Run everything

From the repository root:

```powershell
node tools/build/verify-all.cjs
```

It runs these toolchains in order and prints a `PASS`/`FAIL` line for each:

| Key | What runs |
|---|---|
| `web` | `pnpm install --frozen-lockfile`, lint, format check, Vitest, production build and precompression |
| `java` | `gradlew build verifyJava8` (all modules, JUnit, Java 8 bytecode check; packages `web/dist`) |
| `cli` | `cargo fmt --check`, clippy, tests, build for the whole workspace |
| `python` | Creates `.venv` if needed, installs the editable dev extra, ruff, mypy, pytest |
| `spec` | `npm ci`, specification lint, checker tests |
| `i18n` | Catalog check, checker tests, generated-status drift check |

A failing command stops that toolchain, but the remaining toolchains still run. The process exits
non-zero if any toolchain failed.

To run a subset:

```powershell
node tools/build/verify-all.cjs --only=java,web
node tools/build/verify-all.cjs --help
```

Valid keys are `java`, `web`, `cli`, `python`, `spec`, and `i18n`. Selection preserves the order
above, so `--only=java,web` still builds Web first. `--only=java` assumes `web/dist` already exists;
on a clean checkout, run the full command or `--only=web,java` once first.

## Requirements

All tools in [CONTRIBUTING.md](../../CONTRIBUTING.md) must be on `PATH`: JDK 21, Node.js 24, pnpm 9,
Rust/Cargo, and Python 3.10 or newer. The script uses the Gradle wrapper, so no system Gradle is
needed. It does not install Node, Rust, or JDK toolchains for you.

The `python` step creates `sdk/python/.venv` with the `python` command on `PATH`. To use a different
interpreter, set `DEPLOYMC_PYTHON` to it before running. Network access is needed the first time
dependencies are installed.

## Workarounds applied automatically

On Windows, when the checkout path contains non-ASCII characters, the script applies the workarounds
documented in [environment.md](../../docs/development/environment.md):

- Java: Gradle runs through an ASCII directory junction (`%TEMP%\deploymc-ascii`), created on first
  use and reused thereafter. Set `DEPLOYMC_JAVA_ROOT` to use a different ASCII checkout path.
- CLI: `CARGO_TARGET_DIR` is set to `%TEMP%\deploymc-cli-target` unless you set it yourself.
- Java: the Gradle command passes `--no-configuration-cache`. Spotless stores a JVM-local cache that
  goes stale when Gradle reuses the configuration cache across local JVMs
  ([diffplug/spotless#987](https://github.com/diffplug/spotless/issues/987)); CI uses a fresh runner
  and is not affected. This flag only applies to this script, not to the repository's
  `gradle.properties`.

If the checkout path is already ASCII, the junction and `CARGO_TARGET_DIR` overrides are skipped.

## Known limitation

Gradle test workers and OneDrive can leave a transient file lock on `build/reports/` or
`build/test-results/`, which makes a later `test` task fail while deleting its old output. If this
happens, stop the Gradle daemon and remove the module `build/` directories, then run the script again:

```powershell
.\gradlew.bat --stop
```

This is an environment issue, not a code failure. A first-run failure from a stale cache can be
retried; report a failure that repeats after the daemon is stopped.

## What this does not do

- It does not run CodeQL, dependency review, OpenSSF Scorecard, or commit-message linting. Those are
  GitHub-specific and run in the required checks on the pull request.
- It does not run the compatibility harness or a real Minecraft server; neither exists yet.
- A local pass does not replace the required CI checks on a pull request.
