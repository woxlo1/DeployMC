# Translation catalog checks

`tools/i18n/check` validates the Web UI message catalogs in `web/src/locales/`.

## Usage

Run from the repository root:

```powershell
node tools/i18n/check
node tools/i18n/check --strict
node tools/i18n/check --write-status
node --test tools/i18n/test/check.test.cjs
```

`--write-status` regenerates `docs/i18n/STATUS.md`.
`--strict` treats warnings (missing or stale translations) as failures.

## What is checked

Structural errors (fail the check):

- invalid JSON or non-string message values;
- unbalanced or malformed ICU syntax;
- plural/selectordinal/select messages without an `other` branch;
- placeholder arguments that differ between English and a translation.

Warnings (do not fail unless `--strict`):

- keys missing from a translation (runtime falls back to English);
- keys present in a translation but absent from English;
- English keys not referenced by any string literal in `web/src`;
- translations whose last Git change is older than the English catalog.

## Current limitations

- English is the only canonical catalog location: `web/src/locales/en.json`.
- Unused-key detection is a string-literal heuristic. Dynamically composed keys can produce false
  warnings; keep keys explicit in source so reviewers can see them.
- Plural completeness is only enforced for the `other` fallback. Full CLDR category requirements per
  language will be added with the runtime message loader work package.
- Staleness uses Git commit timestamps and is skipped for untracked files.
