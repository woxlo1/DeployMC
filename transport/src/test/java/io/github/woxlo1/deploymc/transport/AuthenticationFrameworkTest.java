package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.AuthorizationDeniedException;
import io.github.woxlo1.deploymc.core.auth.Identity;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Verifies the WP-3.1 request filter, explicit context, and uniform failure responses. */
class AuthenticationFrameworkTest {

  private static final String TOKEN = "dmc_framework_test_token_1234567890abcdef";

  @Test
  void passesRequestMetadataAndVerifiedClaimsToTheHandlerContext() throws Exception {
    AtomicReference<AuthContext> observed = new AtomicReference<AuthContext>();
    AtomicReference<String> requestMetadata = new AtomicReference<String>();
    Authenticator authenticator =
        request -> {
          requestMetadata.set(
              request.method() + " " + request.path() + " " + request.header("X-Identity"));
          Map<String, String> claims = new LinkedHashMap<String, String>();
          claims.put("sub", "verified-user");
          claims.put("email", "verified@example.invalid");
          return AuthenticationResult.authenticated(
              new Identity(
                  "verified-id",
                  "Verified User",
                  "token",
                  true,
                  Collections.<String>emptyList(),
                  claims));
        };
    HttpHandler handler =
        request -> {
          observed.set(request.context());
          return HttpResponse.json(200, "{\"ok\":true}");
        };

    NettyTransportServer server = newServer(authenticator, handler, new NoopWebSocketListener());
    try {
      server.start();
      String response =
          request(
              server.port(),
              "GET /api/v1/server HTTP/1.1\r\n"
                  + "Host: 127.0.0.1\r\n"
                  + "Authorization: Bearer "
                  + TOKEN
                  + "\r\nX-Identity: attacker\r\n");
      assertTrue(response.startsWith("HTTP/1.1 200"), response);
      assertEquals("GET /api/v1/server attacker", requestMetadata.get());
      AuthContext context = observed.get();
      assertNotNull(context);
      assertTrue(context.authenticated());
      assertEquals("verified-id", context.identity().id());
      assertEquals("verified-user", context.identity().claims().get("sub"));
      assertFalse(context.identity().claims().containsKey("X-Identity"));
      assertTrue(context.requestId().length() > 0);
    } finally {
      server.stop();
    }
  }

  @Test
  void mapsAuthenticatorExceptionsToUniform401WithoutDetail() throws Exception {
    Authenticator authenticator =
        request -> {
          throw new IllegalStateException("dmc_secret_value_1234567890");
        };
    NettyTransportServer server =
        newServer(
            authenticator, request -> HttpResponse.json(200, "{}"), new NoopWebSocketListener());
    try {
      server.start();
      String response =
          request(
              server.port(),
              "GET /api/v1/server HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer "
                  + TOKEN
                  + "\r\n");
      assertTrue(response.startsWith("HTTP/1.1 401"), response);
      assertTrue(response.contains("\"code\":\"auth.invalid\""), response);
      assertTrue(response.toLowerCase(Locale.ROOT).contains("www-authenticate: bearer"), response);
      assertFalse(response.contains("dmc_secret_value_1234567890"), response);
      assertFalse(response.contains("IllegalStateException"), response);
    } finally {
      server.stop();
    }
  }

  @Test
  void mapsAuthorizationDenialsToUniform403WithoutDetail() throws Exception {
    Authenticator authenticator = request -> authenticated("actor", "actor@example.invalid");
    HttpHandler handler =
        request -> {
          throw new AuthorizationDeniedException();
        };
    NettyTransportServer server = newServer(authenticator, handler, new NoopWebSocketListener());
    try {
      server.start();
      String response =
          request(
              server.port(),
              "GET /api/v1/server HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer "
                  + TOKEN
                  + "\r\n");
      assertTrue(response.startsWith("HTTP/1.1 403"), response);
      assertTrue(response.contains("\"code\":\"rbac.denied\""), response);
      assertFalse(response.contains("actor@example.invalid"), response);
      assertFalse(response.contains("api/v1/server"), response);
    } finally {
      server.stop();
    }
  }

  @Test
  void carriesAuthenticatedContextIntoWebSocketSession() throws Exception {
    Authenticator authenticator = request -> authenticated("ws-actor", "ws@example.invalid");
    final AtomicReference<String> observedIdentity = new AtomicReference<String>();
    final CountDownLatch opened = new CountDownLatch(1);
    WebSocketListener listener =
        new WebSocketListener() {
          @Override
          public void onOpen(WebSocketSession session) {
            Identity identity = session.context().identity();
            observedIdentity.set(identity == null ? "none" : identity.id());
            opened.countDown();
          }

          @Override
          public void onText(WebSocketSession session, String text) {}

          @Override
          public void onClose(WebSocketSession session) {}
        };

    NettyTransportServer server =
        newServer(authenticator, request -> HttpResponse.json(200, "{}"), listener);
    try {
      server.start();
      String response =
          firstStatusLine(
              server.port(),
              "GET /api/v1/ws HTTP/1.1\r\n"
                  + "Host: 127.0.0.1\r\n"
                  + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                  + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                  + "Sec-WebSocket-Version: 13\r\n"
                  + "Authorization: Bearer "
                  + TOKEN
                  + "\r\n\r\n");
      assertEquals("HTTP/1.1 101 Switching Protocols", response);
      assertTrue(opened.await(5, TimeUnit.SECONDS), "WebSocket onOpen was not called");
      assertEquals("ws-actor", observedIdentity.get());
    } finally {
      server.stop();
    }
  }

  @Test
  void httpRequestKeepsTheExplicitContextObject() throws Exception {
    Identity identity = new Identity("id", "label", "token", false, null);
    AuthContext context = AuthContext.authenticated("req-1", identity);
    HttpRequest request =
        HttpRequest.withContext(
            "req-1",
            "GET",
            "/api/v1/server",
            "",
            Collections.<String, java.util.List<String>>emptyMap(),
            new byte[0],
            context);

    assertSame(context, request.context());
    assertSame(identity, request.identity());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HttpRequest.withContext(
                "req-2",
                "GET",
                "/api/v1/server",
                "",
                Collections.<String, java.util.List<String>>emptyMap(),
                new byte[0],
                context));
  }

  private static AuthenticationResult authenticated(String id, String email) {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("sub", id);
    claims.put("email", email);
    return AuthenticationResult.authenticated(
        new Identity(
            id, "Authenticated Actor", "token", true, Collections.<String>emptyList(), claims));
  }

  private static NettyTransportServer newServer(
      Authenticator authenticator, HttpHandler handler, WebSocketListener listener) {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
    return new NettyTransportServer(
        config,
        handler,
        listener,
        new DefaultTransportPolicy(config),
        new NoopLogger(),
        authenticator,
        TransportMessages.of(Locale.ENGLISH));
  }

  private static String request(int port, String requestLineAndHeaders) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5000);
      OutputStream output = socket.getOutputStream();
      output.write((requestLineAndHeaders + "\r\n").getBytes(StandardCharsets.US_ASCII));
      output.flush();
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

  private static String firstStatusLine(int port, String requestText) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5000);
      OutputStream output = socket.getOutputStream();
      output.write(requestText.getBytes(StandardCharsets.US_ASCII));
      output.flush();
      InputStream input = socket.getInputStream();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII));
      String line = reader.readLine();
      return line == null ? "" : line.trim();
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
