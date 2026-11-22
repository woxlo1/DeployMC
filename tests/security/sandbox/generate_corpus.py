#!/usr/bin/env python3
"""Generate deterministic, versioned sandbox corpora and their expectation manifest.

Output uses Java-style escapes: backslash, quote, control characters and non-ASCII
characters are written as \\uXXXX or \\xXX so the files stay ASCII and reviewable in a diff.
All generated files use LF line endings on every host so their SHA-256 digests are stable.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
SUPPORTED_OS = ("linux", "windows", "macos")

SAFE_SEGMENT = re.compile(r"[\w.][\w. -]*\Z", re.UNICODE)
RESERVED = {"CON", "PRN", "AUX", "NUL", "CLOCK$", "CONIN$", "CONOUT$"}
RESERVED |= {f"COM{i}" for i in range(1, 10)} | {f"LPT{i}" for i in range(1, 10)}

# Seed chunks give every generated payload a stable category. The chunks are ordered so the
# flattened list equals the historical REJECT_SEEDS order: the noise window uses the first 80
# seeds, so reordering would silently change which payloads are generated.
DESCRIPTIONS = {
    "dot-dot": "Literal parent-directory segments and separator variants.",
    "encoded": "Percent-encoded traversal markers, including overlong and double encodings.",
    "absolute": "Root-relative, UNC, and drive-prefixed paths rejected before segment parsing.",
    "unicode-lookalike": "Full-width dot and separator lookalikes not normalized away by NFC.",
    "invalid-unicode": "Unpaired surrogates, bidirectional controls, and format characters.",
    "nul-and-control": "NUL bytes and ISO control characters rejected before normalization.",
    "windows-name": "Device names, alternate data streams, trailing dots or spaces, short names.",
    # Protected seeds are benign strings and are filtered from the reject corpus; host-owned
    # path behavior is covered by SandboxResolverTest instead of this bulk payload set.
    "protected": "Host-owned configuration and token paths request data can never reach.",
    "oversize": "Segments over 255 characters and requests over the 4096-character limit.",
    "ordinary": "Ordinary sandbox-relative paths that must resolve successfully.",
}

SEED_GROUPS: list[tuple[str, list[str]]] = [
    (
        "dot-dot",
        [
            "..", "../", "..\\", "../..", "..\\..", "../../etc/passwd", "..\\..\\windows\\system32",
            "....//", "....\\\\", "..././", "..;/", "..\\./", "./../", "a/../../b", "a/..\\..\\b",
            "a/../..", "a/b/../../../c",
        ],
    ),
    (
        "encoded",
        [
            "..%2f", "..%5c", "%2e%2e/", "%2e%2e\\", "%2E%2E/", "%2e%2e%2fetc",
            "%252e%252e%252f", "%252e%252e%255c", "%c0%ae%c0%ae/", "%e0%80%ae/",
            "%f0%80%80%ae/", "%uff0e%uff0e/", "%EF%BC%8E%EF%BC%8E/", "..%c0%af",
            "..%e0%80%af", "..\\%2e%2e/", "%2e%2e\\..\\",
        ],
    ),
    ("dot-dot", ["..//"]),
    (
        "absolute",
        [
            "\\\\", "//", "/etc/passwd", "/../etc", "\\Windows", "C:\\", "C:/", "C:foo",
            "d:..\\x", "\\\\server\\share", "\\\\?\\C:\\", "\\\\.\\pipe\\x",
        ],
    ),
    ("dot-dot", ["~/../x", "~\\..\\x"]),
    ("unicode-lookalike", ["\uff0e\uff0e/", "\uff0fetc", "a\uff0fb", "a\uff0fb.."]),
    ("invalid-unicode", ["\ud800", "\udc00", "a\ud800b", "a\u202eb", "a\u200fb"]),
    ("nul-and-control", ["\u0000", "a\u0000b", "\u0001", "\r", "\n", "\t", "\u007f", "a\u001fb"]),
    (
        "windows-name",
        [
            "CON", "con", "CoN.txt", "PRN", "AUX", "NUL", "COM1", "Com9.log", "LPT1",
            "LPT9.txt", "COM\u00b9", "CONIN$", "CONOUT$", "CLOCK$", "file.txt:stream",
            "file.txt::$DATA", "name.", "name ", " .", ".. ", "foo~1", "foo~12.txt",
            "foo<bar", "foo>bar", 'foo"bar', "foo|bar", "foo?bar", "foo*bar", "foo:bar",
        ],
    ),
    ("dot-dot", ["a\\..\\b", "a\\.\\b", "a/./../b", "a/b/..\\..\\.."]),
    (
        "protected",
        [
            ".deploymc", ".deploymc/", ".deploymc/history/1.json", ".DEPLOYMC/config.yml",
            "DeployMC", "DeployMC/", "DeployMC/config.yml", "deploymc/tokens.d/admin.json",
            "DeployMC/tokens.d/a.json",
        ],
    ),
    (
        "oversize",
        ["a/" + "x" * 300, "x" * 5000, "a/b/" + ".." * 200],
    ),
]

REJECT_SEEDS = [seed for _, seeds in SEED_GROUPS for seed in seeds]
SEED_CATEGORY = {
    seed: category for category, seeds in SEED_GROUPS for seed in seeds
}

NOISE = [
    "", ".", "./", ".\\", "a", "a/b", "plugins/foo.jar", "dir/name.txt", "foo..bar",
    "ver-.yml", "ja-JP/messages.properties", "nested/a.b.c", "sp ace/file.txt",
    ".hidden", "a/.hidden/b", "日本語/設定.yml", "café/plugin.yml", "under_score",
]

CONTAINERS = ["", "plugins/", "Worlds/", "nested/dir/", "a/b/c/", ".\\", "./"]


def escape(value: str) -> str:
    # unicode_escape already emits \\ for literal backslashes and \uXXXX for non-ASCII.
    return value.encode("unicode_escape").decode("ascii")


def escape_trailing_spaces(value: str) -> str:
    """Keep files whitespace-clean while decoding to the exact original payload."""
    escaped = escape(value)
    stripped = escaped.rstrip(" ")
    return stripped + "\\x20" * (len(escaped) - len(stripped))


def is_benign(value: str) -> bool:
    """True when the payload only contains ordinary segments after resolver normalization."""
    if value.startswith(("/", "\\")) or len(value) > 4096:
        return False
    segments = [part for part in value.replace("\\", "/").split("/") if part not in ("", ".")]
    if not segments:
        return True
    for segment in segments:
        if (
            not SAFE_SEGMENT.match(segment)
            or segment.startswith("..")
            or segment.endswith((".", " "))
            or len(segment) > 255
            or segment.upper() in RESERVED
        ):
            return False
    return True


def build_reject() -> dict[str, str]:
    """Returns escaped payload to category id, preserving first category assignment."""
    payloads: dict[str, str] = {}

    def add(payload: str, seed: str) -> None:
        payloads.setdefault(payload, SEED_CATEGORY[seed])

    for seed in REJECT_SEEDS:
        add(seed, seed)
        for container in CONTAINERS:
            add(container + seed, seed)
            add(seed + "/tail.txt", seed)
            add(container + seed + "\\tail.txt", seed)
    for noise in NOISE:
        for seed in REJECT_SEEDS[:80]:
            add(noise + "/" + seed if noise else seed, seed)
    for i in range(1, 80):
        add("../" * i + "etc/passwd", REJECT_SEEDS[0])
        add("..\\" * i + "Windows", REJECT_SEEDS[0])
        add("a/" + "%2e%2e/" * i + "b", "..%2f")

    return {
        payload: category
        for payload, category in payloads.items()
        if not is_benign(payload)
    }


def build_allowed() -> dict[str, str]:
    """Returns escaped allowed path to category id."""
    allowed: dict[str, str] = {}
    for container in ["", "plugins/", "nested/dir/"]:
        for leaf in [
            "foo.jar", "config.yml", "foo..bar", "ja-JP/messages.properties", ".hidden",
            "café/plugin.yml", "日本語/設定.yml", "a.b.c", "sp ace/file.txt",
        ]:
            allowed[container + leaf] = "ordinary"
    allowed[""] = "ordinary"
    allowed["."] = "ordinary"
    return allowed


def write_text(path: Path, text: str) -> None:
    """Write deterministic LF bytes on every host."""
    with path.open("w", encoding="ascii", newline="\n") as stream:
        stream.write(text)


def corpus_text(header: list[str], groups: dict[str, str], payloads: dict[str, str],
                outcome: str) -> str:
    lines = list(header)
    lines.append(
        "# @schema-version {0} @corpus-version {1}".format(SCHEMA_VERSION, CORPUS_VERSION)
    )
    for category, group_description in groups.items():
        members = sorted(value for value, owner in payloads.items() if owner == category)
        if not members:
            continue
        lines.append(
            "# @category {0} outcome={1} os=all :: {2}".format(
                category, outcome, group_description
            )
        )
        lines.extend(escape_trailing_spaces(payload) for payload in members)
    return "\n".join(lines) + "\n"


def manifest_text(target: Path, sources: list[tuple[str, str, str, dict[str, str]]]) -> str:
    lines = [
        "# DeployMC sandbox corpus manifest. Generated by generate_corpus.py; do not edit.",
        "# Versioned contract shared by Java unit tests, fuzz runs, and scheduled CI.",
        "schema.version={0}".format(SCHEMA_VERSION),
        "corpus.version={0}".format(CORPUS_VERSION),
        "generator=tests/security/sandbox/generate_corpus.py",
        "supported.os={0}".format(",".join(SUPPORTED_OS)),
        "# Overrides use category.<id>.os.<osId>. Schema 1 has none because SandboxResolver",
        "# intentionally applies Windows name rules on every host. Add an entry only after a",
        "# verified resolver difference requires a different outcome on a specific OS.",
        "override.count=0",
    ]
    for source_id, file_name, outcome, payloads in sources:
        digest = hashlib.sha256((target / file_name).read_bytes()).hexdigest()
        lines.extend(
            [
                "source.{0}.file={1}".format(source_id, file_name),
                "source.{0}.lines={1}".format(source_id, len(payloads)),
                "source.{0}.sha256={1}".format(source_id, digest),
                "source.{0}.default.outcome={1}".format(source_id, outcome),
            ]
        )
    used = sorted({owner for _, _, _, payloads in sources for owner in payloads.values()})
    lines.append("category.list={0}".format(",".join(used)))
    for category in used:
        lines.append("category.{0}.description={1}".format(category, DESCRIPTIONS[category]))
    return "\n".join(lines) + "\n"


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
    reject = build_reject()
    allowed = build_allowed()
    reject_groups = dict(DESCRIPTIONS)
    write_text(
        target / "traversal-corpus.txt",
        corpus_text(
            [
                "# DeployMC sandbox traversal corpus.",
                "# Every non-comment line must be rejected on every supported OS.",
            ],
            reject_groups,
            reject,
            "reject",
        ),
    )
    write_text(
        target / "allowed-paths.txt",
        corpus_text(
            [
                "# DeployMC sandbox allowed-path corpus.",
                "# Every non-comment line must resolve successfully on every supported OS.",
            ],
            {"ordinary": DESCRIPTIONS["ordinary"]},
            allowed,
            "accept",
        ),
    )
    write_text(
        target / "corpus-manifest.properties",
        manifest_text(
            target,
            [
                ("traversal", "traversal-corpus.txt", "reject", reject),
                ("allowed", "allowed-paths.txt", "accept", allowed),
            ]
        ),
    )
    print("reject={0} allow={1} schema={2} corpus={3}".format(
        len(reject), len(allowed), SCHEMA_VERSION, CORPUS_VERSION
    ))


if __name__ == "__main__":
    main()
