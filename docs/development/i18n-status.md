# Localization Status

Last updated: 2026-11-18.

The generated source of truth is [docs/i18n/STATUS.md](../i18n/STATUS.md). Do not edit that file by
hand; regenerate it with `node tools/i18n/check --write-status`.

| Locale | Keys | Missing | Structural errors | Review status |
|---|---:|---:|---:|---|
| en | 7 | 0 | 0 | source |
| ja | 7 | 0 | 0 | machine-translated-draft, no native review |

Structural checks cover placeholder parity, ICU syntax, and plural `other` branches. Missing or
stale translations warn and fall back to English at runtime; structural errors fail CI.

Verified commands on 2026-11-18:

```text
node tools/i18n/check
i18n check passed: 0 error(s), 0 warning(s)

node --test tools/i18n/test/check.test.cjs
7 tests passed
```

Declared policy: English is canonical and Japanese is the first maintained translation. Additional
languages should not be started without native reviewers; this is an open owner decision (D-005 in
[decisions pending](decisions-pending.md)). Translation style guides and the reviewer workflow are
tracked in #125.

These counts cover the Web message catalog only. Plugin messages, CLI output, API error text, and
documentation translations are not yet implemented or translated.
