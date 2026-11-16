# Governance

DeployMC is a small, early-stage project. This document describes how decisions are made **today**
and how the model is expected to grow. It will be revised before it no longer matches reality.

## Current model

DeployMC is maintained by its project owner, [@woxlo1](https://github.com/woxlo1), who is the final
decision maker while the project has a single maintainer. This is a benevolent-dictator model with a
strong preference for public reasoning: decisions are explained in issues, pull requests, or
architecture decision records (ADRs) so contributors can understand and challenge them.

There is no foundation, company, or committee behind the project. There is no voting body yet, and no
claim of community consensus where none exists.

## How decisions are made

| Decision type | Process |
|---|---|
| Routine bug fix, test, documentation, translation | Pull request review; merged when correct and green |
| New feature within an approved work package | Issue or short proposal first; maintainer approves scope |
| Architecture, dependency, storage format, API shape | ADR under `docs/adr/`, status `Proposed` then `Accepted` |
| Security-sensitive change | Private assessment where needed, tests first, coordinated disclosure per [SECURITY.md](SECURITY.md) |
| Breaking change | ADR or issue with migration notes; versioning policy followed |
| License, governance, or trademark change | Public proposal with a comment period; documented decision |

Routine pull requests use **lazy consensus**: if the maintainer has no objection and CI passes, the
change can merge. Silence is not approval for large or irreversible changes; those need an explicit
recorded decision.

## Reviews and merges

- Every change arrives through a pull request against a protected `main`. Direct pushes are disabled.
- CI checks are authoritative. A pull request that fails a required check is not merged.
- While the project has one maintainer, the maintainer may author and merge their own pull request
  after CI passes. This is stated openly rather than presented as independent review.
- When additional maintainers join, changes to code they own require a review from someone other than
  the author. `CODEOWNERS` will encode ownership when it is added.
- Merge strategy is rebase so history stays linear and each commit remains meaningful.

## Becoming a maintainer

There is no formal application process yet. The owner may invite a contributor to become a maintainer
after sustained, high-quality contributions, demonstrated care for security and user data, and
constructive review behavior. Maintainer status is expected to bring merge access, issue triage, and
participation in architectural decisions.

Contributors are credited in [AUTHORS.md](AUTHORS.md) with their consent. Contribution does not
automatically grant decision-making authority.

## Releases

- The maintainer decides when a milestone's acceptance criteria are met.
- Releases are tagged from `main`, built in CI, and published with checksums, an SBOM, and provenance
  once those pipelines exist.
- No release is published on a contributor's behalf without the owner's authorization.
- Release notes are written for humans and state compatibility, breaking changes, and security fixes
  honestly.

## Security decisions

Security reports follow [SECURITY.md](SECURITY.md). The maintainer may keep details private until a
fix is available. Security fixes are not delayed to bundle them with unrelated features, and severity
is assessed from impact rather than convenience.

## Conflicts of interest

The maintainer discloses any financial, employment, or personal interest that could affect a project
decision. Contributions from anyone are judged on technical merit and adherence to the project's
rules, not on relationship.

## Code of Conduct enforcement

The maintainer is the current enforcement contact described in [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
Enforcement decisions are made privately with the reporter, documented internally, and explained to
the affected party. The maintainer recuses from handling a report in which they are personally
involved, when a neutral third party is available.

## Changing this document

Governance changes are proposed as a pull request or issue with a clear rationale. Material changes
stay open for comment for at least 14 days before merge. The change is recorded in the pull request
history; no separate charter exists.

## What this project will not do

- Merge broken code or bypass required checks to look active.
- Fabricate contributors, reviews, stars, or activity.
- Promise compatibility or performance without measured evidence.
- Sell or transfer user data. DeployMC has no telemetry by default.
