#!/usr/bin/env python3
"""Generate deterministic JWT test vectors and public JWKS documents.

All private keys are regenerated in memory from fixed throwaway seeds and are never
written to disk. The committed output contains only public keys, signed tokens,
expectations, and SHA-256 digests. Generated files use LF bytes on every host.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import math
import random
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
CORPUS_VERSION = "2026-11-22.1"
REFERENCE_UTC = "2026-11-22T00:00:00Z"
ISSUER = "https://team.example.test"
AUDIENCE = "https://deploy.example.test"
WRONG_ISSUER = "https://other-team.example.test"
WRONG_AUDIENCE = "https://other.example.test"
PRIME_BITS = 1024
PUBLIC_EXPONENT = 65537
SHA256_DIGEST_INFO_PREFIX = bytes.fromhex("3031300d060960864801650304020105000420")
SMALL_PRIMES = (
    2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71,
    73, 79, 83, 89, 97,
)

KEY_SEEDS = {
    "primary": 0x4A5754D1,
    "rotated": 0x4A5754D2,
    "unknown": 0x4A5754D3,
}
KEY_IDS = {
    "primary": "dmctest-key-primary-2026-11-22",
    "rotated": "dmctest-key-rotated-2026-11-22",
    "unknown": "dmctest-key-unknown-2026-11-22",
}
REFERENCE_EPOCH = int(
    datetime.fromisoformat(REFERENCE_UTC.replace("Z", "+00:00")).timestamp()
)


@dataclass(frozen=True)
class RsaKey:
    """Throwaway RSA key kept in memory by the generator."""

    name: str
    kid: str
    seed: int
    n: int
    d: int

    @property
    def byte_length(self) -> int:
        return (self.n.bit_length() + 7) // 8

    def public_jwk(self) -> dict[str, str]:
        return {
            "alg": "RS256",
            "e": b64url(PUBLIC_EXPONENT.to_bytes(3, "big")),
            "kid": self.kid,
            "kty": "RSA",
            "n": b64url(self.n.to_bytes(self.byte_length, "big")),
            "use": "sig",
        }


def is_probable_prime(candidate: int) -> bool:
    """Miller-Rabin with fixed bases; deterministic across Python versions."""
    if candidate < 2:
        return False
    for prime in SMALL_PRIMES:
        if candidate % prime == 0:
            return candidate == prime
    odd = candidate - 1
    shifts = 0
    while odd % 2 == 0:
        odd //= 2
        shifts += 1
    for base in SMALL_PRIMES:
        value = pow(base, odd, candidate)
        if value in (1, candidate - 1):
            continue
        for _ in range(shifts - 1):
            value = value * value % candidate
            if value == candidate - 1:
                break
        else:
            return False
    return True


def generate_prime(seed: int) -> int:
    rng = random.Random(seed)
    candidate = rng.getrandbits(PRIME_BITS) | (1 << (PRIME_BITS - 1))
    candidate |= 1 << (PRIME_BITS - 2) | 1
    while not is_probable_prime(candidate):
        candidate += 2
    return candidate


def generate_key(name: str) -> RsaKey:
    seed = KEY_SEEDS[name]
    p = generate_prime(seed)
    q = generate_prime(seed + 1)
    attempt = 2
    totient = (p - 1) * (q - 1)
    while p == q or math.gcd(PUBLIC_EXPONENT, totient) != 1:
        q = generate_prime(seed + attempt)
        attempt += 1
        totient = (p - 1) * (q - 1)
    return RsaKey(
        name=name,
        kid=KEY_IDS[name],
        seed=seed,
        n=p * q,
        d=pow(PUBLIC_EXPONENT, -1, totient),
    )


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode(
        "utf-8"
    )


def segment(value: Any) -> str:
    return b64url(canonical_json(value))


def sign_rs256(message: bytes, key: RsaKey) -> bytes:
    digest = hashlib.sha256(message).digest()
    encoded = SHA256_DIGEST_INFO_PREFIX + digest
    padding = key.byte_length - len(encoded) - 3
    if padding < 8:
        raise ValueError("throwaway key is too small for RS256")
    block = b"\x00\x01" + b"\xff" * padding + b"\x00" + encoded
    signature = pow(int.from_bytes(block, "big"), key.d, key.n)
    return signature.to_bytes(key.byte_length, "big")


def verify_rs256(message: bytes, signature: bytes, jwk: dict[str, str]) -> bool:
    """Local verification helper used by the generator tests."""
    modulus = int.from_bytes(base64.urlsafe_b64decode(jwk["n"] + "=="), "big")
    exponent = int.from_bytes(base64.urlsafe_b64decode(jwk["e"] + "=="), "big")
    if len(signature) != (modulus.bit_length() + 7) // 8:
        return False
    encoded = SHA256_DIGEST_INFO_PREFIX + hashlib.sha256(message).digest()
    expected = b"\x00\x01" + b"\xff" * (len(signature) - len(encoded) - 3) + b"\x00" + encoded
    restored = pow(int.from_bytes(signature, "big"), exponent, modulus)
    return restored.to_bytes(len(signature), "big") == expected


def header(kid: str, algorithm: str = "RS256") -> dict[str, str]:
    return {"alg": algorithm, "kid": kid, "typ": "JWT"}


def claims(jti: str, **overrides: Any) -> dict[str, Any]:
    result: dict[str, Any] = {
        "aud": AUDIENCE,
        "common_name": "DeployMC Fixture User",
        "email": "fixture@example.test",
        "exp": REFERENCE_EPOCH + 7200,
        "iat": REFERENCE_EPOCH - 60,
        "iss": ISSUER,
        "jti": jti,
        "nbf": REFERENCE_EPOCH - 60,
        "sub": "dmctest-user",
    }
    result.update(overrides)
    return result


def signed_token(key: RsaKey, token_header: dict[str, str], token_claims: dict[str, Any]) -> str:
    signing_input = "{0}.{1}".format(segment(token_header), segment(token_claims)).encode("ascii")
    return signing_input.decode("ascii") + "." + b64url(sign_rs256(signing_input, key))


def expectation(outcome: str, error_code: str | None = None) -> dict[str, str]:
    result = {"outcome": outcome}
    if error_code is not None:
        result["error_code"] = error_code
    return result


def build_vectors(keys: dict[str, RsaKey]) -> list[dict[str, Any]]:
    primary, rotated, unknown = keys["primary"], keys["rotated"], keys["unknown"]
    vectors: list[dict[str, Any]] = []

    def add(
        identifier: str,
        category: str,
        description: str,
        key_name: str,
        token_header: dict[str, str],
        token_claims: dict[str, Any],
        initial: dict[str, str],
        rotated_state: dict[str, str],
        token: str | None = None,
    ) -> None:
        vectors.append(
            {
                "category": category,
                "claims": token_claims,
                "description": description,
                "expectations": {"initial_jwks": initial, "rotated_jwks": rotated_state},
                "header": token_header,
                "id": identifier,
                "key": key_name,
                "token": token or signed_token(keys[key_name], token_header, token_claims),
            }
        )

    accept = expectation("accept")
    invalid = expectation("reject", "auth.invalid")
    add(
        "valid",
        "valid",
        "Correct signature, issuer, audience, and time window.",
        "primary",
        header(primary.kid),
        claims("dmctest-valid"),
        accept,
        accept,
    )
    add(
        "expired",
        "time",
        "Correct signature with exp before the fixed reference time.",
        "primary",
        header(primary.kid),
        claims("dmctest-expired", exp=REFERENCE_EPOCH - 3600),
        expectation("reject", "auth.expired"),
        expectation("reject", "auth.expired"),
    )
    add(
        "nbf-in-future",
        "time",
        "Correct signature with nbf after the fixed reference time.",
        "primary",
        header(primary.kid),
        claims("dmctest-nbf-future", nbf=REFERENCE_EPOCH + 3600),
        expectation("reject", "auth.not-yet-valid"),
        expectation("reject", "auth.not-yet-valid"),
    )
    add(
        "wrong-aud",
        "claims",
        "Correct signature with an unexpected audience.",
        "primary",
        header(primary.kid),
        claims("dmctest-wrong-aud", aud=WRONG_AUDIENCE),
        invalid,
        invalid,
    )
    add(
        "wrong-iss",
        "claims",
        "Correct signature with an unexpected issuer.",
        "primary",
        header(primary.kid),
        claims("dmctest-wrong-iss", iss=WRONG_ISSUER),
        invalid,
        invalid,
    )
    none_header = header(primary.kid, "none")
    none_claims = claims("dmctest-alg-none")
    none_token = "{0}.{1}.".format(segment(none_header), segment(none_claims))
    add(
        "alg-none",
        "algorithm",
        "Unsigned token with alg none and an empty signature segment.",
        "primary",
        none_header,
        none_claims,
        invalid,
        invalid,
        token=none_token,
    )
    confusion_header = header(primary.kid, "HS256")
    confusion_claims = claims("dmctest-alg-confusion")
    confusion_input = "{0}.{1}".format(
        segment(confusion_header), segment(confusion_claims)
    ).encode("ascii")
    confusion_signature = hmac.new(
        str(primary.n).encode("ascii"), confusion_input, hashlib.sha256
    ).digest()
    add(
        "alg-confusion",
        "algorithm",
        "HS256-style token signed with the public RSA modulus as a shared secret.",
        "primary",
        confusion_header,
        confusion_claims,
        invalid,
        invalid,
        token=confusion_input.decode("ascii") + "." + b64url(confusion_signature),
    )
    tampered_claims = claims("dmctest-tampered", email="tampered@example.test")
    valid_parts = signed_token(primary, header(primary.kid), claims("dmctest-tampered")).split(".")
    add(
        "tampered",
        "signature",
        "Valid RS256 signature reused with modified claim bytes.",
        "primary",
        header(primary.kid),
        tampered_claims,
        invalid,
        invalid,
        token="{0}.{1}.{2}".format(valid_parts[0], segment(tampered_claims), valid_parts[2]),
    )
    add(
        "unknown-kid",
        "key",
        "Correct RS256 signature from a throwaway key absent from both JWKS documents.",
        "unknown",
        header(unknown.kid),
        claims("dmctest-unknown-kid"),
        invalid,
        invalid,
    )
    add(
        "rotated-key",
        "key",
        "Correct RS256 signature from the replacement key retained after rotation.",
        "rotated",
        header(rotated.kid),
        claims("dmctest-rotated-key"),
        accept,
        accept,
    )
    add(
        "retired-key",
        "key",
        "Correct signature from the key that is removed when the JWKS rotates.",
        "primary",
        header(primary.kid),
        claims("dmctest-retired-key"),
        accept,
        invalid,
    )
    return vectors


def write_text(path: Path, text: str) -> None:
    with path.open("w", encoding="ascii", newline="\n") as stream:
        stream.write(text)


def jwks_text(keys: list[RsaKey]) -> str:
    document = {"keys": [key.public_jwk() for key in keys]}
    return json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def vector_text(vectors: list[dict[str, Any]]) -> str:
    return "".join(json.dumps(vector, sort_keys=True, separators=(",", ":")) + "\n"
                   for vector in vectors)


def outcome_text(expectation_value: dict[str, str]) -> str:
    return expectation_value.get("error_code") or expectation_value["outcome"]


def manifest_text(
    target: Path, vectors: list[dict[str, Any]], initial: list[RsaKey], rotated: list[RsaKey]
) -> str:
    sources = (
        ("vectors", "jwt-vectors.jsonl", len(vectors)),
        ("jwks.initial", "jwks-initial.json", len(initial)),
        ("jwks.rotated", "jwks-rotated.json", len(rotated)),
    )
    lines = [
        "# DeployMC JWT vector manifest. Generated by generate_jwt_vectors.py; do not edit.",
        "# All keys are throwaway, fixed-seed, and generated in memory. No private key is exported.",
        "schema.version={0}".format(SCHEMA_VERSION),
        "corpus.version={0}".format(CORPUS_VERSION),
        "generator=tests/security/jwt/generate_jwt_vectors.py",
        "reference.utc={0}".format(REFERENCE_UTC),
        "issuer={0}".format(ISSUER),
        "audience={0}".format(AUDIENCE),
        "key.size.bits={0}".format(PRIME_BITS * 2),
        "key.private.exported=false",
    ]
    for source_id, file_name, count in sources:
        digest = hashlib.sha256((target / file_name).read_bytes()).hexdigest()
        lines.extend(
            [
                "source.{0}.file={1}".format(source_id, file_name),
                "source.{0}.entries={1}".format(source_id, count),
                "source.{0}.sha256={1}".format(source_id, digest),
            ]
        )
    lines.append("vector.list={0}".format(",".join(vector["id"] for vector in vectors)))
    for vector in vectors:
        lines.append("vector.{0}.category={1}".format(vector["id"], vector["category"]))
        lines.append(
            "vector.{0}.initial={1}".format(
                vector["id"], outcome_text(vector["expectations"]["initial_jwks"])
            )
        )
        lines.append(
            "vector.{0}.rotated={1}".format(
                vector["id"], outcome_text(vector["expectations"]["rotated_jwks"])
            )
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
    target.mkdir(parents=True, exist_ok=True)
    keys = {name: generate_key(name) for name in ("primary", "rotated", "unknown")}
    vectors = build_vectors(keys)
    write_text(target / "jwt-vectors.jsonl", vector_text(vectors))
    write_text(target / "jwks-initial.json", jwks_text([keys["primary"], keys["rotated"]]))
    write_text(target / "jwks-rotated.json", jwks_text([keys["rotated"]]))
    write_text(
        target / "jwt-corpus-manifest.properties",
        manifest_text(target, vectors, [keys["primary"], keys["rotated"]], [keys["rotated"]]),
    )
    print("vectors={0} keys=3 schema={1} corpus={2}".format(
        len(vectors), SCHEMA_VERSION, CORPUS_VERSION
    ))


if __name__ == "__main__":
    main()
