package io.github.woxlo1.deploymc.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Single-use {@link Authenticator} for the host-controlled local testing token. */
public final class OneTimeTokenAuthenticator implements Authenticator {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final int MIN_TOKEN_LENGTH = 16;
  private static final int MAX_TOKEN_LENGTH = 512;
  private static final int MAX_ATTEMPTS = 20;

  private final OneTimeToken token;
  private final Clock clock;
  private final int maxAttempts;
  private State state = State.ACTIVE;
  private int attempts;

  public OneTimeTokenAuthenticator(OneTimeToken token, Clock clock, int maxAttempts) {
    this.token = Objects.requireNonNull(token, "token");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS) {
      throw new IllegalArgumentException("maxAttempts must be between 1 and 20");
    }
    this.maxAttempts = maxAttempts;
  }

  @Override
  public synchronized AuthenticationResult authenticate(AuthenticationRequest request) {
    if (request == null) {
      return AuthenticationResult.failed(AuthenticationFailure.REQUIRED);
    }
    String authorization = request.header(AUTHORIZATION);
    if (authorization == null || authorization.trim().isEmpty()) {
      return AuthenticationResult.failed(AuthenticationFailure.REQUIRED);
    }
    if (state == State.CONSUMED || state == State.BURNED) {
      return AuthenticationResult.failed(AuthenticationFailure.ONE_TIME_CONSUMED);
    }
    Instant now = clock.instant();
    if (state == State.EXPIRED || token.isExpired(now)) {
      state = State.EXPIRED;
      return AuthenticationResult.failed(AuthenticationFailure.ONE_TIME_EXPIRED);
    }
    if (!authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
      return consumeAttempt();
    }
    String candidate = authorization.substring(BEARER.length()).trim();
    if (!isPlausibleToken(candidate)) {
      return consumeAttempt();
    }
    if (!matches(candidate)) {
      return consumeAttempt();
    }
    state = State.CONSUMED;
    return AuthenticationResult.authenticated(token.identity());
  }

  private AuthenticationResult consumeAttempt() {
    attempts++;
    if (attempts >= maxAttempts) {
      state = State.BURNED;
      return AuthenticationResult.failed(AuthenticationFailure.ONE_TIME_CONSUMED);
    }
    return AuthenticationResult.failed(AuthenticationFailure.INVALID);
  }

  private boolean matches(String candidate) {
    byte[] expected = token.secret().getBytes(StandardCharsets.UTF_8);
    byte[] supplied = candidate.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, supplied);
  }

  private static boolean isPlausibleToken(String value) {
    if (value.length() < MIN_TOKEN_LENGTH || value.length() > MAX_TOKEN_LENGTH) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character <= 0x20 || character >= 0x7f || character == '"' || character == '\\') {
        return false;
      }
    }
    return true;
  }

  private enum State {
    ACTIVE,
    CONSUMED,
    EXPIRED,
    BURNED
  }
}
