package io.github.woxlo1.deploymc.core.auth;

/** Stable, public failure reasons for authentication. No reason reveals an identity. */
public enum AuthenticationFailure {
  REQUIRED("auth.required"),
  INVALID("auth.invalid"),
  EXPIRED("auth.expired");

  private final String code;

  AuthenticationFailure(String code) {
    this.code = code;
  }

  /** RFC 9457 error code from the API error catalog. */
  public String code() {
    return code;
  }
}
