# ADR-0015: Diff engine algorithm and dependency policy

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** maintainers
- **Related:** [WP-2.4 issue #53](https://github.com/woxlo1/DeployMC/issues/53),
  [diff engine contract](../../specification/diff/diff-engine.md), [ADR-0007](ADR-0007-snapshot-storage-format.md),
  [ADR-0009](ADR-0009-public-api-versioning.md)

## Context

WP-2.4 needs stable line diffs, word-level highlights for the UI, key-path semantic diffs for
YAML and JSON, JAR descriptor and dependency diffs, and an explicit `too-large` result for bounded
computation. The engine will be called from snapshot and deployment review flows, so its output must
be deterministic across hosts and safe to display after authorization. It must compile to Java 8
bytecode and run inside the shaded plugin JAR alongside Netty and SnakeYAML.

The repository already hardens SnakeYAML for host-controlled configuration parsing. It does not
have a diff library, and adding one changes relocation, plugin-JAR verification, dependency review,
and the final artifact size. The master specification requires an ADR when choosing a diff
algorithm or implementing one in house.

## Problem

Which algorithm and dependency strategy should produce DeployMC diffs without sacrificing Java 8
compatibility, deterministic output, bounded resource use, or the plugin packaging guarantees?

## Considered alternatives

### Option A — Add a general-purpose Java diff library

- Use a mature library for Myers or patience diffs.
- Advantages: less algorithm code; established edge-case behavior; possible built-in patch formats.
- Disadvantages: a new dependency must be relocated and verified in the final JAR; library output
  ordering and tokenization may not be a stable DeployMC contract; extra dependency-review and CVE
  surface; Java 8 and shading behavior must be revalidated; structured YAML/JAR logic is still needed.

### Option B — Invoke Git or another external process

- Write both revisions to temporary files and call a system diff tool.
- Advantages: mature command-line behavior; no Java algorithm code.
- Disadvantages: requires an external executable and host filesystem writes; output varies by
  version and locale; process execution expands the trust boundary; conflicts with the pure core
  module rule and makes paths part of the operation.

### Option C — Prefix and suffix comparison only

- Keep the common prefix and suffix and report the middle as one replacement block.
- Advantages: trivial, fast, deterministic, and no dependency.
- Disadvantages: poor readability for edits inside a file; moved blocks and multiple small changes
  become one large replacement; word-level highlighting would be misleading.

### Option D — In-house bounded Myers plus typed structured adapters

- Implement a deterministic, bounded Myers shortest-edit script for lines and tokens, and key-path
  adapters for YAML/JSON and JAR metadata using the existing safe parsers.
- Advantages: stable output owned by DeployMC; no new dependency; pure byte-array input; explicit
  work budgets; one implementation path for Java 8 through newer runtimes.
- Disadvantages: more code and tests; move inference is deliberately omitted; careful budget and
  tokenization design is required to avoid pathological work.

## Decision

Use Option D. The core engine implements a bounded Myers algorithm over whole lines and over word
tokens for paired changed lines. It consumes byte arrays only and returns the typed result defined in
[the diff contract](../../specification/diff/diff-engine.md). YAML and JSON use the existing hardened
parser and are compared by escaped JSON-Pointer paths; a parse failure falls back to the line diff
with an explicit note. JAR diffing reads only the ZIP central directory and recognized plugin
descriptors; it never extracts entries.

The default textual input cap is 2 MiB per side, inclusive. Other defaults include 100,000 lines per
side, 1,000,000 Myers work units, 4,096 tokens per paired line, 100,000 semantic nodes, and 100,000
ZIP entries. Exceeding a limit returns `too-large` with the limit name; no partial diff is emitted.
Output ordering, line terminators, tokenization, rename pairing, and end-of-file handling follow the
normative contract rather than library or platform defaults.

Move detection is out of scope. A moved block is represented deterministically as a removal group
and an addition group. No new diff dependency is added in this work package.

## Consequences

### Positive

- The public result shape and ordering are stable and owned by DeployMC.
- The engine remains pure, Java 8 compatible, and free of process or filesystem side effects.
- Budgets make denial-of-service behavior explicit and testable.
- Existing SnakeYAML hardening and Shadow relocation assumptions are unchanged.

### Negative and costs

- The Myers implementation and its tie-breaking require focused tests, including pathological
  repeated-line inputs.
- No cross-block move detection means some UI diffs are less concise than Git-style diffs.
- Structured adapters must be maintained separately for YAML/JSON and JAR descriptor variants.
- Inflating descriptors safely requires explicit ZIP bounds and ZIP64 rejection.

### Follow-up

- Keep the focused evidence tests when a transport handler is added; the Java 8 engine itself is
  implemented for WP-2.4.
- Add localized English and Japanese messages only at the future UI/API boundary; keep stable codes
  in the engine result.
- Revisit the no-dependency decision if a measured, Java 8 compatible library materially reduces
  code without changing the stable contract; that requires a replacement ADR.

## Evidence and validation

- **Decided from design:** the owner approved WP-2.4 and the repository requires an ADR for the diff
  algorithm choice. The contract in
  [`specification/diff/diff-engine.md`](../../specification/diff/diff-engine.md) is normative.
- **Implemented:** `core.diff` uses the accepted in-house bounded Myers implementation, the existing
  hardened SnakeYAML parser, and direct ZIP central-directory reads. No new dependency was added.
  On 2026-11-21, `.\gradlew.bat :core:test --tests '*Diff*' --console=plain --no-configuration-cache`
  passed all 31 `DiffEngineTest` cases; `.\gradlew.bat spotlessApply --console=plain
  --no-configuration-cache` passed; and `node tools/build/verify-all.cjs`, with
  `DEPLOYMC_JAVA_ROOT` set to the ASCII junction, reported `PASS` for `web`, `java`, `cli`, `python`,
  `spec`, `i18n`, and `compat`. JUnit XML totals were 261 tests, 0 failures, 0 errors, and 1 skipped
  symlink test. Full validation commands and results are also recorded in
  [implementation status](../development/implementation-status.md); no benchmark is claimed.
- The accepted Java 8 bytecode baseline is recorded in [ADR-0002](ADR-0002-java-8-bytecode-baseline.md);
  shading and relocation constraints are recorded in [ADR-0004](ADR-0004-shading-and-relocation.md).

## References

- Master specification, WP-2.4, section 25.3, and the resource-limit table.
- [Snapshot store contract](../../specification/snapshot/snapshot-store.md), which supplies the
  revisions that the diff engine will compare.
- [ADR-0009](ADR-0009-public-api-versioning.md) for spec-first API changes.
