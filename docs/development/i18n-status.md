# Localization Status

Last updated: 2026-11-19.

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

## Plugin messages

WP-1.6 added the Bukkit-family [plugin message runtime](../i18n/plugin-messages.md). Its Java
`.properties` catalogs are not included in the table above: the Node checker covers Web catalogs
only. `LocalizedMessagesTest` enforces catalog completeness, named placeholders, diagnostics,
per-key fallback, and JVM-default-locale isolation. `SenderLocaleResolverTest` and
`LegacyColorFormatterTest` cover feature-detected sender locales and legacy color output. All are
server-free tests; no real-server locale behavior is claimed.

Declared policy: English is canonical and Japanese is the first maintained translation. Additional
languages should not be started without native reviewers; this is an open owner decision (D-005 in
[decisions pending](decisions-pending.md)). Translation style guides and the reviewer workflow are
tracked in #125.

These counts cover the Web message catalog only. Plugin messages are implemented for the Bukkit
runtime and documented separately above; CLI output, API error text, and documentation translations
are not yet implemented or translated.
