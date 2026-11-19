package io.github.woxlo1.deploymc.core.auth;

import java.util.Objects;

/** Immutable result of one authentication attempt. */
public final class AuthenticationResult {

  private final Identity identity;
  private final AuthenticationFailure failure;

  private AuthenticationResult(Identity identity, AuthenticationFailure failure) {
    this.identity = identity;
    this.failure = failure;
  }

  public static AuthenticationResult authenticated(Identity identity) {
    return new AuthenticationResult(Objects.requireNonNull(identity, "identity"), null);
  }

  public static AuthenticationResult failed(AuthenticationFailure failure) {
    return new AuthenticationResult(null, Objects.requireNonNull(failure, "failure"));
  }

  public boolean authenticated() {
    return identity != null;
  }

  public Identity identity() {
    return identity;
  }

  public AuthenticationFailure failure() {
    return failure;
  }

  /** Stable error code for a failed attempt, or {@code null} on success. */
  public String code() {
    return failure == null ? null : failure.code();
  }
}
