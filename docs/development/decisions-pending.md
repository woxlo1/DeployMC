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

## Open

The following items do not block Phase 0 documentation and scaffolding but must be answered before
the related decisions are finalized.

| ID | Question | Needed by | Impact if unanswered |
|---|---|---|---|
| D-001 | What is the owner's real server hosting setup (bare Paper, Pterodactyl, Docker, systemd, Windows service) and which Minecraft versions are in use? | Phase 1 compatibility priorities | Tier 1 matrix order and restart-mode defaults may not match real usage |
| D-002 | Does the owner have a Cloudflare plan and a domain usable for Tunnel/Access verification? | Phase 3 Cloudflare guide | Parts of the guide can only be marked unverified |
| D-003 | Which distribution accounts exist (Modrinth, Hangar, SpigotMC, Maven Central, npm, PyPI, crates.io)? | Phase 7+ distribution | Publication steps cannot be executed or verified |
| D-005 | Should languages beyond Japanese be started before native reviewers exist? | Phase 0 i18n and later | Machine-translated content must be labeled and not presented as reviewed |
| D-006 | Should a Discord server be created now, or should Discussions be the only community channel initially? | Phase 0 community setup | Community links in the README remain Discussions-only |
| D-007 | What weekly time budget should the public roadmap assume? | Roadmap publication | Roadmap communication may be unrealistic |

## How to resolve a decision

1. Ask the owner in Japanese with the trade-offs and a recommendation.
2. Record the answer in this file with the date.
3. Create or update the relevant ADR if the decision is architectural.
4. Update affected documents in the same PR.
