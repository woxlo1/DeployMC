# Commit and Branch Conventions

This document is the practical reference for Part 12.3 and Part 12.4 of the development specification.

## Branches

Never develop directly on `main` after the bootstrap commit. Use one of these prefixes:

```
feature/<description>
fix/<description>
docs/<description>
spec/<description>
refactor/<description>
test/<description>
build/<description>
ci/<description>
i18n/<description>
perf/<description>
security/<description>
```

Keep each branch focused on one understandable unit of work.

## Commit messages

Format:

```
<type>(<scope>): <description>

<body>

<footer>
```

### Types

`feat`, `fix`, `docs`, `spec`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `revert`, `i18n`,
`security`.

### Scopes

`core`, `api`, `sandbox`, `snapshot`, `deploy`, `auth`, `rbac`, `audit`, `console`, `transport`,
`platform-bukkit`, `platform-folia`, `platform-velocity`, `platform-bungee`, `standalone`, `web`,
`cli`, `sdk-ts`, `sdk-py`, `sdk-java`, `action`, `docs`, `compat`, `i18n`, `release`.

A scope is optional but preferred when it clarifies the area.

### Rules

- Write all commit messages in English.
- Use the imperative mood: `add`, `fix`, `document`, not `added` or `fixes`.
- Do not end the subject with a period.
- Keep the subject at or below 72 characters.
- Add a body when the change is not self-evident; explain what changed and why.
- Reference issues in the body or footer, for example `Closes #12`.
- Use `!` after the type or scope and a `BREAKING CHANGE:` footer for breaking changes.
- Do not combine an entire feature into one enormous commit, and do not create meaningless commits.

### Examples

```
docs: add repository bootstrap and editor configuration
spec(api): define the server resource
build(core): initialize the Gradle multi-project build
feat(sandbox): implement canonical path resolution
fix(deploy): handle update-folder collision on Windows
test(sandbox): add traversal payload corpus
i18n(web): add Japanese message catalog
ci(compat): add Paper 1.21 integration job
```

## AI assistance attribution

When an AI coding tool genuinely contributes to a commit, add an accurate `Co-authored-by` trailer as
the last non-empty line.

For OpenCode:

```
Co-authored-by: opencode <221189863+opencode-agent@users.noreply.github.com>
```

Never add a trailer for a person or tool that did not contribute.

## Pull request titles

PR titles use the same Conventional Commits format because squash merges preserve the PR title as the
commit subject. CI validates the PR title.
