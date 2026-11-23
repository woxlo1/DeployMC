# ADR-0011: Web UI framework and editor selection

- **Status:** Accepted (framework); editor choice Proposed
- **Date:** 2026-11-17
- **Deciders:** maintainers; owner for the editor trade-off
- **Related:** [API layering](../architecture/api-layering.md), [ADR-0012](ADR-0012-localization-architecture.md)

## Context

DeployMC's Web UI is embedded in the plugin and served from the same JAR. It must work in
current browsers without a Node.js runtime on the server. The primary screens are a file
tree, a text editor with syntax highlighting and server-authoritative validation, a
snapshot/diff view, a deployment status view, a console stream, and an audit viewer.

The UI is not a generic admin dashboard. It is a narrow, security-sensitive client of the
public API. It must handle conflicts, large files, localization, accessibility, and
degraded behavior when a platform capability is missing.

Bundle size matters because the assets are packaged in a plugin JAR and served over a
tunnel. Editor quality matters because editing YAML and JSON safely is a core feature.

## Problem

Which UI framework should be used, and which code editor should provide editing and
diffing?

## Considered alternatives — framework

### Option A — React

- Advantages: large ecosystem; strong TypeScript support; familiar to contributors;
  testing tools are mature.
- Disadvantages: bundle size and dependency churn require discipline; no built-in
  solution for every concern.

### Option B — Vue

- Advantages: approachable single-file components; good ecosystem.
- Disadvantages: smaller overlap with the existing skeleton and contributor pool; no
  decisive advantage for this product.

### Option C — Svelte

- Advantages: small runtime and concise components.
- Disadvantages: smaller ecosystem; fewer ready-made editor and accessibility
  integrations; team familiarity is lower.

### Option D — Angular

- Advantages: opinionated structure and tooling.
- Disadvantages: heavy for an embedded SPA; a steep learning curve; overkill for this
  surface.

## Considered alternatives — editor

### Option A — CodeMirror 6

- Advantages: modular, small baseline, good mobile support, extensible, strong diff
  options, accessible.
- Disadvantages: more assembly work; some language support is community-maintained;
  configuration is less batteries-included.

### Option B — Monaco

- Advantages: VS Code-grade editing, excellent language services and diff view.
- Disadvantages: large bundle and worker setup; heavier on low-end devices; embedding and
  asset serving are more complex.

### Option C — A simple textarea with highlighting

- Advantages: tiny and simple.
- Disadvantages: poor editing and diff experience; unacceptable for YAML/JSON work.

## Decision

Use React 18 with Vite 6 and TypeScript 5.7 in strict mode, plus ESLint, Prettier, and
Vitest, for the Web UI:

- The UI talks only to the public HTTP/WebSocket API.
- The production build is a static asset set served by the plugin; there is no Node.js
  runtime on the server.
- Server-side validation is authoritative. Browser-side validation is a convenience and
  must not be the only check.
- The UI must degrade when a capability is unavailable and must never fake success.
- The UI uses the message catalogs and ICU formatting described in
  [ADR-0012](ADR-0012-localization-architecture.md).
- Tailwind is not installed in the skeleton. Whether to add it is an open styling
  decision; the current CSS approach is not a long-term commitment.

The editor choice is **Proposed**. A benchmark will compare CodeMirror 6 and Monaco on:

1. production bundle size after compression, including workers;
2. diff-editor quality for text and structured files;
3. YAML/JSON/TOML and properties language support;
4. accessibility, keyboard behavior, and mobile usability;
5. theming, i18n, and CSP compatibility;
6. maintenance status and license.

Until the benchmark is recorded, no editor dependency is added to the production build.

## Consequences

### Positive

- A mainstream framework with a mature TypeScript and testing ecosystem.
- The static build fits the embedded deployment model.
- The editor decision is made on measured bundle and diff quality, not preference.

### Negative and costs

- React and editor dependencies require bundle-budget discipline.
- An embedded SPA needs careful cache headers, CSP, and asset versioning.
- Monaco would add significant weight if chosen; CodeMirror requires more integration
  work.
- The UI cannot rely on server-side rendering or a Node runtime.

### Follow-up

- Run and record the editor benchmark.
- Define a bundle-size budget in CI.
- Add accessibility and Playwright end-to-end tests.
- Decide the styling approach and document it.
- Verify that all assets are served correctly from the shaded JAR and through the tunnel.

## Evidence and validation

- The Web skeleton exists with React 18.3.1, Vite 6.0.5, TypeScript 5.7.2, Vitest 2.1.8,
  ESLint 9.17.0, and Prettier 3.4.2.
- CI run
  [44039215763](https://github.com/woxlo1/DeployMC/actions/runs/44039215763) passed the
  Web lint, format, test, and build job on 2026-11-17.
- **Not verified:** no editor dependency is installed, no benchmark has been run, and no
  real UI screen exists. The framework decision is verified as buildable; the editor
  decision is open.

## Spike evidence (2026-11-23)

[Embedded UI size spike 6](../research/spikes/06-embedded-ui-size.md) records the current no-editor
baseline: 155,810 raw bytes and 50,632 gzip bytes for the main JavaScript asset, plus a 380,370-byte
source map. The final JAR was reported at 4,721,584 bytes. These figures are a baseline for a future
budget; they are not a Monaco/CodeMirror benchmark, so the editor part of this ADR remains Proposed.

## References

- Master specification, sections 4.2 and 32.2.
- `web/package.json` for the exact current versions.
- [docs/development/ci.md](../development/ci.md) for the Web CI job.
