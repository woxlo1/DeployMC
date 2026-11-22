"""Deterministic log corpus tests.

Run from the repository root:

    python -m unittest discover -s tests/fixtures/log-corpora -p 'test_*.py'
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

import generate_log_corpora as generator  # noqa: E402

HERE = Path(__file__).parent
MANIFEST_NAME = "log-corpora-manifest.properties"


def parse_manifest() -> dict[str, str]:
    lines = (HERE / MANIFEST_NAME).read_text(encoding="ascii").splitlines()
    return {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in lines
        if line and not line.startswith("#")
    }


def generate_into(target: Path) -> None:
    with mock.patch.object(sys, "argv", ["generate_log_corpora.py", str(target)]):
        with contextlib.redirect_stdout(io.StringIO()):
            generator.main()


class GenerateLogCorporaTest(unittest.TestCase):
    def test_regeneration_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory() as first_dir, tempfile.TemporaryDirectory() as second_dir:
            first, second = Path(first_dir), Path(second_dir)
            generate_into(first)
            generate_into(second)
            files = [record.relative_path for record in generator.fixtures()]
            files.append(MANIFEST_NAME)
            for relative_path in files:
                with self.subTest(file=relative_path):
                    self.assertEqual(
                        (first / relative_path).read_bytes(),
                        (second / relative_path).read_bytes(),
                    )
            self.assertEqual(
                (HERE / MANIFEST_NAME).read_bytes(), (first / MANIFEST_NAME).read_bytes()
            )

    def test_manifest_matches_generated_corpora(self) -> None:
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
                    text = data.decode("utf-8")
                    self.assertEqual(
                        hashlib.sha256(data).hexdigest(),
                        manifest["fixture.{0}.sha256".format(identifier)],
                    )
                    self.assertEqual(
                        str(len(data)), manifest["fixture.{0}.bytes".format(identifier)]
                    )
                    self.assertEqual(
                        str(len(text)),
                        manifest["fixture.{0}.codepoints".format(identifier)],
                    )
                    self.assertEqual(
                        record.current_consumer,
                        manifest["fixture.{0}.current.consumer".format(identifier)],
                    )
                    self.assertEqual(
                        record.future_requirement,
                        manifest["fixture.{0}.future.requirement".format(identifier)],
                    )

    def test_stack_trace_corpus_has_multiline_structure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            text = (target / "stack-traces.log").read_text(encoding="utf-8")
            self.assertEqual(12, len(text.splitlines()))
            self.assertIn("Caused by: java.io.IOException", text)
            self.assertIn("\t... 12 more", text)
            self.assertIn("Suppressed: java.lang.RuntimeException", text)
            self.assertIn("node-a.example.test", text)

    def test_ansi_corpus_contains_deliberate_control_sequences(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            # Decode bytes directly so universal newlines do not rewrite the deliberate CR.
            text = (target / "ansi-sequences.log").read_bytes().decode("utf-8")
            self.assertIn("\u001b[31m", text)
            self.assertIn("\u001b[1;4;38;5;196m", text)
            self.assertIn("\u001b[2K\r", text)
            self.assertIn("\u001b]8;;https://docs.example.test\u0007", text)
            self.assertIn("\u001b[?25l", text)
            self.assertIn("\u0007", text)

    def test_long_line_corpus_has_declared_lengths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            lines = (target / "long-lines.log").read_text(encoding="utf-8").splitlines()
            self.assertEqual(1 + len(generator.BOUNDARY_LENGTHS) + 1, len(lines))
            for length in generator.BOUNDARY_LENGTHS:
                line = next(item for item in lines if item.startswith(
                    "boundary:{0} ".format(length)
                ))
                self.assertEqual(len("boundary:{0} ".format(length)) + length, len(line))
            long_line = next(item for item in lines if item.startswith("long:"))
            self.assertEqual(
                len("long:{0} ".format(generator.LONG_LINE_CODEPOINTS))
                + generator.LONG_LINE_CODEPOINTS,
                len(long_line),
            )
            self.assertEqual("tail: short line", lines[-1])

    def test_unicode_corpus_contains_expected_scalars(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            text = (target / "unicode.log").read_text(encoding="utf-8")
            for scalar in ("\u0301", "\u00e9", "\u202e", "\u2066", "\u200b", "\ufe0f",
                           "\u6f22", "\u00a0", "\u2003", "\u0001", "\u007f"):
                with self.subTest(scalar=hex(ord(scalar))):
                    self.assertIn(scalar, text)
            self.assertIn("\U0001f469", text)
            self.assertIn("\U0001f4bb", text)


if __name__ == "__main__":
    unittest.main()
