# ADR-0001: Monorepo structure

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner, maintainers
- **Related:** [Architecture overview](../architecture/overview.md), [ADR-0014](ADR-0014-platform-support-tiers.md)

## Context

DeployMC consists of a Java plugin, a Web UI, a Rust CLI, Python and other SDKs, a GitHub
Action, normative API specifications, documentation, and a compatibility harness. These
parts share one version story and change together: an API field, error code, or deployment
state affects the Java server, the Web UI, the CLI, and the tests at the same time.

The project is developed by a very small team and an AI agent. Coordination overhead and
the risk of drifting versions are significant. At the same time, the artifacts are
distributed through different channels (Maven Central, npm, PyPI, crates.io, GitHub
Releases) and have different toolchains.

## Problem

Should the project live in one repository or in several repositories split by language or
artifact?

## Considered alternatives

### Option A — One monorepo

- All source, specifications, tests, and tooling in one repository.
- Advantages: atomic cross-cutting changes; one issue tracker, CI configuration, and
  release version; specifications and clients can be reviewed together; a fresh
  contributor sees the whole system.
- Disadvantages: larger clone; CI must avoid rebuilding unrelated paths; language
  toolchains coexist; repository-level permissions are coarse.

### Option B — Polyrepo per language or artifact

- Separate repositories for the plugin, Web UI, CLI, SDKs, and specifications.
- Advantages: independent release cadence and access control; smaller checkouts.
- Disadvantages: cross-repository version skew; coordinated changes require multiple
  pull requests; duplicated CI and documentation; specifications can drift from
  implementations; much higher overhead for a small team.

### Option C — One language with generated clients

- A single Java or TypeScript codebase with generated clients for other languages.
- Advantages: fewer toolchains.
- Disadvantages: forces an unnatural runtime on the CLI or browser; loses the benefits of
  Rust for static binaries and of TypeScript for the UI; contradicts the specification's
  explicit multi-language rationale.

## Decision

Use one monorepo with clear module boundaries:

- `core`, `api`, `transport`, `platform/*`, and `plugin` for Java;
- `web/` for the React UI;
- `cli/` for the Rust workspace;
- `sdk/` for language clients;
- `actions/` for the GitHub Action;
- `specification/` for normative API, configuration, and format documents;
- `docs/`, `tests/`, `tools/`, and `examples/` for documentation, verification, and
  support material.

Artifacts are published independently when their release process exists, but they share
the repository version until a documented reason justifies divergence. Generated code and
specifications are reviewed together.

## Consequences

### Positive

- One pull request can change a specification, the server, the client, and the tests.
- One CI graph and one issue tracker.
- Easier review of security and compatibility changes across components.

### Negative and costs

- CI must use path filters or intelligent caching to stay fast; this is not implemented
  yet.
- A clone contains toolchains a given contributor may not need.
- Repository permissions cannot separate, for example, SDK maintainers from plugin
  maintainers. This is acceptable for the current team size.
- A broken shared specification can block several languages at once.

### Follow-up

- Add path-aware CI optimization when build time becomes a problem.
- Keep module dependency rules enforced in review; a build-time module-boundary check can
  be added later.
- Document the release process per artifact before the first publication.

## Evidence and validation

- The repository already contains the Java Gradle multi-project, `web/`, `cli/`,
  `sdk/python/`, `tools/i18n/`, and documentation structure.
- CI run
  [44039215763](https://github.com/woxlo1/DeployMC/actions/runs/44039215763) passed the
  Java, Web, CLI, Python, and i18n jobs from the monorepo on 2026-11-17.
- This decision is organizational; it does not need a runtime prototype.

## References

- Master specification, Part 6 (repository structure).
- [docs/development/ci.md](../development/ci.md) for the current workflow layout.
