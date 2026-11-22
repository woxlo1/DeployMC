"""Deterministic config corpus tests.

Run from the repository root:

    python -m unittest discover -s tests/fixtures/config-corpora -p 'test_*.py'
"""

from __future__ import annotations

import contextlib
import hashlib
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

import generate_config_corpora as generator  # noqa: E402

HERE = Path(__file__).parent
MANIFEST_NAME = "config-corpus-manifest.properties"
ALLOWED_OUTCOMES = {"pass", "fail"}


def parse_manifest() -> dict[str, str]:
    lines = (HERE / MANIFEST_NAME).read_text(encoding="ascii").splitlines()
    return {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in lines
        if line and not line.startswith("#")
    }


def generate_into(target: Path) -> None:
    with mock.patch.object(sys, "argv", ["generate_config_corpora.py", str(target)]):
        with contextlib.redirect_stdout(io.StringIO()):
            generator.main()


class GenerateConfigCorporaTest(unittest.TestCase):
    def test_regeneration_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            files = [record.relative_path for record in generator.fixtures()]
            files.append(MANIFEST_NAME)
            for relative_path in files:
                with self.subTest(file=relative_path):
                    self.assertEqual(
                        (HERE / relative_path).read_bytes(), (target / relative_path).read_bytes()
                    )

    def test_manifest_matches_committed_corpora(self) -> None:
        manifest = parse_manifest()
        records = generator.fixtures()
        self.assertEqual(str(generator.SCHEMA_VERSION), manifest["schema.version"])
        self.assertEqual(generator.CORPUS_VERSION, manifest["corpus.version"])
        self.assertEqual(generator.GENERATOR, manifest["generator"])
        self.assertEqual("UTF-8", manifest["encoding"])
        self.assertEqual("LF", manifest["line.separator"])
        self.assertEqual("false", manifest["bom"])
        self.assertEqual(str(len(records)), manifest["fixture.count"])
        self.assertEqual(
            [record.relative_path for record in records],
            manifest["fixture.list"].split(","),
        )
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            for record in records:
                with self.subTest(fixture=record.relative_path):
                    data = (target / record.relative_path).read_bytes()
                    identifier = record.identifier
                    self.assertFalse(data.startswith(b"\xef\xbb\xbf"))
                    self.assertNotIn(b"\r", data)
                    data.decode("ascii")
                    self.assertEqual(
                        hashlib.sha256(data).hexdigest(),
                        manifest["fixture.{0}.sha256".format(identifier)],
                    )
                    self.assertEqual(
                        str(len(data)), manifest["fixture.{0}.bytes".format(identifier)]
                    )
                    self.assertEqual(
                        record.outcome,
                        manifest["fixture.{0}.expected.outcome".format(identifier)],
                    )
                    self.assertIn(record.outcome, ALLOWED_OUTCOMES)
                    self.assertEqual(
                        record.validator_issue,
                        manifest["fixture.{0}.expected.validator.issue".format(identifier)],
                    )
                    self.assertEqual(
                        record.validator_warning,
                        manifest["fixture.{0}.expected.validator.warning".format(identifier)],
                    )
                    self.assertEqual(
                        record.loader_key,
                        manifest["fixture.{0}.expected.loader.key".format(identifier)],
                    )

    def test_expected_signal_pairs_are_explicit(self) -> None:
        records = generator.fixtures()
        for record in records:
            with self.subTest(fixture=record.relative_path):
                if record.outcome == "pass":
                    self.assertEqual("", record.loader_key)
                else:
                    self.assertTrue(record.loader_key.startswith("config.error."))
                    self.assertTrue(record.validator_issue.startswith("config.error."))
                self.assertFalse(record.validator_issue and record.validator_warning)

    def test_fixture_versions_cover_migration_boundaries(self) -> None:
        versions = {record.found_version for record in generator.fixtures()}
        self.assertEqual({"", "0", "1", "2"}, versions)


if __name__ == "__main__":
    unittest.main()
