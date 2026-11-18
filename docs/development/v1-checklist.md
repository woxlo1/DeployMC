# v1.0.0 Acceptance Checklist

This checklist comes from Part 17 of the
[master specification](DeployMC_Complete_Master_Development_Specification.md). A box may be checked
only when the criterion is verified and an evidence link is attached. DeployMC is **not** v1.0.0
until every box is checked or the owner explicitly accepts a documented gap in the release notes.
All boxes are unchecked today (2026-11-18, pre-alpha).

## 17.1 Functionality

- [ ] Sandboxed file explorer/editor with server-side validation, optimistic concurrency, and
      protected paths. Evidence: not yet; #50-#51, #63-#65.
- [ ] Snapshots with diff and one-click restore, retention, and integrity verification.
      Evidence: not yet; #52-#53.
- [ ] JAR deployment pipeline with staging, validation, controlled restart, health verification,
      automatic rollback, crash recovery, and boot-loop protection. Evidence: not yet; #72-#80.
- [ ] Real-time console with filtering, commands, backpressure, and audit. Evidence: not yet;
      #81-#86.
- [ ] Authentication in `cloudflare`, `token`, and `local-onetime` modes; RBAC with path scopes;
      audit log. Evidence: not yet; #49, #56-#62.
- [ ] Public API v1 (REST, WebSocket, webhooks) fully specified, versioned, and conformance-tested.
      Evidence: not yet; skeletons only, #87-#89.
- [ ] Java API module, TypeScript/Python/Java clients, CLI, and GitHub Action released.
      Evidence: not yet; #90-#95.

## 17.2 Compatibility

- [ ] Support matrix generated from real CI results; every Tier 1 entry green on the latest run.
      Evidence: [empty matrix](../compatibility/support-matrix.md), harness #48.
- [ ] Paper (latest), Folia, and at least one proxy platform in Tier 1. Evidence: not yet;
      #42, #96, #101.
- [ ] Declared legacy range tested at the tiers claimed, with documented limitations.
      Evidence: not yet; #98.
- [ ] Java 8 baseline bytecode verified; verified to start on every declared Java version.
      Evidence: `verifyJava8` passes for class files; no server start test exists.

## 17.3 Security

- [ ] Threat model complete and reviewed; all security test suites green on Linux and Windows.
      Evidence: [draft threat model](../../specification/security/threat-model.md); #106, #116.
- [ ] No known unresolved critical/high issues; dependency audit clean or documented.
      Evidence: not yet; #121-#123.
- [ ] Signed releases, SBOM, provenance; `SECURITY.md` process exercised at least once in a drill.
      Evidence: not yet; #128, #130.
- [ ] Verified fail-closed behavior in all auth failure modes. Evidence: not yet; #56-#62.

## 17.4 Quality and Performance

- [ ] Coverage targets defined and met for security-critical modules. Evidence: targets in
      [quality](quality.md); measurement #119.
- [ ] Performance budgets met and documented; soak test passed. Evidence: not yet; #119.
- [ ] Accessibility checks pass; keyboard-only operation of core workflows. Evidence: not yet; #70.

## 17.5 Documentation and Localization

- [ ] README complete, accurate, with real screenshots/GIFs, in English and every declared
      translation. Evidence: [README](../../README.md) is honest but pre-alpha; no screenshots.
- [ ] Documentation site complete and versioned; guides for Cloudflare, Windows, Docker, CI.
      Evidence: not yet; #124, #126.
- [ ] Web UI and plugin messages complete in all declared languages (missing keys = 0; stale keys
      tracked). Evidence: [i18n status](i18n-status.md) covers 7 Web keys only; plugin messages do
      not exist yet.
- [ ] API reference, Javadoc, and SDK docs published. Evidence: not yet; #94.
- [ ] Contribution, translation, and governance docs complete. Evidence: present in repository;
      final v1 review still required.

## 17.6 Release and Community

- [ ] Release automation proven by at least two real pre-releases. Evidence: not yet; #129.
- [ ] Published to declared distribution channels. Evidence: none; requires owner approval.
- [ ] Issue forms, Discussions, labels, milestones, and good-first-issue backlog active.
      Evidence: issue forms/labels/milestones present; Discussions has default categories and zero
      threads; good-first-issue backlog not seeded.
- [ ] Public roadmap current. Evidence: [ROADMAP](../../ROADMAP.md) updated 2026-11-18.

## Evidence rules

- Link the exact CI run, test output file, or reviewed document for each item.
- Never substitute a design document for implementation evidence.
- Record any owner-accepted gap in the release notes with its rationale.
- The final evidence bundle is tracked in #130.
