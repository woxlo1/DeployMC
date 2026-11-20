package io.github.woxlo1.deploymc.core.files;

/** Explicit deletion intent. Recursive deletion requires the caller to confirm an entry count. */
public final class DeleteRequest {

  private final boolean recursive;
  private final Long expectedEntryCount;
  private final String expectedRevision;

  private DeleteRequest(boolean recursive, Long expectedEntryCount, String expectedRevision) {
    this.recursive = recursive;
    this.expectedEntryCount = expectedEntryCount;
    this.expectedRevision = expectedRevision;
  }

  /** Deletes one file or an empty directory. */
  public static DeleteRequest single(String expectedRevision) {
    return new DeleteRequest(false, null, expectedRevision);
  }

  /** Deletes a tree only when the current entry count equals the caller-supplied count. */
  public static DeleteRequest recursive(long expectedEntryCount, String expectedRevision) {
    if (expectedEntryCount < 1) {
      throw new IllegalArgumentException("Confirmed entry count must be positive");
    }
    return new DeleteRequest(true, Long.valueOf(expectedEntryCount), expectedRevision);
  }

  public boolean recursive() {
    return recursive;
  }

  public Long expectedEntryCount() {
    return expectedEntryCount;
  }

  public String expectedRevision() {
    return expectedRevision;
  }
}
