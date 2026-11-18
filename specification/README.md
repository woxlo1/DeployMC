# Specification skeletons

> **Status: 2026-11-18.** These documents define intended contracts. No server, loader,
> engine, or audit writer implements them yet. A skeleton is normative for design work,
> not evidence that a feature works.

| Document | Defines | Implementation status |
|---|---|---|
| [api/openapi.yaml](api/openapi.yaml) | REST API skeleton: health, server info, shared Problem errors, pagination, ETag, and security components | Not implemented |
| [api/asyncapi.yaml](api/asyncapi.yaml) | WebSocket envelope, channels, subscribe/unsubscribe/ping, and event schemas | Not implemented |
| [api/errors.md](api/errors.md) | Stable error-code catalog and stability rules | Not implemented |
| [config/config.schema.json](config/config.schema.json) | JSON Schema for the host-controlled `config.yml` | No loader |
| [config/example.config.yaml](config/example.config.yaml) | Complete schema-valid example | Example only |
| [audit/audit-log-format.md](audit/audit-log-format.md) | JSON Lines audit record, hash chain, rotation, verification | No writer |
| [deployment/state-machine.md](deployment/state-machine.md) | Normative deployment transition data and invariants | No engine |
| [security/threat-model.md](security/threat-model.md) | Threat model draft | Draft |

## How these documents are checked

[`tools/spec/`](../tools/spec/) lints the OpenAPI and AsyncAPI documents, validates the
example configuration against the JSON Schema plus cross-reference and path rules, and
checks the deployment transition graph. CI runs `npm ci && npm run lint && npm test` in
that directory as the `Spec` job.

The checks prove that the specifications are internally consistent. They do not prove
that an implementation conforms; contract tests against a running server are a later work
package.

## Change rules

- Change the specification first, then the implementation, then the contract tests.
- A breaking API change requires a new major API version. See
  [ADR-0009](../docs/adr/ADR-0009-public-api-versioning.md).
- A new error code must be added to [api/errors.md](api/errors.md) in the same change that
  produces it.
- The normative deployment transition data is the fenced YAML block in
  [deployment/state-machine.md](deployment/state-machine.md); update the checker and tests
  when its rules change.
