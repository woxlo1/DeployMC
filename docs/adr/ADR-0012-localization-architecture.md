# ADR-0012: Localization architecture

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner, maintainers
- **Related:** [ADR-0011](ADR-0011-web-ui-framework-and-editor.md), [ADR-0009](ADR-0009-public-api-versioning.md)

## Context

The project owner wants multi-language support from the beginning, with English as the
canonical language and Japanese as the first maintained translation. Localization affects
the Web UI, plugin messages, API error text, the README, and documentation. Retrofitting
it after features exist is expensive and produces hard-coded strings, broken plural rules,
and untranslatable error messages.

Translations must not become a source of security bugs. An error code must stay stable
even when its human-readable message changes.

## Problem

How should user-facing strings be authored, translated, validated, and shipped across the
Web UI, the plugin, the API, and documentation?

## Considered alternatives

### Option A — Hard-coded English, translate later

- Write strings directly in components and Java code.
- Advantages: fastest initial development.
- Disadvantages: extraction is error-prone; plural and placeholder bugs appear late;
  translators need code access; retrofitting touches every feature.

### Option B — gettext `.po` files

- Use a mature translation format.
- Advantages: good tooling and translator familiarity.
- Disadvantages: a poor fit for the browser without additional tooling; plural handling
  differs from ICU; not a natural match for the TypeScript UI.

### Option C — JSON catalogs with ICU MessageFormat

- Stable keys, ICU messages, one catalog per locale.
- Advantages: works in the browser and in Java tooling; explicit placeholders and plural
  categories; easy structural validation; readable in review.
- Disadvantages: translators need context comments; ICU has a learning curve; large
  catalogs need organization.

### Option D — Platform resource bundles only

- Use Java `ResourceBundle` for everything.
- Advantages: built into the JDK.
- Disadvantages: not usable directly by the Web UI; properties escaping is awkward for
  rich messages; does not solve documentation translation.

## Decision

Use English as the canonical source and JSON catalogs with ICU MessageFormat for the Web
UI, with structural validation in CI:

- The canonical catalog is `web/src/locales/en.json`; other locales are additions, never
  replacements.
- Keys are stable, descriptive identifiers. English entries carry translator context
  where needed.
- Placeholders, plural/select categories, and an `other` branch are validated.
- Missing keys fall back to English with a warning; placeholder mismatches and invalid ICU
  syntax are errors and fail CI.
- `tools/i18n/check` validates catalogs, detects unused keys, and generates
  `docs/i18n/STATUS.md`.
- Language is auto-detected and can be switched manually; the choice is persisted.
- Layouts must tolerate longer translations and right-to-left languages, even before an
  RTL translation exists.
- Plugin messages use `lang/<lang>.yml` bundles with a documented fallback chain
  (user locale -> server default -> English). Per-player locale is used when the platform
  exposes it; otherwise the server default applies.
- API errors expose a stable machine-readable code that is never localized, plus a
  localizable message negotiated through `Accept-Language`.
- Documentation translations carry a status header with source commit, completeness,
  review status, reviewer, and last-reviewed date. English is always the source of truth.
- Japanese is the first maintained translation. Machine-translated or AI-assisted content
  is labeled and never presented as natively reviewed.
- Do not concatenate translated sentence fragments. Use a complete message with
  placeholders.

## Consequences

### Positive

- Every new feature is localizable by construction.
- Structural errors are caught before merge.
- Translators can work without reading application code.
- API clients are not coupled to English prose.

### Negative and costs

- Tooling and CI maintenance.
- Translators need context; stale translations accumulate and must be visible.
- ICU plural rules and RTL layout require testing beyond string replacement.
- Documentation translation status can lag the English source; the header makes this
  visible rather than hiding it.

### Follow-up

- Add pseudo-locale and RTL tests.
- Add a translation-status check for documentation headers.
- Build the plugin message runtime and its fallback chain.
- Recruit native reviewers before claiming a translation is reviewed.
- Keep the Japanese README synchronized and update its source commit when the English
  README changes.

## Evidence and validation

- `tools/i18n/check` and its tests exist. The checker validates ICU syntax, placeholders,
  plural `other` branches, missing keys, unused keys, and catalog staleness.
- The generated `docs/i18n/STATUS.md` reports the current catalogs. `en` and `ja` each
  have 7 keys with 0 structural errors at the time of writing.
- CI run
  [44039215763](https://github.com/woxlo1/DeployMC/actions/runs/44039215763) passed the
  i18n job on 2026-11-17.
- The gate was proven able to fail: run
  [44038277496](https://github.com/woxlo1/DeployMC/actions/runs/44038277496) removed
  `{count}` from the Japanese plural message; the `i18n` job failed with
  `placeholder mismatch: missing {count}` and the Web tests failed on the rendered plural.
- **Not verified:** no native-speaker review exists, no RTL locale is implemented, and the
  plugin message runtime is not written.

## References

- Master specification, Part 11.
- `tools/i18n/lib.cjs`, `tools/i18n/test/check.test.cjs`, and `docs/i18n/STATUS.md`.
- [TRANSLATING.md](../../TRANSLATING.md).
