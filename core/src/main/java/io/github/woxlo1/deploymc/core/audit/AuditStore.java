package io.github.woxlo1.deploymc.core.audit;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Host-facing audit facade: writes, queries, exports, verifies, and retains records. */
public final class AuditStore implements AutoCloseable {

  public static final int MAX_EXPORT_RECORDS = 100000;

  private final AuditConfig config;
  private final Clock clock;
  private final AuditReader reader;
  private final AuditWriter writer;
  private final AuditVerifier verifier;
  private final AuditRetention retention;

  public AuditStore(Path directory, AuditConfig config, Clock clock) throws AuditException {
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
    Path resolved = Objects.requireNonNull(directory, "directory");
    this.reader = new AuditReader(resolved);
    this.writer = new AuditWriter(resolved, config, clock);
    this.verifier = new AuditVerifier(reader, config, clock);
    this.retention = new AuditRetention(reader, clock);
  }

  /** Resolves the host-controlled audit directory against the plugin data directory. */
  public static AuditStore open(Path dataDirectory, AuditConfig config, Clock clock)
      throws AuditException {
    Objects.requireNonNull(dataDirectory, "dataDirectory");
    try {
      return new AuditStore(config.resolveAgainst(dataDirectory), config, clock);
    } catch (IllegalArgumentException invalid) {
      throw AuditException.invalid("invalid audit configuration");
    }
  }

  public AuditRecord append(AuditEvent event) throws AuditException {
    return writer.append(event);
  }

  public List<AuditRecord> appendBatch(List<AuditEvent> events) throws AuditException {
    return writer.appendBatch(events);
  }

  /** Commits the mandatory pending record before a privileged state change. */
  public AuditRecord begin(AuditEvent event) throws AuditException {
    if (event.result() != AuditResult.PENDING) {
      throw AuditException.invalid("pending audit event has an invalid result");
    }
    return writer.append(event);
  }

  /** Commits the final record after a privileged state change, even when it failed. */
  public AuditRecord complete(AuditRecord pending, AuditResult result, String reason)
      throws AuditException {
    if (pending == null || pending.result() != AuditResult.PENDING) {
      throw AuditException.invalid("final audit record has no pending predecessor");
    }
    if (result == null || result == AuditResult.PENDING) {
      throw AuditException.invalid("final audit result is invalid");
    }
    AuditEvent event =
        AuditEvent.builder()
            .timestamp(clock.instant())
            .actor(pending.actor())
            .action(pending.action())
            .target(pending.target())
            .result(result)
            .reason(reason)
            .requestId(pending.requestId())
            .deploymentId(pending.deploymentId())
            .details(new LinkedHashMap<String, Object>(pending.details()))
            .build();
    return writer.append(event);
  }

  /** Records access to an audit route after its response work and without returned row data. */
  public AuditRecord recordAccess(
      AuditActor actor,
      String requestId,
      String action,
      String result,
      AuditQuery query,
      int rowCount)
      throws AuditException {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("filter", query.filterSummary());
    details.put("row-count", Long.valueOf(Math.max(0, rowCount)));
    AuditResult parsed = AuditResult.parse(result);
    if (parsed == null || parsed == AuditResult.PENDING) {
      throw AuditException.invalid("invalid audit access result");
    }
    return writer.append(
        AuditEvent.builder()
            .actor(actor)
            .action(action)
            .result(parsed)
            .requestId(requestId)
            .details(details)
            .build());
  }

  public AuditPage query(Map<String, String> parameters) throws AuditException {
    return query(AuditQuery.parse(parameters));
  }

  public AuditPage query(AuditQuery query) throws AuditException {
    Objects.requireNonNull(query, "query");
    List<AuditRecord> matched = matching(query);
    long after = query.afterSeq() == null ? 0L : query.afterSeq().longValue();
    List<AuditRecord> page = new ArrayList<AuditRecord>();
    for (AuditRecord record : matched) {
      if (record.seq() <= after) {
        continue;
      }
      page.add(record);
      if (page.size() >= query.limit()) {
        break;
      }
    }
    String nextCursor = null;
    if (!page.isEmpty()
        && page.size() == query.limit()
        && hasLater(matched, page.get(page.size() - 1).seq())) {
      nextCursor = AuditQuery.encodeCursor(page.get(page.size() - 1).seq());
    }
    return new AuditPage(page, nextCursor, matched.size());
  }

  /** Exports matching records verbatim as UTF-8 JSON Lines, or fails before any partial export. */
  public String export(Map<String, String> parameters) throws AuditException {
    AuditQuery query = AuditQuery.parse(parameters);
    if (query.cursor() != null) {
      throw AuditException.invalid("audit export does not accept a cursor");
    }
    List<AuditRecord> matched = matching(query);
    if (matched.size() > MAX_EXPORT_RECORDS) {
      throw AuditException.exportLimit("audit export exceeds the record limit");
    }
    StringBuilder result = new StringBuilder();
    for (AuditRecord record : matched) {
      result.append(record.rawLine()).append('\n');
    }
    return result.toString();
  }

  public AuditVerificationReport verify() throws AuditException {
    return verifier.verify();
  }

  /** Runs retention and returns file names that were old but not anchor-covered. */
  public List<AuditVerificationWarning> applyRetention() throws AuditException {
    return retention.apply(config);
  }

  /** Appends a clean-shutdown anchor before closing the writer. */
  @Override
  public void close() throws AuditException {
    AuditException failure = null;
    try {
      writer.appendAnchor();
    } catch (AuditException exception) {
      failure = exception;
    }
    try {
      writer.close();
    } catch (AuditException exception) {
      if (failure == null) {
        failure = exception;
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  public long lastSequence() {
    return writer.lastSequence();
  }

  public String lastHash() {
    return writer.lastHash();
  }

  private List<AuditRecord> matching(AuditQuery query) throws AuditException {
    List<AuditRecord> matched = new ArrayList<AuditRecord>();
    for (AuditRecord record : reader.read().records()) {
      if (query.matches(record)) {
        matched.add(record);
      }
    }
    Collections.sort(
        matched,
        new java.util.Comparator<AuditRecord>() {
          @Override
          public int compare(AuditRecord left, AuditRecord right) {
            return left.seq() < right.seq() ? -1 : left.seq() > right.seq() ? 1 : 0;
          }
        });
    return matched;
  }

  private static boolean hasLater(List<AuditRecord> records, long sequence) {
    for (AuditRecord record : records) {
      if (record.seq() > sequence) {
        return true;
      }
    }
    return false;
  }
}
