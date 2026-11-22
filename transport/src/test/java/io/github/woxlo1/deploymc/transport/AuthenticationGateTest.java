package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.auth.TokenAuthenticator;
import io.github.woxlo1.deploymc.core.auth.TokenRecord;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves the fail-closed transport gate over the normative REST and WebSocket contracts. */
class AuthenticationGateTest {

  private static final String TOKEN = "dmc_gate_test_token_1234567890abcdef";
  private static final Set<String> METHODS =
      new HashSet<String>(
          Arrays.asList("get", "post", "put", "patch", "delete", "head", "options"));
  private static final List<String> HEALTH_PATHS = Arrays.asList("/healthz", "/readyz");

  @TempDir Path tempDir;

  @Test
  void protectsEveryOpenApiRouteExceptHealthProbes() throws Exception {
    List<Route> routes = routesFromOpenApi();
    assertFalse(routes.isEmpty(), "openapi.yaml contains no routes");
    assertTrue(
        routes.contains(new Route("GET", "/api/v1/server")),
        "openapi.yaml must contain GET /api/v1/server");

    NettyTransportServer server = newServer(new NoopLogger());
    try {
      server.start();
      for (Route route : routes) {
        if (HEALTH_PATHS.contains(route.path)) {
          continue;
        }
        String response = rawRequest(server.port(), route.method, route.path, null);
        assertTrue(response.startsWith("HTTP/1.1 401"), route + ": " + response);
        assertTrue(response.contains("\"code\":\"auth.required\""), route + ": " + response);
        assertFalse(response.contains(TOKEN), route + ": " + response);
      }
      for (String health : HEALTH_PATHS) {
        String response = rawRequest(server.port(), "GET", health, null);
        assertTrue(response.startsWith("HTTP/1.1 200"), health + ": " + response);
        assertTrue(response.contains("\"status\":\"ok\""), health + ": " + response);
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void acceptsValidTokenAndRejectsInvalidToken() throws Exception {
    NettyTransportServer server = newServer(new NoopLogger());
    try {
      server.start();
      String valid = rawRequest(server.port(), "GET", "/api/v1/server", TOKEN);
      assertTrue(valid.startsWith("HTTP/1.1 200"), valid);
      assertTrue(valid.contains("\"ok\":true"), valid);

      String invalid =
          rawRequest(server.port(), "GET", "/api/v1/server", "dmc_wrong_token_value_1234");
      assertTrue(invalid.startsWith("HTTP/1.1 401"), invalid);
      assertTrue(invalid.contains("\"code\":\"auth.invalid\""), invalid);
      assertTrue(invalid.toLowerCase(Locale.ROOT).contains("www-authenticate: bearer"), invalid);
    } finally {
      server.stop();
    }
  }

  @Test
  void protectsWebSocketUpgradeAndAsyncApiDeclaresIt() throws Exception {
    String asyncApi = readResource("specification/api/asyncapi.yaml");
    assertTrue(asyncApi.contains("address: /api/v1/ws"));
    assertTrue(asyncApi.contains("#/components/securitySchemes/bearerAuth"));

    NettyTransportServer server = newServer(new NoopLogger());
    try {
      server.start();
      String rejected = firstStatusLine(server.port(), upgradeRequest(null));
      assertEquals("HTTP/1.1 401 Unauthorized", rejected);

      String accepted = firstStatusLine(server.port(), upgradeRequest(TOKEN));
      assertEquals("HTTP/1.1 101 Switching Protocols", accepted);
    } finally {
      server.stop();
    }
  }

  @Test
  void carriesAuthenticatedIdentityInsteadOfClientHeaders() throws Exception {
    AtomicReference<String> observed = new AtomicReference<String>();
    HttpHandler handler =
        request -> {
          Identity identity = request.identity();
          observed.set(identity == null ? "none" : identity.id());
          return HttpResponse.json(200, "{\"ok\":true}");
        };

    NettyTransportServer server = newServer(new NoopLogger(), handler);
    try {
      server.start();
      String response =
          rawRequest(server.port(), "GET", "/api/v1/server", TOKEN + "\r\nX-Identity: attacker");
      assertTrue(response.startsWith("HTTP/1.1 200"), response);
      assertEquals("t-gate", observed.get());
    } finally {
      server.stop();
    }
  }

  @Test
  void neverLogsAuthorizationHeader() throws Exception {
    CapturingLogger logger = new CapturingLogger();
    NettyTransportServer server = newServer(logger);
    try {
      server.start();
      rawRequest(server.port(), "GET", "/api/v1/server", TOKEN);
      rawRequest(server.port(), "GET", "/api/v1/server", "dmc_wrong_token_value_1234");
      for (String line : logger.lines) {
        assertFalse(line.contains(TOKEN), line);
        assertFalse(line.contains("dmc_wrong_token_value_1234"), line);
        assertFalse(line.toLowerCase(Locale.ROOT).contains("authorization"), line);
      }
      assertTrue(logger.lines.size() >= 2);
    } finally {
      server.stop();
    }
  }

  private static final class HealthAwareHandler implements HttpHandler {

    private final HealthHandler health = new HealthHandler();

    @Override
    public HttpResponse handle(HttpRequest request) {
      if ("/healthz".equals(request.path()) || "/readyz".equals(request.path())) {
        return health.handle(request);
      }
      return HttpResponse.json(200, "{\"ok\":true}");
    }
  }

  private NettyTransportServer newServer(RequestLogger requestLogger) throws Exception {
    return newServer(requestLogger, new HealthAwareHandler());
  }

  private NettyTransportServer newServer(RequestLogger requestLogger, HttpHandler httpHandler)
      throws Exception {
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8));
    store.write(new TokenRecord("t-gate", "gate test", digest, null));
    Authenticator authenticator = new TokenAuthenticator(store, java.time.Clock.systemUTC());
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
    return new NettyTransportServer(
        config,
        httpHandler,
        new NoopWebSocketListener(),
        new DefaultTransportPolicy(config),
        requestLogger,
        authenticator,
        TransportMessages.of(Locale.ENGLISH));
  }

  private static List<Route> routesFromOpenApi() throws IOException {
    List<Route> routes = new ArrayList<Route>();
    boolean inPaths = false;
    String currentPath = null;
    for (String line : readResource("specification/api/openapi.yaml").split("\\R")) {
      if (line.equals("paths:")) {
        inPaths = true;
        continue;
      }
      if (!inPaths) {
        continue;
      }
      if (!line.isEmpty() && !line.startsWith(" ")) {
        break;
      }
      if (line.startsWith("  ") && !line.startsWith("    ") && line.trim().startsWith("/")) {
        String trimmed = line.trim();
        currentPath = trimmed.substring(0, trimmed.length() - 1);
        continue;
      }
      if (currentPath != null
          && line.startsWith("    ")
          && !line.startsWith("      ")
          && line.trim().endsWith(":")) {
        String method = line.trim().substring(0, line.trim().length() - 1).toLowerCase(Locale.ROOT);
        if (METHODS.contains(method)) {
          routes.add(new Route(method.toUpperCase(Locale.ROOT), currentPath));
        }
      }
    }
    return routes;
  }

  private static String readResource(String name) throws IOException {
    try (InputStream stream =
        AuthenticationGateTest.class.getClassLoader().getResourceAsStream(name)) {
      if (stream == null) {
        throw new IOException("missing test resource: " + name);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static String rawRequest(int port, String method, String path, String token)
      throws IOException {
    StringBuilder request = new StringBuilder();
    request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
    request.append("Host: 127.0.0.1\r\nConnection: close\r\n");
    if (token != null) {
      request.append("Authorization: Bearer ").append(token).append("\r\n");
    }
    request.append("\r\n");
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5000);
      socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().flush();
      ByteArrayOutputStream collected = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      try {
        int read;
        while ((read = socket.getInputStream().read(buffer)) != -1) {
          collected.write(buffer, 0, read);
        }
      } catch (SocketTimeoutException ignored) {
        // A closed connection is the expected end of a one-shot HTTP response.
      }
      return new String(collected.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static String upgradeRequest(String token) {
    StringBuilder request = new StringBuilder();
    request.append("GET /api/v1/ws HTTP/1.1\r\n");
    request.append("Host: 127.0.0.1\r\n");
    request.append("Upgrade: websocket\r\nConnection: Upgrade\r\n");
    request.append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n");
    request.append("Sec-WebSocket-Version: 13\r\n");
    if (token != null) {
      request.append("Authorization: Bearer ").append(token).append("\r\n");
    }
    request.append("\r\n");
    return request.toString();
  }

  private static String firstStatusLine(int port, String request) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5000);
      OutputStream output = socket.getOutputStream();
      output.write(request.getBytes(StandardCharsets.UTF_8));
      output.flush();
      InputStream input = socket.getInputStream();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII));
      String line = reader.readLine();
      return line == null ? "" : line.trim();
    }
  }

  private static final class Route {

    private final String method;
    private final String path;

    private Route(String method, String path) {
      this.method = method;
      this.path = path;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Route)) {
        return false;
      }
      Route route = (Route) other;
      return method.equals(route.method) && path.equals(route.path);
    }

    @Override
    public int hashCode() {
      return method.hashCode() * 31 + path.hashCode();
    }

    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  private static final class NoopWebSocketListener implements WebSocketListener {

    @Override
    public void onOpen(WebSocketSession session) {}

    @Override
    public void onText(WebSocketSession session, String text) {}

    @Override
    public void onClose(WebSocketSession session) {}
  }

  private static final class NoopLogger implements RequestLogger {

    @Override
    public void started(String address) {}

    @Override
    public void stopped() {}

    @Override
    public void nonLoopbackWarning(String address) {}

    @Override
    public void log(RequestLogEntry entry) {}

    @Override
    public void rejected(String requestId, String reason) {}

    @Override
    public void websocketRejected(String remoteAddress, String reason) {}

    @Override
    public void websocketOpened(String connectionId, String remoteAddress) {}

    @Override
    public void websocketClosed(String connectionId) {}

    @Override
    public void websocketQueueFull(String connectionId) {}
  }

  private static final class CapturingLogger implements RequestLogger {

    private final List<String> lines = new ArrayList<String>();

    @Override
    public void started(String address) {
      lines.add("started " + address);
    }

    @Override
    public void stopped() {
      lines.add("stopped");
    }

    @Override
    public void nonLoopbackWarning(String address) {
      lines.add("warning " + address);
    }

    @Override
    public void log(RequestLogEntry entry) {
      lines.add(
          "request "
              + entry.requestId()
              + " "
              + entry.method()
              + " "
              + entry.path()
              + " "
              + entry.status());
    }

    @Override
    public void rejected(String requestId, String reason) {
      lines.add("rejected " + requestId + " " + reason);
    }

    @Override
    public void websocketRejected(String remoteAddress, String reason) {
      lines.add("websocket rejected " + remoteAddress + " " + reason);
    }

    @Override
    public void websocketOpened(String connectionId, String remoteAddress) {
      lines.add("websocket opened " + connectionId + " " + remoteAddress);
    }

    @Override
    public void websocketClosed(String connectionId) {
      lines.add("websocket closed " + connectionId);
    }

    @Override
    public void websocketQueueFull(String connectionId) {
      lines.add("websocket queue full " + connectionId);
    }
  }
}
