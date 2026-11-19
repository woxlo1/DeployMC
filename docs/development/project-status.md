# Project Status

Last updated: 2026-11-19 (Phase 1, WP-1.4 merged; WP-1.5 loader stage merged). Verified against
`main` at `5fc04a1`.

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
  post-WP-1.4 documentation PR #138 merged at `5dd95b9`. The first WP-1.5 stage (issue #45) merged
  in PR #139 at `5fc04a1`; issue #45 remains open until the Bukkit startup and `/deploymc` command
  integration is also merged.
- Later milestones: `0.3.0` through `0.10.0` exist with the remaining specification work packages
  and category 3-5 work registered as issues #42-#130.

Verified GitHub state on 2026-11-19 after PR #139 was merged:

```text
git log -1 --oneline
5fc04a1 feat(config): add validated configuration loader (#139)

gh pr view 139 --json state,mergeCommit,url
state=MERGED mergeCommit=5fc04a173dea8bf3924ebd6207179532f87d6cbb
url=https://github.com/woxlo1/DeployMC/pull/139

gh issue view 45 --json state
state=OPEN

gh pr list --state open --limit 1000
open PRs: 17   (#23-#39 Dependabot updates)

gh issue list --state open --limit 1000
open issues: 86
```

All 16 currently required pull-request checks passed before PR #139 was merged; the first attempt
failed only `Commit messages` because one body line exceeded 120 characters. The message was amended,
the branch was force-pushed with lease, and the rerun passed every required check. Post-merge `main`
workflows for the loader stage all passed: CI
[44481755600](https://github.com/woxlo1/DeployMC/actions/runs/44481755600), CodeQL
[44481755589](https://github.com/woxlo1/DeployMC/actions/runs/44481755589), and Scorecard
[44481755610](https://github.com/woxlo1/DeployMC/actions/runs/44481755610). Earlier passing runs for
WP-1.4, WP-1.3, and their documentation PRs are recorded in the repository history.

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
command execution, browser rendering, or conflicting-library classloader check was performed. This
stage is uncommitted on the feature branch at the time of this update; no PR or CI run exists yet.

## Known limitations

- DeployMC is pre-alpha. No DeployMC feature runs inside a Minecraft server.
- The WP-1.5 command surface has server-free tests only; it has not run on a real Minecraft server.
- Token management is intentionally unimplemented until WP-1.9; `/deploymc token` creates no token.
- The compatibility matrix is empty; no harness exists. See [compat status](compat-status.md).
- Research is desk research with source links; no hands-on competitor test, benchmark, or legal
  review was performed. See [research](../research/README.md).
- Japanese UI strings are a machine-translated draft without native review. See
  [i18n status](i18n-status.md).
- Many security-critical components are designed but not implemented. See
  [implementation status](implementation-status.md) and [risks](risks.md).

## Open architectural and owner questions

Unresolved owner decisions are listed in [decisions pending](decisions-pending.md): D-001 hosting
setup, D-002 Cloudflare plan/domain, D-003 distribution accounts, D-005 additional languages,
D-006 community channel, and D-007 weekly time budget. None blocks Phase 0 documentation.

## Next tasks

Owner-directed order: #2, #7, #10, #41, #42, #43, #44 (all done) -> category 2 (#45-#114, #45 in
progress) -> category 3 (#115-#120) -> category 4 (#121-#123) -> category 5 (#124-#130). Open, verify,
and merge the WP-1.5 Bukkit startup/command PR before starting #46. Start the next item only when
earlier items are complete or the owner explicitly approves.

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
