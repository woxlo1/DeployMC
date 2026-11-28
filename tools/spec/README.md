# Specification tools

This package checks the DeployMC specification skeletons and generates the narrow Web client
surface from the OpenAPI document. It does not run a server.

## Commands

From this directory:

```sh
npm ci
npm run lint
npm test
```

`npm run lint` runs four independent checks:

| Check | Tool | Target |
|---|---|---|
| `lint:openapi` | `@redocly/cli` with `redocly.yaml` | `specification/api/openapi.yaml` |
| `lint:asyncapi` | local script using `@asyncapi/parser` | `specification/api/asyncapi.yaml` |
| `lint:config` | local script using `ajv` and `yaml` | `specification/config/example.config.yaml` against `config.schema.json` |
| `lint:state-machine` | local script using `yaml` | the normative YAML block in `specification/deployment/state-machine.md` |

`npm test` runs `node:test` cases for the local checkers and the Web client generator, including
negative cases such as an invalid or empty AsyncAPI document, an undefined role reference, an
illegal deployment transition, and stale generated output.

## Web client generation

`npm run generate:web-client` reads `specification/api/openapi.yaml` and writes
`web/src/api/generated.ts`. The generated module contains the API version, a SHA-256 of the source
document, operation descriptors derived from paths and parameters, and TypeScript types for the
component schemas. `npm run check:web-client` regenerates in memory and fails on drift; the main
`npm run lint` script includes it so CI fails when a specification change is not regenerated.

The generator intentionally covers the subset of OpenAPI 3.1 used by this repository. It rejects
unknown `$ref` targets and duplicate operation ids instead of guessing. It does not invent runtime
behavior, authentication state, or response validation; those live in `web/src/api/client.ts`.
The generated file is excluded from Prettier because its formatting is owned by the generator.

All paths are resolved from this file's location, so the commands also work when invoked
as `npm run lint --prefix tools/spec` from the repository root (the form used in CI).

## What the config checker adds beyond JSON Schema

JSON Schema cannot check cross-references or relative-path containment. `check-config.cjs`
therefore also rejects:

- a `default-role`, user role, or approver role that is not defined in `roles`;
- a role limit greater than the global limit;
- `auth.token.default-ttl-days` greater than `max-ttl-days`;
- absolute paths or `..` escapes in the sandbox, snapshot, staging, and audit directories;
- a non-empty `approver-roles` requirement that is not satisfied.

It warns about unknown top-level keys, removed protective defaults, non-loopback binds,
symlink following, private webhook targets, and telemetry. Warnings do not fail CI.

## What the state-machine checker enforces

`check-state-machine.cjs` parses the fenced YAML block marked with
`state-machine-data:begin/end` and checks graph invariants: terminal states have no
outgoing transitions, every non-terminal state has an outgoing transition or recovery
action, cancellation is not possible after `APPLYING`, `RESTARTING` only leads to
`VERIFYING`, `ROLLBACK_FAILED` only leads to `SAFE_STATE`, `APPLYING` is entered only from
`SNAPSHOTTING`, and every state can reach a finish state.

## Dependencies

Versions are pinned in `package.json` and locked in `package-lock.json`. CI uses `npm ci`.
Third-party actions in the workflow are pinned by commit SHA. AsyncAPI validation calls the
official parser directly; the CLI plugin manager and its bundled `npm` dependency tree are not
installed. The parser's Spectral dependency emits a Node `punycode` deprecation warning; it does
not affect the result.

Status: this tooling checks specification consistency and generates the Web client surface. It is
not a contract test against a running implementation; server-free tests remain the verification
boundary in local CI.
