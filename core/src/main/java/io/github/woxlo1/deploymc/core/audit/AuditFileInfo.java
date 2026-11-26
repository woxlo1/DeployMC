package io.github.woxlo1.deploymc.core.audit;

import java.time.Instant;

/** Bounded metadata for one retained audit file. Never carries an absolute host path. */
public final class AuditFileInfo {

  private final String name;
  private final long lastSeq;
  private final Instant lastTimestamp;
  private final boolean active;

  public AuditFileInfo(String name, long lastSeq, Instant lastTimestamp, boolean active) {
    this.name = name;
    this.lastSeq = lastSeq;
    this.lastTimestamp = lastTimestamp;
    this.active = active;
  }

  public String name() {
    return name;
  }

  public long lastSeq() {
    return lastSeq;
  }

  public Instant lastTimestamp() {
    return lastTimestamp;
  }

  public boolean active() {
    return active;
  }
}
