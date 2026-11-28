# Secure deployment and Cloudflare Tunnel contract

> **Status: normative contract for WP-3.7 (2026-11-27).** This document defines what the
> documentation and examples must state; it is not evidence that an operator has deployed the
> topology or that this repository has been tested against Cloudflare.

## 1. Authority and scope

Where this file and the master specification disagree, this file is authoritative for WP-3.7 and the
conflict is recorded in `docs/development/spec-errata.md`.

This contract covers the operator-facing security documentation for a DeployMC deployment behind
Cloudflare Tunnel and Cloudflare Access. It does not change product code, authentication behavior,
RBAC, audit semantics, or transport limits. It does not claim a support tier, a Cloudflare plan, or a
verified production topology until owner decision D-002 is resolved and an operator supplies evidence.

No statement in this contract may bypass the rules in `AGENTS.md`: `/healthz` and `/readyz` remain
the only unauthenticated endpoints; no bypass flag exists; all file access stays behind the sandbox
resolver; no unverified proxy header establishes identity.

## 2. Required deliverables

### 2.1 Finalized threat model

`specification/security/threat-model.md` must be a finalized document, not a draft. It must identify:

- assets: server data, configuration files, tokens, audit chain, plugin artifacts, and operator access;
- trust boundaries: public Internet, Cloudflare edge, tunnel connector, loopback transport, host
  filesystem, and in-game console;
- adversaries: unauthenticated network client, stolen bearer token, malicious forwarded header,
  hostile repository content, compromised CI credential, and insider with host access;
- controls already implemented: bearer authentication, RBAC, audit, sandbox resolver, atomic writes,
  and WP-3.6 transport protections;
- controls that are only documented: tunnel/Access topology and firewall policy;
- residual risks: D-001 hosting setup, D-002 Cloudflare plan/domain, no off-host audit anchor, no
  running-server or browser evidence, and no cross-implementation parity claim beyond the
  implemented RS256 path.

### 2.2 Cloudflare Tunnel example

`examples/cloudflare-tunnel/` must contain:

- `config.yml`: a locally-managed tunnel configuration with obvious placeholders only; a public
  hostname to `http://127.0.0.1:<loopback-port>`, an explicit `http_status:404` catch-all, and no
  credentials, real hostnames, tokens, or account identifiers;
- `README.md`: step-by-step operator instructions for tunnel creation, config validation, ingress
  route testing, DNS/hostname routing, Access application and policy creation, CI service-token
  creation, and secret handling;
- `verification-checklist.md`: a checklist whose final section proves that the DeployMC port is not
  reachable except through the tunnel;
- `sources.md`: a table of official Cloudflare documentation links, page `dateModified` where
  published, access date, and the exact claim each source supports.

### 2.3 Hardening checklist

`docs/guides/security.md` must provide the administrator hardening checklist from master-spec PART
37.8: loopback bind; Access MFA; least-privilege roles; short token TTLs; approvals for JAR
deployments; hash allowlist for production; a separate staging server; regular snapshot
verification; audit-log forwarding; separate world backups; keep DeployMC updated; and subscribe to
security advisories. Each item must distinguish implemented product behavior from operator action
or a planned feature. `SECURITY.md` must link this checklist and the finalized threat model.

### 2.4 Reference integrity

Every Cloudflare-specific instruction must be traceable to an official Cloudflare Docs page in
`sources.md` with an access date of 2026-11-27 or later. If a page changes, the guide must be
re-verified before the claim is repeated. The example must not copy Cloudflare IP lists or SNI
hostnames into the repository; it must link to the current official firewall page instead.

Owner decision D-002 remains open. The guide may describe the published-app topology as a
documentation example, but must not claim that a specific Cloudflare plan, domain, or account is
supported, tested, or required by the product.

## 3. Topology invariants

1. `cloudflared` runs on the same host as DeployMC and connects outbound-only.
2. DeployMC's HTTP listener binds to loopback; no wildcard or public bind is used in the example.
3. The ingress rule maps the public hostname to the loopback listener and ends with
   `http_status:404`.
4. All inbound host firewall rules deny the DeployMC port from external interfaces; loopback and an
   existing administrative SSH rule are the only documented exceptions.
5. The WP-3.6 trusted-peer list contains only the loopback connector address or an explicit literal
   IP/CIDR of the local `cloudflared` process. DNS names and wildcards are not accepted.
6. Cloudflare Access protects the public hostname. The product accepts only the
   `Cf-Access-Jwt-Assertion` header in `auth.mode: cloudflare`; cookies are not accepted by DeployMC,
   and no cookie session or CSRF token is introduced.
7. Cloudflare JWT identities are not admin-equivalent. RBAC still governs every REST and WebSocket
   route, and `local-onetime` token provisioning is refused in Cloudflare mode.
8. Audit `actor.ip` remains null. Forwarded addresses are used only for rate limiting,
   authentication-failure counting, and token-mode lockout.

## 4. Access and CI service-token procedure

The README must follow the current official dashboard flow: Zero Trust > Access controls >
Applications; create a self-hosted application for the public hostname; add a policy; applications
are deny-by-default until an Allow or Service Auth policy matches. For CI, create a service token
under Zero Trust > Access controls > Service credentials > Service Tokens, set a duration, copy the
Client Secret once, and send `CF-Access-Client-Id` and `CF-Access-Client-Secret` headers. The guide
must require strict service-token authentication where available, must state that Cloudflare shows
the secret only once, and must instruct operators to store it outside the repository and rotate or
revoke it on exposure. No real or example-looking secret value is committed.

## 5. Verification checklist requirements

The checklist must include the seven production-readiness checks from master-spec PART 37.1, with a
command or dashboard observation and an expected result for each:

1. the DeployMC port is bound to `127.0.0.1` only, with `ss`/`netstat` evidence;
2. from another machine, a direct connection to `<host>:<port>` fails;
3. a request to the public hostname without Access authentication is blocked by Cloudflare;
4. a request reaching DeployMC with a forged `Cf-Access-Authenticated-User-Email` header but no
   valid JWT is rejected;
5. a JWT for a different Access application (wrong `aud`) is rejected;
6. removing a user from the Access policy locks them out within the documented session lifetime and
   DeployMC RBAC removal takes effect on reload;
7. service-token authentication works and maps to the intended RBAC role.

The guide must also cover ingress validation (`cloudflared tunnel ingress validate` and
`cloudflared tunnel ingress rule <public-url>`), tunnel health, external `curl` to the hostname,
JWT `iss`/`aud`/`exp`/`nbf` validation with `kid`-matched JWKS from the team domain
`cdn-cgi/access/certs`, common mistakes (`Bypass`/`Everyone`, missing AUD, publishing the port on
all Docker interfaces, a non-loopback origin without network isolation), and rotation/incident
response for signing keys, service tokens, users, and the safe state.

Doctor evidence must stay within the implemented contract: `auth.jwt` exposes only redacted counters,
and `auth.tunnel` remains `implemented: false`. The guide must not claim that DeployMC proves all
traffic arrived through the tunnel; the firewall and direct-connection checks are operator evidence.

Each item must record `NOT RUN` unless the operator actually executed it. The repository's own
evidence must state that no tunnel, Cloudflare account, running server, browser, E2E run, or full
integration suite was used for WP-3.7.

## 6. Non-claims

This work package does not add product code, CI gates, localization keys, or new doctor checks. It
does not verify real Cloudflare compatibility, any paid-plan behavior, off-host audit anchoring,
restart/update-folder/version scenarios blocked by D-001, or cross-implementation parity. It does not
turn Cloudflare identities into administrators.
