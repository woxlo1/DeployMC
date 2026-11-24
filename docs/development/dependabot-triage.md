# Dependabot Pull Request Triage

Last updated: 2026-11-24 (UTC).

This page records the individual triage of the open Dependabot pull requests #23-#39 against
`main` at `671a702e5cb8ee3c965f59461df1a43dcabfe53e`. It implements issue #121 and follows the
[dependency policy](dependency-policy.md). It is evidence and a decision record, not a claim that a
dependency was upgraded, that an advisory was remediated, or that a support tier was earned.
Issue #122 remains responsible for advisory remediation.

## Method

The following commands were run for every pull request from the repository root. `S`, `F`, and `N`
below mean `SUCCESS`, `FAILURE`, and `NEUTRAL` as reported by `gh pr checks`.

```text
gh pr view <number> --json number,title,state,mergeable,mergeStateStatus,headRefOid,files,commits
gh api repos/woxlo1/DeployMC/pulls/<number>/files --jq '.[] | [.filename,.additions,.deletions] | @tsv'
gh pr diff <number> --patch
gh pr checks <number> --json name,state,link
gh run view <run-id> --log-failed                 (only for failing checks)
gh api repos/woxlo1/DeployMC/pulls/<number> --jq .auto_merge
git merge-base 671a702e5cb8ee3c965f59461df1a43dcabfe53e <head-sha>
```

`gh pr diff <number> --stat` is not available in the installed `gh 2.104.0`; it returned
`unknown flag: --stat`. File counts were taken from `gh pr view` and the files API instead.
Official upstream release notes or documentation were checked for every version change, with an
access date of 2026-11-24. A green check alone was not treated as approval.

## Classification definitions

| Classification | Meaning |
|---|---|
| SAFE_TO_MERGE | All observed checks green, low-risk patch or minor update, no breaking change or new permission. Still requires rebase to current `main`, a fresh full check run, and an individual reviewed merge. |
| BREAKING_CHANGE_REVIEW | All observed checks green but a major range, migration, baseline change, or possible breaking behavior requires explicit owner review. |
| SUPERSEDED | Another open pull request or the current `main` already carries the same change. |
| CLOSE_WITH_REASON | Stale, invalid, or incompatible change with a recorded reason. This triage did not close any PR; candidates are listed below for owner decision. |
| KEEP_OPEN_FAILING | At least one required check failed. The pull request stays open for a later fix; no gate is disabled. A secondary breaking-change aspect is noted in the row. |

## Repository controls observed

`gh api repos/woxlo1/DeployMC/branches/main/protection` on 2026-11-24 returned `strict: true` for
required status checks, `enforce_admins: true`, `required_conversation_resolution: true`,
`allow_force_pushes: false`, and `allow_deletions: false`. Eighteen required status-check contexts
were listed: Java (ubuntu/windows), Web, CLI (ubuntu/windows/macos), Python (ubuntu/windows), i18n,
Commit messages, Pull request title, Dependency review, Analyze (java-kotlin/javascript-typescript/python),
Spec, and Compatibility (ubuntu/windows). `required_approving_review_count` was `0` and `rulesets`
returned no output. `gh api repos/woxlo1/DeployMC` returned `allow_auto_merge: true` and
`allow_rebase_merge: true`.

All 17 pull requests are `OPEN`, `MERGEABLE`, and `BEHIND` relative to current `main`; their
`merge-base` values are older commits. Every pull request has `auto_merge` enabled by `woxlo1`
with `merge_method: rebase`. Auto-merge is guarded by branch protection and is not evidence of
approval. Several heads report 18 checks without the Compatibility contexts, so an all-green row
below does not prove that the full required set passed. A rebase and fresh full check run are
mandatory before any merge. No setting was changed by this triage.

## Triage summary

| PR | Change | Observed checks | Classification | Reason and recommended owner action |
|---|---|---|---|---|
| #23 | `github/codeql-action/autobuild` 3.39.3 → 4.39.3; 1 file +1/-1 | 20: 17S / 2F / 1N | KEEP_OPEN_FAILING (breaking aspect) | `Commit messages` body line 144 chars; `Analyze (java-kotlin)` reports loaded config v3.39.3 but running v4.39.3. Recreate as a grouped `init`/`analyze`/`autobuild` v4 update, fix the body, verify bundle >= 2.20.7. |
| #24 | `com.diffplug.spotless` 6.25.0 → 8.11.0; 1 file +1/-1 | 20: 20S | SAFE_TO_MERGE | Green on all observed checks. Upstream default `google-java-format` 2.0.0 and legacy-marker/versionCatalog changes do not apply as-is: the repo pins 1.24.0 and uses `com.diffplug.spotless` without `versionCatalog()`. Rebase, rerun four local checks and CI, then merge individually. |
| #25 | `com.gradleup.shadow` 8.3.5 → 9.6.1; 1 file +1/-1 | 20: 14S / 5F / 1N | KEEP_OPEN_FAILING (breaking aspect) | Java/Compatibility/Analyze jobs fail with `NoSuchMethodError` on `AdhocComponentWithVariants.addVariantsFromConfiguration`. Shadow 9.5+ requires Gradle 9.2+; the wrapper is 8.10.2. Coordinate with #34 and verify the final JAR; otherwise ask the owner to close. |
| #26 | `actions/setup-node` 4.4.0 → 7.1.0; 1 file +5/-5 | 20: 19S / 1F | KEEP_OPEN_FAILING (breaking aspect) | Only `Commit messages` fails (body line 142 chars), but v5-v7 span automatic caching changes, Node 24/ESM, and removal of the dummy `NODE_AUTH_TOKEN` export. Fix the body, review those migrations, rebase, rerun. |
| #27 | `org.junit.platform:junit-platform-launcher` 1.11.3 → 6.1.3; 1 file +1/-1 | 20: 18S / 2F | KEEP_OPEN_FAILING (close candidate) | Both Java jobs fail: JUnit 6 requires JVM 17+ while the repo targets Java 8. The Jupiter catalog stays at 5.11.3. Ask the owner to close with this reason; a future Java 17 baseline needs a coordinated PR. |
| #28 | `pytest>=8.3,<9` → `pytest>=8.3,<10`; 1 file +1/-1 | 18: 18S | BREAKING_CHANGE_REVIEW | All observed checks green, but pytest 9 drops Python 3.9 and makes `PytestRemovedIn9Warning` an error. Run real pytest/collection/plugin checks, then rebase and rerun before merge. |
| #29 | `actions/upload-artifact` 4.6.2 → 7.0.2; 2 files +2/-2 | 20: 19S / 1F | KEEP_OPEN_FAILING | `Commit messages` fails (120-char body line). v7 changes `archive:false` behavior, rejects multiple files, ignores `name`, and runs on Node 24. Fix the body, review v7, rebase, rerun. |
| #30 | `actions/setup-python` 5.6.0 → 7.1.0; 1 file +2/-2 | 20: 19S / 1F | KEEP_OPEN_FAILING | `Commit messages` fails (120-char body line). v7 uses Node 24/ESM and removes `pip-install`; this repo uses `python-version: 3.12` and `cache: pip`, not `pip-install`. Fix the body, review, rebase, rerun. |
| #31 | `mypy>=1.13,<2` → `mypy>=1.13,<3`; 1 file +1/-1 | 18: 18S | BREAKING_CHANGE_REVIEW | All observed checks green, but mypy 2 changes parser defaults and several flags and drops Python 3.9. Run real mypy and compare findings, then rebase and rerun. |
| #32 | `github/codeql-action/upload-sarif` 3.39.3 → 4.39.3; 1 file +1/-1 | 20: 19S / 1F | KEEP_OPEN_FAILING | `Commit messages` fails (120-char body line). The SHA changes to v4 but the YAML comment still says `# v3`; v4 requires bundle >= 2.20.7 and Node 24. Fix body and comment, review, rebase, rerun. |
| #33 | `eslint-plugin-react-refresh` 0.4.16 → 0.5.8 in `/web`; 2 files +7/-7 | 18: 16S / 1F / 1N | KEEP_OPEN_FAILING | `Analyze (java-kotlin)` fails during Gradle autobuild on unresolved `logging-parent:11.3.0`, `solstice:1.7.5`, and `org.eclipse.jgit:6.7.0...`; the same analysis passed for #36, so the Web change is not the cause. Rerun CodeQL, rebase, recheck. Branch name says 0.5.7 while title/diff say 0.5.8. |
| #34 | Gradle wrapper 8.10.2 → 9.9.1; jar plus 3 scripts | 20: 16S / 4F | KEEP_OPEN_FAILING (breaking aspect) | Java and Compatibility jobs fail in `:plugin:shadowJar` with an ASM/Groovy `Remapper.mapValue()` error from Shadow 8.3.5. Gradle 9 requires JVM 17+. Plan Gradle 9 and Shadow (#25) together, then rebase and rerun all checks. |
| #35 | JUnit Jupiter 5.11.3 → 6.1.3; 1 file +1/-1 | 20: 16S / 3F / 1N | KEEP_OPEN_FAILING (close candidate) | Gradle and CodeQL report that JUnit 6.1.3 is compatible only with JVM 17+, conflicting with the non-negotiable Java 8 target. Ask the owner to close with this reason; do not adopt Java 17 by assumption. |
| #36 | `globals` 15.14.0 → 17.14.0 in `/web`; 2 files +6/-6 | 18: 18S | SAFE_TO_MERGE | Both versions declare `engines.node: >=18`; 17.14.0 is a data update. Compatibility contexts were absent on this head, so rebase, rerun the full required set, then merge individually. |
| #37 | react/react-dom 18.3.1 → 19.3.1 and `@types/react` 18.3.17 → 19.3.0; 2 files +37/-56 | 18: 17S / 1F | KEEP_OPEN_FAILING (breaking aspect) | `Commit messages` fails (120-char body). React 19 requires both `@types/react` and `@types/react-dom` at 19, but `@types/react-dom` stays 18.3.5. Fix body and types, review migration, rebase, rerun. |
| #38 | `@testing-library/react` 16.1.0 → 16.3.3 in `/web`; 2 files +6/-6 | 18: 18S | SAFE_TO_MERGE | Minor update; the v16 peer-breaking change is already absorbed because `main` uses 16.1.0. Compatibility contexts were absent on this head, so rebase and rerun before merge. |
| #39 | `@vitejs/plugin-react` 4.3.4 → 5.2.0 in `/web`; 2 files +39/-33 | 18: 17S / 1F | KEEP_OPEN_FAILING (breaking aspect) | `Commit messages` fails (120-char body). Plugin 5 requires Node 20.19+/22.12+ and changes dedupe and `exclude` defaults; its Vite peer range includes the repo's 6.0.5. Fix body, review migration, rebase, rerun. |

Counts: 3 `SAFE_TO_MERGE` (#24, #36, #38), 2 `BREAKING_CHANGE_REVIEW` (#28, #31),
12 `KEEP_OPEN_FAILING` (#23, #25, #26, #27, #29, #30, #32, #33, #34, #35, #37, #39),
0 `SUPERSEDED`, and 0 `CLOSE_WITH_REASON` executed. No duplicate open PR updates the same
dependency and current `main` still carries the old versions, so no PR was superseded. #27 and #35
are close candidates pending owner decision.

## Failure evidence

The most common failure is not a dependency test failure. Seven pull requests (#23, #26, #29, #30,
#32, #37, #39) failed `Commit messages` because Dependabot's compare URL made a commit-body line
longer than 120 characters:

```text
##[error]You have commit messages with errors
✖   body's lines must not be longer than 120 characters [body-max-line-length]
✖   found 1 problems, 0 warnings
```

Observed run IDs: #23 `45016522613` (line 144), #26 `45016521311` (line 142), #29 `45016516602`,
#30 `45016524909`, #32 `45016523310`, #37 `44286217803`, and #39 `44286223917`. The remedy is to
rewrite the commit body, not to disable commitlint or the required check.

Other root causes were read from the actual failure logs:

```text
#23 run 45016522620
Loaded a configuration file for version '3.39.3', but running version '4.39.3'.
All steps in a workflow that use `github/codeql-action` must use the same version.

#25 runs 45012614749 / 45012614758
java.lang.NoSuchMethodError: 'void org.gradle.api.component.AdhocComponentWithVariants.
addVariantsFromConfiguration(org.gradle.api.provider.Provider, org.gradle.api.Action)'

#27 runs 45012621405 (both Java jobs)
Could not resolve org.junit.platform:junit-platform-launcher:6.1.3.
Dependency resolution is looking for a library compatible with JVM runtime version 8,
but ... 6.1.3 is only compatible with JVM runtime version 17 or newer.

#33 run 44286164252
Could not find org.apache.logging:logging-parent:11.3.0
Could not find dev.equo.ide:solstice:1.7.5
Could not find org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r

#34 run 45012648491
Execution failed for task ':plugin:shadowJar'.
Caused by: groovy.lang.MissingMethodException: No signature of method:
org.objectweb.asm.commons.Remapper.mapValue() ... (Long) ...

#35 runs 45012634179 / 45012634183
Could not resolve org.junit.jupiter:junit-jupiter:6.1.3.
... only compatible with JVM runtime version 17 or newer.
```

## Official references (all accessed 2026-11-24)

| Source | Used for |
|---|---|
| https://github.com/github/codeql-action/releases/tag/v4.39.0 and /v4.39.2 /v4.39.3 | CodeQL v4 bundle minimum, supported versions, path filtering |
| https://github.com/github/codeql-action/blob/main/src/config-utils.ts | All `github/codeql-action` steps must use the same version |
| https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference#groups-- | Grouped Dependabot updates |
| https://github.com/diffplug/spotless/releases/tag/gradle%2F8.10.3 and /8.11.0 | Spotless marker, `versionCatalog()`, `google-java-format` default |
| https://github.com/gradleup/shadow/releases/tag/9.0.0 and https://gradleup.com/shadow/ | Shadow 9 breaking changes and Gradle version requirements |
| https://github.com/actions/setup-node/releases/tag/v5.0.0 /v6.0.0 /v7.0.0 /v7.1.0 | Node 24, ESM, caching and `NODE_AUTH_TOKEN` changes |
| https://docs.junit.org/6.0.0/release-notes.html and https://docs.junit.org/6.1.3/release-notes.html | JUnit 6 Java 17 baseline |
| https://github.com/junit-team/junit5/releases/tag/r6.0.0 | JUnit Platform/Jupiter/Vintage 6.0.0 |
| https://github.com/pytest-dev/pytest/releases/tag/9.0.0 and /9.2.0, https://pypi.org/pypi/pytest/9.2.0/json | pytest 9 breaking changes and Python baseline |
| https://raw.githubusercontent.com/python/mypy/master/CHANGELOG.md, https://pypi.org/pypi/mypy/2.4.1/json | mypy 2 defaults and Python baseline |
| https://github.com/actions/upload-artifact/releases/tag/v7.0.0 /v6.0.0 and https://raw.githubusercontent.com/actions/upload-artifact/v7.0.2/action.yml | `archive:false`, Node 24 runner requirement |
| https://github.com/actions/setup-python/releases/tag/v7.0.0 and https://raw.githubusercontent.com/actions/setup-python/v7.1.0/action.yml | v7 ESM and removed `pip-install` |
| https://raw.githubusercontent.com/github/codeql-action/main/CHANGELOG.md and https://github.com/github/codeql-action/releases | CodeQL 4.39.x changes; 4.39.4 exists upstream but no in-repo superseding PR |
| https://raw.githubusercontent.com/ArnaudBarre/eslint-plugin-react-refresh/main/CHANGELOG.md | 0.5.0 ESLint 9 / Node 20 / `extraHOCs` requirements |
| https://docs.gradle.org/9.9.1/userguide/compatibility.html and /upgrading_major_version_9.html | Gradle 9 JVM 17+ and plugin migration |
| https://github.com/sindresorhus/globals/releases and https://registry.npmjs.org/globals/17.14.0 | `globals` data update and unchanged Node baseline |
| https://react.dev/blog/2024/04/25/react-19-upgrade-guide and https://react.dev/blog/2026/09/09/react-19-3 | React 19 migration and both `@types` packages |
| https://github.com/vitejs/vite-plugin-react/releases/tag/plugin-react@5.0.0 and https://registry.npmjs.org/@vitejs/plugin-react/5.2.0 | plugin-react 5 Node baseline, dedupe/exclude changes, Vite peer range |
| https://github.com/testing-library/react-testing-library/releases/tag/v16.0.0 and https://registry.npmjs.org/@testing-library/react/16.3.3 | v16 peer changes and React 18/19 support |

## Decisions and owner actions

1. No pull request was merged, closed, rebased, approved, or had auto-merge changed. No required
   check, branch-protection rule, manifest, or lockfile was changed.
2. Individual merge candidates after rebase and a fresh full check run: #24, #36, #38. Each still
   needs its own reviewed PR merge; the owner should not bulk-merge them.
3. Breaking-change review before any merge: #28 (pytest 9), #31 (mypy 2), #26 (setup-node 7),
   #37 (React 19 and missing `@types/react-dom` 19), #39 (plugin-react 5), #23 (grouped CodeQL v4),
   #25 and #34 (Shadow 9 with Gradle 9). #33 needs a CodeQL rerun for an unrelated dependency
   resolution failure. #29, #30, and #32 need only a commit-body fix before their major-action
   review, but their gates remain red until that fix lands.
4. Close candidates requiring explicit owner decision: #27 and #35. Both would force JUnit 6 on a
   Java 8 project, contradicting the non-negotiable Java 8 rule. They stay open in this triage.
5. Every PR is behind current `main`. Before any action, fetch `origin/main`, rebase the individual
   branch, rerun the four local pre-push checks and the full required check set, and post a Japanese
   verification comment. Auto-merge must not be treated as approval or as a bypass.
6. Issue #122 remains open for advisory remediation. This document does not inspect the Security
   tab's current advisory set or claim that a vulnerability count is fixed.

## Limitations

- The subagent triage was read-only. Local `pnpm install/build/test`, Gradle build/`verifyJava8`/
  Spotless, Cargo, pytest, mypy, i18n checks, compatibility harness, server, browser, E2E, full
  integration suite, and in-game commands were **NOT RUN** on these PRs. CI results were read from
  existing runs on old bases.
- `Compatibility (ubuntu-latest)` and `Compatibility (windows-latest)` are required contexts but
  were absent from the head check-runs of several 18-check PRs. Those rows are not claims that the
  full required set passed.
- The root cause of #33's unresolved upstream artifacts was not identified beyond the logged names.
- The GitHub Security tab is authoritative for current advisories; this page does not freeze a
  vulnerability count or assert that a dependency is safe because no alert was seen.
- No product code, CI workflow, branch protection, required check, user-facing string, or
  localization catalog changed with this documentation.
