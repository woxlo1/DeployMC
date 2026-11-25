package io.github.woxlo1.deploymc.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/** Creates host-provisioned tokens and persists only their SHA-256 digests. */
public final class TokenProvisioner {

  private static final int SECRET_BYTES = 32;
  private static final int ID_BYTES = 6;
  private static final int MAX_LABEL_LENGTH = 80;
  private static final String TOKEN_PREFIX = "dmc_";

  private final TokenStore store;
  private final Clock clock;
  private final SecureRandom random;

  public TokenProvisioner(TokenStore store, Clock clock, SecureRandom random) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.random = Objects.requireNonNull(random, "random");
  }

  /** Creates a token with the requested lifetime. A null or zero lifetime means no expiry. */
  public CreatedToken create(String label, Duration lifetime) throws TokenStoreException {
    return create(label, lifetime, Collections.<String>emptyList());
  }

  /**
   * Creates a token with the requested lifetime and stored scopes. Scope validation is structural;
   * enforcement belongs to the later RBAC engine.
   */
  public CreatedToken create(String label, Duration lifetime, Collection<String> scopes)
      throws TokenStoreException {
    String safeLabel = cleanLabel(label);
    byte[] secret = new byte[SECRET_BYTES];
    random.nextBytes(secret);
    byte[] idBytes = new byte[ID_BYTES];
    random.nextBytes(idBytes);
    String id = "t-" + hex(idBytes);
    String token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    Instant expiresAt = null;
    if (lifetime != null && !lifetime.isZero() && !lifetime.isNegative()) {
      expiresAt = clock.instant().plus(lifetime);
    }
    TokenRecord record =
        new TokenRecord(id, safeLabel, sha256(token), expiresAt, scopes, null, null);
    store.write(record);
    return new CreatedToken(record, token);
  }

  private static String cleanLabel(String label) {
    if (label == null) {
      throw new IllegalArgumentException("label");
    }
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < label.length() && result.length() < MAX_LABEL_LENGTH; index++) {
      char character = label.charAt(index);
      if (character >= 0x20 && character != 0x7f) {
        result.append(character);
      }
    }
    return result.toString();
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(Character.forDigit((value >> 4) & 0xf, 16));
      result.append(Character.forDigit(value & 0xf, 16));
    }
    return result.toString();
  }
}
