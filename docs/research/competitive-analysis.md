# Competitive analysis

- **Prepared:** 2026-11-18
- **Scope:** published features and deployment patterns relevant to DeployMC. This is a desk
  review of public documentation, not a hands-on product test and not a benchmark.
- **Standard:** every factual claim has a source link and an explicit access date. Statements
  labeled **Assessment** are this project's interpretation, not claims from the cited source.

## Summary

**Assessment:** server panels already provide file management, backups, role controls, and scheduled
tasks; in-server plugin managers already provide enable/reload workflows. DeployMC therefore should
be positioned as a *complementary, in-plugin deployment and audit layer with a first-class API*,
not as a replacement panel. The differentiators in the design are a strict sandbox, snapshots and
diff, a hash-chained audit log, localization, and a documented public API. Those are design goals
only; none is a shipped feature as of 2026-11-18 (see
[implementation status](../development/implementation-status.md), read 2026-11-18).

## Server panels

| Tool and finding | Source | Accessed |
|---|---|---|
| Pterodactyl describes itself as a free, open-source game server management panel built with PHP, React, and Go, running each game server in an isolated Docker container. | [Pterodactyl](https://pterodactyl.io/) | 2026-11-18 |
| Pterodactyl Wings configuration exposes a dedicated SFTP port (default 2022) and a `system.sftp.read_only` setting. | [Wings configuration](https://docs.pterodactyl.io/v2/wings/configuration) | 2026-11-18 |
| Pterodactyl panel setup documentation includes running the Laravel scheduler every minute. | [Panel getting started](https://docs.pterodactyl.io/v2/panel/getting-started) | 2026-11-18 |
| Crafty Controller's product page describes creating and running servers, managing players, running commands, viewing and editing server files, and making backups. | [Crafty Controller](https://craftycontrol.com/) | 2026-11-18 |
| Crafty's documentation has a dedicated Server File Manager with upload, download, delete, unzip, rename, and create operations exposed through role permissions. | [Crafty file manager](https://docs.craftycontrol.com/pages/user-guide/file-manager), [Crafty user/role configuration](https://docs.craftycontrol.com/pages/user-guide/user-role-config) | 2026-11-18 |
| Crafty role configuration includes permissions for creating, deleting, downloading, restoring, and accessing backups. | [Crafty user/role configuration](https://docs.craftycontrol.com/pages/user-guide/user-role-config) | 2026-11-18 |
| AMP is described on its official site as a game server control panel for Windows and Linux; its documentation wiki includes a file manager. | [CubeCoders AMP](https://cubecoders.com/AMP), [AMP wiki](https://github.com/cubecoders/amp/wiki/Migrating-from-McMyAdmin-2-to-AMP) | 2026-11-18 |
| MCSManager describes itself as an open-source, distributed, out-of-the-box control panel supporting Minecraft and Steam game servers. | [MCSManager documentation](https://docs.mcsmanager.com) | 2026-11-18 |
| MCSManager documents a File Manager API and a `canFileManager` configuration option. | [MCSManager File Manager API](https://docs.mcsmanager.com/apis/api_fileManager.html), [MCSManager data and configs](https://docs.mcsmanager.com/ops/config_files.html) | 2026-11-18 |

**Assessment:** Pterodactyl, Crafty, AMP, and MCSManager cover the "MCSM-like panel" category for
this review. No additional panels were evaluated. Their file managers operate at the panel/container
or host-file level; DeployMC's planned sandbox and public API are the intended point of difference,
but that difference is not implemented or verified yet.

## In-server plugin managers

| Tool and finding | Source | Accessed |
|---|---|---|
| PlugManX lets administrators enable, disable, restart, load, reload, and unload plugins from in-game or console without restarting the server. | [PlugManX on Modrinth](https://modrinth.com/plugin/plugmanx) | 2026-11-18 |
| PlugManX lists Bukkit, BungeeCord, Folia, Paper, Purpur, Spigot, and Velocity among its platforms and states that all command permissions default to OP. | [PlugManX on Modrinth](https://modrinth.com/plugin/plugmanx) | 2026-11-18 |
| PlugManX is MIT-licensed and describes itself as a fork of PlugMan. | [PlugManX on Modrinth](https://modrinth.com/plugin/plugmanx) | 2026-11-18 |
| PlugMan supports downloading a plugin by URL, editing another plugin's config values, toggling plugins, and reloading plugins. | [PlugMan repository](https://github.com/notTamion/PlugMan) | 2026-11-18 |
| PlugMan is MIT-licensed. | [PlugMan repository](https://github.com/notTamion/PlugMan) | 2026-11-18 |

**Assessment:** these tools demonstrate demand for no-restart plugin control, but they use
server-specific plugin lifecycle behavior. DeployMC's non-negotiable rule is "no NMS or server
internals"; capabilities must be feature-detected and reported (see
[AGENTS.md](../../AGENTS.md) and the [platform SPI](../architecture/platform-spi.md), read
2026-11-18). Any similarity in feature names must not be read as an implementation claim.

## File workflows: SFTP, WebDAV, and panels

| Finding | Source | Accessed |
|---|---|---|
| RFC 4918 defines HTTP Extensions for Web Distributed Authoring and Versioning (WebDAV), was published in June 2007 as a Proposed Standard, and obsoletes RFC 2518. | [RFC 4918](https://www.rfc-editor.org/rfc/rfc4918) | 2026-11-18 |
| Pterodactyl Wings supports SFTP for server files, with a configurable port and a read-only mode. | [Wings configuration](https://docs.pterodactyl.io/v2/wings/configuration) | 2026-11-18 |
| Crafty exposes a browser file manager with upload/download/unzip/rename/create operations. | [Crafty file manager](https://docs.craftycontrol.com/pages/user-guide/file-manager) | 2026-11-18 |
| MCSManager exposes a documented File Manager API. | [MCSManager File Manager API](https://docs.mcsmanager.com/apis/api_fileManager.html) | 2026-11-18 |

**Assessment:** DeployMC's planned file API competes with established workflows. The design
response is to make every path go through a sandbox resolver, retain snapshots, and emit audit
events (see the [implementation status](../development/implementation-status.md) and
[threat model](../../specification/security/threat-model.md), read 2026-11-18). None of that
exists as working code yet.

## Git-based deployment and CI/CD

| Finding | Source | Accessed |
|---|---|---|
| GitHub Actions supports custom continuous deployment workflows that build and test code before deployment. | [GitHub Docs: Continuous deployment](https://docs.github.com/en/actions/get-started/continuous-deployment) | 2026-11-18 |
| CD workflows can be triggered by pushes, schedules, manual runs, or the `repository_dispatch` webhook. | [GitHub Docs: Continuous deployment](https://docs.github.com/en/actions/get-started/continuous-deployment) | 2026-11-18 |
| GitHub Actions environments support required reviewers, wait timers, deployment branch/tag restrictions, environment secrets, and custom protection rules; self-review can be prevented. | [GitHub Docs: Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments) | 2026-11-18 |
| On GitHub Free, Pro, and Team plans, required reviewers and wait timers are only available for public repositories, and environment secrets are only available in public repositories on the Free plan. | [GitHub Docs: Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments) | 2026-11-18 |
| Concurrency can limit a CD pipeline to one in-progress deployment. | [GitHub Docs: Continuous deployment](https://docs.github.com/en/actions/get-started/continuous-deployment) | 2026-11-18 |

**Assessment:** the planned GitHub Action and API should feed these existing gates rather than
replace them, with explicit owner approval for external publishing. The Action is not implemented
as of 2026-11-18.

## Cloudflare Access and Tunnel patterns

| Finding | Source | Accessed |
|---|---|---|
| Cloudflare Tunnel connects resources to Cloudflare without a publicly routable IP; `cloudflared` initiates outbound-only connections from the origin. | [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/) | 2026-11-18 |
| Access sends the application token in the `Cf-Access-Jwt-Assertion` header; browsers also pass a `CF_Authorization` cookie. Cloudflare recommends validating the header. | [Cloudflare: Validate JWTs](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json) | 2026-11-18 |
| Signing keys are published as JWKs at `https://<team-name>.cloudflareaccess.com/cdn-cgi/access/certs`; keys rotate by default every six weeks and remain valid for seven days after rotation. | [Cloudflare: Validate JWTs](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json) | 2026-11-18 |
| Validation must check the RS256 signature against the key matching the token's `kid`, plus issuer, application `aud`, `exp`, and `nbf`; a valid signature alone is insufficient. | [Cloudflare: Validate JWTs](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json) | 2026-11-18 |
| Access service tokens authenticate automated systems with a Client ID and Client Secret sent as `CF-Access-Client-Id` and `CF-Access-Client-Secret` headers; secrets can be rotated, disabled, and revoked. | [Cloudflare: Service tokens](https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/) | 2026-11-18 |

**Assessment:** the documented `cloudflare` authentication mode should validate the header against
the JWKS with an algorithm allow-list and full claim checks, and must fail closed when
configuration is missing. This is a requirement derived from the cited documentation, not evidence
that DeployMC implements it.

## Gaps in this review

| Gap | Basis | Accessed |
|---|---|---|
| No hands-on installation, performance measurement, or security test of any competing product was performed. | Method note at the top of this document. | 2026-11-18 |
| No conclusion is drawn about competitor licensing beyond the licenses explicitly stated on the linked pages. | [PlugManX](https://modrinth.com/plugin/plugmanx), [PlugMan](https://github.com/notTamion/PlugMan) | 2026-11-18 |
| "MCSM-like panels" are represented by the four panels listed above; other panels were not surveyed. | Source tables in this document. | 2026-11-18 |
