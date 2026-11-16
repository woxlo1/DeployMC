# AGENTS.md - DeployMC

Read this file and `docs/development/HANDOFF.md` before doing anything.

## Mission

Build DeployMC: a secure, multi-version, multi-platform, multi-language deployment and collaboration
tool for Minecraft server plugin directories, with a first-class public API. The complete specification
is `docs/development/DeployMC_Complete_Master_Development_Specification.md`.

## Non-negotiable rules

1. Never commit directly to `main` after the bootstrap commit. Branch -> commits -> PR -> CI green -> merge.
2. Use Conventional Commits in English: `<type>(<scope>): <description>`. Allowed types and scopes are
   defined in Part 12.3 of the development specification and in `docs/development/commit-convention.md`.
3. If an AI tool contributed to a commit, add the accurate `Co-authored-by` trailer as the last line.
   For OpenCode, use: `Co-authored-by: opencode <221189863+opencode-agent@users.noreply.github.com>`.
   Never add trailers for anyone who did not contribute.
4. Never claim a test passed unless it was run. Report the exact command and result.
5. Never claim something is implemented, supported, or released unless verified. The README must match reality.
6. Priority order: security and data safety > correctness and honesty > recoverability > compatibility >
   operability > UX > feature breadth > code volume and schedule.
7. No unauthenticated endpoint except `/healthz` and `/readyz`. No bypass flags. Fail closed.
8. All file access goes through the sandbox resolver. Never construct files from raw request input.
9. No NMS or server internals. Feature-detect and report capabilities.
10. Java in `core`, `api`, `transport`, and `platform/*` must compile with `--release 8`.
11. User-facing strings go through i18n catalogs. No hard-coded UI or plugin message strings.
12. No secrets, real tokens, or real hostnames in the repository. Use obviously fake examples.
13. Verify time-sensitive facts (versions, Cloudflare behavior, GitHub criteria) against official
    documentation and record evidence with access dates.
14. Update `docs/development/{project-status,implementation-status,compat-status,i18n-status,HANDOFF}.md`
    at the end of each session.
15. Talk to the project owner in Japanese. Repository content is English except localized content.

## Repository state

Phase 0 (foundation). The Java and Web skeletons exist and are verified; the CLI and Python
skeletons are not added yet.

Verified commands (2026-11-16, Windows 11, Gradle 8.10.2 wrapper, JDK 21 toolchain):

```powershell
.\gradlew.bat build            # compiles all Java modules, runs 32 Java tests, runs Spotless check
.\gradlew.bat verifyJava8      # checks production class files target Java 8 bytecode
.\gradlew.bat spotlessApply    # formats Java sources and repository text files
```

Verified Web commands (2026-11-16, Node.js 24.13.1, pnpm 9.15.4), run from `web/`:

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm format:check
pnpm test                      # 13 Vitest tests
pnpm build                     # tsc --noEmit, then Vite production build
```

On Windows, Gradle test workers cannot load test classes when the absolute repository path contains
non-ASCII characters. This machine's path contains `ドキュメント`; create an ASCII junction and run
Gradle through it (see `docs/development/environment.md`):

```powershell
cmd /c mklink /J C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii "<repository path>"
```

Do not document a command here before it has been executed successfully.

## Where things live

| Path | Purpose |
|---|---|
| `core/` | Platform-independent Java 8 core (sandbox, snapshot, deploy, auth, rbac, audit, api layer) |
| `api/` | Public Java API published to Maven Central |
| `transport/` | Embedded HTTP/WebSocket server |
| `platform/` | Platform adapters: bukkit, folia, velocity, bungee, standalone |
| `plugin/` | Shadow JAR aggregator and plugin descriptors |
| `web/` | React + Vite + TypeScript Web UI |
| `cli/` | Rust CLI |
| `sdk/` | TypeScript, Python, and Java clients |
| `actions/` | GitHub Action |
| `specification/` | Normative API, configuration, audit, and deployment specifications |
| `docs/` | Architecture, ADRs, research, guides, compatibility, development, i18n |
| `tests/` | Unit support, integration, compatibility matrix, E2E, security, fuzz, performance |
| `tools/` | Release, i18n, and matrix generation scripts |

## Definition of done

Implemented completely, tested (and the tests actually run), documented, localized where user-facing,
compatibility impact assessed, security considered, reviewed, CI green, and merged through a PR.
