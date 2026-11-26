package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NettyTransportServerObservabilityTest {

  private static final String TOKEN = "dmc_observability_test_token_0123456789";
  private static final String WS_KEY = "dGhlIHNhbXBsZSBub25jZQ==";

  @Test
  void addsRequestIdAndShedsIdentityBudgetWithRateLimitHeaders() throws Exception {
    ObservabilityConfig observability =
        ObservabilityConfig.builder().identityRateLimitPerMinute(1).build();
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .observability(observability)
            .build();
    NettyTransportServer server = newServer(config, new MetricsRegistry());
    try {
      server.start();
      String first = request(server.port(), "GET /api/v1/me HTTP/1.1", true);
      String second = request(server.port(), "GET /api/v1/me HTTP/1.1", true);

      assertTrue(first.startsWith("HTTP/1.1 200"), first);
      assertTrue(first.toLowerCase(Locale.ROOT).contains("x-request-id:"), first);
      assertTrue(second.startsWith("HTTP/1.1 429"), second);
      assertTrue(second.contains("auth.rate-limited"), second);
      assertTrue(second.toLowerCase(Locale.ROOT).contains("retry-after:"), second);
      assertTrue(second.toLowerCase(Locale.ROOT).contains("ratelimit-limit: 1"), second);
      assertTrue(second.toLowerCase(Locale.ROOT).contains("x-request-id:"), second);
    } finally {
      server.stop();
    }
  }

  @Test
  void metricsRouteIsOptInAndNeverAnonymous() throws Exception {
    MetricsRegistry registry = new MetricsRegistry();
    ObservabilityConfig observability = ObservabilityConfig.builder().metricsEnabled(true).build();
    TransportConfig enabled =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .observability(observability)
            .build();
    NettyTransportServer server = newServer(enabled, registry);
    try {
      server.start();
      String anonymous = request(server.port(), "GET /api/v1/metrics HTTP/1.1", false);
      String authenticated = request(server.port(), "GET /api/v1/metrics HTTP/1.1", true);

      assertTrue(anonymous.startsWith("HTTP/1.1 401"), anonymous);
      assertTrue(authenticated.startsWith("HTTP/1.1 200"), authenticated);
      assertTrue(
          authenticated.toLowerCase(Locale.ROOT).contains("text/plain; version=0.0.4"),
          authenticated);
      assertTrue(authenticated.contains("deploymc_http_requests_total"), authenticated);
    } finally {
      server.stop();
    }

    TransportConfig disabled =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
    NettyTransportServer disabledServer = newServer(disabled, new MetricsRegistry());
    try {
      disabledServer.start();
      String response = request(disabledServer.port(), "GET /api/v1/metrics HTTP/1.1", true);
      assertTrue(response.startsWith("HTTP/1.1 404"), response);
      assertFalse(response.contains("deploymc_http_requests_total"), response);
    } finally {
      disabledServer.stop();
    }
  }

  @Test
  void excessWebSocketUpgradesAreRejectedWith503() throws Exception {
    MetricsRegistry registry = new MetricsRegistry();
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .maxWebSocketConnections(1)
            .build();
    NettyTransportServer server = newServer(config, registry);
    Socket first = null;
    try {
      server.start();
      first = openUpgrade(server.port());
      awaitWebSocketGauge(registry, 1);

      String second = rawRequest(server.port(), upgradeRequest());
      assertTrue(second.startsWith("HTTP/1.1 503"), second);
      assertTrue(second.contains("server.busy"), second);
      assertTrue(second.toLowerCase(Locale.ROOT).contains("retry-after: 1"), second);
    } finally {
      if (first != null) {
        first.close();
      }
      server.stop();
    }
  }

  @Test
  void watchdogThreadStopsBeforeShutdownCompletes() throws Exception {
    NettyTransportServer server =
        newServer(
            TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build(),
            new MetricsRegistry());
    server.start();
    assertTrue(hasWatchdogThread(), "watchdog thread was not started");
    server.stop();
    assertFalse(hasWatchdogThread(), "watchdog thread survived stop");
  }

  private static NettyTransportServer newServer(TransportConfig config, MetricsRegistry metrics) {
    Authenticator authenticator =
        request ->
            ("Bearer " + TOKEN).equals(request.header("Authorization"))
                ? AuthenticationResult.authenticated(
                    new Identity("test", "test", "token", true, null))
                : AuthenticationResult.failed(AuthenticationFailure.INVALID);
    return new NettyTransportServer(
        config,
        request ->
            "/api/v1/me".equals(request.path())
                ? HttpResponse.json(200, "{\"ok\":true}")
                : HttpResponse.json(404, "{\"code\":\"not-found\"}"),
        new WebSocketListener() {
          @Override
          public void onOpen(WebSocketSession session) {}

          @Override
          public void onText(WebSocketSession session, String text) {}

          @Override
          public void onClose(WebSocketSession session) {}
        },
        new DefaultTransportPolicy(config),
        new NoopLogger(),
        authenticator,
        TransportMessages.of(Locale.ENGLISH),
        RbacTestSupport.permitAll(),
        metrics);
  }

  private static String request(int port, String requestLine, boolean authenticated)
      throws IOException {
    String request = requestLine + "\r\nHost: 127.0.0.1\r\nConnection: close\r\n";
    if (authenticated) {
      request += "Authorization: Bearer " + TOKEN + "\r\n";
    }
    return rawRequest(port, request + "\r\n");
  }

  private static String rawRequest(int port, String request) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5_000);
      OutputStream output = socket.getOutputStream();
      output.write(request.getBytes(StandardCharsets.UTF_8));
      output.flush();
      ByteArrayOutputStream collected = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int read;
      while ((read = socket.getInputStream().read(buffer)) != -1) {
        collected.write(buffer, 0, read);
      }
      return new String(collected.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static Socket openUpgrade(int port) throws IOException {
    Socket socket = new Socket("127.0.0.1", port);
    socket.setSoTimeout(5_000);
    OutputStream output = socket.getOutputStream();
    output.write(upgradeRequest().getBytes(StandardCharsets.UTF_8));
    output.flush();
    String headers = readHeaders(socket.getInputStream());
    assertTrue(headers.startsWith("HTTP/1.1 101"), headers);
    return socket;
  }

  private static String upgradeRequest() {
    return "GET /api/v1/ws HTTP/1.1\r\n"
        + "Host: 127.0.0.1\r\n"
        + "Upgrade: websocket\r\n"
        + "Connection: Upgrade\r\n"
        + "Sec-WebSocket-Key: "
        + WS_KEY
        + "\r\n"
        + "Sec-WebSocket-Version: 13\r\n"
        + "Authorization: Bearer "
        + TOKEN
        + "\r\n\r\n";
  }

  private static String readHeaders(InputStream input) throws IOException {
    ByteArrayOutputStream collected = new ByteArrayOutputStream();
    while (!collected.toString("ISO-8859-1").contains("\r\n\r\n")) {
      int value = input.read();
      if (value < 0) {
        break;
      }
      collected.write(value);
    }
    return new String(collected.toByteArray(), StandardCharsets.ISO_8859_1);
  }

  private static void awaitWebSocketGauge(MetricsRegistry registry, int expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
    while (System.nanoTime() < deadline) {
      if (registry
          .renderPrometheus()
          .contains("deploymc_websocket_connections_active " + expected + "\n")) {
        return;
      }
      Thread.sleep(10L);
    }
    throw new AssertionError("WebSocket gauge did not reach " + expected);
  }

  private static boolean hasWatchdogThread() {
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.isAlive() && "DeployMC-watchdog-transport".equals(thread.getName())) {
        return true;
      }
    }
    return false;
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
}
