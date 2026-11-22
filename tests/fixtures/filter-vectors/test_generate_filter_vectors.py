"""Cross-implementation filter vector tests.

Run from the repository root:

    python -m unittest discover -s tests/fixtures/filter-vectors -p 'test_*.py'
"""

from __future__ import annotations

import contextlib
import hashlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

import generate_filter_vectors as generator  # noqa: E402

HERE = Path(__file__).parent
MANIFEST_NAME = "filter-vectors.json"
SPEC_EXAMPLES = (
    "level>=WARN",
    "logger:MyRpgPlugin",
    'plugin:"My Plugin"',
    "thread:/Server thread/",
    'text~"NullPointerException"',
    "text~/timeout \\d+ms/",
    "since:10m",
    "until:2026-01-15T09:00Z",
)
EXPECTED_REASONS = {
    "syntax.empty",
    "syntax.unbalanced-parenthesis",
    "syntax.unterminated-quote",
    "field.unknown",
    "level.invalid",
    "duration.invalid",
    "timestamp.invalid",
    "operator.unknown",
    "regex.complexity",
    "regex.too-long",
    "expression.too-long",
}


def manifest_bytes(path: Path = HERE / MANIFEST_NAME) -> bytes:
    return path.read_bytes()


def read_manifest(path: Path = HERE / MANIFEST_NAME) -> dict:
    return json.loads(manifest_bytes(path).decode("utf-8"))


def generate_into(target: Path) -> str:
    with mock.patch.object(sys, "argv", ["generate_filter_vectors.py", str(target)]):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            generator.main()
        return output.getvalue()


def vector_digest(items: list[dict]) -> str:
    raw = json.dumps(items, ensure_ascii=True, sort_keys=True, separators=(",", ":")).encode("ascii")
    return hashlib.sha256(raw).hexdigest()


class GenerateFilterVectorsTest(unittest.TestCase):
    def test_manifest_is_ascii_lf_and_byte_identical(self) -> None:
        raw = manifest_bytes()
        self.assertFalse(raw.startswith(b"\xef\xbb\xbf"))
        self.assertNotIn(b"\r", raw)
        raw.decode("ascii")
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / MANIFEST_NAME
            output = generate_into(target)
            self.assertIn("vectors={0}".format(len(generator.vectors())), output)
            self.assertEqual(raw, target.read_bytes())

    def test_metadata_and_vector_digest(self) -> None:
        manifest = read_manifest()
        self.assertEqual(generator.SCHEMA_VERSION, manifest["schemaVersion"])
        self.assertEqual(generator.CORPUS_VERSION, manifest["corpusVersion"])
        self.assertEqual(generator.GENERATOR, manifest["generator"])
        self.assertEqual("UTF-8", manifest["encoding"])
        self.assertEqual("LF", manifest["lineSeparator"])
        self.assertFalse(manifest["bom"])
        self.assertEqual(set(generator.CONSUMER_STATUS), set(manifest["consumerStatus"]))
        for status in manifest["consumerStatus"].values():
            self.assertEqual("not-implemented", status)
        self.assertEqual(vector_digest(manifest["vectors"]), manifest["vectorsSha256"])

    def test_vector_identity_and_expected_values(self) -> None:
        manifest = read_manifest()
        vectors = manifest["vectors"]
        self.assertEqual(manifest["vectorCount"], len(vectors))
        self.assertEqual(len(vectors), len({vector["id"] for vector in vectors}))
        self.assertEqual(len(vectors), len({vector["expression"] for vector in vectors}))
        categories = {
            "comparison",
            "field",
            "time",
            "unicode",
            "escape",
            "boolean",
            "syntax",
            "validation",
            "limit",
        }
        for vector in vectors:
            with self.subTest(vector=vector["id"]):
                self.assertIn(vector["category"], categories)
                self.assertIn(vector["expected"], {"accept", "reject"})
                self.assertIsInstance(vector["expression"], str)

    def test_accepted_vectors_include_ast_and_evaluation(self) -> None:
        manifest = read_manifest()
        accepted = [vector for vector in manifest["vectors"] if vector["expected"] == "accept"]
        self.assertTrue(any(vector["expectedMatch"] for vector in accepted))
        self.assertTrue(any(not vector["expectedMatch"] for vector in accepted))
        for vector in accepted:
            with self.subTest(vector=vector["id"]):
                self.assertIn("ast", vector)
                self.assertIsInstance(vector["ast"], dict)
                self.assertIsInstance(vector["expectedMatch"], bool)
                event = vector.get("event", manifest["sampleEvent"])
                self.assertEqual(
                    {"level", "logger", "plugin", "thread", "text", "timestamp"}, set(event)
                )
                self.assertEqual({"now"}, set(manifest["evaluationContext"]))

    def test_rejected_vectors_have_stable_reasons(self) -> None:
        manifest = read_manifest()
        rejected = [vector for vector in manifest["vectors"] if vector["expected"] == "reject"]
        reasons = {vector["reason"] for vector in rejected}
        self.assertTrue(EXPECTED_REASONS.issubset(reasons))
        for vector in rejected:
            with self.subTest(vector=vector["id"]):
                self.assertNotIn("ast", vector)
                self.assertNotIn("expectedMatch", vector)
                self.assertTrue(vector["note"])

    def test_manifest_covers_master_specification_examples(self) -> None:
        manifest = read_manifest()
        accepted = {vector["expression"] for vector in manifest["vectors"] if vector["expected"] == "accept"}
        for expression in SPEC_EXAMPLES:
            with self.subTest(expression=expression):
                self.assertIn(expression, accepted)

    def test_limit_vectors_match_declared_limits(self) -> None:
        manifest = read_manifest()
        limits = manifest["limits"]
        vectors = {vector["id"]: vector for vector in manifest["vectors"]}
        self.assertGreater(limits["maxExpressionLength"], 0)
        self.assertGreater(limits["maxRegexLength"], 0)
        self.assertGreater(limits["maxNestingDepth"], 0)
        self.assertGreater(
            len(vectors["expression-too-long"]["expression"]), limits["maxExpressionLength"]
        )
        self.assertGreater(len(vectors["regex-too-long"]["expression"]), limits["maxRegexLength"])
        self.assertIn("(", vectors["regex-complexity"]["expression"])


if __name__ == "__main__":
    unittest.main()
