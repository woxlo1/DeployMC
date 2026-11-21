# Content validation contract

> Status: 2026-11-21. This document defines the normative WP-2.5 validator contract.
> Java code is not implemented by this document; implementation evidence is required before
> the contract can be marked implemented.

DeployMC accepts configuration and plugin data from authenticated users. Parsing that data is a
security boundary: a malformed or hostile document must produce deterministic findings, never
execute code, never resolve a host path, and never read the file system. This contract defines a
pure validator, an extensible registry, save-time enforcement, an authoritative validation result,
and a narrowly scoped comment-preserving YAML edit helper.

## 1. Scope and non-goals

- The validator operates on a sandbox-relative path string and an in-memory byte array. It does not
  open files, resolve paths, execute processes, load plugins, or read host configuration.
- Validation is server-authoritative. A client-side result is never trusted, and no request parameter
  can disable, weaken, or raise a validation limit.
- A validator result reports syntax and bounded structural findings. It does not prove that a plugin
  is safe, that a configuration has the intended semantics, or that a JAR is trustworthy.
- Comment-preserving editing is limited to scalar YAML values. It is not a general YAML round-trip
  emitter.
- The public Java API for third-party validator registration remains out of scope for WP-2.5. The
  `core.validation` registry is extensible inside the plugin; exposing it through `api/` is a later
  API decision.

## 2. Inputs and decoding

### 2.1 Path input

- `relativePath` uses `/` separators, is not absolute, contains no `.` or `..` segments, and has no
  empty segment. The transport resolves it through `SandboxResolver` before validation. The registry
  only uses it for rule matching and the result; it never constructs a file path.
- A malformed path is a programming error and throws `IllegalArgumentException`. It is not returned
  as a validation finding.
- Matching is case-sensitive for path patterns and case-insensitive for ASCII file extensions.

### 2.2 Byte input and text decoding

- Text validators decode strict UTF-8. A leading UTF-8 BOM is accepted and skipped for parsing, but
  the BOM is still counted in `maxBytes`.
- Invalid UTF-8 produces one `validation.encoding` error with the zero-based byte offset of the first
  invalid sequence. No partial document is parsed.
- Raw NUL outside a format-specific escape is an error for all text formats.
- Line and column values are 1-based positions in the decoded text, excluding a leading BOM. A value
  of `0` means the position is not available; the validator must not invent a position.

## 3. Limits

Host configuration owns all limits. Request data can only supply values at or below the configured
maximum. Every limit is inclusive: a document at exactly the limit is accepted; the first unit
beyond it fails.

| Limit | Default | Applies to |
|---|---:|---|
| `maxBytes` | 10 MiB | Raw input byte array |
| `maxCodePoints` | 1,000,000 | Decoded Unicode code points, excluding a leading BOM |
| `maxLines` | 50,000 | Decoded lines |
| `maxDepth` | 100 | YAML/JSON/TOML nesting depth and property key depth |
| `maxNodes` | 1,000,000 | Total composed document nodes |
| `maxAliasExpansionsPerNode` | 100 | Reused YAML anchor nodes |
| `maxKeyLength` | 1,024 | Characters in one mapping key or property key |
| `maxIssues` | 200 | Findings returned in one result |

Exceeding any limit other than `maxIssues` throws `ValidationLimitException`. The API maps every
limit failure to the top-level `validation.limit-exceeded` Problem with HTTP 413. When the exhausted limit is a
format-specific structural budget, the response also carries one `errors[]` entry whose `code` is
the matching issue code from section 6 (`validation.yaml-node-limit`, `validation.yaml-depth`,
`validation.yaml-alias-limit`, `validation.json-depth`, `validation.toml-node-limit`, or
`validation.toml-depth`). A limit is never reported as `validation.failed`. The `maxIssues` limit
truncates findings after sorting by severity, code, path, line, column, and offset; it must never
produce different findings for the same input across runs.

## 4. Result model

`ValidationResult` is immutable and contains:

| Field | Type | Meaning |
|---|---|---|
| `path` | string | The normalized sandbox-relative input path |
| `matched` | boolean | Whether a validator rule matched |
| `format` | string or null | Matched validator id, such as `yaml`; null when unmatched |
| `valid` | boolean | True when no ERROR finding exists; warnings do not make it false |
| `issues` | list | Zero or more `ValidationIssue` values, deterministically ordered |

`ValidationIssue` contains:

| Field | Type | Meaning |
|---|---|---|
| `severity` | `ERROR` or `WARNING` | Whether the finding blocks a write |
| `code` | string | Stable machine code from section 6 |
| `pointer` | string | Empty for the whole document; otherwise an RFC 6901 pointer |
| `line` | integer | 1-based decoded line, or 0 |
| `column` | integer | 1-based decoded column, or 0 |
| `offset` | long | 0-based raw byte offset, or -1 |
| `parameters` | list | Safe values used only for localized message formatting |

Parameters contain key names, counts, or position-adjacent metadata. They never contain raw file
content, credentials, tokens, or absolute host paths. `ValidationIssue` exposes a localized `detail`
through the validation message catalog; the stable code is never translated.

For `.properties` files, the pointer is `/<escaped-key>` where `~` becomes `~0` and `/` becomes `~1`.
For structured formats, the pointer follows mapping keys and array/table indices. Duplicate-key
findings point at the repeated key, not the whole document.

An unmatched file yields `matched=false`, `format=null`, and no issues. It MUST be reported as
unmatched; a caller must never present it as "validated". This is explicit non-applicability, not a
bypass flag.

The validation endpoint renders the same fields as JSON:

```json
{
  "path": "config.yml",
  "matched": true,
  "format": "yaml",
  "valid": false,
  "issues": [
    {
      "severity": "ERROR",
      "code": "validation.yaml-duplicate-key",
      "pointer": "/server/port",
      "line": 12,
      "column": 5,
      "offset": 247,
      "detail": "Duplicate key."
    }
  ]
}
```

## 5. Validator and registry

```java
public interface ContentValidator {
  String id();
  ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits)
      throws ValidationException;
}

public interface ValidationGate {
  ValidationResult validate(String relativePath, byte[] content) throws ValidationException;

  // Host byte budget for pre-validation reads; null when the custom gate has no known budget.
  default ValidationLimits limits() { return null; }
}
```

`ValidatorRegistry` implements `ValidationGate`, is immutable after construction, and offers
`withBuiltIns()` and `empty()` factories plus registration of an id, extensions, path patterns, and
a `ContentValidator`. Registration validates its own inputs and throws `IllegalArgumentException`
for an invalid pattern or empty rule.

Path patterns use `/`, `?` (one character except `/`), `*` (zero or more characters except `/`), and
`**` (zero or more characters including `/`). Matching precedence is deterministic:

1. exact normalized path;
2. path pattern, ordered by descending count of literal characters, then registration order;
3. extension, ordered by descending length, then registration order.

A match by an exact path overrides a pattern or extension rule. A pattern override lets a host treat
a specific file differently from its extension peers. Built-in registrations use extensions only:
`.yml`/`.yaml` → `yaml`, `.json` → `json`, `.toml` → `toml`, `.properties` → `properties`.

A validator that throws an unexpected exception produces `validation.validator-unavailable`; the
write is aborted. A validator may never catch its own limit exception and convert it to a warning.

## 6. Stable codes

Top-level Problem codes are returned by the validation endpoint or a save-time write failure:

| Code | HTTP | Retryable | Meaning |
|---|---:|---|---|
| `validation.failed` | 422 | no | At least one ERROR finding was produced. |
| `validation.limit-exceeded` | 413 | no | An input or work limit was exceeded. |
| `validation.validator-unavailable` | 503 | yes | A registered validator failed unexpectedly; fail closed. |

Issue codes appear only inside `errors[]` and in `ValidationIssue.code`. They are not top-level
Problem codes:

| Code | Severity | Meaning |
|---|---|---|
| `validation.encoding` | ERROR | Input is not strict UTF-8 or contains a forbidden NUL. |
| `validation.yaml-syntax` | ERROR | YAML scanner or parser error. |
| `validation.yaml-duplicate-key` | ERROR | A mapping key occurs twice in one mapping. |
| `validation.yaml-tab` | ERROR | A tab is used where YAML forbids it, such as indentation. |
| `validation.yaml-alias-limit` | ERROR | An alias reference or expansion budget is exceeded; recursive or excessive aliases are rejected. |
| `validation.yaml-tag-rejected` | ERROR | A non-standard YAML tag or custom type was requested. |
| `validation.yaml-multiple-documents` | ERROR | More than one YAML document was present. |
| `validation.yaml-node-limit` | ERROR | The composed node budget was exceeded; reported under `validation.limit-exceeded`. |
| `validation.yaml-depth` | ERROR | YAML nesting exceeded `maxDepth`; reported under `validation.limit-exceeded`. |
| `validation.json-syntax` | ERROR | JSON does not conform to the accepted grammar. |
| `validation.json-duplicate-key` | ERROR | A JSON object key occurs twice. |
| `validation.json-depth` | ERROR | JSON nesting exceeded `maxDepth`; reported under `validation.limit-exceeded`. |
| `validation.toml-syntax` | ERROR | TOML syntax is malformed. |
| `validation.toml-duplicate-key` | ERROR | A key or table is defined twice. |
| `validation.toml-unsupported` | ERROR | A construct outside the supported TOML profile was used. |
| `validation.toml-node-limit` | ERROR | The TOML node budget was exceeded; reported under `validation.limit-exceeded`. |
| `validation.toml-depth` | ERROR | TOML nesting exceeded `maxDepth`; reported under `validation.limit-exceeded`. |
| `validation.properties-syntax` | ERROR | A properties line has no valid key or cannot be continued safely. |
| `validation.properties-invalid-escape` | ERROR | A backslash escape or Unicode escape is malformed. |
| `validation.properties-duplicate-key` | WARNING | A key is defined more than once; the last value wins. |
| `validation.comment-edit-unsupported` | ERROR | The YAML edit target or replacement cannot be preserved safely. |

The catalog in [api/errors.md](../api/errors.md) is authoritative. Adding or changing a code is a
breaking API change unless the addition is purely additive.

## 7. Format profiles

### 7.1 YAML

- Use the existing hardened SnakeYAML with a validator-specific loader: duplicate keys disabled,
  recursive keys disabled, alias expansion capped by `maxAliasExpansionsPerNode`, total composed
  nodes capped by `maxNodes`, nesting capped by `maxDepth`, code points capped by `maxCodePoints`,
  and a tag inspector that allows only standard tags.
- Exactly one document is allowed. An empty document is valid.
- Alias references are counted through the composed node graph. A cycle or a reference count beyond
  the configured budget is the structural finding `validation.yaml-alias-limit`, reported under
  top-level `validation.limit-exceeded`; it is never expanded recursively by this validator.
- Exceeding `maxNodes` is `validation.yaml-node-limit`; exceeding `maxDepth` is
  `validation.yaml-depth`. Both are structural findings reported under `validation.limit-exceeded`.
- Scanner errors caused by forbidden tabs are `validation.yaml-tab`. Other scanner or composer
  errors are `validation.yaml-syntax`.
- Positions come from SnakeYAML marks when available; otherwise `line=0`, `column=0`, and
  `offset=-1`.

### 7.2 JSON

- Accept RFC 8259 JSON with strict UTF-8, no comments, no trailing commas, no single quotes, no
  `NaN`/`Infinity`, no duplicate object keys, and no unpaired surrogate escapes.
- Numbers are validated lexically; the validator does not require a particular numeric range or
  floating-point implementation.
- Depth beyond `maxDepth` is the structural finding `validation.json-depth`, reported under
  top-level `validation.limit-exceeded`. Other grammar failures are `validation.json-syntax`, and
  repeated object keys are `validation.json-duplicate-key`.
- The parser is a bounded recursive-descent parser. It never calls `eval`, reflection, JNDI, or a
  script engine.

### 7.3 TOML

- The validator accepts the bounded TOML profile: comments; bare, quoted, and dotted keys; basic and
  literal single-line strings; integers with underscores and radix prefixes; floats; booleans;
  date/time literals treated as opaque tokens; arrays; inline tables; `[table]` headers; and
  `[[array-of-tables]]` headers.
- Multiline strings, heterogeneous arrays, key/value constructs outside this profile, and syntax the
  parser cannot bound are `validation.toml-unsupported`. This is a supported profile, not a claim of
  full TOML 1.0 conformance.
- A repeated key, table, or array-of-tables entry in the same scope is `validation.toml-duplicate-key`.
- Exceeding `maxNodes` is `validation.toml-node-limit`; exceeding `maxDepth` is
  `validation.toml-depth`. Both are structural findings reported under `validation.limit-exceeded`.
- Other malformed input is `validation.toml-syntax`.

### 7.4 `.properties`

- Accept Java properties syntax: `#` and `!` comments, `=`/`:`/whitespace separators, backslash line
  continuation, escaped separators, escaped whitespace, and `\\uXXXX` escapes including surrogate
  pairs.
- An invalid escape or incomplete Unicode escape is `validation.properties-invalid-escape`. A line
  with no key, an unterminated continuation, or an invalid logical line is
  `validation.properties-syntax`.
- Duplicate keys are a WARNING with code `validation.properties-duplicate-key`; the final value wins,
  matching Java properties semantics.

## 8. Save-time enforcement

- `FileOperationsService` gains an optional `ValidationGate`. Wiring a gate is a host decision; no
  request parameter can add, remove, or weaken it. The transport wiring for WP-2.6 MUST configure a
  gate built from the registry.
- Validation happens after path resolution and precondition checks, and before the target file is
  snapshotted, replaced, moved, or deleted. For streamed uploads, bytes are staged in the temporary
  file first; the gate validates that staged content before the old target is snapshotted and before
  the atomic move. The temporary file is cleaned up on every failure.
- An ERROR finding aborts the operation with `FileOperationException.Reason.VALIDATION_FAILED` and
  top-level code `validation.failed`. No target file is changed and no snapshot is captured.
- `ValidationLimitException` maps to `Reason.VALIDATION_LIMIT` / `validation.limit-exceeded`. When
  the exhausted limit is a format structural budget, the HTTP adapter also emits one `errors[]` entry
  with the format-specific issue code. Any other `ValidationException` maps to
  `Reason.VALIDATOR_UNAVAILABLE` / `validation.validator-unavailable`. All three fail closed.
- A WARNING does not block the write. Methods returning `ValidatedFileResult` expose the full result
  so the caller can surface or log warnings; existing `write`/`upload` signatures continue to return
  `FileInfo` and silently accept warnings.
- A move, rename, or delete of existing content validates the affected content before the snapshot
  when a gate is configured. If content cannot be read for validation, the operation fails closed
  with `validation.validator-unavailable`.

## 9. Authoritative validation endpoint

WP-2.6 will add `POST /api/v1/files/validate`. Its contract is:

- Request body: `path` (sandbox-relative string) and `content` (UTF-8 JSON string). The route body
  limit is the smaller of the configured validation `maxBytes` and a host-controlled route maximum;
  an oversized body is `request.too-large`. The generic non-upload 1 MiB default does not apply to
  this route unless the host lowers it.
- The server resolves `path` through the sandbox first. A rejected path maps to `files.path-rejected`
  or `files.protected`; it is never passed to a validator.
- Success returns the result shape in section 4 with HTTP 200, including `matched=false` for
  unmatched types and including warnings.
- One or more ERROR findings return Problem `validation.failed` with status 422 and one `errors[]`
  entry per finding. A limit failure returns `validation.limit-exceeded` with status 413 and, for a
  format structural budget, one `errors[]` entry carrying the format-specific issue code. An
  unexpected validator failure returns `validation.validator-unavailable`.
- `detail` strings are localized through `Accept-Language` with English fallback. Codes, pointers,
  line/column, and severity are not localized.
- The response never includes the submitted content, an absolute host path, or a raw parser message.

## 10. Comment-preserving YAML edit helper

`YamlScalarEditor.update(byte[] source, String pointer, String value, ValidationLimits limits)`
returns new bytes and never mutates its input. It supports programmatic edits needed by later config
templates:

- The source must be one valid YAML document whose relevant path consists only of mapping keys.
  Array indices, aliases, anchors, merge keys, multi-line values, and duplicate keys are unsupported.
- Pointers use RFC 6901 escaping. The target scalar must be a single-line plain, single-quoted, or
  double-quoted scalar.
- Only the scalar token is replaced. Every other byte, including comments, blank lines, indentation,
  key spelling, and CR/LF/CRLF line endings, is preserved exactly.
- Plain scalars accept the new value only when it cannot change YAML structure: no newline, no
  leading/trailing whitespace, no `:` followed by whitespace, no unquoted ` #`, and no flow
  indicators. Otherwise the edit is rejected with `validation.comment-edit-unsupported`.
- Quoted scalars are re-escaped for their quote style. A missing or non-scalar target and any
  unsupported construct also produce `validation.comment-edit-unsupported`.
- The helper never writes a file. It is bounded by the same `ValidationLimits` and does not perform a
  full YAML round trip.

## 11. Localization

- Java message catalogs live with the implementation as
  `validation-messages.properties` and `validation-messages_ja.properties`.
- Keys are derived from stable codes (`code.<stable-code>`), and parameters are formatted with
  `MessageFormat` using a fixed root locale. A missing locale key falls back to English.
- The validator may emit codes and parameters; it must not emit English or Japanese prose from Java
  source. The HTTP adapter owns localized `title` and `detail`.

## 12. Evidence required before this contract can be marked implemented

The implementation PR must record exact commands and results demonstrating:

1. YAML duplicate keys, forbidden tabs, alias/cycle limits, custom tags, multiple documents, and
   error positions, plus an empty document and the exact node/code-point boundaries.
2. JSON syntax errors, duplicate keys, depth limits, invalid UTF-8, and valid nested documents.
3. TOML accepted profile, unsupported constructs, duplicate keys, and malformed syntax.
4. `.properties` escapes, continuation lines, duplicate-key warnings, and invalid escapes.
5. Registry precedence for exact path, path pattern, extension, and unmatched files.
6. Save-time ordering: ERROR prevents snapshot and mutation; limit and validator failures fail closed;
   warnings are exposed by `ValidatedFileResult`.
7. The authoritative result JSON shape, stable issue ordering, and repeated-run determinism.
8. Comment-preserving edits retain untouched bytes and reject unsafe or unsupported edits.
9. Java 8 bytecode verification, the full Gradle build, all-toolchain verification, and spec lint.

## 13. Related documents

- [Master specification](../../docs/development/DeployMC_Complete_Master_Development_Specification.md),
  WP-2.5 and parts 24.10, 32.3, and 33.2
- [ADR-0016](../../docs/adr/ADR-0016-content-validation.md)
- [Error catalog](../api/errors.md)
- [File operations contract](../files/file-operations.md)
- [API layering](../../docs/architecture/api-layering.md)
