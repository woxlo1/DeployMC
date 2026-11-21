# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
intends to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once it publishes
releases. **Nothing here has been released.** Entries under `Unreleased` describe work on `main`.

## [Unreleased]

### Added

- Gradle multi-project Java skeleton (`core`, `api`, `transport`, `platform/*`, `plugin`) with Java 8
  bytecode enforcement, JUnit 5, and Spotless.
- API contracts, platform SPI and capability keys, transport bind configuration, a Bukkit version
  parser, and standalone configuration parsing with validation.
- Web UI skeleton using Vite, React, and TypeScript, with ESLint, Prettier, and Vitest; English and
  Japanese message catalogs, ICU plurals, locale persistence, and placeholder-safe pseudo-localization.
- Rust CLI workspace with a `deploymc` binary exposing `--version`, `--help`, and a local `info`
  command.
- Python SDK package with validated client configuration that enforces HTTPS off loopback, rejects
  credentials in URLs, and redacts tokens from representations.
- Message-catalog checker with tests, generated translation status, translation guide, and an
  AI-assisted Japanese README marked as unreviewed.
- Core project documents: contribution guide, code of conduct, security policy, support policy,
  roadmap, governance, authors, and this changelog.
- Authenticated file and validation REST adapters over the sandbox resolver, `core.files`, and
  `core.validation`: RFC 9457 Problem Details with localized findings, stable error codes,
  ETag/`If-Match`, byte ranges, cursor pagination, strict request parsing, and fail-closed snapshot
  configuration.

### Security

- Security policy with private vulnerability reporting enabled for the repository.
- File and validation routes remain behind the bearer-token gate. Until 0.4.0 every valid token is
  admin-equivalent and is not scoped by role or path; do not share tokens between principals with
  different permissions.

[Unreleased]: https://github.com/woxlo1/DeployMC/commits/main
