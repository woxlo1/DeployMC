# Risk Register

Last updated: 2026-11-28. Seed risks come from Part 27 of the
[master specification](DeployMC_Complete_Master_Development_Specification.md). Likelihood and impact
are planning assessments, not measured probabilities. The project owner decides which residual
risks are accepted; see [v1-checklist.md](v1-checklist.md).

| ID | Risk | Likelihood | Impact | Mitigation direction | Owner | Status |
|---|---|---|---|---|---|---|
| R1 | JAR deploy abused for code execution by a compromised account | Medium | Critical | Cloudflare Access with MFA, RBAC, audit, rate limits, explicit documentation of limits | Maintainer / owner | Open; controls implemented, no running-server verification |
| R2 | Cloudflare misconfiguration exposes the port | Medium | Critical | Loopback default, JWT verification rather than header trust, doctor checks, tunnel verification checklist | Maintainer | Documentation and checklist added for WP-3.7; real Cloudflare compatibility remains blocked by D-002 |
| R3 | Classloader/library conflicts with other plugins | High | High | Minimal dependencies, mandatory relocation, JAR verification task, matrix tests with conflicting plugins | Maintainer | Partially addressed: Netty and SnakeYAML 2.7 are relocated and the final JAR is verified in `check`; real-server conflicting-library test remains in #48 |
| R4 | Legacy versions behave differently | High | High | Feature detection, capability reporting, tiered claims, harness evidence | Maintainer | Open; #48, #98 |
| R5 | Windows file locking breaks deployment | Medium | High | Staged replacement, update folder, Windows CI, explicit refusal paths | Maintainer | Open; #77, #118 |
| R6 | Restart not possible in some hosting setups | Medium | Medium | Restart modes, clear messaging, supervisor guides | Maintainer | Open; #76, #126 |
| R7 | Rollback itself fails or loops | Medium | Critical | Attempt limits, safe state, out-of-band recovery doc, fault-injection tests | Maintainer | Open; #78-#80 |
| R8 | Plugin JAR too large because of embedded UI | Medium | Medium | Bundle budget, lazy chunks, possible slim distribution variant | Maintainer | Partially addressed: CI reports the final JAR size (4.07 MiB); no numeric budget gate yet; #44, #118 |
| R9 | Folia threading violations | Medium | High | Scheduler abstraction, thread assertions in tests, Folia in Tier 1 | Maintainer | Open; #96 |
| R10 | Server software/API changes break compatibility | High | High | Scheduled new-version detection, matrix automation | Maintainer | Open; #99, #105 |
| R11 | Translation quality or staleness | Medium | Medium | Status tracking, labels, native review, CI staleness warnings | Maintainer / owner | Open; Japanese draft unreviewed; #125 |
| R12 | Scope too large for one maintainer | High | High | Strict phase gating, small PRs, tracking docs, contributor onboarding, honest roadmap | Owner | Mitigated by phase gating and issue backlog; re-audit each milestone |
| R13 | Supply-chain compromise | Medium | Critical | Pinned actions, dependency verification, signed releases, SBOM, provenance | Maintainer / owner | Partially addressed: pinned SHAs and dependency review in CI; #121-#123, #128 |
| R14 | Spec/code/README drift | Medium | High | Generated artifacts, consistency checks, CI drift gates | Maintainer | Partially addressed: [audit procedure](project-status.md#consistency-audit-procedure); no product code yet |
| R15 | Licensing conflicts with Bukkit/Spigot/Paper or bundled libraries | Medium | High | License ADR, dependency license scanning in CI | Maintainer / owner | Partially addressed: ADR-0013, [license comparison](../research/license-comparison.md), and Apache-2.0 evidence for SnakeYAML 2.7; no automated license scan gate yet |
| R16 | Hosting providers/panels restrict features | Medium | Medium | Standalone mode, capability reporting, guides per panel | Maintainer | Open; #103, #126 |
| R17 | Name/trademark conflicts (Minecraft, Mojang, Cloudflare marks) | Medium | Medium | Naming check, non-affiliation disclaimers, no misuse of logos | Owner | Open: [naming check](../research/naming-check.md) found exact-name repositories; no trademark search performed |

Update every risk at each milestone. Record new risks here in the same PR that introduces the
cause, and link the mitigating issue or evidence.
