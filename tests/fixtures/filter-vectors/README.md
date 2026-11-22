# Cross-implementation filter vectors

A versioned JSON manifest of 24 vectors for the log filter expression language described in
PART 24.8 of the master specification. It is shared fixture data for future Java, TypeScript, and
Rust parsers; no such parser exists in the product yet.

## Status and scope

`consumerStatus` is `not-implemented` for all three target languages. These vectors do **not**
claim parsing or evaluation parity: no product parser, CLI command, Web UI filter, server endpoint,
or in-game command consumes the manifest. The manifest records intent only.

The file is ASCII, LF, UTF-8-compatible JSON without a BOM. The generator is deterministic and the
manifest pins a SHA-256 digest of the canonical vector array. Seven Python tests verify generation,
metadata, unique IDs, coverage of the PART 24.8 examples, AST/evaluation shape, stable rejection
codes, and declared limits.

```powershell
python tests/fixtures/filter-vectors/generate_filter_vectors.py
python -m unittest discover -s tests/fixtures/filter-vectors -p 'test_*.py'
```

## Vector shape

Each accepted vector has an `id`, `category`, `expression`, canonical `ast`, and `expectedMatch`
against either the shared `sampleEvent` or a per-vector `event`. `evaluationContext` supplies a
fixed `now` instant so `since:` and `until:` results are deterministic. The sample event includes
both matching and non-matching content so accepted vectors cover `expectedMatch` true and false.

Each rejected vector has a stable `reason` and explanatory `note` instead of an AST. Rejection
reasons cover syntax, unknown fields, invalid values, unknown operators, regex complexity, regex
length, and expression length. The manifest declares fixture limits for the length vectors; no
product code enforces them yet.

The canonical AST uses `comparison`, `field`, `time`, `boolean`, and `not` nodes. It is fixture
notation for future implementations, not a runtime API. No generated parser or evaluator source is
committed.
