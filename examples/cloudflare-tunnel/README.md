# Cloudflare Tunnel + Access example for DeployMC

Status: **verified documentation, unverified deployment**. Every Cloudflare-specific statement was
checked against official Cloudflare Docs on 2026-11-27; see [sources.md](sources.md). No Cloudflare
account, tunnel, running Minecraft server, browser, E2E run, or full integration suite was used to
produce this example. Execute [verification-checklist.md](verification-checklist.md) on a real setup
and record actual results before calling a deployment production-ready.

Owner decision D-002 is unresolved. This example does not claim a specific Cloudflare plan, a verified
production topology, or product support for any account configuration.

## Concepts

Cloudflare Tunnel is an outbound-only connection from `cloudflared` to Cloudflare; it does not require
an inbound port on the host. Cloudflare Access is the identity-aware policy layer in front of the
public hostname. DeployMC verifies the Access JWT itself: it reads `Cf-Access-Jwt-Assertion`, derives
the JWKS endpoint from the configured team domain, and checks `iss`, `aud`, `exp`, and `nbf` with an
RS256 `kid` match. A forwarded email header alone never authenticates.

## Prerequisites

- A domain whose DNS is managed in Cloudflare and a Zero Trust organization with Access available.
- `cloudflared` installed on the Minecraft host (or on a sidecar that can reach the listener).
- DeployMC configured with `bind.address: 127.0.0.1` and `auth.mode: cloudflare`.
- An operator who can create Access applications, policies, and service tokens.

The exact availability of Access features depends on the Cloudflare plan and account; verify it with
current official documentation and the owner's D-002 decision before making a plan-specific claim.

## Step 1 - create the tunnel

Create a named, locally-managed tunnel and a credentials file using the current official Cloudflare
Tunnel getting-started flow. Record the tunnel UUID. Do not commit the credentials file or any token.

Place [config.yml](config.yml) in the `cloudflared` configuration directory, replacing the all-zero
UUID, credentials path, hostname, and port with the operator's values. The example maps
`deploymc.example.com` to `http://127.0.0.1:8080` and ends with the required `http_status:404`
catch-all rule.

```sh
cloudflared tunnel ingress validate --config <CONFIG_PATH>
cloudflared tunnel ingress rule https://deploymc.example.com --config <CONFIG_PATH>
```

Both commands must succeed, and the second must match the DeployMC rule rather than the catch-all.
Start the tunnel with the current documented `cloudflared tunnel run <UUID or NAME>` command. Confirm
the tunnel is `HEALTHY` in the Zero Trust dashboard.

## Step 2 - route the public hostname

Create a published-application route for the public hostname with service URL
`http://127.0.0.1:<PORT>`. A dashboard-created route adds a CNAME to the tunnel subdomain; confirm
the hostname in DNS before continuing. Do not create a public `A` record that bypasses the tunnel.

## Step 3 - create the Access application

In the Cloudflare dashboard, go to **Zero Trust > Access controls > Applications**, select
**Create new application**, choose a self-hosted application, and add the public hostname. Access
applications are deny-by-default until a policy matches.

Create an **Allow** policy for interactive operators using the organization's identity provider,
with MFA required where available, and set a session duration appropriate for the environment. Add a
separate **Service Auth** policy for CI (Step 4). Do not use `Everyone` or `Bypass` for this
application. Configure the optional 401 response for Service Auth so machine clients receive a clear
denial instead of an identity-provider redirect.

Copy the team domain and the application **AUD** tag from **Additional settings** into DeployMC:

```yaml
bind:
  address: 127.0.0.1
  port: 8080
  allowed-origins:
    - https://deploymc.example.com
auth:
  mode: cloudflare
  cloudflare:
    team-domain: <TEAM_NAME>.cloudflareaccess.com
    audience: <APPLICATION_AUD_TAG>
```

Do not add a cookie session, CSRF token, or forwarded identity header. With `cloudflared` on the
same host, `bind.trusted-proxies` may remain empty because loopback tunnel terminators are already
trusted for effective-peer parsing; an explicit literal `127.0.0.1` is also valid. DNS names and
wildcards are rejected by the product. Restart DeployMC after these restart-only settings change.

## Step 4 - create a CI service token

In **Zero Trust > Access controls > Service credentials > Service Tokens**, create a token with a
bounded duration and an identifying name. Cloudflare displays the Client Secret only once; store it
in the CI secret store, never in the repository. Enable strict service-token authentication where
the organization supports it, and attach the token to the Step 3 **Service Auth** policy with an IP
range restriction where practical.

CI requests send:

```sh
curl -i \
  -H 'CF-Access-Client-Id: <CLIENT_ID>' \
  -H 'CF-Access-Client-Secret: <CLIENT_SECRET>' \
  https://deploymc.example.com/api/v1/me
```

The response must show the intended non-admin identity. Rotate and revoke the token per current
official guidance if exposure is suspected. A Cloudflare identity is never admin-equivalent; DeployMC
RBAC still decides every REST and WebSocket permission.

## Step 5 - verify arrival and limits

Run `deploymc doctor`. It has 15 checks; `auth.jwt` exposes only redacted rejection codes and
counts. `auth.tunnel` is `implemented: false`, so DeployMC cannot prove from diagnostics that all
traffic arrived through the tunnel. Use the public-hostname, loopback, firewall, and direct-connection
checks in [verification-checklist.md](verification-checklist.md) for that evidence.

A privileged mutation must show `actor.ip: null` in the audit query. Forwarded addresses affect only
per-IP rate limiting, authentication-failure counting, and token-mode lockout; they never establish
identity.

## Common mistakes

- An Access `Bypass` or `Everyone` policy, or a missing AUD tag.
- Publishing the DeployMC port on all Docker interfaces.
- Running `cloudflared` on another host against a non-loopback DeployMC listener without network
  isolation.
- Assuming a `HEALTHY` tunnel proves the local service route works; it does not.
- Treating a forged `Cf-Access-Authenticated-User-Email` header as identity.

## Rotation and incident response

1. Revoke the affected Access user or service token in the Cloudflare dashboard first.
2. Rotate the service-token secret; rotate signing keys only through the current documented process.
3. Remove the exposed secret from the CI store and any logs, then update consumers.
4. Put DeployMC in safe state if a deployment is suspected, review the audit chain and snapshots,
   and only then restore service.
5. Re-run the full verification checklist and update [sources.md](sources.md) if official docs changed.

## Contract

See [secure-deployment.md](../../specification/security/secure-deployment.md) for the normative
contract and [security.md](../../docs/guides/security.md) for the administrator hardening checklist.
