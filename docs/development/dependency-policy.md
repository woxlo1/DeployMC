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
Dependabot alerts and security updates are separate from these version-update pull requests.

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
