#!/usr/bin/env python3
"""Generate deterministic sandbox path corpora for unit tests and scheduled fuzzing.

Output uses Java-style escapes: backslash, quote, control characters and non-ASCII
characters are written as \\uXXXX so the files stay ASCII and reviewable in a diff.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

SAFE_SEGMENT = re.compile(r"[\w.][\w. -]*\Z", re.UNICODE)
RESERVED = {"CON", "PRN", "AUX", "NUL", "CLOCK$", "CONIN$", "CONOUT$"}
RESERVED |= {f"COM{i}" for i in range(1, 10)} | {f"LPT{i}" for i in range(1, 10)}

REJECT_SEEDS = [
    "..", "../", "..\\", "../..", "..\\..", "../../etc/passwd", "..\\..\\windows\\system32",
    "....//", "....\\\\", "..././", "..;/", "..\\./", "./../", "a/../../b", "a/..\\..\\b",
    "a/../..", "a/b/../../../c", "..%2f", "..%5c", "%2e%2e/", "%2e%2e\\", "%2E%2E/",
    "%2e%2e%2fetc", "%252e%252e%252f", "%252e%252e%255c", "%c0%ae%c0%ae/", "%e0%80%ae/",
    "%f0%80%80%ae/", "%uff0e%uff0e/", "%EF%BC%8E%EF%BC%8E/", "..%c0%af", "..%e0%80%af",
    "..\\%2e%2e/", "%2e%2e\\..\\", "..//", "\\\\", "//", "/etc/passwd", "/../etc", "\\Windows",
    "C:\\", "C:/", "C:foo", "d:..\\x", "\\\\server\\share", "\\\\?\\C:\\", "\\\\.\\pipe\\x",
    "~/../x", "~\\..\\x", "\uff0e\uff0e/", "\uff0fetc", "a\uff0fb", "a\uff0fb..",
    "\ud800", "\udc00", "a\ud800b", "a\u202eb", "a\u200fb", "\u0000", "a\u0000b",
    "\u0001", "\r", "\n", "\t", "\u007f", "a\u001fb", "CON", "con", "CoN.txt", "PRN",
    "AUX", "NUL", "COM1", "Com9.log", "LPT1", "LPT9.txt", "COM\u00b9", "CONIN$", "CONOUT$",
    "CLOCK$", "file.txt:stream", "file.txt::$DATA", "name.", "name ", " .", ".. ",
    "foo~1", "foo~12.txt", "foo<bar", "foo>bar", 'foo"bar', "foo|bar", "foo?bar", "foo*bar",
    "foo:bar", "a\\..\\b", "a\\.\\b", "a/./../b", "a/b/..\\..\\..", ".deploymc",
    ".deploymc/", ".deploymc/history/1.json", ".DEPLOYMC/config.yml", "DeployMC",
    "DeployMC/", "DeployMC/config.yml", "deploymc/tokens.d/admin.json", "DeployMC/tokens.d/a.json",
    "a/" + "x" * 300, "x" * 5000, "a/b/" + ".." * 200,
]

NOISE = [
    "", ".", "./", ".\\", "a", "a/b", "plugins/foo.jar", "dir/name.txt", "foo..bar",
    "ver-.yml", "ja-JP/messages.properties", "nested/a.b.c", "sp ace/file.txt",
    ".hidden", "a/.hidden/b", "日本語/設定.yml", "caf\u00e9/plugin.yml", "under_score",
]

CONTAINERS = ["", "plugins/", "Worlds/", "nested/dir/", "a/b/c/", ".\\", "./"]


def escape(value: str) -> str:
    # unicode_escape already emits \\ for literal backslashes and \uXXXX for non-ASCII.
    return value.encode("unicode_escape").decode("ascii")


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


def build_reject() -> list[str]:
    payloads: set[str] = set()
    for seed in REJECT_SEEDS:
        payloads.add(seed)
        for container in CONTAINERS:
            payloads.add(container + seed)
            payloads.add(seed + "/tail.txt")
            payloads.add(container + seed + "\\tail.txt")
    for noise in NOISE:
        for seed in REJECT_SEEDS[:80]:
            payloads.add(noise + "/" + seed if noise else seed)
    for i in range(1, 80):
        payloads.add("../" * i + "etc/passwd")
        payloads.add("..\\" * i + "Windows")
        payloads.add("a/" + "%2e%2e/" * i + "b")
    return sorted(payload for payload in payloads if not is_benign(payload))


def build_allowed() -> list[str]:
    allowed: set[str] = set()
    for container in ["", "plugins/", "nested/dir/"]:
        for leaf in [
            "foo.jar", "config.yml", "foo..bar", "ja-JP/messages.properties", ".hidden",
            "caf\u00e9/plugin.yml", "日本語/設定.yml", "a.b.c", "sp ace/file.txt",
        ]:
            allowed.add(container + leaf)
    allowed.add("")
    allowed.add(".")
    return sorted(allowed)


def write(path: Path, header: str, values: list[str]) -> None:
    lines = [header]
    lines.extend(escape(value) for value in values)
    path.write_text("\n".join(lines) + "\n", encoding="ascii")


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
    write(
        target / "traversal-corpus.txt",
        "# Traversal and invalid-name corpus. Every non-comment line must be rejected.",
        build_reject(),
    )
    write(
        target / "allowed-paths.txt",
        "# Paths that must resolve successfully. Empty means the sandbox root.",
        build_allowed(),
    )
    print(f"reject={len(build_reject())} allow={len(build_allowed())}")


if __name__ == "__main__":
    main()
