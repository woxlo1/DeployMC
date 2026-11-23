# PART 40.3 early risk-reduction spikes

Access date for new external sources in this directory: 2026-11-23. These records distinguish
observed local evidence from source verification and from work that still needs a real server, a
supervisor, or an owner decision. They are research records, not support claims.

| Status | Meaning |
|---|---|
| `OBSERVED` | A command was run locally and its output is recorded. |
| `SOURCE-VERIFIED` | An official source was read on the access date and cited. |
| `LOCAL-ONLY` | Measurements were local; they do not represent a production environment. |
| `NOT RUN` | The required environment or owner decision was unavailable. No result is claimed. |

| Spike | Status | Finding |
|---|---|---|
| [Transport](01-transport.md) | `OBSERVED` | Existing Netty decision remains accepted; server-free transport tests pass. |
| [Windows JAR replacement](02-windows-jar-replacement.md) | `OBSERVED`, `SOURCE-VERIFIED` | Windows file replacement depends on the open handle's share mode; a real server was not used. |
| [Update-folder semantics](03-update-folder-semantics.md) | `SOURCE-VERIFIED`, `NOT RUN` | Paper documents a server-managed `update` folder; proxy behavior is not verified. |
| [Log capture](04-log-capture.md) | `LOCAL-ONLY`, `NOT RUN` | Corpus tests pass; no oldest/newest server version was exercised. |
| [Folia scheduling](05-folia-scheduling.md) | `SOURCE-VERIFIED`, `NOT RUN` | Official guidance requires region-aware scheduling; the current SPI has no region context. |
| [Embedded UI size](06-embedded-ui-size.md) | `OBSERVED` | Baseline Web assets are measured; the Monaco/CodeMirror benchmark was not run. |
| [Restart behavior](07-restart-behavior.md) | `NOT RUN` | Owner hosting setup D-001 is still open; no supervisor test is claimed. |

No spike in this directory promotes a platform to a support tier. The tier rules remain in
[the support matrix](../../compatibility/support-matrix.md) and
[ADR-0014](../../adr/ADR-0014-platform-support-tiers.md).
