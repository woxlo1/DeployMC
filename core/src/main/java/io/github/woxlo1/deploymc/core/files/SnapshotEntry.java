package io.github.woxlo1.deploymc.core.files;

/** One entry captured before a destructive file operation. */
public final class SnapshotEntry {

  private final String relativePath;
  private final byte[] content;
  private final String revision;
  private final boolean directory;

  private SnapshotEntry(String relativePath, byte[] content, String revision, boolean directory) {
    this.relativePath = relativePath;
    this.content = content == null ? new byte[0] : content.clone();
    this.revision = revision;
    this.directory = directory;
  }

  public static SnapshotEntry file(String relativePath, byte[] content, String revision) {
    return new SnapshotEntry(relativePath, content, revision, false);
  }

  public static SnapshotEntry directory(String relativePath) {
    return new SnapshotEntry(relativePath, null, null, true);
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
}
