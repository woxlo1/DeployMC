package io.github.woxlo1.deploymc.core.diff;

/** Input interpretation requested for a diff operation. */
public enum DiffMode {
  AUTO("auto"),
  TEXT("text"),
  YAML("yaml"),
  JSON("json"),
  JAR("jar");

  private final String value;

  DiffMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
