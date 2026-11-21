package io.github.woxlo1.deploymc.core.snapshot;

/** Counts from one retention or manual prune run. */
public final class RetentionReport {

  private final int considered;
  private final int protectedRecords;
  private final int pruned;
  private final int failed;
  private final long storeBytesBefore;
  private final long storeBytesAfter;

  public RetentionReport(
      int considered,
      int protectedRecords,
      int pruned,
      int failed,
      long storeBytesBefore,
      long storeBytesAfter) {
    this.considered = considered;
    this.protectedRecords = protectedRecords;
    this.pruned = pruned;
    this.failed = failed;
    this.storeBytesBefore = storeBytesBefore;
    this.storeBytesAfter = storeBytesAfter;
  }

  public int considered() {
    return considered;
  }

  public int protectedRecords() {
    return protectedRecords;
  }

  public int pruned() {
    return pruned;
  }

  public int failed() {
    return failed;
  }

  public long storeBytesBefore() {
    return storeBytesBefore;
  }

  public long storeBytesAfter() {
    return storeBytesAfter;
  }
}
