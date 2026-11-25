package io.github.woxlo1.deploymc.core.auth;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** One host-provisioned token. Only the secret hash is ever stored. */
public final class TokenRecord {

  private static final Pattern SCOPE =
      Pattern.compile("^(?:\\*|[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9-]*)+)$");
  private static final int MAX_SCOPES = 16;
  private static final int MAX_SCOPE_LENGTH = 128;

  private final String id;
  private final String label;
  private final byte[] secretHash;
  private final Instant expiresAt;
  private final List<String> scopes;
  private final Instant revokedAt;
  private final Instant lastUsedAt;

  public TokenRecord(String id, String label, byte[] secretHash, Instant expiresAt) {
    this(id, label, secretHash, expiresAt, Collections.<String>emptyList(), null, null);
  }

  public TokenRecord(
      String id,
      String label,
      byte[] secretHash,
      Instant expiresAt,
      Collection<String> scopes,
      Instant revokedAt,
      Instant lastUsedAt) {
    this.id = requireText(id, "id");
    this.label = requireText(label, "label");
    Objects.requireNonNull(secretHash, "secretHash");
    if (secretHash.length != 32) {
      throw new IllegalArgumentException("secretHash must be a 32-byte SHA-256 digest");
    }
    this.secretHash = secretHash.clone();
    this.expiresAt = expiresAt;
    this.scopes = copyScopes(scopes);
    this.revokedAt = revokedAt;
    this.lastUsedAt = lastUsedAt;
    if (lastUsedAt != null && expiresAt != null && expiresAt.isBefore(lastUsedAt)) {
      throw new IllegalArgumentException("lastUsedAt cannot follow expiry");
    }
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

  /** Immutable declaration-order copy. Empty means no narrowing; it is not an RBAC grant. */
  public List<String> scopes() {
    return scopes;
  }

  public Instant revokedAt() {
    return revokedAt;
  }

  public Instant lastUsedAt() {
    return lastUsedAt;
  }

  public boolean isRevoked() {
    return revokedAt != null;
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

  /** Returns a record with a monotonic last-used timestamp. */
  public TokenRecord withLastUsedAt(Instant timestamp) {
    Objects.requireNonNull(timestamp, "timestamp");
    if (lastUsedAt != null && !timestamp.isAfter(lastUsedAt)) {
      throw new IllegalArgumentException("lastUsedAt must advance");
    }
    return new TokenRecord(id, label, secretHash, expiresAt, scopes, revokedAt, timestamp);
  }

  /** Returns a record marked revoked. An already revoked record is returned unchanged. */
  public TokenRecord withRevokedAt(Instant timestamp) {
    Objects.requireNonNull(timestamp, "timestamp");
    if (revokedAt != null) {
      return this;
    }
    return new TokenRecord(id, label, secretHash, expiresAt, scopes, timestamp, lastUsedAt);
  }

  /** Whether a string is a valid WP-3.3 scope name or whole-grant wildcard. */
  public static boolean isValidScope(String scope) {
    return scope != null && scope.length() <= MAX_SCOPE_LENGTH && SCOPE.matcher(scope).matches();
  }

  /** Identity exposed to the application. The token value is never part of it. */
  public Identity identity() {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("token.id", id);
    claims.put("token.label", label);
    claims.put("auth.method", "token");
    if (!scopes.isEmpty()) {
      StringBuilder joined = new StringBuilder();
      for (int index = 0; index < scopes.size(); index++) {
        if (index > 0) {
          joined.append(',');
        }
        joined.append(scopes.get(index));
      }
      claims.put("token.scopes", joined.toString());
    }
    return new Identity(id, label, "token", true, Collections.<String>emptyList(), claims);
  }

  private static List<String> copyScopes(Collection<String> values) {
    Objects.requireNonNull(values, "scopes");
    if (values.size() > MAX_SCOPES) {
      throw new IllegalArgumentException("too many scopes");
    }
    Set<String> unique = new LinkedHashSet<String>();
    for (String value : values) {
      if (!isValidScope(value)) {
        throw new IllegalArgumentException("invalid token scope");
      }
      if (!unique.add(value)) {
        throw new IllegalArgumentException("duplicate token scope");
      }
    }
    return Collections.unmodifiableList(new ArrayList<String>(unique));
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }
}
