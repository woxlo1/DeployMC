# Threat Model

> **Status: finalized for the v1 security model (2026-11-28), with implementation evidence through
> WP-3.6.** This document defines the threats DeployMC addresses and the ones it explicitly does
> not. "Finalized" means this is the v1 statement of assets, boundaries, controls, and residual
> risks; it does **not** claim that every control is implemented, tuned, or verified on a running
> deployment. Cloudflare compatibility, plan-specific behavior, and hosting-specific setup remain
> unverified under owner decisions D-001 and D-002. This is documentation, not deployment evidence.

## Why this document exists

DeployMC's core feature is remote code deployment. A user who can upload a plugin JAR can
run arbitrary code as the Minecraft server process. There is no configuration of DeployMC
that makes that safe against a malicious authorized user. This threat model exists to:

- state that fact plainly, instead of implying that RBAC makes deployment "secure";
- list the assets at risk, the actors, and the trust boundaries;
- enumerate the attack paths that the design must block or detect;
- record the residual risk that remains after the mitigations;
- state what DeployMC does not defend against, so operators do not rely on it.

Security controls elsewhere in the repository refer back to this document. A control that
is claimed in an ADR or a guide must map to an attack path here, or the claim is marketing.
The normative companion is [secure deployment](secure-deployment.md); the operator checklist is
[the administrator security guide](../../docs/guides/security.md).

## Assets

| Asset | Why it matters | Where it lives |
|---|---|---|
| Server process integrity | The plugin runs inside the server JVM. Code execution there controls the server. | Server JVM |
| Plugin JARs and configuration files | Changing them changes server behavior, potentially to attacker-controlled code | Configured plugin root (default `plugins/`) |
| Worlds, player data, server core files | Data loss and griefing; outside the default sandbox root | Server directories outside the plugin root |
| Host file system and OS account | The server account's files and privileges | Host |
| DeployMC data directory | Snapshot history, staging, deployment records, audit log, lock | `.deploymc/` |
| Audit log integrity | Needed to reconstruct who changed what | `.deploymc/audit/` |
| Authentication material | Cloudflare JWT signing keys, API token digests, one-time tokens | Plugin config, token records, and memory |
| Host configuration and secrets | Webhook secrets, token hashes, Cloudflare team/audience | `config.yml`, secret stores |
| Identity and authorization policy | Determines who may do what | `config.yml`, Cloudflare Access policy, DeployMC RBAC policy |
| Web UI and API availability | A deployment tool that is down blocks recovery | Transport, server process |
| Release artifacts and supply chain | A tampered JAR executes attacker code before any runtime control applies | Maven/npm/PyPI/crates/GitHub releases |

## Actors

| Actor | Capability | Trust level |
|---|---|---|
| Host administrator | Full file-system and process access; installs the plugin; edits `config.yml` locally | Outside the protection boundary (trusted by necessity) |
| Authorized deployer (developer) | May upload and deploy JARs within granted paths and permissions | **Trusted to execute code**; not trusted with the host or with data outside their scope |
| Authorized editor (observer/developer) | May read/edit configuration files within path scopes | May be able to escalate to code execution through a plugin's own config |
| Read-only user | May view files and console | Not trusted to see secrets unless explicitly allowed |
| CI service token / GitHub Action | Automated deployer with scoped token permissions | Machine identity; scope-limited, but powerful within scope |
| Cloudflare Access | Identity provider and edge authentication in `cloudflare` mode | Trust anchor in that mode; a misconfiguration is a primary failure |
| Cloudflare Tunnel / `cloudflared` | Forwards traffic from the edge to loopback | Trusted transport path; DeployMC cannot attest to arrival through it |
| Local process / local user | Can reach `127.0.0.1:<port>` and the data directory | Untrusted; must not be able to authenticate without credentials |
| Malicious plugin author | Supplies a JAR that may do anything once loaded | Untrusted content |
| Network attacker on the path to Cloudflare | Can intercept or forge traffic outside TLS | Blocked by TLS and Cloudflare; not a plugin-side control |
| Future contributor / compromised dependency | Can change code or a dependency | Handled by review and supply-chain controls, not runtime controls |

The distinction that matters most: **an authorized deployer is trusted to execute code**. The
controls below protect the host, the audit trail, and other users from *unintended* or
*out-of-scope* actions. They do not sandbox an intentionally malicious deployer.

## Trust boundaries

```
[ browser / CLI / CI ]  --untrusted network-->  [ Cloudflare edge (trust anchor) ]
                                                          |
                                             trusted tunnel
                                                          v
[ host loopback ] --> [ transport ] --> [ auth ] --> [ rbac ] --> [ application ]
                                                                       |
                                           +---------------------------+-------------------+
                                           v                           v                   v
                                    [ sandbox ]               [ snapshot/audit ]   [ platform SPI ]
                                           |                           |                   |
                                           v                           v                   v
                                    [ plugin root ]             [ .deploymc/ ]      [ server JVM ]
```

Boundaries and their rules:

1. **Network -> Cloudflare edge.** TLS is terminated at Cloudflare. Cloudflare Access is the
   identity authority in `cloudflare` mode. A policy mistake here is the most likely real
   failure, and DeployMC cannot detect all of them.
2. **Cloudflare edge -> plugin transport.** The tunnel forwards to loopback. The plugin must
   not trust a forwarding header as identity; only a verified JWT is acceptable. Binding to a
   non-loopback address widens this boundary and requires an explicit warning.
3. **Transport -> application.** The transport enforces identity, exact `Host`/`Origin` checks,
   trusted-peer effective addresses, and protocol limits. It does not make resource authorization
   decisions. Forwarded addresses affect rate limiting, authentication-failure counting, and
   token-mode lockout only; they never establish identity or audit `actor.ip`.
4. **Application -> sandbox.** Every user-supplied path crosses here. No other component may
   turn a string into a file operation.
5. **Application -> platform.** The SPI is the only boundary to server APIs. Adapter bugs
   must not become authorization bypasses.
6. **DeployMC -> host file system.** The server account can read and write everything the
   process can. DeployMC narrows this for API users only; it does not constrain the process.
7. **DeployMC data directory.** Inside the process boundary but outside the API sandbox.
   Protected from API access, including administrators, because it contains history and
   audit records.

## Attack paths and mitigations

Each path lists the primary controls. "Residual" states what remains after the controls.
Implementation status is stated separately from design, and server-free tests are not presented
as running-server evidence.

### A1. Unauthenticated or forged access to the API

- **Path:** an attacker reaches the port and calls the API without valid credentials, or
  forges `Cf-Access-Authenticated-User-Email` or other forwarded identity headers.
- **Controls:** loopback default bind; a final fail-closed authentication filter over HTTP and
  WebSocket; verification of `Cf-Access-Jwt-Assertion` with `iss`, `aud`, `exp`, `nbf`, `iat`,
  an RS256 algorithm allow-list, and a matched `kid`; bounded JWKS cache with fail-closed refresh;
  constant-time token digest comparison; bounded token records; role-lowered rate limits and
  token-mode lockout; exact `Host`/`Origin` checks. There is no cookie session or CSRF token in
  v1, and no unauthenticated mode other than `/healthz` and `/readyz`.
- **Implementation evidence:** the authentication framework, Cloudflare verifier, full API token
  lifecycle, RBAC gate, and WP-3.6 web protections have Java implementations and server-free or
  embedded-socket tests. All 11 JWT vectors pass through the product verifier. No running server,
  browser, E2E run, or real Cloudflare account exercised these paths.
- **Residual:** Cloudflare Access policy misconfiguration can admit the wrong identity, and
  DeployMC cannot detect every such mistake. A local process with a valid token or one-time token
  is authenticated. Loopback binding does not protect against a compromised host account.

### A2. Path traversal or sandbox escape

- **Path:** `..`, absolute paths, drive letters, UNC paths, NUL/control bytes, Windows device
  names, alternate data streams, short names, junctions/reparse points, symlinks planted
  inside the root, Unicode normalization tricks, or a TOCTOU race between check and open.
- **Controls:** one resolver; canonicalize then re-verify component-wise containment; reject
  dangerous segments on every platform; `NOFOLLOW_LINKS`/open-by-handle where available;
  post-open identity check; protected-path matching on the resolved key; bounded limits; a
  traversal corpus and fuzzing on Linux and Windows.
- **Implementation evidence (2026-11-20):** `SandboxResolver` applies these rules with an
  injected file-system boundary. A deterministic corpus contains 2,736 reject payloads and 29
  allowed paths; 16 sandbox unit/fuzz/corpus tests ran locally with 15 passed and 1 skipped.
  The skipped test requires symlink privileges unavailable on this Windows host. Implementation
  PR #153 merged at `748bbc8` after all PR checks passed at `9d52a85`; post-merge `main` CI
  [44716375506](https://github.com/woxlo1/DeployMC/actions/runs/44716375506), CodeQL
  [44716375542](https://github.com/woxlo1/DeployMC/actions/runs/44716375542), and Scorecard
  [44716375486](https://github.com/woxlo1/DeployMC/actions/runs/44716375486) passed.
  WP-2.2 merged in PR #155 at `017c2ab`: `core.files` resolves every request path through the
  resolver, computes full-byte SHA-256 revisions, and commits writes atomically behind a
  fail-closed snapshot boundary. Authenticated file and validation REST adapters now consume it,
  with server-free tests only.
- **Residual:** TOCTOU cannot be eliminated on every platform; root revalidation and the post-open
  file-key check narrow the window. A host administrator can create layouts the resolver rejects;
  that is a denial of convenience, not an escape. Existing NFD filenames are compared under NFC
  but their exact bytes are not preserved. Server software or another plugin with host access is
  unaffected.

### A3. Unauthorized action within the API

- **Path:** an authenticated user performs an action outside their permission or path scope,
  or exploits a confused-deputy flow (for example an approval or restore that uses another
  user's context).
- **Controls:** deny-by-default RBAC over a closed permission registry; deny rules win; permission
  check plus path-scope check on the resolved path; tokens are intersected with their owner's
  permissions and never exceed them; REST and WebSocket gates; authenticated `/api/v1/me`;
  WebSocket close `4403` on permission loss; deny-all reload on invalid policy; explain mode for
  diagnosis; denials recorded with stable reason codes.
- **Implementation evidence:** the Java 8 evaluator and transport enforcement merged in WP-3.4,
  with 20 auth/RBAC algorithm tests in PART 33 and server-free or embedded-socket verification.
  Cloudflare JWT identities are not admin-equivalent, and `local-onetime` is a reserved identity.
- **Residual:** a user with `files.write` inside a scope can still change what that plugin
  does at runtime. See A4. A host administrator remains outside this boundary, and an approval
  workflow is not implemented.

### A4. Code execution through configuration

- **Path:** an editor changes a plugin config that causes command execution (command
  aliases, script hooks, expression evaluation), or changes a plugin JAR's data files in a
  way that alters behavior.
- **Controls:** path scopes limit who may write where; pre-save validation blocks malformed
  files; audit records actor and hash; optional secret masking; documentation states the
  risk; deployment approval is a policy option.
- **Residual:** DeployMC cannot know which configuration keys are dangerous in every
  third-party plugin. **A user allowed to edit a plugin's configuration may be able to
  achieve code execution.** This is not preventable in general.

### A5. Malicious or malformed JAR

- **Path:** an uploaded JAR contains a zip-slip entry, a zip bomb, a broken descriptor, a
  name/version that impersonates another plugin, or code that attacks the server after load.
- **Controls:** bounded YAML, JSON, TOML, and `.properties` validation; declared plugin
  descriptor metadata where available; size limits; snapshots and rollback; audit hashes;
  a hostile archive generator corpus for future regression testing.
- **Implementation evidence:** `core.validation` and the host-wired save gate exist, and config
  corpora are consumed by real Java tests. The hostile archive corpus has not yet been consumed
  by a product archive validator; no such compatibility or parity claim is made.
- **Residual:** static validation **cannot prove a JAR is safe**. Once loaded, the code runs
  with the server's privileges. A denylist is a convenience, not a security boundary.

### A6. Half-applied or corrupted files

- **Path:** the process crashes or a write fails midway, leaving a mixed JAR/config set, a
  truncated file, or an overwritten loaded JAR (especially on Windows).
- **Controls:** temp-then-rename with fsync; same-directory staging; no in-place replacement
  of loaded JARs; update-folder/restart-time swap; persisted deployment state before each
  side effect; snapshots before destructive operations; startup recovery; rollback.
- **Implementation evidence:** `core.snapshot` provides immutable blobs, atomic metadata writes,
  restore-first reversibility, retention, verify/repair, conservative GC, and a single-instance
  lock. A deployment state machine is normative, but a deployment engine is not implemented;
  restart, update-folder, and oldest/newest-version evidence is blocked by D-001.
- **Residual:** if the server never restarts after `RESTARTING`, no DeployMC code runs; the
  on-disk record is the only recovery source and requires an operator. File systems without
  atomic rename need a documented fallback and cannot guarantee the same property.

### A7. Audit log tampering or loss

- **Path:** an authorized user or a local process edits or deletes audit records, or a crash
  loses entries.
- **Controls:** append-only JCS-canonical JSON Lines records with `sha256:` chain hashes;
  fail-closed writes; rotation continuations; anchor-covered retention; daily and clean-shutdown
  anchors; protected data directory; no API write path to the audit log; `pending` before a
  privileged mutation and `success`/`failed` after; export for off-host retention.
- **Implementation evidence:** the Java 8 writer/verifier, authenticated query/export/verify
  routes, mutation recorder, and Bukkit lifecycle wiring merged in WP-3.5 with server-free and
  handler-level tests. `actor.ip` is null because no unverified proxy header is accepted.
- **Residual:** **anyone with host file-system access can read or modify the audit log.**
  Hash chaining detects some tampering but cannot prevent a host administrator from
  rewriting the whole chain. There is no off-host anchor, and no Web UI consumes the routes.

### A8. Secret disclosure

- **Path:** tokens, Authorization headers, JWT assertions, webhook secrets, or file contents
  appear in logs, error payloads, snapshots, WebSocket events, or webhook bodies.
- **Controls:** redacted stable error codes and doctor output; secrets never in events or
  webhook payloads; secret-masking rules for file reads; one-time token shown only once and
  stored as a SHA-256 digest; config secret files outside the sandbox; no cookie sessions.
- **Residual:** snapshots may contain secrets from configuration files; they are protected by
  the same path scopes as the original, not encrypted at rest. A host administrator can read
  everything.

### A9. Denial of service and resource exhaustion

- **Path:** oversized uploads, many connections, slow clients, YAML alias bombs, huge
  directories, expensive diffs, or a flood of authentication failures.
- **Controls:** bounded queues with documented rejection; body and file size limits;
  connection/time limits; parser limits for YAML/JSON/TOML; bounded diff size; per-identity
  and per-IP rate limits with role-lowered budgets; token-mode lockout; exact WebSocket Origin
  checks; a log ring buffer that never blocks the server; per-connection WebSocket queues with
  gap notices.
- **Implementation evidence:** WP-3.6 enforces trusted-peer parsing, exact Origin checks,
  three bounded rate buckets, endpoint body limits, and redacted rejections. PART 33 adds
  16 runtime-limit algorithm tests, with product boundaries tested only where implemented.
- **Residual:** a determined attacker with a valid identity and permission can still consume
  their share of CPU and disk within the limits. Thresholds are not measured or tuned, and
  local evidence is server-free and embedded-socket only.

### A10. Webhook and outbound request abuse (SSRF)

- **Path:** a webhook target points at loopback, a private range, or a redirect that reaches
  an internal service.
- **Controls:** target validation before every attempt, including redirects; HTTPS-only by
  default; signed payloads; bounded retries; delivery log.
- **Residual:** DNS rebinding and unusual network topologies can defeat a static check; the
  allowlist for private ranges is an explicit administrator decision.

### A11. Deployment confusion and replay

- **Path:** a repeated or replayed request creates a second deployment, a stale `If-Match`
  overwrites a newer revision, or a user approves their own deployment.
- **Controls:** idempotency keys; ETag/`If-Match` preconditions with conflict responses;
  one active deployment per data directory; approval must come from a different actor;
  persisted state and history.
- **Residual:** an idempotency key reused with a different payload is a conflict, not a
  silent success; clients that ignore conflict responses can still surprise their users.
  A deployment engine and approval workflow are not implemented.

### A12. Supply-chain compromise

- **Path:** a dependency, a build action, or a released artifact is compromised.
- **Controls:** pinned third-party actions by commit SHA; dependency review; CodeQL;
  secret scanning and push protection; OpenSSF Scorecard; pinned tool versions; planned
  SBOM, signed releases, and provenance.
- **Residual:** runtime controls cannot help if the shipped JAR already contains attacker
  code. Release signing and provenance are planned, not implemented.

## Residual risk summary

After all designed controls:

1. **An authorized deployer can execute arbitrary code as the server.** Approval and RBAC
   reduce accidents and insider risk, not capability.
2. **A configuration editor may reach code execution** through a third-party plugin's own
   features. Scope and validation reduce the blast radius but cannot remove the class of
   risk.
3. **The host administrator is outside the boundary.** They can read and rewrite the data
   directory, audit log, and plugin files.
4. **Cloudflare Access is a trust anchor** in `cloudflare` mode. A policy mistake is the most
   likely real-world compromise and is largely invisible to the plugin; D-002 prevents a real
   compatibility or plan-specific claim.
5. **A malicious JAR cannot be detected by static validation.** Validation catches mistakes
   and obvious attacks, not intent.
6. **Crash recovery is not guaranteed.** If the process does not return, recovery is manual;
   restart and update-folder scenarios remain blocked by D-001.
7. **Unencrypted history and audit at rest.** Protection relies on host file permissions,
   and no off-host audit anchor exists.
8. **Running-system evidence is incomplete.** Server-free tests, embedded sockets, and Paper
   smoke cover important boundaries, but no full integration suite, browser, E2E run,
   running-server deployment, or in-game command exercised these controls.

## What DeployMC does not defend against

- A malicious host administrator, root, or anyone with equivalent file-system access.
- A compromised Minecraft server, server software, or another plugin with host access.
- A compromised Cloudflare account, Access policy, or Tunnel configuration.
- A malicious JAR that has already been approved and loaded.
- Code execution reached through a plugin configuration that the user was authorized to edit.
- Attacks on the player-facing game server (griefing, exploits) that are unrelated to
  DeployMC's file and deployment surface.
- Network attackers between a client and Cloudflare when TLS is correctly configured.
- Physical access, backups, or the durability of the host's storage.
- Legal or licensing violations in user-supplied plugins.

These are out of scope, not solved. Documentation and the Web UI must not imply otherwise.

## Assumptions the design relies on

- The host administrator installs DeployMC from a trusted source and protects `config.yml`.
- The server account is dedicated and does not run unrelated privileged services.
- Cloudflare Access is configured to require authentication for the application, and the
  team domain and audience in `config.yml` match the real application. This is an operator
  assumption, not a tested product claim.
- The plugin binds to loopback unless the administrator deliberately changes it.
- The file system provides atomic rename, or the administrator accepts the documented
  degraded behavior.
- Time is approximately correct on the host (JWT validity depends on it).

If an assumption fails, the corresponding control may not hold; this document does not
promise protection under a violated assumption.

## Security test evidence and remaining plan

The threat model is only credible if these tests exist and run. The following records actual
local evidence; items marked "not implemented" or "not exercised" are not claims.

| Area | Evidence or remaining test |
|---|---|
| Sandbox | PART 33 adds 24 file-safety algorithm tests; the WP-2.1 corpus, fuzz, and Windows/Linux cases ran locally. Exact-byte preservation for existing NFD filenames remains unimplemented. |
| AuthN | 11 Cloudflare JWT vectors pass through the product verifier; PART 33 adds 20 auth/RBAC tests covering JWT vectors and rate limiting. Real Cloudflare compatibility is not claimed. |
| AuthZ | RBAC evaluator and REST/WebSocket enforcement have server-free tests; approval, self-approval, and confused-deputy flows lack a product engine. |
| Transport | WP-3.6 rate buckets, Origin checks, body limits, and redaction have server-free and embedded-socket tests; no browser, E2E, or running-server test exercised them. |
| Files | Atomic-write and snapshot boundaries have PART 33 crash-point and algorithm tests; no full integration suite exercised a running deployment. |
| Parsers | Bounded config validators and byte-preserving YAML editing have Java tests; the hostile archive corpus is a fixture contract, not yet consumed by a product validator. |
| Secrets | Doctor and audit redaction have server-free tests; no log-ingestion or production secret-scanning exercise was run. |
| Supply chain | Dependency review, CodeQL, secret scanning, Scorecard, and pinned actions run in CI; release signing and provenance are not implemented. |

At WP-3.6 merge, the recorded tree had 670 Java tests (0 failures, 0 errors, 1 skipped), all
seven local toolchains reported `PASS`, and required PR checks plus post-merge CI/CodeQL/Scorecard
passed. Those results verify the cited code paths, not a deployed topology.

## Open questions

- The exact approval policy (roles, expiry, two-person rule, break-glass) is not finalized.
- Whether audit records leave the host by default (and how) is undecided; no off-host anchor exists.
- Encryption at rest for snapshots and audit is not planned initially; it needs a decision
  if snapshots are ever stored off-host.
- Detection of a misconfigured Cloudflare Access policy (for example, an open policy) has no
  design beyond documentation.
- Rate-limit and lockout thresholds need measurement before they are treated as tuned.
- D-001 blocks verified hosting-specific restart, update-folder, and version evidence.
- D-002 blocks real Cloudflare tenant verification and plan-specific guidance.
- D-005 and the absence of native review mean the Japanese README translation remains an
  AI-assisted draft.

## Related documents

- [Secure deployment contract](secure-deployment.md)
- [Administrator security guide](../../docs/guides/security.md)
- [Cloudflare Tunnel + Access example](../../examples/cloudflare-tunnel/README.md)
- [Web protection contract](web-protections.md)
- [RBAC contract](rbac.md)
- [Audit log format](../audit/audit-log-format.md)
- [Architecture overview](../../docs/architecture/overview.md)
- [Sandbox design and protected paths](../../docs/adr/ADR-0006-sandbox-and-protected-paths.md)
- [Authentication modes and Cloudflare JWT](../../docs/adr/ADR-0005-authentication-and-cloudflare-jwt.md)
- [Snapshot storage format](../../docs/adr/ADR-0007-snapshot-storage-format.md)
- [Deployment state machine](../../docs/architecture/deployment-state-machine.md)
- Master specification, Part 9 and Part 37.
