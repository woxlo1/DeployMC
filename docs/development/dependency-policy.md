# Dependency Policy

Last updated: 2026-11-23 (Asia/Tokyo).

This page defines how DeployMC selects, pins, reviews, updates, and exempts dependencies, and how it
responds to dependency advisories. It applies to runtime and build dependencies, toolchain versions,
and third-party GitHub Actions. It complements [SECURITY.md](../../SECURITY.md),
[GOVERNANCE.md](../../GOVERNANCE.md), [CI and enforced checks](ci.md), and
[community configuration](community.md). The normative background is Part 3.3, Part 4.1, Part 9.7,
and Part 19.8 of the [master specification](DeployMC_Complete_Master_Development_Specification.md),
plus supply-chain risk A12 in the [threat model](../../specification/security/threat-model.md).

This is policy and recorded practice, not evidence that every planned control exists. Gaps are listed
at the end of this page. No product code, CI behavior, or user-facing strings were changed by this
document.

## Scope and principles

- Security and data safety come before convenience. A dependency change that weakens a security
  default, adds a bypass flag, or exposes data is rejected unless an owner-approved ADR explains why
  and how the risk is contained.
- Keep dependencies minimal. Prefer the JDK or standard library when it is sufficient; every added
  dependency increases review, supply-chain, compatibility, and maintenance surface.
- Deploying a JAR is code execution on the server. Static checks and runtime controls reduce risk but
  cannot make a malicious authorized actor or a compromised bundled library harmless.
- Every request remains authenticated except `/healthz` and `/readyz`; there is no unauthenticated
  mode or development bypass flag. Dependency changes must preserve that security model.
- No secrets, real tokens, or real hostnames belong in dependency manifests, issues, or evidence.
  Use obviously fake examples in documentation and tests.

## Selecting a new dependency

Before adding a direct dependency or upgrading a third-party action, record the evaluation in the
pull request. Architecture-significant choices require an ADR under `docs/adr/` with status
`Proposed` and then `Accepted`, following [GOVERNANCE.md](../../GOVERNANCE.md).

| Criterion | Requirement |
|---|---|
| Necessity | Explain why the standard library or an existing dependency is insufficient. |
| License | Confirm compatibility with the Apache-2.0 project and with shading or redistribution. Link the license evidence. |
| Maintenance | Check recent releases, responsiveness, and whether the project is archived or abandoned. |
| Security record | Check the GitHub Advisory Database, upstream advisories, and known security practices. Do not rely only on package popularity. |
| Size and scope | Prefer a small, focused library. Record the final plugin-JAR size effect when the dependency is bundled. |
| Java baseline | A dependency in the plugin JAR must support Java 8 bytecode; check the built artifact, not only a README. |
| Classloaders | Bundled Java dependencies are relocated under the project prefix and checked by final-JAR verification. |
| Compatibility | Check every supported platform and OS path in the compatibility matrix; do not claim support from a successful build alone. |
| Reproducibility | Prefer an exact version or a committed lock entry that CI installs without resolution drift. |
| User-facing text | Use the i18n catalogs for any string the dependency causes DeployMC to display. Do not add hard-coded UI strings. |

Server internals (NMS and CraftBukkit internals) are not dependencies to adopt. Use stable APIs and
runtime capability detection. A dependency that would make a bypass flag, hidden endpoint, or
unauthenticated diagnostic necessary is rejected.

## Pinning and sources of truth

Manifests declare intent; committed lockfiles or catalogs record the resolved graph. CI must install
from those records rather than resolving floating versions at build time.

| Area | Source of truth | Current practice |
|---|---|---|
| Gradle Java modules | `gradle/libs.versions.toml` and the Gradle wrapper | Versions are centralized in the catalog and the wrapper pins Gradle. Gradle dependency verification metadata is **not** configured yet. |
| Web UI | `web/package.json` and `web/pnpm-lock.yaml` | Direct versions are exact; `pnpm install --frozen-lockfile` enforces the lockfile. |
| Specification tools | `tools/spec/package.json` and `tools/spec/package-lock.json` | Direct versions are exact; CI uses `npm ci`. |
| CLI | `cli/Cargo.toml` and `cli/Cargo.lock` | The workspace records constraints and the committed lockfile records the resolved graph. |
| Python SDK | `sdk/python/pyproject.toml` | Runtime dependencies are empty; development tools use bounded ranges. There is no committed pip lockfile. |
| GitHub Actions | `.github/workflows/*.yml` | Third-party actions are pinned by full commit SHA, with least-privilege `permissions`. Dependabot proposes SHA updates. |

Do not use `latest`, a moving branch, or an unpinned tag for a shipped build or third-party action.
A constrained range is acceptable only where the ecosystem or build tool requires it and the pull
request explains the bound. Manifest and lockfile changes must land together; a PR that changes one
without the other is incomplete unless the ecosystem has no lockfile.

## Review gates

Every dependency change enters through a pull request against protected `main`. Required checks are
strict, so the branch must be current before merge. The current set includes the Java, Web, CLI,
Python, specification, i18n, compatibility, commit-message, pull-request-title, CodeQL, and
`Dependency review` checks. Conversation resolution is required and the rules apply to
administrators; linear history is enforced. Checks are not bypass flags and must not be disabled to
land an update.

The `Dependency review` workflow uses the SHA-pinned `actions/dependency-review-action` with
`fail-on-severity: high` and comments on failure. Dependency review compares added and updated
dependencies in the pull request, including changes visible through lockfiles. The dependency graph
and Dependabot alerts are enabled; CI fails closed if the graph is unavailable. The Security tab is
authoritative for current advisories; this document deliberately does not freeze an advisory count.

A reviewer checks, at minimum:

1. The manifest and lockfile diff, including transitive additions and removals.
2. Upstream release notes or changelog, known breaking changes, and the fixed version for any
   advisory.
3. License evidence, Java baseline, shading/relocation, plugin-JAR size, and platform impact.
4. New workflow permissions, action SHAs, network destinations, or build-time execution.
5. The exact verification commands and results recorded in the pull request. A green check is not a
   substitute for reading what changed.

Dependabot pull requests are triaged individually; do not merge in bulk. Rolling back a dependency
requires the same review and verification as adding it.

## Routine updates and guarded auto-merge

[`.github/dependabot.yml`](../../.github/dependabot.yml) proposes weekly version updates for Gradle,
npm in `web/`, npm in `tools/spec/`, Cargo, pip, and GitHub Actions. Each ecosystem allows at most
five open pull requests, applies `type:chore`, and uses the `chore(deps)` commit prefix. The schedule
is Monday for Gradle and npm, Tuesday for Cargo and pip, and Wednesday for Actions in Asia/Tokyo.
Dependabot alerts are separate from these version-update pull requests; any security-update pull
request that GitHub creates is evaluated by the same workflow.

Patch and minor updates are normally routine when they pass every gate. Major versions, new direct
dependencies, action or permission changes, and anything with a possible breaking change require
explicit owner review. Cross-reference the relevant Dependabot pull request and advisory or issue
instead of opening a duplicate change.

Repository auto-merge was enabled when this policy was written; the setting was checked through the
GitHub API on 2026-11-23 (`allow_auto_merge: true`). It is deliberately guarded, not blanket
approval:

- A maintainer must first read the change and deliberately enable auto-merge for that pull request.
- Every required check, including `Dependency review`, must pass, the branch must be current, and
  conversation resolution must be satisfied. Required checks are authoritative; auto-merge cannot
  bypass them.
- Eligible changes are low-risk patch or minor updates with no known high or critical advisory, no
  new direct dependency, no unexpected transitive change, and no workflow permission or action
  security change.
- Security fixes, major upgrades, new direct dependencies, release-branch changes, and any change
  with an open exemption or unresolved advisory are merged manually after review.
- A push by someone without write access disables auto-merge. If the maintainer is unsure, removes
  a guard, or sees a new failing check, disable auto-merge and reassess.

Enabling auto-merge is not an approval, an endorsement of safety, or permission to skip release
notes. No workflow was added to auto-merge Dependabot pull requests by this policy.

## Exemptions

An exemption is a recorded, time-limited decision, never a silent suppression. The owner decides
whether to accept a risk under [GOVERNANCE.md](../../GOVERNANCE.md). Every exemption must name the
package, exact version, advisory or update, affected branch, rationale, compensating control, review
date, and expiry. Record it in the pull request or a security issue and, for an accepted product
risk, in `docs/development/risks.md` or `docs/development/decisions-pending.md`.

- **Advisory exemption:** permitted only after the verification workflow below establishes why the
  vulnerable code is unreachable or why a compensating control prevents exploitation. High and
  critical exemptions require explicit owner approval. Do not add a blanket advisory allowlist or
  dismiss an alert solely because a scanner or a maintainer believes the code is unused.
- **Version pin exemption:** an older version may be retained for Java baseline, compatibility, or
  upstream regression reasons. Record the blocking issue, the safe version to move to, and the
  condition for removing the pin.
- **Update ignore:** a Dependabot `ignore` rule must have an expiry and issue reference. Never use
  one to avoid triage or to hide a security update without an approved advisory exemption.

No exemption may disable a required check, weaken branch protection, mask a failed dependency
review, or authorize a public disclosure of private report details. Reassess every exemption at each
release milestone and whenever the affected dependency, reachability, or advisory changes.

## Verifying advisories against official sources

Every advisory is verified before it is dismissed, exempted, or fixed. Record the following in the
security issue or remediation pull request:

1. Capture the GitHub Security Advisory identifier (`GHSA-...`), any CVE identifier, ecosystem,
   package name, affected and fixed versions, reported severity, alert URL, and the date the source
   was accessed.
2. Verify the advisory in the [GitHub Advisory Database](https://github.com/advisories) and, when a
   GHSA page exists, on that canonical GHSA page. Cross-check the CVE record when one is listed and
   the upstream maintainer's official advisory or release notes. For package-specific guidance, also
   check the package registry's advisory entry when the registry provides one.
3. Resolve the version actually used by each affected module from the committed catalog or lockfile.
   Distinguish direct from transitive, runtime from development-only, and bundled/shaded from
   build-only dependencies. Check whether the vulnerable code path is reachable from an authenticated
   request, a build step, or a shipped artifact.
4. Record each source URL with an access date in `YYYY-MM-DD` format (Asia/Tokyo), the evidence that
   the affected version is present or absent, and the conclusion. A link without an access date is
   not sufficient evidence for an exemption.
5. If official sources conflict, the advisory is unreviewed, or a source is unavailable, do not
   declare a false positive. Treat the finding as at least medium, escalate to the owner, and keep
   the alert open until the evidence is reconciled.
6. Follow [SECURITY.md](../../SECURITY.md) for private reporting and coordinated disclosure. Do not
   post private report details, tokens, hostnames, or a working exploit in a public issue or pull
   request; use the private advisory or a redacted reproduction.

Dependabot alerts are generated from GitHub-reviewed advisories and supported ecosystems, so the
absence of an alert is not proof that a dependency is safe. Review new direct dependencies during
selection and keep manifests and lockfiles current for accurate detection.

## Severity and response expectations

Severity starts with the canonical advisory rating. The assessed severity may increase when the
vulnerable path is reachable from an authenticated request, a shipped plugin JAR, or a privileged
build step; it may decrease only with documented evidence of unreachability or an effective
compensating control and owner approval. GitHub labels may use "Moderate"; this policy treats that
as "Medium". CI fails on high or critical findings introduced by a pull request.

| Severity | Internal response expectation | Merge and release rule |
|---|---|---|
| Critical | Notify the owner promptly, verify within the current work session where possible, and seek a fix or mitigation plan within the 30-day target in `SECURITY.md`. | No release with an unmitigated critical finding in a supported artifact. A public issue must not disclose a private report before coordination. |
| High | Verify and triage promptly, cross-reference any Dependabot pull request, and seek a fix or mitigation plan within the 30-day target in `SECURITY.md`. | A pull request that introduces a high advisory fails dependency review. Do not merge or release a supported artifact with an unmitigated high finding without an owner-approved exemption. |
| Medium | Verify in the normal triage cycle and seek a fix or mitigation plan within the 90-day target in `SECURITY.md`. | Fix in a routine dependency pull request. The owner decides whether a release may proceed with a documented, non-reachable finding. |
| Low | Verify and batch with routine updates; record any decision not to upgrade. | No automatic release block, but the advisory and rationale remain recorded. |
| Unknown or unreviewed | Treat as at least Medium until the official sources are reconciled. Escalate if a credible source indicates higher impact. | No exemption and no auto-merge while verification is incomplete. |

These are targets for a small unfunded project, not contractual guarantees. The acknowledgement,
initial assessment, and coordinated-disclosure targets in `SECURITY.md` govern externally reported
vulnerabilities; this table applies the same fix or mitigation-plan expectations to dependency
advisories. If a target will be missed, explain why in the private advisory or issue rather than
silently changing the severity.

## Advisory response workflow

1. **Detect and record.** Start from the Security tab alert, a Dependabot pull request, CodeQL or
   CI failure, an upstream notice, or a private report. Open a security issue or draft advisory and
   record the identifiers and sources before changing code.
2. **Verify and assess.** Follow the verification steps above. Determine affected versions,
   reachability, severity, supported branches, and whether a fixed version exists.
3. **Contain.** If the product is already affected, preserve evidence and consider a private fix
   fork. Use a documented owner-approved mitigation when no fixed version exists; never disable an
   endpoint, authentication check, sandbox rule, or required CI gate as an undeclared workaround.
4. **Remediate.** Prefer upgrading to the fixed version. If that is impossible, replace the
   dependency, remove the affected code path, or apply a narrowly scoped upstream patch with an ADR.
   A downgrade is acceptable only to a known-safe version and with an expiry to upgrade again.
5. **Verify the fix.** Open a focused pull request that updates manifests and lockfiles together,
   includes regression evidence for the vulnerable behavior where practical, and records the exact
   commands and results. Run the relevant language toolchain, dependency review, and the full
   required check set before merge. Do not bundle unrelated changes with a security fix.
6. **Confirm closure.** After merge, confirm that the dependency graph reflects the fixed version on
   `main` and that CI is green. Close or resolve the tracking item with the advisory identifier,
   fixed commit, verification evidence, and any remaining risk. Reopen triage if the graph still
   reports the affected version.
7. **Disclose.** Follow the 90-day default coordinated-disclosure window from `SECURITY.md`, unless
   the reporter and maintainer agree otherwise. Release notes state the security fix honestly
   without exposing private details or invented impact.

## Security fixes on release branches

Today `SECURITY.md` supports only unreleased `0.1.x` from `main` on a best-effort basis; there are no
older releases to backport to. When release branches exist, this policy applies to every branch
listed as supported in `SECURITY.md`.

- Fix and verify on `main` first when disclosure timing allows. Cherry-pick the focused fix to each
  supported release branch; do not copy unrelated `main` changes into a security backport.
- Under coordinated disclosure, a private advisory fork may hold the fix until supported branches
  are ready. Merge or cherry-pick the same reviewed change to `main` and each supported branch, then
  tag the patched releases before publication.
- A release-branch security pull request uses the same required checks and verification evidence as
  `main`, plus a version or changelog entry and the advisory identifier. If a check is unavailable
  for an old branch, record that honestly and require owner approval; never disable a gate.
- Unsupported branches receive no backport by default. An exception needs an owner decision in the
  advisory and an update to `SECURITY.md` if the branch becomes supported.
- Release tags are built in CI. Signatures, SBOMs, provenance, and checksums are planned in
  [GOVERNANCE.md](../../GOVERNANCE.md) and Part 9.7; do not claim they exist for a branch until the
  release pipeline produces and verifies them.

## Evidence and official references

Official documents were checked on 2026-11-23 (Asia/Tokyo). The access date records when this policy
was verified; it is not a claim that a remote document is unchanged since then.

| Source | Used for | URL | Accessed |
|---|---|---|---|
| GitHub: Dependabot alerts | Alert scope, reviewed advisories, and limits | https://docs.github.com/en/code-security/dependabot/dependabot-alerts/about-dependabot-alerts | 2026-11-23 |
| GitHub: Dependency review | Pull-request dependency diff and action enforcement | https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review | 2026-11-23 |
| GitHub: Repository security advisories | Private fix workflow and publication | https://docs.github.com/en/code-security/security-advisories/about-github-security-advisories | 2026-11-23 |
| GitHub: Dependabot version updates | Version versus security updates and release notes review | https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/about-dependabot-version-updates | 2026-11-23 |
| GitHub: Managing auto-merge | Repository setting and per-pull-request behavior | https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-auto-merge-for-pull-requests-in-your-repository | 2026-11-23 |
| GitHub Advisory Database | Canonical reviewed and unreviewed advisory records | https://github.com/advisories | 2026-11-23 |

Repository evidence is the current `SECURITY.md`, `.github/dependabot.yml`,
`.github/workflows/dependency-review.yml`, branch-protection settings, and the master specification.
Repository settings were checked with the `gh` CLI on the same date.

## Current gaps

State these gaps honestly in reviews and release notes:

- Gradle dependency verification metadata and a documented full lockfile policy are not configured.
- Python development dependencies use bounded ranges and have no committed lockfile.
- SBOM generation, artifact signing, provenance, and release checksum pipelines are planned, not
  implemented.
- No release branch or backport has been exercised; there is no published release before `0.1.0`.
- Auto-merge is a repository setting with procedural guards, not a custom workflow that enforces the
  eligibility checklist.
- No running server, full integration suite, browser, or in-game command verifies this document; it
  is a policy change only.
