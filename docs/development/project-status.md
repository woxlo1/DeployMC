# Project Status

Last updated: 2026-11-20 (Phase 1 WP-1.4, both WP-1.5 stages, WP-1.6-WP-1.9, and WP-2.1 merged and
closed). Verified against `main` at `748bbc8` after PR #153; earlier merge evidence is recorded
below.

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
  as completed. Verification is recorded below.
- Later milestones: `0.3.0` through `0.10.0` exist with the remaining specification work packages
  and category 3-5 work registered as issues #42-#130.

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
The scheduled fuzz workflow has not run on a schedule yet, and no file API or server process uses
`SandboxPath`.

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
  --conflict-plugin-jar tests/fixtures/conflicting-plugin/build/libs/legacy-netty-fixture-1.0.0.jar \
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
- Many security-critical components are designed but not implemented. See
  [implementation status](implementation-status.md) and [risks](risks.md).

## Open architectural and owner questions

Unresolved owner decisions are listed in [decisions pending](decisions-pending.md): D-001 hosting
setup, D-002 Cloudflare plan/domain, D-003 distribution accounts, D-005 additional languages,
D-006 community channel, and D-007 weekly time budget. None blocks Phase 0 documentation.

## Next tasks

Owner-directed order: #2, #7, #10, #41, #42, #43, #44, #45, #46, #47, #48, #49, #50 (all done) ->
category 2 (#51-#114) -> category 3 (#115-#120) -> category 4 (#121-#123) -> category 5 (#124-#130).
WP-2.1 is merged, issue #50 is closed, post-merge evidence is recorded, and the feature branch is
deleted. Identify the next open category 2 item before starting later work. Start later items only
when earlier items are complete or the owner explicitly approves.

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
