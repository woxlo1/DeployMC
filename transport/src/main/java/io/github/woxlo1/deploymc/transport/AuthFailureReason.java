package io.github.woxlo1.deploymc.transport;

/** Closed reasons recorded for authentication failures. */
public enum AuthFailureReason {
  REQUIRED("required"),
  INVALID("invalid"),
  EXPIRED("expired");

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
      case EXPIRED:
        return EXPIRED;
      case REQUIRED:
      default:
        return REQUIRED;
    }
  }
}
