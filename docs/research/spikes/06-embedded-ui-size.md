# Spike 6: Embedded UI bundle size

Status: `OBSERVED` for the current baseline; Monaco/CodeMirror comparison `NOT RUN`.

## Question

How large is the current embedded Web UI inside the plugin JAR, and is there enough evidence to choose
between Monaco and CodeMirror?

## Commands and results

On 2026-11-23, from the repository root:

```powershell
node tools/build/verify-all.cjs --only=web
node tools/build/verify-all.cjs --only=java
```

Both reported `PASS`. Vite reported:

```text
index.html                   0.46 kB │ gzip:  0.28 kB
assets/index-BACSjab-.css    1.36 kB │ gzip:  0.62 kB
assets/index-CyCrObLO.js   155.35 kB │ gzip: 50.71 kB │ map: 380.37 kB
Precompressed 2 gzip and 2 Brotli assets
```

Exact bytes in `web/dist/`:

| File | Bytes |
|---|---:|
| `assets/index-BACSjab-.css` | 1,358 |
| `assets/index-BACSjab-.css.br` | 491 |
| `assets/index-BACSjab-.css.gz` | 621 |
| `assets/index-CyCrObLO.js` | 155,810 |
| `assets/index-CyCrObLO.js.br` | 44,342 |
| `assets/index-CyCrObLO.js.gz` | 50,632 |
| `assets/index-CyCrObLO.js.map` | 380,370 |
| `index.html` | 458 |

The Java build copied `web/dist` into the plugin and the final-JAR verifier reported
`DeployMC-0.1.0-SNAPSHOT.jar, 4721584 bytes, 2706 classes (0 versioned), max common bytecode 52`.
That JAR includes the Web assets; it is not a measurement of a finished editor UI.

## Result

The current no-editor baseline is small enough to embed. A source map is emitted and its packaging
policy is still open. This measurement does not answer Monaco versus CodeMirror because neither
editor is installed in the production build. ADR-0011 stays `Proposed` for the editor choice.

## Not verified

No browser loaded these assets, no tunnel transfer was measured, no startup or idle cost was
measured on a server, and no editor benchmark was run. The current numbers are a baseline for a
future bundle budget, not a budget or a support claim.
