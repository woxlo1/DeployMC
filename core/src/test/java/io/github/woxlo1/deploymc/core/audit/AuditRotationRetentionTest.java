package io.github.woxlo1.deploymc.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditRotationRetentionTest {

  private static final long MEGABYTE = 1024L * 1024L;
  private static final String PAYLOAD = repeat('x', 4096);

  @TempDir Path tempDir;

  @Test
  void rotationWritesAChainedContinuationRecord() throws Exception {
    MutableClock clock =
        new MutableClock(Instant.parse("2026-01-15T09:30:12.345Z"), ZoneOffset.UTC);
    Path directory = tempDir.resolve("audit");
    AuditConfig config =
        AuditConfig.of(".deploymc/audit", MEGABYTE, 365, AuditConfig.FsyncPolicy.BATCH, 256);

    try (AuditStore store = new AuditStore(directory, config, clock)) {
      store.appendBatch(events(0, 200));
      store.appendBatch(events(200, 60));

      List<Path> rotated = rotatedFiles(directory);
      assertEquals(1, rotated.size());
      AuditRecord firstRotated = firstRecord(rotated.get(0));
      assertEquals(AuditActions.FILES_WRITE, firstRotated.action());
      AuditRecord continuation = firstRecord(directory.resolve(AuditReader.ACTIVE_FILE));
      assertEquals(AuditActions.AUDIT_ROTATE, continuation.action());
      assertEquals(AuditActor.system().id(), continuation.actor().id());
      assertEquals(Long.valueOf(1L), continuation.details().get("format-version"));

      AuditVerificationReport report = store.verify();
      assertTrue(
          report.ok(),
          report.firstFailure() == null
              ? "unexpected failure"
              : report.firstFailure().code()
                  + " at "
                  + report.firstFailure().file()
                  + ":"
                  + report.firstFailure().line()
                  + " seq "
                  + report.firstFailure().seq());
      assertTrue(report.recordsChecked() >= 260L);
    }
  }

  @Test
  void retentionDeletesOnlyAnchorCoveredRotatedFiles() throws Exception {
    MutableClock clock =
        new MutableClock(Instant.parse("2026-01-15T09:30:12.345Z"), ZoneOffset.UTC);
    Path directory = tempDir.resolve("audit");
    AuditConfig config =
        AuditConfig.of(".deploymc/audit", MEGABYTE, 1, AuditConfig.FsyncPolicy.BATCH, 256);

    try (AuditStore store = new AuditStore(directory, config, clock)) {
      store.appendBatch(events(0, 200));
      store.appendBatch(events(200, 60));
      assertEquals(1, rotatedFiles(directory).size());

      clock.set(Instant.parse("2026-01-17T00:00:00.000Z"));
      store.append(event(260));
      List<AuditVerificationWarning> warnings = store.applyRetention();

      assertTrue(warnings.isEmpty());
      assertTrue(rotatedFiles(directory).isEmpty());
      AuditVerificationReport report = store.verify();
      assertTrue(
          report.ok(),
          report.firstFailure() == null
              ? "unexpected failure"
              : report.firstFailure().code() + " at seq " + report.firstFailure().seq());
      assertNotNull(report.latestAnchor());
      assertEquals(AuditActions.AUDIT_ANCHOR, report.latestAnchor().action());
      assertEquals(AuditReader.ACTIVE_FILE, report.latestAnchor().fileName());
    }
  }

  @Test
  void nonBatchPolicyFlushesEveryRecordAndBatchPolicyHonorsTheBound() throws Exception {
    MutableClock clock =
        new MutableClock(Instant.parse("2026-01-15T09:30:12.345Z"), ZoneOffset.UTC);
    AuditConfig recordConfig =
        AuditConfig.of(".deploymc/audit", 50L * MEGABYTE, 365, AuditConfig.FsyncPolicy.RECORD, 4);
    AuditConfig batchConfig =
        AuditConfig.of(".deploymc/audit", 50L * MEGABYTE, 365, AuditConfig.FsyncPolicy.BATCH, 2);

    try (AuditStore store = new AuditStore(tempDir.resolve("record"), recordConfig, clock)) {
      store.appendBatch(events(0, 4));
      assertEquals(4L, store.lastSequence());
      assertTrue(store.verify().ok());
    }
    try (AuditStore batchStore = new AuditStore(tempDir.resolve("batch"), batchConfig, clock)) {
      assertEquals(
          "request.invalid",
          org.junit.jupiter.api.Assertions.assertThrows(
                  AuditException.class, () -> batchStore.appendBatch(events(0, 3)))
              .problemCode());
      batchStore.appendBatch(events(0, 2));
      assertEquals(2L, batchStore.lastSequence());
      assertTrue(batchStore.verify().ok());
    }
  }

  private List<AuditEvent> events(int start, int count) {
    List<AuditEvent> result = new ArrayList<AuditEvent>(count);
    for (int index = start; index < start + count; index++) {
      result.add(event(index));
    }
    return result;
  }

  private static AuditEvent event(int index) {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("payload", PAYLOAD);
    details.put("index", Long.valueOf(index));
    return AuditEvent.builder()
        .actor(AuditActor.of("alice", "token", Collections.singletonList("developer"), null))
        .action(AuditActions.FILES_WRITE)
        .target(AuditTarget.path("plugins/Example/config.yml"))
        .result(AuditResult.SUCCESS)
        .requestId("req-" + index)
        .details(details)
        .build();
  }

  private static List<Path> rotatedFiles(Path directory) throws IOException {
    List<Path> result = new ArrayList<Path>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        String name = entry.getFileName().toString();
        if (name.startsWith("audit-") && name.endsWith(".jsonl")) {
          result.add(entry);
        }
      }
    }
    Collections.sort(result);
    return result;
  }

  private static AuditRecord firstRecord(Path file) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line = reader.readLine();
      assertNotNull(line);
      return AuditRecord.parse(line, file.getFileName().toString(), 1);
    }
  }

  private static String repeat(char value, int count) {
    char[] characters = new char[count];
    Arrays.fill(characters, value);
    return new String(characters);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    private MutableClock(Instant instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void set(Instant newInstant) {
      this.instant = newInstant;
    }
  }
}
