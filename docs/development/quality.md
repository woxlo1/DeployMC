# Quality Gates

Last updated: 2026-11-23. Targets come from Part 41 of the
[master specification](DeployMC_Complete_Master_Development_Specification.md). They are signals to
be measured, not achievements to be claimed. Adjust them only with recorded evidence.

## Reporting tool

Generate the deterministic Markdown report from artifacts that local verification has already
produced:

```powershell
node tools/quality/check.cjs
node tools/quality/check.cjs --report docs/development/quality-report.md
```

The tool reads `web/dist/assets/*.js.gz` and `plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar`. Run
[`verify-all.cjs`](../../tools/build/README.md) first when those artifacts are absent. The tool does
not run builds, tests, network calls, or CI checks. `--report <path>` only writes the generated
report; it cannot inject measurements. A missing, ambiguous, unreadable, or over-budget artifact
produces a `NOT MEASURED` or `FAIL` result and a non-zero exit code.

## Initial targets

| Area | Gate | Current status |
|---|---|---|
| Core logic (sandbox, RBAC, auth, deploy state machine, snapshot) | Line coverage >= 90% and branch coverage >= 85% for security-critical packages; mutation testing on sandbox and RBAC where feasible | NOT MEASURED; implemented core modules have unit tests but no coverage report |
| Other Java modules | Line coverage >= 80% | NOT MEASURED |
| Web UI | Unit coverage >= 70%; all core workflows covered by E2E | NOT MEASURED; no product screens |
| CLI / SDKs | Line coverage >= 80% plus conformance suite | NOT MEASURED; skeletons only |
| Static analysis | Zero new high-severity findings; warnings treated as errors where practical | NOT MEASURED by this tool; linters and CodeQL pass on skeletons, but no product-code finding baseline is collected |
| Bundle sizes | Web UI gzip budget set after the size spike and enforced; plugin JAR size reported per PR with a regression threshold | Two local artifact budgets are enforced below; no CI regression gate added |
| Startup overhead | Plugin enable time under 1 second excluding optional network checks (initial target) | NOT MEASURED; no running-server timing after adapter wiring |
| Idle overhead | CPU and memory budget measured on a reference server profile and documented | NOT MEASURED; no reference profile |
| API stability | No breaking changes to released API versions | NOT MEASURED; no breaking-change check |
| Docs | Zero broken links; every public config key/permission/error code documented | NOT MEASURED; no link/key checker |
| i18n | Zero structural errors; stale/missing tracked and reported | NOT MEASURED by this report, although the separate catalog checker runs in CI |
| Flakiness | Retry-free green rate on `main` >= 98% over rolling 30 runs; flaky tests quarantined with issues | NOT MEASURED; no retry-free run history |

## Derived artifact budgets

These budgets are derived from the local baseline recorded for #118, with headroom. They are not
performance, support, or compatibility claims.

| Artifact | Enforced budget | Local measurement | Evidence |
|---|---:|---:|---|
| Web main JS gzip | <= 60000 bytes | 50632 bytes | [Embedded UI size spike (PR #210)](https://github.com/woxlo1/DeployMC/pull/210), accessed 2026-11-23 |
| Final plugin JAR | <= 5500000 bytes | 4721584 bytes | [Embedded UI size spike (PR #210)](https://github.com/woxlo1/DeployMC/pull/210), accessed 2026-11-23 |

The full spike will be at `docs/research/spikes/06-embedded-ui-size.md` after PR #210 merges. Until
then, PR #210 is the evidence link for the two local measurements.

Only these two budgets are measured by the reporting tool. Every other PART 41 area remains an
unmeasured signal, including coverage, E2E, static-analysis findings, startup, idle overhead,
API breakage, docs link/key checks, i18n gate results, and flakiness. The report prints
`NOT MEASURED` for those areas; it does not implement or verify those gates.

## Reporting rules

- Record the exact command, tool version, scope, and result for every measurement.
- Coverage numbers without a named scope and commit are not evidence.
- A red enforced budget blocks the quality report; do not weaken a threshold to make it pass without
  a documented, owner-approved reason.
- Budgets derived from spikes must keep the spike evidence link and access date.

The reporting tool is tracked by #119. The issue remains open until the final evidence PR records
verification. v1 acceptance also references this file from [v1-checklist.md](v1-checklist.md).
