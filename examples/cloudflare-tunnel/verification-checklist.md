# Cloudflare Tunnel + Access verification checklist

Status: **UNVERIFIED / community-reported** until every item is executed by an operator on a real
setup. Replace every placeholder. Record `NOT RUN` for items that were not executed; do not copy an
expected result into the result column.

The seven production-readiness checks are required by master-spec PART 37.1. Items 0a-0f are setup
checks that support the hard-to-prove claim that the DeployMC port is reachable only through the
tunnel.

| ID | Check | Command or observation | Expected result | Result |
|---|---|---|---|---|
| 0a | Validate ingress rules | `cloudflared tunnel ingress validate --config <CONFIG_PATH>` | No validation errors | `NOT RUN` |
| 0b | Test ingress match | `cloudflared tunnel ingress rule https://deploymc.example.com --config <CONFIG_PATH>` | Matches the DeployMC rule, not the catch-all | `NOT RUN` |
| 0c | Tunnel healthy | Cloudflare dashboard > Zero Trust > Networks > Tunnels | Tunnel reports `HEALTHY` | `NOT RUN` |
| 0d | Public hostname works externally | `curl -v https://deploymc.example.com/healthz` from a machine outside the host | HTTP 200 from DeployMC | `NOT RUN` |
| 0e | Loopback listener works locally | `curl -v http://127.0.0.1:<PORT>/healthz` on the Minecraft host | HTTP 200 from DeployMC | `NOT RUN` |
| 0f | Firewall rule survives reboot | Reboot the host, then repeat 0d and 1b | Public hostname still works; direct external access still fails | `NOT RUN` |
| 1 | DeployMC binds loopback only | `ss -ltnp \| grep <PORT>` (Linux) or `netstat -ano \| findstr :<PORT>` (Windows) | Listen address is `127.0.0.1:<PORT>` or `[::1]:<PORT>` only | `NOT RUN` |
| 2 | Direct external connection fails | From another machine: `curl -v --connect-timeout 3 http://<HOST_PUBLIC_IP>:<PORT>/healthz` | Connection refused, timeout, or no route; never an HTTP response | `NOT RUN` |
| 3 | Unauthenticated public request blocked | From another machine, open `https://deploymc.example.com/api/v1/server` with no JWT or token | Cloudflare Access blocks before DeployMC; no 200 response | `NOT RUN` |
| 4 | Forged identity header rejected | `curl -i -H 'Cf-Access-Authenticated-User-Email: admin@example.com' https://deploymc.example.com/api/v1/server` | Rejected; a header alone never authenticates | `NOT RUN` |
| 5 | Wrong-AUD JWT rejected | Send a valid Access JWT issued for a different application to the DeployMC hostname | Rejected by the product verifier; no 200 response | `NOT RUN` |
| 6a | Access policy removal locks out user | Remove a test user from the Access policy, then request the hostname after the documented session lifetime | Access denies the request | `NOT RUN` |
| 6b | RBAC change takes effect on reload | Remove the user's DeployMC role, reload, then retry with a still-valid JWT | DeployMC denies with 403 | `NOT RUN` |
| 7 | Service token maps to intended role | `curl -i -H 'CF-Access-Client-Id: <CLIENT_ID>' -H 'CF-Access-Client-Secret: <CLIENT_SECRET>' https://deploymc.example.com/api/v1/me` | 200 with the expected non-admin identity and permissions | `NOT RUN` |

## JWT-specific checks

| Check | Expected result | Result |
|---|---|---|
| `iss` equals `https://<TEAM_DOMAIN>` | Mismatch rejected | `NOT RUN` |
| `aud` contains the DeployMC application AUD tag | Other AUD rejected | `NOT RUN` |
| `exp` is in the future and `nbf` is not | Expired or not-yet-valid rejected | `NOT RUN` |
| JWT `kid` matches a key from `https://<TEAM_DOMAIN>/cdn-cgi/access/certs` | Unknown `kid` rejected; no hard-coded key | `NOT RUN` |

## Doctor and audit honesty boundaries

- `deploymc doctor` has 15 checks. `auth.jwt` shows only closed rejection codes and counts; it does
  not print token, audience, or header values.
- `auth.tunnel` is `implemented: false`. DeployMC cannot prove from a doctor check that every
  request arrived through the tunnel; items 1, 2, and 0f are the operator evidence for that claim.
- A privileged mutation shown in the audit query must have `actor.ip: null`. Do not expect a
  client address to be recorded from a forwarded header.

## Common mistakes and incident response

- Do not use an Access `Bypass` or `Everyone` policy for this application.
- Do not forget the AUD tag in DeployMC `config.yml`.
- Do not publish the DeployMC port on all Docker interfaces (`-p 0.0.0.0:<PORT>:<PORT>`).
- Do not run `cloudflared` on a different host with a non-loopback origin unless the network is
  isolated and `bind.allow-non-loopback` is explicitly justified.
- On suspected exposure: revoke the Access user or service token first, rotate the service-token
  secret and signing keys per current official guidance, put the server in safe state, then review
  the audit chain and snapshots. See `README.md` and `sources.md`.
