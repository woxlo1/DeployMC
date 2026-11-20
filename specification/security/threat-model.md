# Threat Model

> **Status: draft, design-level with WP-2.1 implementation evidence (2026-11-20).** This document
> describes the threats DeployMC is designed to address and the ones it explicitly does not.
> The token authenticator and sandbox resolver now exist in code; RBAC, an audit log, and a
> deployment engine do not. Nothing in this document has been validated against a running system:
> the resolver has unit, corpus, and fuzz evidence, but no file API or server process uses it yet.
> It will be revised as implementation and security tests produce further evidence.

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

## Assets

| Asset | Why it matters | Where it lives |
|---|---|---|
| Server process integrity | The plugin runs inside the server JVM. Code execution there controls the server. | Server JVM |
| Plugin JARs and configuration files | Changing them changes server behavior, potentially to attacker-controlled code | Configured plugin root (default `plugins/`) |
| Worlds, player data, server core files | Data loss and griefing; outside the default sandbox root | Server directories outside the plugin root |
| Host file system and OS account | The server account's files and privileges | Host |
| DeployMC data directory | Snapshot history, staging, deployment records, audit log, lock | `.deploymc/` |
| Audit log integrity | Needed to reconstruct who changed what | `.deploymc/audit/` |
| Authentication material | Cloudflare JWT signing keys, API token hashes, one-time tokens, session cookies | Plugin config and memory |
| Host configuration and secrets | Webhook secrets, token hashes, Cloudflare team/audience | `config.yml`, secret stores |
| Identity and authorization policy | Determines who may do what | `config.yml`, Cloudflare Access policy |
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
| Cloudflare Tunnel / `cloudflared` | Forwards traffic from the edge to loopback | Trusted transport path |
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
[ host loopback ] --> [ plugin transport ] --> [ auth ] --> [ rbac ] --> [ application ]
                                                                              |
                                                          +-------------------+-------------------+
                                                          v                   v                   v
                                                   [ sandbox ]         [ snapshot/audit ]   [ platform SPI ]
                                                          |                   |                   |
                                                          v                   v                   v
                                                   [ plugin root ]     [ .deploymc/ ]      [ server JVM ]
```

Boundaries and their rules:

1. **Network -> Cloudflare edge.** TLS is terminated at Cloudflare. Cloudflare Access is the
   identity authority in `cloudflare` mode. A policy mistake here is the most likely real
   failure, and DeployMC cannot detect all of them.
2. **Cloudflare edge -> plugin transport.** The tunnel forwards to loopback. The plugin must
   not trust a forwarding header as identity; only a verified JWT is acceptable. Binding to a
   non-loopback address widens this boundary and requires an explicit warning.
3. **Transport -> application.** The transport proves identity and enforces protocol limits.
   It must not make authorization decisions or accept resource paths as authorized.
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

### A1. Unauthenticated or forged access to the API

- **Path:** an attacker reaches the port and calls the API without valid credentials, or
  forges `Cf-Access-Authenticated-User-Email`.
- **Controls:** loopback default bind; no unauthenticated mode; verify the signed
  `Cf-Access-Jwt-Assertion` (signature, `iss`, `aud`, `exp`, `nbf`, `iat`, algorithm
  allow-list, key id); fail closed on JWKS failure; constant-time secret comparison; rate
  limiting; `Host`/`Origin` validation; CSRF protection for cookie sessions.
- **Residual:** Cloudflare Access policy misconfiguration can admit the wrong identity.
  A local process that obtains a valid token or one-time token is authenticated. Loopback
  binding does not protect against a compromised host account.

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
  PR #153 was awaiting required CI checks and merge when this evidence was recorded.
- **Residual:** TOCTOU cannot be eliminated on every platform; root revalidation and the post-open
  file-key check narrow the window. A host administrator can create layouts the resolver rejects;
  that is a denial of convenience, not an escape. Existing NFD filenames are compared under NFC
  but their exact bytes are not preserved. No file API or running server uses the resolver yet.
  Server software or another plugin with host access is unaffected.

### A3. Unauthorized action within the API

- **Path:** an authenticated user performs an action outside their permission or path scope,
  or exploits a confused-deputy flow (for example an approval or restore that uses another
  user's context).
- **Controls:** deny-by-default RBAC; deny rules win; permission check plus path-scope check
  on the resolved path; tokens are intersected with their owner's permissions and never
  exceed them; approval by a different actor than the requester; denials audited with a
  reason code; explain mode for diagnosis.
- **Residual:** a user with `files.write` inside a scope can still change what that plugin
  does at runtime. See A4.

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
- **Controls:** archive validation (valid ZIP, no traversal entries, bounded expansion,
  entry count and size limits); plugin descriptor parsing; declared name/version surfaced
  before deployment; size limits; optional signature/hash allowlist and denylist; snapshots
  and rollback; audit hashes.
- **Residual:** static validation **cannot prove a JAR is safe**. Once loaded, the code runs
  with the server's privileges. A denylist is a convenience, not a security boundary.

### A6. Half-applied or corrupted files

- **Path:** the process crashes or a write fails midway, leaving a mixed JAR/config set, a
  truncated file, or an overwritten loaded JAR (especially on Windows).
- **Controls:** temp-then-rename with fsync; same-directory staging; no in-place replacement
  of loaded JARs; update-folder/restart-time swap; persisted deployment state before each
  side effect; snapshots before destructive operations; startup recovery; rollback.
- **Residual:** if the server never restarts after `RESTARTING`, no DeployMC code runs; the
  on-disk record is the only recovery source and requires an operator. File systems without
  atomic rename need a documented fallback and cannot guarantee the same property.

### A7. Audit log tampering or loss

- **Path:** an authorized user or a local process edits or deletes audit records, or a crash
  loses entries.
- **Controls:** append-only hash-chained records; flush on shutdown; protected data
  directory; no API write path to the audit log; audit written before/after side effects;
  export for off-host retention.
- **Residual:** **anyone with host file-system access can read or modify the audit log.**
  Hash chaining detects some tampering but cannot prevent a host administrator from
  rewriting the whole chain. Off-host shipping is the only real mitigation and is not
  implemented.

### A8. Secret disclosure

- **Path:** tokens, Authorization headers, JWT assertions, webhook secrets, or file contents
  appear in logs, error payloads, snapshots, WebSocket events, or webhook bodies.
- **Controls:** log redaction; stable error codes with non-sensitive detail; secrets never in
  events or webhook payloads; secret-masking rules for file reads; one-time token shown only
  once and stored hashed; config secret files outside the sandbox.
- **Residual:** snapshots may contain secrets from configuration files; they are protected by
  the same path scopes as the original, not encrypted at rest. A host administrator can read
  everything.

### A9. Denial of service and resource exhaustion

- **Path:** oversized uploads, many connections, slow clients, YAML alias bombs, huge
  directories, expensive diffs, or a flood of authentication failures.
- **Controls:** bounded queues with documented rejection; body and file size limits;
  connection/time limits; parser limits for YAML/JSON/TOML; bounded diff size; per-identity
  and per-IP rate limits; log ring buffer that never blocks the server; per-connection
  WebSocket queues with gap notices.
- **Residual:** a determined attacker with a valid identity and permission can still consume
  their share of CPU and disk within the limits. DeployMC must not make the server lag to
  serve a client; where it must choose, it sheds client work.

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
   likely real-world compromise and is largely invisible to the plugin.
5. **A malicious JAR cannot be detected by static validation.** Validation catches mistakes
   and obvious attacks, not intent.
6. **Crash recovery is not guaranteed.** If the process does not return, recovery is manual.
7. **Unencrypted history and audit at rest.** Protection relies on host file permissions.

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
  team domain and audience in `config.yml` match the real application.
- The plugin binds to loopback unless the administrator deliberately changes it.
- The file system provides atomic rename, or the administrator accepts the documented
  degraded behavior.
- Time is approximately correct on the host (JWT validity depends on it).

If an assumption fails, the corresponding control may not hold; this document does not
promise protection under a violated assumption.

## Security test plan

The threat model is only credible if these tests exist and run. None exist yet.

| Area | Planned test |
|---|---|
| Sandbox | Traversal payload corpus, symlink/junction escapes, Windows device names and ADS, NFC/NFD, race tests, fuzzing, on Linux and Windows |
| AuthN | JWT forgery, `none` algorithm, wrong `iss`/`aud`, expired/not-yet-valid, key rotation, JWKS outage, header spoofing, token brute force |
| AuthZ | RBAC bypass attempts, scope confusion, deny-wins, token exceeding owner, self-approval |
| Transport | Oversized bodies, slow clients, header limits, WebSocket origin, close codes, request smuggling behavior of the chosen server |
| Files | Atomic write failure injection, Windows locked-JAR handling, concurrent save/restore races |
| Parsers | YAML alias bombs, deep nesting, huge inputs, malformed JAR/zip-slip |
| Secrets | Log redaction, error payload inspection, webhook body inspection |
| Supply chain | Dependency review, CodeQL, secret scanning, Scorecard, release signature verification once implemented |

## Open questions

- The exact approval policy (roles, expiry, two-person rule, break-glass) is not finalized.
- Whether audit records leave the host by default (and how) is undecided.
- Encryption at rest for snapshots and audit is not planned initially; it needs a decision
  if snapshots are ever stored off-host.
- Detection of a misconfigured Cloudflare Access policy (for example, an open policy) has no
  design yet beyond documentation.
- Rate-limit and lockout thresholds need measurement before they are treated as tuned.

## Related documents

- [Architecture overview](../../docs/architecture/overview.md)
- [Sandbox design and protected paths](../../docs/adr/ADR-0006-sandbox-and-protected-paths.md)
- [Authentication modes and Cloudflare JWT](../../docs/adr/ADR-0005-authentication-and-cloudflare-jwt.md)
- [Snapshot storage format](../../docs/adr/ADR-0007-snapshot-storage-format.md)
- [Deployment state machine](../../docs/architecture/deployment-state-machine.md)
- Master specification, Part 9.
