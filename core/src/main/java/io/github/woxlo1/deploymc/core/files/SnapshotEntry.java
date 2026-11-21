package io.github.woxlo1.deploymc.core.files;

/** One entry captured before a destructive file operation. */
public final class SnapshotEntry {

  private final String relativePath;
  private final byte[] content;
  private final String revision;
  private final boolean directory;
  private final boolean deleted;

  private SnapshotEntry(
      String relativePath, byte[] content, String revision, boolean directory, boolean deleted) {
    this.relativePath = relativePath;
    this.content = content == null ? new byte[0] : content.clone();
    this.revision = revision;
    this.directory = directory;
    this.deleted = deleted;
  }

  public static SnapshotEntry file(String relativePath, byte[] content, String revision) {
    return new SnapshotEntry(relativePath, content, revision, false, false);
  }

  public static SnapshotEntry directory(String relativePath) {
    return new SnapshotEntry(relativePath, null, null, true, false);
  }

  /** Marker for an absent target, used to keep a restore reversible. */
  public static SnapshotEntry deleted(String relativePath) {
    return new SnapshotEntry(relativePath, null, null, false, true);
  }

  public String relativePath() {
    return relativePath;
  }

  public byte[] content() {
    return content.clone();
  }

  public String revision() {
    return revision;
  }

  public boolean directory() {
    return directory;
  }

  public boolean deleted() {
    return deleted;
  }
}
