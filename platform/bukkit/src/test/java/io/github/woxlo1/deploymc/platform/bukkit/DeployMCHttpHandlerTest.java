package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.woxlo1.deploymc.transport.HttpHandler;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class DeployMCHttpHandlerTest {

  @Test
  void servesHealthProbesWithoutCallingTheApiHandler() throws Exception {
    HttpHandler api =
        request -> {
          throw new AssertionError("health probes must not reach the API handler");
        };
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api);

    assertEquals(200, handler.handle(request("/healthz")).status());
    assertEquals(200, handler.handle(request("/readyz")).status());
  }

  @Test
  void delegatesApiPathsToTheApiHandler() throws Exception {
    HttpHandler api = request -> HttpResponse.json(200, "{\"api\":true}");
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api);

    HttpResponse response = handler.handle(request("/api/v1/server"));

    assertEquals(200, response.status());
    assertEquals("{\"api\":true}", new String(response.body(), StandardCharsets.UTF_8));
  }

  private static HttpRequest request(String path) {
    return new HttpRequest(
        "test-request",
        "GET",
        path,
        "",
        Collections.<String, java.util.List<String>>emptyMap(),
        new byte[0]);
  }
}
