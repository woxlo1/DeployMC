package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe rejection counters with no credential or identity material. */
public final class JwtDiagnostics {

  private final Clock clock;
  private final Map<JwtRejection, Long> counts =
      new EnumMap<JwtRejection, Long>(JwtRejection.class);
  private final AtomicReference<Instant> lastUpdatedAt = new AtomicReference<Instant>();
  private volatile JwtRejection lastReason;

  public JwtDiagnostics(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Records one rejection. Only the closed reason is retained. */
  public void record(JwtRejection reason) {
    Objects.requireNonNull(reason, "reason");
    synchronized (counts) {
      Long current = counts.get(reason);
      counts.put(reason, Long.valueOf((current == null ? 0L : current.longValue()) + 1L));
      lastReason = reason;
      lastUpdatedAt.set(clock.instant());
    }
  }

  public JwtDiagnosticsSnapshot snapshot() {
    Map<String, Long> copy = new LinkedHashMapUtil().asMap(counts);
    JwtRejection reason = lastReason;
    return new JwtDiagnosticsSnapshot(
        copy,
        reason == null ? "" : reason.name(),
        reason == null ? "" : reason.errorCode(),
        lastUpdatedAt.get());
  }

  /** Small helper keeps snapshot ordering deterministic without exposing the mutable map. */
  private static final class LinkedHashMapUtil {
    Map<String, Long> asMap(Map<JwtRejection, Long> source) {
      List<JwtRejection> reasons = new ArrayList<JwtRejection>(source.keySet());
      Collections.sort(reasons, java.util.Comparator.comparing(Enum::name));
      Map<String, Long> result = new java.util.LinkedHashMap<String, Long>();
      for (JwtRejection reason : reasons) {
        result.put(reason.name(), source.get(reason));
      }
      return result;
    }
  }
}
