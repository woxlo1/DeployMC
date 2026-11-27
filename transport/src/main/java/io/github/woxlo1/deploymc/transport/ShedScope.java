package io.github.woxlo1.deploymc.transport;

/** Closed scopes counted when the transport sheds load. */
public enum ShedScope {
  ADDRESS("address"),
  IDENTITY("identity"),
  AUTH_FAILURE("auth-failure");

  private final String wireLabel;

  ShedScope(String wireLabel) {
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
}
