#!/usr/bin/env python3
"""Generate deterministic hostile archive and YAML fixtures at test time.

Dangerous or large payloads are written only to the requested output directory, which is
expected to be temporary or ignored by Git. The committed manifest records source digests,
sizes, and expectations for byte-identical regeneration. No archive is extracted to disk.
"""

from __future__ import annotations

import hashlib
import struct
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
GENERATOR = "tests/security/hostile-inputs/generate_hostile_inputs.py"
DEFAULT_OUTPUT = "tests/security/hostile-inputs/generated"

DOS_TIME = 0
DOS_DATE = 0x5D76
VERSION_NEEDED = 20
VERSION_MADE_BY = (3 << 8) | VERSION_NEEDED
REGULAR_MODE = 0o100644
SYMLINK_MODE = 0o120777
LOCAL_HEADER = "<IHHHHHIIIHH"
CENTRAL_HEADER = "<IHHHHHHIIIHHHHHII"
END_HEADER = "<IHHHHIIH"

BOMB_BYTES = 8 * 1024 * 1024
BOMB_FILL = 65
LARGE_SCALAR_CODE_POINTS = 256 * 1024
DEEP_NESTING_LEVELS = 120
ALIAS_COUNT = 150
NODE_COUNT = 2000
ENCRYPTION_PASSWORD = b"dmctest"
ENCRYPTED_PLAINTEXT = b"dmctest zipcrypto entry\n"

CRC_TABLE = []
for _index in range(256):
    _crc = _index
    for _ in range(8):
        _crc = (_crc >> 1) ^ (0xEDB88320 if _crc & 1 else 0)
    CRC_TABLE.append(_crc)


@dataclass(frozen=True)
class ZipEntry:
    """One central-directory entry written by the deterministic ZIP writer."""

    name: str
    payload: bytes
    size: int
    crc: int
    flags: int = 0
    method: int = 0
    external_attr: int = REGULAR_MODE << 16


@dataclass(frozen=True)
class Fixture:
    """Metadata recorded in the versioned fixture manifest."""

    relative_path: str
    category: str
    risk: str
    current_reader: str
    future_requirement: str
    properties: tuple[tuple[str, str], ...] = ()

    @property
    def identifier(self) -> str:
        return Path(self.relative_path).stem.replace("-", "_")


class BitWriter:
    """Minimal LSB-first bit writer for a fixed-Huffman DEFLATE block."""

    def __init__(self) -> None:
        self.output = bytearray()
        self.bit = 0

    def write_bits(self, value: int, width: int) -> None:
        for offset in range(width):
            if self.bit == 0:
                self.output.append(0)
            if (value >> offset) & 1:
                self.output[-1] |= 1 << self.bit
            self.bit += 1
            if self.bit == 8:
                self.bit = 0

    def write_symbol(self, symbol: int) -> None:
        if symbol <= 143:
            code, width = 0x30 + symbol, 8
        elif symbol <= 255:
            code, width = 0x190 + symbol - 144, 9
        elif symbol <= 279:
            code, width = symbol - 256, 7
        else:
            code, width = 0xC0 + symbol - 280, 8
        reversed_code = 0
        for _ in range(width):
            reversed_code = (reversed_code << 1) | (code & 1)
            code >>= 1
        self.write_bits(reversed_code, width)


class ZipCrypto:
    """Traditional PKZIP stream cipher used only by the encrypted fixture."""

    def __init__(self, password: bytes) -> None:
        self.keys = [0x12345678, 0x23456789, 0x34567890]
        for value in password:
            self._update(value)

    def _update(self, value: int) -> None:
        self.keys[0] = (self.keys[0] >> 8) ^ CRC_TABLE[(self.keys[0] ^ value) & 0xFF]
        self.keys[1] = (self.keys[1] + (self.keys[0] & 0xFF)) & 0xFFFFFFFF
        self.keys[1] = (self.keys[1] * 134775813 + 1) & 0xFFFFFFFF
        high = (self.keys[1] >> 24) & 0xFF
        self.keys[2] = (self.keys[2] >> 8) ^ CRC_TABLE[(self.keys[2] ^ high) & 0xFF]

    def _stream_byte(self) -> int:
        temp = (self.keys[2] | 2) & 0xFFFF
        return ((temp * (temp ^ 1)) >> 8) & 0xFF

    def encrypt(self, plaintext: bytes, crc: int) -> bytes:
        header = bytes(range(11)) + bytes([(crc >> 24) & 0xFF])
        encrypted = bytearray()
        for value in header + plaintext:
            cipher = value ^ self._stream_byte()
            self._update(value)
            encrypted.append(cipher)
        return bytes(encrypted)


def crc32(data: bytes) -> int:
    return zlib.crc32(data) & 0xFFFFFFFF


def fixed_deflate_repeated(total: int, value: int) -> bytes:
    """Encode one repeated byte with fixed Huffman codes and distance-one matches."""
    writer = BitWriter()
    writer.write_bits(0b011, 3)
    writer.write_symbol(value)
    remaining = total - 1
    while remaining >= 258:
        writer.write_symbol(285)
        writer.write_bits(0, 5)
        remaining -= 258
    for _ in range(remaining):
        writer.write_symbol(value)
    writer.write_symbol(256)
    return bytes(writer.output)


def stored_entry(name: str, data: bytes, external_attr: int = REGULAR_MODE << 16) -> ZipEntry:
    return ZipEntry(name, data, len(data), crc32(data), external_attr=external_attr)


def zip_bytes(entries: Iterable[ZipEntry]) -> bytes:
    """Write a ZIP with fixed timestamps and no data descriptors or extra fields."""
    local = bytearray()
    central = bytearray()
    for entry in entries:
        name = entry.name.encode("ascii")
        offset = len(local)
        local.extend(
            struct.pack(
                LOCAL_HEADER,
                0x04034B50,
                VERSION_NEEDED,
                entry.flags,
                entry.method,
                DOS_TIME,
                DOS_DATE,
                entry.crc,
                len(entry.payload),
                entry.size,
                len(name),
                0,
            )
        )
        local.extend(name)
        local.extend(entry.payload)
        central.extend(
            struct.pack(
                CENTRAL_HEADER,
                0x02014B50,
                VERSION_MADE_BY,
                VERSION_NEEDED,
                entry.flags,
                entry.method,
                DOS_TIME,
                DOS_DATE,
                entry.crc,
                len(entry.payload),
                entry.size,
                len(name),
                0,
                0,
                0,
                0,
                entry.external_attr,
                offset,
            )
        )
        central.extend(name)
    count = len(entries)
    end = struct.pack(
        END_HEADER, 0x06054B50, 0, 0, count, count, len(central), len(local), 0
    )
    return bytes(local) + bytes(central) + end


def write_text(path: Path, text: str) -> None:
    with path.open("wb") as stream:
        stream.write(text.encode("ascii"))


def write_bytes(path: Path, data: bytes) -> None:
    with path.open("wb") as stream:
        stream.write(data)


def zip_slip_archive() -> bytes:
    name = "../deploymc-zip-slip.txt"
    return zip_bytes([stored_entry(name, b"zip-slip test payload\n")])


def zip_bomb_archive() -> bytes:
    plaintext = bytes([BOMB_FILL]) * BOMB_BYTES
    payload = fixed_deflate_repeated(BOMB_BYTES, BOMB_FILL)
    entry = ZipEntry("bomb.txt", payload, BOMB_BYTES, crc32(plaintext), method=8)
    return zip_bytes([entry])


def duplicate_entries_archive() -> bytes:
    return zip_bytes(
        [
            stored_entry("duplicate.txt", b"first payload\n"),
            stored_entry("duplicate.txt", b"second payload\n"),
        ]
    )


def duplicate_descriptor_archive() -> bytes:
    return zip_bytes(
        [
            stored_entry("plugin.yml", b"name: FirstDescriptor\n"),
            stored_entry("plugin.yml", b"name: SecondDescriptor\n"),
        ]
    )


def encrypted_entry_archive() -> bytes:
    crc = crc32(ENCRYPTED_PLAINTEXT)
    encrypted = ZipCrypto(ENCRYPTION_PASSWORD).encrypt(ENCRYPTED_PLAINTEXT, crc)
    entry = ZipEntry("secret.txt", encrypted, len(ENCRYPTED_PLAINTEXT), crc, flags=0x0001)
    return zip_bytes([entry])


def symlink_entry_archive() -> bytes:
    target = b"../outside/deploymc-symlink-target.txt"
    return zip_bytes(
        [stored_entry("link.txt", target, external_attr=SYMLINK_MODE << 16)]
    )


def deep_nesting_yaml() -> bytes:
    lines = [
        "  " * index + "level{0:03d}:".format(index + 1)
        for index in range(DEEP_NESTING_LEVELS)
    ]
    lines.append("  " * DEEP_NESTING_LEVELS + "leaf: deep-nesting-value")
    return ("\n".join(lines) + "\n").encode("ascii")


def alias_expansion_yaml() -> bytes:
    lines = ["base: &anchor [alpha, beta, gamma]"]
    lines.extend(
        "alias{0:03d}: *anchor".format(index + 1) for index in range(ALIAS_COUNT)
    )
    return ("\n".join(lines) + "\n").encode("ascii")


def large_scalar_yaml() -> bytes:
    scalar = "A" * LARGE_SCALAR_CODE_POINTS
    return ('value: "{0}"\n'.format(scalar)).encode("ascii")


def many_nodes_yaml() -> bytes:
    lines = [
        "key{0:04d}: value{1:04d}".format(index, index) for index in range(NODE_COUNT)
    ]
    return ("\n".join(lines) + "\n").encode("ascii")


def fixtures() -> list[Fixture]:
    source_review = "source-review"
    return [
        Fixture(
            "archives/zip-slip.zip",
            "archive",
            "unsafe-entry-name",
            "not-rejected-by-" + source_review,
            "reject-unsafe-entry-name",
            (("entry.name", "../deploymc-zip-slip.txt"),),
        ),
        Fixture(
            "archives/zip-bomb.zip",
            "archive",
            "compression-ratio",
            "not-expanded-by-" + source_review,
            "enforce-expansion-and-ratio-limits",
            (
                ("uncompressed.bytes", str(BOMB_BYTES)),
                ("compression.method", "8"),
                ("compression.scheme", "fixed-huffman-run"),
            ),
        ),
        Fixture(
            "archives/duplicate-entries.zip",
            "archive",
            "duplicate-entry-name",
            "not-rejected-by-" + source_review,
            "reject-duplicate-entry-names",
            (("duplicate.name", "duplicate.txt"), ("duplicate.count", "2")),
        ),
        Fixture(
            "archives/duplicate-descriptor.zip",
            "archive",
            "duplicate-descriptor",
            "rejected:diff.descriptor-duplicate",
            "reject-duplicate-descriptor",
            (("duplicate.name", "plugin.yml"), ("duplicate.count", "2")),
        ),
        Fixture(
            "archives/encrypted-entry.zip",
            "archive",
            "encrypted-entry",
            "rejected:diff.zip-encrypted",
            "reject-encrypted-entries",
            (
                ("encryption", "zipcrypto"),
                ("password", ENCRYPTION_PASSWORD.decode("ascii")),
                ("flag.bits", "0x0001"),
            ),
        ),
        Fixture(
            "archives/symlink-entry.zip",
            "archive",
            "symlink-entry",
            "not-rejected-by-" + source_review,
            "reject-symlink-entries",
            (
                ("mode", "0o120777"),
                ("target", "../outside/deploymc-symlink-target.txt"),
            ),
        ),
        Fixture(
            "yaml/deep-nesting.yml",
            "yaml",
            "nesting-depth",
            "product-check-not-executed",
            "bound-nesting-depth",
            (
                ("depth", str(DEEP_NESTING_LEVELS)),
                ("limit.default", "100"),
                ("limit.small", "8"),
            ),
        ),
        Fixture(
            "yaml/alias-expansion.yml",
            "yaml",
            "alias-expansion",
            "product-check-not-executed",
            "bound-alias-expansions",
            (
                ("aliases", str(ALIAS_COUNT)),
                ("limit.default", "100"),
                ("limit.small", "2"),
            ),
        ),
        Fixture(
            "yaml/large-scalar.yml",
            "yaml",
            "large-scalar",
            "product-check-not-executed",
            "bound-code-points",
            (
                ("scalar.codepoints", str(LARGE_SCALAR_CODE_POINTS)),
                ("limit.default", "1000000"),
                ("limit.small", "4096"),
            ),
        ),
        Fixture(
            "yaml/many-nodes.yml",
            "yaml",
            "node-count",
            "product-check-not-executed",
            "bound-node-count",
            (
                ("nodes", str(NODE_COUNT)),
                ("limit.default", "1000000"),
                ("limit.small", "128"),
            ),
        ),
    ]


def payloads() -> dict[str, bytes]:
    return {
        "archives/zip-slip.zip": zip_slip_archive(),
        "archives/zip-bomb.zip": zip_bomb_archive(),
        "archives/duplicate-entries.zip": duplicate_entries_archive(),
        "archives/duplicate-descriptor.zip": duplicate_descriptor_archive(),
        "archives/encrypted-entry.zip": encrypted_entry_archive(),
        "archives/symlink-entry.zip": symlink_entry_archive(),
        "yaml/deep-nesting.yml": deep_nesting_yaml(),
        "yaml/alias-expansion.yml": alias_expansion_yaml(),
        "yaml/large-scalar.yml": large_scalar_yaml(),
        "yaml/many-nodes.yml": many_nodes_yaml(),
    }


def manifest_text(generated: dict[str, bytes], records: list[Fixture]) -> str:
    lines = [
        "# DeployMC hostile-input fixture manifest. Generated; do not edit.",
        "# Binary and large payloads are generated at test time and never committed.",
        "# current.reader values are static source review, not executed product checks.",
        "schema.version={0}".format(SCHEMA_VERSION),
        "corpus.version={0}".format(CORPUS_VERSION),
        "generator={0}".format(GENERATOR),
        "fixture.count={0}".format(len(records)),
        "fixture.list={0}".format(",".join(record.relative_path for record in records)),
    ]
    for record in records:
        data = generated[record.relative_path]
        identifier = record.identifier
        lines.extend(
            [
                "fixture.{0}.file={1}".format(identifier, record.relative_path),
                "fixture.{0}.category={1}".format(identifier, record.category),
                "fixture.{0}.risk={1}".format(identifier, record.risk),
                "fixture.{0}.current.reader={1}".format(
                    identifier, record.current_reader
                ),
                "fixture.{0}.future.requirement={1}".format(
                    identifier, record.future_requirement
                ),
                "fixture.{0}.sha256={1}".format(
                    identifier, hashlib.sha256(data).hexdigest()
                ),
                "fixture.{0}.bytes={1}".format(identifier, len(data)),
            ]
        )
        for name, value in record.properties:
            lines.append("fixture.{0}.{1}={2}".format(identifier, name, value))
    return "\n".join(lines) + "\n"


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(DEFAULT_OUTPUT)
    target.mkdir(parents=True, exist_ok=True)
    (target / "archives").mkdir(exist_ok=True)
    (target / "yaml").mkdir(exist_ok=True)
    generated = payloads()
    for relative_path, data in generated.items():
        write_bytes(target / relative_path, data)
    write_text(target / "hostile-inputs-manifest.properties", manifest_text(generated, fixtures()))
    print(
        "fixtures={0} schema={1} corpus={2}".format(
            len(generated), SCHEMA_VERSION, CORPUS_VERSION
        )
    )


if __name__ == "__main__":
    main()
