#!/usr/bin/env python3
"""Generate deterministic log corpora for console and backpressure tests.

Corpora contain deliberate control characters, long lines, and non-ASCII text. They are
written only to an ignored or temporary output directory; the committed manifest pins
their UTF-8 bytes. No product console consumer is invoked by this generator or its tests.
"""

from __future__ import annotations

import hashlib
import sys
from dataclasses import dataclass
from pathlib import Path

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
GENERATOR = "tests/fixtures/log-corpora/generate_log_corpora.py"
DEFAULT_OUTPUT = "tests/fixtures/log-corpora/generated"
ENCODING = "UTF-8"
LONG_LINE_CODEPOINTS = 64 * 1024
BOUNDARY_LENGTHS = (4095, 4096, 4097)
FIXED_TIMESTAMP = "2026-11-22T00:00:00.000Z"


@dataclass(frozen=True)
class Fixture:
    """Metadata recorded in the versioned log-corpus manifest."""

    relative_path: str
    category: str
    risk: str
    current_consumer: str
    future_requirement: str
    properties: tuple[tuple[str, str], ...] = ()

    @property
    def identifier(self) -> str:
        return Path(self.relative_path).stem.replace("-", "_")


def stack_trace_corpus() -> str:
    lines = [
        FIXED_TIMESTAMP + " INFO [Server thread/INFO]: fixture stack trace start",
        "java.lang.IllegalStateException: deliberate failure on node-a.example.test",
        "\tat io.github.woxlo1.deploymc.fixture.FailingTask.run(FailingTask.java:17)",
        "\tat io.github.woxlo1.deploymc.fixture.FixtureRunner.call(FixtureRunner.java:42)",
        "\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)",
        "\t... 12 more",
        "Caused by: java.io.IOException: fixture cause from cache.example.test",
        "\tat io.github.woxlo1.deploymc.fixture.FixtureIO.read(FixtureIO.java:8)",
        "\tSuppressed: java.lang.RuntimeException: fixture suppressed detail",
        "\t\tat io.github.woxlo1.deploymc.fixture.FixtureIO.close(FixtureIO.java:19)",
        "\t... 3 more",
        FIXED_TIMESTAMP + " ERROR [Server thread/ERROR]: fixture stack trace end",
    ]
    return "\n".join(lines) + "\n"


def ansi_corpus() -> str:
    escape = "\u001b"
    lines = [
        FIXED_TIMESTAMP + " INFO [Server thread/INFO]: " + escape + "[31mred" + escape + "[0m",
        escape + "[1;4;38;5;196mbright underlined" + escape + "[0m",
        escape + "[2K\rprogress reset after carriage return",
        escape + "]8;;https://docs.example.test\u0007link" + escape + "]8;;\u0007",
        escape + "[?25lhidden cursor" + escape + "[?25h",
        escape + "[1A" + escape + "[2C cursor moved by CSI",
        "plain tail after escape sequences",
    ]
    return "\n".join(lines) + "\n"


def long_line_corpus() -> str:
    lines = [
        "boundary:{0} {1}".format(length, "B" * length) for length in BOUNDARY_LENGTHS
    ]
    lines.append("long:{0} {1}".format(LONG_LINE_CODEPOINTS, "L" * LONG_LINE_CODEPOINTS))
    lines.append("tail: short line")
    return "\n".join(lines) + "\n"


def unicode_corpus() -> str:
    lines = [
        FIXED_TIMESTAMP + " INFO [Server thread/INFO]: unicode fixture",
        "combining: e\u0301 and \u00e9",
        "bidi: \u202ereversed-label\u202c and \u2066isolated\u2069",
        "joiner: a\u200bb and \U0001f469\u200d\U0001f4bb",
        "variation: \u2764\ufe0f and \u261d\ufe0f",
        "cjk: \u6f22\u5b57 and \u3042\u3044\u3046",
        "spacing: a\u00a0b and a\u2003b",
        "controls: \u0001control-one and \u007fdel",
        "tail: unicode fixture end",
    ]
    return "\n".join(lines) + "\n"


def fixtures() -> list[Fixture]:
    not_implemented = "not-implemented"
    return [
        Fixture(
            "stack-traces.log",
            "multi-line",
            "stack-trace-segmentation",
            not_implemented,
            "split-and-bound-multiline-records",
            (("lines", "12"), ("has.caused.by", "true"), ("has.tab", "true")),
        ),
        Fixture(
            "ansi-sequences.log",
            "control",
            "ansi-and-osc-sequences",
            not_implemented,
            "strip-or-escape-control-sequences",
            (
                ("has.csi", "true"),
                ("has.osc8", "true"),
                ("has.carriage-return", "true"),
                ("has.bel", "true"),
            ),
        ),
        Fixture(
            "long-lines.log",
            "size",
            "oversized-lines",
            not_implemented,
            "truncate-long-lines",
            (
                ("long.line.codepoints", str(LONG_LINE_CODEPOINTS)),
                ("boundary.lengths", ",".join(str(value) for value in BOUNDARY_LENGTHS)),
            ),
        ),
        Fixture(
            "unicode.log",
            "unicode",
            "non-ascii-and-control-scalars",
            not_implemented,
            "handle-unicode-safely",
            (
                ("has.combining", "true"),
                ("has.bidi.override", "true"),
                ("has.emoji", "true"),
                ("has.zero-width-joiner", "true"),
            ),
        ),
    ]


def payloads() -> dict[str, str]:
    return {
        "stack-traces.log": stack_trace_corpus(),
        "ansi-sequences.log": ansi_corpus(),
        "long-lines.log": long_line_corpus(),
        "unicode.log": unicode_corpus(),
    }


def manifest_text(generated: dict[str, str], records: list[Fixture]) -> str:
    lines = [
        "# DeployMC log corpus manifest. Generated; do not edit.",
        "# Large or control-character payloads are generated at test time and never committed.",
        "# current.consumer is not-implemented because no product console log consumer exists.",
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
                "fixture.{0}.risk={1}".format(identifier, record.risk),
                "fixture.{0}.current.consumer={1}".format(
                    identifier, record.current_consumer
                ),
                "fixture.{0}.future.requirement={1}".format(
                    identifier, record.future_requirement
                ),
                "fixture.{0}.sha256={1}".format(
                    identifier, hashlib.sha256(data).hexdigest()
                ),
                "fixture.{0}.bytes={1}".format(identifier, len(data)),
                "fixture.{0}.codepoints={1}".format(
                    identifier, len(generated[record.relative_path])
                ),
            ]
        )
        for name, value in record.properties:
            lines.append("fixture.{0}.{1}={2}".format(identifier, name, value))
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
    write_text(target / "log-corpora-manifest.properties", manifest_text(generated, fixtures()))
    print(
        "fixtures={0} schema={1} corpus={2}".format(
            len(generated), SCHEMA_VERSION, CORPUS_VERSION
        )
    )


if __name__ == "__main__":
    main()
