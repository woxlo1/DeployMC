"""Deterministic JWT vector and mock JWKS server tests.

Run from the repository root:

    python -m unittest discover -s tests/security/jwt -p 'test_*.py'
"""

from __future__ import annotations

import base64
import hashlib
import http.client
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

import generate_jwt_vectors as generator  # noqa: E402
import mock_jwks_server as jwks_server  # noqa: E402

HERE = Path(__file__).parent
GENERATED = (
    "jwt-vectors.jsonl",
    "jwks-initial.json",
    "jwks-rotated.json",
    "jwt-corpus-manifest.properties",
)


def decode_segment(segment: str) -> dict[str, object]:
    return json.loads(base64.urlsafe_b64decode(segment + "=="))


def load_vectors() -> list[dict[str, object]]:
    return [json.loads(line) for line in (HERE / "jwt-vectors.jsonl").read_text(
        encoding="ascii"
    ).splitlines()]


def load_manifest() -> dict[str, str]:
    return {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in (HERE / "jwt-corpus-manifest.properties").read_text(
            encoding="ascii"
        ).splitlines()
        if line and not line.startswith("#")
    }


class GenerateJwtVectorsTest(unittest.TestCase):
    def test_regeneration_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            with mock.patch.object(sys, "argv", ["generate_jwt_vectors.py", str(target)]):
                generator.main()
            for name in GENERATED:
                self.assertEqual(
                    (HERE / name).read_bytes(),
                    (target / name).read_bytes(),
                    name,
                )

    def test_manifest_matches_generated_sources_and_expectations(self) -> None:
        manifest = load_manifest()
        vectors = load_vectors()
        self.assertEqual("1", manifest["schema.version"])
        self.assertEqual(generator.CORPUS_VERSION, manifest["corpus.version"])
        self.assertEqual(generator.REFERENCE_UTC, manifest["reference.utc"])
        self.assertEqual("false", manifest["key.private.exported"])
        self.assertEqual("2048", manifest["key.size.bits"])
        self.assertEqual(len(vectors), int(manifest["source.vectors.entries"]))
        self.assertEqual([vector["id"] for vector in vectors],
                         manifest["vector.list"].split(","))
        for source, name in (
            ("vectors", "jwt-vectors.jsonl"),
            ("jwks.initial", "jwks-initial.json"),
            ("jwks.rotated", "jwks-rotated.json"),
        ):
            digest = hashlib.sha256((HERE / name).read_bytes()).hexdigest()
            self.assertEqual(digest, manifest["source.{0}.sha256".format(source)])
            self.assertEqual(name, manifest["source.{0}.file".format(source)])
        for vector in vectors:
            identifier = vector["id"]
            self.assertEqual(vector["category"], manifest["vector.{0}.category".format(identifier)])
            for state in ("initial", "rotated"):
                expectation = vector["expectations"][
                    "initial_jwks" if state == "initial" else "rotated_jwks"
                ]
                expected = expectation.get("error_code") or expectation["outcome"]
                self.assertEqual(expected, manifest["vector.{0}.{1}".format(identifier, state)])

    def test_generated_output_contains_no_private_key_material(self) -> None:
        for name in GENERATED:
            text = (HERE / name).read_text(encoding="ascii")
            self.assertNotIn("PRIVATE KEY", text, name)
            for field in ('"d":', '"p":', '"q":', '"dp":', '"dq":', '"qi":'):
                self.assertNotIn(field, text, name)

    def test_tokens_match_headers_claims_and_expected_signatures(self) -> None:
        initial = json.loads((HERE / "jwks-initial.json").read_text(encoding="ascii"))
        rotated = json.loads((HERE / "jwks-rotated.json").read_text(encoding="ascii"))
        initial_keys = {key["kid"]: key for key in initial["keys"]}
        rotated_keys = {key["kid"]: key for key in rotated["keys"]}
        self.assertEqual(2, len(initial_keys))
        self.assertEqual(["dmctest-key-rotated-2026-11-22"], list(rotated_keys))
        for vector in load_vectors():
            with self.subTest(vector=vector["id"]):
                parts = vector["token"].split(".")
                self.assertEqual(3, len(parts))
                self.assertEqual(vector["header"], decode_segment(parts[0]))
                self.assertEqual(vector["claims"], decode_segment(parts[1]))
                signature = base64.urlsafe_b64decode(parts[2] + "==")
                kid = vector["header"]["kid"]
                algorithm = vector["header"]["alg"]
                if vector["expectations"]["initial_jwks"]["outcome"] == "accept":
                    self.assertIn(kid, initial_keys)
                    self.assertTrue(generator.verify_rs256(
                        (parts[0] + "." + parts[1]).encode("ascii"),
                        signature,
                        initial_keys[kid],
                    ))
                elif algorithm == "none":
                    self.assertEqual(b"", signature)
                elif algorithm == "HS256":
                    self.assertNotEqual(b"", signature)
                    self.assertFalse(generator.verify_rs256(
                        (parts[0] + "." + parts[1]).encode("ascii"),
                        signature,
                        initial_keys[kid],
                    ))
                elif kid not in initial_keys:
                    self.assertNotIn(kid, rotated_keys)
                elif vector["id"] == "tampered":
                    self.assertFalse(generator.verify_rs256(
                        (parts[0] + "." + parts[1]).encode("ascii"),
                        signature,
                        initial_keys[kid],
                    ))
                else:
                    # Claim and time failures carry valid signatures so a verifier must reject
                    # them from claims rather than from cryptography.
                    self.assertTrue(generator.verify_rs256(
                        (parts[0] + "." + parts[1]).encode("ascii"),
                        signature,
                        initial_keys[kid],
                    ))
                    self.assertIn(
                        "error_code", vector["expectations"]["initial_jwks"]
                    )
        retired = next(vector for vector in load_vectors() if vector["id"] == "retired-key")
        self.assertIn(retired["header"]["kid"], initial_keys)
        self.assertNotIn(retired["header"]["kid"], rotated_keys)


class MockJwksServerTest(unittest.TestCase):
    def test_dynamic_port_initial_rotation_and_unavailable_states(self) -> None:
        with jwks_server.JwksServer.from_directory(HERE) as first:
            with jwks_server.JwksServer.from_directory(HERE) as second:
                self.assertNotEqual(first.port, second.port)
            self.assertEqual(200, jwks_server.fetch("/state", first.port)[0])
            status, document, headers = jwks_server.fetch("/cdn-cgi/access/certs", first.port)
            self.assertEqual(200, status)
            self.assertEqual(2, len(document["keys"]))
            self.assertIn("max-age=3600", headers["Cache-Control"])
            self.assertTrue(headers["ETag"].startswith('"'))
            first.rotate()
            status, document, _ = jwks_server.fetch("/jwks", first.port)
            self.assertEqual(200, status)
            self.assertEqual(1, len(document["keys"]))
            self.assertEqual("dmctest-key-rotated-2026-11-22", document["keys"][0]["kid"])
            first.set_unavailable(True)
            status, document, headers = jwks_server.fetch("/jwks", first.port)
            self.assertEqual(503, status)
            self.assertEqual("jwks_unavailable", document["error"])
            self.assertEqual("1", headers["Retry-After"])
            first.set_unavailable(False)
            first.reset()
            status, document, _ = jwks_server.fetch("/jwks", first.port)
            self.assertEqual(200, status)
            self.assertEqual(2, len(document["keys"]))
            self.assertEqual(2, first.state()["generation"])
            self.assertEqual(404, jwks_server.fetch("/unknown", first.port)[0])

    def test_control_endpoints_and_etag_revalidation(self) -> None:
        with jwks_server.JwksServer.from_directory(HERE) as server:
            status, state, _ = jwks_server.fetch("/control/requests", server.port)
            self.assertEqual(200, status)
            self.assertEqual(0, state["jwks_requests"])

            status, _, headers = jwks_server.fetch("/cdn-cgi/access/certs", server.port)
            self.assertEqual(200, status)
            status, state, _ = jwks_server.fetch("/control/requests", server.port)
            self.assertEqual(1, state["jwks_requests"])

            connection = http.client.HTTPConnection("127.0.0.1", server.port, timeout=5.0)
            try:
                connection.request(
                    "GET",
                    "/cdn-cgi/access/certs",
                    headers={"If-None-Match": headers["ETag"]},
                )
                response = connection.getresponse()
                response.read()
                self.assertEqual(304, response.status)
                self.assertEqual(headers["ETag"], response.getheader("ETag"))
            finally:
                connection.close()

            status, state, _ = jwks_server.fetch("/control/rotate", server.port)
            self.assertEqual(200, status)
            self.assertEqual("rotated", state["state"])
            status, document, _ = jwks_server.fetch("/jwks", server.port)
            self.assertEqual(1, len(document["keys"]))

            status, state, _ = jwks_server.fetch("/control/unavailable?value=1", server.port)
            self.assertEqual(200, status)
            self.assertTrue(state["unavailable"])
            self.assertEqual(503, jwks_server.fetch("/jwks", server.port)[0])

            status, state, _ = jwks_server.fetch("/control/unavailable?value=0", server.port)
            self.assertFalse(state["unavailable"])
            status, state, _ = jwks_server.fetch("/control/reset", server.port)
            self.assertEqual("initial", state["state"])


if __name__ == "__main__":
    unittest.main()
