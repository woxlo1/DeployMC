# Translating DeployMC

English is the canonical language. Translations are additions, never replacements. A missing
translation falls back to English at runtime; it must never produce a blank string or a raw key.

## What you can translate

- `docs/i18n/README.<language>.md` and other documents copied from the English source.
- Web UI message catalogs in `web/src/locales/<language>.json`.
- Later phases: plugin message bundles and documentation sites.

Do **not** translate error codes, configuration keys, API field names, command names, permission
identifiers, or file paths.

## Documentation translations

1. Copy the current English document, for example `README.md` to `docs/i18n/README.ja.md`.
2. Add a translation-status header as the first lines of the file:

   ```html
   <!--
   Translation status
   - Source: README.md
   - Source commit: <git commit that last changed the source>
   - Completeness: complete draft | partial
   - Review: AI-assisted draft, not reviewed by a native speaker
   - Reviewer: none
   - Last reviewed: not reviewed
   -->
   ```

3. Keep links, code blocks, commands, version numbers, and product names unchanged.
4. Update the header when a native speaker reviews the translation. Never claim a review that did not
   happen.

## Message catalogs

1. Add or change the key in `web/src/locales/en.json` first.
2. Add the translation to `web/src/locales/<language>.json`. Keep the same named placeholders.
3. Update `@meta` in the translated catalog:
   - `reviewStatus`: `machine-translated-draft`, `reviewed`, and so on;
   - `reviewedBy` and `reviewedAt` only after a real review.
4. Run the checks from the repository root:

   ```powershell
   node tools/i18n/check
   node tools/i18n/check --write-status
   ```

## Machine translation policy

Machine translation may bootstrap a draft. Mark it as machine-translated in the translation header
and in the catalog `@meta` block. Never present an unreviewed machine translation as verified.

## Review and support

Open a translation issue using the repository issue form with the language, scope, and whether you
can review or only translate. Discussion in other languages is welcome in GitHub Discussions.
