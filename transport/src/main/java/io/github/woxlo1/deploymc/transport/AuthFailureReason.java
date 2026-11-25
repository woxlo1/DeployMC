package io.github.woxlo1.deploymc.transport;

/** Closed reasons recorded for authentication failures. */
public enum AuthFailureReason {
  REQUIRED("required"),
  INVALID("invalid"),
  REVOKED("revoked"),
  EXPIRED("expired"),
  NOT_YET_VALID("not-yet-valid"),
  JWKS_UNAVAILABLE("jwks-unavailable"),
  ONE_TIME_CONSUMED("one-time-consumed"),
  ONE_TIME_EXPIRED("one-time-expired"),
  INTERNAL_ERROR("internal-error");

  private final String wireLabel;

  AuthFailureReason(String wireLabel) {
    this.wireLabel = wireLabel;
  }

  /** Stable Prometheus label. */
  public String wireLabel() {
    return wireLabel;
  }

  /** Alias for {@link #wireLabel()}. */
  public String label() {
    return wireLabel;
  }

  /** Maps the core authentication failure type to the closed metric reason set. */
  public static AuthFailureReason from(
      io.github.woxlo1.deploymc.core.auth.AuthenticationFailure failure) {
    if (failure == null) {
      return REQUIRED;
    }
    switch (failure) {
      case INVALID:
        return INVALID;
      case REVOKED:
        return REVOKED;
      case EXPIRED:
        return EXPIRED;
      case NOT_YET_VALID:
        return NOT_YET_VALID;
      case JWKS_UNAVAILABLE:
        return JWKS_UNAVAILABLE;
      case ONE_TIME_CONSUMED:
        return ONE_TIME_CONSUMED;
      case ONE_TIME_EXPIRED:
        return ONE_TIME_EXPIRED;
      case INTERNAL_ERROR:
        return INTERNAL_ERROR;
      case REQUIRED:
      default:
        return REQUIRED;
    }
  }
}
