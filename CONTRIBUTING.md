# Contributing to DeployMC

Thank you for considering a contribution. DeployMC is **pre-alpha**: interfaces, configuration, and
behavior can change without notice. Bug reports, documentation fixes, translations, and compatibility
reports are as valuable as code right now.

By participating, you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- Report a reproducible bug (see [SUPPORT.md](SUPPORT.md)).
- Improve or translate documentation (see [TRANSLATING.md](TRANSLATING.md)).
- Report real compatibility results on a specific server version or platform.
- Propose or implement a feature through an issue first for anything non-trivial.
- Review pull requests, especially in areas you operate in production.

For security vulnerabilities, **do not open a public issue**. Follow [SECURITY.md](SECURITY.md).

## Required tools

| Tool | Version used by the maintainers | Notes |
|---|---|---|
| JDK | 21 (Temurin 21.0.11 verified) | Gradle toolchains compile production Java with `--release 8` |
| Gradle | wrapper (8.10.2) | Always use `gradlew.bat` / `./gradlew`, never a system Gradle |
| Node.js | 24.13.1 | Web UI |
| pnpm | 9.15.4 | `corepack enable` or install pnpm 9 |
| Rust | 1.100.0 (GNU on Windows, stable elsewhere) | CLI |
| Python | 3.12.10 (minimum 3.10) | Python SDK |
| Git | any current version | Branch + pull request workflow |

Also useful: `gh` for GitHub operations, Docker for the later compatibility harness (not needed yet).

## Repository layout

| Path | Contents |
|---|---|
| `core/` | Platform-independent Java 8 library: sandbox, snapshots, deployment, auth, RBAC, audit |
| `api/` | Public Java API for plugin authors |
| `transport/` | Embedded HTTP/WebSocket server |
| `platform/` | Bukkit-family, Folia, Velocity, BungeeCord, and standalone adapters |
| `plugin/` | Shaded plugin JAR assembly |
| `web/` | React + TypeScript Web UI |
| `cli/` | Rust command-line client |
| `sdk/` | TypeScript, Python, and Java clients |
| `actions/` | GitHub Action |
| `specification/` | Normative API and configuration specifications |
| `docs/` | Architecture, ADRs, guides, research, development tracking, i18n |
| `tools/` | Repository automation, including the i18n checker |
| `tests/` | Integration, compatibility, end-to-end, security, fuzz, and performance tests |

## Setup and build

Clone the repository and work on a branch. `main` is protected; every change arrives through a pull
request.

### Java

```powershell
.\gradlew.bat build
.\gradlew.bat verifyJava8
.\gradlew.bat spotlessApply
```

`build` compiles all modules, runs the JUnit 5 tests, and checks formatting. `verifyJava8` proves that
production classes target Java 8 bytecode. `api`, `core`, `transport`, and `platform/*` must never
require a newer Java API.

Windows note: if the absolute repository path contains non-ASCII characters, Gradle test workers may
fail to load classes. Run Gradle through an ASCII junction as described in
[docs/development/environment.md](docs/development/environment.md).

### Web UI

Run from `web/`:

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm format:check
pnpm test
pnpm build
```

### CLI

Run from `cli/`:

```powershell
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
cargo build --workspace
```

On Windows with the GNU toolchain, point `CARGO_TARGET_DIR` at an ASCII path if the repository path
contains non-ASCII characters (see `docs/development/environment.md`).

### Python SDK

Run from `sdk/python/`:

```powershell
python -m venv .venv
.venv\Scripts\python -m pip install -e ".[dev]"
.venv\Scripts\python -m ruff check .
.venv\Scripts\python -m ruff format --check .
.venv\Scripts\python -m mypy
.venv\Scripts\python -m pytest
```

### Message catalogs

```powershell
node tools/i18n/check
node --test tools/i18n/test/check.test.cjs
```

## Coding standards

- Repository content is in English: code, comments, commit messages, issues, pull requests, test
  names, and documentation. Translations are additions under `docs/i18n/` and `web/src/locales/`.
- Follow the formatting and lint configuration in each component; do not reformat unrelated code.
- Never access the platform through NMS or undocumented internals. Feature-detect and report
  capabilities.
- All file access goes through the sandbox resolver; never build paths from raw input.
- No unauthenticated endpoint except `/healthz` and `/readyz`, and no bypass flags. Fail closed.
- Do not add a user-facing string without a message-catalog entry.
- New dependencies need a reason, a license check, and a maintained version. Shaded dependencies are
  relocated and verified by the build.
- Tests must not touch the real home directory, real server data, or unrelated network resources.

## Commit conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) in English:

```text
<type>(<scope>): <description>
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`,
`security`, `revert`. Use the scope that matches the repository area (`core`, `api`, `transport`,
`platform`, `web`, `cli`, `sdk-py`, `sdk-ts`, `action`, `docs`, `i18n`, `ci`, `release`, ...).

- Keep the subject imperative, lowercase after the type, and under 72 characters.
- Explain *why* in the body when the change is not obvious.
- Reference the tracking issue in the footer (`Refs #12`).
- If an AI tool contributed, add a truthful `Co-authored-by` trailer for that tool.
- Do not add trailers for people who did not contribute.
- One logical change per commit. Do not mix formatting-only changes with behavior changes.

## Branches and pull requests

- Branch from up to date `main`, named `<type>/<short-slug>`, for example `feat/sandbox-resolver` or
  `docs/core-project-documents`.
- Open one focused pull request per work package or logical stage. Split a change that grows beyond a
  reviewable size.
- Fill in the pull request template: what changed, why, the work package, tests actually run, and any
  compatibility, security, or localization impact.
- Never claim a command passed unless you ran it. Paste the exact commands and results.
- Keep the branch linear and merge with rebase so each commit stays meaningful.
- A pull request must be green in CI and have no unresolved review conversations before merge. The
  workflows and their local equivalents are documented in
  [docs/development/ci.md](docs/development/ci.md).

## Review procedure

- The maintainer reviews every pull request. While the project has a single maintainer, the same
  person may author and merge a pull request after CI passes; this is disclosed rather than hidden.
- Reviewers check correctness, security defaults, sandbox and auth implications, tests, documentation,
  localization, and honesty of status claims.
- Address review comments with new commits; do not force-push over an active review unless asked.
- Security-sensitive changes may be reviewed privately before the pull request is made public.

## Documentation and tracking

- Update the relevant document when behavior changes: README, component README, specification, and
  tracking files under `docs/development/`.
- Never describe a planned feature as implemented. The README and status documents must match reality.
- Document new commands, configuration keys, and error codes where they are defined.
- User-facing changes need catalog entries and, for a new language, a translation-status header.

## Translation workflow

Read [TRANSLATING.md](TRANSLATING.md). English is canonical; translations never replace English and
must carry an honest review status. Machine translation may start a draft but must be marked as
unreviewed.

## Reporting issues

Use the GitHub issue forms when available. Include:

- DeployMC version (or commit), platform and version, Java version, and operating system;
- exact steps to reproduce, expected and actual behavior;
- relevant logs with tokens and hostnames removed;
- whether files, snapshots, deployment, console, auth, or the Web UI are affected.

Questions and general discussion belong in GitHub Discussions. Be patient: this is an unfunded
pre-alpha project.

## License

By contributing, you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), section 5 ("Submission of Contributions"). No separate contributor
license agreement is required.
