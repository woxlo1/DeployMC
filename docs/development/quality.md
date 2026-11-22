# Quality Gates

Last updated: 2026-11-18. Targets come from Part 41 of the
[master specification](DeployMC_Complete_Master_Development_Specification.md). They are signals to
be measured, not achievements to be claimed. Adjust them only with recorded evidence.

## Initial targets

| Area | Gate | Current status |
|---|---|---|
| Core logic (sandbox, RBAC, auth, deploy state machine, snapshot) | Line coverage >= 90% and branch coverage >= 85% for security-critical packages; mutation testing on sandbox and RBAC where feasible | Not measured; implemented core modules have unit tests but no coverage report |
| Other Java modules | Line coverage >= 80% | Not measured |
| Web UI | Unit coverage >= 70%; all core workflows covered by E2E | Not measured; no product screens |
| CLI / SDKs | Line coverage >= 80% plus conformance suite | Not measured; skeletons only |
| Static analysis | Zero new high-severity findings; warnings treated as errors where practical | Linters and CodeQL pass on skeletons; targets not yet applied to product code |
| Bundle sizes | Web UI gzip budget set after the size spike and enforced; plugin JAR size reported per PR with a regression threshold | Not set; spike tracked in #118 |
| Startup overhead | Plugin enable time under 1 second excluding optional network checks (initial target) | Not measured; no running-server timing after adapter wiring |
| Idle overhead | CPU and memory budget measured on a reference server profile and documented | Not measured; no reference profile |

## Reporting rules

- Record the exact command, tool version, scope, and result for every measurement.
- Coverage numbers without a named scope and commit are not evidence.
- A red gate blocks merge; do not weaken a threshold to make CI pass without a documented,
  owner-approved reason.
- Budgets derived from spikes must keep the spike evidence link and access date.

Implementation of the reporting gates is tracked in #119. v1 acceptance also references this file
from [v1-checklist.md](v1-checklist.md).
