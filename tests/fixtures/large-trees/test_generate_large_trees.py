"""Deterministic large-tree fixture tests.

Run from the repository root:

    python -m unittest discover -s tests/fixtures/large-trees -p 'test_*.py'
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

import generate_large_trees as generator  # noqa: E402

HERE = Path(__file__).parent
MANIFEST_NAME = generator.MANIFEST_NAME


def parse_manifest() -> dict[str, str]:
    lines = (HERE / MANIFEST_NAME).read_text(encoding="ascii").splitlines()
    return {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in lines
        if line and not line.startswith("#")
    }


def generate_into(target: Path) -> str:
    with mock.patch.object(sys, "argv", ["generate_large_trees.py", str(target)]):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            generator.main()
        return output.getvalue()


def files_in(target: Path) -> list[Path]:
    return sorted(
        (path for path in target.rglob("*") if path.is_file() and path.name != MANIFEST_NAME),
        key=lambda item: item.relative_to(target).as_posix(),
    )


def digest_of(target: Path, files: list[Path]) -> tuple[str, int]:
    content = hashlib.sha256()
    total = 0
    for path in files:
        data = path.read_bytes()
        total += len(data)
        relative = path.relative_to(target).as_posix()
        content.update(relative.encode("utf-8") + b"\n")
        content.update(hashlib.sha256(data).hexdigest().encode("ascii") + b"\n")
    return content.hexdigest(), total


class GenerateLargeTreesTest(unittest.TestCase):
    def test_regeneration_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory() as first_dir, tempfile.TemporaryDirectory() as second_dir:
            first, second = Path(first_dir), Path(second_dir)
            generate_into(first)
            generate_into(second)
            first_files = [p.relative_to(first).as_posix() for p in files_in(first)]
            second_files = [p.relative_to(second).as_posix() for p in files_in(second)]
            self.assertEqual(first_files, second_files)
            for relative in first_files:
                with self.subTest(file=relative):
                    self.assertEqual((first / relative).read_bytes(), (second / relative).read_bytes())
            self.assertEqual(
                (first / MANIFEST_NAME).read_bytes(), (second / MANIFEST_NAME).read_bytes()
            )

    def test_manifest_matches_generated_tree(self) -> None:
        manifest = parse_manifest()
        self.assertEqual(str(generator.SCHEMA_VERSION), manifest["schema.version"])
        self.assertEqual(generator.CORPUS_VERSION, manifest["corpus.version"])
        self.assertEqual(generator.GENERATOR, manifest["generator"])
        self.assertEqual("not-implemented", manifest["current.listing.consumer"])
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            files = files_in(target)
            digest, total = digest_of(target, files)
            self.assertEqual(str(len(files)), manifest["file.count"])
            self.assertEqual(str(total), manifest["total.bytes"])
            self.assertEqual(digest, manifest["content.sha256"])
            self.assertEqual(
                str(len({path.parent for path in files})), manifest["directory.count"]
            )
            self.assertEqual(
                str(max(len(path.relative_to(target).parts) for path in files)),
                manifest["max.path.components"],
            )
            self.assertEqual(
                (HERE / MANIFEST_NAME).read_bytes(), (target / MANIFEST_NAME).read_bytes()
            )

    def test_paths_are_safe_and_bounded(self) -> None:
        manifest = parse_manifest()
        max_components = int(manifest["max.path.components"])
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            for path in target.rglob("*"):
                relative = path.relative_to(target)
                with self.subTest(path=relative.as_posix()):
                    self.assertFalse(path.is_symlink())
                    self.assertNotIn("..", relative.parts)
                    self.assertLessEqual(len(relative.parts), max_components)
                    self.assertTrue(all(len(part) <= 255 for part in relative.parts))

    def test_boundary_shapes_are_present(self) -> None:
        manifest = parse_manifest()
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            generate_into(target)
            files = files_in(target)
            long_files = [path for path in files if len(path.name) == int(
                manifest["category.long.name.length"]
            )]
            self.assertEqual(1, len(long_files))
            large_files = [path for path in files if path.suffix == ".bin"]
            self.assertEqual(int(manifest["category.large.files"]), len(large_files))
            for path in large_files:
                self.assertEqual(int(manifest["category.large.file.bytes"]), path.stat().st_size)
            deep_files = [
                path
                for path in files
                if len(path.relative_to(target).parts) == int(manifest["max.path.components"])
            ]
            self.assertTrue(deep_files)
            self.assertTrue(all(path.name == "leaf.txt" for path in deep_files))


if __name__ == "__main__":
    unittest.main()
