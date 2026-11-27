package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.audit.AuditActions;
import io.github.woxlo1.deploymc.core.audit.AuditActor;
import io.github.woxlo1.deploymc.core.audit.AuditConfig;
import io.github.woxlo1.deploymc.core.audit.AuditQuery;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.auth.Identity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free coverage for the public seams used by product host wiring. */
class AuditRecorderHostSeamTest {

  private static final Identity ACTOR =
      new Identity("owner", "owner", "cloudflare-access", false, Collections.<String>emptyList());
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-27T00:00:00.000Z"), ZoneOffset.UTC);

  @TempDir Path tempDir;

  private AuditStore store;

  @BeforeEach
  void setUp() throws Exception {
    store =
        AuditStore.open(
            tempDir,
            AuditConfig.of("audit", 1024L * 1024L, 365, AuditConfig.FsyncPolicy.RECORD, 1),
            CLOCK);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (store != null) {
      AuditStore closing = store;
      store = null;
      closing.close();
    }
  }

  @Test
  void wrapMutationsCommitsPendingAndFinalRecords() throws Exception {
    AuditRecorder recorder = AuditRecorder.of(store, RbacTestSupport.permitAll());
    AtomicBoolean invoked = new AtomicBoolean(false);
    HttpHandler wrapped =
        recorder.wrapMutations(
            request -> {
              invoked.set(true);
              return HttpResponse.status(201);
            });

    HttpResponse response = wrapped.handle(createRequest());

    assertEquals(201, response.status());
    assertTrue(invoked.get());
    List<AuditRecord> writes = writeRecords();
    assertEquals(2, writes.size());
    assertEquals(AuditResult.PENDING, writes.get(0).result());
    assertEquals(AuditResult.SUCCESS, writes.get(1).result());
    assertEquals("config.yml", writes.get(1).target().path());
  }

  @Test
  void wrapMutationsFailsClosedBeforeTheDelegateWhenPendingCannotBeWritten() throws Exception {
    AuditStore closed = store;
    store = null;
    closed.close();
    AuditRecorder recorder = AuditRecorder.of(closed, RbacTestSupport.permitAll());
    AtomicBoolean invoked = new AtomicBoolean(false);
    HttpHandler wrapped =
        recorder.wrapMutations(
            request -> {
              invoked.set(true);
              return HttpResponse.status(201);
            });

    HttpResponse response = wrapped.handle(createRequest());

    assertEquals(503, response.status());
    assertFalse(invoked.get());
  }

  @Test
  void systemBeginAndCompletionMergeFinalDetails() throws Exception {
    AuditRecorder recorder = AuditRecorder.of(store, RbacTestSupport.permitAll());
    Map<String, Object> pendingDetails = new LinkedHashMap<String, Object>();
    pendingDetails.put("subcommand", "reload");
    AuditRecord pending = recorder.beginSystem(AuditActions.CONFIG_RELOAD, null, pendingDetails);
    assertNotNull(pending);
    Map<String, Object> finalDetails = new LinkedHashMap<String, Object>();
    finalDetails.put("changed-paths", Arrays.asList("language", "bind.port"));

    recorder.complete(pending, AuditResult.SUCCESS, null, finalDetails);

    List<AuditRecord> records = configReloadRecords();
    assertEquals(2, records.size());
    assertEquals(AuditActor.system().id(), records.get(0).actor().id());
    assertEquals("reload", records.get(1).details().get("subcommand"));
    assertEquals(
        Arrays.asList("language", "bind.port"), records.get(1).details().get("changed-paths"));
  }

  @Test
  void disabledRecorderLeavesTheDelegateUnwrappedForServerFreeTests() {
    HttpHandler delegate = request -> HttpResponse.status(200);

    assertSame(delegate, AuditRecorder.disabledForServerFreeTests().wrapMutations(delegate));
  }

  private List<AuditRecord> writeRecords() throws Exception {
    return recordsWithAction(AuditActions.FILES_WRITE);
  }

  private List<AuditRecord> configReloadRecords() throws Exception {
    return recordsWithAction(AuditActions.CONFIG_RELOAD);
  }

  private List<AuditRecord> recordsWithAction(String action) throws Exception {
    List<AuditRecord> records = new java.util.ArrayList<AuditRecord>();
    for (AuditRecord record : store.query(AuditQuery.defaults()).items()) {
      if (action.equals(record.action())) {
        records.add(record);
      }
    }
    return records;
  }

  private static HttpRequest createRequest() {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("accept-language", Arrays.asList("en"));
    byte[] body =
        ("{\"path\":\"config.yml\",\"kind\":\"file\",\"content\":\"a: 1\\n\"}")
            .getBytes(StandardCharsets.UTF_8);
    return new HttpRequest("req-seam", "POST", "/api/v1/files", "", headers, body, ACTOR);
  }
}
