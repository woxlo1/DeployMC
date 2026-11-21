package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.files.FileOperationLimits;
import io.github.woxlo1.deploymc.core.files.FileOperationsService;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreConfig;
import io.github.woxlo1.deploymc.core.validation.ValidatorRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileApiHandlerTest {

  @TempDir Path tempDir;

  private SnapshotStore snapshots;
  private FileOperationsService files;
  private FileApiHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    SandboxResolver resolver = new SandboxResolver(tempDir);
    snapshots = SnapshotStore.open(tempDir, resolver, SnapshotStoreConfig.defaults());
    ValidatorRegistry validators = ValidatorRegistry.withBuiltIns();
    files =
        new FileOperationsService(resolver, FileOperationLimits.defaults(), snapshots, validators);
    handler = new FileApiHandler(resolver, files, validators, Locale.ENGLISH, 2 * 1024 * 1024);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (snapshots != null) {
      snapshots.close();
    }
  }

  @Test
  void createsFileWithValidationEtagAndLocation() throws Exception {
    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/files",
                "",
                json(createBody("config.yml", "file", "a: 1\n")),
                "content-type",
                "application/json"));

    assertEquals(201, response.status());
    YamlValue root = parse(response);
    YamlValue entry = root.get("entry");
    assertEquals("config.yml", entry.get("path").raw());
    assertEquals("file", entry.get("kind").raw());
    assertEquals(5L, entry.get("size").raw());
    assertTrue(((String) entry.get("revision").raw()).startsWith("sha256:"));
    assertTrue(header(response, "etag").startsWith("\"sha256:"));
    assertEquals("/api/v1/files/content?path=config.yml", header(response, "location"));
    YamlValue validation = root.get("validation");
    assertEquals(Boolean.TRUE, validation.get("matched").raw());
    assertEquals("yaml", validation.get("format").raw());
    assertEquals(Boolean.TRUE, validation.get("valid").raw());
    assertEquals(
        "a: 1\n",
        new String(Files.readAllBytes(tempDir.resolve("config.yml")), StandardCharsets.UTF_8));
  }

  @Test
  void rejectsSandboxRootMutationWithoutDisclosingHostPath() throws Exception {
    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/files",
                "",
                json(createBody("", "directory", null)),
                "content-type",
                "application/json"));

    assertProblem(response, 400, "files.invalid-operation");
    assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains(tempDir.toString()));
  }

  @Test
  void writeRequiresCurrentIfMatch() throws Exception {
    HttpResponse created =
        handler.handle(
            request(
                "POST",
                "/api/v1/files",
                "",
                json(createBody("note.txt", "file", "one")),
                "content-type",
                "application/json"));
    String revision = parse(created).get("entry").get("revision").raw().toString();

    HttpResponse missing =
        handler.handle(
            request(
                "PUT",
                "/api/v1/files/content",
                "path=note.txt",
                json("two"),
                "content-type",
                "application/octet-stream"));
    assertProblem(missing, 428, "request.precondition-required");

    HttpResponse stale =
        handler.handle(
            request(
                "PUT",
                "/api/v1/files/content",
                "path=note.txt",
                json("two"),
                "content-type",
                "application/octet-stream",
                "if-match",
                "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""));
    assertProblem(stale, 412, "files.revision-conflict");

    HttpResponse current =
        handler.handle(
            request(
                "PUT",
                "/api/v1/files/content",
                "path=note.txt",
                json("two"),
                "content-type",
                "application/octet-stream",
                "if-match",
                '"' + revision + '"'));
    assertEquals(200, current.status());
    YamlValue validation = parse(current).get("validation");
    assertEquals(Boolean.FALSE, validation.get("matched").raw());
    assertEquals(Boolean.TRUE, validation.get("valid").raw());
    assertEquals(
        "two", new String(Files.readAllBytes(tempDir.resolve("note.txt")), StandardCharsets.UTF_8));
  }

  @Test
  void readsFullContentRangesAndConditionalRequests() throws Exception {
    Files.write(tempDir.resolve("range.txt"), "abcdef".getBytes(StandardCharsets.US_ASCII));

    HttpResponse full =
        handler.handle(request("GET", "/api/v1/files/content", "path=range.txt", new byte[0]));
    assertEquals(200, full.status());
    assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII), full.body());
    assertEquals("bytes", header(full, "accept-ranges"));
    String etag = header(full, "etag");

    HttpResponse partial =
        handler.handle(
            request(
                "GET",
                "/api/v1/files/content",
                "path=range.txt",
                new byte[0],
                "range",
                "bytes=2-5"));
    assertEquals(206, partial.status());
    assertArrayEquals("cde".getBytes(StandardCharsets.US_ASCII), partial.body());
    assertEquals("bytes 2-4/6", header(partial, "content-range"));
    assertEquals(etag, header(partial, "etag"));

    HttpResponse cached =
        handler.handle(
            request(
                "GET",
                "/api/v1/files/content",
                "path=range.txt",
                new byte[0],
                "if-none-match",
                etag));
    assertEquals(304, cached.status());
    assertEquals(0, cached.body().length);
    assertEquals(etag, header(cached, "etag"));

    HttpResponse invalid =
        handler.handle(
            request(
                "GET",
                "/api/v1/files/content",
                "path=range.txt",
                new byte[0],
                "range",
                "bytes=99-100"));
    assertProblem(invalid, 416, "files.invalid-range");
  }

  @Test
  void listsEntriesWithOpaqueCursorAndRejectsTampering() throws Exception {
    files.create("a.txt", "a".getBytes(StandardCharsets.UTF_8));
    files.create("b.txt", "b".getBytes(StandardCharsets.UTF_8));
    files.mkdir("dir");

    HttpResponse first =
        handler.handle(request("GET", "/api/v1/files", "path=&depth=1&limit=1", new byte[0]));
    assertEquals(200, first.status());
    YamlValue page = parse(first);
    assertEquals("", page.get("path").raw());
    assertEquals(3L, page.get("total").raw());
    assertEquals(1, page.get("items").items().size());
    assertEquals("a.txt", page.get("items").items().get(0).get("path").raw());
    String cursor = page.get("nextCursor").raw().toString();

    HttpResponse second =
        handler.handle(
            request("GET", "/api/v1/files", "path=&depth=1&limit=1&cursor=" + cursor, new byte[0]));
    assertEquals(200, second.status());
    assertEquals("b.txt", parse(second).get("items").items().get(0).get("path").raw());

    String tampered = cursor.substring(0, cursor.length() - 1) + (cursor.endsWith("A") ? "B" : "A");
    HttpResponse badCursor =
        handler.handle(
            request(
                "GET", "/api/v1/files", "path=&depth=1&limit=1&cursor=" + tampered, new byte[0]));
    assertProblem(badCursor, 400, "request.invalid-cursor");
  }

  @Test
  void recursiveDeleteRequiresMatchingEntryCount() throws Exception {
    files.mkdir("tree");
    files.create("tree/child.txt", "child".getBytes(StandardCharsets.UTF_8));

    HttpResponse missing =
        handler.handle(request("DELETE", "/api/v1/files", "path=tree&recursive=true", new byte[0]));
    assertProblem(missing, 400, "request.invalid");

    HttpResponse wrong =
        handler.handle(
            request(
                "DELETE",
                "/api/v1/files",
                "path=tree&recursive=true&expectedEntryCount=1",
                new byte[0]));
    assertProblem(wrong, 409, "files.delete-confirmation-required");

    HttpResponse deleted =
        handler.handle(
            request(
                "DELETE",
                "/api/v1/files",
                "path=tree&recursive=true&expectedEntryCount=2",
                new byte[0]));
    assertEquals(204, deleted.status());
    assertEquals(0, deleted.body().length);
    assertFalse(Files.exists(tempDir.resolve("tree")));
  }

  @Test
  void moveRejectsSandboxRootAsSource() throws Exception {
    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/files/move",
                "",
                json(moveBody("", "moved", false)),
                "content-type",
                "application/json"));

    assertProblem(response, 400, "files.invalid-operation");
  }

  @Test
  void validatesInvalidYamlWithNegotiatedJapaneseFindings() throws Exception {
    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/files/validate",
                "",
                json(validateBody("config.yml", "a: [1\n")),
                "content-type",
                "application/json",
                "accept-language",
                "ja,en;q=0.8"));

    assertProblem(response, 422, "validation.failed");
    YamlValue issue = parse(response).get("errors").items().get(0);
    assertEquals("ERROR", issue.get("severity").raw());
    assertNotNull(issue.get("code").raw());
    String detail = issue.get("detail").raw().toString();
    assertTrue(detail.length() > 0);
    assertFalse("YAML syntax error.".equals(detail));
  }

  @Test
  void mapsStructuralValidationLimitToTopLevelValidationCode() throws Exception {
    StringBuilder nested = new StringBuilder();
    for (int index = 0; index < 105; index++) {
      nested.append('[');
    }
    nested.append('0');
    for (int index = 0; index < 105; index++) {
      nested.append(']');
    }

    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/files/validate",
                "",
                json(validateBody("deep.json", nested.toString())),
                "content-type",
                "application/json"));

    assertProblem(response, 413, "validation.limit-exceeded");
    assertEquals(
        "validation.json-depth", parse(response).get("errors").items().get(0).get("code").raw());
  }

  @Test
  void rejectsUnsupportedMediaTypeAndMalformedJson() throws Exception {
    HttpResponse wrongType =
        handler.handle(
            request(
                "POST",
                "/api/v1/files",
                "",
                json(createBody("note.txt", "file", "x")),
                "content-type",
                "text/plain"));
    assertProblem(wrongType, 415, "request.unsupported-media-type");

    HttpResponse malformed =
        handler.handle(
            request(
                "POST",
                "/api/v1/files",
                "",
                json("{\"path\":"),
                "content-type",
                "application/json"));
    assertProblem(malformed, 400, "request.invalid");
  }

  @Test
  void rejectsTraversalBeforeAnyFileAccess() throws Exception {
    HttpResponse response =
        handler.handle(
            request(
                "POST",
                "/api/v1/files",
                "",
                json(createBody("../escape.txt", "file", "x")),
                "content-type",
                "application/json"));

    assertProblem(response, 400, "files.path-rejected");
    assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains(tempDir.toString()));
  }

  @Test
  void rejectsUndefinedMethodsAndRoutes() throws Exception {
    HttpResponse method = handler.handle(request("GET", "/api/v1/files/move", "", new byte[0]));
    assertProblem(method, 405, "request.method-not-allowed");
    assertEquals("POST", header(method, "allow"));

    HttpResponse route = handler.handle(request("GET", "/api/v1/unknown", "", new byte[0]));
    assertProblem(route, 404, "files.not-found");
  }

  private static void assertProblem(HttpResponse response, int status, String code)
      throws Exception {
    assertEquals(status, response.status());
    assertEquals("application/problem+json; charset=utf-8", header(response, "content-type"));
    YamlValue problem = parse(response);
    assertEquals("urn:deploymc:error:" + code, problem.get("type").raw());
    assertEquals(code, problem.get("code").raw());
    assertEquals((long) status, problem.get("status").raw());
    assertEquals("req-00000001", problem.get("requestId").raw());
    assertNotNull(problem.get("retryable").raw());
  }

  private static YamlValue parse(HttpResponse response) throws Exception {
    return ConfigYamlParser.parseSingleDocument(
        new String(response.body(), StandardCharsets.UTF_8));
  }

  private static String header(HttpResponse response, String name) {
    List<String> values = response.headers().get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static HttpRequest request(
      String method, String path, String query, byte[] body, String... headerPairs) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    for (int index = 0; index < headerPairs.length; index += 2) {
      List<String> values = new ArrayList<String>();
      values.add(headerPairs[index + 1]);
      headers.put(headerPairs[index].toLowerCase(Locale.ROOT), values);
    }
    return new HttpRequest("req-00000001", method, path, query, headers, body);
  }

  private static byte[] json(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String createBody(String path, String kind, String content) {
    StringBuilder body =
        new StringBuilder("{\"path\":")
            .append(jsonString(path))
            .append(",\"kind\":")
            .append(jsonString(kind));
    if (content != null) {
      body.append(",\"content\":").append(jsonString(content));
    }
    return body.append('}').toString();
  }

  private static String moveBody(String from, String to, boolean overwrite) {
    return "{\"from\":"
        + jsonString(from)
        + ",\"to\":"
        + jsonString(to)
        + ",\"overwrite\":"
        + overwrite
        + "}";
  }

  private static String validateBody(String path, String content) {
    return "{\"path\":" + jsonString(path) + ",\"content\":" + jsonString(content) + "}";
  }

  private static String jsonString(String value) {
    StringBuilder result = new StringBuilder(value.length() + 2).append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        result.append('\\').append(character);
      } else if (character == '\n') {
        result.append("\\n");
      } else if (character == '\r') {
        result.append("\\r");
      } else if (character == '\t') {
        result.append("\\t");
      } else if (character < 0x20) {
        result.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character)));
      } else {
        result.append(character);
      }
    }
    return result.append('"').toString();
  }
}
