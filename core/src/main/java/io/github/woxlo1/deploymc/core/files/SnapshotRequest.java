package io.github.woxlo1.deploymc.core.files;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable description of the entries a caller wants preserved before a mutation. */
public final class SnapshotRequest {

  private final String rootPath;
  private final String reason;
  private final List<SnapshotEntry> entries;

  public SnapshotRequest(String rootPath, String reason, List<SnapshotEntry> entries) {
    if (rootPath == null || reason == null || entries == null) {
      throw new IllegalArgumentException("Snapshot request fields must not be null");
    }
    this.rootPath = rootPath;
    this.reason = reason;
    this.entries = Collections.unmodifiableList(new ArrayList<SnapshotEntry>(entries));
  }

  public String rootPath() {
    return rootPath;
  }

  public String reason() {
    return reason;
  }

  public List<SnapshotEntry> entries() {
    return entries;
  }
}
