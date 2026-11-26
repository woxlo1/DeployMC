# Specification Errata and Change Control

This file is the repository log for Part 39 of the
[master specification](DeployMC_Complete_Master_Development_Specification.md). The seed table in
Part 39.2 is normative; this file mirrors it and records later errata. No specification requirement
may be silently ignored.

## Change workflow

Use a `spec/<description>` branch and a reviewed pull request for every correction:

1. Record the problem: what the specification says, what reality or analysis shows, and the evidence
   (test output, commit, issue, or external source with an access date).
2. Propose the change in the `spec/` branch PR. Edit the master specification in
   `docs/development/`. If behavior changes, also edit the relevant `specification/` file and the
   relevant ADR in the same PR.
3. Add the new entry to the seed table in Part 39.2 and to the errata log below. Assign the next
   unused `E-NNN` identifier and keep earlier entries unchanged.
4. Notify the owner in Japanese with the reason and the impact. Record the notification and, where
   required, the owner's explicit approval in the PR.
5. If a requirement cannot be met as written, record it as an open gap with an issue in the
   [mandatory milestone consistency audit](project-status.md). Do not omit the check or treat the
   requirement as waived.

## Owner approval requirements

The following changes require explicit owner approval before a `spec/` PR is merged:

- Any change that weakens a security requirement, including removing authentication, adding a bypass
  flag, or relaxing sandbox containment.
- Any change that removes a deliverable, including a planned feature, work package, or acceptance
  gate.
- Any change to the legal or licensing stance, including the license choice, third-party license
  obligations, or distribution terms.

Until approval is recorded, the existing normative text remains in force. A PR must not rely on
implied approval, a bypass flag, or a fail-open exception. Attach the Japanese notification, the
reason, the impact, and the owner's approval to the PR record.

## Errata log

New errata are appended here and to the seed table in Part 39.2 of the master specification in the
same `spec/` PR.

| ID | Date | Change | Reason | Evidence |
|---|---|---|---|---|
| E-001 | (initial) | Added WP-1.9 minimal authentication; removed the Phase 2 "temporary authentication stub"; `/api/v1/server` is authenticated from 0.2.0 | Original draft exposed an unauthenticated endpoint in Phase 1, conflicting with 9.3 ("no unauthenticated mode") | Part 39.2 seed table |
| E-002 | 2026-11-25 | Added `specification/security/rbac.md` and reconciled the closed 23-permission registry, absent versus empty `paths.allow`, token owner mapping, `local-onetime` as an RBAC identity, deny-all on invalid reload, WebSocket close `4403`, and doctor `rbac` check | Issue #59 analysis found conflicts between the master algorithm, config schema, and token record contract; every resolution is a tightening or clarification and no bypass was added | Issue #59 spec analysis; `specification/security/rbac.md` |
| E-003 | 2026-11-26 | Clarified that `ExplainResult.reason` is a stable denial reason and is JSON `null` for an allow decision; the allow case is not a sixth reason | The implementation exposed a required enum that had no valid value for an allowed explain decision. Allowing `null` preserves the closed reason table and avoids inventing a denial reason | Issue #59 implementation analysis; `specification/security/rbac.md`; `specification/api/openapi.yaml` |
