package io.github.woxlo1.deploymc.core.snapshot;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Authoritative metadata for one immutable snapshot. */
public final class SnapshotRecord {

  private final String id;
  private final String capture;
  private final String path;
  private final SnapshotKind kind;
  private final String hash;
  private final long size;
  private final String mode;
  private final Instant createdAt;
  private final String actor;
  private final SnapshotReason reason;
  private final String deploymentId;
  private final String parent;
  private final Map<String, String> pluginMetadata;

  public SnapshotRecord(
      String id,
      String capture,
      String path,
      SnapshotKind kind,
      String hash,
      long size,
      String mode,
      Instant createdAt,
      String actor,
      SnapshotReason reason,
      String deploymentId,
      String parent,
      Map<String, String> pluginMetadata) {
    this.id = Objects.requireNonNull(id, "id");
    this.capture = Objects.requireNonNull(capture, "capture");
    this.path = Objects.requireNonNull(path, "path");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.hash = hash;
    this.size = size;
    this.mode = mode;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.actor = Objects.requireNonNull(actor, "actor");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.deploymentId = deploymentId;
    this.parent = parent;
    Map<String, String> copy = new LinkedHashMap<String, String>();
    if (pluginMetadata != null) {
      copy.putAll(pluginMetadata);
    }
    this.pluginMetadata = Collections.unmodifiableMap(copy);
  }

  public String id() {
    return id;
  }

  public String capture() {
    return capture;
  }

  public String path() {
    return path;
  }

  public SnapshotKind kind() {
    return kind;
  }

  /** Strong revision including the {@code sha256:} prefix, or null for non-content records. */
  public String hash() {
    return hash;
  }

  /** Lowercase hex digest without the prefix, or null for non-content records. */
  public String hashHex() {
    return hash == null ? null : hash.substring("sha256:".length());
  }

  public long size() {
    return size;
  }

  public String mode() {
    return mode;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String actor() {
    return actor;
  }

  public SnapshotReason reason() {
    return reason;
  }

  public String deploymentId() {
    return deploymentId;
  }

  public String parent() {
    return parent;
  }

  public Map<String, String> pluginMetadata() {
    return pluginMetadata;
  }

  @Override
  public String toString() {
    return "SnapshotRecord[" + id + ", " + path + ", " + kind.value() + "]";
  }
}
