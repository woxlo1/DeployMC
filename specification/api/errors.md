# Error Code Catalog

> **Status: updated 2026-11-21.** This catalog defines the structure and the stable
> codes referenced by the API specification. Authentication and file-operation codes have
> implementations; snapshot codes are produced by the WP-2.3 store, diff codes are produced by the
> Java 8 `core.diff` engine, and validation codes are produced by the Java 8 `core.validation` engine.
> D-008 is resolved: every structural budget failure is the top-level `validation.limit-exceeded`
> Problem with a format-specific issue code in `errors[]`. Codes are added with the feature
> that produces them; an unused code stays out of the table.

## Why a catalog

Clients must branch on a stable machine-readable code, never on an HTTP status alone and
never on English prose. Two different failures can share a status (`403` for a missing
permission and for a policy denial; `409` for a busy deployment and a duplicate key). A
localized `detail` string changes with translation and wording, so it cannot be an API.

The catalog is normative. A code that is not listed here is not part of the public API.

## Format

Errors use RFC 9457 Problem Details with DeployMC extensions, served as
`application/problem+json`:

```json
{
  "type": "urn:deploymc:error:files.revision-conflict",
  "title": "Revision conflict",
  "status": 412,
  "detail": "The file changed since the supplied revision.",
  "code": "files.revision-conflict",
  "requestId": "01J00000000000000000000000",
  "retryable": false,
  "errors": [
    {
      "pointer": "/path",
      "code": "files.revision-mismatch",
      "detail": "Expected sha256:a1b2..., found sha256:c3d4..."
    }
  ]
}
```

Rules:

- `code` is lowercase, dot-separated, and matches
  `^[a-z][a-z0-9]*(\.[a-z][a-z0-9-]*)+$`.
- `type` is `urn:deploymc:error:<code>`. `about:blank` is allowed only when no specific
  code applies; in practice every DeployMC error has a code.
- `title` is a short, localized summary. `detail` is a localized explanation.
- `status` matches the HTTP status.
- `requestId` is always present and appears in the server log.
- `retryable` states whether the same request may succeed later without changes. A
  `Retry-After` header, when present, is authoritative for timing.
- `errors` contains field- or path-level issues, each with a stable `code` and a
  `pointer`. For JSON bodies the pointer is a JSON Pointer; for files it is a
  sandbox-relative path. It never contains an absolute host path. A content-validation
  finding also carries `severity`, `line`, `column`, `offset`, and optional `parameters`;
  those fields are absent for other error families.
- Details never include tokens, credentials, JWTs, file contents, or host paths outside
  the sandbox.
- A `401` response does not disclose whether an identity exists. A `403` does not disclose
  whether a path exists unless the caller could already list its parent.

## Namespaces

| Prefix | Owner | Meaning |
|---|---|---|
| `auth.` | Authentication | Missing, malformed, expired, or unverifiable credentials |
| `request.` | Transport | Malformed request, unsupported media type, bad cursor, limit, or precondition syntax |
| `rbac.` | Authorization | Authenticated but denied, including path-scope denials |
| `server.` | Lifecycle | Not ready, shutting down, overloaded, capability unavailable |
| `files.` | Sandbox and file API | Path rejected, not found, too large, revision conflict |
| `snapshots.` | Snapshot store | Not found, corrupt, restore conflict |
| `validation.` | Content validation | Invalid content, exhausted limit, or validator failure |
| `diff.` | Diff engine | Input exceeds a bound or cannot be decoded |
| `deployments.` | Deployment engine | Invalid state, busy, validation/verification/rollback failure |
| `console.` | Console | Capture unavailable, buffer gap, command rejected |
| `webhooks.` | Event delivery | Invalid target, delivery rejected by SSRF policy |
| `internal.` | Server | Unexpected failure that is not safely attributable |

## Initial catalog

Codes marked **reserved** are planned for a feature that does not exist yet. They are
listed so the specification can reference them consistently; they must not be returned by
an implementation that lacks the feature.

### Authentication

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `auth.required` | 401 | no | No credential was supplied. |
| `auth.invalid` | 401 | no | The credential is malformed, has an invalid signature, or fails `iss`/`aud` validation. |
| `auth.expired` | 401 | no | The credential expired. Obtain a new one. |
| `auth.not-yet-valid` | 401 | no | `nbf`/`iat` is in the future beyond the allowed clock skew. |
| `auth.jwks-unavailable` | 503 | yes | No valid cached signing key exists and the JWKS endpoint is unreachable. Fail closed. |
| `auth.rate-limited` | 429 | yes | Too many failed authentication attempts from this identity or source. |
| `auth.one-time-consumed` | 401 | no | The local one-time token was already used. |
| `auth.one-time-expired` | 401 | no | The local one-time token expired. |

### Request and transport

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `request.invalid` | 400 | no | The request body or parameters fail validation. |
| `request.unsupported-media-type` | 415 | no | The `Content-Type` is not supported for this operation. |
| `request.too-large` | 413 | no | The body exceeds the configured limit. |
| `request.invalid-cursor` | 400 | no | The pagination cursor is malformed or no longer valid. |
| `request.precondition-required` | 428 | no | A destructive write omitted `If-Match`. |
| `request.timeout` | 408 | yes | The operation exceeded its deadline. |
| `request.method-not-allowed` | 405 | no | The method is not defined for this resource. |

### Authorization

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `rbac.denied` | 403 | no | The identity lacks the required permission. |
| `rbac.path-denied` | 403 | no | The identity lacks access to this path scope, or a deny rule matched. |
| `rbac.self-approval-denied` | 403 | no | The requester tried to approve their own deployment. |
| `rbac.token-scope-exceeded` | 403 | no | The token scope is narrower than the requested action, or wider than its owner's permissions. |

### Server lifecycle

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `server.not-ready` | 503 | yes | Startup has not completed. |
| `server.shutting-down` | 503 | yes | The instance is disabling and rejects new work. |
| `server.busy` | 503 | yes | A bounded queue or connection limit is full. |
| `server.capability-unavailable` | 409 | no | The platform does not provide a capability this operation requires. The detail names the capability and the fallback. |
| `server.internal` | 500 | yes | An unexpected, non-attributable failure. Details are not disclosed. |

### Files

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `files.path-rejected` | 400 | no | The path failed sandbox validation (traversal, reserved name, symlink policy, invalid Unicode, and similar). |
| `files.not-found` | 404 | no | The file or directory does not exist. |
| `files.not-directory` | 400 | no | A directory was required but the path names a file, or vice versa. |
| `files.already-exists` | 409 | no | The target already exists and the operation does not overwrite. |
| `files.revision-conflict` | 412 | no | The supplied revision does not match the current one. |
| `files.revision-mismatch` | 412 | no | An `errors[]` entry code describing the expected and actual revisions. |
| `files.too-large` | 413 | no | The file, upload, listing, or deletion exceeds the configured limit. |
| `files.protected` | 403 | no | The path is protected (`.deploymc/`, DeployMC config, secret store, or a deny pattern). |
| `files.binary-not-editable` | 415 | no | A binary file was requested as editable text. Download is available instead. |
| `files.directory-not-empty` | 409 | no | A non-recursive delete targeted a non-empty directory. |
| `files.invalid-range` | 416 | no | A byte-range request was negative, malformed, or outside the file. |
| `files.delete-confirmation-required` | 409 | no | Recursive deletion did not confirm the current entry count. |
| `files.delete-limit-exceeded` | 413 | no | A tree exceeds the host-controlled deletion entry cap. |
| `files.snapshot-required` | 409 | no | A destructive operation was attempted without a configured snapshot hook. |
| `files.snapshot-failed` | 503 | yes | Snapshot capture failed; the mutation was aborted before changing files. |
| `files.atomic-move-unsupported` | 500 | no | The file system cannot perform the required atomic move; no partial write was committed. |
| `files.invalid-operation` | 400 | no | The operation is not valid for this target, such as writing the sandbox root. |
| `files.io` | 500 | yes | A host file-system failure prevented the operation. Details are sanitized. |

### Snapshots

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `snapshots.not-found` | 404 | no | The snapshot id or metadata record does not exist or was pruned. |
| `snapshots.corrupt` | 409 | no | Stored content, metadata, or index failed integrity verification. |
| `snapshots.restore-conflict` | 412 | no | The target changed since the snapshot was taken and a precondition was not satisfied. |
| `snapshots.store-unavailable` | 503 | yes | The store is closed, locked, busy with an active deployment, or has an unsupported format version. |

### Diff (implemented in `core.diff`; REST mapping is not implemented)

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `diff.too-large` | 413 | no | A textual side exceeds 2 MiB, or another diff computation limit is exceeded. |
| `diff.invalid-input` | 400 | no | Text is not valid UTF-8, or JAR metadata is not a supported ZIP. |

### Validation (implemented in `core.validation`; REST mapping arrives in WP-2.6)

Top-level codes returned by the validation endpoint or a save-time write:

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `validation.failed` | 422 | no | At least one ERROR finding was produced. |
| `validation.limit-exceeded` | 413 | no | An input or work limit was exceeded, including a format structural budget. |
| `validation.validator-unavailable` | 503 | yes | A registered validator failed unexpectedly; the write is aborted. |

Issue codes appear only in `errors[]` and in `ValidationIssue.code`, never as the top-level `code`.
A structural-budget issue code accompanies the top-level `validation.limit-exceeded` Problem:

| Code | Severity | Meaning |
|---|---|---|
| `validation.encoding` | ERROR | Input is not strict UTF-8 or contains a forbidden NUL. |
| `validation.yaml-syntax` | ERROR | YAML scanner or parser error. |
| `validation.yaml-duplicate-key` | ERROR | A mapping key occurs twice in one mapping. |
| `validation.yaml-tab` | ERROR | A tab is used where YAML forbids it, such as indentation. |
| `validation.yaml-alias-limit` | ERROR | An alias reference or expansion budget is exceeded; reported under `validation.limit-exceeded`. |
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

The full normative behavior, pointers, positions, and limits are defined in
[content validation](../validation/content-validation.md).

### Deployments

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `deployments.not-found` | 404 | no | The deployment id does not exist. |
| `deployments.busy` | 409 | yes | Another deployment owns the data directory. |
| `deployments.invalid-state` | 409 | no | The requested transition or cancellation is not allowed from the current state. |
| `deployments.validation-failed` | 422 | no | Static validation rejected the artifact set. |
| `deployments.idempotency-conflict` | 409 | no | The idempotency key was reused with a different payload. |
| `deployments.approval-required` | 409 | no | The transaction is waiting for approval. |
| `deployments.verification-failed` | 409 | no | Post-restart checks failed; rollback follows. |
| `deployments.rollback-failed` | 500 | no | Rollback could not complete; the instance entered `SAFE_STATE`. |
| `deployments.restart-unsupported` | 409 | no | The configured restart mode cannot perform the requested restart. |
| `deployments.artifact-invalid` | 422 | no | The uploaded artifact is not a valid plugin archive. |

### Console

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `console.unavailable` | 409 | no | Log capture is not available on this platform. |
| `console.gap` | 200 (event) | n/a | A WebSocket event indicating dropped messages, not an HTTP error. |
| `console.command-denied` | 403 | no | Command dispatch is not permitted for this identity or matches a deny policy. |

### Webhooks

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `webhooks.invalid-target` | 422 | no | The target URL is malformed or uses an unsupported scheme. |
| `webhooks.ssrf-blocked` | 422 | no | The target resolves to a blocked address or redirects to one. |

### Internal

| Code | HTTP | Retryable | Meaning |
|---|---|---|---|
| `internal.error` | 500 | yes | Unexpected failure. The request id correlates to the server log. |
| `internal.not-implemented` | 501 | no | The operation is defined but not implemented in this build. |

## Stability rules

1. A code is added with the feature that returns it, and documented in the same change.
2. A code is never reused for a different meaning.
3. Removing a code, changing its HTTP status, or changing its meaning is a breaking API
   change and requires a new major API version.
4. Adding a code, an optional field, or a new `errors[]` entry code is additive and allowed
   within `v1`.
5. Once a major version is released, a removed code carries `Deprecation` and `Sunset`
   headers for the documented overlap period.
6. The catalog, the OpenAPI schema, and the implementation are checked against each other
   by contract tests. A mismatch is a bug.

## Localization

`title` and `detail` are translated through `Accept-Language` with English fallback. The
`code` and every `errors[].code` are never translated. A translator must not change the
meaning of a code; the documentation for each code is the source for its message.

## Adding a code

1. Choose the namespace and a name that describes the cause, not the UI reaction.
2. Add the row to this document.
3. Add or reuse it in the OpenAPI examples and, when it has a structured body, in the
   schemas.
4. Implement it in the application layer with a typed error, and map it in one place in
   the transport.
5. Add a contract test that asserts the status, `code`, and required fields.
6. Add the message to the message catalogs if it is user-facing.

## Related documents

- [OpenAPI specification](openapi.yaml)
- [API layering](../../docs/architecture/api-layering.md)
- [ADR-0009: Public API versioning](../../docs/adr/ADR-0009-public-api-versioning.md)
- Master specification, Part 8.
