package io.github.woxlo1.deploymc.core.auth;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

/** One host-provisioned token. Only the secret hash is ever stored. */
public final class TokenRecord {

  private final String id;
  private final String label;
  private final byte[] secretHash;
  private final Instant expiresAt;

  public TokenRecord(String id, String label, byte[] secretHash, Instant expiresAt) {
    this.id = requireText(id, "id");
    this.label = requireText(label, "label");
    Objects.requireNonNull(secretHash, "secretHash");
    if (secretHash.length != 32) {
      throw new IllegalArgumentException("secretHash must be a 32-byte SHA-256 digest");
    }
    this.secretHash = secretHash.clone();
    this.expiresAt = expiresAt;
  }

  public String id() {
    return id;
  }

  public String label() {
    return label;
  }

  /** Returns a copy of the digest. */
  public byte[] secretHash() {
    return secretHash.clone();
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  /** Constant-time comparison of two SHA-256 digests. */
  public boolean matches(byte[] candidateHash) {
    return candidateHash != null
        && candidateHash.length == secretHash.length
        && MessageDigest.isEqual(secretHash, candidateHash);
  }

  /** A token is expired at its expiry instant, not one second later. */
  public boolean isExpired(Instant now) {
    Objects.requireNonNull(now, "now");
    return expiresAt != null && !expiresAt.isAfter(now);
  }

  /** Identity exposed to the application. The token value is never part of it. */
  public Identity identity() {
    java.util.Map<String, String> claims = new java.util.LinkedHashMap<String, String>();
    claims.put("token.id", id);
    claims.put("token.label", label);
    claims.put("auth.method", "token");
    return new Identity(
        id, label, "token", true, java.util.Collections.<String>emptyList(), claims);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }
}
