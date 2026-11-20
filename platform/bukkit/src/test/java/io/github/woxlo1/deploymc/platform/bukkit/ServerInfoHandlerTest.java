package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.CapabilityState;
import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerInfoHandlerTest {

  private final PlatformInfo platform =
      new PlatformInfo("Paper", "1.21.11", "1.21.11", "21.0.1", "Test OS \"quoted\"");

  private final PlatformCapabilities capabilities = capabilities();

  @Test
  void returnsDocumentedServerInfoWithRequestIdAndEtag() {
    ServerInfoHandler handler = new ServerInfoHandler("0.1.0-SNAPSHOT", platform, capabilities);

    HttpResponse response =
        handler.handle(request("GET", Collections.<String, List<String>>emptyMap()));

    assertEquals(200, response.status());
    String body = new String(response.body(), StandardCharsets.UTF_8);
    assertTrue(body.contains("\"deployMcVersion\":\"0.1.0-SNAPSHOT\""), body);
    assertTrue(body.contains("\"apiVersion\":\"v1\""), body);
    assertTrue(body.contains("\"softwareName\":\"Paper\""), body);
    assertTrue(body.contains("\"operatingSystem\":\"Test OS \\\"quoted\\\"\""), body);
    assertTrue(body.contains("\"command.dispatch\":\"AVAILABLE\""), body);
    assertTrue(body.contains("\"update.folder\":\"UNAVAILABLE\""), body);
    assertEquals(Collections.singletonList("test-request"), response.headers().get("x-request-id"));
    String etag = response.headers().get("etag").get(0);
    assertTrue(etag.startsWith("\"") && etag.endsWith("\""), etag);
    assertFalse(etag.contains("\n"));
  }

  @Test
  void returnsNotModifiedWhenEtagMatches() {
    ServerInfoHandler handler = new ServerInfoHandler("0.1.0-SNAPSHOT", platform, capabilities);
    String etag =
        handler
            .handle(request("GET", Collections.<String, List<String>>emptyMap()))
            .headers()
            .get("etag")
            .get(0);
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("if-none-match", Collections.singletonList(etag));

    HttpResponse response = handler.handle(request("GET", headers));

    assertEquals(304, response.status());
    assertEquals(Collections.singletonList(etag), response.headers().get("etag"));
  }

  @Test
  void rejectsOtherMethodsAndPaths() {
    ServerInfoHandler handler = new ServerInfoHandler("0.1.0-SNAPSHOT", platform, capabilities);

    assertEquals(
        405,
        handler.handle(request("POST", Collections.<String, List<String>>emptyMap())).status());
    assertEquals(
        404,
        handler
            .handle(
                new HttpRequest(
                    "test-request",
                    "GET",
                    "/api/v1/other",
                    "",
                    Collections.<String, List<String>>emptyMap(),
                    new byte[0]))
            .status());
  }

  private static HttpRequest request(String method, Map<String, List<String>> headers) {
    return new HttpRequest("test-request", method, "/api/v1/server", "", headers, new byte[0]);
  }

  private static PlatformCapabilities capabilities() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put("command.dispatch", CapabilityState.AVAILABLE);
    states.put("update.folder", CapabilityState.UNAVAILABLE);
    states.put("players.locale", CapabilityState.UNKNOWN);
    return PlatformCapabilities.of(states);
  }
}
