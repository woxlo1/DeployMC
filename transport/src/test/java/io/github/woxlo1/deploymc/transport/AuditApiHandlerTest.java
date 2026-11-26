package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.audit.AuditActor;
import io.github.woxlo1.deploymc.core.audit.AuditConfig;
import io.github.woxlo1.deploymc.core.audit.AuditEvent;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.audit.AuditTarget;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditApiHandlerTest {

  private static final Identity ACTOR =
      new Identity("owner", "owner", "cloudflare-access", false, Collections.<String>emptyList());
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-26T00:00:00.000Z"), ZoneOffset.UTC);

  @TempDir Path tempDir;

  private AuditStore store;
  private AuditApiHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    AuditConfig config =
        AuditConfig.of("audit", 1024L * 1024L, 365, AuditConfig.FsyncPolicy.RECORD, 1);
    store = AuditStore.open(tempDir, config, CLOCK);
    handler =
        new AuditApiHandler(
            store, AuditRecorder.of(store, RbacTestSupport.permitAll()), Locale.ENGLISH);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (store != null) {
      store.close();
    }
  }

  @Test
  void queriesRecordsAndRecordsTheAccessItself() throws Exception {
    seed("files.write");

    HttpResponse response = handler.handle(request("GET", AuditApiHandler.PATH, "", null));

    assertEquals(200, response.status());
    YamlValue root = parse(response);
    assertEquals(1L, root.get("total").raw());
    assertEquals(1, root.get("items").items().size());
    assertEquals("files.write", root.get("items").items().get(0).get("action").raw());
    assertNotNull(header(response, "x-request-id"));
  }

  @Test
  void exportsVerbatimJsonLinesWithDownloadHeaders() throws Exception {
    seed("files.write");

    HttpResponse response = handler.handle(request("GET", AuditApiHandler.EXPORT_PATH, "", null));

    assertEquals(200, response.status());
    assertEquals(
        "attachment; filename=\"audit-export.ndjson\"", header(response, "content-disposition"));
    String body = new String(response.body(), StandardCharsets.UTF_8);
    assertTrue(body.contains("\"action\":\"files.write\""));
    assertTrue(body.endsWith("\n"));
  }

  @Test
  void verifiesTheChainAndRecordsTheAccess() throws Exception {
    seed("files.write");

    HttpResponse response = handler.handle(request("GET", AuditApiHandler.VERIFY_PATH, "", null));

    assertEquals(200, response.status());
    YamlValue root = parse(response);
    assertEquals(Boolean.TRUE, root.get("ok").raw());
    assertTrue(((List<?>) root.get("limitations").raw()).size() >= 2);
    assertEquals(
        "audit.read",
        parse(handler.handle(request("GET", AuditApiHandler.PATH, "action=audit.read", null)))
            .get("items")
            .items()
            .get(0)
            .get("action")
            .raw());
  }

  @Test
  void rejectsInvalidFiltersAndCursorsWithoutLeakingStoreDetails() throws Exception {
    HttpResponse invalid =
        handler.handle(request("GET", AuditApiHandler.PATH, "action=NOT_VALID", null));
    assertEquals(400, invalid.status());
    assertEquals("request.invalid", parse(invalid).get("code").raw());

    HttpResponse cursor =
        handler.handle(request("GET", AuditApiHandler.PATH, "cursor=not-a-cursor", null));
    assertEquals(400, cursor.status());
    assertEquals("request.invalid-cursor", parse(cursor).get("code").raw());
  }

  @Test
  void rejectsUnsupportedMethods() throws Exception {
    HttpResponse response = handler.handle(request("POST", AuditApiHandler.PATH, "", null));
    assertEquals(405, response.status());
    assertEquals("GET", header(response, "allow"));
    assertEquals("request.method-not-allowed", parse(response).get("code").raw());
  }

  private void seed(String action) throws Exception {
    store.append(
        AuditEvent.builder()
            .timestamp(CLOCK.instant())
            .actor(AuditActor.fromIdentity(ACTOR, RbacTestSupport.permitAll(), null))
            .action(action)
            .target(AuditTarget.path("config.yml"))
            .result(AuditResult.SUCCESS)
            .requestId("seed-request")
            .details(Collections.<String, Object>emptyMap())
            .build());
  }

  private static HttpRequest request(String method, String path, String query, byte[] body) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("accept-language", Arrays.asList("en"));
    return new HttpRequest(
        "req-00000001", method, path, query, headers, body == null ? new byte[0] : body, ACTOR);
  }

  private static YamlValue parse(HttpResponse response)
      throws io.github.woxlo1.deploymc.core.config.ConfigException {
    return ConfigYamlParser.parseSingleDocument(
        new String(response.body(), StandardCharsets.UTF_8));
  }

  private static String header(HttpResponse response, String name) {
    List<String> values = response.headers().get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }
}
