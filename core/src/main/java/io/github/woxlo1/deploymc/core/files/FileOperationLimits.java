package io.github.woxlo1.deploymc.core.files;

/** Host-controlled limits. Request data can never raise these bounds. */
public final class FileOperationLimits {

  public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
  public static final long DEFAULT_MAX_UPLOAD_BYTES = 64L * 1024L * 1024L;
  public static final int DEFAULT_MAX_LIST_ENTRIES = 5000;
  public static final int DEFAULT_MAX_DELETE_ENTRIES = 1000;

  private final long maxFileSizeBytes;
  private final long maxUploadBytes;
  private final int maxListEntries;
  private final int maxDeleteEntries;

  public FileOperationLimits(
      long maxFileSizeBytes, long maxUploadBytes, int maxListEntries, int maxDeleteEntries) {
    if (maxFileSizeBytes < 1 || maxUploadBytes < 1 || maxListEntries < 1 || maxDeleteEntries < 1) {
      throw new IllegalArgumentException("File operation limits must be positive");
    }
    this.maxFileSizeBytes = maxFileSizeBytes;
    this.maxUploadBytes = maxUploadBytes;
    this.maxListEntries = maxListEntries;
    this.maxDeleteEntries = maxDeleteEntries;
  }

  public static FileOperationLimits defaults() {
    return new FileOperationLimits(
        DEFAULT_MAX_FILE_SIZE_BYTES,
        DEFAULT_MAX_UPLOAD_BYTES,
        DEFAULT_MAX_LIST_ENTRIES,
        DEFAULT_MAX_DELETE_ENTRIES);
  }

  public long maxFileSizeBytes() {
    return maxFileSizeBytes;
  }

  public long maxUploadBytes() {
    return maxUploadBytes;
  }

  public int maxListEntries() {
    return maxListEntries;
  }

  public int maxDeleteEntries() {
    return maxDeleteEntries;
  }
}
