package io.github.woxlo1.deploymc.core.diff;

/** Outcome of a diff operation. */
public enum DiffStatus {
  IDENTICAL("identical"),
  CHANGED("changed"),
  TOO_LARGE("too-large"),
  INVALID_INPUT("invalid-input");

  private final String value;

  DiffStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
