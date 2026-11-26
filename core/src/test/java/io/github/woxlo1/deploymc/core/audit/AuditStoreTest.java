package io.github.woxlo1.deploymc.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditStoreTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-15T09:30:12.345Z"), ZoneOffset.UTC);

  @TempDir Path tempDir;

  @Test
  void pendingAndFinalRecordsFormAVerifiableChain() throws Exception {
    Path directory = tempDir.resolve("audit");
    try (AuditStore store = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      AuditRecord pending = store.begin(event(AuditResult.PENDING, "req-1", 1L));
      AuditRecord finalRecord = store.complete(pending, AuditResult.SUCCESS, null);

      assertEquals(1L, pending.seq());
      assertEquals(2L, finalRecord.seq());
      assertEquals(pending.hash(), finalRecord.previousHash());
      assertEquals("req-1", finalRecord.requestId());
      assertEquals(2L, store.lastSequence());

      AuditVerificationReport report = store.verify();
      assertTrue(report.ok());
      assertEquals(1, report.formatVersion());
      assertEquals(2L, report.recordsChecked());
      assertEquals(finalRecord.hash(), report.lastHash());
      assertNull(report.firstFailure());
      assertTrue(
          report.limitations().contains(AuditVerificationReport.LIMITATION_NO_OFFHOST_ANCHOR));
    }

    try (AuditStore reopened = new AuditStore(directory, AuditConfig.defaults(), CLOCK)) {
      AuditVerificationReport report = reopened.verify();
      assertTrue(report.ok());
      assertNotNull(report.latestAnchor());
      assertEquals(AuditActions.AUDIT_ANCHOR, report.latestAnchor().action());
    }
  }

  @Test
  void cleanCloseAppendsACoveringAnchor() throws Exception {
    Path directory = tempDir.resolve("audit");
    AuditConfig config = AuditConfig.defaults();
    AuditStore store = new AuditStore(directory, config, CLOCK);
    store.append(event(AuditResult.SUCCESS, "req-2", 5L));
    store.close();

    try (AuditStore reopened = new AuditStore(directory, config, CLOCK)) {
      AuditVerificationReport report = reopened.verify();
      assertTrue(report.ok());
      assertNotNull(report.latestAnchor());
      assertEquals(AuditActions.AUDIT_ANCHOR, report.latestAnchor().action());
      assertEquals(Long.valueOf(1L), report.latestAnchor().details().get("first-seq"));
      assertEquals(Long.valueOf(1L), report.latestAnchor().details().get("last-seq"));
    }
  }

  @Test
  void queryFiltersCursorAndExportsVerbatimRecords() throws Exception {
    try (AuditStore store =
        new AuditStore(tempDir.resolve("audit"), AuditConfig.defaults(), CLOCK)) {
      store.append(event(AuditResult.SUCCESS, "req-a", 1L));
      store.append(event(AuditResult.FAILED, "req-b", 2L));
      store.append(event(AuditResult.SUCCESS, "req-c", 3L));

      AuditPage firstPage = store.query(Collections.singletonMap("limit", "1"));
      assertEquals(1, firstPage.items().size());
      assertEquals(1L, firstPage.items().get(0).seq());
      assertEquals(3L, firstPage.total());
      assertNotNull(firstPage.nextCursor());

      AuditPage secondPage =
          store.query(Collections.singletonMap("cursor", firstPage.nextCursor()));
      assertEquals(2, secondPage.items().size());
      assertEquals(2L, secondPage.items().get(0).seq());
      assertEquals(3L, secondPage.items().get(1).seq());
      assertEquals(3L, secondPage.total());

      Map<String, String> resultFilter = new LinkedHashMap<String, String>();
      resultFilter.put("result", "success");
      assertEquals(2L, store.query(resultFilter).total());

      Map<String, String> actorFilter = new LinkedHashMap<String, String>();
      actorFilter.put("actor", "alice");
      assertEquals(3L, store.query(actorFilter).total());

      String exported = store.export(Collections.<String, String>emptyMap());
      assertEquals(3, exported.split("\n", -1).length - 1);
      assertTrue(exported.contains("\"action\":\"files.write\""));
    }
  }

  @Test
  void recordAccessStoresOnlyFilterSummaryAndRowCount() throws Exception {
    try (AuditStore store =
        new AuditStore(tempDir.resolve("audit"), AuditConfig.defaults(), CLOCK)) {
      store.append(event(AuditResult.SUCCESS, "req-source", 1L));
      AuditQuery query = AuditQuery.parse(Collections.singletonMap("action", "files.write"));
      store.recordAccess(
          AuditActor.of("alice", "token", Collections.singletonList("developer"), null),
          "req-audit",
          AuditActions.AUDIT_READ,
          "success",
          query,
          1);

      AuditPage page = store.query(Collections.singletonMap("action", "audit.read"));
      assertEquals(1, page.items().size());
      @SuppressWarnings("unchecked")
      Map<String, Object> details = page.items().get(0).details();
      assertEquals(Long.valueOf(1L), details.get("row-count"));
      assertTrue(details.get("filter") instanceof Map);
      @SuppressWarnings("unchecked")
      Map<String, Object> filter = (Map<String, Object>) details.get("filter");
      assertEquals("files.write", filter.get("action"));
    }
  }

  @Test
  void sensitiveDetailsAreRejectedBeforeAnyLineIsWritten() throws Exception {
    try (AuditStore store =
        new AuditStore(tempDir.resolve("audit"), AuditConfig.defaults(), CLOCK)) {
      Map<String, Object> details = new LinkedHashMap<String, Object>();
      details.put("token-digest", "sha256:fake");
      AuditEvent event =
          AuditEvent.builder()
              .actor(AuditActor.of("alice", "token", Collections.singletonList("developer"), null))
              .action(AuditActions.FILES_WRITE)
              .result(AuditResult.SUCCESS)
              .details(details)
              .build();

      AuditException exception = assertThrows(AuditException.class, () -> store.append(event));

      assertEquals("audit.write-failed", exception.problemCode());
      assertEquals(0L, store.lastSequence());
      assertTrue(store.verify().ok());
    }
  }

  @Test
  void invalidQueryInputsFailClosed() throws Exception {
    try (AuditStore store =
        new AuditStore(tempDir.resolve("audit"), AuditConfig.defaults(), CLOCK)) {
      assertEquals(
          "request.invalid",
          assertThrows(
                  AuditException.class, () -> store.query(Collections.singletonMap("limit", "501")))
              .problemCode());
      assertEquals(
          "request.invalid-cursor",
          assertThrows(
                  AuditException.class,
                  () -> store.query(Collections.singletonMap("cursor", "not-a-cursor")))
              .problemCode());
    }
  }

  private static AuditEvent event(AuditResult result, String requestId, long bytes) {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("bytes", Long.valueOf(bytes));
    return AuditEvent.builder()
        .actor(AuditActor.of("alice", "token", Arrays.asList("developer", "admin"), null))
        .action(AuditActions.FILES_WRITE)
        .target(AuditTarget.path("plugins/Example/config.yml"))
        .result(result)
        .requestId(requestId)
        .details(details)
        .build();
  }
}
