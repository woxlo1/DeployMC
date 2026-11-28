# Security Policy

## Project status

DeployMC is **pre-alpha**. There is no supported production release yet, and no version has been
published to a distribution channel. Security reports are still welcome and are treated seriously.

## Supported versions

| Version | Supported | Notes |
|---|---|---|
| `0.1.x` (unreleased, `main`) | Best effort | Pre-alpha foundation; fixes land on `main` |
| Older or unlisted versions | No | No release exists before `0.1.0` |

This table will be updated when the first release is published. Until then, do not deploy DeployMC to
a server you are not prepared to rebuild.

## Reporting a vulnerability

**Do not open a public issue, pull request, or discussion for a suspected vulnerability.**

Use GitHub's private vulnerability reporting:

1. Open <https://github.com/woxlo1/DeployMC/security/advisories/new>.
2. Describe the issue, affected component and version (or commit), and impact.
3. Include reproduction steps or a proof of concept if you have one.

Private vulnerability reporting is enabled for this repository. If the form is unavailable to you,
contact the maintainer [@woxlo1](https://github.com/woxlo1) privately through GitHub without posting
details publicly.

### What to include

- Affected component (`core`, `api`, `transport`, `platform/*`, `web`, `cli`, `sdk/*`, `actions`).
- Version, commit, operating system, Java version, and platform where you reproduced it.
- Security impact in your assessment: what an attacker gains and under which preconditions.
- Reproduction steps, configuration, and logs with tokens, hostnames, and personal data removed.
- Whether the issue is already public anywhere.

### What to expect

These are targets for a small unfunded project, not contractual guarantees:

| Stage | Target |
|---|---|
| Acknowledgement of the report | 7 days |
| Initial assessment and severity | 14 days |
| Fix or mitigation plan for critical/high issues | 30 days |
| Fix or mitigation plan for medium/low issues | 90 days |
| Default coordinated disclosure window | 90 days from acknowledgement |

We will keep you informed, credit you in the advisory if you want, and coordinate disclosure timing
with you. If a fix takes longer, we will explain why rather than let the report expire silently.

## Scope

### In scope

- Sandbox escapes: path traversal, symlink or junction escape, Windows path quirks, protected-path
  bypass.
- Authentication and authorization flaws: token handling, Cloudflare Access JWT verification,
  RBAC bypass, session or CSRF issues, fail-open behavior.
- Request handling: header spoofing, Host/Origin validation, request smuggling, resource exhaustion,
  oversized uploads.
- Archive handling: zip-slip, zip bombs, malformed JAR processing, YAML bombs.
- Secret exposure: tokens or credentials in logs, API responses, error messages, snapshots, or the
  file API.
- Deployment integrity: rollback bypass, snapshot tampering, audit-log tampering, race conditions.
- Dependency or build-chain issues that affect DeployMC artifacts.
- Cross-site scripting, unsafe rendering, or console/log injection in the Web UI.

### Out of scope

- A compromised server host, server operator account, GitHub account, or Cloudflare account.
- Social engineering of maintainers or contributors.
- Vulnerabilities in Minecraft, Mojang/Microsoft services, Cloudflare, or third-party plugins.
- Denial of service that requires the ability to stop the server or saturate the host network.
- Self-XSS and issues that require the victim to paste attacker-controlled code into their own
  browser console.
- Missing hardening headers or version banners without a demonstrated impact.
- Reports produced only by an automated scanner without a reproducible impact.

## Security model in brief

Deploying a JAR is **code execution on the server**. DeployMC reduces operational risk by limiting
access to a plugin directory, authenticating every request, authorizing per path and action, auditing
changes, and keeping snapshots. It does not make a malicious authorized account harmless, and it does
not protect the host outside the sandbox.

Default expectations for every phase:

- No unauthenticated endpoint except `/healthz` and `/readyz`. No bypass flags. Fail closed.
- Bound to loopback by default; exposing a non-loopback address requires explicit configuration and a
  warning.
- Cloudflare Access JWTs are verified, never trusted from headers alone.
- Tokens are stored hashed, compared in constant time, and redacted from logs.
- All file access goes through the sandbox resolver.

The [threat model](specification/security/threat-model.md) is finalized for the v1 security model.
Operators should also read the [administrator security guide](docs/guides/security.md) and the
[secure deployment contract](specification/security/secure-deployment.md).

## Safe harbor

We support good-faith security research. If you make a reasonable effort to avoid privacy violations,
data destruction, and service disruption, and you report promptly and privately, we will not pursue
legal action or report you to platform operators for your research. This statement does not authorize
testing against servers or accounts you do not own or have explicit permission to test.

## Hardening guidance for operators

Before any production use, follow the [administrator security guide](docs/guides/security.md).
Keep DeployMC bound to loopback behind Cloudflare Tunnel and Access, grant the minimum roles,
keep snapshots enabled, and review the audit log. Treat the Cloudflare example as unverified until
its checklist has been completed on the real setup. Never expose the plugin directory through a
second channel such as SFTP or a web file manager.
