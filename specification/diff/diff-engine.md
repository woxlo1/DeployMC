# Diff engine contract

> Status: 2026-11-21. This document defines the normative WP-2.4 diff contract.
> It does not claim that an implementation exists.

DeployMC shows changes between two file revisions in the editor, snapshot view, and deployment
review screen. The diff engine is a pure Java 8 computation over two byte arrays. It never resolves
a path, opens a file, executes a process, uses the network, or reads host configuration. Callers
that load bytes must authorize both revisions under the same path scope before calling it.

## Inputs and modes

The caller supplies `before` bytes, `after` bytes, a mode, and optional limits. The default limits in
this document are part of the contract; a caller may only lower them.

| Mode | Behavior |
|---|---|
| `auto` | Detect a ZIP/JAR signature and use `jar`; otherwise try `yaml` and fall back to `text`. JSON is accepted by the YAML parser. |
| `text` | Decode UTF-8 strictly and compute a line diff with word-level detail. |
| `yaml` | Parse both sides as one safe YAML document and compute a key-path semantic diff. On any parse failure, fall back to `text`. |
| `json` | Same as `yaml`, retained as an explicit mode for callers that know the content type. |
| `jar` | Inspect ZIP metadata and recognized plugin descriptors. Never extract entries. |

The engine accepts an empty byte array in every mode. In `auto`, an empty side is treated as text
unless the other side has a ZIP signature. In `jar`, an empty side is an invalid JAR input.

Bytes are decoded as strict UTF-8. A leading UTF-8 BOM is removed before parsing or line matching and
is reported with the `diff.bom` note. Invalid UTF-8 in a text or structured mode produces an
`invalid-input` result; it is never replaced with U+FFFD.

## Byte-level equality

If the two byte arrays are equal, the result is `identical`, the strategy is `none`, and all detail
lists are empty. Byte equality is checked before decoding or parsing. Both sides always carry their
SHA-256 as `sha256:<64 lowercase hex>` and their unsigned byte length, even for an `identical`
result.

## Limits

Limits are checked before any unbounded work. A limit is inclusive: exactly 2,097,152 bytes is
accepted, and 2,097,153 bytes returns `too-large`.

| Limit | Default | Applies to |
|---|---:|---|
| `bytes-per-side` | 2,097,152 | `text`, `yaml`, and `json` raw bytes |
| `lines-per-side` | 100,000 | `text` line arrays after decoding |
| `edit-operations` | 1,000,000 | line and word Myers work units |
| `word-tokens-per-line` | 4,096 | each paired changed line |
| `semantic-nodes` | 100,000 | combined parsed YAML/JSON nodes |
| `jar-entries` | 100,000 | ZIP central-directory entries per side |
| `jar-descriptor-bytes` | 262,144 | uncompressed bytes per recognized descriptor |
| `jar-descriptor-total-bytes` | 1,048,576 | combined uncompressed recognized descriptor bytes per side |
| `jar-entry-list-output` | 1,000 | entry names emitted per added/removed list |

A `too-large` result contains no line, word, semantic, or JAR detail lists. It names the limit and
the observed value. The 2 MiB cap deliberately applies only to textual modes: JAR archives are
bounded by the host upload limit before they reach this engine, and JAR inspection reads metadata
rather than diffing raw bytes.

## Stable result envelope

```json
{
  "status": "changed",
  "strategy": "line",
  "reason": null,
  "limit": null,
  "observed": null,
  "before": { "size": 42, "sha256": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" },
  "after": { "size": 41, "sha256": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" },
  "notes": [],
  "lineHunks": [],
  "wordDiffs": [],
  "semanticChanges": [],
  "jarMetadata": null
}
```

`status` is exactly one of `identical`, `changed`, `too-large`, or `invalid-input`. `strategy` is one
of `none`, `line`, `semantic`, `text-fallback`, or `jar-metadata`. For `too-large` and
`invalid-input`, the strategy is `none`. `reason` is `diff.too-large` or `diff.invalid-input` for
those statuses and `null` otherwise. A note is an informational stable code, not an API error; it
must never contain a parser stack trace, token, host path, or raw file content beyond the structured
diff fields defined below.

Output ordering is part of the contract: hunks by `oldStart` then `newStart`, lines in file order,
semantic changes by path then kind, descriptor names lexicographically, and all emitted maps sorted
by key. No result depends on hash-map iteration order, locale, default charset, clock, or platform
line separator.

## Line diff

Line terminators are `LF`, `CRLF`, or `CR`. A final line with no terminator has ending `EOF`. A file
ending with a terminator does not gain an extra empty line. Matching compares line text and ending
as one unit, so CRLF versus LF produces explicit changed lines rather than being silently ignored or
broadcast across an entire file. When only endings differ, the result also carries the `diff.line-ending`
note.

The algorithm is a bounded Myers shortest-edit script over whole lines. When two scripts have equal
cost, deletions sort before insertions. It does not infer moves: a relocated block appears as one
removal group and one addition group. This is intentional because move inference is ambiguous for
repeated lines and would make the output unstable.

Adjacent changes are grouped into hunks with up to three unchanged context lines on each side. A
hunk has 1-based `oldStart`, `oldLines`, `newStart`, `newLines`, and ordered `lines`. Each line has
`kind` (`context`, `removed`, or `added`), nullable 1-based `oldLine` and `newLine`, `text`, and
`ending`. Empty files produce zero hunks. Identical files produce zero hunks. If the edit budget is
exhausted, the whole result becomes `too-large` with `limit: "edit-operations"`; no partial hunk is
returned.

## Word-level highlighting

Word detail is computed only for a removed line and an added line that are adjacent in the same
hunk and share a paired index. Pure additions and pure removals remain line-level only. A token is a
maximal run of Unicode letters, digits, or underscore, a maximal whitespace run, or one other code
point. Tokenization is case-sensitive and deterministic across hosts.

The same bounded Myers algorithm compares token arrays. Each `wordDiff` carries 1-based `oldLine`,
`newLine`, `oldSegments`, and `newSegments`. A segment has `kind` (`unchanged`, `removed`, or `added`)
and `text`; adjacent segments with the same kind are merged. If a line exceeds the token budget, the
result becomes `too-large` with `limit: "word-tokens-per-line"` rather than returning a partial
segmentation.

## YAML and JSON semantic diff

Both sides are parsed with the hardened SnakeYAML safe path also used by the configuration loader:
duplicate keys, aliases, custom tags, recursive keys, and nesting beyond the existing parser limits
are rejected. JSON is a YAML subset. Exactly one document is allowed. If either side cannot be
parsed, or a node budget is exceeded, the engine returns a `line` strategy result with strategy
`text-fallback` and the `diff.parse-fallback` note. A node-budget failure instead returns
`too-large` with `limit: "semantic-nodes"`.

Paths use JSON Pointer syntax with `~` escaped as `~0` and `/` escaped as `~1`. Array items use
decimal indexes. Each change has one of these kinds:

| Kind | Fields | Meaning |
|---|---|---|
| `added` | `path`, `after`, `afterType` | The path exists only in `after`. |
| `removed` | `path`, `before`, `beforeType` | The path exists only in `before`. |
| `changed` | `path`, `before`, `beforeType`, `after`, `afterType` | The value or type changed at the same path. |
| `renamed` | `from`, `to`, `value`, `valueType` | A removed key and an added key in the same object have equal canonical values. |
| `reordered` | `path`, `beforeOrder`, `afterOrder` | Object keys are identical but their order differs and no value changed. |

Values are canonical strings: `null` is `"null"`, booleans are `true` or `false`, integers are
decimal, floating-point values use `Double.toString`, and strings are emitted unchanged. Type is
one of `null`, `boolean`, `integer`, `number`, `string`, `object`, or `array`. Object key order is
not a value change, but a pure order change is reported once as `reordered`. Array moves are never
inferred; array differences are index-based `added`, `removed`, or `changed` entries.

Rename pairing is deterministic: within one object, sort removed keys and added keys separately,
then pair the first equal-valued candidate from each sorted list. Each key is paired at most once. A
paired rename suppresses the corresponding `added` and `removed` entries. If values are equal but
the keys move between different parent objects, they remain separate `removed` and `added` entries.

## JAR metadata diff

A recognized JAR starts with `PK\x03\x04` or the empty-ZIP signature `PK\x05\x06`. Anything else is
`invalid-input`. The engine reads the ZIP central directory directly from the supplied bytes. It
does not extract entries, follow entry names, allocate an entry's declared uncompressed size, or
execute anything. ZIP64, encrypted entries, truncated central directories, and duplicate descriptor
names are `invalid-input` with the `diff.zip64-unsupported`, `diff.zip-encrypted`,
`diff.zip-truncated`, or `diff.descriptor-duplicate` note respectively.

Recognized descriptor paths are `plugin.yml`, `paper-plugin.yml`, `bungee.yml`, and
`velocity-plugin.json`. Descriptor bytes are inflated only up to the descriptor byte budgets. YAML
descriptors use the hardened YAML parser; `velocity-plugin.json` uses the same parser in JSON mode.
A missing descriptor is valid and yields `diff.descriptor-missing`. An unparseable descriptor is
valid and yields `diff.descriptor-invalid`; its parsed metadata is empty, with no raw parser text in
the result.

Descriptor fields are normalized to `name`, `version`, `main`, `apiVersion`, `description`,
`dependencies`, and `softDependencies`. Bukkit/Paper sources use `name`, `version`, `main`,
`api-version`, `description`, `depend`/`depends`, `softdepend`/`softdepends`, and `loadbefore`.
Velocity sources use `id`, `version`, `main`, and the `dependencies` array with each item's `id` and
`optional` flag. Field changes use the semantic `changed`, `added`, and `removed` shapes. Dependency
changes additionally record `name`, `kind` (`added`, `removed`, or `scope-changed`), and `scope`
(`required` or `optional`).

`jarMetadata` always records `hashChanged`, `sizeDelta`, `entriesAdded`, `entriesRemoved`, and
`descriptors`. Entry-name lists are sorted and truncated at `jar-entry-list-output`; truncation adds
the `diff.entry-list-truncated` note. The overall result is `changed` when any raw byte, entry name,
descriptor field, or dependency differs. A version bump without any other change is therefore a
`changed` result with a semantic change at `/version`.

## Security and disclosure

- The engine is pure and has no bypass flag, filesystem access, or process execution.
- It never accepts or emits absolute paths. ZIP entry names remain ZIP-relative strings and are never
  used to construct a file path.
- Diffs can contain configuration values, including secrets. Transport handlers must apply the same
  authorization as the source path and must not log result bodies.
- Error and note text is a stable machine code. Localized messages are added at the UI or API
  boundary; changing a translation never changes a code.
- Work is bounded by the limits above. Exceeding a limit returns `too-large` before emitting any
  partial content.

## Stable codes

Informational notes may repeat and are sorted lexicographically:

`diff.bom`, `diff.descriptor-invalid`, `diff.descriptor-missing`, `diff.descriptor-duplicate`,
`diff.entry-list-truncated`, `diff.line-ending`, `diff.parse-fallback`, `diff.zip64-unsupported`,
`diff.zip-encrypted`, `diff.zip-truncated`.

`reason` uses the API error codes `diff.too-large` and `diff.invalid-input` defined in
[api/errors.md](../api/errors.md). No other error code may be produced by this engine.

## Error mapping

| Condition | Status | Reason |
|---|---|---|
| Either textual side exceeds `bytes-per-side` | `too-large` | `diff.too-large` |
| Any other configured limit is exceeded | `too-large` | `diff.too-large` |
| Text input is not valid UTF-8 | `invalid-input` | `diff.invalid-input` |
| JAR input is not a supported ZIP | `invalid-input` | `diff.invalid-input` |
| YAML/JSON parse fails in a structured mode | `changed`, strategy `text-fallback` | `null` |

A later REST handler maps `diff.too-large` to HTTP 413 and `diff.invalid-input` to HTTP 400 through
the shared Problem Details layer. Until that handler exists, these codes appear only in the typed
result.

## Evidence required before claiming implementation

The implementation is not complete until tests demonstrate identical and empty files, both sides of
the 2 MiB boundary, CRLF versus LF with preserved endings, moved blocks with deterministic removal
and addition groups, YAML key rename and key reorder, YAML/JSON parse fallback, word segmentation on
a paired changed line, a JAR version bump and dependency change, invalid and ZIP64 archives, and
stable output ordering across repeated runs. The Java 8 bytecode check, full Gradle build, spec lint,
i18n check, and all-toolchain verification must also pass before the status page marks the engine as
implemented.
