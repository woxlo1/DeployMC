package io.github.woxlo1.deploymc.core.snapshot;

/** Metadata kinds defined by the snapshot store format. */
public enum SnapshotKind {
  FILE("file"),
  JAR("jar"),
  DELETED("deleted"),
  DIRECTORY("directory");

  private final String value;

  SnapshotKind(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean hasContent() {
    return this == FILE || this == JAR;
  }

  public static SnapshotKind fromValue(String value) {
    for (SnapshotKind kind : values()) {
      if (kind.value.equals(value)) {
        return kind;
      }
    }
    return null;
  }
}
