package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.PathScope;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicy;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicyManager;
import io.github.woxlo1.deploymc.core.rbac.RolePolicy;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Embedded-socket transport test only: no running server, Bukkit runtime, browser, E2E suite, or
 * in-game command is involved.
 */
class NettyTransportServerRbacWebSocketTest {

  private static final String TOKEN = "dmc_rbac_ws_test_token_0123456789";
  private static final String WS_KEY = "dGhlIHNhbXBsZSBub25jZQ==";
  private static final String IDENTITY = "ws-actor";
  private static final int PERMISSION_REVOKED = 4403;
  private static final String CLOSE_REASON = "rbac.permission-revoked";
  private static final long TIMEOUT_SECONDS = 5L;

  @Test
  void revokedConsoleReadClosesWebSocketWith4403AndStableReason() throws Exception {
    RbacPolicyManager manager = new RbacPolicyManager(initialPolicy());
    RbacEvaluator evaluator = new RbacEvaluator(manager::current, null);
    AtomicInteger texts = new AtomicInteger();
    CountDownLatch firstText = new CountDownLatch(1);
    NettyTransportServer server = newServer(evaluator, texts, firstText);
    Socket socket = null;
    try {
      server.start();
      socket = openUpgrade(server.port());

      sendText(socket, "before-revoke");
      assertTrue(
          firstText.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "first frame was not delivered");
      assertEquals(1, texts.get());

      manager.denyAll();
      sendText(socket, "after-revoke");

      CloseFrame close = readCloseFrame(socket);
      assertEquals(PERMISSION_REVOKED, close.code());
      assertEquals(CLOSE_REASON, close.reason());
      assertEquals(1, texts.get(), "no frame may be delivered after permission loss");
    } finally {
      if (socket != null) {
        socket.close();
      }
      server.stop();
    }
  }

  private static RbacPolicy initialPolicy() {
    RolePolicy reader =
        new RolePolicy(
            "ws-reader",
            false,
            Collections.singletonList(RbacGate.PERMISSION_CONSOLE_READ),
            PathScope.wholeSandbox(Collections.<String>emptyList()));
    return RbacPolicy.of(
        Collections.<String, RolePolicy>singletonMap("ws-reader", reader),
        Collections.<String, java.util.List<String>>singletonMap(
            IDENTITY, Collections.singletonList("ws-reader")),
        "none");
  }

  private static NettyTransportServer newServer(
      RbacEvaluator evaluator, AtomicInteger texts, CountDownLatch firstText) {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
    Authenticator authenticator =
        request ->
            ("Bearer " + TOKEN).equals(request.header("Authorization"))
                ? AuthenticationResult.authenticated(user())
                : AuthenticationResult.failed(AuthenticationFailure.INVALID);
    WebSocketListener listener =
        new WebSocketListener() {
          @Override
          public void onOpen(WebSocketSession session) {}

          @Override
          public void onText(WebSocketSession session, String text) {
            texts.incrementAndGet();
            firstText.countDown();
          }

          @Override
          public void onClose(WebSocketSession session) {}
        };
    return new NettyTransportServer(
        config,
        request -> HttpResponse.json(200, "{}"),
        listener,
        new DefaultTransportPolicy(config),
        new NoopLogger(),
        authenticator,
        TransportMessages.of(Locale.ENGLISH),
        evaluator);
  }

  private static Identity user() {
    return new Identity(
        IDENTITY, "WebSocket Actor", "cloudflare-access", false, Collections.<String>emptyList());
  }

  private static Socket openUpgrade(int port) throws IOException {
    Socket socket = new Socket("127.0.0.1", port);
    socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    OutputStream output = socket.getOutputStream();
    output.write(
        ("GET /api/v1/ws HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Origin: http://127.0.0.1:"
                + port
                + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: "
                + WS_KEY
                + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Authorization: Bearer "
                + TOKEN
                + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    output.flush();
    String headers = readHeaders(socket.getInputStream());
    assertTrue(headers.startsWith("HTTP/1.1 101"), headers);
    return socket;
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

  private static void sendText(Socket socket, String text) throws IOException {
    byte[] payload = text.getBytes(StandardCharsets.UTF_8);
    assertTrue(payload.length > 0 && payload.length < 126, "test frame must be short");
    byte[] mask = {0x12, 0x34, 0x56, 0x78};
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.write(0x81);
    frame.write(0x80 | payload.length);
    frame.write(mask, 0, mask.length);
    for (int index = 0; index < payload.length; index++) {
      frame.write(payload[index] ^ mask[index % mask.length]);
    }
    OutputStream output = socket.getOutputStream();
    output.write(frame.toByteArray());
    output.flush();
  }

  private static CloseFrame readCloseFrame(Socket socket) throws IOException {
    DataInputStream input = new DataInputStream(socket.getInputStream());
    int first = input.readUnsignedByte();
    int second = input.readUnsignedByte();
    assertEquals(0x08, first & 0x0f, "expected a WebSocket close frame");
    int length = second & 0x7f;
    assertTrue(length >= 2 && length <= 125, "unexpected close payload length: " + length);
    byte[] payload = new byte[length];
    input.readFully(payload);
    int code = (payload[0] & 0xff) << 8 | payload[1] & 0xff;
    String reason = new String(payload, 2, length - 2, StandardCharsets.UTF_8);
    return new CloseFrame(code, reason);
  }

  private static final class CloseFrame {

    private final int code;
    private final String reason;

    private CloseFrame(int code, String reason) {
      this.code = code;
      this.reason = reason;
    }

    private int code() {
      return code;
    }

    private String reason() {
      return reason;
    }
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
