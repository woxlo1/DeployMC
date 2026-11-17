# Architecture Decision Records

An Architecture Decision Record (ADR) captures one significant decision, the alternatives
that were considered, and the consequences. ADRs are historical: an accepted decision
stays recorded even after it is replaced. A replacement is a new ADR that marks the old
one as superseded.

## Status vocabulary

| Status | Meaning |
|---|---|
| `Proposed` | The direction is under evaluation. It may change after a spike, benchmark, or owner decision. Do not treat it as settled. |
| `Accepted` | The direction is chosen and later work should follow it. Acceptance is not evidence that the decision was implemented or validated; each ADR records its own evidence. |
| `Superseded by ADR-NNNN` | A later ADR replaced this decision. The old record remains for history. |
| `Rejected` | The option was considered and deliberately not chosen. |
| `Deprecated` | The decision still describes current code but should not be used for new work. |

`Accepted` is used only when the decision itself is made. If an implementation detail
still needs a prototype (for example, the HTTP server choice), that detail stays
`Proposed` either inside the ADR or in a separate ADR.

## When to write one

Write or update an ADR when a change affects:

- a module boundary or dependency rule;
- a trust boundary, authentication mode, or authorization model;
- a persisted format (snapshots, audit log, deployment records);
- a public API shape, versioning rule, or compatibility promise;
- a platform contract or support-tier claim;
- a build, packaging, or release policy that is hard to reverse;
- a language, framework, or major dependency choice.

Small implementation choices inside an accepted boundary do not need an ADR.

## Process

1. Copy [`template.md`](template.md) to the next free number and a short slug.
2. Fill in Context, Problem, Considered alternatives, Decision, Consequences, Status, and
   Evidence.
3. Open a pull request. Discussion happens there; the owner makes owner-level decisions.
4. When accepted, update the index below in the same pull request.
5. If a later decision replaces it, add `Superseded by ADR-NNNN` to the old ADR and link
   both ways. Never rewrite history to pretend the old decision never existed.

Numbering is never reused. File names use zero-padded four-digit numbers.

## Index

| ADR | Title | Status | Date |
|---|---|---|---|
| [0001](ADR-0001-monorepo-structure.md) | Monorepo structure | Accepted | 2026-11-17 |
| [0002](ADR-0002-java-8-bytecode-baseline.md) | Java 8 bytecode baseline with a modern toolchain | Accepted | 2026-11-17 |
| [0003](ADR-0003-embedded-http-websocket-server.md) | Embedded HTTP/WebSocket server selection | Proposed | 2026-11-17 |
| [0004](ADR-0004-shading-and-relocation.md) | Shading and relocation strategy | Accepted | 2026-11-17 |
| [0005](ADR-0005-authentication-and-cloudflare-jwt.md) | Authentication modes and Cloudflare JWT verification | Accepted | 2026-11-17 |
| [0006](ADR-0006-sandbox-and-protected-paths.md) | Sandbox design and protected paths | Accepted | 2026-11-17 |
| [0007](ADR-0007-snapshot-storage-format.md) | Snapshot storage format | Accepted | 2026-11-17 |
| [0008](ADR-0008-deployment-state-machine.md) | Deployment state machine and rollback semantics | Accepted | 2026-11-17 |
| [0009](ADR-0009-public-api-versioning.md) | Public API versioning and spec-first workflow | Accepted | 2026-11-17 |
| [0010](ADR-0010-cli-language.md) | CLI language selection | Accepted | 2026-11-17 |
| [0011](ADR-0011-web-ui-framework-and-editor.md) | Web UI framework and editor selection | Accepted (editor choice Proposed) | 2026-11-17 |
| [0012](ADR-0012-localization-architecture.md) | Localization architecture | Accepted | 2026-11-17 |
| [0013](ADR-0013-license.md) | License selection | Accepted | 2026-11-17 |
| [0014](ADR-0014-platform-support-tiers.md) | Platform support tiers and compatibility harness | Accepted | 2026-11-17 |

## Notes on evidence

The repository is in Phase 0. Most ADRs record a design decision that has not been
validated against a running server. Each ADR has an **Evidence and validation** section
that states exactly what was run and what remains unproven. A reader should never infer
from the word `Accepted` that a feature works.
