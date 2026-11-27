package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Embedded-socket coverage for WP-3.6 transport enforcement. */
class WebProtectionEnforcementTest {

  private static final String TOKEN = "dmc_web_protection_test_token_0123456789";
  private static final String WS_KEY = "dGhlIHNhbXBsZSBub25jZQ==";

  @Test
  void rejectsUpgradesWithoutAnAllowlistedOriginBeforeAuthentication() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    Authenticator authenticator =
        request -> {
          attempts.incrementAndGet();
          return AuthenticationResult.failed(AuthenticationFailure.INVALID);
        };
    NettyTransportServer server =
        newServer(
            TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build(),
            authenticator,
            request -> HttpResponse.json(200, "{}"));
    try {
      server.start();
      String missing = raw(server.port(), upgradeRequest(server.port(), null));
      String invalid =
          raw(server.port(), upgradeRequest(server.port(), "http://example.invalid:8080"));

      assertTrue(missing.startsWith("HTTP/1.1 403"), missing);
      assertTrue(missing.contains("\"code\":\"request.origin-rejected\""), missing);
      assertTrue(invalid.startsWith("HTTP/1.1 403"), invalid);
      assertTrue(invalid.contains("\"code\":\"request.origin-rejected\""), invalid);
      assertTrue(
          missing.toLowerCase(Locale.ROOT).contains("ratelimit-limit:"),
          "the address bucket was evaluated before Origin rejection");
      assertEquals(0, attempts.get());
    } finally {
      server.stop();
    }
  }

  @Test
  void checksPresentOriginsOnNonWebSocketRequestsButAllowsAbsentOrigins() throws Exception {
    NettyTransportServer server =
        newServer(
            TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build(),
            request -> AuthenticationResult.failed(AuthenticationFailure.INVALID),
            request -> HttpResponse.status(200));
    try {
      server.start();
      String absent = raw(server.port(), healthRequest(null));
      String rejected = raw(server.port(), healthRequest("http://example.invalid:8080"));

      assertTrue(absent.startsWith("HTTP/1.1 200"), absent);
      assertTrue(rejected.startsWith("HTTP/1.1 403"), rejected);
      assertTrue(rejected.contains("\"code\":\"request.origin-rejected\""), rejected);
      assertTrue(
          rejected.toLowerCase(Locale.ROOT).contains("ratelimit-limit:"),
          "the address bucket was evaluated before Origin rejection");
    } finally {
      server.stop();
    }
  }

  @Test
  void limitsRequestsPerEffectivePeerFromTrustedLoopback() throws Exception {
    ObservabilityConfig observability =
        ObservabilityConfig.builder().addressRateLimitPerMinute(1).build();
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .observability(observability)
            .build();
    NettyTransportServer server =
        newServer(
            config,
            request -> AuthenticationResult.failed(AuthenticationFailure.INVALID),
            request -> HttpResponse.status(200));
    try {
      server.start();
      String first = raw(server.port(), healthRequest(null, "198.51.100.10"));
      String otherPeer = raw(server.port(), healthRequest(null, "198.51.100.11"));
      String repeated = raw(server.port(), healthRequest(null, "198.51.100.10"));

      assertTrue(first.startsWith("HTTP/1.1 200"), first);
      assertTrue(otherPeer.startsWith("HTTP/1.1 200"), otherPeer);
      assertTrue(repeated.startsWith("HTTP/1.1 429"), repeated);
      assertTrue(repeated.contains("\"code\":\"auth.rate-limited\""), repeated);
      assertTrue(repeated.toLowerCase(Locale.ROOT).contains("ratelimit-limit: 1"), repeated);
      assertTrue(repeated.toLowerCase(Locale.ROOT).contains("ratelimit-remaining: 0"), repeated);
      assertTrue(repeated.toLowerCase(Locale.ROOT).contains("retry-after:"), repeated);
      assertFalse(repeated.contains("198.51.100"), repeated);
    } finally {
      server.stop();
    }
  }

  @Test
  void appliesEndpointSpecificBodyLimitsAfterAggregation() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .maxBodyBytes(1024)
            .maxUploadBodyBytes(2048)
            .build();
    NettyTransportServer server =
        newServer(
            config, request -> authenticated("wp-actor"), request -> HttpResponse.status(200));
    byte[] body = new byte[1500];
    try {
      server.start();
      String ordinary =
          raw(server.port(), postRequest("/api/v1/me", body.length, tokenHeader()), body);
      String upload =
          raw(server.port(), postRequest("/api/v1/files/upload", body.length, tokenHeader()), body);

      assertTrue(ordinary.startsWith("HTTP/1.1 413"), ordinary);
      assertTrue(ordinary.contains("\"code\":\"request.too-large\""), ordinary);
      assertTrue(
          ordinary.toLowerCase(Locale.ROOT).contains("ratelimit-limit:"),
          "the address bucket was evaluated before the body limit");
      assertTrue(upload.startsWith("HTTP/1.1 200"), upload);
    } finally {
      server.stop();
    }
  }

  @Test
  void locksTokenModePeersAfterFiveFailuresWithoutCheckingFurtherCredentials() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    Authenticator authenticator =
        request -> {
          attempts.incrementAndGet();
          return AuthenticationResult.failed(AuthenticationFailure.INVALID);
        };
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .tokenLockoutEnabled(true)
            .build();
    NettyTransportServer server =
        newServer(config, authenticator, request -> HttpResponse.status(200));
    try {
      server.start();
      for (int index = 0; index < 5; index++) {
        String response =
            raw(
                server.port(),
                getRequest("/api/v1/server", tokenHeader() + "X-Forwarded-For: 198.51.100.20\r\n"));
        assertTrue(response.startsWith("HTTP/1.1 401"), response);
        assertTrue(
            response.toLowerCase(Locale.ROOT).contains("ratelimit-limit: 60"),
            "auth-failure headers were not applied to " + response);
      }
      String locked =
          raw(
              server.port(),
              getRequest("/api/v1/server", tokenHeader() + "X-Forwarded-For: 198.51.100.20\r\n"));

      assertTrue(locked.startsWith("HTTP/1.1 429"), locked);
      assertTrue(locked.contains("\"code\":\"auth.locked-out\""), locked);
      assertTrue(locked.contains("\"retryable\":true"), locked);
      assertTrue(locked.toLowerCase(Locale.ROOT).contains("retry-after:"), locked);
      assertEquals(5, attempts.get());
      assertFalse(locked.contains("198.51.100"), locked);
    } finally {
      server.stop();
    }
  }

  private static NettyTransportServer newServer(
      TransportConfig config, Authenticator authenticator, HttpHandler handler) {
    return new NettyTransportServer(
        config,
        handler,
        new NoopWebSocketListener(),
        new DefaultTransportPolicy(config),
        new NoopLogger(),
        authenticator,
        TransportMessages.of(Locale.ENGLISH),
        RbacTestSupport.permitAll(),
        new MetricsRegistry());
  }

  private static AuthenticationResult authenticated(String id) {
    return AuthenticationResult.authenticated(
        new Identity(id, id, "token", true, Collections.<String>emptyList()));
  }

  private static String tokenHeader() {
    return "Authorization: Bearer " + TOKEN + "\r\n";
  }

  private static String getRequest(String path, String extraHeaders) {
    return "GET "
        + path
        + " HTTP/1.1\r\nHost: 127.0.0.1\r\n"
        + extraHeaders
        + "Connection: close\r\n\r\n";
  }

  private static String healthRequest(String origin) {
    return getRequest("/healthz", origin == null ? "" : "Origin: " + origin + "\r\n");
  }

  private static String healthRequest(String origin, String forwardedFor) {
    return getRequest(
        "/healthz",
        (origin == null ? "" : "Origin: " + origin + "\r\n")
            + "X-Forwarded-For: "
            + forwardedFor
            + "\r\n");
  }

  private static String postRequest(String path, int contentLength, String extraHeaders) {
    return "POST "
        + path
        + " HTTP/1.1\r\nHost: 127.0.0.1\r\n"
        + extraHeaders
        + "Content-Length: "
        + contentLength
        + "\r\nConnection: close\r\n\r\n";
  }

  private static String upgradeRequest(int port, String origin) {
    return "GET /api/v1/ws HTTP/1.1\r\n"
        + "Host: 127.0.0.1\r\n"
        + (origin == null ? "" : "Origin: " + origin + "\r\n")
        + "Upgrade: websocket\r\n"
        + "Connection: Upgrade\r\n"
        + "Sec-WebSocket-Key: "
        + WS_KEY
        + "\r\n"
        + "Sec-WebSocket-Version: 13\r\n"
        + "\r\n";
  }

  private static String raw(int port, String request) throws IOException {
    return raw(port, request, new byte[0]);
  }

  private static String raw(int port, String request, byte[] body) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5_000);
      OutputStream output = socket.getOutputStream();
      output.write(request.getBytes(StandardCharsets.US_ASCII));
      output.write(body);
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
}
