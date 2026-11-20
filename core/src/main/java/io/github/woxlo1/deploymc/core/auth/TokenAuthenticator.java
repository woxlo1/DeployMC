package io.github.woxlo1.deploymc.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Objects;

/** First {@link Authenticator} implementation: host-provisioned bearer tokens. */
public final class TokenAuthenticator implements Authenticator {

  private static final String BEARER = "Bearer ";
  private static final int MIN_TOKEN_LENGTH = 16;
  private static final int MAX_TOKEN_LENGTH = 512;

  private final TokenStore store;
  private final Clock clock;

  public TokenAuthenticator(TokenStore store, Clock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public AuthenticationResult authenticate(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
      return AuthenticationResult.failed(AuthenticationFailure.REQUIRED);
    }
    if (!authorizationHeader.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
      return AuthenticationResult.failed(AuthenticationFailure.INVALID);
    }
    String token = authorizationHeader.substring(BEARER.length()).trim();
    if (!isPlausibleToken(token)) {
      return AuthenticationResult.failed(AuthenticationFailure.INVALID);
    }

    byte[] candidateHash = sha256(token);
    Identity match = null;
    boolean expiredMatch = false;
    for (TokenRecord record : store.records()) {
      if (!record.matches(candidateHash)) {
        continue;
      }
      if (record.isExpired(clock.instant())) {
        expiredMatch = true;
      } else if (match == null) {
        match = record.identity();
      }
    }
    if (match != null) {
      return AuthenticationResult.authenticated(match);
    }
    return AuthenticationResult.failed(
        expiredMatch ? AuthenticationFailure.EXPIRED : AuthenticationFailure.INVALID);
  }

  private static boolean isPlausibleToken(String token) {
    if (token.length() < MIN_TOKEN_LENGTH || token.length() > MAX_TOKEN_LENGTH) {
      return false;
    }
    for (int index = 0; index < token.length(); index++) {
      char character = token.charAt(index);
      if (character <= 0x20
          || character >= 0x7f
          || character == '"'
          || character == '\\'
          || character == ',') {
        return false;
      }
    }
    return true;
  }

  private static byte[] sha256(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }
}
