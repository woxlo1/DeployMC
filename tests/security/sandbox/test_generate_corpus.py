"""Determinism checks for the versioned sandbox corpus generator.

Run from the repository root with:
    python -m unittest discover -s tests/security/sandbox -p 'test_*.py'
"""

from __future__ import annotations

import hashlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import generate_corpus

GENERATED = ("traversal-corpus.txt", "allowed-paths.txt", "corpus-manifest.properties")


def manifest_values(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


class GenerateCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = Path(generate_corpus.__file__).parent

    def test_regeneration_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            with mock.patch.object(sys, "argv", ["generate_corpus.py", str(target)]):
                generate_corpus.main()
            for name in GENERATED:
                with self.subTest(name=name):
                    self.assertEqual(
                        (self.source / name).read_bytes(),
                        (target / name).read_bytes(),
                        "regenerated file differs from committed file",
                    )

    def test_manifest_digests_and_counts_match_files(self) -> None:
        values = manifest_values((self.source / "corpus-manifest.properties").read_text("ascii"))
        self.assertEqual(str(generate_corpus.SCHEMA_VERSION), values["schema.version"])
        self.assertEqual(generate_corpus.CORPUS_VERSION, values["corpus.version"])
        self.assertEqual(",".join(generate_corpus.SUPPORTED_OS), values["supported.os"])
        self.assertEqual("0", values["override.count"])

        for source in ("traversal", "allowed"):
            name = values[f"source.{source}.file"]
            data = (self.source / name).read_bytes()
            with self.subTest(source=source):
                self.assertEqual(
                    values[f"source.{source}.sha256"], hashlib.sha256(data).hexdigest()
                )
                lines = data.decode("ascii").splitlines()
                payloads = [line for line in lines if not line.startswith("#")]
                self.assertEqual(len(payloads), int(values[f"source.{source}.lines"]))
                categories = {
                    line.split()[2]
                    for line in lines
                    if line.startswith("# @category ")
                }
                for category in categories:
                    self.assertIn(category, values["category.list"].split(","))

    def test_lines_are_ascii_escaped_and_whitespace_clean(self) -> None:
        for name in GENERATED:
            lines = (self.source / name).read_text("ascii").splitlines()
            with self.subTest(name=name):
                self.assertTrue(lines)
                self.assertFalse([line for line in lines if line != line.rstrip()])


if __name__ == "__main__":
    unittest.main()
