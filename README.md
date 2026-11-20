# DeployMC

English · [日本語](docs/i18n/README.ja.md)

**Secure, collaborative plugin deployment and file management for Minecraft servers, with a first-class public API.**

> **Status: pre-alpha (0.1.0 foundation).** DeployMC is at the very beginning of development.
> The repository currently contains project documentation, specifications, and build scaffolding.
> Most user-facing features described below are **not implemented yet**. This README describes
> the intended product and states clearly what works today.

> **Security:** deploying a JAR is code execution on the server. DeployMC reduces operational risk
> but cannot make JAR deployment safe against a malicious account. Read the security model before
> granting deployment permissions.

## What works today

| Area | Status |
|---|---|
| Repository, governance, and development documentation | In progress (Phase 0) |
| Build skeletons for Java, Web, CLI, and the Python SDK | Buildable; configuration loader and embedded transport have implemented slices |
| Architecture baseline and architecture decision records | Drafted (Phase 0) |
| CI workflows and branch protection | Working on `main` |
| Specifications (REST, WebSocket, configuration, audit, deployment) | Normative drafts; implemented subsets are marked in `specification/README.md` |
| Authentication foundation | Host-provisioned bearer tokens with a fail-closed HTTP/WebSocket gate; no RBAC yet |
| Plugin runtime, API, Web UI, CLI, SDKs | A pinned Paper smoke run observes plugin enable, authenticated `/api/v1/server`, library isolation, and clean shutdown; no support tier or product feature set is claimed yet |

A more detailed, always current status is maintained in
[`docs/development/implementation-status.md`](docs/development/implementation-status.md).

> **Token permissions:** until 0.4.0 every valid token is **admin-equivalent**. Tokens are not
> scoped by role or path yet. Do not share a token between principals that need different
> permissions. See [token records](specification/security/token-records.md).

## Why DeployMC

Teams that develop a Minecraft server usually pass JAR files and YAML edits through one person who
controls the host. That person becomes a bottleneck, and the alternative - sharing SSH or SFTP
credentials - exposes the whole machine.

DeployMC aims to provide a narrow, auditable, authenticated layer for the plugin directory only:
edit files in a sandbox, deploy JARs as transactions with snapshots and rollback, watch the console,
and automate deployments from CI, without handing out host credentials.

Private keys, SSH access, and the host file system are explicitly out of scope.

## Planned capabilities

- Sandboxed file explorer and editor for the plugin directory
- Transactional JAR deployment with validation, controlled restart, health verification, and rollback
- Automatic snapshots with diff and one-click restore
- Real-time console streaming with filtering and policy-controlled commands
- Identity, RBAC, and an audit log; Cloudflare Access integration plus token modes
- A versioned REST/WebSocket API, Java API, SDKs, CLI, and a GitHub Action
- Multi-version and multi-platform compatibility, verified by a real CI compatibility matrix
- Documentation and interface localization, starting with English and Japanese

## Architecture

The planned architecture is documented in
[`docs/architecture/overview.md`](docs/architecture/overview.md), with companion documents for
the [platform SPI](docs/architecture/platform-spi.md),
[deployment state machine](docs/architecture/deployment-state-machine.md),
[threading model](docs/architecture/threading-model.md), and
[API layering](docs/architecture/api-layering.md). Significant decisions are recorded as
[architecture decision records](docs/adr/README.md), and the security posture is described in the
[draft threat model](specification/security/threat-model.md).

Normative specification skeletons live under [`specification/`](specification/): the
[REST API](specification/api/openapi.yaml), [WebSocket API](specification/api/asyncapi.yaml),
[error code catalog](specification/api/errors.md),
[configuration schema](specification/config/config.schema.json),
[audit log format](specification/audit/audit-log-format.md), and the
[formal deployment state machine](specification/deployment/state-machine.md), and the
[host-provisioned token record format](specification/security/token-records.md). They describe
the intended contracts; implemented subsets are marked in `specification/README.md`. They are
checked in CI by [`tools/spec/`](tools/spec/).

The complete development specification lives in
[`docs/development/DeployMC_Complete_Master_Development_Specification.md`](docs/development/DeployMC_Complete_Master_Development_Specification.md).

## Non-goals

- DeployMC is not a general-purpose remote shell, SFTP server, or hosting panel.
- DeployMC does not manage the operating system, worlds, or server core files by default.
- DeployMC does not replace a backup system for worlds and player data.
- DeployMC does not make a hostile team member harmless. JAR deployment is code execution.

## Compatibility

No Minecraft version, server platform, or Java runtime combination has been tested yet. DeployMC
intends to assign support tiers only from evidence produced by a real compatibility harness:

| Tier | Meaning | Current entries |
|---|---|---|
| Tier 1 | Tested on every pull request with a real server | none |
| Tier 2 | Tested on a schedule | none |
| Tier 3 | Best effort; may work but is untested | none |

Until then, treat every platform statement in this repository as a design goal, not a supported
configuration. The rules for earning a tier, the evidence each entry must record, and the blocking
gaps are defined in [`docs/compatibility/support-matrix.md`](docs/compatibility/support-matrix.md).

## Project status and documentation

The governance, contribution, and operations documents are being added in Phase 0. Until they are
merged, the authoritative source is the
[development specification](docs/development/DeployMC_Complete_Master_Development_Specification.md).
Development tracking documents live under [`docs/development/`](docs/development/).
Translation workflow and message-catalog rules are documented in
[`TRANSLATING.md`](TRANSLATING.md).

## Community and contributing

- [Contributing guide](CONTRIBUTING.md) — setup, build commands, commit and pull request rules
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security policy](SECURITY.md) — **report vulnerabilities privately, never in a public issue**
- [Support](SUPPORT.md) — where to ask, what to include, response targets
- [Community configuration](docs/development/community.md) — issue forms, labels, Dependabot, settings
- [Roadmap](ROADMAP.md) — phase order and milestone versions, not a schedule
- [Governance](GOVERNANCE.md) — how decisions are made today
- [Authors](AUTHORS.md) and [Changelog](CHANGELOG.md)

## License

DeployMC is licensed under the [Apache License 2.0](LICENSE).

## Non-affiliation

DeployMC is not affiliated with Mojang Studios, Microsoft, or Cloudflare. Minecraft is a trademark
of Mojang Studios. Cloudflare and Cloudflare Access are trademarks of Cloudflare, Inc.
