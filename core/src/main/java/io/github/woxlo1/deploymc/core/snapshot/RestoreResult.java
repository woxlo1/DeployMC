package io.github.woxlo1.deploymc.core.snapshot;

/** Identifiers produced by a restore, including the reversible pre-restore snapshot. */
public final class RestoreResult {

  private final String restoredSnapshotId;
  private final String preRestoreSnapshotId;

  public RestoreResult(String restoredSnapshotId, String preRestoreSnapshotId) {
    this.restoredSnapshotId = restoredSnapshotId;
    this.preRestoreSnapshotId = preRestoreSnapshotId;
  }

  public String restoredSnapshotId() {
    return restoredSnapshotId;
  }

  public String preRestoreSnapshotId() {
    return preRestoreSnapshotId;
  }
}
