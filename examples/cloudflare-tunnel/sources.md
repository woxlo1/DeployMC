# Official sources for the Cloudflare Tunnel example

Every Cloudflare-specific statement in this directory was checked against the following official
Cloudflare Docs pages on **2026-11-27**. The `Page modified` values were published by Cloudflare;
they are not access dates. Re-verify this table before repeating a claim if a page changes.

| Topic | Official page | Page modified (if published) | Claim supported here |
|---|---|---|---|
| Locally-managed tunnel config | [Configuration file](https://developers.cloudflare.com/tunnel/features/locally-managed-tunnels/configuration-file/) | 2026-11-10 | `tunnel`, `credentials-file`, `ingress`; catch-all `http_status:404`; `cloudflared tunnel ingress validate`; `cloudflared tunnel ingress rule <URL>`; path-normalization caution for Access JWT rules |
| Tunnel firewall | [Tunnel with firewall](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/tunnel-with-firewall/) | 2026-10-12 | Outbound-only positive security model; block ingress; allow `cloudflared` egress on port `7844`; verify the service is reachable through the hostname but not the host IP; persist firewall rules. IP lists and SNI hostnames are intentionally not copied into this repository. |
| Published application route | [Routing to a tunnel](https://developers.cloudflare.com/tunnel/concepts/routing/) | not published | Public hostname maps to a local service such as `http://localhost:8080`; dashboard-created routes add a CNAME to the tunnel subdomain |
| Access application | [Create an Access application](https://developers.cloudflare.com/learning-paths/clientless-access/access-application/create-access-app/) | 2026-04-23 | Zero Trust > Access controls > Applications; select Self-hosted; add hostname in an active zone; deny-by-default; session duration; optional 401 response for Service Auth |
| Access policies | [Access policies](https://developers.cloudflare.com/cloudflare-one/access-controls/policies/) | not published | Allow/Block/Bypass/Service Auth actions; Include/Require/Exclude rule types; `Bypass` and `Everyone` are dangerous for protected applications |
| Service tokens | [Service tokens](https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/) | 2026-11-17 | Create under Zero Trust > Access controls > Service credentials > Service Tokens; Client Secret shown once; `CF-Access-Client-Id` and `CF-Access-Client-Secret` headers; strict service-token authentication; rotation and revocation |
| Access JWT verification | [Validate JWTs](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/) | 2026-11-17 | `Cf-Access-Jwt-Assertion` header is preferred; JWKS at `https://<team>/cdn-cgi/access/certs`; validate `iss`, `aud`, `exp`, `nbf`; match `kid`; do not hard-code keys |
| End-to-end check | [Set up a private origin via Cloudflare Tunnel](https://developers.cloudflare.com/dns/private-origins/set-up-via-cloudflare-tunnel/) | 2026-11-05 | Confirm tunnel health, request the public hostname from outside, and confirm the origin sees traffic from the `cloudflared` host. The page describes private routing; this example uses the published-application route concept. |

## Status of this guide

The commands and dashboard steps below are **verified documentation**, not a tested deployment.
No Cloudflare account, tunnel, running Minecraft server, browser, or E2E run was used to produce this
example. The operator must execute `verification-checklist.md` and record actual results before
calling a deployment production-ready.
