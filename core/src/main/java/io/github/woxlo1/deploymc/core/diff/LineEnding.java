package io.github.woxlo1.deploymc.core.diff;

/** Preserved line terminator for a diff line. */
public enum LineEnding {
  LF("LF"),
  CRLF("CRLF"),
  CR("CR"),
  EOF("EOF");

  private final String value;

  LineEnding(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
