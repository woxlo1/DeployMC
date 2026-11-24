package io.github.woxlo1.deploymc.core.auth;

import java.util.Objects;

/**
 * Fail-closed request-level authentication filter.
 *
 * <p>This filter is the only supported path from a raw request to an authenticated identity. A
 * missing authenticator, malformed request, null result, or exception from an authenticator becomes
 * a public authentication failure. Exception messages and raw credential material are never exposed
 * to callers, logs, or HTTP responses.
 */
public final class AuthenticationFilter {

  private final Authenticator authenticator;

  /** A {@code null} authenticator is accepted so that the filter itself can fail closed. */
  public AuthenticationFilter(Authenticator authenticator) {
    this.authenticator = authenticator;
  }

  public AuthenticationResult filter(AuthenticationRequest request) {
    if (request == null || authenticator == null) {
      return AuthenticationResult.failed(AuthenticationFailure.REQUIRED);
    }
    try {
      AuthenticationResult result = authenticator.authenticate(request);
      if (result == null || (!result.authenticated() && result.failure() == null)) {
        return AuthenticationResult.failed(AuthenticationFailure.INVALID);
      }
      return result;
    } catch (RuntimeException exception) {
      return AuthenticationResult.failed(AuthenticationFailure.INVALID);
    } catch (Error error) {
      return AuthenticationResult.failed(AuthenticationFailure.INVALID);
    }
  }

  /** Returns the configured authenticator, or {@code null} when the gate is unconfigured. */
  public Authenticator authenticator() {
    return authenticator;
  }

  /** Creates the explicit context for a successful result. Fails closed for a failed result. */
  public AuthContext context(String requestId, AuthenticationResult result) {
    Objects.requireNonNull(result, "result");
    if (!result.authenticated()) {
      return AuthContext.anonymous(requestId);
    }
    return AuthContext.authenticated(requestId, result.identity());
  }
}
