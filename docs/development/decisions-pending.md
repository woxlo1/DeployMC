# Decisions Pending

This file tracks decisions that require the project owner's input. Resolved decisions are recorded
here with their date and outcome, then reflected in the relevant ADR or configuration.

## Resolved

| Date | Decision | Outcome |
|---|---|---|
| 2026-11-16 | License | Apache License 2.0 (ADR-0013) |
| 2026-11-16 | Java package and Maven group | `io.github.woxlo1.deploymc` |
| 2026-11-16 | Development machine work path | Keep the current OneDrive-synced path; document the risk |
| 2026-11-16 | AI co-author trailer | Use the OpenCode trailer when OpenCode contributes to a commit |
| 2026-11-16 | Development tracking | Use GitHub Issues, milestones, and labels |
| 2026-11-16 | Initial language priority | English canonical, Japanese first translation |
| 2026-11-17 | Branch protection and required checks | Enabled on `main`: linear history, no force-push or deletion, conversation resolution applies to admins, 15 required checks with `strict` (see [ci.md](ci.md)) |
| 2026-11-21 | D-008 structural-budget error mapping | Unified: every structural budget failure is top-level `validation.limit-exceeded` (413) with a format-specific issue code in `errors[]`; JSON depth moves from a top-level code to that shape. Recorded in the content-validation contract and error catalog. |
| 2026-11-25 | WP-3.3 token hashing | Store SHA-256 over the complete 256-bit random token. A password KDF or separate HMAC key adds no meaningful preimage resistance for this secret and would add key-rotation failure modes. Existing SHA-256 records remain valid. |
| 2026-11-25 | WP-3.3 scope enforcement | Store and propagate `scopes` as `token.scopes` in WP-3.3, but do not enforce them until the WP-3.4 RBAC engine exists. Every valid token remains admin-equivalent until then. |
| 2026-11-25 | WP-3.3 local one-time token | Keep the token in memory only, print it once to the console, require loopback unless `bind.allow-non-loopback: true` is explicit, and bound it by TTL and failed-attempt limits. Restart invalidates it; there is no fallback to another auth mode. |

## Open

The following items do not block Phase 0 documentation and scaffolding but must be answered before
the related decisions are finalized.

| ID | Question | Needed by | Impact if unanswered |
|---|---|---|---|
| D-001 | What is the owner's real server hosting setup (bare Paper, Pterodactyl, Docker, systemd, Windows service) and which Minecraft versions are in use? | Phase 1 compatibility priorities | Tier 1 matrix order and restart-mode defaults may not match real usage |
| D-002 | Does the owner have a Cloudflare plan and a domain usable for Tunnel/Access verification? | Real Cloudflare verification and the Phase 3 Cloudflare guide | WP-3.2 local implementation and fixture verification are complete, but real Access compatibility and plan-specific guidance stay unverified |
| D-003 | Which distribution accounts exist (Modrinth, Hangar, SpigotMC, Maven Central, npm, PyPI, crates.io)? | Phase 7+ distribution | Publication steps cannot be executed or verified |
| D-005 | Should languages beyond Japanese be started before native reviewers exist? | Phase 0 i18n and later | Machine-translated content must be labeled and not presented as reviewed |
| D-006 | Should a Discord server be created now, or should Discussions be the only community channel initially? | Phase 0 community setup | Community links in the README remain Discussions-only |
| D-007 | What weekly time budget should the public roadmap assume? | Roadmap publication | Roadmap communication may be unrealistic |
| D-009 | Which dispositions should be applied to the 17 triaged Dependabot PRs, especially close candidates #27/#35 and rebase-then-merge candidates #24/#36/#38? | Before any dependency PR is merged, closed, or rewritten | Failing gates stay red and no dependency PR can be changed safely |

WP-3.2 (issue #57) is implemented and fixture-verified; D-002 now blocks only real Cloudflare tenant
validation and plan-specific guide claims, not local implementation.

See [dependabot-triage.md](dependabot-triage.md) for per-PR evidence. The triage is read-only; no PR
was merged, closed, rebased, or approved. #27 and #35 require JUnit 6 on a Java 8 project, so they
stay open until the owner decides whether to close them or change the Java baseline.

The PART 40.3 [restart-behavior spike](../research/spikes/07-restart-behavior.md) is blocked by D-001:
without the real hosting setup and versions, no systemd, Docker, loop-script, or Pterodactyl result
can be recorded. The [update-folder](../research/spikes/03-update-folder-semantics.md) and
[log-capture](../research/spikes/04-log-capture.md) spikes likewise leave oldest/newest target
versions untested. Those capability values remain `UNKNOWN`.

## How to resolve a decision

1. Ask the owner in Japanese with the trade-offs and a recommendation.
2. Record the answer in this file with the date.
3. Create or update the relevant ADR if the decision is architectural.
4. Update affected documents in the same PR.
