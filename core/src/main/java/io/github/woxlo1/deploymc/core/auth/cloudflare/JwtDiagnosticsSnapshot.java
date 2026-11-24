package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, redacted diagnostic counters. Never contains a token, identity, or header value. */
public final class JwtDiagnosticsSnapshot {

  private final Map<String, Long> countsByReason;
  private final String lastReason;
  private final String lastErrorCode;
  private final Instant lastUpdatedAt;

  JwtDiagnosticsSnapshot(
      Map<String, Long> countsByReason,
      String lastReason,
      String lastErrorCode,
      Instant lastUpdatedAt) {
    this.countsByReason =
        Collections.unmodifiableMap(new LinkedHashMap<String, Long>(countsByReason));
    this.lastReason = lastReason == null ? "" : lastReason;
    this.lastErrorCode = lastErrorCode == null ? "" : lastErrorCode;
    this.lastUpdatedAt = lastUpdatedAt;
  }

  /** Counts keyed by stable {@link JwtRejection} names, in deterministic order. */
  public Map<String, Long> countsByReason() {
    return countsByReason;
  }

  public String lastReason() {
    return lastReason;
  }

  public String lastErrorCode() {
    return lastErrorCode;
  }

  public Instant lastUpdatedAt() {
    return lastUpdatedAt;
  }
}
