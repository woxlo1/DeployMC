# Log corpora

Test-only log payloads for future console capture, backpressure, and UI rendering tests.
No product console log consumer exists yet; the Bukkit `ConsoleBridge.subscribe` implementation
currently throws `CAPABILITY_UNAVAILABLE`, and no log REST or WebSocket consumer reads these files.

## Safety and determinism

`generate_log_corpora.py` writes UTF-8 corpora to an output directory at test time. The default
from the repository root is `tests/fixtures/log-corpora/generated/`, which is ignored by Git.
Tests use a fresh `TemporaryDirectory`. Only the generator, manifest, tests, and this README are
committed. There is no UTF-8 BOM, and the line separator is LF; the ANSI corpus contains a
deliberate embedded CR control byte that must not be normalized away by readers.

`log-corpora-manifest.properties` records schema and corpus versions, encoding, SHA-256 digests,
byte and code-point counts, and expectations. Regeneration is byte-identical. The manifest marks
every corpus with `current.consumer=not-implemented`; this is a statement about product coverage,
not a claim that the text is harmless.

## Corpora

| Corpus | Signal | Future requirement |
|---|---|---|
| `stack-traces.log` | 12-line trace with tabs, `Caused by`, `Suppressed`, and `... N more` | Split and bound multi-line records |
| `ansi-sequences.log` | CSI colours/attributes, cursor movement, erase line, OSC 8 link, CR, and BEL | Strip or escape control sequences safely |
| `long-lines.log` | Boundary lines of 4,095/4,096/4,097 code points and one 65,536-code-point line | Truncate long lines before buffering or rendering |
| `unicode.log` | Combining marks, bidi controls, zero-width joiner, variation selectors, CJK, emoji, NBSP, and DEL | Handle Unicode safely without mangling or injection |

Hostnames use reserved `.test` domains. Control characters and Unicode sequences are deliberate
test signals, not product UI strings. No server, console endpoint, Web UI, or rendering code was
exercised by these fixtures.

## Commands

From the repository root:

```powershell
python tests/fixtures/log-corpora/generate_log_corpora.py
python -m unittest discover -s tests/fixtures/log-corpora -p 'test_*.py' -v
```

The tests verify byte-identical regeneration, manifest digests and counts, stack-trace structure,
exact ANSI control sequences including the embedded CR, declared long-line lengths, and the
expected Unicode scalars. They never invoke a product parser or console consumer.
