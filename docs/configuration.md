# Configuration

Last updated: 2026-11-23. This page describes the configuration loader in `core` and its Bukkit
startup and `/deploymc` command integration.

## Location and ownership

`plugins/DeployMC/config.yml` is host-controlled. It is created with extensive comments on first
start if it does not exist. API requests can never read or write this file: platform code receives
an immutable `ConfigView` with no write methods, and its returned maps and lists are recursively
unmodifiable. Only a local administrator may edit the file.

The normative schema is [`specification/config/config.schema.json`](../specification/config/config.schema.json).
The loader validates against a copy packaged on the classpath, so the schema travels with the
plugin.

## Startup and validation

Startup performs these steps in order:

1. Write the commented default template if `config.yml` is absent.
2. Parse YAML safely. Duplicate keys, aliases, recursive keys, non-scalar mapping keys,
   non-standard global tags, excessive nesting, and excessive input size are rejected.
3. Read `config-version`. A future version fails closed; an older version requires a registered
   migration.
4. Before applying a migration, create a timestamped sibling backup such as
   `config.yml.backup-20261118T123456Z`. The migrated file is written atomically and validated
   again.
5. Validate all values against the schema. Findings carry a 1-based line and column.
   Invalid values, including unknown keys in sections with `additionalProperties: false`, stop
   startup. Unknown keys at the root produce warnings and never enable a feature.
6. Check role integrity. `default-role`, user role grants, and approval roles must be `none` or
   name a role defined in `roles`.

There is no permissive fallback. A parse, version, migration, validation, or role-reference failure
prevents activation of the configuration.

On Bukkit-family servers, `DeployMCPlugin.onEnable()` loads the configuration before creating the
platform adapter. The validated `bind` and `auth.mode` values feed the startup plan and banner; the
previous hard-coded defaults are no longer used. If loading fails, startup is logged through the
localized `lifecycle.startFailed` message and the plugin disables itself.

## `/deploymc` commands

The generated `plugin.yml` registers `/deploymc` behind `deploymc.command.use` and declares six
operator-only permission nodes:

| Permission | Command |
|---|---|
| `deploymc.command.use` | Base command access. |
| `deploymc.command.status` | `status` |
| `deploymc.command.reload` | `reload` |
| `deploymc.command.doctor` | `doctor` |
| `deploymc.command.token` | `token` |
| `deploymc.command.audit` | `audit` |

All permission identifiers, subcommand names, and configuration keys are stable identifiers and are
never localized. The response text is localized from English and Japanese Bukkit resource bundles;
the catalog selected for a command follows the active configuration's `language` value.

- `status` reports the plugin version, config version, language, effective bind address and port,
  authentication mode, warning count, and config file path. It never prints secrets or token values.
- `reload` revalidates the file and reports changed and restart-required paths. A failed reload keeps
  the last known-good configuration active and reports the localized error.
- `doctor` reports checks that are actually implemented and marks areas without a product
  implementation as `NOT IMPLEMENTED`. It always adds a warning that the output is **not** a
  support-tier verdict.
- `token` is console-only. `token create [label]` provisions a host-stored token and prints the secret
  once. It never prints a stored hash or accepts a token supplied by an API request.
- `audit verify` checks the local hash chain and reports counts, the first stable failure code when
  present, and known limitations. It does not off-host anchor the log and never prints sealed record
  contents or absolute host paths.

Command dispatch and the `reload`, token create, and token revoke mutations are recorded through the
host audit store. If a mandatory audit write fails, the command is refused with a localized error
before the action runs. Tab completion offers only subcommands for which the sender holds the
matching permission.

## Safe reload

`ConfigManager.reload()` re-reads and revalidates the file before swapping the active view. If the
new file is invalid, the previous known-good view remains active and the failure is reported. A
successful report lists changed dotted paths and any paths that require a server restart.

Hot-reloadable settings include `language`, token TTL values, sandbox protected paths, deny
patterns and size limits, snapshot retention, most deployment verification, rollback, approval and
JAR-policy values, console buffer and command policy, audit rotation and retention, the API rate
limit, roles, users, `default-role`, webhook policy, and telemetry.

Restart-only settings include `config-version`, all `bind` values, `auth.mode`, every Cloudflare
setting (team domain, audience, clock skew and JWKS cache/staleness limits), `sandbox.root`,
`sandbox.follow-symlinks`, snapshot and staging directories, `deploy.restart.mode`,
`console.enabled`, `audit.directory`, `limits.max-websocket-connections`, and every `observability`
setting. The metrics endpoint is disabled by default; enabling it exposes only aggregate,
low-cardinality metrics behind the normal authentication gate.

In `auth.mode: cloudflare`, DeployMC verifies only `Cf-Access-Jwt-Assertion` with RS256 and derives
identity from verified claims. `Cf-Access-Authenticated-User-Email` and other forwarded identity
headers are never trusted. `require-shared-secret-header` is reserved and must stay empty because
no secret store or product consumer exists yet; a non-empty value fails validation.

## Diagnostics

Configuration messages are localized through English and Japanese Java resource bundles. Errors
include the path, line, column, and localized message. For example, an invalid authentication mode
is reported at `auth.mode` with the exact line and column of the offending value.

## Verification

Verified on 2026-11-18 and 2026-11-19 with the Gradle ASCII junction:

```powershell
.\gradlew.bat spotlessApply :core:test --console=plain
.\gradlew.bat spotlessApply :platform:bukkit:test --console=plain
.\gradlew.bat build verifyJava8 --console=plain
```

All 53 core tests and all 42 Bukkit tests passed. The full multi-module build passed, and the Java 8
bytecode check passed. The property test generated 250 deterministic invalid mutations of the default
configuration and verified that every one failed validation and never became active. The command
tests are server-free: they exercise permission checks, tab completion, status output, safe reload,
limited doctor coverage, and console-only token behavior without loading a Minecraft server. No real
server load, real command execution in-game, browser rendering, or conflicting-library classloader
check was performed; no support tier is claimed. The Japanese command strings are a machine-translated
draft awaiting native review.
