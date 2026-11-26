package io.github.woxlo1.deploymc.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditVerificationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-15T09:30:12.345Z"), ZoneOffset.UTC);

  @TempDir Path tempDir;

  @Test
  void tamperedRecordIsReportedAsTheFirstHashFailure() throws Exception {
    Path directory = tempDir.resolve("audit");
    try (AuditStore store = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      store.append(event("req-1", 1L));
      store.append(event("req-2", 2L));
    }

    Path active = directory.resolve(AuditReader.ACTIVE_FILE);
    List<String> lines = Files.readAllLines(active, StandardCharsets.UTF_8);
    assertEquals(3, lines.size());
    lines.set(1, lines.get(1).replace("\"bytes\":2", "\"bytes\":3"));
    Files.write(active, lines, StandardCharsets.UTF_8);

    AuditVerificationReport report = verify(directory);
    assertTrue(!report.ok());
    assertNotNull(report.firstFailure());
    assertEquals("hash-mismatch", report.firstFailure().code());
    assertEquals(2L, report.firstFailure().seq());
  }

  @Test
  void changedPreviousHashIsReportedBeforeHashMismatch() throws Exception {
    Path directory = tempDir.resolve("audit");
    try (AuditStore store = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      store.append(event("req-1", 1L));
      store.append(event("req-2", 2L));
    }

    Path active = directory.resolve(AuditReader.ACTIVE_FILE);
    List<String> lines = Files.readAllLines(active, StandardCharsets.UTF_8);
    AuditRecord second = AuditRecord.parse(lines.get(1), AuditReader.ACTIVE_FILE, 2);
    lines.set(1, lines.get(1).replace(second.previousHash(), AuditHash.ZERO));
    Files.write(active, lines, StandardCharsets.UTF_8);

    AuditVerificationReport report = verify(directory);
    assertTrue(!report.ok());
    assertEquals("prev-mismatch", report.firstFailure().code());
  }

  @Test
  void unmatchedPendingIsAWarningRatherThanAChainFailure() throws Exception {
    Path directory = tempDir.resolve("audit");
    try (AuditStore store = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      store.begin(
          AuditEvent.builder()
              .actor(AuditActor.system())
              .action(AuditActions.TOKENS_CREATE)
              .result(AuditResult.PENDING)
              .requestId("req-pending")
              .build());
    }

    AuditVerificationReport report = verify(directory);
    assertTrue(report.ok());
    assertTrue(
        report.warnings().stream()
            .anyMatch(
                warning -> AuditVerificationWarning.PENDING_WITHOUT_FINAL.equals(warning.code())));
  }

  @Test
  void partialFinalLineIsAWarningRatherThanTampering() throws Exception {
    Path directory = tempDir.resolve("audit");
    try (AuditStore store = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      store.append(event("req-1", 1L));
    }

    Path active = directory.resolve(AuditReader.ACTIVE_FILE);
    String current = new String(Files.readAllBytes(active), StandardCharsets.UTF_8);
    Files.write(active, (current + "{\"seq\":2").getBytes(StandardCharsets.UTF_8));

    AuditVerificationReport report = verify(directory);
    assertTrue(report.ok());
    assertTrue(
        report.warnings().stream()
            .anyMatch(warning -> AuditVerificationWarning.PARTIAL_LINE.equals(warning.code())));
  }

  @Test
  void unknownFormatVersionIsReported() throws Exception {
    Path directory = tempDir.resolve("audit");
    try (AuditStore store = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      store.append(event("req-1", 1L));
    }
    Files.write(
        directory.resolve(AuditReader.FORMAT_VERSION_FILE),
        "2\n".getBytes(StandardCharsets.US_ASCII));

    AuditVerificationReport report = verify(directory);
    assertTrue(!report.ok());
    assertEquals("format-version", report.firstFailure().code());
  }

  private static AuditVerificationReport verify(Path directory) throws AuditException {
    return new AuditVerifier(new AuditReader(directory), AuditConfig.defaults(), CLOCK).verify();
  }

  private static AuditEvent event(String requestId, long bytes) {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("bytes", Long.valueOf(bytes));
    return AuditEvent.builder()
        .actor(AuditActor.of("alice", "token", Arrays.asList("developer"), null))
        .action(AuditActions.FILES_WRITE)
        .target(AuditTarget.path("plugins/Example/config.yml"))
        .result(AuditResult.SUCCESS)
        .requestId(requestId)
        .details(details)
        .build();
  }
}
