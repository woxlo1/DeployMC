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
16. Split large work into several reviewable Conventional Commits instead of one large commit. Use
    rebase merge so those commits remain on `main`; do not squash them away.
17. Work may proceed in parallel in separate worktrees and branches, but merge implementation PRs in
    ascending issue order. A later issue may be prepared, reviewed, and kept ready, but do not merge
    it ahead of an earlier open issue unless the owner explicitly approves that order.
18. Keep an issue open until a final consolidated evidence PR records verification, CI, and post-merge
    results and updates status pages. Intermediate implementation PRs use `Refs #NNN` and must not edit
    `docs/development/implementation-status.md`, `docs/development/project-status.md`, or
    `docs/development/HANDOFF.md`; the final evidence PR owns those updates and closes the issue.
19. Fetch and rebase onto the latest `origin/main` before merging. If `main` advances while a PR is
    open, rebase again and rerun all local pre-push checks and CI before merge. Never merge a stale PR.
20. Dependabot PRs may use GitHub auto-merge only when branch protection and every required check are
    enforced. Never bypass checks or bulk-merge unchecked PRs. A failing dependency PR stays open for
    a later fix; record the failure and do not disable the gate to make it merge.
21. Before every push, run all four checks from the repository root: `git fetch origin main`, then
    `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD`,
    `node tools/build/verify-all.cjs`,
    `.\\gradlew.bat spotlessCheck --console=plain --no-configuration-cache`, and `git diff --check`.
    A local pass does not replace required CI checks; see
    [`docs/development/parallel-workflow.md`](docs/development/parallel-workflow.md).
22. Add a Japanese verification comment to every PR before merge and a Japanese post-merge evidence
    comment after required post-merge workflows finish. Use the templates in `tools/pr-evidence/`,
    record exact commands and results, and never claim an unrun check passed.

## Repository state

Phase 0 (foundation), Phase 1 WP-1.1-WP-1.9, and Phase 2 WP-2.1-WP-2.5 are merged. The Java, Web,
CLI, and Python skeletons exist and are verified, along with the localization
tooling, core project documents, and Japanese README. Build/test, lint, i18n, CodeQL, dependency
review, Scorecard, and two compatibility jobs run in CI, and branch protection with required checks
is enforced on `main`.
Architecture documents, the ADR set, a threat-model draft, support-matrix design,
implementation-status page, REST/AsyncAPI/configuration specifications, and GitHub community forms
exist. WP-1.8 added a pinned Paper smoke harness; passing results at merge commit `839b789` on Ubuntu
and Windows recorded plugin enable, the localized banner, authenticated `/api/v1/server`, a
conflicting Netty fixture, and clean disable with Netty threads released. This is smoke evidence, not
a support tier. WP-2.1 merged in [PR #153](https://github.com/woxlo1/DeployMC/pull/153) at `748bbc8`
with issue #50 closed. It adds a canonical `SandboxResolver`, host-controlled policy, protected/deny
globs, root revalidation, post-open file-key checks, localized rejections, and a deterministic
corpus/fuzz suite. WP-2.2 merged in [PR #155](https://github.com/woxlo1/DeployMC/pull/155) at
`017c2ab` with issue #51 closed. It adds `core.files`, which resolves every request path through the
resolver, returns sandbox-relative metadata, computes full-byte SHA-256 revisions, and commits writes
with a single atomic move behind a fail-closed snapshot boundary. WP-2.3 merged its normative
snapshot-store contract in [PR #158](https://github.com/woxlo1/DeployMC/pull/158) at `879bdc5` and
its Java 8 implementation in [PR #159](https://github.com/woxlo1/DeployMC/pull/159) at `0425c8`,
with issue #52 closed. `core.snapshot` adds immutable SHA-256 blobs, atomic JSON metadata/index
writes, restore-first reversibility, retention, verify/repair, conservative GC, and a single-instance
`data.lock`. WP-2.4 merged its normative contract in [PR #162](https://github.com/woxlo1/DeployMC/pull/162)
at `2a4e7f8` and its Java 8 implementation in [PR #163](https://github.com/woxlo1/DeployMC/pull/163)
at `80a3f0b` with issue #53 closed. `core.diff` adds bounded line/word diffs, hardened YAML/JSON
semantic diffs, metadata-only ZIP/JAR descriptor diffs, stable machine codes, and inclusive 2 MiB
textual limits. WP-2.5 merged its normative contract in [PR #165](https://github.com/woxlo1/DeployMC/pull/165)
at `6cd6015` and its Java 8 implementation in [PR #166](https://github.com/woxlo1/DeployMC/pull/166)
at `9363e0c` with issue #54 closed. `core.validation` adds bounded YAML, JSON, TOML, and `.properties`
validators, strict UTF-8 decoding, stable localized findings, deterministic registry matching, a
byte-preserving YAML scalar editor, and a host-wired fail-closed `core.files` save gate. PR checks and
post-merge CI, CodeQL, and Scorecard passed for WP-2.1, WP-2.2, both WP-2.3 stages, both WP-2.4 stages,
both WP-2.5 stages, and all merged WP-2.6 stages; implementation evidence is recorded in
`docs/development`. WP-2.6 merged the file/validation REST specification in
[PR #168](https://github.com/woxlo1/DeployMC/pull/168) at `8970728`, its authenticated adapters in
[PR #169](https://github.com/woxlo1/DeployMC/pull/169) at `08fe424`, the snapshot-route contract in
[PR #170](https://github.com/woxlo1/DeployMC/pull/170) at `bf82e26`, its merge evidence in
[PR #171](https://github.com/woxlo1/DeployMC/pull/171) at `88a6541`, and the authenticated snapshot
REST adapters in [PR #172](https://github.com/woxlo1/DeployMC/pull/172) at `136ce00` with issue #55
closed. The file/validation adapters consume the sandbox resolver, `core.files`, and `core.validation`
behind the bearer-token gate, with Problem Details, ETags, ranges, and cursor pagination. Six snapshot
REST routes in `specification/api/openapi.yaml` are implemented by `transport.SnapshotApiHandler` over
`core.snapshot` and `core.diff`, also behind the bearer-token gate.
No running server, UI, or in-game command consumes those REST adapters yet, and exact-byte
preservation for existing NFD filenames is not implemented. All REST adapters have server-free tests
only; the full integration suite has not run, and no in-game `/deploymc` command has been exercised.
PART 35 fixture evidence is complete through PR #197: stage 1 at `bdcd64a`, documentation evidence
in PR #175 at `a5ca873`, broken-enable in PR #176 at `7c2100e`, broken-descriptor in PR #177 at
`2508043`, slow-enable in PR #178 at `a03435`, dependency in PR #180 at `4bcf20f`, conflicting-library
alignment in PR #181 at `fd92cd0`, log-noise in PR #182 at `9b20b36`, catch-up evidence in PR #183 at
`9fac5f1`, wrong-platform in PR #184 at `4819195`, versioned per-OS traversal expectations in
PR #185 at `3b85de8`, catch-up evidence in PR #186 at `cfd47b6`, JWT vectors with a localhost mock
JWKS server in PR #187 at `ce4fdc7`, hostile archive and YAML generators in PR #189 at `49f300a`,
catch-up evidence in PR #190 at `49a9375`, log corpora in PR #191 at `2a253a1`, catch-up evidence in
PR #192 at `211e57f`, config corpora in PR #193 at `2b6d0f5`, catch-up evidence in PR #194 at
`706adff`, generated large trees in PR #195 at `3087abc`, catch-up evidence in PR #196 at
`289b9a4`, and cross-implementation filter vectors in PR #197 at `9b7b5ee`. All have passing
post-merge `main` workflows; issue #115 is complete after all fixture/data stages merged with
evidence. No server, UI, in-game command, harness, full integration suite, or E2E run consumes the
new fixtures yet, no product JWT verifier exists, no product archive or YAML validator consumed the
hostile-input corpus, no product console consumer reads the log corpora, no listing API consumes the
large trees, and no product filter parser consumes the vectors or establishes parity. Config corpora
are the exception: Java tests feed them through the real `ConfigValidator` and `ConfigManager`.
PART 33 algorithm regression suites merged in PR #199 at `0b83150`, PR #200 at `98b6b79`, and PR
#201 at `5d279c6`. They add 60 dedicated tests (24 file safety, 16 runtime limits, 20 auth/RBAC)
for sandbox resolution, atomic writes, YAML/archive limits, snapshot GC, log attribution, rate
limiting/backpressure, JWT vectors, RBAC, and deployment recovery. Implemented boundaries are
tested against real code; algorithms without a product implementation are validated only as
specification and fixture contracts, with gaps recorded as `NOT IMPLEMENTED` and no parity claims.
No product code, CI, or user-facing strings changed. No running server, full integration suite,
E2E run, Web UI, or in-game command consumed the suites.
PART 36 observability baseline merged in PR #203 at `072feda`, PR #204 at `2f3abfc`, PR #205 at
`7555c3d`, PR #206 at `d42f5d2`, and PR #207 at `b882f95`. It adds the normative doctor, logging,
metrics, and self-protection contract; a Web doctor panel; Java 8 `core.doctor`; transport
observability with a watchdog fix; and Bukkit host wiring. The doctor route is authenticated and
redacted, metrics are opt-in, and `/healthz` and `/readyz` remain the only unauthenticated endpoints.
PR #207 was rebase-merged so its five Conventional Commits remain on `main`, following the owner's
large-work commit-split rule. Issue #117 closed at `83b9107`. Only server-free tests and builds
verified these paths; no running server, Bukkit mock, browser, E2E run, full integration suite, or
in-game command exercised them.
PART 40.3 early risk-reduction spikes are recorded in PR #210 under `docs/research/spikes/`, with a
reproducible Windows replacement probe. The records distinguish observed output, source verification,
and `NOT RUN` work; no support tier is claimed. Issue #118 closes after PR #210 merges. D-001 still
blocks restart, update-folder, and oldest/newest-version evidence.

To check every language toolchain at once, run `node tools/build/verify-all.cjs` from the repository
root (see [`tools/build/README.md`](tools/build/README.md)). CI remains the merge authority. The
individual commands below are for investigating one toolchain.

Verified commands (2026-11-21 for Java, 2026-11-18 for the other toolchains; Windows 11, Gradle
8.10.2 wrapper, JDK 21 toolchain). The Java build packages `web/dist`, so build Web first or run
`node tools/build/verify-all.cjs`, which orders Web before Java:

```powershell
.\gradlew.bat build            # compiles all Java modules, runs 288 Java tests (1 skipped)
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
