package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.woxlo1.deploymc.transport.AuditApiHandler;
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

  @Test
  void delegatesFileApiPathsToTheFileHandler() throws Exception {
    HttpHandler api =
        request -> {
          throw new AssertionError("file routes must not reach the general API handler");
        };
    HttpHandler files = request -> HttpResponse.json(200, "{\"files\":true}");
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api, files);

    assertEquals(200, handler.handle(request("/api/v1/files")).status());
    assertEquals(200, handler.handle(request("/api/v1/files/content")).status());
    assertEquals(
        "{\"files\":true}",
        new String(handler.handle(request("/api/v1/files/move")).body(), StandardCharsets.UTF_8));
  }

  @Test
  void delegatesSnapshotApiPathsToTheSnapshotHandler() throws Exception {
    HttpHandler api =
        request -> {
          throw new AssertionError("snapshot routes must not reach the general API handler");
        };
    HttpHandler snapshots = request -> HttpResponse.json(200, "{\"snapshots\":true}");
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api, null, snapshots);

    assertEquals(200, handler.handle(request("/api/v1/snapshots")).status());
    assertEquals(
        "{\"snapshots\":true}",
        new String(
            handler.handle(request("/api/v1/snapshots/snap_test/content")).body(),
            StandardCharsets.UTF_8));
  }

  @Test
  void delegatesDoctorPathBeforeTheGeneralApiHandler() throws Exception {
    HttpHandler api =
        request -> {
          throw new AssertionError("doctor routes must not reach the general API handler");
        };
    HttpHandler doctor = request -> HttpResponse.json(200, "{\"doctor\":true}");
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api, null, null, doctor);

    HttpResponse response = handler.handle(request(DoctorHandler.PATH));

    assertEquals(200, response.status());
    assertEquals("{\"doctor\":true}", new String(response.body(), StandardCharsets.UTF_8));
  }

  @Test
  void delegatesMePathBeforeTheGeneralApiHandler() throws Exception {
    HttpHandler api =
        request -> {
          throw new AssertionError("me routes must not reach the general API handler");
        };
    HttpHandler me = request -> HttpResponse.json(200, "{\"me\":true}");
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api, null, null, null, me);

    HttpResponse response = handler.handle(request("/api/v1/me"));

    assertEquals(200, response.status());
    assertEquals("{\"me\":true}", new String(response.body(), StandardCharsets.UTF_8));
  }

  @Test
  void delegatesExactAuditPathsBeforeTheGeneralApiHandler() throws Exception {
    HttpHandler api = request -> HttpResponse.json(200, "{\"api\":true}");
    HttpHandler audit =
        request -> {
          if (request.path().endsWith("/other")) {
            throw new AssertionError("lookalike routes must not reach the audit handler");
          }
          return HttpResponse.json(200, "{\"audit\":true}");
        };
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api, null, null, null, null, audit);

    for (String path :
        new String[] {
          AuditApiHandler.PATH, AuditApiHandler.EXPORT_PATH, AuditApiHandler.VERIFY_PATH
        }) {
      assertEquals(
          "{\"audit\":true}",
          new String(handler.handle(request(path)).body(), StandardCharsets.UTF_8));
    }
    assertEquals(
        "{\"api\":true}",
        new String(
            handler.handle(request(AuditApiHandler.PATH + "/other")).body(),
            StandardCharsets.UTF_8));
  }

  @Test
  void doesNotDelegateLookalikeOrGeneralApiPaths() throws Exception {
    HttpHandler api = request -> HttpResponse.json(200, "{\"api\":true}");
    HttpHandler files =
        request -> {
          throw new AssertionError("only exact file API routes may reach the file handler");
        };
    HttpHandler snapshots =
        request -> {
          throw new AssertionError("only exact snapshot API routes may reach the snapshot handler");
        };
    HttpHandler me =
        request -> {
          throw new AssertionError("only the exact /api/v1/me route may reach the me handler");
        };
    DeployMCHttpHandler handler = new DeployMCHttpHandler(api, files, snapshots, null, me);

    assertEquals(
        "{\"api\":true}",
        new String(handler.handle(request("/api/v1/files-other")).body(), StandardCharsets.UTF_8));
    assertEquals(
        "{\"api\":true}",
        new String(
            handler.handle(request("/api/v1/snapshots-other")).body(), StandardCharsets.UTF_8));
    assertEquals(
        "{\"api\":true}",
        new String(handler.handle(request("/api/v1/server")).body(), StandardCharsets.UTF_8));
    assertEquals(
        "{\"api\":true}",
        new String(handler.handle(request("/api/v1/me/other")).body(), StandardCharsets.UTF_8));
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
