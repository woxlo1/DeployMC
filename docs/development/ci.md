# Continuous integration

GitHub Actions is the authority for merge readiness. All third-party actions are pinned by commit
SHA, workflows use least-privilege `permissions`, and concurrent runs for the same ref are cancelled.

## Workflows

| Workflow | Trigger | What it enforces |
|---|---|---|
| `ci.yml` | Push to `main`, pull requests, manual | Java build/tests and Java 8 bytecode on Ubuntu and Windows; Web lint, formatting, tests, and production build; CLI formatting, clippy, tests, and build on Ubuntu, Windows, and macOS; Python ruff, format, mypy, and pytest on Ubuntu and Windows; i18n structural check, checker tests, and a generated-status drift check |
| `lint.yml` | Pull request opened, edited, synchronized, reopened | Conventional Commits via commitlint and the pull request title rules |
| `codeql.yml` | Push to `main`, pull requests, weekly schedule, manual | CodeQL analysis for Java/Kotlin, JavaScript/TypeScript, and Python |
| `dependency-review.yml` | Pull request | Fails on newly introduced dependencies with high or critical advisories |
| `scorecard.yml` | Weekly schedule, push to `main`, branch protection changes, manual | OpenSSF Scorecard, published to code scanning |

## Not yet enabled

These gates depend on files that do not exist yet and will be added once their work packages land:

- Documentation link check (needs the architecture and tracking documents).
- Spelling check for English prose.
- OpenAPI/AsyncAPI lint and breaking-change detection (needs the specification skeletons).
- Compatibility harness and real-server smoke test.
- Secret scanning is enabled through GitHub repository settings rather than a workflow.

## Local equivalents

| CI job | Local command |
|---|---|
| Java | `.\gradlew.bat build verifyJava8` |
| Web | `pnpm lint && pnpm format:check && pnpm test && pnpm build` in `web/` |
| CLI | `cargo fmt --all -- --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace` in `cli/` |
| Python | `python -m ruff check . && python -m ruff format --check . && python -m mypy && python -m pytest` in `sdk/python/` |
| i18n | `node tools/i18n/check && node --test tools/i18n/test/check.test.cjs` |
| Commit messages | `npx commitlint --from <base> --to <head>` with `commitlint.config.cjs` |

Windows paths containing non-ASCII characters can break Gradle test workers and the GNU linker. See
`environment.md` for the junction and `CARGO_TARGET_DIR` workarounds. CI runners use ASCII paths and
are not affected.
