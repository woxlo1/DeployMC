# Web API Client and State

> **Status: WP-4.2 implementation note (2026-11-28).** This document records the Web
> client boundaries that are implemented in `web/src/`. Evidence is server-free and
> jsdom-only: no running server, browser, E2E run, full integration suite, or in-game
> command has exercised these paths.

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
write. The component is currently a reusable surface: no file editor or deployment
screen applies it yet.

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
in-memory test double. No product server event publisher, running server, browser,
E2E run, full integration suite, or in-game command has consumed this client.
