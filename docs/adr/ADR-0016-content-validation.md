# ADR-0016: Content validation parser and edit strategy

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** maintainers
- **Related:** [WP-2.5 issue #54](https://github.com/woxlo1/DeployMC/issues/54),
  [content validation contract](../../specification/validation/content-validation.md),
  [master specification](../development/DeployMC_Complete_Master_Development_Specification.md),
  [ADR-0002](ADR-0002-java-8-bytecode-baseline.md),
  [ADR-0004](ADR-0004-shading-and-relocation.md),
  [ADR-0015](ADR-0015-diff-engine-algorithm.md)

## Context

WP-2.5 requires server-authoritative validation of YAML, JSON, TOML, and `.properties` content, an
extensible registry keyed by extension and path pattern, warnings versus errors, save-time
enforcement, and a comment-preserving YAML helper for later config templates. Validation runs on
authenticated user content inside the plugin process, so a parser bug is a denial-of-service or code
execution risk. All `core` code must target Java 8 bytecode, be relocatable in the shaded plugin JAR,
and avoid hard-coded user-facing strings.

The repository already depends on SnakeYAML for host-controlled configuration. `ConfigYamlParser`
hardens that parser for one configuration file, but it is not a general file validator: it uses zero
alias expansion and a depth of 32, and it reports config-specific localized exceptions rather than
stable per-file findings. No JSON, TOML, or properties library is present in `core/build.gradle.kts`.

## Problem

Which parsing and editing strategy should the general validator use without adding an unbounded
attack surface, breaking Java 8 compatibility, or overstating format conformance?

## Considered alternatives

### Option A — add mature JSON and TOML libraries

- Mature parsers provide broad format coverage and community testing.
- Adding dependencies increases the shaded JAR size, relocation work, dependency-review noise, and
  the number of libraries that need CVE monitoring. A TOML library with a small Java 8-compatible
  footprint still needs a hardening wrapper for byte, depth, alias, and issue budgets. The master
  specification requires an ADR and measurement before choosing a JSON library.

### Option B — use SnakeYAML as a universal parser

- YAML is a superset of JSON, so one parser could cover two formats.
- SnakeYAML does not parse TOML or Java properties syntax. Using YAML semantics for `.json` would
  accept non-JSON input and produce misleading findings. It does not preserve comments on emit.

### Option C — harden the existing SnakeYAML for YAML, use bounded in-house parsers for the rest

- SnakeYAML remains the YAML parser with a validator-specific loader: standard tags only, duplicate
  keys disabled, recursive keys disabled, explicit alias and node budgets, and source marks for
  positions. Small recursive-descent parsers handle the JSON grammar and the defined TOML and
  properties profiles with explicit limits and no execution features.
- The validator carries the test burden for those parsers. Unsupported TOML constructs must be
  rejected as `validation.toml-unsupported` rather than silently accepted. The parsers must be
  fuzzed or corpus-tested before the work package is called complete.

### Option D — regex-based structural checks

- Cheap and small.
- Regular expressions cannot safely validate nested JSON/TOML or YAML aliases, can be
  catastrophic without a step budget, and cannot produce correct key paths and positions.

### Option E — full YAML round-trip emitter for edits

- A comment-preserving AST emitter would support arbitrary edits.
- SnakeYAML's composed node graph discards comments. Building and proving a full round-trip emitter
  is a larger project than WP-2.5 and could change untouched bytes. The immediate need is scalar
  value updates for config templates.

## Decision

Adopt Option C with a narrow Option E alternative for edits:

1. Reuse the already pinned SnakeYAML with validator-specific hardened options. Do not reuse
   `ConfigYamlParser` directly because its configuration-specific limits and exceptions are not the
   validator contract.
2. Implement bounded Java 8 recursive-descent validators for strict JSON, the documented TOML
   profile, and Java properties syntax. Add no new dependency for WP-2.5.
3. Represent findings as stable codes, RFC 6901-style pointers, safe parameters, optional 1-based
   positions, and severity. Keep localized prose in `validation-messages[_ja].properties`.
4. Register built-ins by extension and allow host registrations by exact path or glob pattern with
   deterministic precedence. Unmatched files are reported as unmatched, not as validated.
5. Enforce validation in `FileOperationsService` after preconditions and before snapshot and target
   mutation. A gate failure or unexpected validator failure is fail-closed.
6. Provide `YamlScalarEditor` as a byte-preserving single-scalar editor. It never claims a full
   YAML round trip and rejects unsupported paths, anchors, aliases, multi-line values, and unsafe
   plain-scalar replacements.
7. Defer public Java API registration of third-party validators to a later API work package.

Explicitly out of scope: full TOML 1.0 conformance, arbitrary YAML round-trip editing, semantic
validation of plugin behavior, custom YAML tags, and any request-controlled bypass of validation.

## Consequences

### Positive

- No new third-party dependency or relocation change for WP-2.5.
- A single stable finding model serves the validation endpoint, the editor, and save-time errors.
- Explicit profiles prevent accidental claims that malformed or unsupported syntax was validated.
- The comment-preserving helper has a testable byte-for-byte contract.

### Negative and costs

- The in-house parsers need thorough syntax, boundary, and hostile-input tests before release.
- TOML users may see `validation.toml-unsupported` for valid TOML 1.0 constructs outside the profile.
- A host that needs a different parser must register a custom validator in `core`; the public Java
  API extension point is not part of this work package.
- YAML edits cannot move keys, edit arrays, or rewrite multi-line scalars.

### Follow-up

- `core.validation` and the `FileOperationsService` gate described in the contract are implemented.
- Add the `POST /api/v1/files/validate` route in WP-2.6 with contract tests generated from OpenAPI.
- Resolve D-008: the contract lists YAML node/depth finding codes, but the implemented budget breach
  follows the save-time limit path. WP-2.6 must map this consistently before endpoint tests are final.
- Add a later ADR before exposing validator registration through the public Java API.

## Evidence and validation

- **Decided from design (2026-11-21):** the parser boundaries, dependency choice, finding model, and
  edit limits are selected by this ADR and the normative contract.
- **Implemented (2026-11-21):** the four validators use the selected strategy with no new runtime
  dependency. A deterministic corpus covers syntax, hostile encodings, duplicate keys, tabs, tags,
  aliases and cycles, depth/node/code-point boundaries, comments, and byte-preserving edits. No
  randomized fuzz harness was added in this work package.
- **Verified locally (2026-11-21):** 27 focused validation tests passed; the full Gradle build
  produced 288 JUnit XML tests with 0 failures, 0 errors, and 1 skipped symlink test;
  `verifyJava8`, `node tools/i18n/check`, `tools/spec` lint, and `node tools/build/verify-all.cjs`
  passed with all seven toolchains reporting `PASS`. Exact commands and JAR verification output are
  recorded in [project status](../development/project-status.md#wp-25-implementation-verification-local-pre-merge).
- **Verified in CI (2026-11-21):** all required checks passed for PR #166; post-merge CI, CodeQL, and
  Scorecard succeeded on `main` at `9363e0c`. Run IDs and evidence links are recorded in
  [project status](../development/project-status.md#wp-25-implementation-merge-evidence) and the
  implementation PR comment.
- **Not verified:** no performance benchmark, TOML 1.0 conformance claim, randomized fuzz result, or
  full YAML round-trip claim is made.

## References

- [Content validation contract](../../specification/validation/content-validation.md)
- [WP-2.5 issue #54](https://github.com/woxlo1/DeployMC/issues/54)
- [Master specification, parts 4.1, 24.10, 32.3, and 33.2](../development/DeployMC_Complete_Master_Development_Specification.md)
- [ADR-0002: Java 8 bytecode baseline](ADR-0002-java-8-bytecode-baseline.md)
- [ADR-0004: Shading and relocation](ADR-0004-shading-and-relocation.md)
- [ADR-0015: Diff engine algorithm](ADR-0015-diff-engine-algorithm.md)
