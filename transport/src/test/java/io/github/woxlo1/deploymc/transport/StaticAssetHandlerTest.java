package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class StaticAssetHandlerTest {

  private static final String HASHED_ASSET = "/assets/index-abcdef12.js";

  @Test
  void servesIndexWithHtmlContentTypeAndWorkerCsp() throws Exception {
    HttpResponse response = handler(null).handle(request("/"));

    assertEquals(200, response.status());
    assertTrue(text(response).contains("WEB_INDEX"));
    assertEquals("text/html; charset=utf-8", header(response, "content-type"));
    assertEquals("no-store", header(response, "cache-control"));
    String csp = header(response, "content-security-policy");
    assertNotNull(csp);
    assertTrue(csp.contains("worker-src 'self' blob:"), csp);
    assertTrue(csp.contains("default-src 'self'"), csp);
  }

  @Test
  void usesImmutableCachingForHashedAssets() throws Exception {
    HttpResponse response = handler(null).handle(request(HASHED_ASSET));

    assertEquals(200, response.status());
    assertEquals("text/javascript; charset=utf-8", header(response, "content-type"));
    assertEquals("public, max-age=31536000, immutable", header(response, "cache-control"));
    assertEquals("Accept-Encoding", header(response, "vary"));
    assertTrue(text(response).contains("WEB_ASSET"));
  }

  @Test
  void servesPrecompressedGzipWhenAccepted() throws Exception {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("accept-encoding", Collections.singletonList("gzip, deflate"));
    HttpResponse response = handler(null).handle(request(HASHED_ASSET, headers));

    assertEquals(200, response.status());
    assertEquals("gzip", header(response, "content-encoding"));
    assertEquals("text/javascript; charset=utf-8", header(response, "content-type"));
    assertEquals("Accept-Encoding", header(response, "vary"));
    assertTrue(ungzip(response.body()).contains("WEB_ASSET"));
  }

  @Test
  void prefersBrotliWhenBothEncodingsAreAccepted() throws Exception {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("accept-encoding", Collections.singletonList("br, gzip"));
    HttpResponse response = handler(null).handle(request(HASHED_ASSET, headers));

    assertEquals(200, response.status());
    assertEquals("br", header(response, "content-encoding"));
    assertArrayEquals(resourceBytes("deploymc-web/assets/index-abcdef12.js.br"), response.body());
  }

  @Test
  void doesNotUseGzipWhenClientDoesNotAcceptIt() throws Exception {
    HttpResponse response = handler(null).handle(request(HASHED_ASSET));

    assertEquals(null, header(response, "content-encoding"));
  }

  @Test
  void fallsBackToIndexForClientSideRoutes() throws Exception {
    HttpResponse response = handler(null).handle(request("/settings/deployments"));

    assertEquals(200, response.status());
    assertEquals("no-store", header(response, "cache-control"));
    assertTrue(text(response).contains("WEB_INDEX"));
  }

  @Test
  void doesNotFallBackForMissingAssetsWithExtensions() throws Exception {
    HttpResponse response = handler(null).handle(request("/assets/missing.js"));

    assertEquals(404, response.status());
    assertFalse(text(response).contains("WEB_INDEX"));
  }

  @Test
  void delegatesApiPathsInsteadOfServingTheSpa() throws Exception {
    HttpHandler api = request -> HttpResponse.json(200, "{\"api\":true}");
    HttpResponse response = handler(api).handle(request("/api/v1/server"));

    assertEquals(200, response.status());
    assertTrue(text(response).contains("\"api\":true"));
  }

  @Test
  void returnsNotFoundForApiPathsWithoutADelegate() throws Exception {
    HttpResponse response = handler(null).handle(request("/api/v1/server"));

    assertEquals(404, response.status());
    assertFalse(text(response).contains("WEB_INDEX"));
  }

  @Test
  void rejectsRawAndEncodedTraversalWithoutLeakingOtherResources() throws Exception {
    String[] paths = {
      "/../plugin.yml",
      "/../../plugin.yml",
      "/%2e%2e/plugin.yml",
      "/%2E%2E%2Fplugin.yml",
      "/..%2fplugin.yml",
      "/%252e%252e%252fplugin.yml",
      "/assets/..%5c..%5cplugin.yml",
      "/assets/%2e%2e/plugin.yml",
      "/..\\plugin.yml",
      "/%00plugin.yml",
      "/../secret-plugin.yml"
    };
    for (String path : paths) {
      HttpResponse response = handler(null).handle(request(path));
      assertEquals(404, response.status(), path);
      assertFalse(text(response).contains("PLUGIN_YML_MARKER"), path);
      assertFalse(text(response).contains("SECRET_PLUGIN_YML"), path);
      assertFalse(text(response).contains("WEB_INDEX"), path);
    }
  }

  @Test
  void rejectsMalformedPercentEncoding() throws Exception {
    HttpResponse response = handler(null).handle(request("/assets/%zz.js"));

    assertEquals(404, response.status());
  }

  @Test
  void ignoresQueryStringsAndDoesNotTreatThemAsPartOfThePath() throws Exception {
    HttpResponse response = handler(null).handle(request("/assets/index-abcdef12.js?ignored=1"));

    assertEquals(200, response.status());
    assertTrue(text(response).contains("WEB_ASSET"));
  }

  private static StaticAssetHandler handler(HttpHandler api) {
    return new StaticAssetHandler(api, StaticAssetHandlerTest.class.getClassLoader());
  }

  private static HttpRequest request(String path) {
    return request(path, Collections.emptyMap());
  }

  private static HttpRequest request(String path, Map<String, List<String>> headers) {
    int query = path.indexOf('?');
    String cleanPath = query < 0 ? path : path.substring(0, query);
    String cleanQuery = query < 0 ? "" : path.substring(query + 1);
    return new HttpRequest("test", "GET", cleanPath, cleanQuery, headers, new byte[0]);
  }

  private static String text(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static String header(HttpResponse response, String name) {
    List<String> values = response.headers().get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static byte[] resourceBytes(String resource) throws IOException {
    try (InputStream stream =
        StaticAssetHandlerTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IOException("missing test resource: " + resource);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static String ungzip(byte[] bytes) throws IOException {
    try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(bytes));
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
