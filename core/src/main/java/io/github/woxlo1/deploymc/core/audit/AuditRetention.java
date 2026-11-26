package io.github.woxlo1.deploymc.core.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deletes only anchor-covered rotated files that are wholly older than retention. */
public final class AuditRetention {

  private final AuditReader reader;
  private final Clock clock;

  public AuditRetention(AuditReader reader, Clock clock) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Returns file names that were too old but lacked whole-file anchor coverage. */
  public List<AuditVerificationWarning> apply(AuditConfig config) throws AuditException {
    AuditReadResult current = reader.read();
    Map<String, long[]> ranges = fileRanges(current);
    List<long[]> anchors = anchorRanges(current);
    Instant cutoff = clock.instant().minus(Duration.ofDays(config.retentionDays()));
    List<AuditVerificationWarning> warnings = new ArrayList<AuditVerificationWarning>();
    for (AuditFileInfo file : current.files()) {
      if (file.active() || file.lastTimestamp() == null || !file.lastTimestamp().isBefore(cutoff)) {
        continue;
      }
      long[] range = ranges.get(file.name());
      if (range == null || !covered(anchors, range[0], range[1])) {
        warnings.add(
            new AuditVerificationWarning(
                AuditVerificationWarning.RETENTION_INCOMPLETE, "file " + file.name()));
        continue;
      }
      Path target = reader.directory().resolve(file.name());
      if (Files.isSymbolicLink(target)) {
        throw AuditException.unavailable("audit retention target is a link");
      }
      try {
        Files.deleteIfExists(target);
      } catch (IOException exception) {
        throw AuditException.unavailable("audit retention deletion failed");
      }
    }
    return warnings;
  }

  private static Map<String, long[]> fileRanges(AuditReadResult result) {
    Map<String, long[]> ranges = new HashMap<String, long[]>();
    for (AuditRecord record : result.records()) {
      String file = record.fileName();
      if (file == null) {
        continue;
      }
      long[] range = ranges.get(file);
      if (range == null) {
        ranges.put(file, new long[] {record.seq(), record.seq()});
      } else {
        range[1] = Math.max(range[1], record.seq());
        range[0] = Math.min(range[0], record.seq());
      }
    }
    return ranges;
  }

  private static List<long[]> anchorRanges(AuditReadResult result) {
    List<long[]> anchors = new ArrayList<long[]>();
    for (AuditRecord record : result.records()) {
      if (!AuditActions.AUDIT_ANCHOR.equals(record.action())) {
        continue;
      }
      Long first = number(record.details().get("first-seq"));
      Long last = number(record.details().get("last-seq"));
      if (first != null && last != null) {
        anchors.add(new long[] {first.longValue(), last.longValue()});
      }
    }
    return anchors;
  }

  private static boolean covered(List<long[]> anchors, long first, long last) {
    for (long[] anchor : anchors) {
      if (anchor[0] <= first && anchor[1] >= last) {
        return true;
      }
    }
    return false;
  }

  private static Long number(Object value) {
    return value instanceof Long ? (Long) value : null;
  }
}
