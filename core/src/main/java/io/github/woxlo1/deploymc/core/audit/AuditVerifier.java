package io.github.woxlo1.deploymc.core.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Verifies the hash chain, rotation continuity, anchors, and retention bounds. */
public final class AuditVerifier {

  private final AuditReader reader;
  private final AuditConfig config;
  private final Clock clock;

  public AuditVerifier(AuditReader reader, AuditConfig config, Clock clock) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Produces a bounded report; integrity failures stay in the body instead of throwing. */
  public AuditVerificationReport verify() throws AuditException {
    AuditReadResult input = reader.readWithDefects();
    List<AuditVerificationFailure> failures =
        new ArrayList<AuditVerificationFailure>(input.failures());
    List<AuditVerificationWarning> warnings =
        new ArrayList<AuditVerificationWarning>(input.warnings());

    if (input.formatVersion() != 1 && failures.isEmpty()) {
      failures.add(
          new AuditVerificationFailure(AuditReader.FORMAT_VERSION_FILE, 1, 0L, "format-version"));
    }

    List<AuditRecord> anchors = new ArrayList<AuditRecord>();
    List<AuditRecord> pendings = new ArrayList<AuditRecord>();
    List<AuditRecord> finals = new ArrayList<AuditRecord>();
    Map<String, AnchorRange> anchorRanges = new LinkedHashMap<String, AnchorRange>();
    String previousAnchorHash = null;
    for (AuditRecord record : input.records()) {
      if (!AuditActions.AUDIT_ANCHOR.equals(record.action())) {
        continue;
      }
      anchors.add(record);
      AnchorRange range = parseAnchorRange(record, failures);
      if (range == null) {
        continue;
      }
      if (!equal(record.details().get("previous-anchor"), previousAnchorHash)) {
        addFailure(failures, record, "anchor-mismatch");
      }
      previousAnchorHash = record.hash();
      anchorRanges.put(record.hash(), range);
    }

    long expectedSeq = 1L;
    String expectedPrevious = AuditHash.ZERO;
    Instant previousTimestamp = null;
    String previousFile = null;
    long previousFileLastSeq = 0L;
    String previousFileLastHash = AuditHash.ZERO;
    if (!input.records().isEmpty()) {
      AuditRecord first = input.records().get(0);
      if (first.seq() > 1L && AuditActions.AUDIT_ROTATE.equals(first.action())) {
        Long coveredSeq = asLong(first.details().get("previous-last-seq"));
        String coveredHash = (String) first.details().get("previous-last-hash");
        if (coveredSeq != null
            && AuditHash.valid(coveredHash)
            && anchoredCovers(anchorRanges, coveredSeq.longValue())) {
          expectedSeq = first.seq();
          expectedPrevious = first.previousHash();
        }
      }
    }

    for (AuditRecord record : input.records()) {
      String file = record.fileName() == null ? AuditReader.ACTIVE_FILE : record.fileName();
      boolean firstOfFile = !file.equals(previousFile);
      if (firstOfFile && !AuditActions.AUDIT_ROTATE.equals(record.action())) {
        if (previousFile != null || record.seq() > 1L) {
          addFailure(failures, record, "rotation-continuity");
        }
      } else if (firstOfFile
          && previousFile != null
          && !validRotationDetails(
              record, previousFile, previousFileLastSeq, previousFileLastHash)) {
        addFailure(failures, record, "rotation-continuity");
      }

      if (record.seq() != expectedSeq) {
        addFailure(
            failures, record, record.seq() < expectedSeq ? "sequence-duplicate" : "sequence-gap");
      }
      if (!expectedPrevious.equals(record.previousHash())) {
        addFailure(failures, record, "prev-mismatch");
      }
      if (!record.hash().equals(record.computedHash())) {
        addFailure(failures, record, "hash-mismatch");
      }
      if (previousTimestamp != null
          && record.timestamp().isBefore(previousTimestamp.minusSeconds(1L))) {
        addWarning(
            warnings,
            new AuditVerificationWarning(
                AuditVerificationWarning.CLOCK_REGRESSION,
                "sequence " + record.seq() + " regressed"));
      }

      if (record.result() == AuditResult.PENDING) {
        pendings.add(record);
      } else {
        finals.add(record);
      }

      expectedSeq = record.seq() + 1L;
      expectedPrevious = record.hash();
      previousTimestamp = record.timestamp();
      previousFile = file;
      previousFileLastSeq = record.seq();
      previousFileLastHash = record.hash();
    }

    for (AuditRecord pending : pendings) {
      if (!hasFinal(pending, finals)) {
        addWarning(
            warnings,
            new AuditVerificationWarning(
                AuditVerificationWarning.PENDING_WITHOUT_FINAL,
                "sequence " + pending.seq() + " has no final record"));
      }
    }

    Instant cutoff = clock.instant().minus(Duration.ofDays(config.retentionDays()));
    for (AuditFileInfo file : input.files()) {
      if (file.active() || file.lastTimestamp() == null || !file.lastTimestamp().isBefore(cutoff)) {
        continue;
      }
      if (!anchorCovers(anchorRanges, file)) {
        addWarning(
            warnings,
            new AuditVerificationWarning(
                AuditVerificationWarning.RETENTION_INCOMPLETE, "file " + file.name()));
      }
    }

    AuditVerificationFailure firstFailure = failures.isEmpty() ? null : failures.get(0);
    List<String> limitations = new ArrayList<String>();
    limitations.add(AuditVerificationReport.LIMITATION_NO_OFFHOST_ANCHOR);
    limitations.add(AuditVerificationReport.LIMITATION_TAMPER_EVIDENCE);
    AuditRecord latestAnchor = anchors.isEmpty() ? null : anchors.get(anchors.size() - 1);
    long lastSeq =
        input.records().isEmpty() ? 0L : input.records().get(input.records().size() - 1).seq();
    String lastHash =
        input.records().isEmpty() ? null : input.records().get(input.records().size() - 1).hash();
    return new AuditVerificationReport(
        failures.isEmpty(),
        input.formatVersion(),
        input.records().size(),
        lastSeq,
        lastHash,
        firstFailure,
        warnings,
        latestAnchor,
        limitations);
  }

  private AnchorRange parseAnchorRange(
      AuditRecord record, List<AuditVerificationFailure> failures) {
    Map<String, Object> details = record.details();
    Long first = asLong(details.get("first-seq"));
    Long last = asLong(details.get("last-seq"));
    Long count = asLong(details.get("record-count"));
    Object lastHash = details.get("last-hash");
    Object previousAnchor = details.get("previous-anchor");
    if (first == null
        || last == null
        || count == null
        || first.longValue() < 1L
        || last.longValue() < first.longValue()
        || last.longValue() >= record.seq()
        || count.longValue() != last.longValue() - first.longValue() + 1L
        || !AuditHash.valid((String) lastHash)
        || (previousAnchor != null && !AuditHash.valid((String) previousAnchor))) {
      addFailure(failures, record, "anchor-mismatch");
      return null;
    }
    return new AnchorRange(first.longValue(), last.longValue(), (String) lastHash);
  }

  private boolean validRotationDetails(
      AuditRecord record, String previousFile, long previousLastSeq, String previousLastHash) {
    Map<String, Object> details = record.details();
    return previousFile.equals(details.get("previous-file"))
        && Long.valueOf(previousLastSeq).equals(asLong(details.get("previous-last-seq")))
        && previousLastHash.equals(details.get("previous-last-hash"))
        && Long.valueOf(1L).equals(asLong(details.get("format-version")));
  }

  private boolean hasFinal(AuditRecord pending, List<AuditRecord> finals) {
    for (AuditRecord candidate : finals) {
      if (!equal(pending.requestId(), candidate.requestId())) {
        continue;
      }
      if (pending.seq() >= candidate.seq()) {
        continue;
      }
      if (pending.action().equals(candidate.action()) && sameTarget(pending, candidate)) {
        return true;
      }
    }
    return false;
  }

  private boolean sameTarget(AuditRecord left, AuditRecord right) {
    AuditTarget a = left.target();
    AuditTarget b = right.target();
    if (a == null || b == null) {
      return a == b;
    }
    return equal(a.path(), b.path()) && equal(a.revision(), b.revision());
  }

  private boolean anchorCovers(Map<String, AnchorRange> anchors, AuditFileInfo file) {
    for (AnchorRange range : anchors.values()) {
      if (range.firstSeq <= file.lastSeq() && range.lastSeq >= file.lastSeq()) {
        return true;
      }
    }
    return false;
  }

  private boolean anchoredCovers(Map<String, AnchorRange> anchors, long sequence) {
    for (AnchorRange range : anchors.values()) {
      if (range.firstSeq <= sequence && range.lastSeq >= sequence) {
        return true;
      }
    }
    return false;
  }

  private static Long asLong(Object value) {
    return value instanceof Long ? (Long) value : null;
  }

  private static boolean equal(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private static void addFailure(
      List<AuditVerificationFailure> failures, AuditRecord record, String code) {
    if (failures.isEmpty()) {
      failures.add(
          new AuditVerificationFailure(
              record.fileName() == null ? AuditReader.ACTIVE_FILE : record.fileName(),
              record.lineNumber() == 0 ? 1 : record.lineNumber(),
              record.seq(),
              code));
    }
  }

  private static void addWarning(
      List<AuditVerificationWarning> warnings, AuditVerificationWarning warning) {
    if (warnings.size() < 1024) {
      warnings.add(warning);
    }
  }

  private static final class AnchorRange {

    private final long firstSeq;
    private final long lastSeq;
    private final String lastHash;

    private AnchorRange(long firstSeq, long lastSeq, String lastHash) {
      this.firstSeq = firstSeq;
      this.lastSeq = lastSeq;
      this.lastHash = lastHash;
    }
  }
}
