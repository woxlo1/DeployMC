#!/usr/bin/env python3
"""Generate cross-implementation log filter vectors.

The committed JSON manifest is fixture data only. No Java, TypeScript, or Rust filter parser
exists yet, so no product implementation consumes these vectors.
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
GENERATOR = "tests/fixtures/filter-vectors/generate_filter_vectors.py"
DEFAULT_OUTPUT = "tests/fixtures/filter-vectors/filter-vectors.json"
ENCODING = "UTF-8"
LIMITS = {
    "maxExpressionLength": 4096,
    "maxRegexLength": 256,
    "maxNestingDepth": 16,
}
CONSUMER_STATUS = {
    "java": "not-implemented",
    "typescript": "not-implemented",
    "rust": "not-implemented",
}
SAMPLE_EVENT = {
    "level": "WARN",
    "logger": "MyRpgPlugin",
    "plugin": "My Plugin",
    "thread": "Server thread",
    "text": "timeout 12ms: NullPointerException, say \"hi\"",
    "timestamp": "2026-01-15T08:59:59Z",
}
EVALUATION_CONTEXT = {"now": "2026-01-15T09:00:00Z"}
UNICODE_LOGGER = "\u65e5\u672c\u8a9e\u30d7\u30e9\u30b0\u30a4\u30f3"


def comparison(field: str, operator: str, value: str) -> dict[str, object]:
    return {"kind": "comparison", "field": field, "operator": operator, "value": value}


def field_match(field: str, mode: str, value: str) -> dict[str, object]:
    return {"kind": "field", "field": field, "mode": mode, "value": value}


def time_match(field: str, value: str) -> dict[str, object]:
    return {"kind": "time", "field": field, "value": value}


def boolean(operator: str, *operands: dict[str, object]) -> dict[str, object]:
    return {"kind": "boolean", "operator": operator, "operands": list(operands)}


def logical_not(operand: dict[str, object]) -> dict[str, object]:
    return {"kind": "not", "operand": operand}


def accepted(
    vector_id: str,
    category: str,
    expression: str,
    ast: dict[str, object],
    expected_match: bool = True,
    event: dict[str, str] | None = None,
) -> dict[str, object]:
    vector: dict[str, object] = {
        "id": vector_id,
        "category": category,
        "expression": expression,
        "expected": "accept",
        "ast": ast,
        "expectedMatch": expected_match,
    }
    if event is not None:
        vector["event"] = event
    return vector


def rejected(vector_id: str, category: str, expression: str, reason: str, note: str) -> dict[str, object]:
    return {
        "id": vector_id,
        "category": category,
        "expression": expression,
        "expected": "reject",
        "reason": reason,
        "note": note,
    }


def unicode_event() -> dict[str, str]:
    event = dict(SAMPLE_EVENT)
    event["logger"] = UNICODE_LOGGER
    return event


def vectors() -> list[dict[str, object]]:
    logger_or_plugin = boolean(
        "OR",
        field_match("logger", "literal", "MyRpgPlugin"),
        field_match("plugin", "quoted", "My Plugin"),
    )
    return [
        accepted(
            "level-gte-warn",
            "comparison",
            "level>=WARN",
            comparison("level", ">=", "WARN"),
        ),
        accepted(
            "level-gte-error-no-match",
            "comparison",
            "level>=ERROR",
            comparison("level", ">=", "ERROR"),
            False,
        ),
        accepted(
            "level-lte-info",
            "comparison",
            "level<=INFO",
            comparison("level", "<=", "INFO"),
        ),
        accepted(
            "logger-literal",
            "field",
            "logger:MyRpgPlugin",
            field_match("logger", "literal", "MyRpgPlugin"),
        ),
        accepted(
            "plugin-quoted",
            "field",
            'plugin:"My Plugin"',
            field_match("plugin", "quoted", "My Plugin"),
        ),
        accepted(
            "thread-regex",
            "field",
            "thread:/Server thread/",
            field_match("thread", "regex", "Server thread"),
        ),
        accepted(
            "text-substring",
            "field",
            'text~"NullPointerException"',
            field_match("text", "substring", "NullPointerException"),
        ),
        accepted(
            "text-regex",
            "field",
            "text~/timeout \\d+ms/",
            field_match("text", "regex", "timeout \\d+ms"),
        ),
        accepted("since-duration", "time", "since:10m", time_match("since", "10m")),
        accepted(
            "until-timestamp",
            "time",
            "until:2026-01-15T09:00Z",
            time_match("until", "2026-01-15T09:00Z"),
        ),
        accepted(
            "logger-unicode",
            "unicode",
            "logger:" + UNICODE_LOGGER,
            field_match("logger", "literal", UNICODE_LOGGER),
            True,
            unicode_event(),
        ),
        accepted(
            "text-escaped-quote",
            "escape",
            'text~"say \\"hi\\""',
            field_match("text", "substring", 'say "hi"'),
        ),
        accepted(
            "boolean-not-and-group",
            "boolean",
            "NOT level<=INFO AND (logger:MyRpgPlugin OR plugin:\"My Plugin\")",
            boolean(
                "AND",
                logical_not(comparison("level", "<=", "INFO")),
                logger_or_plugin,
            ),
        ),
        rejected("empty-expression", "syntax", "", "syntax.empty", "Expression must not be empty."),
        rejected(
            "unbalanced-parenthesis",
            "syntax",
            "(logger:MyRpgPlugin",
            "syntax.unbalanced-parenthesis",
            "Opening parenthesis has no matching close.",
        ),
        rejected(
            "unterminated-quote",
            "syntax",
            'text~"oops',
            "syntax.unterminated-quote",
            "Quoted value has no closing quote.",
        ),
        rejected(
            "unknown-field",
            "validation",
            "owner:admin",
            "field.unknown",
            "owner is not a declared filter field.",
        ),
        rejected(
            "invalid-level",
            "validation",
            "level>=LOUD",
            "level.invalid",
            "LOUD is not a declared log level.",
        ),
        rejected(
            "invalid-duration",
            "validation",
            "since:10",
            "duration.invalid",
            "Relative duration needs a unit.",
        ),
        rejected(
            "invalid-timestamp",
            "validation",
            "until:not-a-time",
            "timestamp.invalid",
            "Absolute time must be an ISO-8601 instant.",
        ),
        rejected(
            "unknown-operator",
            "validation",
            "level~WARN",
            "operator.unknown",
            "Field comparisons use declared comparison operators.",
        ),
        rejected(
            "regex-complexity",
            "limit",
            "text~/((a+)+)$/",
            "regex.complexity",
            "Nested quantifiers are rejected before evaluation.",
        ),
        rejected(
            "regex-too-long",
            "limit",
            "text~/" + ("a" * 300) + "/",
            "regex.too-long",
            "Regex body exceeds the declared length limit.",
        ),
        rejected(
            "expression-too-long",
            "limit",
            "logger:" + ("A" * 5000),
            "expression.too-long",
            "Expression exceeds the declared length limit.",
        ),
    ]


def canonical_digest(items: list[dict[str, object]]) -> str:
    raw = json.dumps(items, ensure_ascii=True, sort_keys=True, separators=(",", ":")).encode("ascii")
    return hashlib.sha256(raw).hexdigest()


def manifest() -> dict[str, object]:
    items = vectors()
    return {
        "schemaVersion": SCHEMA_VERSION,
        "corpusVersion": CORPUS_VERSION,
        "generator": GENERATOR,
        "encoding": ENCODING,
        "lineSeparator": "LF",
        "bom": False,
        "consumerStatus": CONSUMER_STATUS,
        "limits": LIMITS,
        "evaluationContext": EVALUATION_CONTEXT,
        "sampleEvent": SAMPLE_EVENT,
        "vectorCount": len(items),
        "vectorsSha256": canonical_digest(items),
        "vectors": items,
    }


def manifest_text() -> str:
    return json.dumps(manifest(), ensure_ascii=True, indent=2, sort_keys=True) + "\n"


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(DEFAULT_OUTPUT)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as stream:
        stream.write(manifest_text().encode("ascii"))
    print("vectors={0} digest={1}".format(len(vectors()), canonical_digest(vectors())))


if __name__ == "__main__":
    main()
