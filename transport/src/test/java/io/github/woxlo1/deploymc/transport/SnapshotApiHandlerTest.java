package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.files.FileRevision;
import io.github.woxlo1.deploymc.core.files.SnapshotEntry;
import io.github.woxlo1.deploymc.core.files.SnapshotRequest;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotRecord;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotApiHandlerTest {

  private static final Identity ACTOR =
      new Identity("token-admin", "administrator", "token", true, Collections.<String>emptyList());

  @TempDir Path tempDir;

  private SnapshotStore snapshots;
  private SnapshotApiHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    SandboxResolver resolver = new SandboxResolver(tempDir);
    snapshots = SnapshotStore.open(tempDir, resolver, SnapshotStoreConfig.defaults());
    handler = new SnapshotApiHandler(resolver, snapshots, Locale.ENGLISH, 2 * 1024 * 1024);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (snapshots != null) {
      snapshots.close();
    }
  }

  @Test
  void listsHistoryWithOpaqueMetadataAndCursor() throws Exception {
    seed("config.yml", "a: 1\n");
    seed("config.yml", "a: 2\n");

    HttpResponse response =
        handler.handle(request("GET", "/api/v1/snapshots", "path=config.yml&limit=1", null, null));

    assertEquals(200, response.status());
    YamlValue root = parse(response);
    assertEquals("config.yml", root.get("path").raw());
    assertEquals(2L, root.get("total").raw());
    assertEquals(1, root.get("items").items().size());
    YamlValue item = root.get("items").items().get(0);
    assertTrue(((String) item.get("id").raw()).matches("^snap_[0-9a-z]{26}$"));
    assertEquals("config.yml", item.get("path").raw());
    assertEquals("file", item.get("kind").raw());
    assertNotNull(item.get("hash").raw());
    assertNotNull(item.get("createdAt").raw());
    String cursor = (String) root.get("nextCursor").raw();
    assertNotNull(cursor);

    HttpResponse second =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots",
                "path=config.yml&limit=1&cursor=" + cursor,
                null,
                null));
    assertEquals(200, second.status());
    assertNull(parse(second).get("nextCursor").raw());

    HttpResponse clamped =
        handler.handle(
            request("GET", "/api/v1/snapshots", "path=config.yml&limit=501", null, null));
    assertEquals(200, clamped.status());
    assertEquals(2, parse(clamped).get("items").items().size());

    HttpResponse invalid =
        handler.handle(request("GET", "/api/v1/snapshots", "path=config.yml&limit=0", null, null));
    assertEquals(400, invalid.status());
    assertEquals("request.invalid", parse(invalid).get("code").raw());
  }

  @Test
  void readsMetadataWithQuotedETagAndConditionalResponse() throws Exception {
    String id = seed("config.yml", "a: 1\n");
    String etag = "\"" + id + "\"";

    HttpResponse response =
        handler.handle(request("GET", "/api/v1/snapshots/" + id, "", null, null));

    assertEquals(200, response.status());
    assertEquals(etag, header(response, "etag"));
    assertEquals(id, parse(response).get("id").raw());
    assertEquals("system", parse(response).get("actor").raw());

    HttpResponse conditional =
        handler.handle(
            request("GET", "/api/v1/snapshots/" + id, "", null, null, "if-none-match", etag));
    assertEquals(304, conditional.status());
    assertEquals(etag, header(conditional, "etag"));
    assertEquals(0, conditional.body().length);
  }

  @Test
  void readsExactContentWithRangesAndConditionalResponse() throws Exception {
    String id = seed("config.yml", "a: 1\nb: 2\n");
    SnapshotRecord record = snapshots.read(id);

    HttpResponse response =
        handler.handle(request("GET", "/api/v1/snapshots/" + id + "/content", "", null, null));

    assertEquals(200, response.status());
    assertArrayEquals("a: 1\nb: 2\n".getBytes(StandardCharsets.UTF_8), response.body());
    assertEquals("\"" + record.hash() + "\"", header(response, "etag"));
    assertEquals("bytes", header(response, "accept-ranges"));

    HttpResponse ranged =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/" + id + "/content",
                "",
                null,
                null,
                "range",
                "bytes=3-5"));
    assertEquals(206, ranged.status());
    assertArrayEquals("1\n".getBytes(StandardCharsets.UTF_8), ranged.body());
    assertEquals("bytes 3-4/10", header(ranged, "content-range"));

    HttpResponse invalid =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/" + id + "/content",
                "",
                null,
                null,
                "range",
                "bytes=6-3"));
    assertEquals(416, invalid.status());
    assertEquals("files.invalid-range", parse(invalid).get("code").raw());

    HttpResponse conditional =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/" + id + "/content",
                "",
                null,
                null,
                "if-none-match",
                "\"" + record.hash() + "\""));
    assertEquals(304, conditional.status());
  }

  @Test
  void comparesSnapshotsWithBoundedEnvelope() throws Exception {
    String a = seed("config.yml", "a: 1\n");
    String b = seed("config.yml", "a: 2\n");

    HttpResponse response =
        handler.handle(
            request(
                "GET", "/api/v1/snapshots/diff", "a=" + a + "&b=" + b + "&mode=text", null, null));

    assertEquals(200, response.status());
    YamlValue root = parse(response);
    assertEquals(a, root.get("a").raw());
    assertEquals(b, root.get("b").raw());
    assertEquals("changed", root.get("status").raw());
    assertNotNull(root.get("lineHunks"));
    assertNotNull(root.get("wordDiffs"));
    assertNotNull(root.get("semanticChanges"));
    assertNull(root.get("reason"));

    HttpResponse invalidMode =
        handler.handle(
            request(
                "GET", "/api/v1/snapshots/diff", "a=" + a + "&b=" + b + "&mode=bogus", null, null));
    assertEquals(400, invalidMode.status());
    assertEquals("request.invalid", parse(invalidMode).get("code").raw());

    HttpResponse missing =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/diff",
                "a=" + a + "&b=snap_00000000000000000000000000",
                null,
                null));
    assertEquals(404, missing.status());
    assertEquals("snapshots.not-found", parse(missing).get("code").raw());
  }

  @Test
  void rejectsDiffInputsOverTheConfiguredBoundBeforeReadingBlobs() throws Exception {
    String a = seed("large-a.txt", repeat('a', 1025));
    String b = seed("large-b.txt", repeat('b', 1025));
    SnapshotApiHandler bounded =
        new SnapshotApiHandler(new SandboxResolver(tempDir), snapshots, Locale.ENGLISH, 1024);

    HttpResponse response =
        bounded.handle(request("GET", "/api/v1/snapshots/diff", "a=" + a + "&b=" + b, null, null));

    assertEquals(413, response.status());
    assertEquals("diff.too-large", parse(response).get("code").raw());
  }

  @Test
  void restoresOnlyRecordedPathWithIdentityActorAndPrecondition() throws Exception {
    String oldId = seed("config.yml", "old");
    Files.write(tempDir.resolve("config.yml"), "new".getBytes(StandardCharsets.UTF_8));
    String currentRevision = FileRevision.of("new".getBytes(StandardCharsets.UTF_8));

    HttpResponse missing =
        handler.handle(
            request("POST", "/api/v1/snapshots/" + oldId + "/restore", "", new byte[0], ACTOR));
    assertEquals(428, missing.status());
    assertEquals("request.precondition-required", parse(missing).get("code").raw());

    HttpResponse stale =
        handler.handle(
            request(
                "POST",
                "/api/v1/snapshots/" + oldId + "/restore",
                "",
                new byte[0],
                ACTOR,
                "if-match",
                FileRevision.of("old".getBytes(StandardCharsets.UTF_8))));
    assertEquals(412, stale.status());
    assertEquals("snapshots.restore-conflict", parse(stale).get("code").raw());

    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/snapshots/" + oldId + "/restore",
                "",
                new byte[0],
                ACTOR,
                "if-match",
                currentRevision));
    assertEquals(200, response.status());
    YamlValue root = parse(response);
    assertEquals(oldId, root.get("restoredSnapshotId").raw());
    assertNotNull(root.get("preRestoreSnapshotId").raw());
    assertEquals(
        "old",
        new String(Files.readAllBytes(tempDir.resolve("config.yml")), StandardCharsets.UTF_8));

    SnapshotRecord pre = snapshots.read((String) root.get("preRestoreSnapshotId").raw());
    assertEquals(ACTOR.id(), pre.actor());
    assertEquals("restore", pre.reason().value());

    HttpResponse anonymous =
        handler.handle(
            request("POST", "/api/v1/snapshots/" + oldId + "/restore", "", new byte[0], null));
    assertEquals(500, anonymous.status());
    assertEquals("server.internal", parse(anonymous).get("code").raw());
    assertEquals(Boolean.TRUE, parse(anonymous).get("retryable").raw());
  }

  @Test
  void restoresAnAbsentTargetWithoutIfMatchAndPinsThePreRestoreDeletion() throws Exception {
    String id = seed("missing.yml", "old");
    Files.delete(tempDir.resolve("missing.yml"));

    HttpResponse response =
        handler.handle(
            request("POST", "/api/v1/snapshots/" + id + "/restore", "", new byte[0], ACTOR));

    assertEquals(200, response.status());
    assertEquals(
        "old",
        new String(Files.readAllBytes(tempDir.resolve("missing.yml")), StandardCharsets.UTF_8));
    SnapshotRecord pre =
        snapshots.read(parse(response).get("preRestoreSnapshotId").raw().toString());
    assertEquals("deleted", pre.kind().value());
    assertEquals(ACTOR.id(), pre.actor());
  }

  @Test
  void acceptsExactlyOnePruneTargetAndRejectsAmbiguousBodies() throws Exception {
    seed("config.yml", "a: 1\n");
    String id = seed("other.yml", "b: 2\n");

    HttpResponse byPath =
        handler.handle(
            request(
                "POST",
                "/api/v1/snapshots/prune",
                "",
                json("{\"path\":\"config.yml\"}"),
                ACTOR,
                "content-type",
                "application/json"));
    assertEquals(200, byPath.status());
    YamlValue report = parse(byPath);
    assertEquals(2L, report.get("considered").raw());
    assertEquals(2L, report.get("protectedRecords").raw());
    assertEquals(0L, report.get("pruned").raw());
    assertNotNull(report.get("storeBytesBefore").raw());

    HttpResponse byId =
        handler.handle(
            request(
                "POST",
                "/api/v1/snapshots/prune",
                "",
                json("{\"id\":\"" + id + "\"}"),
                ACTOR,
                "content-type",
                "application/json"));
    assertEquals(200, byId.status());

    HttpResponse both =
        handler.handle(
            request(
                "POST",
                "/api/v1/snapshots/prune",
                "",
                json("{\"path\":\"config.yml\",\"id\":\"" + id + "\"}"),
                ACTOR,
                "content-type",
                "application/json"));
    assertEquals(400, both.status());
    assertEquals("request.invalid", parse(both).get("code").raw());

    HttpResponse unknown =
        handler.handle(
            request(
                "POST",
                "/api/v1/snapshots/prune",
                "",
                json("{\"id\":\"snap_00000000000000000000000000\"}"),
                ACTOR,
                "content-type",
                "application/json"));
    assertEquals(404, unknown.status());
    assertEquals("snapshots.not-found", parse(unknown).get("code").raw());
  }

  @Test
  void rejectsHostPathsAndReturnsLocalizedProblems() throws Exception {
    HttpResponse traversal =
        handler.handle(request("GET", "/api/v1/snapshots", "path=../escape.txt", null, null));
    assertEquals(400, traversal.status());
    assertEquals("files.path-rejected", parse(traversal).get("code").raw());

    HttpResponse protectedPath =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots",
                "path=.deploymc%2Fhistory%2Fformat-version",
                null,
                null));
    assertEquals(403, protectedPath.status());
    assertEquals("files.protected", parse(protectedPath).get("code").raw());

    HttpResponse japanese =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/snap_00000000000000000000000000",
                "",
                null,
                null,
                "accept-language",
                "ja"));
    assertEquals(404, japanese.status());
    assertTrue(parse(japanese).get("title").raw().toString().contains("\u30b9\u30ca\u30c3\u30d7"));
    assertFalse(new String(japanese.body(), StandardCharsets.UTF_8).contains(tempDir.toString()));
  }

  @Test
  void negotiatesLocaleUsingAcceptLanguageQualityValues() throws Exception {
    HttpResponse japanese =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/snap_00000000000000000000000000",
                "",
                null,
                null,
                "accept-language",
                "en;q=0.2,ja;q=0.9"));
    assertEquals(404, japanese.status());
    assertTrue(parse(japanese).get("title").raw().toString().contains("\u30b9\u30ca\u30c3\u30d7"));

    HttpResponse english =
        handler.handle(
            request(
                "GET",
                "/api/v1/snapshots/snap_00000000000000000000000000",
                "",
                null,
                null,
                "accept-language",
                "ja;q=0.2,en;q=0.9"));
    assertEquals(404, english.status());
    assertEquals("Snapshot not found", parse(english).get("title").raw());
  }

  @Test
  void reportsDamagedBlobAsCorruptWithoutExposingContent() throws Exception {
    String id = seed("config.yml", "a: 1\n");
    SnapshotRecord record = snapshots.read(id);
    String hex = record.hashHex();
    Path blob =
        tempDir
            .resolve(".deploymc")
            .resolve("history")
            .resolve("objects")
            .resolve(hex.substring(0, 2))
            .resolve(hex.substring(2));
    Files.write(blob, "tampered".getBytes(StandardCharsets.UTF_8));

    HttpResponse response =
        handler.handle(request("GET", "/api/v1/snapshots/" + id + "/content", "", null, null));

    assertEquals(409, response.status());
    assertEquals("snapshots.corrupt", parse(response).get("code").raw());
    assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains("tampered"));
  }

  @Test
  void rejectsUnknownRoutesAndWrongMethods() throws Exception {
    HttpResponse unknown =
        handler.handle(request("GET", "/api/v1/snapshots/diff/extra", "", null, null));
    assertEquals(404, unknown.status());

    HttpResponse method =
        handler.handle(request("POST", "/api/v1/snapshots", "", new byte[0], ACTOR));
    assertEquals(405, method.status());
    assertEquals("GET", header(method, "allow"));
  }

  private String seed(String path, String content) throws Exception {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    Files.createDirectories(tempDir.resolve(path).getParent());
    Files.write(tempDir.resolve(path), bytes);
    snapshots.capture(
        new SnapshotRequest(
            tempDir.toString(),
            "before-overwrite",
            Collections.singletonList(SnapshotEntry.file(path, bytes, FileRevision.of(bytes)))));
    List<SnapshotRecord> records = snapshots.findByPath(path);
    return records.get(records.size() - 1).id();
  }

  private static YamlValue parse(HttpResponse response) throws ConfigException {
    return ConfigYamlParser.parseSingleDocument(
        new String(response.body(), StandardCharsets.UTF_8));
  }

  private static String header(HttpResponse response, String name) {
    List<String> values = response.headers().get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static HttpRequest request(
      String method,
      String path,
      String query,
      byte[] body,
      Identity identity,
      String... headerPairs) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    for (int index = 0; index < headerPairs.length; index += 2) {
      List<String> values = new ArrayList<String>();
      values.add(headerPairs[index + 1]);
      headers.put(headerPairs[index].toLowerCase(Locale.ROOT), values);
    }
    return new HttpRequest(
        "req-00000001", method, path, query, headers, body == null ? new byte[0] : body, identity);
  }

  private static byte[] json(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String repeat(char value, int count) {
    StringBuilder text = new StringBuilder(count);
    for (int index = 0; index < count; index++) {
      text.append(value);
    }
    return text.toString();
  }
}
