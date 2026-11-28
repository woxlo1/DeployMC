# DeployMC Web UI

React + TypeScript + Vite skeleton for the DeployMC web interface.

## Commands

Run from `web/`:

```powershell
pnpm install
pnpm lint
pnpm format:check
pnpm test
pnpm build
```

`pnpm build` runs the TypeScript compiler in check-only mode before the Vite production build.

## Shell routes

The UI uses hash routes such as `#/dashboard` and `#/files`, so static hosting needs no rewrite
rules. All screens from the Web UI screen inventory are present in the sidebar. The doctor screen
hosts the existing authenticated, redacted diagnostics panel and the files screen hosts the
authenticated file explorer. Other screens are shell placeholders until their work packages connect
real API clients.

Every screen accepts a view-state preview query for shell verification:

- `#/files?state=loading`
- `#/files?state=empty` (the default when the query is absent or invalid)
- `#/files?state=error`

For `files`, the preview is rendered by the shared shell while the explorer host remains mounted.
Without a verified token and `files.read`, no directory listing is requested; with them, the
explorer may fetch from the authenticated file API even while a preview state is present. This query
is a rendering contract, not a simulated API response.

## File explorer

The files screen provides a bounded, sandbox-relative tree. Directories load lazily one level at a
time; rows are virtualized for scrolling. Filename search is limited to the first 200 matching names
within the first four directory levels. Selection supports click, Ctrl/Cmd-click, and Shift-click
ranges. The context menu offers create, rename, and delete; delete and move require confirmation.
Rename and move send the current ETag as `If-Match`, and a stale write opens the shared conflict
notice instead of replaying automatically.

Files can be uploaded with the toolbar picker or by dropping files onto a directory. Progress shows
a real local `FileReader` percentage followed by an indeterminate sending phase; native `fetch` does
not expose an upload percentage, so no network percentage is invented. Uploads can be canceled.
Permission gates and protected/readonly badges are derived from the verified identity and the
server's `GET /api/v1/me?explain=` decision. The UI never constructs an absolute path, `..`, or a
backslash path from user input.

## Theme and keyboard shortcuts

The theme selector supports `light`, `dark`, and `system`. `system` follows
`prefers-color-scheme`; a manual choice is persisted in local storage. Design tokens are CSS custom
properties in `src/index.css`.

`Alt` plus a digit opens a sidebar section (`Alt+1` dashboard through `Alt+9` doctor, `Alt+0`
about, `Alt+I` identity). `?` opens the shortcut dialog; `Escape` closes it. Shortcuts are ignored
while typing in form fields. The dialog traps focus while open and restores it on close.

## Security notes

The shell does not store bearer tokens, render raw exception text, or expose host paths. The error
boundary and error view display only a generated `req_…` correlation id and a closed machine code.
Global toasts accept catalog keys, not free-form strings, so user-visible text stays localized.

## Localization

User-facing strings live in `src/locales/en.json` (canonical) and `src/locales/ja.json`.
`src/i18n` provides catalog loading, English fallback for missing keys, ICU plural messages, and
placeholder interpolation. Japanese content is an AI-assisted draft until the status metadata in the
catalog records a native review.
