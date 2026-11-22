# Hostile input fixtures

Test-only archive and YAML payloads for future archive-ingestion and validation tests.
No product validator, running server, or integration consumer uses these fixtures yet.

## Safety and determinism

`generate_hostile_inputs.py` writes every binary or large payload to an output directory at test
time. The default output from the repository root is `tests/security/hostile-inputs/generated/`,
which is ignored by Git. Tests use a fresh `TemporaryDirectory` instead. No generated archive,
expanded payload, or large YAML document is committed.

The ZIP writer emits fixed DOS timestamps, no data descriptors, no extra fields, and ASCII names.
The zip bomb uses a small hand-written fixed-Huffman DEFLATE stream instead of platform zlib, so
its bytes are identical across hosts. The encrypted entry uses traditional ZipCrypto with the
obvious throwaway password `dmctest`; it is a real encrypted entry, not a flag-only imitation.
The password is a test signal and never a credential.

`hostile-inputs-manifest.properties` records schema and corpus versions, file paths, SHA-256
digests, byte sizes, and expectations. Regeneration is byte-identical; the tests verify the
committed manifest against a fresh generated copy.

## Fixtures

| Fixture | Risk | `current.reader` | Future requirement |
|---|---|---|---|
| `archives/zip-slip.zip` | Entry name `../deploymc-zip-slip.txt` | Not rejected by static source review | Reject unsafe entry names |
| `archives/zip-bomb.zip` | 8 MiB expansion from about 53 KiB | Not expanded by static source review | Enforce expansion and ratio limits |
| `archives/duplicate-entries.zip` | Two `duplicate.txt` entries | Not rejected by static source review | Reject duplicate entry names |
| `archives/duplicate-descriptor.zip` | Two `plugin.yml` entries | Rejected: `diff.descriptor-duplicate` | Reject duplicate descriptors |
| `archives/encrypted-entry.zip` | ZipCrypto flag and encrypted payload | Rejected: `diff.zip-encrypted` | Reject encrypted entries |
| `archives/symlink-entry.zip` | Unix mode `0o120777` with a relative target | Not rejected by static source review | Reject symlink entries |
| `yaml/deep-nesting.yml` | 120 nested mappings | Product check not executed | Bound nesting depth |
| `yaml/alias-expansion.yml` | 150 aliases to one small anchor | Product check not executed | Bound alias expansions |
| `yaml/large-scalar.yml` | 256 KiB quoted scalar | Product check not executed | Bound code points |
| `yaml/many-nodes.yml` | 2,000 flat nodes | Product check not executed | Bound node count |

`current.reader` values for ZIP come from inspecting `ZipMetadataReader` and `JarDiffSupport`;
they are not the result of executing those classes against these generated files. The YAML values
are deliberately `product-check-not-executed` because no fixture consumer exists yet. The limits
named in the manifest come from static review of `ValidationLimits` and `ConfigYamlParser`.
"Planned" or "future" expectations are not claims of current product behavior or support.

## Commands

From the repository root:

```powershell
python tests/security/hostile-inputs/generate_hostile_inputs.py
python -m unittest discover -s tests/security/hostile-inputs -p 'test_*.py' -v
```

The tests verify byte-identical regeneration, manifest digests and metadata, ZIP central-directory
structure, the raw DEFLATE stream and declared size of the bomb, successful ZipCrypto decryption,
the symlink mode, and YAML depth, alias, scalar, and node counts. They never extract an archive
to disk and never invoke a product parser.
