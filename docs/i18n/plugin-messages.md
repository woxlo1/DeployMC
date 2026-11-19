# Plugin Message Runtime

Last updated: 2026-11-19. This page describes the Bukkit-family plugin message runtime implemented
for WP-1.6 (issue #46).

## Catalogs

Plugin messages are Java `.properties` resource bundles under
`io/github/woxlo1/deploymc/platform/bukkit/`:

- `messages.properties` is the canonical English base catalog.
- `messages_ja.properties` is the first translation. It is a machine-translated draft and has not
  been reviewed by a native speaker.

Message keys are stable dot-separated identifiers declared in `MessageKeys`. Keys, paths, and
identifiers are never localized. User-facing text is never assembled from literal strings in adapter
code; it is rendered from the catalogs.

## Fallback chain

A lookup resolves one key at a time in this order:

1. The sender locale, when the platform exposes one.
2. The server default configured by `language` in `config.yml`.
3. The English base catalog.

A partially translated locale may override a single key and inherit every other key from the next
rung. The JVM default locale is never consulted. A key absent from the entire chain does not render
as a raw identifier or a blank string; the localized `message.missingKey` diagnostic is returned.

Named placeholders use the `{name}` form. If a message needs a value that was not supplied, the
localized `message.missingPlaceholder` diagnostic identifies both the placeholder and the message
key. Placeholder values are inserted literally, so `$` and `\` in paths cannot be interpreted as
regex replacement syntax.

## Legacy color formatting

Messages may contain legacy `&` color codes. At the Bukkit send boundary, valid codes such as `&a`
or `&l` are translated to the section-sign form used by old clients and servers. Existing `§`
sequences are preserved and unknown `&` sequences stay literal. This is a plain Java formatter with
no dependency on `ChatColor` or modern text components, so it does not raise the minimum server
version.

Color codes are presentation syntax, not translatable text. The English and Japanese catalogs keep
the same keys and placeholder sets.

## Per-sender locale

`CommandOutput.locale()` exposes an optional sender locale. The Bukkit adapter feature-detects
public `locale()` and `getLocale()` accessors by reflection, accepting either a `java.util.Locale` or
a language tag string. Missing accessors, unsupported return types, and accessor failures are
treated as unavailable; the configured server default is then used.

Feature detection avoids compiling against newer Bukkit APIs while the plugin targets the old
1.8.8 API surface. The `players.locale` capability remains `UNKNOWN` until a real server run proves
it available. No support tier is claimed from these server-free tests.

## Verification scope

Server-free Java tests cover catalog completeness, named placeholders, missing-key and
missing-placeholder diagnostics, partial translation fallback, JVM-default-locale isolation, sender
locale override, reflective locale accessors, and legacy color translation. The Node i18n checker
still covers the Web UI catalogs only; Java catalog structure is enforced by the Java tests.

No real Minecraft server load, in-game command execution, or text-component compatibility check was
performed for this runtime.
