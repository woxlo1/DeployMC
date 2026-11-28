# Web API Client and State

> **Status: WP-4.3 implementation note (2026-11-28).** This document records the Web
> client boundaries that are implemented in `web/src/`. Evidence is server-free and
> jsdom/fetch-mock only: no running server, browser, E2E run, full integration suite, or
> in-game command has exercised these paths.

## Sources of truth

The browser does not hard-code API shapes. Two checked-in generated modules are produced
from the normative specifications by `tools/spec`:

| Source | Generated module | Exported contract |
|---|---|---|
| `specification/api/openapi.yaml` | `web/src/api/generated.ts` | API version, source SHA-256, operation descriptors, declared parameters, component schema types, success-schema map, and permission identifiers |
| `specification/api/asyncapi.yaml` | `web/src/api/generated-events.ts` | WebSocket endpoint, protocol version, subscribed channels, control message types, and server event types |

`npm run generate:web-client --prefix tools/spec` regenerates both modules.
`npm run check:web-client` runs in spec lint and fails on drift. Generated files are
excluded from Prettier because their formatting is owned by the generators. Unknown
`$ref` targets, duplicate operation ids, and missing event constants fail generation
instead of being guessed.

## REST client

`ApiClient` accepts only generated operation ids. For each request it:

- substitutes path parameters and serializes query parameters from the operation descriptor;
- ignores undeclared parameters instead of forwarding arbitrary request input;
- sends the bearer token once in `Authorization`, with `credentials: 'omit'` and
  `referrerPolicy: 'no-referrer'`;
- adds `If-Match` or `If-None-Match` only when the generated descriptor declares that
  header;
- parses bounded Problem Details, ETags, and `X-Request-Id`, treating `304` as a
  successful conditional response even though `Response.ok` is false;
- maps `401`, `403`, `409`, `412`, `416`, `428`, `429`, and server errors to stable
  reasons without exposing server prose, tokens, or raw exceptions.

The global `fetch` is resolved per request unless a fetch implementation is explicitly
injected. That preserves test and host replacement without retaining transport state.

## Authentication and identity

A token lives only in React memory for the lifetime of the mounted application. It is
never written to `localStorage`, `sessionStorage`, cookies, the URL, or logs. Input is
trimmed and checked as 8–512 printable characters before any request is made.
`GET /api/v1/me` is parsed strictly; only a verified, bounded identity snapshot is
displayed. The shell and identity screen show the verified identity id, method, and
role/permission counts, not raw claims.

A `401` from any authenticated path calls `reportUnauthorized()`, which clears the
token, identity, toast state, and cache entries for the previous identity scope. The
scope is derived from the verified opaque identity id and never from token material.

## Query cache

`QueryCache` is bounded, time-limited, and in-memory. Keys are composed from the opaque
identity scope, generated operation id, and canonical declared parameters. A bearer
token is never part of a key. Identical in-flight reads are shared; successful entries
evict the oldest entry when the configured limit is exceeded. `invalidate(prefix)`
removes all entries for one identity scope so a different identity cannot observe a
cached representation. This is a cache, not an authorization boundary: every request
still goes through the server gate.

## Optimistic concurrency UX

`ConflictNotice` renders only the stable current ETag and request id supplied by a
mapped `409`/`412` error. It never renders server prose, file paths, or credentials. Its
actions are `reload`, `retry`, and `dismiss`; there is no automatic replay of a stale
write. The file explorer applies it to rename and move conflicts. No deployment screen
applies it yet.

## File explorer

`web/src/api/files.ts` is the only boundary between the explorer and the generated file
operations. It validates every user-supplied name and sandbox-relative path locally,
rejects control characters, traversal segments, absolute/drive/backslash forms, encoded
dots, and reserved names, and parses every response before it reaches React state.
Directory reads are cached under the opaque identity scope; mutations invalidate that
scope's cached listings and explanation responses. The bearer token is passed per call
and is never retained by the boundary.

`FileExplorer` renders the `files` route inside `PlaceholderView`, preserving the shared
`?state=loading|empty|error` preview contract. Directory children are fetched lazily one
level at a time from `listFiles`; `buildTreeRows` produces a deterministic, cycle-safe row
order, and `virtualWindow` limits mounted rows with four rows of overscan. Search uses a
bounded `listFiles` read of the first four directory levels, keeps the first 200 matching
basenames, and never walks the host file system. Breadcrumbs are rebuilt only from
validated path segments.

The verified identity's permissions gate create, edit, delete, and upload controls.
Because `FileEntry` carries no restriction flags, protected/readonly badges and reasons
come from the server-authoritative `getCurrentIdentity` explain mode for `files.write`
and a bounded set of closed reason codes. This is presentation only; every mutation is
still authorized by the server. Rename and drag-and-drop move send the current ETag as
`If-Match` and route conflicts through `ConflictNotice`. Delete confirms first and, for a
directory, fetches its bounded entry count before requesting recursive deletion with
that count.

Uploads accept the toolbar picker or a drop onto a directory. `uploadEntry` reports a
real local `FileReader` percentage while reading, then an indeterminate sending phase;
native `fetch` does not expose upload progress, so no network percentage is synthesized.
Each upload has an `AbortController`, a bounded progress list, and cancellation on
unmount. Names are validated before an upload path is constructed.

## WebSocket client

Native browser WebSockets cannot set an `Authorization` header. This client therefore
never places a token in the URL or a subprotocol. It uses the generated endpoint and
relies on ambient authentication at the upgrade; a `SocketFactory` hook lets a host
supply an authenticated socket without putting credentials in this module.

The client validates every envelope against the generated event types, protocol version,
bounded id/timestamp, and payload limits. Event ids are de-duplicated across reconnects,
while sequences are evaluated per connection because the specification defines sequence
as per-connection. A detected gap is reported to the UI without rendering payload data.
On every successful open it resubscribes the desired channels. Non-fatal closes and
factory failures use bounded exponential backoff with a bounded attempt count; fatal
policy codes (`1008`, `4401`, `4403`) and attempt exhaustion stop reconnecting. The UI
shows connection state, last event type, sequence, gap, and invalid-envelope warnings.

## Evidence boundary

Server-free Vitest and jsdom tests cover generated-client drift, REST request shaping,
identity parsing, cache behavior, auth clearing, conflict rendering, envelope parsing,
reconnect/backoff, resubscription, gap detection, and UI status. The fake socket is an
in-memory test double. WP-4.3 adds fetch-mocked tests for path/name validation, strict
file response parsing, lazy tree row ordering and cycle handling, bounded virtual
windows, selection modifiers, identity gating, explanation badges, create/rename/delete,
confirmed drag-and-drop moves, and upload state/progress. No product server event
publisher, running server, browser, E2E run, full integration suite, or in-game command
has consumed this client or the file explorer.
