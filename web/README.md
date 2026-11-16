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

## Localization

User-facing strings live in `src/locales/en.json` (canonical) and `src/locales/ja.json`.
`src/i18n` provides catalog loading, English fallback for missing keys, ICU plural messages, and
placeholder interpolation. Japanese content is an AI-assisted draft until the status metadata in the
catalog records a native review.
