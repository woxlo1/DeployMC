package io.github.woxlo1.deploymc.core.files;

/** Bytes returned by a read or download, plus the full-file revision used for ETag handling. */
public final class FileContent {

  private final String relativePath;
  private final byte[] content;
  private final String revision;
  private final long totalSize;
  private final long rangeStart;

  public FileContent(
      String relativePath, byte[] content, String revision, long totalSize, long rangeStart) {
    if (content == null) {
      throw new IllegalArgumentException("content");
    }
    this.relativePath = relativePath;
    this.content = content.clone();
    this.revision = revision;
    this.totalSize = totalSize;
    this.rangeStart = rangeStart;
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

  public long totalSize() {
    return totalSize;
  }

  public long rangeStart() {
    return rangeStart;
  }
}
