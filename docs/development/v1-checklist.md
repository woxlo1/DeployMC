# v1.0.0 Acceptance Checklist

This checklist comes from Part 17 of the
[master specification](DeployMC_Complete_Master_Development_Specification.md). A box may be checked
only when the criterion is verified and an evidence link is attached. DeployMC is **not** v1.0.0
until every box is checked or the owner explicitly accepts a documented gap in the release notes.
All boxes remain unchecked (2026-11-28, pre-alpha); partial evidence is noted where a work package
has landed.

## 17.1 Functionality

- [ ] Sandboxed file explorer/editor with server-side validation, optimistic concurrency, and
      protected paths. Partial: PR #153 (`SandboxResolver`) and PR #155 (`core.files` with revisions,
      atomic writes, and a fail-closed snapshot boundary) are merged and #50-#51 are closed. WP-2.5
      WP-2.5 has a normative validator contract, ADR-0016, a Java 8 validator, and a save-time gate.
      Authenticated REST file/validation adapters exist; no Web UI explorer exists yet (#63-#65).
- [ ] Snapshots with diff and one-click restore, retention, and integrity verification.
      Partial: PR #158 (contract) and PR #159 (`core.snapshot`) are merged and #52 is closed. The
      store has retention, verify/repair, GC, and reversible restore primitives. WP-2.4 merged its
      normative contract in PR #162 and the Java 8 `core.diff` engine in PR #163 with #53 closed, but
      no application-level one-click restore or live-server backup/restore exists yet.
- [ ] JAR deployment pipeline with staging, validation, controlled restart, health verification,
      automatic rollback, crash recovery, and boot-loop protection. Evidence: not yet; #72-#80.
- [ ] Real-time console with filtering, commands, backpressure, and audit. Evidence: not yet;
      #81-#86.
- [ ] Authentication in `cloudflare`, `token`, and `local-onetime` modes; RBAC with path scopes;
      audit log. Partial: all three modes, RBAC, and audit have implementations and server-free
      evidence (#56-#62 are closed), but no running server, browser, E2E run, full integration suite,
      or in-game command exercised them. Real Cloudflare compatibility remains unverified under D-002.
- [ ] Public API v1 (REST, WebSocket, webhooks) fully specified, versioned, and conformance-tested.
      Evidence: not yet; skeletons only, #87-#89.
- [ ] Java API module, TypeScript/Python/Java clients, CLI, and GitHub Action released.
      Evidence: not yet; #90-#95.

## 17.2 Compatibility

- [ ] Support matrix generated from real CI results; every Tier 1 entry green on the latest run.
      Evidence: [empty matrix](../compatibility/support-matrix.md), harness #48. Pinned Paper 26.3
      smoke entries passed on Ubuntu and Windows at `839b789`; no support tier is claimed.
- [ ] Paper (latest), Folia, and at least one proxy platform in Tier 1. Evidence: not yet;
      #42, #96, #101.
- [ ] Declared legacy range tested at the tiers claimed, with documented limitations.
      Evidence: not yet; #98.
- [ ] Java 8 baseline bytecode verified; verified to start on every declared Java version.
      Evidence: `verifyJava8` passes for class files; one pinned Paper 26.3 start on Java 25 was
      observed in the PR #151 smoke. No declared-version matrix exists yet.

## 17.3 Security

- [ ] Threat model complete and reviewed; all security test suites green on Linux and Windows.
      Evidence: [finalized v1 threat model](../../specification/security/threat-model.md) and the
      [secure-deployment contract](../../specification/security/secure-deployment.md) from #62; server-free
      evidence only, with D-001/D-002 limits. Running security review remains #106; suites #116.
- [ ] No known unresolved critical/high issues; dependency audit clean or documented.
      Evidence: not yet; #121-#123.
- [ ] Signed releases, SBOM, provenance; `SECURITY.md` process exercised at least once in a drill.
      Evidence: not yet; #128, #130.
- [ ] Verified fail-closed behavior in all auth failure modes. Partial: #56-#62 closed with
      server-free and embedded-socket tests; no running-server failure drill or browser E2E run.

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
      tracked). Evidence: [i18n status](i18n-status.md) covers 7 Web keys only. The Bukkit plugin
      runtime exists and has server-free catalog tests; see
      [plugin messages](../i18n/plugin-messages.md). Full-language coverage and native review remain
      outstanding.
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
