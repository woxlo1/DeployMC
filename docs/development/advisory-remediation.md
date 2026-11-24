# Dependency Advisory Remediation

Last updated: 2026-11-24 (UTC).

This page records issue #122 remediation for the advisories reported on the repository Security tab.
It follows the [dependency policy](dependency-policy.md). The Security tab is authoritative; the
counts below are the observed pre-remediation snapshot, not a frozen current count.

## Owner decisions and scope

- The owner approved a full remediation attempt, including major upgrades, in a new pull request.
- Existing Dependabot pull requests #23-#39 were not merged, closed, rebased, approved, or had
  auto-merge changed. Their dispositions remain recorded as D-009 in
  [decisions pending](decisions-pending.md).
- No product code, CI gate, branch protection, user-facing string, or localization catalog changed.
  All affected packages are development-only web build/test tools or specification-tool dependencies.

## Evidence sources

Sources were checked on 2026-11-24.

| Source | Use |
|---|---|
| `gh api repos/woxlo1/DeployMC/dependabot/alerts --paginate` | Security-tab alert inventory, severity, manifest, vulnerable range, and first patched version |
| `https://github.com/advisories/<GHSA>` | Canonical GitHub Advisory Database records for affected and patched versions |
| npm registry metadata via `npm view` | Released versions, engines, and transitive dependency constraints |
| Upstream release pages | Vite v6.4.4, Vitest v4.1.12, simple-git@4.0.1, and minimatch v11.0.1 release evidence |

Observed pre-remediation inventory: 60 alerts total, 57 open and 3 auto-dismissed. Open severity was
9 critical, 7 high, 34 medium, and 7 low. The 57 open alerts collapse to 37 unique GHSA records.

## Applied remediation

| Area | Before | After | Evidence |
|---|---|---|---|
| Web `vite` | 6.0.5 | 6.4.4 | Official Vite v6.4.4 release page; GHSA ranges for all Vite alerts are satisfied |
| Web `vitest` | 2.1.8 | 4.1.12 | Official Vitest v4.1.12 release notes harden manual and redirect mocks |
| Web `@vitest/mocker` | 2.1.8 | 4.1.12 | Transitive through Vitest 4.1.12; canonical GHSA-82fw and GHSA-qxg6 pages |
| Web `tinypool` | 1.1.1 | not present | Vitest 4.1.12 no longer resolves the vulnerable 1.x package |
| Web `esbuild` | 0.24.0 | 0.25.12 | Transitive through Vite 6.4.4; GHSA-67mh first patched 0.25.0 |
| Web `minimatch` via `@typescript-eslint/typescript-estree` | 9.0.9 | 11.0.1 | Targeted pnpm override; other 3.x consumers remain outside the advisory range |
| Spec `simple-git` | 3.36.0 | 4.0.1 | npm override; GitHub-listed first patched version |
| Spec `@simple-git/argv-parser` | 1.1.1 | 2.0.1 | npm override; required because simple-git 4.0.1 pins parser 2.0.0 |
| Spec `jsonpath-plus` | 7.2.0 | 10.4.0 | npm override; newer than the 10.2.0 and 10.3.0 patched floors |
| Spec `minimatch` | 9.0.9 / 10.2.6 | 11.0.1 | npm override; official minimatch v11.0.1 release page |
| Spec `glob` | 10.5.0 / 13.0.6 | 14.0.2 | npm override; official glob 14.0.2 uses minimatch 11 |
| Spec `ajv` under `json-schema-migrate` | 5.5.2 | 6.14.0 | Nested npm override; root `ajv` 8.20.0 was already current |
| Spec `ajv` under `webapi-parser` | 6.5.2 | 6.14.0 | Nested npm override |
| Spec `@babel/core` under generator React SDK | 7.12.9 | 7.29.13 | npm override; GHSA-4x5r first patched 7.29.6 |

## Unique advisory triage

The table below lists every unique GHSA observed in the pre-remediation Security-tab snapshot.
`State` reflects that snapshot. Duplicate alerts from `package.json` and the corresponding lockfile
are collapsed into one row.

| Severity | State | GHSA / CVE | Package | Manifest(s) | Requirement before | Vulnerable range | First patched | Official source | Action |
|---|---|---|---|---|---|---|---|---|---|
| critical | open | GHSA-5gmw-xhrv-c9v3<br>CVE-2026-104848 | tinypool | web/pnpm-lock.yaml | 1.1.1 | <= 2.1.0 | 2.1.1 | [GHSA-5gmw-xhrv-c9v3](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3) | removed by vitest 4.1.12 |
| critical | open | GHSA-5xrq-8626-4rwp<br>CVE-2026-47429 | vitest | web/pnpm-lock.yaml<br>web/package.json | 2.1.8 | < 3.2.6 | 3.2.6 | [GHSA-5xrq-8626-4rwp](https://github.com/advisories/GHSA-5xrq-8626-4rwp) | web direct -> 4.1.12 |
| critical | open | GHSA-85c8-ppgw-ccpr<br>CVE-2026-104849 | tinypool | web/pnpm-lock.yaml | 1.1.1 | < 2.1.2 | 2.1.2 | [GHSA-85c8-ppgw-ccpr](https://github.com/advisories/GHSA-85c8-ppgw-ccpr) | removed by vitest 4.1.12 |
| critical | open | GHSA-9crc-q9x8-hgqq<br>CVE-2025-24964 | vitest | web/pnpm-lock.yaml<br>web/package.json | 2.1.8 | >= 2.0.0, < 2.1.9 | 2.1.9 | [GHSA-9crc-q9x8-hgqq](https://github.com/advisories/GHSA-9crc-q9x8-hgqq) | web direct -> 4.1.12 |
| critical | open | GHSA-pppg-cpfq-h7wr<br>CVE-2024-21534 | jsonpath-plus | tools/spec/package-lock.json | 7.2.0 | < 10.2.0 | 10.2.0 | [GHSA-pppg-cpfq-h7wr](https://github.com/advisories/GHSA-pppg-cpfq-h7wr) | spec override -> 10.4.0 |
| critical | open | GHSA-v5rq-49vh-5v5c<br>CVE-2026-102829 | @simple-git/argv-parser | tools/spec/package-lock.json | 1.1.1 | < 2.0.1 | 2.0.1 | [GHSA-v5rq-49vh-5v5c](https://github.com/advisories/GHSA-v5rq-49vh-5v5c) | spec override -> 2.0.1 |
| critical | open | GHSA-x6jw-m9v5-85vh<br>CVE-2026-102828 | simple-git | tools/spec/package-lock.json | 3.36.0 | >= 3.15.0, < 4.0.1 | 4.0.1 | [GHSA-x6jw-m9v5-85vh](https://github.com/advisories/GHSA-x6jw-m9v5-85vh) | spec override -> 4.0.1 |
| high | open | GHSA-587h-7wmj-h5pm<br>CVE-2026-112942 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.3 | 6.4.4 | [GHSA-587h-7wmj-h5pm](https://github.com/advisories/GHSA-587h-7wmj-h5pm) | web direct -> 6.4.4 |
| high | open | GHSA-858h-whjf-mvg5<br>CVE-2026-102827 | simple-git | tools/spec/package-lock.json | 3.36.0 | <= 3.36.0 | 4.0.0 | [GHSA-858h-whjf-mvg5](https://github.com/advisories/GHSA-858h-whjf-mvg5) | spec override -> 4.0.1 |
| high | open | GHSA-g4wm-2vf7-vfgr<br>CVE-2026-102826 | simple-git | tools/spec/package-lock.json | 3.36.0 | <= 3.36.0 | 4.0.0 | [GHSA-g4wm-2vf7-vfgr](https://github.com/advisories/GHSA-g4wm-2vf7-vfgr) | spec override -> 4.0.1 |
| high | open | GHSA-hw8r-x6gr-5gjp<br>CVE-2025-1302 | jsonpath-plus | tools/spec/package-lock.json | 7.2.0 | < 10.3.0 | 10.3.0 | [GHSA-hw8r-x6gr-5gjp](https://github.com/advisories/GHSA-hw8r-x6gr-5gjp) | spec override -> 10.4.0 |
| high | open | GHSA-p9ff-h696-f583<br>CVE-2026-39363 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, <= 6.4.1 | 6.4.2 | [GHSA-p9ff-h696-f583](https://github.com/advisories/GHSA-p9ff-h696-f583) | web direct -> 6.4.4 |
| medium | auto_dismissed | GHSA-2g4f-4pwh-qvx6<br>CVE-2025-69873 | ajv | tools/spec/package-lock.json | 5.5.2 | < 6.14.0 | 6.14.0 | [GHSA-2g4f-4pwh-qvx6](https://github.com/advisories/GHSA-2g4f-4pwh-qvx6) | nested json-schema-migrate override -> 6.14.0 |
| medium | open | GHSA-356w-63v5-8wf4<br>CVE-2025-32395 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, < 6.0.15 | 6.0.15 | [GHSA-356w-63v5-8wf4](https://github.com/advisories/GHSA-356w-63v5-8wf4) | web direct -> 6.4.4 |
| medium | auto_dismissed | GHSA-3g3m-2fjr-6pw4<br>CVE-2026-118773 | glob | tools/spec/package-lock.json | 13.0.6 | >= 9.0.0, <= 14.0.1 | 14.0.2 | [GHSA-3g3m-2fjr-6pw4](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4) | auto-dismissed; no change |
| medium | open | GHSA-4r4m-qw57-chr8<br>CVE-2025-31125 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, < 6.0.13 | 6.0.13 | [GHSA-4r4m-qw57-chr8](https://github.com/advisories/GHSA-4r4m-qw57-chr8) | web direct -> 6.4.4 |
| medium | open | GHSA-4w7w-66w2-5vf9<br>CVE-2026-39365 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.1 | 6.4.2 | [GHSA-4w7w-66w2-5vf9](https://github.com/advisories/GHSA-4w7w-66w2-5vf9) | web direct -> 6.4.4 |
| medium | open | GHSA-67mh-4wv8-2f99<br>- | esbuild | web/pnpm-lock.yaml | 0.24.0 | <= 0.24.2 | 0.25.0 | [GHSA-67mh-4wv8-2f99](https://github.com/advisories/GHSA-67mh-4wv8-2f99) | transitive via vite 6.4.4 |
| medium | open | GHSA-698v-4r3x-3vcv<br>CVE-2026-112943 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.3 | 6.4.4 | [GHSA-698v-4r3x-3vcv](https://github.com/advisories/GHSA-698v-4r3x-3vcv) | web direct -> 6.4.4 |
| medium | open | GHSA-82fw-gwwq-j7x9<br>CVE-2026-84373 | vitest | web/pnpm-lock.yaml<br>web/package.json | 2.1.8 | >= 2.1.0, < 4.1.11 | 4.1.11 | [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9) | web direct -> 4.1.12 |
| medium | open | GHSA-859w-5945-r5v3<br>CVE-2025-46565 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, <= 6.1.5 | 6.1.6 | [GHSA-859w-5945-r5v3](https://github.com/advisories/GHSA-859w-5945-r5v3) | web direct -> 6.4.4 |
| medium | open | GHSA-93m4-6634-74q7<br>CVE-2025-62522 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, <= 6.4.0 | 6.4.1 | [GHSA-93m4-6634-74q7](https://github.com/advisories/GHSA-93m4-6634-74q7) | web direct -> 6.4.4 |
| medium | open | GHSA-fx2h-pf6j-xcff<br>CVE-2026-53571 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.2 | 6.4.3 | [GHSA-fx2h-pf6j-xcff](https://github.com/advisories/GHSA-fx2h-pf6j-xcff) | web direct -> 6.4.4 |
| medium | open | GHSA-g3hv-75wg-m249<br>CVE-2026-118764 | minimatch | tools/spec/package-lock.json<br>web/pnpm-lock.yaml | 10.2.6<br>9.0.9 | >= 7.0.0, < 11.0.1 | 11.0.1 | [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249) | override -> 11.0.1 |
| medium | open | GHSA-qxg6-89mg-rqv8<br>CVE-2026-112949 | @vitest/mocker | web/pnpm-lock.yaml | 2.1.8 | >= 2.1.0-beta.7, < 4.1.12 | 4.1.12 | [GHSA-qxg6-89mg-rqv8](https://github.com/advisories/GHSA-qxg6-89mg-rqv8) | transitive via vitest 4.1.12 |
| medium | open | GHSA-r825-w8mm-5pxw<br>CVE-2026-112944 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.3 | 6.4.4 | [GHSA-r825-w8mm-5pxw](https://github.com/advisories/GHSA-r825-w8mm-5pxw) | web direct -> 6.4.4 |
| medium | open | GHSA-rq7h-c2jc-7f22<br>CVE-2026-112946 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.3 | 6.4.4 | [GHSA-rq7h-c2jc-7f22](https://github.com/advisories/GHSA-rq7h-c2jc-7f22) | web direct -> 6.4.4 |
| medium | open | GHSA-v6wh-96g9-6wx3<br>CVE-2026-53632 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.2 | 6.4.3 | [GHSA-v6wh-96g9-6wx3](https://github.com/advisories/GHSA-v6wh-96g9-6wx3) | web direct -> 6.4.4 |
| medium | open | GHSA-v88g-cgmw-v5xw<br>CVE-2020-15366 | ajv | tools/spec/package-lock.json | 5.5.2 | < 6.12.3 | 6.12.3 | [GHSA-v88g-cgmw-v5xw](https://github.com/advisories/GHSA-v88g-cgmw-v5xw) | nested json-schema-migrate override -> 6.14.0 |
| medium | open | GHSA-vg6x-rcgg-rjx6<br>CVE-2025-24010 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, <= 6.0.8 | 6.0.9 | [GHSA-vg6x-rcgg-rjx6](https://github.com/advisories/GHSA-vg6x-rcgg-rjx6) | web direct -> 6.4.4 |
| medium | open | GHSA-x574-m823-4x7w<br>CVE-2025-30208 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, < 6.0.12 | 6.0.12 | [GHSA-x574-m823-4x7w](https://github.com/advisories/GHSA-x574-m823-4x7w) | web direct -> 6.4.4 |
| medium | open | GHSA-xcj6-pq6g-qj4x<br>CVE-2025-31486 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, < 6.0.14 | 6.0.14 | [GHSA-xcj6-pq6g-qj4x](https://github.com/advisories/GHSA-xcj6-pq6g-qj4x) | web direct -> 6.4.4 |
| low | open | GHSA-4x5r-pxfx-6jf8<br>CVE-2026-49356 | @babel/core | tools/spec/package-lock.json | 7.12.9 | <= 7.29.0 | 7.29.6 | [GHSA-4x5r-pxfx-6jf8](https://github.com/advisories/GHSA-4x5r-pxfx-6jf8) | spec override -> 7.29.13 |
| low | open | GHSA-9jrq-w75r-8gcw<br>CVE-2026-112945 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | <= 6.4.3 | 6.4.4 | [GHSA-9jrq-w75r-8gcw](https://github.com/advisories/GHSA-9jrq-w75r-8gcw) | web direct -> 6.4.4 |
| low | open | GHSA-g4jq-h2w9-997c<br>CVE-2025-58751 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, <= 6.3.5 | 6.3.6 | [GHSA-g4jq-h2w9-997c](https://github.com/advisories/GHSA-g4jq-h2w9-997c) | web direct -> 6.4.4 |
| low | open | GHSA-jqfw-vq24-v9c3<br>CVE-2025-58752 | vite | web/pnpm-lock.yaml<br>web/package.json | 6.0.5 | >= 6.0.0, <= 6.3.5 | 6.3.6 | [GHSA-jqfw-vq24-v9c3](https://github.com/advisories/GHSA-jqfw-vq24-v9c3) | web direct -> 6.4.4 |
| low | auto_dismissed | GHSA-xffm-g5w8-qvg7<br>- | @eslint/plugin-kit | web/pnpm-lock.yaml | 0.2.8 | < 0.3.4 | 0.3.4 | [GHSA-xffm-g5w8-qvg7](https://github.com/advisories/GHSA-xffm-g5w8-qvg7) | auto-dismissed; no change |

## Reachability and compatibility assessment

- Vite and Vitest advisories concern development servers and test tooling. Production artifacts are
  built from the pinned React runtime dependencies; the Web build and Vitest suites were rerun.
- Vitest 4.1.12 requires Node 20, 22, or 24+. The Web package requires Node 22+, and CI uses Node 24.
  Vite 6.4.4 supports Node 18, 20, and 22+. The existing `@vitejs/plugin-react` peer range accepts
  Vite 6.
- The specification tools are development-only lints and checks. Upstream packages still constrain
  some transitive dependencies to old majors, so npm `overrides` were used instead of rewriting the
  upstream packages. All specification lint and checker tests were rerun.
- `simple-git` 4.0.1 is the GitHub-listed first patched version, but its upstream release notes only
  say that `package.json` was prepared for publishing and the GHSA text also says no released
  remediation was identified. The parser was therefore additionally overridden to 2.0.1. The
  post-merge Security tab remains the authority for whether GitHub considers this alert resolved.
- The three auto-dismissed alerts were reviewed but not reopened or manually dismissed. They are
  recorded for completeness.

## Verification

Commands were run from the repository root unless a directory is shown.

| Command | Result |
|---|---|
| `git fetch origin main` | exit 0 |
| `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD` | exit 0 |
| `node tools/build/verify-all.cjs` | `PASS web`, `PASS java`, `PASS cli`, `PASS python`, `PASS spec`, `PASS i18n`, `PASS compat` |
| `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | `BUILD SUCCESSFUL`, `75 actionable tasks: 75 up-to-date` |
| `git diff --check` | exit 0 |
| `cd web && pnpm lint` | exit 0 |
| `cd web && pnpm format:check` | `All matched files use Prettier code style!` |
| `cd web && pnpm test` | 5 files and 37 tests passed with Vitest 4.1.12 |
| `cd web && pnpm build` | Vite 6.4.4 build succeeded; 2 gzip and 2 Brotli assets precompressed |
| `cd tools/spec && npm run lint` | OpenAPI, AsyncAPI, config, and state-machine lint passed |
| `cd tools/spec && npm test` | 14 tests passed, 0 failed |

Gradle commands ran through the ASCII junction
`C:\Users\satos\AppData\Local\Temp\opencode\dmc-catchup` because this host repository path contains
non-ASCII characters.

## Residual risk

- npm `overrides` cannot rewrite the dependencies bundled inside the `npm` package in
  `tools/spec/package-lock.json`. A nested `glob@13.0.6` and `minimatch@10.2.5` remain there. This
  chain is development-only, is not invoked by the specification lint/test scripts, and corresponds
  to an auto-dismissed `glob` advisory. No additional override was left in the manifest because it
  was verified not to change the bundled tree.
- `npm audit` still reports moderate advisories in the wider AsyncAPI/npm transitive tree. Those are
  not the same set as the GitHub Security alerts triaged here; no fix is available without replacing
  the upstream toolchain. No new accepted-risk exemption was created by this page.
- No server, browser, E2E run, full integration suite, or in-game command was exercised.

## Issue and pull request

- PR #217 is the final consolidated evidence pull request for issue #122 and carries
  `Closes #122`. The issue is closed when that pull request is merged.
- Existing dependency PRs #23-#39 remain untouched under D-009.
- Japanese verification and post-merge evidence for PR #217 are recorded in the PR comments. The
  post-merge Security-tab recheck is the authority for the remaining alert state.
