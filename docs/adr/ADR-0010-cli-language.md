# ADR-0010: CLI language selection

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** maintainers
- **Related:** [API layering](../architecture/api-layering.md), [ADR-0009](ADR-0009-public-api-versioning.md)

## Context

DeployMC needs a command-line client for local use, CI, and automation. It must run on
Linux, macOS, and Windows without requiring a JVM, provide reliable exit codes, handle
JSON output, support shell completion, and be easy to distribute as a single binary.

The CLI talks only to the public HTTP/WebSocket API; it does not embed server internals.
It must be testable without a running server and should not add a large runtime
dependency for users who only want to deploy a JAR.

## Problem

Which language and runtime should the CLI use?

## Considered alternatives

### Option A — Java

- Reuse the server's language and tooling.
- Advantages: one language; shared value objects possible.
- Disadvantages: requires a JVM on the user's machine; startup and distribution are
  heavier; does not exercise the public API as an independent client.

### Option B — Go

- Static binaries and a simple deployment story.
- Advantages: easy cross-compilation; small binaries; good HTTP tooling.
- Disadvantages: a second language with its own conventions; less memory safety than
  Rust; the team has no existing Go code.

### Option C — Rust

- Compiled binary with strong safety and a mature CLI ecosystem.
- Advantages: no runtime; predictable binaries; good HTTP and JSON libraries; strong
  error handling.
- Disadvantages: slower compiles; a smaller contributor pool; cross-compilation and TLS
  choices need care.

### Option D — Python

- Fast to write and familiar to many administrators.
- Advantages: readable; rich ecosystem.
- Disadvantages: requires a Python runtime and dependency management; startup and
  packaging are less predictable; not ideal for a single-binary distribution.

### Option E — TypeScript/Node.js

- Reuse the Web UI language and generated types.
- Advantages: shared API types; fast development.
- Disadvantages: requires Node.js; larger runtime footprint; not a single binary without
  extra packaging.

## Decision

Use Rust 2021 with `clap` 4 (derive) as the CLI language and framework:

- The workspace lives in `cli/` with `crates/deploymc-cli`.
- Use `reqwest` with rustls for HTTPS; avoid OpenSSL and native TLS dependencies where
  possible.
- Use `tokio` only where async I/O is actually needed; do not add it for its own sake.
- Follow the documented exit codes and JSON output schema. Human output and JSON output
  are both first-class.
- The CLI uses the public API only; it does not link the Java modules.
- Release builds target Linux, macOS, and Windows with static or self-contained runtime
  dependencies where feasible.
- Shell completion and `--no-color`/`--quiet`/`--verbose` behavior follow the master
  specification's CLI section.

The current skeleton uses `clap`; HTTP, authentication, and the full command set are not
implemented yet.

## Consequences

### Positive

- A single binary with no JVM requirement.
- Strong error handling and predictable exit behavior.
- The CLI is a real conformance client for the public API.

### Negative and costs

- A second compiled language increases CI time and toolchain setup.
- Cross-compilation and TLS/root-certificate behavior need testing per OS.
- Contributors who know Java or TypeScript may not know Rust.
- The CLI cannot reuse Java value objects; it depends on the OpenAPI contract and
  generated or hand-written types.

### Follow-up

- Add HTTP, authentication, deployment, file, snapshot, console, and audit commands.
- Add JSON-schema tests and a conformance suite against a real server.
- Verify static binaries and exit codes on all three operating systems.
- Document installation and shell completion.

## Evidence and validation

- The Rust workspace, `clap` dependency, formatter, clippy, and unit tests exist.
- CI run
  [44039215763](https://github.com/woxlo1/DeployMC/actions/runs/44039215763) passed the
  CLI jobs on `ubuntu-latest`, `windows-latest`, and `macos-latest` (format, clippy,
  tests, build) on 2026-11-17.
- **Not verified:** the CLI does not talk to a server yet; there is no HTTP client,
  authentication, or conformance test. The language choice is verified as buildable, not
  as a complete product.

## References

- Master specification, sections 4.3 and 24.6.
- `cli/Cargo.toml`.
- [docs/development/ci.md](../development/ci.md) for the CLI CI jobs.
