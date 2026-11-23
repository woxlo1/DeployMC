package io.github.woxlo1.deploymc.transport;

/** Diagnostic logging formats supported by the transport. */
public enum LoggingFormat {
  TEXT("text"),
  JSON("json");

  private final String wireLabel;

  LoggingFormat(String wireLabel) {
    this.wireLabel = wireLabel;
  }

  /** Stable configuration label. */
  public String wireLabel() {
    return wireLabel;
  }

  /** Alias for {@link #wireLabel()}. */
  public String label() {
    return wireLabel;
  }
}
