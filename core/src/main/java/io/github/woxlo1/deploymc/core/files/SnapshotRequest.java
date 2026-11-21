package io.github.woxlo1.deploymc.core.files;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable description of the entries a caller wants preserved before a mutation. */
public final class SnapshotRequest {

  private final String rootPath;
  private final String reason;
  private final List<SnapshotEntry> entries;
  private final String actor;
  private final String deploymentId;
  private final Map<String, String> pluginMetadata;

  public SnapshotRequest(String rootPath, String reason, List<SnapshotEntry> entries) {
    this(rootPath, reason, entries, "system", null, null);
  }

  public SnapshotRequest(
      String rootPath,
      String reason,
      List<SnapshotEntry> entries,
      String actor,
      String deploymentId,
      Map<String, String> pluginMetadata) {
    if (rootPath == null || reason == null || entries == null) {
      throw new IllegalArgumentException("Snapshot request fields must not be null");
    }
    if (actor == null || actor.isEmpty()) {
      throw new IllegalArgumentException("Snapshot actor must not be empty");
    }
    this.rootPath = rootPath;
    this.reason = reason;
    this.entries = Collections.unmodifiableList(new ArrayList<SnapshotEntry>(entries));
    this.actor = actor;
    this.deploymentId = deploymentId;
    Map<String, String> copy = new LinkedHashMap<String, String>();
    if (pluginMetadata != null) {
      copy.putAll(pluginMetadata);
    }
    this.pluginMetadata = Collections.unmodifiableMap(copy);
  }

  /** Canonical sandbox root used only to identify the owning store; never persisted. */
  public String rootPath() {
    return rootPath;
  }

  public String reason() {
    return reason;
  }

  public List<SnapshotEntry> entries() {
    return entries;
  }

  public String actor() {
    return actor;
  }

  public String deploymentId() {
    return deploymentId;
  }

  public Map<String, String> pluginMetadata() {
    return pluginMetadata;
  }
}
