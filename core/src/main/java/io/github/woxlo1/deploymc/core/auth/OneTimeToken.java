package io.github.woxlo1.deploymc.core.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;

/** The in-memory credential issued by {@code local-onetime} mode. It is never persisted. */
public final class OneTimeToken {

  private static final int SECRET_BYTES = 32;
  private static final String TOKEN_PREFIX = "dmc_";
  private static final String IDENTITY_ID = "local-onetime";
  private static final Duration MIN_LIFETIME = Duration.ofMinutes(1);
  private static final Duration MAX_LIFETIME = Duration.ofMinutes(60);

  private final String secret;
  private final Instant expiresAt;

  private OneTimeToken(String secret, Instant expiresAt) {
    this.secret = secret;
    this.expiresAt = expiresAt;
  }

  /** Creates a 256-bit token whose lifetime is bounded to 1-60 minutes. */
  public static OneTimeToken issue(SecureRandom random, Clock clock, Duration lifetime) {
    Objects.requireNonNull(random, "random");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(lifetime, "lifetime");
    if (lifetime.compareTo(MIN_LIFETIME) < 0 || lifetime.compareTo(MAX_LIFETIME) > 0) {
      throw new IllegalArgumentException("one-time lifetime must be between 1 and 60 minutes");
    }
    byte[] bytes = new byte[SECRET_BYTES];
    random.nextBytes(bytes);
    String secret = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new OneTimeToken(secret, clock.instant().plus(lifetime));
  }

  /** The secret to print once to the host console. Never include it in logs or responses. */
  public String secret() {
    return secret;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public boolean isExpired(Instant now) {
    Objects.requireNonNull(now, "now");
    return !expiresAt.isAfter(now);
  }

  /** The identity has no credential claims and is admin-equivalent only in this local mode. */
  public Identity identity() {
    return new Identity(
        IDENTITY_ID,
        IDENTITY_ID,
        IDENTITY_ID,
        true,
        Collections.<String>emptyList(),
        Collections.<String, String>emptyMap());
  }

  @Override
  public String toString() {
    return "OneTimeToken[expiresAt=" + expiresAt + "]";
  }
}
