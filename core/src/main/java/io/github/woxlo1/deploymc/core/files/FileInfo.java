package io.github.woxlo1.deploymc.core.files;

/** Metadata for a sandbox-relative entry. Absolute host paths are never exposed. */
public final class FileInfo {

  private final String relativePath;
  private final long size;
  private final String revision;
  private final long lastModifiedMillis;
  private final boolean directory;

  private FileInfo(
      String relativePath, long size, String revision, long lastModifiedMillis, boolean directory) {
    this.relativePath = relativePath;
    this.size = size;
    this.revision = revision;
    this.lastModifiedMillis = lastModifiedMillis;
    this.directory = directory;
  }

  public static FileInfo file(
      String relativePath, long size, String revision, long lastModifiedMillis) {
    return new FileInfo(relativePath, size, revision, lastModifiedMillis, false);
  }

  public static FileInfo directory(String relativePath, long lastModifiedMillis) {
    return new FileInfo(relativePath, 0L, null, lastModifiedMillis, true);
  }

  public String relativePath() {
    return relativePath;
  }

  public long size() {
    return size;
  }

  /** Strong content revision for files; {@code null} for directories. */
  public String revision() {
    return revision;
  }

  public long lastModifiedMillis() {
    return lastModifiedMillis;
  }

  public boolean directory() {
    return directory;
  }
}
