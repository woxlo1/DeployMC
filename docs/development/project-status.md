# Project Status

Last updated: 2026-11-18 (Phase 1, WP-1.2 in PR #134). Verified against `main` at `ca2bd4b`.

This is the session-level status page. For per-component facts use
[implementation status](implementation-status.md); for intended design use the
[master specification](DeployMC_Complete_Master_Development_Specification.md).

## Current version and milestone

- Build version: `0.1.0-SNAPSHOT`.
- Foundation milestone: `0.1.0` (Phase 0) is complete. WP-0.7 was verified as already delivered
  by PR #18, and WP-0.10 was completed by PR #132; both issues were closed on 2026-11-18.
- Active milestone: `0.2.0` (Phase 1). Active work package: WP-1.2 (issue #42), minimal
  Bukkit-family adapter, open in PR #134 from `feature/wp-1.2-bukkit-adapter`. WP-1.1 was merged
  in PR #133 with issue #41 closed.
- Later milestones: `0.3.0` through `0.10.0` exist with the remaining specification work packages
  and category 3-5 work registered as issues #42-#130.

Verified GitHub state on 2026-11-18:

```text
git log -1 --oneline
ca2bd4b feat(core): add capability catalog and fake SPI platform (#133)

gh pr list --state open --limit 100
open PRs: 18   (#23-#39 Dependabot updates and WP-1.2 PR #134)

gh issue list --state open --limit 200
open issues: 89
```

## Known limitations

- DeployMC is pre-alpha. No DeployMC feature runs inside a Minecraft server.
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

Owner-directed order: #2, #7, #10, #41 (all done) -> category 2 (#42-#114, #42 in PR #134) ->
category 3 (#115-#120) -> category 4 (#121-#123) -> category 5 (#124-#130). Start the next item
only when earlier items are complete or the owner explicitly approves.

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
| Endpoints vs OpenAPI | Not applicable: no server implementation exists. Re-audit at WP-1.3/WP-2.6. |
| Config keys vs schema | Not applicable: no config loader exists. Re-audit at WP-1.5. |
| Permissions and error codes vs catalogs | Not applicable: no authorization code exists. Re-audit at WP-3.x. |
| README vs implementation status | Aligned: both describe buildable skeletons and no product features. |
| Support matrix vs CI | Aligned: matrix empty, no harness or support claim. |
| Translation catalogs | `node tools/i18n/check` reported 0 errors and 0 warnings. |
| ADR statuses | 14 ADRs reviewed: ADR-0003 and the editor part of ADR-0011 are Proposed, the rest Accepted; no contradiction with Phase 0 state found. Re-audit when code exists. |

Re-run the local toolchain checks with `node tools/build/verify-all.cjs`; CI remains the merge
authority. See [CI documentation](ci.md).
