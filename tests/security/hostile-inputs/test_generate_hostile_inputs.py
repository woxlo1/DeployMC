"""Deterministic hostile-input fixture tests.

Run from the repository root:

    python -m unittest discover -s tests/security/hostile-inputs -p 'test_*.py'
"""

from __future__ import annotations

import contextlib
import hashlib
import io
import sys
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

import generate_hostile_inputs as generator  # noqa: E402

HERE = Path(__file__).parent
MANIFEST_NAME = "hostile-inputs-manifest.properties"


def parse_manifest() -> dict[str, str]:
    lines = (HERE / MANIFEST_NAME).read_text(encoding="ascii").splitlines()
    return {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in lines
        if line and not line.startswith("#")
    }


def generate_into(target: Path) -> None:
    with mock.patch.object(sys, "argv", ["generate_hostile_inputs.py", str(target)]):
        with contextlib.redirect_stdout(io.StringIO()):
            generator.main()


class GenerateHostileInputsTest(unittest.TestCase):
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

    def test_manifest_matches_generated_payloads(self) -> None:
        manifest = parse_manifest()
        records = generator.fixtures()
        self.assertEqual(str(generator.SCHEMA_VERSION), manifest["schema.version"])
        self.assertEqual(generator.CORPUS_VERSION, manifest["corpus.version"])
        self.assertEqual(generator.GENERATOR, manifest["generator"])
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
                    self.assertEqual(
                        hashlib.sha256(data).hexdigest(),
                        manifest["fixture.{0}.sha256".format(identifier)],
                    )
                    self.assertEqual(
                        str(len(data)), manifest["fixture.{0}.bytes".format(identifier)]
                    )
                    self.assertEqual(
                        record.current_reader,
                        manifest["fixture.{0}.current.reader".format(identifier)],
                    )
                    self.assertEqual(
                        record.future_requirement,
                        manifest["fixture.{0}.future.requirement".format(identifier)],
                    )

    def test_zip_fixtures_match_declared_structure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            archives = target / "archives"

            slip = self._archive(archives / "zip-slip.zip")
            self.assertEqual(["../deploymc-zip-slip.txt"], [i.filename for i in slip])

            bomb = self._archive(archives / "zip-bomb.zip")
            self.assertEqual(["bomb.txt"], [i.filename for i in bomb])
            self.assertEqual(generator.BOMB_BYTES, bomb[0].file_size)
            self.assertEqual(8, bomb[0].compress_type)
            self.assertLess(bomb[0].compress_size, 128 * 1024)
            raw = (archives / "zip-bomb.zip").read_bytes()
            name_length = len(bomb[0].filename.encode("ascii"))
            start = 30 + name_length
            payload = raw[start : start + bomb[0].compress_size]
            self.assertEqual(b"A" * generator.BOMB_BYTES, zlib.decompress(payload, -15))

            duplicate = self._archive(archives / "duplicate-entries.zip")
            self.assertEqual(["duplicate.txt", "duplicate.txt"], [i.filename for i in duplicate])
            descriptor = self._archive(archives / "duplicate-descriptor.zip")
            self.assertEqual(["plugin.yml", "plugin.yml"], [i.filename for i in descriptor])

            encrypted_bytes = (archives / "encrypted-entry.zip").read_bytes()
            with zipfile.ZipFile(io.BytesIO(encrypted_bytes)) as archive:
                info = archive.infolist()[0]
                self.assertEqual(0x0001, info.flag_bits & 0x0001)
                self.assertEqual(
                    generator.ENCRYPTED_PLAINTEXT,
                    archive.open(info, pwd=generator.ENCRYPTION_PASSWORD).read(),
                )

            symlink = self._archive(archives / "symlink-entry.zip")
            self.assertEqual(0o120777, symlink[0].external_attr >> 16)

    def test_yaml_fixtures_match_declared_limits(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            yaml_dir = target / "yaml"

            deep = (yaml_dir / "deep-nesting.yml").read_text(encoding="ascii")
            self.assertEqual(generator.DEEP_NESTING_LEVELS, sum(
                1 for line in deep.splitlines() if line.lstrip().startswith("level")
            ))
            self.assertIn("leaf: deep-nesting-value", deep)

            aliases = (yaml_dir / "alias-expansion.yml").read_text(encoding="ascii")
            self.assertEqual(generator.ALIAS_COUNT, aliases.count(": *anchor"))
            self.assertIn("base: &anchor [alpha, beta, gamma]", aliases)

            large = (yaml_dir / "large-scalar.yml").read_text(encoding="ascii")
            scalar = large.removeprefix('value: "').removesuffix('"\n')
            self.assertEqual(generator.LARGE_SCALAR_CODE_POINTS, len(scalar))
            self.assertEqual(set("A"), set(scalar))

            nodes = (yaml_dir / "many-nodes.yml").read_text(encoding="ascii")
            self.assertEqual(generator.NODE_COUNT, len(nodes.splitlines()))

    @staticmethod
    def _archive(path: Path) -> list[zipfile.ZipInfo]:
        with zipfile.ZipFile(io.BytesIO(path.read_bytes())) as archive:
            return archive.infolist()


if __name__ == "__main__":
    unittest.main()
