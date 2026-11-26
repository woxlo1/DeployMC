package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.audit.AuditActions;
import io.github.woxlo1.deploymc.core.audit.AuditConfig;
import io.github.woxlo1.deploymc.core.audit.AuditQuery;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
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

/** Server-free tests for fail-closed pending and final audit records around mutations. */
class AuditMutationHandlerTest {

  private static final Identity ACTOR =
      new Identity("owner", "owner", "cloudflare-access", false, Collections.<String>emptyList());
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-26T00:00:00.000Z"), ZoneOffset.UTC);

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
    closeStore();
  }

  @Test
  void commitsPendingBeforeDelegateAndSuccessAfterwards() throws Exception {
    AtomicBoolean invoked = new AtomicBoolean(false);
    AuditMutationHandler handler =
        new AuditMutationHandler(
            request -> {
              invoked.set(true);
              assertEquals(AuditResult.PENDING.wire(), writeRecords().get(0).result().wire());
              return HttpResponse.status(201);
            },
            AuditRecorder.of(store, RbacTestSupport.permitAll()));

    HttpResponse response = handler.handle(createRequest());

    assertEquals(201, response.status());
    assertTrue(invoked.get());
    List<AuditRecord> records = writeRecords();
    assertEquals(2, records.size());
    assertEquals(AuditResult.PENDING, records.get(0).result());
    assertEquals(AuditResult.SUCCESS, records.get(1).result());
    assertEquals(AuditActions.FILES_WRITE, records.get(1).action());
    assertEquals("config.yml", records.get(1).target().path());
  }

  @Test
  void commitsFailedRecordWhenDelegateThrows() throws Exception {
    AuditMutationHandler handler =
        new AuditMutationHandler(
            request -> {
              throw new IllegalStateException("delegate failure");
            },
            AuditRecorder.of(store, RbacTestSupport.permitAll()));

    assertThrows(IllegalStateException.class, () -> handler.handle(createRequest()));

    List<AuditRecord> records = writeRecords();
    assertEquals(2, records.size());
    assertEquals(AuditResult.PENDING, records.get(0).result());
    assertEquals(AuditResult.FAILED, records.get(1).result());
    assertEquals("handler-exception", records.get(1).reason());
  }

  @Test
  void rejectsMutationWithoutInvokingDelegateWhenPendingCannotBeWritten() throws Exception {
    AuditStore closed = store;
    closeStore();
    AtomicBoolean invoked = new AtomicBoolean(false);
    AuditMutationHandler handler =
        new AuditMutationHandler(
            request -> {
              invoked.set(true);
              return HttpResponse.status(201);
            },
            AuditRecorder.of(closed, RbacTestSupport.permitAll()));

    HttpResponse response = handler.handle(createRequest());

    assertEquals(503, response.status());
    assertEquals("audit.unavailable", code(response));
    assertFalse(invoked.get());
  }

  @Test
  void rejectsMutationWhenFinalRecordCannotBeWritten() throws Exception {
    AuditMutationHandler handler =
        new AuditMutationHandler(
            request -> {
              closeStore();
              return HttpResponse.status(201);
            },
            AuditRecorder.of(store, RbacTestSupport.permitAll()));

    HttpResponse response = handler.handle(createRequest());

    assertEquals(503, response.status());
    assertEquals("audit.unavailable", code(response));
  }

  private List<AuditRecord> writeRecords() throws Exception {
    List<AuditRecord> all = store.query(AuditQuery.defaults()).items();
    List<AuditRecord> writes = new java.util.ArrayList<AuditRecord>();
    for (AuditRecord record : all) {
      if (AuditActions.FILES_WRITE.equals(record.action())) {
        writes.add(record);
      }
    }
    return writes;
  }

  private void closeStore() throws Exception {
    if (store != null) {
      AuditStore closing = store;
      store = null;
      closing.close();
    }
  }

  private static HttpRequest createRequest() {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("accept-language", Arrays.asList("en"));
    byte[] body =
        ("{\"path\":\"config.yml\",\"kind\":\"file\",\"content\":\"a: 1\\n\"}")
            .getBytes(StandardCharsets.UTF_8);
    return new HttpRequest("req-00000001", "POST", "/api/v1/files", "", headers, body, ACTOR);
  }

  private static String code(HttpResponse response) throws Exception {
    return ConfigYamlParser.parseSingleDocument(new String(response.body(), StandardCharsets.UTF_8))
        .get("code")
        .raw()
        .toString();
  }
}
