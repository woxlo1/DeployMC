#!/usr/bin/env python3
"""Generate deterministic configuration corpora for loader and migration tests.

The committed YAML files are small and intentionally versioned. Java tests in
`core` feed them to the real ConfigManager and ConfigValidator; no server or
host configuration is touched. Regeneration is byte-identical.
"""

from __future__ import annotations

import hashlib
import sys
from dataclasses import dataclass
from pathlib import Path

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
GENERATOR = "tests/fixtures/config-corpora/generate_config_corpora.py"
DEFAULT_OUTPUT = "tests/fixtures/config-corpora"
ENCODING = "UTF-8"


@dataclass(frozen=True)
class Fixture:
    """Expected loader and validator behavior for one committed YAML file."""

    relative_path: str
    category: str
    outcome: str
    validator_issue: str
    validator_warning: str
    loader_key: str
    found_version: str

    @property
    def identifier(self) -> str:
        return Path(self.relative_path).stem.replace("-", "_")


VALID = """config-version: 1
language: en
auth:
  mode: token
"""

VALID_ROOT_WARNING = """config-version: 1
language: en
fixture-note: ignored-by-schema
"""

INVALID_AUTH_MODE = """config-version: 1
auth:
  mode: none
"""

INVALID_PORT = """config-version: 1
bind:
  address: 127.0.0.1
  port: 70000
"""

LEGACY_VERSION = """config-version: 0
language: en
auth:
  mode: token
"""

FUTURE_VERSION = """config-version: 2
language: en
auth:
  mode: token
"""

MISSING_VERSION = """language: en
auth:
  mode: token
"""


def fixtures() -> list[Fixture]:
    return [
        Fixture("valid-current.yml", "valid", "pass", "", "", "", "1"),
        Fixture(
            "valid-root-warning.yml",
            "valid-warning",
            "pass",
            "",
            "config.warning.unknownKey",
            "",
            "1",
        ),
        Fixture(
            "invalid-auth-mode.yml",
            "invalid",
            "fail",
            "config.error.enum",
            "",
            "config.error.invalid",
            "1",
        ),
        Fixture(
            "invalid-port.yml",
            "invalid",
            "fail",
            "config.error.maximum",
            "",
            "config.error.invalid",
            "1",
        ),
        Fixture(
            "legacy-version-0.yml",
            "legacy",
            "fail",
            "config.error.const",
            "",
            "config.error.migrationPath",
            "0",
        ),
        Fixture(
            "future-version-2.yml",
            "future",
            "fail",
            "config.error.const",
            "",
            "config.error.version",
            "2",
        ),
        Fixture(
            "missing-version.yml",
            "missing-version",
            "fail",
            "config.error.required",
            "",
            "config.error.versionMissing",
            "",
        ),
    ]


def payloads() -> dict[str, str]:
    return {
        "valid-current.yml": VALID,
        "valid-root-warning.yml": VALID_ROOT_WARNING,
        "invalid-auth-mode.yml": INVALID_AUTH_MODE,
        "invalid-port.yml": INVALID_PORT,
        "legacy-version-0.yml": LEGACY_VERSION,
        "future-version-2.yml": FUTURE_VERSION,
        "missing-version.yml": MISSING_VERSION,
    }


def manifest_text(generated: dict[str, str], records: list[Fixture]) -> str:
    lines = [
        "# DeployMC config corpus manifest. Generated; do not edit.",
        "# YAML files are small and committed; Java tests exercise the real loader.",
        "schema.version={0}".format(SCHEMA_VERSION),
        "corpus.version={0}".format(CORPUS_VERSION),
        "generator={0}".format(GENERATOR),
        "encoding={0}".format(ENCODING),
        "line.separator=LF",
        "bom=false",
        "fixture.count={0}".format(len(records)),
        "fixture.list={0}".format(",".join(record.relative_path for record in records)),
    ]
    for record in records:
        data = generated[record.relative_path].encode("utf-8")
        identifier = record.identifier
        lines.extend(
            [
                "fixture.{0}.file={1}".format(identifier, record.relative_path),
                "fixture.{0}.category={1}".format(identifier, record.category),
                "fixture.{0}.expected.outcome={1}".format(identifier, record.outcome),
                "fixture.{0}.expected.validator.issue={1}".format(
                    identifier, record.validator_issue
                ),
                "fixture.{0}.expected.validator.warning={1}".format(
                    identifier, record.validator_warning
                ),
                "fixture.{0}.expected.loader.key={1}".format(identifier, record.loader_key),
                "fixture.{0}.found.version={1}".format(identifier, record.found_version),
                "fixture.{0}.sha256={1}".format(identifier, hashlib.sha256(data).hexdigest()),
                "fixture.{0}.bytes={1}".format(identifier, len(data)),
            ]
        )
    return "\n".join(lines) + "\n"


def write_text(path: Path, text: str) -> None:
    with path.open("wb") as stream:
        stream.write(text.encode("utf-8"))


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(DEFAULT_OUTPUT)
    target.mkdir(parents=True, exist_ok=True)
    generated = payloads()
    for relative_path, text in generated.items():
        write_text(target / relative_path, text)
    write_text(target / "config-corpus-manifest.properties", manifest_text(generated, fixtures()))
    print("fixtures={0} schema={1} corpus={2}".format(len(generated), SCHEMA_VERSION, CORPUS_VERSION))


if __name__ == "__main__":
    main()
