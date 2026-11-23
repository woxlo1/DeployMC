package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.Identity;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MetricsHandlerTest {

  @Test
  void servesPrometheusTextToAdminIdentity() {
    MetricsRegistry registry = new MetricsRegistry();
    registry.recordRequest(EndpointClass.SERVER, 1_000_000L, 200);
    MetricsHandler handler = new MetricsHandler(registry, "metrics.read");

    HttpResponse response = handler.handle(request("GET", MetricsHandler.PATH, admin()));

    assertEquals(200, response.status());
    assertEquals(MetricsHandler.CONTENT_TYPE, firstHeader(response, "content-type"));
    assertEquals("req-117", firstHeader(response, "x-request-id"));
    String body = new String(response.body(), StandardCharsets.UTF_8);
    assertTrue(body.contains("deploymc_http_requests_total{endpoint=\"server\"} 1"));
    assertFalse(body.contains("Authorization"));
  }

  @Test
  void returns404ForAnyOtherPath() {
    HttpResponse response =
        new MetricsHandler(new MetricsRegistry(), "metrics.read")
            .handle(request("GET", "/api/v1/server", admin()));

    assertEquals(404, response.status());
    assertEquals("req-117", firstHeader(response, "x-request-id"));
    assertTrue(body(response).contains("metrics.not-found"));
  }

  @Test
  void returns405WithAllowHeaderForNonGetRequests() {
    HttpResponse response =
        new MetricsHandler(new MetricsRegistry(), "metrics.read")
            .handle(request("POST", MetricsHandler.PATH, admin()));

    assertEquals(405, response.status());
    assertEquals("GET", firstHeader(response, "allow"));
    assertTrue(body(response).contains("request.method-not-allowed"));
  }

  @Test
  void failsClosedForMissingOrNonAdminIdentity() {
    MetricsHandler handler = new MetricsHandler(new MetricsRegistry(), "metrics.read");

    HttpResponse missing = handler.handle(request("GET", MetricsHandler.PATH, null));
    assertEquals(401, missing.status());
    assertTrue(body(missing).contains("auth.required"));

    HttpResponse nonAdmin =
        handler.handle(
            request(
                "GET", MetricsHandler.PATH, new Identity("test", "test", "token", false, null)));
    assertEquals(403, nonAdmin.status());
    assertTrue(body(nonAdmin).contains("metrics.forbidden"));
  }

  @Test
  void rejectsUnsupportedPermissionAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MetricsHandler(new MetricsRegistry(), "metrics.write"));
  }

  @Test
  void usesTheInjectedCatalogForProblemDetails() {
    MetricsHandler handler =
        new MetricsHandler(
            new MetricsRegistry(), "metrics.read", TransportMessages.of(Locale.JAPANESE));

    HttpResponse response = handler.handle(request("GET", "/api/v1/server", admin()));

    assertEquals(404, response.status());
    assertTrue(body(response).contains("メトリクス"));
  }

  private static Identity admin() {
    return new Identity("test", "test", "token", true, null);
  }

  private static HttpRequest request(String method, String path, Identity identity) {
    return new HttpRequest(
        "req-117",
        method,
        path,
        "",
        Collections.<String, java.util.List<String>>emptyMap(),
        new byte[0],
        identity);
  }

  private static String firstHeader(HttpResponse response, String name) {
    return response.headers().get(name).get(0);
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
