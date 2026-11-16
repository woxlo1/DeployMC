# DeployMC

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
| Specifications (REST, WebSocket, configuration) | In progress (Phase 0) |
| Plugin runtime, API, Web UI, CLI, SDKs | Not started (Phase 1+) |

A more detailed, always current status is maintained in
[`docs/development/implementation-status.md`](docs/development/implementation-status.md).

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

The planned architecture is documented in [`docs/architecture/overview.md`](docs/architecture/overview.md).
The complete development specification lives in
[`docs/development/DeployMC_Complete_Master_Development_Specification.md`](docs/development/DeployMC_Complete_Master_Development_Specification.md).

## Non-goals

- DeployMC is not a general-purpose remote shell, SFTP server, or hosting panel.
- DeployMC does not manage the operating system, worlds, or server core files by default.
- DeployMC does not replace a backup system for worlds and player data.
- DeployMC does not make a hostile team member harmless. JAR deployment is code execution.

## Project status and documentation

The governance, contribution, and operations documents are being added in Phase 0. Until they are
merged, the authoritative source is the
[development specification](docs/development/DeployMC_Complete_Master_Development_Specification.md).
Development tracking documents live under [`docs/development/`](docs/development/).

## License

DeployMC is licensed under the [Apache License 2.0](LICENSE).

## Non-affiliation

DeployMC is not affiliated with Mojang Studios, Microsoft, or Cloudflare. Minecraft is a trademark
of Mojang Studios. Cloudflare and Cloudflare Access are trademarks of Cloudflare, Inc.
