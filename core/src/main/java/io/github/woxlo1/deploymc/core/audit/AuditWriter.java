package io.github.woxlo1.deploymc.core.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Append-only, fail-closed audit writer with hashing, rotation, anchors, and fsync policy. */
public final class AuditWriter {

  private static final DateTimeFormatter ROTATION_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

  private final Path directory;
  private final Path activeFile;
  private final AuditConfig config;
  private final Clock clock;
  private final AuditReader reader;
  private final Object lock = new Object();

  private FileChannel channel;
  private long lastSeq;
  private String lastHash = AuditHash.ZERO;
  private Instant lastTimestamp;
  private long anchorFirstSeq = 1L;
  private String previousAnchorHash;
  private AuditRecord latestAnchor;
  private int unforcedCount;
  private boolean failed;
  private boolean closed;

  public AuditWriter(Path directory, AuditConfig config, Clock clock) throws AuditException {
    this.directory = Objects.requireNonNull(directory, "directory");
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.activeFile = directory.resolve(AuditReader.ACTIVE_FILE);
    this.reader = new AuditReader(directory);
    initialize();
  }

  /** Appends one event with the configured flush policy. */
  public AuditRecord append(AuditEvent event) throws AuditException {
    return appendBatch(Collections.singletonList(event)).get(0);
  }

  /** Appends a bounded synchronous batch. A batch never exceeds the configured maximum. */
  public List<AuditRecord> appendBatch(List<AuditEvent> events) throws AuditException {
    synchronized (lock) {
      ensureUsable();
      if (events == null || events.isEmpty()) {
        return Collections.emptyList();
      }
      if (events.size() > config.maxBatchRecords()) {
        throw AuditException.invalid("audit batch exceeds configured maximum");
      }
      List<AuditRecord> created = new ArrayList<AuditRecord>();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try {
        for (AuditEvent event : events) {
          if (event == null) {
            throw AuditException.invalid("audit event is null");
          }
          Instant timestamp = event.timestamp() == null ? clock.instant() : event.timestamp();
          appendClockAnomalyIfNeeded(timestamp, created, buffer);
          appendDailyAnchorIfNeeded(timestamp, created, buffer);
          appendEventInternal(event, timestamp, created, buffer);
        }
        flush(buffer, true);
      } catch (IllegalArgumentException rejected) {
        throw AuditException.writeFailed("audit record rejected before hashing");
      } catch (IOException exception) {
        failed = true;
        throw AuditException.writeFailed("audit append failed");
      }
      return Collections.unmodifiableList(created);
    }
  }

  /** Appends a chain anchor covering all records since the previous anchor. */
  public AuditRecord appendAnchor() throws AuditException {
    synchronized (lock) {
      ensureUsable();
      if (lastSeq == 0L) {
        return null;
      }
      List<AuditRecord> created = new ArrayList<AuditRecord>();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try {
        AuditRecord anchor = appendAnchorInternal(clock.instant(), created, buffer);
        flush(buffer, true);
        return anchor;
      } catch (IllegalArgumentException rejected) {
        throw AuditException.writeFailed("audit anchor rejected before hashing");
      } catch (IOException exception) {
        failed = true;
        throw AuditException.writeFailed("audit anchor write failed");
      }
    }
  }

  public long lastSequence() {
    synchronized (lock) {
      return lastSeq;
    }
  }

  public String lastHash() {
    synchronized (lock) {
      return lastHash;
    }
  }

  public AuditRecord latestAnchor() {
    synchronized (lock) {
      return latestAnchor;
    }
  }

  /**
   * Flushes any pending bytes and closes the channel. Clean shutdown anchors are added by the
   * store.
   */
  public void close() throws AuditException {
    synchronized (lock) {
      if (closed) {
        return;
      }
      try {
        if (!failed && channel != null) {
          channel.force(true);
        }
        if (channel != null) {
          channel.close();
        }
      } catch (IOException exception) {
        failed = true;
        throw AuditException.writeFailed("audit close failed");
      } finally {
        channel = null;
        closed = true;
      }
    }
  }

  private void initialize() throws AuditException {
    try {
      if (Files.isSymbolicLink(directory)) {
        throw AuditException.unavailable("audit directory is a link");
      }
      Files.createDirectories(directory);
      if (Files.isSymbolicLink(activeFile)) {
        throw AuditException.unavailable("audit active file is a link");
      }
      Path formatVersion = directory.resolve(AuditReader.FORMAT_VERSION_FILE);
      if (Files.isSymbolicLink(formatVersion)) {
        throw AuditException.unavailable("audit format version is a link");
      }
      if (!Files.exists(formatVersion, LinkOption.NOFOLLOW_LINKS)) {
        try {
          Files.write(formatVersion, "1\n".getBytes(StandardCharsets.US_ASCII));
        } catch (FileAlreadyExistsException concurrent) {
          // Another host process created the same host-controlled marker; continue.
        }
      }
      if (!Files.exists(activeFile, LinkOption.NOFOLLOW_LINKS)) {
        Files.createFile(activeFile);
      }
      requireCompleteFinalLine();
      AuditReadResult current = reader.read();
      String expectedPrevious = AuditHash.ZERO;
      for (AuditRecord record : current.records()) {
        if (!record.hash().equals(record.computedHash())
            || !expectedPrevious.equals(record.previousHash())) {
          throw AuditException.unavailable("audit chain is damaged");
        }
        if (lastSeq != 0L && record.seq() != lastSeq + 1L) {
          throw AuditException.unavailable("audit chain has a sequence gap");
        }
        lastSeq = record.seq();
        lastHash = record.hash();
        lastTimestamp = record.timestamp();
        if (AuditActions.AUDIT_ANCHOR.equals(record.action())) {
          latestAnchor = record;
          anchorFirstSeq = record.seq() + 1L;
          previousAnchorHash = record.hash();
        }
        expectedPrevious = record.hash();
      }
      channel = FileChannel.open(activeFile, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    } catch (IOException exception) {
      throw AuditException.unavailable("audit store cannot be opened");
    }
  }

  private void requireCompleteFinalLine() throws AuditException {
    try (FileChannel probe = FileChannel.open(activeFile, StandardOpenOption.READ)) {
      long size = probe.size();
      if (size == 0L) {
        return;
      }
      ByteBuffer last = ByteBuffer.allocate(1);
      probe.read(last, size - 1L);
      if (last.get(0) != (byte) '\n') {
        throw AuditException.unavailable("audit active file has a partial line");
      }
    } catch (IOException exception) {
      throw AuditException.unavailable("audit active file cannot be read");
    }
  }

  private void appendClockAnomalyIfNeeded(
      Instant timestamp, List<AuditRecord> created, ByteArrayOutputStream buffer)
      throws IOException, AuditException {
    if (lastTimestamp == null || !timestamp.isBefore(lastTimestamp.minusSeconds(1L))) {
      return;
    }
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("previous-ts", AuditRecord.formatTimestamp(lastTimestamp));
    details.put("observed-ts", AuditRecord.formatTimestamp(timestamp));
    AuditEvent event =
        AuditEvent.builder()
            .timestamp(timestamp)
            .actor(AuditActor.system())
            .action(AuditActions.CLOCK_ANOMALY)
            .result(AuditResult.SUCCESS)
            .details(details)
            .build();
    appendEventInternal(event, timestamp, created, buffer);
  }

  private void appendDailyAnchorIfNeeded(
      Instant timestamp, List<AuditRecord> created, ByteArrayOutputStream buffer)
      throws IOException, AuditException {
    if (lastTimestamp == null || day(lastTimestamp).equals(day(timestamp))) {
      return;
    }
    appendAnchorInternal(timestamp, created, buffer);
    flushIfNeeded(buffer);
  }

  private AuditRecord appendAnchorInternal(
      Instant timestamp, List<AuditRecord> created, ByteArrayOutputStream buffer)
      throws IOException, AuditException {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("first-seq", Long.valueOf(anchorFirstSeq));
    details.put("last-seq", Long.valueOf(lastSeq));
    details.put("last-hash", lastHash);
    details.put("record-count", Long.valueOf(lastSeq - anchorFirstSeq + 1L));
    details.put("previous-anchor", previousAnchorHash);
    AuditEvent event =
        AuditEvent.builder()
            .timestamp(timestamp)
            .actor(AuditActor.system())
            .action(AuditActions.AUDIT_ANCHOR)
            .result(AuditResult.SUCCESS)
            .details(details)
            .build();
    return appendEventInternal(event, timestamp, created, buffer);
  }

  private AuditRecord appendEventInternal(
      AuditEvent event, Instant timestamp, List<AuditRecord> created, ByteArrayOutputStream buffer)
      throws IOException, AuditException {
    AuditRecord probe = AuditRecord.create(event, timestamp, lastSeq + 1L, lastHash);
    rotateIfNeeded(probe, created, buffer);
    AuditRecord record = AuditRecord.create(event, timestamp, lastSeq + 1L, lastHash);
    appendRecord(record, created, buffer);
    flushIfNeeded(buffer);
    return record;
  }

  private void rotateIfNeeded(
      AuditRecord next, List<AuditRecord> created, ByteArrayOutputStream buffer)
      throws IOException, AuditException {
    long pending = (long) buffer.size() + (long) line(next).length;
    if (lastSeq == 0L || channel.size() + pending <= config.rotateBytes()) {
      return;
    }
    flush(buffer, true);
    channel.force(true);
    String rotatedName =
        "audit-"
            + String.format(java.util.Locale.ROOT, "%010d", Long.valueOf(lastSeq))
            + "-"
            + ROTATION_TIME.format(clock.instant())
            + ".jsonl";
    Path rotated = directory.resolve(rotatedName);
    if (Files.exists(rotated, LinkOption.NOFOLLOW_LINKS)) {
      throw AuditException.writeFailed("audit rotation target already exists");
    }
    channel.close();
    Files.move(activeFile, rotated);
    channel =
        FileChannel.open(
            activeFile,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND);

    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("previous-file", rotatedName);
    details.put("previous-last-seq", Long.valueOf(lastSeq));
    details.put("previous-last-hash", lastHash);
    details.put("format-version", Long.valueOf(1L));
    AuditEvent event =
        AuditEvent.builder()
            .timestamp(clock.instant())
            .actor(AuditActor.system())
            .action(AuditActions.AUDIT_ROTATE)
            .result(AuditResult.SUCCESS)
            .details(details)
            .build();
    Instant timestamp = event.timestamp();
    AuditRecord continuation = AuditRecord.create(event, timestamp, lastSeq + 1L, lastHash);
    appendRecord(continuation, created, buffer);
    flush(buffer, true);
  }

  private void appendRecord(
      AuditRecord record, List<AuditRecord> created, ByteArrayOutputStream buffer)
      throws IOException {
    buffer.write(line(record));
    created.add(record);
    unforcedCount++;
    lastSeq = record.seq();
    lastHash = record.hash();
    lastTimestamp = record.timestamp();
    if (AuditActions.AUDIT_ANCHOR.equals(record.action())) {
      latestAnchor = record;
      anchorFirstSeq = record.seq() + 1L;
      previousAnchorHash = record.hash();
    }
  }

  private void flushIfNeeded(ByteArrayOutputStream buffer) throws IOException {
    if (config.fsyncPolicy() == AuditConfig.FsyncPolicy.RECORD
        || unforcedCount >= config.maxBatchRecords()) {
      flush(buffer, true);
    }
  }

  private void flush(ByteArrayOutputStream buffer, boolean force) throws IOException {
    if (buffer.size() > 0) {
      ByteBuffer bytes = ByteBuffer.wrap(buffer.toByteArray());
      while (bytes.hasRemaining()) {
        channel.write(bytes);
      }
      buffer.reset();
      unforcedCount = 0;
    }
    if (force) {
      channel.force(true);
    }
  }

  private static byte[] line(AuditRecord record) {
    return (record.canonicalJson() + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private static LocalDate day(Instant timestamp) {
    return timestamp.atZone(ZoneOffset.UTC).toLocalDate();
  }

  private void ensureUsable() throws AuditException {
    if (closed) {
      throw AuditException.unavailable("audit writer is closed");
    }
    if (failed) {
      throw AuditException.writeFailed("audit writer is unusable after a failed write");
    }
  }
}
