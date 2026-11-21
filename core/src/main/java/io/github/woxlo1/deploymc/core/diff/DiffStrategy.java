package io.github.woxlo1.deploymc.core.diff;

/** Algorithm that produced a diff result. */
public enum DiffStrategy {
  NONE("none"),
  LINE("line"),
  SEMANTIC("semantic"),
  TEXT_FALLBACK("text-fallback"),
  JAR_METADATA("jar-metadata");

  private final String value;

  DiffStrategy(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
