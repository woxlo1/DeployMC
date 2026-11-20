# Continuous integration

GitHub Actions is the authority for merge readiness. All third-party actions are pinned by commit
SHA, workflows use least-privilege `permissions`, and concurrent runs for the same ref are cancelled.

## Workflows

| Workflow | Trigger | What it enforces |
|---|---|---|
| `ci.yml` | Push to `main`, pull requests, manual | Java build/tests, final-JAR verification, Java 8 bytecode, and plugin JAR size report on Ubuntu and Windows; pinned Paper smoke harness with a conflicting-library fixture and before/after-disable thread dumps plus offline harness tests on Ubuntu and Windows; Web lint, formatting, tests, and production build; CLI formatting, clippy, tests, and build on Ubuntu, Windows, and macOS; Python ruff, format, mypy, and pytest on Ubuntu and Windows; specification lint and checker tests; i18n structural check, checker tests, and a generated-status drift check |
| `lint.yml` | Pull request opened, edited, synchronized, reopened | Conventional Commits via commitlint and the pull request title rules |
| `codeql.yml` | Push to `main`, pull requests, weekly schedule, manual | CodeQL analysis for Java/Kotlin, JavaScript/TypeScript, and Python |
| `dependency-review.yml` | Pull request | Fails on newly introduced dependencies with high or critical advisories |
| `scorecard.yml` | Weekly schedule, push to `main`, branch protection changes, manual | OpenSSF Scorecard, published to code scanning |
| `labels.yml` | Push to `main` changing `.github/labels.tsv` or the workflow, manual | Creates/updates repository labels from the TSV using the `gh` CLI; never deletes |

## Repository settings

These are configured in GitHub, not in a workflow file:

- Private vulnerability reporting is enabled; `SECURITY.md` links to it.
- Dependabot alerts and the dependency graph are enabled (`PUT /repos/{owner}/{repo}/vulnerability-alerts`).
  Dependency review fails closed when the graph is unavailable.
- Secret scanning and push protection are enabled.
- Dependabot update pull requests are configured for Gradle, npm in `web/` and `tools/spec/`, Cargo,
  pip, and GitHub Actions. See [community configuration](community.md).
- `main` requires linear history, forbids force-pushes and deletion, requires conversation resolution,
  and applies every rule to administrators.
- Required status checks are enforced with `strict` (the branch must be up to date):
  `Java (ubuntu-latest)`, `Java (windows-latest)`, `Web`, `CLI (ubuntu-latest)`,
  `CLI (windows-latest)`, `CLI (macos-latest)`, `Python (ubuntu-latest)`, `Python (windows-latest)`,
  `Spec`, `i18n`, `Compatibility (ubuntu-latest)`, `Compatibility (windows-latest)`,
  `Commit messages`, `Pull request title`, `Dependency review`, `Analyze (java-kotlin)`,
  `Analyze (javascript-typescript)`, and `Analyze (python)`.

## Not yet enabled

These gates depend on files that do not exist yet and will be added once their work packages land:

- Documentation link check (the architecture, ADR, compatibility, and status documents now exist,
  but no link checker is configured yet).
- Spelling check for English prose.
- Breaking-change detection for OpenAPI/AsyncAPI. Syntax lint is enabled; an API diff gate needs
  a published baseline release.
- Full compatibility integration suite and support-tier promotion. The v1 Paper smoke harness
  exists but does not earn a support tier.

## Local equivalents

Run every language toolchain at once with `node tools/build/verify-all.cjs` from the repository root.
It mirrors the commands below, reports a `PASS`/`FAIL` line per toolchain, and applies the Windows
non-ASCII path workarounds. Use `--only=java,web,...` for a subset. See
[`tools/build/README.md`](../../tools/build/README.md) for details. A local pass does not replace the
required checks on a pull request.

Individual commands, useful for investigating one failure:

| CI job | Local command |
|---|---|
| Java | `.\gradlew.bat build verifyJava8` (includes `:plugin:verifyPluginJar` through `check`) |
| Plugin JAR verification | `.\gradlew.bat :plugin:verifyPluginJar`; fixtures: `.\gradlew.bat :plugin:generatePluginJarFixtures` then `.\gradlew.bat --project-prop=deploymc.verifyJarFile=build/fixtures/<name>.jar :plugin:verifyPluginJarFixture` |
| Plugin JAR size | `.\gradlew.bat :plugin:reportPluginJarSize` |
| Compatibility harness | `python -m unittest discover -s tests/compat-matrix -p 'test_*.py'`; full entry: `python tests/compat-matrix/harness.py run --entry paper-26.3 --plugin-jar plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar --conflict-plugin-jar tests/fixtures/conflicting-plugin/build/libs/legacy-netty-fixture-1.0.0.jar --java <path-to-Java-25>` |
| Web | `pnpm lint && pnpm format:check && pnpm test && pnpm build` in `web/` |
| CLI | `cargo fmt --all -- --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace` in `cli/` |
| Python | `python -m ruff check . && python -m ruff format --check . && python -m mypy && python -m pytest` in `sdk/python/` |
| Spec | `npm ci && npm run lint && npm test` in `tools/spec/` |
| i18n | `node tools/i18n/check && node --test tools/i18n/test/check.test.cjs` |
| Commit messages | `npx commitlint --from <base> --to <head>` with `commitlint.config.cjs` |

Windows paths containing non-ASCII characters can break Gradle test workers and the GNU linker. See
`environment.md` for the junction and `CARGO_TARGET_DIR` workarounds. CI runners use ASCII paths and
are not affected.
