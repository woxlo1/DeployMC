# Project Status

Last updated: 2026-11-23 (Phase 1 WP-1.4, both WP-1.5 stages, WP-1.6-WP-1.9, Phase 2 WP-2.1-WP-2.5,
all merged WP-2.6 specification and adapter stages, and the merged snapshot REST adapter
implementation). GitHub merge evidence below was verified against `main` through `b882f95` after
PR #207. All PART 35 fixtures are merged with local and post-merge evidence; issue #115 is complete.
All PART 33 algorithm regression suites are merged; issue #116 is complete. All PART 36 observability
baseline stages are merged with local and post-merge evidence; consolidated evidence is recorded here
and issue #117 closes after this merge.)

This is the session-level status page. For per-component facts use
[implementation status](implementation-status.md); for intended design use the
[master specification](DeployMC_Complete_Master_Development_Specification.md).

## Current version and milestone

- Build version: `0.1.0-SNAPSHOT`.
- Foundation milestone: `0.1.0` (Phase 0) is complete. WP-0.7 was verified as already delivered
  by PR #18, and WP-0.10 was completed by PR #132; both issues were closed on 2026-11-18.
- Active milestone: `0.2.0` (Phase 1). WP-1.3 (issue #43), the HTTP/WebSocket transport decision
  and implementation, was merged in PR #135 at `dbb32ae`; the issue closed automatically. WP-1.1
  was merged in PR #133 with issue #41 closed; WP-1.2 was merged in PR #134 with issue #42 closed.
  The post-WP-1.3 documentation PR #136 merged at `8d00fa9`. WP-1.4 (issue #44), static asset
  serving from the JAR, was merged in PR #137 at `4548b4b`; the issue closed automatically. The
  post-WP-1.4 documentation PR #138 merged at `5dd95b9`. Both WP-1.5 stages (issue #45) merged: PR
  #139 at `5fc04a1` added the configuration loader and PR #141 at `f853136` added Bukkit startup
  wiring and `/deploymc`. Evidence PR #142 merged at `682e32d`; issue #45 was closed after its
  required checks and post-merge workflows passed. WP-1.6 (issue #46), the plugin message i18n
  runtime, merged in PR #144 at `d38217f`. Evidence PR #145 merged at `e812be6`; issue #46 was closed
  after its required checks and post-merge workflows passed. WP-1.7 (issue #47), with SnakeYAML 2.7,
  mandatory relocations, the final-JAR verifier, and CI size reporting, merged in PR #147 at
  `db3e33a`; the issue closed automatically. Evidence PR #148 merged at `d4ce4c6` with all required
  checks and post-merge workflows passing. WP-1.9 (issue #49), host-provisioned SHA-256 token records
  and the fail-closed HTTP/WebSocket gate, merged in PR #149 at `3244680`; the issue closed
  automatically. WP-1.8 (issue #48), the pinned Paper compatibility smoke harness with a
  conflicting Netty fixture and before/after-disable thread dumps, merged in PR #151 at `839b789`;
  the issue closed as completed. WP-2.1 (issue #50), the canonical sandbox resolver, host-controlled
  policy, localized rejections, and security corpus, merged in PR #153 at `748bbc8`; the issue closed
  as completed. WP-2.2 (issue #51), the sandboxed file operations service, merged in PR #155 at
  `017c2ab`; the issue closed as completed. WP-2.3 first stage, the normative snapshot store
  contract, merged in PR #158 at `879bdc5`; the Java 8 `core.snapshot` implementation merged in PR
  #159 at `0425c8`, and issue #52 closed as completed. WP-2.4 first stage merged in PR #162 at
  `2a4e7f8`; the Java 8 `core.diff` implementation merged in PR #163 at `80a3f0b`, and issue #53
  closed as completed. WP-2.5 first stage merged in PR #165 at `6cd6015`; the Java 8 `core.validation`
  implementation merged in PR #166 at `9363e0c`, and issue #54 closed as completed. WP-2.6 first stage
  merged in PR #168 at `8970728`; the file/validation adapters merged in PR #169 at `08fe424`; the
  snapshot-route specification merged in PR #170 at `bf82e26`; its merge evidence merged in PR #171 at
  `88a6541`; and the authenticated snapshot REST adapters merged in PR #172 at `136ce00` with issue
  #55 closed as completed. PART 36 (issue #117) merged in five stages: the observability contract in
  PR #203 at `072feda`, the Web doctor panel in PR #204 at `2f3abfc`, the `core.doctor` service in
  PR #205 at `7555c3d`, the transport baseline in PR #206 at `d42f5d2`, and the Bukkit host wiring
  in PR #207 at `b882f95`. PR #207 was rebase-merged so all five Conventional Commits remain
  individually on `main`, and the issue stayed open through every implementation stage.
- Later milestones: `0.3.0` through `0.10.0` exist with the remaining specification work packages
  and category 3-5 work registered as issues #42-#130. WP-2.4 (issue #53, milestone `0.3.0`) and
  WP-2.5 (issue #54) are complete through their engines and save-time gate. WP-2.6 (#55) has merged
  file/validation adapters and all six snapshot routes; issue #55 is closed as completed, and
  category 3-5 work remains open. Issue #115 (PART 35 fixtures) is in progress: stage 1 merged the
  coverage map and a source-built versioned healthy plugin in PR #174 at `bdcd64a`; PRs #175-#178
  added documentation evidence, broken-enable, broken-descriptor, and slow-enable fixtures through
  `a03435`. PR #179 recorded catch-up evidence, PR #180 added the dependency fixture, PR #181
  aligned the conflicting-library fixture identity, and PR #182 added the log-noise fixture through
  `9b20b36`. PR #183 recorded catch-up evidence at `9fac5f1`, PR #184 added the source-built
  wrong-platform fixture at `4819195`, PR #185 added the versioned traversal-corpus manifest at
  `3b85de8`, PR #186 recorded catch-up evidence at `cfd47b6`, PR #187 added deterministic JWT vectors
  with a localhost mock JWKS server at `ce4fdc7`, PR #189 added hostile archive and YAML generators
  at `49f300a`, PR #190 recorded catch-up evidence at `49a9375`, PR #191 added log corpora at
  `2a253a1`, PR #192 recorded catch-up evidence at `211e57f`, PR #193 added config corpora at
  `2b6d0f5`, PR #194 recorded catch-up evidence at `706adff`, PR #195 added generated large trees at
  `3087abc`, PR #196 recorded catch-up evidence at `289b9a4`, and PR #197 added cross-implementation
  filter vectors at `9b7b5ee`. All PART 35 fixture/data stages are merged with evidence.

Verified GitHub state on 2026-11-20 after PR #151 was merged and issue #48 was closed:

```text
git log -1 --oneline origin/main
839b789 feat(compat): add Paper compatibility smoke harness (#151)

gh pr view 151 --json state,mergeCommit,url
state=MERGED mergeCommit=839b789ae8483f83dcb4b430641b7f0eafccb303
url=https://github.com/woxlo1/DeployMC/pull/151

gh issue view 48 --json state,stateReason,closedAt
state=CLOSED stateReason=COMPLETED closedAt=2026-11-20T08:42:28Z

gh pr list --state open --limit 1000
open PRs: 17   (#23-#39 Dependabot updates)

gh issue list --state open --limit 1000
open issues: 81
```

Verified GitHub state on 2026-11-20 after PR #153 was merged and the feature branch was deleted:

```text
git log -1 --oneline origin/main
748bbc8 feat(sandbox): implement sandbox path resolver (#153)

gh pr view 153 --json number,state,mergedAt,mergeCommit,url
number=153 state=MERGED mergedAt=2026-11-20T10:06:26Z
mergeCommit=748bbc8adda99e1aae9eeaca600863b44ff2f2ab
url=https://github.com/woxlo1/DeployMC/pull/153

gh issue view 50 --json number,title,state,stateReason,closedAt
number=50 title="WP-2.1: Sandbox path resolver" state=CLOSED
stateReason=COMPLETED closedAt=2026-11-20T10:06:27Z

gh pr list --state open --limit 1000
open PRs: 17 (#23-#39 Dependabot updates)

gh issue list --state open --limit 1000
open issues: 80
```

Verified GitHub state on 2026-11-20 after PR #154 merged and both WP-2.1 branches were deleted:

```text
git log -1 --oneline origin/main
7762a8f docs(docs): record WP-2.1 merge evidence (#154)

gh pr view 154 --json number,state,mergeCommit,url
number=154 state=MERGED mergeCommit=7762a8f608430397e1d9ca11c0bb169c4d480d03
url=https://github.com/woxlo1/DeployMC/pull/154

gh pr list --state open --limit 1000
open PRs: 17 (#23-#39 Dependabot updates)

gh issue list --state open --limit 1000
open issues: 80 (#51-#130)
```

Verified GitHub state on 2026-11-21 after PR #155 was merged and issue #51 was closed:

```text
git log -1 --oneline origin/main
017c2ab feat(files): add sandboxed file operations service (#155)

gh pr view 155 --json number,state,mergedAt,mergeCommit,url
number=155 state=MERGED mergedAt=2026-11-20T10:59:32Z
mergeCommit=017c2abd1549e6c3aa8f947f6324df9da97ccb59
url=https://github.com/woxlo1/DeployMC/pull/155

gh issue view 51 --json number,state,stateReason,closedAt
number=51 state=CLOSED stateReason=COMPLETED closedAt=2026-11-20T10:59:33Z

gh pr list --state open --limit 1000
open PRs: 17 (#23-#39 Dependabot updates)

gh issue list --state open --limit 1000
open issues: 79 (#52-#130)
```

Verified GitHub state on 2026-11-21 after PR #159 was merged and issue #52 was closed:

```text
git log -1 --oneline origin/main
0425c81 feat(snapshot): implement content-addressed snapshot store (#159)

gh pr view 159 --json number,state,mergedAt,mergeCommit,url
number=159 state=MERGED mergedAt=2026-11-21T02:11:52Z
mergeCommit=0425c814018091543929444042dcd6d885bd75d5
url=https://github.com/woxlo1/DeployMC/pull/159

gh issue view 52 --json number,state,stateReason,closedAt
number=52 state=CLOSED stateReason=COMPLETED closedAt=2026-11-21T02:11:54Z

gh pr list --state open --limit 1000
open PRs: 17 (#23-#39 Dependabot updates)

gh issue list --state open --limit 1000
open issues: 78 (#53-#130)
```

`git branch --list 'feature/wp-2.1-sandbox-resolver'` and the matching
`git ls-remote --heads origin` query returned no branches after cleanup. The earlier PR #149 state
is preserved in the repository history. Required branch-protection checks now also include
`Compatibility (ubuntu-latest)` and `Compatibility (windows-latest)`.

All 18 pull-request checks passed before merge (16 required). The first Windows Java check failed
only on the immediate Netty thread-termination assertion; `18eb9f7` bounded that wait, and the rerun
passed. Post-merge `main` runs passed: CI
[44693881031](https://github.com/woxlo1/DeployMC/actions/runs/44693881031), CodeQL
[44693880985](https://github.com/woxlo1/DeployMC/actions/runs/44693880985), and Scorecard
[44693881125](https://github.com/woxlo1/DeployMC/actions/runs/44693881125). Earlier WP-1.7 merge
evidence remains in the repository history.

## WP-2.1 verification (local, pre-merge)

These commands were run on 2026-11-20 on `feature/wp-2.1-sandbox-resolver` before PR #153 was merged:

```text
python tests/security/sandbox/generate_corpus.py
reject=2736 allow=29

$env:DEPLOYMC_JAVA_ROOT = "C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii"
node tools/build/verify-all.cjs
PASS web
PASS java
PASS cli
PASS python
PASS spec
PASS i18n
PASS compat

.\gradlew.bat spotlessApply :core:test --tests '*Sandbox*' --console=plain --no-configuration-cache
BUILD SUCCESSFUL
16 tests: 15 passed, 1 skipped
```

The sandbox-only suite contains 3 corpus tests, 1 seeded fuzz test, and 12 resolver behavior tests.
The skipped test requires symlink creation, which this Windows host does not allow without elevation.
The full Gradle build in `verify-all.cjs` ran 181 Java tests with 0 failures, 0 errors, and 1 skipped
test. `DEPLOYMC_JAVA_ROOT` selected the existing ASCII junction so Gradle and Spotless saw one project
path; otherwise this host can create a second junction and fail Spotless with a path mismatch. The
PR check results and post-merge evidence are recorded in the next section.

### WP-2.1 PR and post-merge evidence

At PR head `9d52a85`, `gh pr checks 153 --watch --interval 30` ended with all checks passing. The
first Windows Java run had failed three assertions that used the configured root instead of the
canonical root; the tests were changed to use `resolver.root()`, and the rewritten history had the
same tree as the failed head (`git diff --stat d582408 HEAD` was empty). `Commit messages` had also
rejected the original docs commit for `footer must have leading blank line`; that message was
shortened. The explicit PR comment is at
<https://github.com/woxlo1/DeployMC/pull/153#issuecomment-7036766443>.

At merge commit `748bbc8`, all post-merge `main` workflows completed successfully:

```text
CI       44716375506  success
CodeQL   44716375542  success
Scorecard 44716375486 success
```

These are GitHub Actions run IDs, linked from [implementation status](implementation-status.md).
The scheduled fuzz workflow has not run on a schedule yet, and at that point no file API or server
process used `SandboxPath`; WP-2.2 later added `core.files` on top of the resolver.

## WP-2.2 verification (local and post-merge)

These commands were run on 2026-11-20 on `feature/wp-2.2-file-operations`:

```text
.\gradlew.bat spotlessApply :core:test --tests '*File*' --console=plain --no-configuration-cache
BUILD SUCCESSFUL
26 tests: 24 new WP-2.2 tests and 2 existing matching tests, all passed

.\gradlew.bat build verifyJava8 --console=plain --no-configuration-cache
BUILD SUCCESSFUL
205 Java tests: 0 failures, 0 errors, 1 skipped symlink test

node tools/i18n/check
i18n check passed: 0 error(s), 0 warning(s)

git diff --check
(no output)

$env:DEPLOYMC_JAVA_ROOT = "C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii"
node tools/build/verify-all.cjs
PASS web
PASS java
PASS cli
PASS python
PASS spec
PASS i18n
PASS compat
```

The 24 new tests cover strong revisions, full and ranged reads, binary and CRLF preservation,
creation and directory conflicts, precondition and stale-revision rejection, streaming uploads and
limits, protected and rejected paths, confirmed recursive deletion, deletion caps, snapshot failure,
destructive-operation fail-closed behavior, move conflicts, concurrency, and deterministic crash
points before and after the atomic commit. The skipped test remains the WP-2.1 symlink test that
needs privileges unavailable on this host. At that stage no REST endpoint or transport handler
consumed `FileOperationsService`; WP-2.6 later added the file/validation REST adapter, still without
a running server or UI.

### WP-2.2 PR and post-merge evidence

At PR head `595fde0`, all required checks passed after the Linux concurrency test was updated to
accept either documented conflict reason. PR comments recorded the implementation, the CI
investigation, and the post-merge evidence:
<https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7037691314>,
<https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7037772338>, and
<https://github.com/woxlo1/DeployMC/pull/155#issuecomment-7054409687>.

At merge commit `017c2ab`, all post-merge `main` workflows completed successfully:

```text
CI        44723836623  success
CodeQL    44723836580  success
Scorecard 44723836649  success
```

The local and remote `feature/wp-2.2-file-operations` branches were deleted after the merge, and
local `main` was left clean at `017c2ab`.

## WP-2.3 verification (local, pre-merge)

These commands were run on 2026-11-21 on `feature/wp-2.3-snapshot-store` before opening the
implementation PR:

```text
$env:DEPLOYMC_JAVA_ROOT = "C:\Users\satos\AppData\Local\Temp\deploymc-ascii"
.\gradlew.bat spotlessApply --console=plain --no-configuration-cache
BUILD SUCCESSFUL

node tools/build/verify-all.cjs
PASS web
PASS java
PASS cli
PASS python
PASS spec
PASS i18n
PASS compat

Java JUnit XML totals: 230 tests, 0 failures, 0 errors, 1 skipped
SnapshotStoreTest: 25 tests, 0 failures, 0 errors, 0 skipped
git diff --check
(no output)
```

The Java all-toolchain step ran `build verifyJava8 --console=plain --no-configuration-cache`, and the
single skipped test remains the WP-2.1 symlink test that needs privileges unavailable on this host.
The 25 snapshot tests cover format handling, canonical blob deduplication, index parent links,
revision and root validation, rejected paths, foreign-root requests, restore reversibility for files
and deletion markers, stale-target rejection, retention, GC guards and grace periods, verify/repair,
staging cleanup, concurrent capture, file-service integration, localized diagnostics, and the
single-instance data lock. A stale Spotless incremental cache from a different ASCII junction had to
be removed before formatting; no source file was changed by that cleanup. No REST endpoint, transport
handler, running server, UI, or in-game command consumes `SnapshotStore`.

### WP-2.3 PR and post-merge evidence

All PR checks passed at head `f0fd908`. Explicit verification comments were posted at
<https://github.com/woxlo1/DeployMC/pull/159#issuecomment-7055673480> and
<https://github.com/woxlo1/DeployMC/pull/159#issuecomment-7055770391>. Squash merge commit
`0425c814018091543929444042dcd6d885bd75d5` closed issue #52 as completed at
2026-11-21T02:11:54Z. At merge commit `0425c814`, all post-merge `main` workflows completed
successfully:

```text
CI        44854531337  success
CodeQL    44854531422  success
Scorecard 44854531350  success
```

The local and remote `feature/wp-2.3-snapshot-store` branches were deleted, and local `main` was left
clean at `0425c814`.

## WP-2.4 verification (local, pre-merge)

These commands were run on 2026-11-21 on `feature/wp-2.4-diff-engine` before opening the implementation
PR:

```text
$env:DEPLOYMC_JAVA_ROOT = "C:\Users\satos\AppData\Local\Temp\deploymc-ascii"
.\gradlew.bat :core:test --tests '*Diff*' --console=plain --no-configuration-cache
DiffEngineTest: 31 tests, 0 failures, 0 errors, 0 skipped

.\gradlew.bat spotlessApply --console=plain --no-configuration-cache
BUILD SUCCESSFUL

node tools/build/verify-all.cjs
PASS web
PASS java
PASS cli
PASS python
PASS spec
PASS i18n
PASS compat

Java JUnit XML totals: 261 tests, 0 failures, 0 errors, 1 skipped
Plugin JAR verification: 4492483 bytes, 2619 classes (0 versioned), max common bytecode 52
git diff --check
(no output)
```

The Java all-toolchain step ran `build verifyJava8 --console=plain --no-configuration-cache`, and the
single skipped test remains the WP-2.1 symlink test that needs privileges unavailable on this host.
The 31 focused tests cover the full normative evidence list: deterministic identical and empty
results, both sides of the 2 MiB boundary, preserved line endings, moved-block stability, YAML rename
and reorder, function-style pointer escaping, text fallback, word segments, JAR descriptor changes,
invalid and ZIP64 archives, and repeated-run JSON stability. The engine is not consumed by any REST
handler, transport route, running server, UI, or in-game command.

### WP-2.4 PR and post-merge evidence

All PR checks passed at head `1a01f54`. Explicit verification comments were posted at
<https://github.com/woxlo1/DeployMC/pull/163#issuecomment-7060316351> and
<https://github.com/woxlo1/DeployMC/pull/163#issuecomment-7060391484>. Squash merge commit
`80a3f0b7eb50718d85ccca3d4d5a4f71b1a97633` closed issue #53 as completed at
2026-11-21T09:06:23Z. At merge commit `80a3f0b`, all post-merge `main` workflows completed
successfully:

```text
CI        44887980566  success
CodeQL    44887980567  success
Scorecard 44887980590  success
```

The CI run includes the pinned Paper 26.3 smoke entry on Ubuntu and Windows. Verified GitHub state
after the merge: 77 open issues (#54-#130) and 17 open Dependabot PRs (#23-#39), tracked for triage by
#121. The local and remote `feature/wp-2.4-diff-engine` branches were deleted, and local `main` was
left clean at `80a3f0b`.

## WP-2.5 specification verification (local, pre-merge)

These commands were run on 2026-11-21 on `spec/wp-2.5-validation` before opening the specification PR:

```text
git diff --check
(no output)

cd tools/spec
npm run lint
npm test
OpenAPI, AsyncAPI, configuration example, and state-machine lint passed
14 tests, 0 failures, 0 cancelled, 0 skipped, 0 todo
```

The specification PR adds the normative validator contract, ADR-0016, validation error codes, and
status updates only. It adds no Java code and claims no validator implementation. It merged in PR
#165 at `6cd6015a141d8a108b7507a9f801b646679698cc`; all post-merge `main` workflows succeeded (CI
44890406029, CodeQL 44890406052, Scorecard 44890406037). The post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/165#issuecomment-7060731358>.

## WP-2.5 implementation verification (local, pre-merge)

These commands were run on 2026-11-21 on `feature/wp-2.5-validation`. Gradle commands ran through the
existing ASCII junction at `C:\Users\satos\AppData\Local\Temp\deploymc-ascii`:

```text
git diff --check
(no output, with a CRLF-to-LF normalization notice for one Japanese properties file)

.\gradlew.bat spotlessApply --console=plain --no-configuration-cache
BUILD SUCCESSFUL

.\gradlew.bat :core:test --tests '*ValidationCoreTest' --tests '*FileOperationsValidationTest'
--tests '*FormatValidatorsTest' --console=plain --no-configuration-cache
BUILD SUCCESSFUL; 27 tests, 0 failures, 0 errors

.\gradlew.bat build --console=plain --no-configuration-cache
BUILD SUCCESSFUL; JUnit XML totals: 288 tests, 0 failures, 0 errors, 1 skipped symlink test
Plugin JAR verification: 4567069 bytes, 2657 classes (0 versioned), max common bytecode 52

.\gradlew.bat verifyJava8 --console=plain --no-configuration-cache
BUILD SUCCESSFUL

node tools/i18n/check
i18n check passed: 0 error(s), 0 warning(s)

cd tools/spec && npm run lint
OpenAPI, AsyncAPI, configuration example, and state-machine lint passed
```

The 27 tests cover strict UTF-8 and NUL rejection, BOM offsets, inclusive input limits, deterministic
finding order and truncation, explicit unmatched results, path validation, registry precedence,
fail-closed validator failures, localized English/Japanese details, YAML duplicate keys, tabs, custom
tags, multiple documents, syntax positions, empty input and the code-point boundary, JSON syntax,
duplicate keys, depth, invalid UTF-8 and valid nested documents, the TOML profile with unsupported,
duplicate, and malformed cases, properties escapes, continuations, duplicate warnings and invalid
escapes, YAML alias/cycle and node boundaries, JSON scalar and array error pointers, properties path
validation, byte-preserving YAML scalar edits, write-time gate ordering for create/write/upload/move/
delete, immutable byte-array snapshots, bounded pre-validation reads, warning exposure, and repeated-run
determinism. The one-command `node tools/build/verify-all.cjs` then reported `PASS` for `web`, `java`,
`cli`, `python`, `spec`, `i18n`, and `compat`. D-008 was resolved by the owner on 2026-11-21 in
favour of one `validation.limit-exceeded` shape for every structural budget; the JSON depth path
still needs the WP-2.6 change.

## WP-2.5 implementation merge evidence

PR #166 merged to `main` at `9363e0c70c4666558959560fe8ca5fe58f80e920` and closed issue #54 as
completed. All 17 required pull-request checks passed before merge. Post-merge workflows on `main`
succeeded: CI
[44893521743](https://github.com/woxlo1/DeployMC/actions/runs/44893521743), CodeQL
[44893521769](https://github.com/woxlo1/DeployMC/actions/runs/44893521769), and Scorecard
[44893521898](https://github.com/woxlo1/DeployMC/actions/runs/44893521898). The explicit verification
comment is at <https://github.com/woxlo1/DeployMC/pull/166#issuecomment-7061084189>, and the
post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/166#issuecomment-7061152266>. The local and remote
`feature/wp-2.5-validation` branches were deleted; local `main` was left at `9363e0c`.

## WP-2.6 specification merge evidence

PR #168 merged the file/validation REST contract to `main` at
`8970728b7872ebe844c510144533485ba4b197ce`. The explicit verification comment is at
<https://github.com/woxlo1/DeployMC/pull/168#issuecomment-7061469819> and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/168#issuecomment-7061542061>. Post-merge
workflows passed: CI [44896183648](https://github.com/woxlo1/DeployMC/actions/runs/44896183648),
CodeQL [44896183641](https://github.com/woxlo1/DeployMC/actions/runs/44896183641), and Scorecard
[44896183615](https://github.com/woxlo1/DeployMC/actions/runs/44896183615). The local and remote
`spec/wp-2.6-file-endpoints` branches were deleted after the evidence was posted.

## WP-2.6 file/validation adapter verification (local, pre-merge)

These commands were run on 2026-11-21 on `feature/wp-2.6-file-validation-endpoints`. Gradle commands
ran through the ASCII junction at `C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii`; the stale
`.gradle/configuration-cache` directory was removed before Spotless ran again.

```text
.\gradlew.bat spotlessApply --no-configuration-cache --console=plain
BUILD SUCCESSFUL

.\gradlew.bat :transport:test --tests '*ProblemDetailsTest' --tests '*FileApiHandlerTest'
--no-configuration-cache --console=plain
BUILD SUCCESSFUL; 14 tests, 0 failures, 0 errors

.\gradlew.bat :platform:bukkit:test --tests '*FileApiRuntimeTest'
--tests '*DeployMCHttpHandlerTest' --no-configuration-cache --console=plain
BUILD SUCCESSFUL; 8 tests, 0 failures, 0 errors

.\gradlew.bat build verifyJava8 --no-configuration-cache --console=plain
BUILD SUCCESSFUL; JUnit XML totals: 311 tests, 0 failures, 0 errors, 1 skipped symlink test
Plugin JAR verification: 4604278 bytes, 2665 classes (0 versioned), max common bytecode 52

node tools/i18n/check
i18n check passed: 0 error(s), 0 warning(s)

git diff --check
(no output)
```

The transport tests cover Problem serialization, create/list/read/range/write/move/delete/upload/
validate routes, ETag/`If-Match`, cursor tampering, recursive-delete confirmation, D-008 limit
mapping, localized findings, sandbox root rejection, traversal, and unsupported media types. The
Bukkit tests cover service construction from host configuration, snapshot-store close/reopen,
schema-maximum budgets, fail-closed snapshot-directory and sandbox-root settings, and exact file-route
delegation. A regression test caught and fixed a runtime bug that rejected schema-valid 2048 MiB
snapshot and 4096 MiB upload budgets. No running-server smoke test, E2E run, in-game `/deploymc`
execution, or Web UI browser check was performed after this wiring, and the full integration suite
has not run. The single provisioned token remains admin-equivalent until Phase 3 RBAC.

## WP-2.6 adapter merge evidence

PR #169 merged the adapters to `main` at
`08fe4245af2d2de474d644e71ca79339cc57dfd3`. The explicit verification comment is at
<https://github.com/woxlo1/DeployMC/pull/169#issuecomment-7062245147> and the post-merge evidence
comment is at <https://github.com/woxlo1/DeployMC/pull/169#issuecomment-7062327345>. Post-merge
workflows passed: CI [44901920780](https://github.com/woxlo1/DeployMC/actions/runs/44901920780),
CodeQL [44901920707](https://github.com/woxlo1/DeployMC/actions/runs/44901920707), and Scorecard
[44901920664](https://github.com/woxlo1/DeployMC/actions/runs/44901920664). The local and remote
`feature/wp-2.6-file-validation-endpoints` branches were deleted after the evidence was posted.

## WP-2.6 snapshot specification verification (local, pre-merge)

On 2026-11-21, the `spec/wp-2.6-snapshot-endpoints` branch added six `/api/v1/snapshots` operations
to `specification/api/openapi.yaml`: list, metadata, content, diff, restore, and prune. The contract
pins opaque `snap_` ids, quoted ETags, start-inclusive/end-exclusive ranges, host-derived restore
actors, protected-record prune invariants, path-scope checks for RBAC, and snapshot/diff error
mapping. It deliberately records that no adapter exists yet.

```text
npm run lint
OpenAPI, AsyncAPI, configuration example, and state-machine lint passed

npm test
14 tests, 0 failures
```

The explicit verification comment is at
<https://github.com/woxlo1/DeployMC/pull/170#issuecomment-7062418966>. PR #170 merged the contract to
`main` at `bf82e265742a36914423ff2f3d62612a4326945d`. The post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/170#issuecomment-7062501576>. Post-merge workflows passed:
CI [44903156530](https://github.com/woxlo1/DeployMC/actions/runs/44903156530), CodeQL
[44903156565](https://github.com/woxlo1/DeployMC/actions/runs/44903156565), and Scorecard
[44903156579](https://github.com/woxlo1/DeployMC/actions/runs/44903156579). The local and remote
`spec/wp-2.6-snapshot-endpoints` branches were deleted after the evidence was posted.

The contract stage claimed no adapter, contract test, running server, or UI check. The following
stage implements the adapter.

## WP-2.6 snapshot adapter verification (local, pre-merge)

These commands were run on 2026-11-22 on `feature/wp-2.6-snapshot-adapters`. Gradle commands ran
through the ASCII junction at `C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii`;
`DEPLOYMC_JAVA_ROOT` was set to the same path for the one-command verification.

```text
.\gradlew.bat spotlessApply :core:test :transport:test :platform:bukkit:test
--no-configuration-cache --console=plain
BUILD SUCCESSFUL

SnapshotApiHandlerTest: 12 tests, 0 failures, 0 errors, 0 skipped
SnapshotStoreTest: 28 tests, 0 failures, 0 errors, 0 skipped (3 new cases: verified content,
absent-revision restore conflict, and minimum-age manual-prune protection)
AuthenticationGateTest: 5 tests, 0 failures, 0 errors, 0 skipped (1 new identity case)
DeployMCHttpHandlerTest: 5 tests, 0 failures, 0 errors, 0 skipped (1 new snapshot routing case)
FileApiRuntimeTest: 4 tests, 0 failures, 0 errors, 0 skipped
FileApiHandlerTest: 12 tests, 0 failures, 0 errors, 0 skipped (1 new limit-clamp assertion)

.\gradlew.bat build verifyJava8 --no-configuration-cache --console=plain
BUILD SUCCESSFUL; JUnit XML totals: 328 tests, 0 failures, 0 errors, 1 skipped symlink test

node tools/i18n/check
i18n check passed: 0 error(s), 0 warning(s)

git diff --check
(no output)

$env:DEPLOYMC_JAVA_ROOT = "C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii"
node tools/build/verify-all.cjs
PASS web
PASS java
PASS cli
PASS python
PASS spec
PASS i18n
PASS compat
```

Review-driven corrections were covered by the same run: absence is pinned under the store lock
during restore, records younger than `minAgeDays` are protected from path/id prune as well as
retention, `limit > 500` is clamped rather than rejected, `Accept-Language` q-values are honoured,
diff inputs are bounded before blobs are read, and internal failures are retryable.

The adapter keeps all six routes behind the bearer-token gate. The identity comes from the
authenticated Netty channel rather than a client header. Snapshot ids stay opaque, ETags are quoted,
ranges are start-inclusive and end-exclusive, restore uses the record's own path and the
authenticated actor, prune accepts one target and never starts GC, and error details never expose
host paths, tokens, or file content. No running-server smoke test, full integration suite, E2E run,
in-game `/deploymc` execution, or Web UI browser check was performed. The single provisioned token
remains admin-equivalent until Phase 3 RBAC.

## WP-2.6 snapshot adapter merge evidence

The explicit verification comment for head commit `8763082` is at
<https://github.com/woxlo1/DeployMC/pull/172#issuecomment-7072665699>. PR #172 squash-merged to
`main` at `136ce00ffc67b472872d8966306e29e1e5f36b39` on `2026-11-22T02:08:39Z`, and issue #55 closed
as completed at `2026-11-22T02:08:41Z`. The post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/172#issuecomment-7072756119>. Post-merge `main` workflows
passed: CI [44974693821](https://github.com/woxlo1/DeployMC/actions/runs/44974693821), CodeQL
[44974693864](https://github.com/woxlo1/DeployMC/actions/runs/44974693864), and Scorecard
[44974693855](https://github.com/woxlo1/DeployMC/actions/runs/44974693855). The local and remote
`feature/wp-2.6-snapshot-adapters` branches were deleted after the evidence was posted.

## PART 35 fixture stage 1 verification (local, pre-merge)

The coverage map is in `tests/fixtures/README.md`. The targeted Gradle clean build
`:tests:fixtures:plugins:deploymc-test-a:clean :tests:fixtures:plugins:deploymc-test-a:build` ran
through the ASCII junction with `--no-configuration-cache` and produced `deploymc-test-a-1.0.0.jar`
and `deploymc-test-a-1.1.0.jar`. ZIP inspection showed matching `plugin.yml` versions and
`fixture-message` config values, and class-byte inspection showed major 52 for both `main` and
`version110` outputs. After CI reported configuration-cache serialization failures, the descriptor
tasks were changed to use task-local values and `.\gradlew.bat build verifyJava8 --console=plain` was
rerun through the same junction with configuration cache enabled: `BUILD SUCCESSFUL` and the cache
entry was stored. No running-server, full-integration, E2E, Web UI, or in-game command execution was
performed. CI remains the merge authority.

## PART 35 fixture stage 1 merge evidence

The explicit verification comment for head commit `5f2a8be` is at
<https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073079151>, with the CI failure and fix
follow-up at <https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073148995>. PR #174
squash-merged to `main` at `bdcd64a6a329ce9c3c37a541ee850137d44b30d9` on `2026-11-22T02:56:30Z`. The
post-merge evidence comment is at
<https://github.com/woxlo1/DeployMC/pull/174#issuecomment-7073263008>. Post-merge `main` workflows
passed: CI [44978349816](https://github.com/woxlo1/DeployMC/actions/runs/44978349816), CodeQL
[44978349824](https://github.com/woxlo1/DeployMC/actions/runs/44978349824), and Scorecard
[44978349870](https://github.com/woxlo1/DeployMC/actions/runs/44978349870). The local and remote
`feature/issue-115-plugin-fixtures` branches were deleted after the evidence was posted. Issue #115
remains open for the remaining PART 35 stages.

## PART 35 fixtures #175-#178 merge evidence

The follow-up PRs were merged after all required checks passed. Each local build used the ASCII
junction and `node tools/build/verify-all.cjs` reported PASS for web, java, cli, python, spec, i18n,
and compat. No running-server, harness, timeout, validator, full-integration, E2E, Web UI, or
in-game command execution was performed for these fixtures.

- PR #175 recorded the stage-1 evidence at `a5ca87345b213838f0aa7f39135390f1d4ac4d81` on
  `2026-11-22T03:12:22Z`; [verification](https://github.com/woxlo1/DeployMC/pull/175#issuecomment-7073361203)
  and [evidence](https://github.com/woxlo1/DeployMC/pull/175#issuecomment-7073504322). Post-merge
  workflows passed: CI [44979557264](https://github.com/woxlo1/DeployMC/actions/runs/44979557264),
  CodeQL [44979557228](https://github.com/woxlo1/DeployMC/actions/runs/44979557228), Scorecard
  [44979557219](https://github.com/woxlo1/DeployMC/actions/runs/44979557219).
- PR #176 added the source-built `DeployMCTestBrokenEnable` fixture at
  `7c2100e5663c68becc31110d1f4c645939241e60` on `2026-11-22T03:33:06Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/176#issuecomment-7073565824) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/176#issuecomment-7073646436). Post-merge
  workflows passed: CI [44981158073](https://github.com/woxlo1/DeployMC/actions/runs/44981158073),
  CodeQL [44981158144](https://github.com/woxlo1/DeployMC/actions/runs/44981158144), Scorecard
  [44981158056](https://github.com/woxlo1/DeployMC/actions/runs/44981158056).
- PR #177 added the source-built `DeployMCTestBrokenDescriptor` fixture at
  `250804358a3c99a8a623bc339423201ccfa7d09c` on `2026-11-22T03:44:09Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/177#issuecomment-7073702671) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/177#issuecomment-7073790173). Post-merge
  workflows passed: CI [44981967425](https://github.com/woxlo1/DeployMC/actions/runs/44981967425),
  CodeQL [44981967564](https://github.com/woxlo1/DeployMC/actions/runs/44981967564), Scorecard
  [44981967446](https://github.com/woxlo1/DeployMC/actions/runs/44981967446).
- PR #178 added the source-built `DeployMCTestSlowEnable` fixture at
  `a034352bed3b8a47d4daf5f15aaf896223ad10d0` on `2026-11-22T03:56:16Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/178#issuecomment-7073841525) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/178#issuecomment-7073916898). Post-merge
  workflows passed: CI [44982831143](https://github.com/woxlo1/DeployMC/actions/runs/44982831143),
  CodeQL [44982831147](https://github.com/woxlo1/DeployMC/actions/runs/44982831147), Scorecard
  [44982831179](https://github.com/woxlo1/DeployMC/actions/runs/44982831179).

The local and remote branches `docs/issue-115-fixture-evidence`,
`feature/issue-115-broken-enable-fixture`, `feature/issue-115-broken-descriptor-fixture`, and
`feature/issue-115-slow-enable-fixture` were deleted after evidence was posted.

## PART 35 fixtures #179-#182 merge evidence

The follow-up PRs were merged after all required checks passed. Each local verification used the
ASCII junction and `node tools/build/verify-all.cjs` reported PASS for web, java, cli, python, spec,
i18n, and compat. No running-server, harness, timeout, validator, full-integration, E2E, Web UI, or
in-game command execution was performed for these fixtures. PR #179 was documentation-only.

- PR #179 recorded catch-up evidence at `a8868d4bb5e0d9496e9bc2569cc2964da8d04e57` on
  `2026-11-22T04:07:25Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/179#issuecomment-7073956949) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/179#issuecomment-7074018596). Post-merge
  workflows passed: CI [44983645507](https://github.com/woxlo1/DeployMC/actions/runs/44983645507),
  CodeQL [44983645437](https://github.com/woxlo1/DeployMC/actions/runs/44983645437), Scorecard
  [44983645469](https://github.com/woxlo1/DeployMC/actions/runs/44983645469).
- PR #180 added the source-built `DeployMCTestDependency` fixture at
  `4bcf20f47e0fc886f9a1177ee0d11346d0ca6774` on `2026-11-22T04:16:51Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/180#issuecomment-7074056587) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/180#issuecomment-7074122174). Post-merge
  workflows passed: CI [44984348108](https://github.com/woxlo1/DeployMC/actions/runs/44984348108),
  CodeQL [44984348196](https://github.com/woxlo1/DeployMC/actions/runs/44984348196), Scorecard
  [44984348143](https://github.com/woxlo1/DeployMC/actions/runs/44984348143).
- PR #181 aligned the conflicting-library fixture identity at
  `fd92cd015cd5dcf80e0d1feaa9f5b3bf939a40a6` on `2026-11-22T04:38:11Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074199434) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074321603). A commit-message
  length failure and a temporary Maven Central 429 during CodeQL were resolved with the tree
  unchanged; [CI fix](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074235950) and
  [retry](https://github.com/woxlo1/DeployMC/pull/181#issuecomment-7074255497) record both. Post-merge
  workflows passed: CI [44985927120](https://github.com/woxlo1/DeployMC/actions/runs/44985927120),
  CodeQL [44985927137](https://github.com/woxlo1/DeployMC/actions/runs/44985927137), Scorecard
  [44985927106](https://github.com/woxlo1/DeployMC/actions/runs/44985927106).
- PR #182 added the source-built `DeployMCTestLogNoise` fixture at
  `9b20b366ebf29f361d106d1fa056dd901f344b11` on `2026-11-22T04:50:09Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/182#issuecomment-7074362365) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/182#issuecomment-7074443419). Post-merge
  workflows passed: CI [44986778917](https://github.com/woxlo1/DeployMC/actions/runs/44986778917),
  CodeQL [44986778940](https://github.com/woxlo1/DeployMC/actions/runs/44986778940), Scorecard
  [44986779134](https://github.com/woxlo1/DeployMC/actions/runs/44986779134).
- PR #183 recorded merged-stage evidence for PRs #179-#182 at
  `9fac5f1a45a14a557717d34bbe749864a06c09aa` on `2026-11-22T05:06:04Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/183#issuecomment-7074479712),
  [commit-message fix](https://github.com/woxlo1/DeployMC/pull/183#issuecomment-7074521401), and
  [evidence](https://github.com/woxlo1/DeployMC/pull/183#issuecomment-7074588509). Post-merge
  workflows passed: CI [44987917059](https://github.com/woxlo1/DeployMC/actions/runs/44987917059),
  CodeQL [44987917093](https://github.com/woxlo1/DeployMC/actions/runs/44987917093), Scorecard
  [44987917097](https://github.com/woxlo1/DeployMC/actions/runs/44987917097).
- PR #184 added the source-built wrong-platform fixture at
  `4819195bea80322ae0086621a5f62f6622e2173c` on `2026-11-22T05:17:24Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/184#issuecomment-7074637223) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/184#issuecomment-7074691718). Post-merge
  workflows passed: CI [44988758023](https://github.com/woxlo1/DeployMC/actions/runs/44988758023),
  CodeQL [44988758031](https://github.com/woxlo1/DeployMC/actions/runs/44988758031), Scorecard
  [44988757990](https://github.com/woxlo1/DeployMC/actions/runs/44988757990).
- PR #185 added the versioned per-OS traversal corpus expectations at
  `3b85de8f9249e6049b2c2ea0b742c03600213184` on `2026-11-22T05:35:13Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/185#issuecomment-7074806104) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/185#issuecomment-7074871792). Post-merge
  workflows passed: CI [44990052609](https://github.com/woxlo1/DeployMC/actions/runs/44990052609),
  CodeQL [44990052604](https://github.com/woxlo1/DeployMC/actions/runs/44990052604), Scorecard
  [44990052561](https://github.com/woxlo1/DeployMC/actions/runs/44990052561).
- PR #186 recorded merged-stage evidence for PRs #183-#185 at
  `cfd47b6e365dcd74518d5c51093538fac7a17662` on `2026-11-22T05:55:58Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/186#issuecomment-7074948038),
  [commit-message fix](https://github.com/woxlo1/DeployMC/pull/186#issuecomment-7075001061), and
  [evidence](https://github.com/woxlo1/DeployMC/pull/186#issuecomment-7075066136). Post-merge
  workflows passed: CI [44991495942](https://github.com/woxlo1/DeployMC/actions/runs/44991495942),
  CodeQL [44991495929](https://github.com/woxlo1/DeployMC/actions/runs/44991495929), Scorecard
  [44991495954](https://github.com/woxlo1/DeployMC/actions/runs/44991495954).
- PR #187 added deterministic JWT vectors and a localhost mock JWKS server at
  `ce4fdc7db3742c17f294813e53961eea98f02583` on `2026-11-22T06:13:36Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/187#issuecomment-7075173934) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/187#issuecomment-7075269244). Post-merge CI
  [44992769433](https://github.com/woxlo1/DeployMC/actions/runs/44992769433) and Scorecard
  [44992769504](https://github.com/woxlo1/DeployMC/actions/runs/44992769504) passed; CodeQL
  [44992769372](https://github.com/woxlo1/DeployMC/actions/runs/44992769372) first failed during
  autobuild because Maven Central returned HTTP 429, and a failed-job rerun passed.
- PR #189 added manifest-pinned hostile archive and YAML generators at
  `49f300af83b4ddee565bad4e57117963f5ac6aaa` on `2026-11-22T06:44:51Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/189#issuecomment-7075468913) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/189#issuecomment-7075543887). Post-merge
  workflows passed: CI [44995117396](https://github.com/woxlo1/DeployMC/actions/runs/44995117396),
  CodeQL [44995117319](https://github.com/woxlo1/DeployMC/actions/runs/44995117319), Scorecard
  [44995117358](https://github.com/woxlo1/DeployMC/actions/runs/44995117358). ZIP reader expectations
  are static source review; no product archive or YAML validator was exercised.
- PR #190 recorded docs-only catch-up evidence at
  `49a937516427c5dfae8986503a98aa1de4011f03` on `2026-11-22T09:42:04Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/190#issuecomment-7075562899) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/190#issuecomment-7077351053). All required
  checks passed. Post-merge workflows passed: CI
  [45008817063](https://github.com/woxlo1/DeployMC/actions/runs/45008817063), CodeQL
  [45008817174](https://github.com/woxlo1/DeployMC/actions/runs/45008817174), Scorecard
  [45008817074](https://github.com/woxlo1/DeployMC/actions/runs/45008817074).
- PR #191 added manifest-pinned log corpora at
  `2a253a1fbb228729e204bfbfb2bde486dd440e77` on `2026-11-22T09:57:14Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/191#issuecomment-7077440487) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/191#issuecomment-7077518318). Six Python tests
  passed locally and in PR CI on Ubuntu and Windows; full local toolchain verification passed.
  Post-merge workflows passed: CI
  [45010024263](https://github.com/woxlo1/DeployMC/actions/runs/45010024263), CodeQL
  [45010024232](https://github.com/woxlo1/DeployMC/actions/runs/45010024232), Scorecard
  [45010024242](https://github.com/woxlo1/DeployMC/actions/runs/45010024242). No product console
  consumer, server, Web UI, or renderer was exercised.
- PR #192 recorded docs-only catch-up evidence at
  `211e57f54cd00e15fff187bccf29e24dca6399b4` on `2026-11-22T10:06:53Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/192#issuecomment-7077541582) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/192#issuecomment-7077623183). All required
  checks passed. Post-merge workflows passed: CI
  [45010825570](https://github.com/woxlo1/DeployMC/actions/runs/45010825570), CodeQL
  [45010825571](https://github.com/woxlo1/DeployMC/actions/runs/45010825571), Scorecard
  [45010825633](https://github.com/woxlo1/DeployMC/actions/runs/45010825633).
- PR #193 added manifest-pinned config corpora at
  `2b6d0f54cf69f32bfaa93b996914c434a53d0531` on `2026-11-22T10:26:30Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/193#issuecomment-7077762217) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/193#issuecomment-7077835172). Four Python tests
  and one Java test passed locally and in PR CI on Ubuntu and Windows; full local toolchain
  verification passed. Post-merge workflows passed: CI
  [45012455730](https://github.com/woxlo1/DeployMC/actions/runs/45012455730), CodeQL
  [45012455726](https://github.com/woxlo1/DeployMC/actions/runs/45012455726), Scorecard
  [45012455722](https://github.com/woxlo1/DeployMC/actions/runs/45012455722). No server or host
  configuration was read.
- PR #194 recorded docs-only catch-up evidence at
  `706adff74070bfdda38f9555a7bfac421cad5716` on `2026-11-22T10:37:40Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/194#issuecomment-7077872089) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/194#issuecomment-7077950671). All required
  checks passed. Post-merge workflows passed: CI
  [45013368573](https://github.com/woxlo1/DeployMC/actions/runs/45013368573), CodeQL
  [45013368614](https://github.com/woxlo1/DeployMC/actions/runs/45013368614), Scorecard
  [45013368556](https://github.com/woxlo1/DeployMC/actions/runs/45013368556).
- PR #195 added generated large directory trees at
  `3087abcaecadbebc197f0e0ef35c3a132b467493` on `2026-11-22T10:51:15Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/195#issuecomment-7078017502) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/195#issuecomment-7078102376). Four Python tests
  passed locally and in PR CI on Ubuntu and Windows; full local toolchain verification passed.
  Post-merge workflows passed: CI
  [45014437863](https://github.com/woxlo1/DeployMC/actions/runs/45014437863), CodeQL
  [45014437845](https://github.com/woxlo1/DeployMC/actions/runs/45014437845), Scorecard
  [45014437919](https://github.com/woxlo1/DeployMC/actions/runs/45014437919). Generated output stays
  in ignored or temporary directories; no listing API, benchmark, server, or UI was exercised.
- PR #196 recorded docs-only catch-up evidence at
  `289b9a4921378b799c9895963033b65112c35a8c` on `2026-11-22T11:01:31Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/196#issuecomment-7078138567) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/196#issuecomment-7078211932). All required
  checks passed. Post-merge workflows passed: CI
  [45015259954](https://github.com/woxlo1/DeployMC/actions/runs/45015259954), CodeQL
  [45015259957](https://github.com/woxlo1/DeployMC/actions/runs/45015259957), Scorecard
  [45015259900](https://github.com/woxlo1/DeployMC/actions/runs/45015259900).
- PR #197 added cross-implementation filter vectors at
  `9b7b5eecf33a63cc25f77de7316fe67eaa641df2` on `2026-11-22T11:14:35Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/197#issuecomment-7078276953) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/197#issuecomment-7078352425). Seven Python tests
  passed locally and in PR CI on Ubuntu and Windows; full local toolchain verification passed.
  Post-merge workflows passed: CI
  [45016345367](https://github.com/woxlo1/DeployMC/actions/runs/45016345367), CodeQL
  [45016345462](https://github.com/woxlo1/DeployMC/actions/runs/45016345462), Scorecard
  [45016345537](https://github.com/woxlo1/DeployMC/actions/runs/45016345537). No product filter
  parser consumes the manifest and no cross-implementation parity is claimed.

The local and remote branches `docs/issue-115-evidence-catchup`,
`feature/issue-115-dependency-fixture`, `feature/issue-115-conflicting-libs-alignment`,
`feature/issue-115-log-noise-fixture`, `docs/issue-115-fixture-evidence-catchup`,
`feature/issue-115-wrong-platform-fixture`, `feature/issue-115-traversal-os-expectations`,
`docs/issue-115-evidence-catchup-185`, `docs/issue-115-evidence-catchup-187`,
`test/jwt-vectors-mock-jwks`, `test/hostile-input-fixtures`, `docs/issue-115-evidence-catchup-189`,
`test/log-corpora-fixtures`, `docs/issue-115-evidence-catchup-191`,
`test/config-corpora-fixtures`, `docs/issue-115-evidence-catchup-193`,
`test/large-tree-fixtures`, `docs/issue-115-evidence-catchup-195`, and
`test/filter-vector-fixtures` were deleted after evidence was posted. The
`docs/issue-115-evidence-catchup-197` branch is temporary for this final evidence update.
Issue #115 is complete; final closure evidence is recorded in the issue.

## PART 33 algorithm regression suites (issue #116)

Three reviewed PRs added 60 dedicated PART 33 tests. Each PR has a Japanese verification comment,
and all 18 required checks passed before merge. Branches that fell behind `main` were rebased,
re-verified, and force-pushed with lease before merging. No product code, CI, build files,
documentation, or user-facing strings changed in those PRs.

- PR #199 added 24 file-safety tests (sandbox 8, atomic write 9, YAML/archive 7), merged at
  `0b831507233d007ecb1e65ccf3645d99e3e0445a` on `2026-11-23T03:55:49Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/199#issuecomment-7091088026) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/199#issuecomment-7091187865). All 18 required
  checks passed. Post-merge workflows: CI
  [45108813174](https://github.com/woxlo1/DeployMC/actions/runs/45108813174), CodeQL
  [45108813105](https://github.com/woxlo1/DeployMC/actions/runs/45108813105), Scorecard
  [45108813125](https://github.com/woxlo1/DeployMC/actions/runs/45108813125).
- PR #200 added 16 runtime-limit tests (snapshot GC 7, log attribution 4, rate limit 5), merged at
  `98b6b79c900502bcdb659c59565a1788d8f7a157` on `2026-11-23T04:20:24Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/200#issuecomment-7091107868) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/200#issuecomment-7091552631). The first merge
  attempt was rejected because #199 had advanced `main`; the branch was rebased and re-verified
  (35 core and 5 transport Part33 tests PASSED). Post-merge workflows: CI
  [45111254553](https://github.com/woxlo1/DeployMC/actions/runs/45111254553), CodeQL
  [45111254527](https://github.com/woxlo1/DeployMC/actions/runs/45111254527), Scorecard
  [45111254520](https://github.com/woxlo1/DeployMC/actions/runs/45111254520).
- PR #201 added 20 auth/RBAC contract tests (JWT 8, RBAC 7, deployment recovery 5), merged at
  `5d279c6bd2ed1a681ebed7ef0657e85039b72633` on `2026-11-23T04:25:47Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/201#issuecomment-7091132575) and
  [evidence](https://github.com/woxlo1/DeployMC/pull/201#issuecomment-7091634008). It was rebased
  onto the #199 and #200 merges and re-verified (55 core and 5 transport Part33 tests PASSED).
  Post-merge workflows: CI [45111799148](https://github.com/woxlo1/DeployMC/actions/runs/45111799148),
  CodeQL [45111799132](https://github.com/woxlo1/DeployMC/actions/runs/45111799132), Scorecard
  [45111799194](https://github.com/woxlo1/DeployMC/actions/runs/45111799194).

Implemented boundaries were exercised against real code: `SandboxResolver`, `FileOperationsService`,
`YamlValidator`/`ValidatorRegistry`, `DiffEngine`/`DiffLimits`, `SnapshotStore`, the bounded
WebSocket queue, and the committed JWT, RBAC, and state-machine artifacts. Algorithms without a
product implementation were validated only as specification/fixture contracts and are recorded as
`NOT IMPLEMENTED`: existing non-NFC filename byte preservation, fstat on opened descriptors, OS
file locks, audit write events, `file.changed`, startup temp cleanup, archive path/symlink/collision
and total-size/ratio checks, product JWT verification, RBAC evaluation, deployment engine execution,
log attribution consumers, HTTP token buckets, `console.gap`, and slow-consumer close. No parity or
support claim is made. No server, harness full run, full integration suite, E2E, Web UI, or in-game
command consumed the suites. Localization was not changed because no user-facing strings were added.

The local and remote branches `test/issue-116-file-safety`, `test/issue-116-runtime-limits`, and
`test/issue-116-auth-rbac` were deleted after evidence was posted. The
`docs/issue-116-evidence-catchup` branch is temporary for this final evidence update.

## PART 36 observability baseline (issue #117)

Five stages delivered the doctor, structured logging, opt-in metrics, self-protection, Web panel, and
Bukkit host wiring. Issue #117 was closed prematurely at `2026-11-23T05:37:26Z` after PR #205; it was
reopened at `2026-11-23T05:48:28Z` with
[comment 7092748466](https://github.com/woxlo1/DeployMC/issues/117#issuecomment-7092748466) and kept
open through PR #207. Each implementation PR has a Japanese verification comment and a Japanese
post-merge evidence comment. All required PR checks passed before merge, and post-merge CI, CodeQL,
and Scorecard passed on every final merge commit.

- PR #203, normative contract, merged at `072feda50851e0fd83f3e4fe3fd3f814dcf54f2a` on
  `2026-11-23T05:03:48Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/203#issuecomment-7092311548). Locally,
  `npm run lint` and `npm test` in `tools/spec` passed. Post-merge: CI
  [45115451116](https://github.com/woxlo1/DeployMC/actions/runs/45115451116), CodeQL
  [45115451110](https://github.com/woxlo1/DeployMC/actions/runs/45115451110), Scorecard
  [45115451108](https://github.com/woxlo1/DeployMC/actions/runs/45115451108).
- PR #204, Web doctor panel, merged at `2f3abfc29fa30fbef167eb6789298482377d4ee5` on
  `2026-11-23T05:24:38Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/204#issuecomment-7092483762). Locally,
  `pnpm lint`, `pnpm format:check`, `pnpm test` (14 tests), and `pnpm build` passed. Post-merge: CI
  [45117597970](https://github.com/woxlo1/DeployMC/actions/runs/45117597970), CodeQL
  [45117598002](https://github.com/woxlo1/DeployMC/actions/runs/45117598002), Scorecard
  [45117597912](https://github.com/woxlo1/DeployMC/actions/runs/45117597912).
- PR #205, `core.doctor`, merged at `7555c3d3a3de58ca296e7cea207789dd7bb222cf` on
  `2026-11-23T05:37:23Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/205#issuecomment-7092622290). Focused core
  tests passed locally. Post-merge: CI
  [45118937861](https://github.com/woxlo1/DeployMC/actions/runs/45118937861), CodeQL
  [45118937712](https://github.com/woxlo1/DeployMC/actions/runs/45118937712), Scorecard
  [45118937628](https://github.com/woxlo1/DeployMC/actions/runs/45118937628).
- PR #206, transport observability and self-protection, merged at
  `d42f5d2aa357d47bb31fad103c83e8f3bb871718` on `2026-11-23T05:43:15Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/206#issuecomment-7092731636). The full
  `:transport:test` task passed 102 tests, and `ExecutorWatchdogTest.acknowledgementPreventsAStallRecord()`
  passed 10/10 repetitions after a timeout fix. Post-merge: CI
  [45119539417](https://github.com/woxlo1/DeployMC/actions/runs/45119539417), CodeQL
  [45119539475](https://github.com/woxlo1/DeployMC/actions/runs/45119539475), Scorecard
  [45119539516](https://github.com/woxlo1/DeployMC/actions/runs/45119539516).
- PR #207, Bukkit host wiring, rebase-merged at `b882f95fcdb324b7de7d0f69201b0bf3e88283a0` on
  `2026-11-23T10:00:33Z`;
  [verification](https://github.com/woxlo1/DeployMC/pull/207#issuecomment-7097261754),
  [commit split](https://github.com/woxlo1/DeployMC/pull/207#issuecomment-7097297218), and
  [post-merge evidence](https://github.com/woxlo1/DeployMC/pull/207#issuecomment-7097457796). The
  owner requested reviewable commits, so the single implementation commit was split into five logical
  Conventional Commits with an identical final tree; a later message-only rewrite fixed the
  `footer-leading-blank` commitlint failure. The five commits remain individually on `main`:
  `480da8c`, `b7c6d91`, `85059ba`, `7f6f3c2`, and `b882f95`. Locally, `.\gradlew.bat
  :platform:bukkit:test --console=plain --no-configuration-cache`, `.\gradlew.bat spotlessApply`,
  `.\gradlew.bat spotlessCheck :platform:bukkit:test verifyJava8 --console=plain
  --no-configuration-cache`, `node tools/i18n/check`, and `git diff --check` all passed. Post-merge:
  CI [45152141284](https://github.com/woxlo1/DeployMC/actions/runs/45152141284), CodeQL
  [45152141084](https://github.com/woxlo1/DeployMC/actions/runs/45152141084), Scorecard
  [45152141128](https://github.com/woxlo1/DeployMC/actions/runs/45152141128).

Final consolidated local verification on the merged tree ran `node tools/build/verify-all.cjs` and
reported `PASS` for all seven toolchains: `web`, `java`, `cli`, `python`, `spec`, `i18n`, and `compat`.
The current Gradle JUnit XML reports 473 Java tests with 0 failures, 0 errors, and 1 skipped symlink
test. `git diff --check` produced no output and exited 0.

Verified boundaries include the authenticated redacted doctor route, `DoctorService` inputs and
redaction, fail-closed host observability config, `bindPortOwnedByHost(true)`, JSON/plain request
logger selection, opt-in authenticated metrics dispatch, the executor watchdog, the Web doctor
panel build, and the 13-check localized `/deploymc doctor` report. Not verified: a running server,
a Bukkit mock, browser rendering, E2E, the full integration suite, or an in-game command execution.
The unbundled PART 36 algorithms remain specification or fixture contracts; no support tier, parity,
or running-product claim is made.

## Earlier pull-request evidence

All 16 currently required pull-request checks passed before PR #139 was merged; the first attempt
failed only `Commit messages` because one body line exceeded 120 characters. The message was amended,
the branch was force-pushed with lease, and the rerun passed every required check. Post-merge `main`
workflows for the loader stage all passed: CI
[44481755600](https://github.com/woxlo1/DeployMC/actions/runs/44481755600), CodeQL
[44481755589](https://github.com/woxlo1/DeployMC/actions/runs/44481755589), and Scorecard
[44481755610](https://github.com/woxlo1/DeployMC/actions/runs/44481755610).

All required pull-request checks passed before PR #141 was merged, including both Java jobs, CodeQL,
i18n, Spec, commit messages, and dependency review. Post-merge `main` CI
[44485350410](https://github.com/woxlo1/DeployMC/actions/runs/44485350410) and Scorecard
[44485350476](https://github.com/woxlo1/DeployMC/actions/runs/44485350476) passed. The first post-merge
CodeQL attempt failed only because Gradle autobuild could not resolve plugin dependencies from
`plugins.gradle.org`; a rerun of
[44485350346](https://github.com/woxlo1/DeployMC/actions/runs/44485350346) passed all three language
analyses, and the pull-request CodeQL analysis had already passed for the same commit.

All required checks passed before evidence PR #142 was merged. Post-merge `main` CI
[44487041759](https://github.com/woxlo1/DeployMC/actions/runs/44487041759), CodeQL
[44487041901](https://github.com/woxlo1/DeployMC/actions/runs/44487041901), and Scorecard
[44487042080](https://github.com/woxlo1/DeployMC/actions/runs/44487042080) all passed, and issue #45
was closed with a comment recording that evidence.

All required checks passed before PR #144 was merged. The Java CodeQL autobuild first failed because
Gradle could not resolve `org.codehaus.plexus:plexus-xml:4.0.4` from `plugins.gradle.org`; a rerun of
[44490102786](https://github.com/woxlo1/DeployMC/actions/runs/44490102786) passed all three language
analyses. The Java, Web, CLI, Python, Spec, i18n, commit-message, dependency-review, and title checks
passed. Post-merge `main` CI
[44490811820](https://github.com/woxlo1/DeployMC/actions/runs/44490811820), CodeQL
[44490811804](https://github.com/woxlo1/DeployMC/actions/runs/44490811804), and Scorecard
[44490811934](https://github.com/woxlo1/DeployMC/actions/runs/44490811934) passed.

All required checks passed before evidence PR #145 was merged. Post-merge `main` CI
[44492254923](https://github.com/woxlo1/DeployMC/actions/runs/44492254923), CodeQL
[44492254967](https://github.com/woxlo1/DeployMC/actions/runs/44492254967), and Scorecard
[44492254918](https://github.com/woxlo1/DeployMC/actions/runs/44492254918) all passed, and issue #46
was closed with a comment recording that evidence. Earlier passing runs for WP-1.5, WP-1.4, WP-1.3,
and their documentation PRs are recorded in the repository history.

## WP-1.4 verification

These commands were run on 2026-11-18 before opening the pull request:

```text
pnpm lint                         # pass
pnpm format:check                 # pass
pnpm test                         # 14 passed, 0 failed
pnpm build                        # pass; 2 gzip and 2 Brotli assets generated
gradlew build verifyJava8         # pass; JUnit XML 93 tests, 0 failures, 0 errors
node tools/build/verify-all.cjs   # PASS web, java, cli, python, spec, i18n
jar tf plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar
  -> deploymc-web/index.html, hashed JS/CSS, source map, and .gz/.br variants
```

The Java build now requires `web/dist`; CI and `verify-all.cjs` build Web first. The same Web-first
order passed on both CI operating systems after merge. Browser rendering, real-server load, and
product routes remain unverified.

## WP-1.5 loader-stage verification

These commands were run on 2026-11-18 on `feature/wp-1.5-config-system` before the first PR was
prepared:

```text
gradlew spotlessApply :core:test --console=plain   # pass; 53 core tests, 0 failures
gradlew build verifyJava8 --console=plain          # pass; 121 Java tests, 0 failures, 0 errors
                                                   # Java 8 bytecode check passed
```

The deterministic property test generated 250 invalid mutations of the default configuration and
verified that every mutation failed validation and never became active. JAR inspection reported 254
relocated and 0 unrelocated SnakeYAML classes, one packaged schema, and one default template. The
configuration design and library evidence are in [configuration](../configuration.md) and
[SnakeYAML selection research](../research/config-yaml-library.md). PR #139 passed all required
checks and merged at `5fc04a1`; its post-merge workflows are listed above. No command execution or
real-server load exists for that stage.

## WP-1.5 command-stage verification

These commands were run on 2026-11-19 on `feature/wp-1.5-commands` before opening the second PR:

```text
gradlew spotlessApply :platform:bukkit:test --console=plain
  # pass; 42 Bukkit tests, 0 failures
gradlew spotlessApply build verifyJava8 --console=plain
  # pass; 133 Java tests, 0 failures, 0 errors; Java 8 bytecode check passed
node tools/i18n/check                 # 0 errors, 0 warnings
```

JAR inspection confirmed the generated `commands` and `permissions` blocks with five operator-only
nodes. The command tests are server-free and cover permission checks, tab completion, status output,
safe reload, limited doctor coverage, and console-only token behavior. No plugin startup, in-game
command execution, browser rendering, or conflicting-library classloader check was performed. PR #141
passed all required checks and merged at `f853136`; its post-merge workflow results are recorded above.

## WP-1.6 verification

These commands were run on 2026-11-19 through the ASCII junction before PR #144 was opened:

```text
.\gradlew.bat spotlessApply :platform:bukkit:test build verifyJava8 --console=plain
  BUILD SUCCESSFUL; 50 Bukkit tests passed; Java 8 bytecode check passed
JUnit XML aggregate: 143 tests, 0 failures, 0 errors
node tools/i18n/check
  i18n check passed: 0 error(s), 0 warning(s)
git diff --check
  pass
```

No real Minecraft server was loaded. `players.locale` remains `UNKNOWN` until verified on a real
server; no support tier is claimed.

## WP-1.7 verification

These commands were run on 2026-11-19 through the ASCII junction before the WP-1.7 pull request was
prepared:

```text
.\gradlew.bat spotlessApply build verifyJava8 --console=plain
  BUILD SUCCESSFUL; 143 Java tests, 0 failures, 0 errors; Java 8 bytecode check passed
  Plugin JAR verification passed: DeployMC-0.1.0-SNAPSHOT.jar, 4268456 bytes,
    2493 classes (0 versioned), max common bytecode 52
.\gradlew.bat :plugin:shadowJar --rerun-tasks --console=plain
  BUILD SUCCESSFUL; forced rerun after changing Shadow exclusions
.\gradlew.bat :plugin:reportPluginJarSize --console=plain
  Plugin JAR size: 4268456 bytes (4.07 MiB): DeployMC-0.1.0-SNAPSHOT.jar
git diff --check
  pass
node tools/build/verify-all.cjs
  PASS web, java, cli, python, spec, i18n
```

Twelve fixture JARs were generated with `.\gradlew.bat :plugin:generatePluginJarFixtures --console=plain`
and checked with
`.\gradlew.bat --project-prop=deploymc.verifyJarFile=build/fixtures/<name>.jar :plugin:verifyPluginJarFixture --console=plain`.
`good.jar`, `valid-mr.jar`, and `good-service.jar` passed. `outside.jar`, `new-bytecode.jar`,
`malformed-mr.jar`, `old-mr.jar`, `mr-without-manifest.jar`, `missing-base.jar`, `duplicate.jar`,
`bad-service.jar`, and `bad-service-provider.jar` each failed with the intended diagnostic.

The produced JAR has 2,681 entries and 2,493 classes, including 230 relocated SnakeYAML classes and
0 unrelocated `org/yaml/**` or `io/netty/**` classes, 0 multi-release entries, 0 `module-info.class`
entries, 0 service files, and 0 duplicate names. PR #147 merged the work at `db3e33a` and issue #47
closed automatically; the GitHub state and post-merge workflow results are recorded above. No real
server, conflicting-library classloader, browser, or text-component check was performed; no support
tier is claimed.

## WP-1.9 verification

These commands were run on 2026-11-19 through the ASCII junction on
`feat/wp-1.9-token-authentication`. All passed:

```text
.\gradlew.bat :platform:bukkit:test --console=plain
  BUILD SUCCESSFUL; 51 Bukkit tests, 0 failures, 0 errors
.\gradlew.bat spotlessApply --console=plain
  BUILD SUCCESSFUL
.\gradlew.bat build verifyJava8 --console=plain
  BUILD SUCCESSFUL; Java 8 bytecode check passed
  JUnit XML aggregate: 159 tests, 0 failures, 0 errors
  Plugin JAR verification passed: DeployMC-0.1.0-SNAPSHOT.jar, 4297968 bytes,
    2510 classes (0 versioned), max common bytecode 52
npm test --prefix tools/spec                       # 14 tests, 14 passed, 0 failed
npm run lint --prefix tools/spec                   # openapi, asyncapi, config, state-machine pass
node tools/i18n/check                              # 0 error(s), 0 warning(s)
node --test tools/i18n/test/check.test.cjs         # 7 tests, 7 passed, 0 failed
node tools/i18n/check --write-status               # 0 error(s), 0 warning(s)
node tools/build/verify-all.cjs                    # PASS web, java, cli, python, spec, i18n
git diff --check                                   # pass
```

The route-enumeration test reads the committed OpenAPI document, asserts 401 for every non-health
route, and separately checks the AsyncAPI bearer declaration for `/api/v1/ws`. Module test counts
were core 64, transport 32, api 8, Bukkit 51, and standalone 4. PR #149 merged at `3244680` and issue
#49 closed automatically; the GitHub state, passing checks, and post-merge workflow runs are recorded
above. No real Minecraft server load, in-game command execution, browser rendering,
conflicting-library classloader check, or text-component compatibility check was performed.

## WP-1.8 verification

The work was organized into eight logical commits on `feat/wp-1.8-compatibility-harness` before the
squash merge: transport catalog fix, Bukkit transport wiring, harness and fixture, verifier step,
CI job, OpenAPI status, compatibility documentation, and status pages. A ninth commit fixed the
Windows runner Java path before the passing rerun. These commands were run on 2026-11-20 on the
ASCII junction; all passed:

```text
python -m unittest discover -s tests/compat-matrix -p 'test_*.py'
  Ran 14 tests ... OK
node tools/build/verify-all.cjs
  PASS web, java, cli, python, spec, i18n, compat
.\gradlew.bat spotlessApply --console=plain
  BUILD SUCCESSFUL
git diff --check
  no output
python tests/compat-matrix/harness.py run --entry paper-26.3 \
  --plugin-jar plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar \
  --conflict-plugin-jar tests/fixtures/conflicting-plugin/build/libs/deploymc-test-conflicting-libs-1.0.0.jar \
  --result tests/compat-matrix/results/compat-v1-local.json \
  --java 'C:\Users\satos\AppData\Local\Temp\opencode\jdk25\extracted\jdk-25.0.5+8\bin\java.exe'
  compat entry paper-26.3: pass
```

The local result recorded Paper 26.3 build 256 on Java 25.0.5, API and Minecraft ports 50989 and
50990, 14 capabilities, and no `DeployMC-netty-*` thread after explicit disable. The first PR run
[44703288127](https://github.com/woxlo1/DeployMC/actions/runs/44703288127) failed only
`Compatibility (windows-latest)`: Git Bash did not resolve the Windows-style `PATH` entry and started
Paper with Java 21. Commit `9dd87ed` passes the native Java 25 path with `--java`; the rerun
[44704128125](https://github.com/woxlo1/DeployMC/actions/runs/44704128125) passed all required
checks. Post-merge runs passed: CI
[44704690836](https://github.com/woxlo1/DeployMC/actions/runs/44704690836), CodeQL
[44704690767](https://github.com/woxlo1/DeployMC/actions/runs/44704690767), and Scorecard
[44704690925](https://github.com/woxlo1/DeployMC/actions/runs/44704690925).

The CI artifacts `compat-v1-ubuntu-latest` and `compat-v1-windows-latest` each contain a passing
result at `839b789`. Both report MC 26.3 build 256, Java 25.0.5, 2988 unrelocated Netty classes in
the fixture, 2164 relocated Netty classes in DeployMC, four `DeployMC-netty-*` threads before
disable and none after, and every required harness check true. They were rendered into the
[support matrix](../compatibility/support-matrix.md). This is smoke evidence only: the Tier 1/2/3
matrix remains empty and `softwareName` is still `Unknown`.

## Known limitations

- DeployMC is pre-alpha. A real Paper smoke run observes plugin enable, transport startup,
  authenticated `/api/v1/server`, and clean shutdown, but the full feature set has not been tested
  on a server.
- The WP-1.5 command surface and WP-1.6 per-sender message path still have server-free tests only;
  the smoke run does not execute `/deploymc` in-game.
- Only `auth.mode: token` is implemented. `cloudflare` and `local-onetime` refuse startup.
- Every token is admin-equivalent until 0.4.0; RBAC and path scopes do not exist yet.
- Per-sender locale detection is feature-detected at runtime; `players.locale` stays `UNKNOWN` until
  real-server verification. See [plugin messages](../i18n/plugin-messages.md).
- The support-tier matrix is empty; the v1 smoke harness records real Paper evidence but earns no
  tier. See [compat status](compat-status.md).
- Final-JAR verification is local and build-time only. A real-server classloader smoke check now
  exists, but no numeric artifact size budget is enforced.
- Research is desk research with source links; no hands-on competitor test, benchmark, or legal
  review was performed. See [research](../research/README.md).
- Japanese Web and plugin message strings are machine-translated drafts without native review. See
  [i18n status](i18n-status.md).
- Many security-critical components are designed or only partially implemented. The WP-2.3 store,
  restore primitives, retention, verify/repair, GC, and the WP-2.6 snapshot REST routes now exist,
  but no running server, UI, or command consumes them. See
  [implementation status](implementation-status.md) and [risks](risks.md).

## Open architectural and owner questions

Unresolved owner decisions are listed in [decisions pending](decisions-pending.md): D-001 hosting
setup, D-002 Cloudflare plan/domain, D-003 distribution accounts, D-005 additional languages,
D-006 community channel, and D-007 weekly time budget. None blocks Phase 0 documentation.

## Next tasks

Owner-directed order: #2, #7, #10, #41-#52 (all done) -> category 2 (#53-#114) -> category 3
(#115-#120) -> category 4 (#121-#123) -> category 5 (#124-#130). Issues #115, #116, and #117 are
complete, and their final consolidated evidence is recorded. Continue with category 3 (#118-#120),
then category 4 (#121-#123) and category 5 (#124-#130). Start later items only when earlier items
are complete or the owner explicitly approves.

## Release checklist

The normative checklist is Part 29 of the master specification. Before any release, copy these
thirteen gates into the release PR and attach evidence to every item:

1. Milestone issues closed or moved with explanations.
2. CI green on `main`, including Windows jobs and compatibility results for declared tiers.
3. `CHANGELOG.md` generated from Conventional Commits and curated.
4. Version consistent across plugin, API, SDKs, CLI, and spec; migrations tested.
5. Support matrix regenerated; README claims verified.
6. Documentation site version tagged; screenshots and translation status updated.
7. Dependency audit, CodeQL, secret scan, SBOM, signed artifacts, provenance, checksums.
8. Artifacts built reproducibly on CI; JAR verification passes.
9. Manual smoke test on a real server per Tier 1 platform.
10. Release notes in English and Japanese, with breaking changes first.
11. Tag and GitHub Release created by the release workflow; assets verified.
12. External publication only with the owner's credentials and explicit approval.
13. Post-release: reopen the next milestone, update project status and HANDOFF, monitor closely.

No release has been prepared. Policy, automation, and signing are tracked in #127, #128, and #129;
the v1 evidence bundle is #130. This page records the gates, not their completion.

## Consistency audit procedure

Run at every milestone and record results here (master specification Part 39.3):

1. Every `MUST`/`MUST NOT` is implemented and tested, or listed as an open gap with an issue.
2. Every endpoint in code appears in `openapi.yaml` and vice versa.
3. Every config key in code appears in `config.schema.json` and the docs.
4. Every permission and error code in code appears in the catalogs.
5. README claims map to rows in the implementation-status table.
6. Support-matrix claims map to the latest CI results.
7. Translation catalogs structurally match English.
8. ADR statuses match reality.

### Audit result: 2026-11-18 (Phase 0)

| Invariant | Result |
|---|---|
| Specification requirements | Phase 0 items delivered; later requirements are open gaps tracked in #41-#130. |
| Endpoints vs OpenAPI | Phase 0 result: no server implementation. WP-1.3 added transport machinery but no registered endpoints; re-audit when routes are added at WP-2.6. |
| Config keys vs schema | Not applicable: no config loader exists. Re-audit at WP-1.5. |
| Permissions and error codes vs catalogs | Not applicable: no authorization code exists. Re-audit at WP-3.x. |
| README vs implementation status | Aligned: both describe buildable skeletons, the source-complete Bukkit adapter, and the embedded transport; neither claims product features. |
| Support matrix vs CI | Aligned: matrix empty, no harness or support claim. |
| Translation catalogs | `node tools/i18n/check` reported 0 errors and 0 warnings. |
| ADR statuses | 14 ADRs reviewed: ADR-0003 is now Accepted for Netty 4.1.141.Final; the editor part of ADR-0011 remains Proposed. No contradiction with verified state found. |

The Phase 0 row records the state at that audit. WP-1.3 updated ADR-0003 after the measured spike;
the editor choice in ADR-0011 remains Proposed.

The 2026-11-19 WP-1.5 command implementation re-audits two rows: the configuration loader now exists
and is schema-driven, so the earlier "no config loader" note is obsolete; the five Bukkit command
permission nodes are declared in the generated descriptor and checked by tests, but no authorization
code or endpoints exist yet. `node tools/i18n/check` still reports 0 errors and 0 warnings, and the
Japanese Bukkit command catalog is a machine-translated draft.

Re-run the local toolchain checks with `node tools/build/verify-all.cjs`; CI remains the merge
authority. See [CI documentation](ci.md).
