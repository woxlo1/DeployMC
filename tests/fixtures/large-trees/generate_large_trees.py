#!/usr/bin/env python3
"""Generate a deterministic directory tree for listing and performance fixtures.

The tree is written only to an ignored or temporary output directory. The committed
manifest pins counts, path depth, and content digests. No product listing API or
server is invoked by this generator or its tests.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
GENERATOR = "tests/fixtures/large-trees/generate_large_trees.py"
DEFAULT_OUTPUT = "tests/fixtures/large-trees/generated"
MANIFEST_NAME = "large-tree-manifest.properties"
TOP_GROUPS = 3
SUBDIRS_PER_GROUP = 8
FILES_PER_SUBDIR = 16
DEEP_DEPTH = 12
LONG_NAME_BODY = 200
LONG_NAME_PREFIX = "long-"
LARGE_FILE_COUNT = 2
LARGE_FILE_BYTES = 64 * 1024
ENCODING = "UTF-8"


def relative_files() -> list[str]:
    paths: list[str] = []
    for group in range(TOP_GROUPS):
        for part in range(SUBDIRS_PER_GROUP):
            for index in range(FILES_PER_SUBDIR):
                paths.append(
                    "tree/group-{0}/part-{1}/file-{2:03d}.txt".format(group, part, index)
                )
    deep = ["tree/deep"] + ["level-{0:02d}".format(level) for level in range(DEEP_DEPTH)]
    paths.append("/".join(deep + ["leaf.txt"]))
    paths.append("tree/names/{0}{1}.txt".format(LONG_NAME_PREFIX, "N" * LONG_NAME_BODY))
    for index in range(LARGE_FILE_COUNT):
        paths.append("tree/sizes/large-{0}.bin".format(index))
    return paths


def file_bytes(relative_path: str) -> bytes:
    if relative_path.endswith(".bin"):
        pattern = b"0123456789abcdef"
        return (pattern * ((LARGE_FILE_BYTES // len(pattern)) + 1))[:LARGE_FILE_BYTES]
    return ("fixture:{0}\n".format(relative_path)).encode("utf-8")


def write_tree(target: Path) -> list[Path]:
    written: list[Path] = []
    for relative_path in relative_files():
        path = target / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("wb") as stream:
            stream.write(file_bytes(relative_path))
        written.append(path)
    return written


def tree_digests(written: list[Path], target: Path) -> tuple[str, int]:
    path_hash = hashlib.sha256()
    content_hash = hashlib.sha256()
    total = 0
    for path in sorted(written, key=lambda item: item.relative_to(target).as_posix()):
        relative = path.relative_to(target).as_posix()
        data = path.read_bytes()
        total += len(data)
        path_hash.update(relative.encode("utf-8") + b"\n")
        content_hash.update(relative.encode("utf-8") + b"\n")
        content_hash.update(hashlib.sha256(data).hexdigest().encode("ascii") + b"\n")
    return content_hash.hexdigest(), total


def manifest_text(target: Path, written: list[Path]) -> str:
    content_digest, total_bytes = tree_digests(written, target)
    directory_count = len({path.parent for path in written})
    max_components = max(len(path.relative_to(target).parts) for path in written)
    lines = [
        "# DeployMC large tree manifest. Generated; do not edit.",
        "# Directory contents are generated at test time and never committed.",
        "schema.version={0}".format(SCHEMA_VERSION),
        "corpus.version={0}".format(CORPUS_VERSION),
        "generator={0}".format(GENERATOR),
        "output={0}".format(DEFAULT_OUTPUT),
        "encoding={0}".format(ENCODING),
        "line.separator=LF",
        "file.count={0}".format(len(written)),
        "directory.count={0}".format(directory_count),
        "max.path.components={0}".format(max_components),
        "total.bytes={0}".format(total_bytes),
        "content.sha256={0}".format(content_digest),
        "category.top.groups={0}".format(TOP_GROUPS),
        "category.subdirs.per.group={0}".format(SUBDIRS_PER_GROUP),
        "category.files.per.subdir={0}".format(FILES_PER_SUBDIR),
        "category.deep.depth={0}".format(DEEP_DEPTH),
        "category.long.name.length={0}".format(
            len(LONG_NAME_PREFIX) + LONG_NAME_BODY + len(".txt")
        ),
        "category.large.files={0}".format(LARGE_FILE_COUNT),
        "category.large.file.bytes={0}".format(LARGE_FILE_BYTES),
        "current.listing.consumer=not-implemented",
        "future.requirement=bounded-listing-and-performance-tests",
    ]
    return "\n".join(lines) + "\n"


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(DEFAULT_OUTPUT)
    target.mkdir(parents=True, exist_ok=True)
    written = write_tree(target)
    with (target / MANIFEST_NAME).open("wb") as stream:
        stream.write(manifest_text(target, written).encode("ascii"))
    print(
        "files={0} directories={1} bytes={2}".format(
            len(written),
            len({path.parent for path in written}),
            sum(path.stat().st_size for path in written),
        )
    )


if __name__ == "__main__":
    main()
