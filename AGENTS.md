# AGENTS.md - DeployMC

Read this file before doing anything. For current state, read
`docs/development/implementation-status.md`, `docs/development/project-status.md`, and
`docs/development/HANDOFF.md`; for intended design, read `docs/architecture/overview.md` and the
master specification.

## Mission

Build DeployMC: a secure, multi-version, multi-platform, multi-language deployment and collaboration
tool for Minecraft server plugin directories, with a first-class public API. The complete specification
is `docs/development/DeployMC_Complete_Master_Development_Specification.md`.

## Non-negotiable rules

1. Never commit directly to `main` after the bootstrap commit. Branch -> commits -> PR -> CI green -> merge.
2. Use Conventional Commits in English: `<type>(<scope>): <description>`. Allowed types and scopes are
   defined in Part 12.3 of the development specification and in `docs/development/commit-convention.md`.
3. If an AI tool contributed to a commit, add the accurate `Co-authored-by` trailer as the last line.
   For OpenCode, use: `Co-authored-by: opencode <221189863+opencode-agent@users.noreply.github.com>`.
   Never add trailers for anyone who did not contribute.
4. Never claim a test passed unless it was run. Report the exact command and result.
5. Never claim something is implemented, supported, or released unless verified. The README must match reality.
6. Priority order: security and data safety > correctness and honesty > recoverability > compatibility >
   operability > UX > feature breadth > code volume and schedule.
7. No unauthenticated endpoint except `/healthz` and `/readyz`. No bypass flags. Fail closed.
8. All file access goes through the sandbox resolver. Never construct files from raw request input.
9. No NMS or server internals. Feature-detect and report capabilities.
10. Java in `core`, `api`, `transport`, and `platform/*` must compile with `--release 8`.
11. User-facing strings go through i18n catalogs. No hard-coded UI or plugin message strings.
12. No secrets, real tokens, or real hostnames in the repository. Use obviously fake examples.
13. Verify time-sensitive facts (versions, Cloudflare behavior, GitHub criteria) against official
    documentation and record evidence with access dates.
14. Update `docs/development/implementation-status.md` when what works changes, and record
    open decisions in `docs/development/decisions-pending.md`.
15. Talk to the project owner in Japanese. Repository content is English except localized content.

## Repository state

Phase 0 (foundation) with Phase 1 WP-1.1-WP-1.9 and WP-2.1 merged. The Java, Web, CLI, and Python
skeletons exist and are verified, along with the localization tooling, core project documents, and
Japanese README. Build/test, lint, i18n, CodeQL, dependency review, Scorecard, and two compatibility
jobs run in CI, and branch protection with required checks is enforced on `main`. Architecture
documents, the ADR set, a threat-model draft, support-matrix design, implementation-status page,
REST/AsyncAPI/configuration specifications, and GitHub community forms exist. WP-1.8 added a pinned
Paper smoke harness; passing results at merge commit `839b789` on Ubuntu and Windows recorded plugin
enable, the localized banner, authenticated `/api/v1/server`, a conflicting Netty fixture, and clean
disable with Netty threads released. This is smoke evidence, not a support tier. WP-2.1 merged in
[PR #153](https://github.com/woxlo1/DeployMC/pull/153) at `748bbc8` with issue #50 closed. It adds a
canonical `SandboxResolver`, host-controlled policy, protected/deny globs, root revalidation,
post-open file-key checks, localized rejections, and a deterministic corpus/fuzz suite. PR checks
and post-merge CI, CodeQL, and Scorecard passed. No file API, transport handler, running server, or
in-game command uses the resolver yet, and exact-byte preservation for existing NFD filenames is not
implemented. The full integration suite has not run, and no in-game `/deploymc` command has been
exercised.

To check every language toolchain at once, run `node tools/build/verify-all.cjs` from the repository
root (see [`tools/build/README.md`](tools/build/README.md)). CI remains the merge authority. The
individual commands below are for investigating one toolchain.

Verified commands (2026-11-20 for Java, 2026-11-18 for the other toolchains; Windows 11, Gradle
8.10.2 wrapper, JDK 21 toolchain). The Java build packages `web/dist`, so build Web first or run
`node tools/build/verify-all.cjs`, which orders Web before Java:

```powershell
.\gradlew.bat build            # compiles all Java modules, runs 181 Java tests (1 skipped)
                              # and runs the Spotless check
.\gradlew.bat verifyJava8      # checks production class files target Java 8 bytecode
.\gradlew.bat spotlessApply    # formats Java sources and repository text files
```

Verified Web commands (2026-11-16, Node.js 24.13.1, pnpm 9.15.4), run from `web/`:

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm format:check
pnpm test                      # 14 Vitest tests
pnpm build                     # tsc --noEmit, Vite build, then gzip/Brotli precompression
```

Verified CLI commands (2026-11-16, Rust 1.100.0 GNU toolchain), run from `cli/`:

```powershell
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace         # 4 unit tests
cargo build --workspace
```

On this Windows host, set `CARGO_TARGET_DIR` to an ASCII path before running Cargo (see
`docs/development/environment.md`).

Verified Python SDK commands (2026-11-16, Python 3.12.10), run from `sdk/python/`:

```powershell
python -m venv .venv
.venv\Scripts\python -m pip install -e ".[dev]"
.venv\Scripts\python -m ruff check .
.venv\Scripts\python -m ruff format --check .
.venv\Scripts\python -m mypy
.venv\Scripts\python -m pytest   # 13 tests
```

Verified i18n commands (2026-11-16, Node.js 24.13.1), run from the repository root:

```powershell
node tools/i18n/check                        # 0 errors, 0 warnings
node --test tools/i18n/test/check.test.cjs   # 7 tests
node tools/i18n/check --write-status         # regenerates docs/i18n/STATUS.md
```

Verified compatibility-harness commands (2026-11-20), run from the repository root:

```powershell
python -m unittest discover -s tests/compat-matrix -p 'test_*.py'   # 14 tests
python tests/compat-matrix/harness.py render <result.json> [<result.json> ...]
```

The real Paper 26.3 smoke entry requires Java 25. CI runs it on Ubuntu and Windows; a local run with
`--java <path-to-Java-25>` reported `compat entry paper-26.3: pass`. See
[`tests/compat-matrix/README.md`](tests/compat-matrix/README.md).

On Windows, Gradle test workers cannot load test classes when the absolute repository path contains
non-ASCII characters. This machine's path contains `ドキュメント`; create an ASCII junction and run
Gradle through it (see `docs/development/environment.md`):

```powershell
cmd /c mklink /J C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii "<repository path>"
```

Do not document a command here before it has been executed successfully.

## Where things live

| Path | Purpose |
|---|---|
| `core/` | Platform-independent Java 8 core (sandbox, snapshot, deploy, auth, rbac, audit, api layer) |
| `api/` | Public Java API published to Maven Central |
| `transport/` | Embedded HTTP/WebSocket server |
| `platform/` | Platform adapters: bukkit, folia, velocity, bungee, standalone |
| `plugin/` | Shadow JAR aggregator and plugin descriptors |
| `web/` | React + Vite + TypeScript Web UI |
| `cli/` | Rust CLI |
| `sdk/` | TypeScript, Python, and Java clients |
| `actions/` | GitHub Action |
| `specification/` | Normative API, configuration, audit, and deployment specifications |
| `docs/` | Architecture, ADRs, research, guides, compatibility, development, i18n |
| `tests/` | Unit support, integration, compatibility matrix, E2E, security, fuzz, performance |
| `tools/` | Release, i18n, and matrix generation scripts |

## Definition of done

Implemented completely, tested (and the tests actually run), documented, localized where user-facing,
compatibility impact assessed, security considered, reviewed, CI green, and merged through a PR.
