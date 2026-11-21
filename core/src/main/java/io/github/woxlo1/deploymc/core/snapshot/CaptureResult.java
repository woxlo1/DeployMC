package io.github.woxlo1.deploymc.core.snapshot;

import java.util.Collections;
import java.util.List;

/** Capture id plus the individual immutable snapshot ids created by one request. */
public final class CaptureResult {

  private final String captureId;
  private final List<String> snapshotIds;

  public CaptureResult(String captureId, List<String> snapshotIds) {
    this.captureId = captureId;
    this.snapshotIds = Collections.unmodifiableList(snapshotIds);
  }

  public String captureId() {
    return captureId;
  }

  public List<String> snapshotIds() {
    return snapshotIds;
  }
}
