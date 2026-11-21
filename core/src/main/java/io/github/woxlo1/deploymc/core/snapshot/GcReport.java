package io.github.woxlo1.deploymc.core.snapshot;

/** Outcome of one conservative garbage-collection pass. */
public final class GcReport {

  private final boolean aborted;
  private final VerifyReport verify;
  private final RetentionReport retention;
  private final int blobsConsidered;
  private final int blobsDeleted;
  private final long bytesReclaimed;

  public GcReport(
      boolean aborted,
      VerifyReport verify,
      RetentionReport retention,
      int blobsConsidered,
      int blobsDeleted,
      long bytesReclaimed) {
    this.aborted = aborted;
    this.verify = verify;
    this.retention = retention;
    this.blobsConsidered = blobsConsidered;
    this.blobsDeleted = blobsDeleted;
    this.bytesReclaimed = bytesReclaimed;
  }

  public boolean aborted() {
    return aborted;
  }

  public VerifyReport verify() {
    return verify;
  }

  public RetentionReport retention() {
    return retention;
  }

  public int blobsConsidered() {
    return blobsConsidered;
  }

  public int blobsDeleted() {
    return blobsDeleted;
  }

  public long bytesReclaimed() {
    return bytesReclaimed;
  }
}
