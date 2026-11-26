# DeployMC Observability and Self-Diagnostics Contract

Status: normative baseline for issue #117. This document describes the contracts that product code
must satisfy. It does not claim that unimplemented platform capabilities exist.

The source requirements are PART 36 of the master development specification and the self-protection
rules in PART 33. Where a PART 36 check needs a subsystem that does not exist yet, the doctor report
must say so explicitly; it must never report a successful check for an unimplemented capability.

## 1. Invariants

- No doctor output, diagnostic log line, or metric label may contain a token, Authorization header,
  cookie, file content, raw sandbox path, identity id, label, group, or remote address.
- The only unauthenticated HTTP endpoints remain `/healthz` and `/readyz`.
- A doctor `PASS` means that a product implementation verified the stated condition. An absent or
  partial implementation is `WARN` with `implemented: false`.
- Doctor checks never mutate the server, deploy content, or configuration. A bind probe may open and
  immediately close a loopback socket; it must not serve requests.
- Metrics use fixed names and a closed set of low-cardinality label values. No metric label may use a
  user id, path, token label, hostname, or IP address.
- Load shedding never blocks a Minecraft server or transport event-loop thread. A full queue results
  in a rejected request, a disconnected WebSocket, or a dropped message with a bounded gap marker.

## 2. Doctor report

The stable JSON schema is published as `DoctorReport` in `specification/api/openapi.yaml`. The fields
`schemaVersion`, `overall`, `scope`, and `checks` are mandatory. `schemaVersion` is `1.0`. `scope` is
`limited` until every PART 36 check has a product implementation. `generatedAt` is an ISO-8601 UTC
instant supplied by an injectable clock.

Check identifiers are stable and use lowercase dotted names. Each check reports:

| Field | Contract |
|---|---|
| `id` | Stable dotted identifier from the table below. |
| `status` | `PASS`, `WARN`, or `FAIL`. |
| `implemented` | `true` only when product code verified the condition. `false` checks are always `WARN`. |
| `detail` | Localized, non-sensitive explanation. At most 512 characters. |
| `remediation` | Localized, non-sensitive hint. At most 512 characters. |
| `evidence` | Optional object with allow-listed scalar keys and values. Never paths or secrets. |

Three renderings are required:

- **Human**: one line per check in the form `[STATUS] id: detail` followed by a remediation line when
  present. The final line reminds the reader that the output is not a support-tier verdict.
- **JSON**: the `DoctorReport` schema without additional fields. It is the representation returned by
  `GET /api/v1/server/doctor`.
- **Redacted copy-paste**: the same checks with host-specific evidence removed and path-like tokens
  replaced by `<redacted>`. The JSON rendering sets `redacted: true`. This mode is intended for bug
  reports and must still omit secrets and file contents.

### 2.1 Initial check set

| `id` | Implemented boundary | Status rule |
|---|---|---|
| `environment` | Platform info, plugin version, API version. | `PASS` when all values are present. |
| `capabilities` | `CapabilityCatalog` plus the platform capability map. | `PASS` when every key is `AVAILABLE`; `WARN` for `UNAVAILABLE` or `UNKNOWN`. |
| `bind` | Loopback flag and a bounded local port probe. | `FAIL` for a non-loopback bind or a port owned by another process; `WARN` when the probe is inconclusive. |
| `auth.mode` | Active startup mode. | `PASS` for implemented `token` or `cloudflare` modes; `WARN` for reserved modes that refuse startup. |
| `auth.tunnel` | None yet. | `WARN`, `implemented: false`. |
| `auth.jwt` | Redacted counters from the Cloudflare Access JWT verifier. | `PASS` when diagnostics exist with zero closed rejections; `WARN` for any rejection or missing diagnostics; `implemented: false` when no snapshot is supplied. Counts appear only in localized `detail`, never in evidence. |
| `rbac` | Immutable RBAC policy snapshot and synthetic explain-mode probes. | `PASS` only when a product evaluator is wired and the snapshot is valid; `WARN` with `implemented: false` otherwise. Evidence is limited to `roles`, `users`, `default-role`, and `registry-version`; no identity, path, glob, token, or real decision reason. |
| `sandbox` | Root existence, writability, symlink policy, and protected-path resolution. | `FAIL` for an unusable root or rejected protected-path probe; `WARN` when free-space detection is unavailable. |
| `snapshot` | Open store, lock status, verify report, and size against retention. | `FAIL` when the store is locked or over its byte limit; `WARN` for verify issues; `WARN`, `implemented: false` when no store is available. |
| `deployment` | In-process active deployment count from the snapshot store. | `WARN` because persisted transactions, safe state, and restart viability are not implemented. |
| `platform.jar-replacement` | None yet. | `WARN`, `implemented: false`. |
| `console.capture` | Declared platform capability. | `WARN` because ring-buffer usage and dropped-line accounting are not implemented. |
| `configuration` | Last validated configuration result. | `FAIL` for errors; `WARN` for warnings; `PASS` when clean. |
| `localization` | Active catalog key coverage. | `PASS` with zero missing keys; `WARN` otherwise. |
| `updates` | None yet. | `WARN`, `implemented: false`; update checks are disabled by default. |

The report always contains all identifiers in a deterministic order. A renderer never computes a
different status from the stored checks.

## 3. Logging

- The dedicated diagnostic logger uses the `io.github.woxlo1.deploymc` namespace and never writes to
  the Minecraft server log with an unredacted message.
- `observability.logging.format` is `text` by default. `json` emits one JSON object per request log
  line with the fields `requestId`, `method`, `path`, `status`, `durationMillis`, and `outcome`.
  `path` is the route class or a normalized path from a closed allow-list; it is never a raw client
  path containing file names or identifiers.
- The audit log is a separate stream with separate retention and integrity rules. Diagnostic logging
  does not replace or write audit records.
- No logger implementation may log request or response bodies, headers, cookies, tokens, or file
  contents.

## 4. Metrics

`GET /api/v1/metrics` is opt-in through `observability.metrics.enabled`. It is always behind the
normal bearer/cloudflare gate. The configured `observability.metrics.permission` is fixed to
`server.read`, a stable identifier from the closed RBAC registry. The evaluator enforces that
grant on every request; no identity is implicitly admin-equivalent.

The response is Prometheus text format `0.0.4` with content type
`text/plain; version=0.0.4; charset=utf-8`. Rendered names and labels are fixed at build time. The
following counters, gauges, and histograms are emitted:

| Name | Kind | Labels | Meaning |
|---|---|---|---|
| `deploymc_http_requests_total` | counter | `endpoint` | Completed HTTP requests by closed endpoint class. |
| `deploymc_http_request_duration_seconds` | histogram | `endpoint` | Request duration with fixed buckets. |
| `deploymc_auth_failures_total` | counter | `reason` | `required`, `invalid`, or `expired`. |
| `deploymc_websocket_connections_active` | gauge | none | Open WebSocket count. |
| `deploymc_websocket_queue_full_total` | counter | none | Non-blocking queue rejections. |
| `deploymc_requests_shed_total` | counter | `scope` | `identity` or `auth-failure` rate-limit rejections. |
| `deploymc_executor_queue_depth` | gauge | none | Depth of the bounded transport task queue. |
| `deploymc_executor_stalls_total` | counter | none | Watchdog observations of stalled executors. |
| `deploymc_snapshot_store_bytes` | gauge | none | Current snapshot store size when a store is supplied. |

Metrics for deployment outcomes, JWKS refresh, console buffer usage, and dropped console lines are
`NOT IMPLEMENTED` in this baseline. The registry must not publish fabricated values for them.

The closed endpoint classes are `health`, `server`, `doctor`, `files`, `validation`, `snapshots`,
`metrics`, `websocket`, `static`, and `other`. A request whose path is not recognized uses `other`.

## 5. Self-protection

- A token bucket refills continuously and is keyed by `(identity id, endpoint class)` and by
  `(remote address, auth failure)`. Remote addresses and identity ids are used only as internal map
  keys and are never exposed as metric or log labels. The key set is bounded with LRU eviction.
- Exceeding the identity bucket returns `429` with `Retry-After`, `RateLimit-Limit`,
  `RateLimit-Remaining`, and `RateLimit-Reset`. Exceeding the auth-failure bucket returns the same
  shape with the existing `auth.rate-limited` problem code.
- Total connections and WebSocket upgrades are capped by host configuration. Excess upgrades are
  rejected with `503 server.busy` rather than queued.
- WebSocket producers never block. When a bounded queue is full, the producer receives `false` and a
  queue-full counter increases. A sustained slow consumer is disconnected; an optional gap notice
  uses only sequence numbers and a count.
- A watchdog owns one named thread, observes executor responsiveness, records a stall counter and a
  redacted diagnostic, and never kills the server process. It stops before transport shutdown
  completes.
- Uploads, logs, diffs, snapshots, WebSocket frames, and retry queues stay bounded by the limits
  already specified in PART 33 and the configuration schema.

## 6. Honesty boundaries

The following remain `NOT IMPLEMENTED` and are not made true by this contract: tunnel-only arrival
heuristics, Windows JAR replacement strategy detection, console ring-buffer accounting, opt-in
update availability checks, a deployment engine or persisted transactions, audit records, and RBAC
evaluation. The WP-3.4 RBAC contract is normative but has no product evaluator until its
implementation PR is merged. Cloudflare JWT verification is implemented; real Cloudflare
compatibility and plan-specific guidance remain unclaimed until D-002 is resolved. The doctor
report and documentation must preserve those boundaries.
