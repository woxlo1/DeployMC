# Administrator security guide

This guide is documentation, not evidence of a tested production deployment. Start with the
[finalized threat model](../../specification/security/threat-model.md), then apply the checklist
below. The central limit is unchanged: **a user authorized to deploy a JAR can execute code as the
server**. RBAC and approvals reduce accidents; they do not make that user harmless.

The [Cloudflare Tunnel + Access example](../../examples/cloudflare-tunnel/README.md) is marked
**unverified deployment** until an operator completes its verification checklist. No Cloudflare plan
or account setup is claimed under unresolved owner decision D-002.

## Hardening checklist

| Item | What to do | Product status |
|---|---|---|
| Loopback bind | Keep `bind.address: 127.0.0.1`; require explicit justification for `allow-non-loopback` | Enforced by config loader; WP-3.6 protects forwarded-peer parsing |
| Access MFA | Require MFA for interactive Access policies | Operator action in Cloudflare; DeployMC does not configure Access |
| Least-privilege roles | Grant the narrowest RBAC roles and path scopes | Java 8 evaluator enforced on REST and WebSocket |
| Short token TTLs | Use short DeployMC token durations and revoke unused tokens | WP-3.3 lifecycle; console create/revoke/list implemented |
| JAR approvals | Require a second actor to approve JAR deployment | Planned policy option; do not claim as implemented |
| Hash allowlist | Use an allowlist for production JARs | Planned; static validation cannot prove a JAR is safe |
| Separate staging server | Test plugins and configuration off the production host | Operator action; no product support tier claimed |
| Snapshot verification | Run `deploymc snapshot verify` on a schedule | Core verifier and REST route implemented; no scheduler |
| Audit forwarding | Export and retain the hash-chained audit log off-host | Export route implemented; off-host anchor is not |
| World backups | Keep world backups as a separate system | Outside DeployMC scope |
| Updates | Keep DeployMC and the server updated; review advisories | Operator action |
| Security advisories | Subscribe through the repository Security policy | See [SECURITY.md](../../SECURITY.md) |

## Network and proxy rules

- `/healthz` and `/readyz` are the only unauthenticated endpoints; there is no bypass flag.
- DeployMC accepts only `Cf-Access-Jwt-Assertion` in Cloudflare mode. No cookie session or CSRF token
  exists in v1, and forwarded email headers are ignored.
- The effective peer from trusted forwarded headers is used only for rate limiting, authentication
  failure counting, and token-mode lockout. It never sets identity, permits a `Host`/`Origin`, or
  becomes audit `actor.ip`.
- WebSocket upgrades require an exact allowlisted `Origin`; a present but disallowed HTTP `Origin` is
  rejected.
- Doctor output is redacted. `auth.tunnel` is `implemented: false`; a doctor check cannot prove that
  all traffic arrived through a tunnel.

## Incident response

1. Revoke the affected user, DeployMC token, or Cloudflare service token.
2. Enter safe state if an untrusted deployment may have loaded.
3. Review the audit chain and snapshots; remember that a host administrator can rewrite the chain.
4. Rotate secrets and re-run the Cloudflare verification checklist before restoring service.

Report vulnerabilities privately through [SECURITY.md](../../SECURITY.md), never in a public issue.
