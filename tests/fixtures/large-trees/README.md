# Large directory tree fixtures

A deterministic, generated tree for future bounded listing and performance tests. No product
listing API, server, or rendered UI consumes it yet.

## Safety and determinism

`generate_large_trees.py` writes 388 small files and two 64 KiB files into an output directory at
test time. The default from the repository root is `tests/fixtures/large-trees/generated/`, which is
ignored by Git. Tests use a fresh `TemporaryDirectory`. Only the generator, manifest, tests, and
this README are committed; no generated tree is committed.

Names are ASCII, paths are relative, no component exceeds 255 bytes, and no symlinks are created.
The tree includes 3 groups with 8 subdirectories and 16 files each, a 12-level deep chain, one
209-character filename, and the two large binary files. The manifest pins file and directory
counts, maximum path depth, total bytes, a content digest, and `current.listing.consumer=
not-implemented`.

```powershell
python tests/fixtures/large-trees/generate_large_trees.py
python -m unittest discover -s tests/fixtures/large-trees -p 'test_*.py' -v
```

The tests verify byte-identical regeneration, manifest counts and digests, path safety, and the
boundary shapes. They do not call `FileOperationsService.list`, claim a performance result, or
start a server.
