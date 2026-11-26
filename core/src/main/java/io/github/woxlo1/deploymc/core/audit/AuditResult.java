package io.github.woxlo1.deploymc.core.audit;

/** Closed result vocabulary for an audit record. */
public enum AuditResult {
  SUCCESS("success"),
  DENIED("denied"),
  FAILED("failed"),
  PENDING("pending");

  private final String wire;

  AuditResult(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }

  public static AuditResult parse(String value) {
    if (value == null) {
      return null;
    }
    for (AuditResult candidate : values()) {
      if (candidate.wire.equals(value)) {
        return candidate;
      }
    }
    return null;
  }
}
