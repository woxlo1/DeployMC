package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NettyTransportServerTest {

  private static final long TIMEOUT_SECONDS = 10;
  private static final String TEST_TOKEN = "dmc_transport_test_token_0123456789";

  @Test
  void startsStopsAndRejectsDoubleStart() {
    NettyTransportServer server = newServer(0, HttpResponse.json(200, "{}"), echoListener());
    try {
      assertFalse(server.isRunning());
      assertEquals(-1, server.port());
      server.start();
      assertTrue(server.isRunning());
      assertTrue(server.port() > 0);
      assertEquals(
          TransportError.ALREADY_STARTED,
          assertThrows(TransportException.class, server::start).error());
      server.stop();
      assertFalse(server.isRunning());
      assertEquals(-1, server.port());
      server.stop();
    } finally {
      server.stop();
    }
  }

  @Test
  void httpResponseHasSecurityHeadersAndNoCors() throws Exception {
    NettyTransportServer server =
        newServer(0, HttpResponse.json(200, "{\"ok\":true}"), echoListener());
    try {
      server.start();
      String response =
          rawRequest(
              server.port(),
              "GET /api/v1/test HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
      assertTrue(response.startsWith("HTTP/1.1 200"), response);
      assertTrue(
          response.toLowerCase(Locale.ROOT).contains("x-content-type-options: nosniff"), response);
      assertTrue(response.toLowerCase(Locale.ROOT).contains("x-frame-options: deny"));
      assertTrue(response.toLowerCase(Locale.ROOT).contains("referrer-policy: no-referrer"));
      assertTrue(response.toLowerCase(Locale.ROOT).contains("cache-control: no-store"));
      assertTrue(response.toLowerCase(Locale.ROOT).contains("content-security-policy"));
      assertFalse(response.toLowerCase(Locale.ROOT).contains("access-control-allow-origin"));
    } finally {
      server.stop();
    }
  }

  @Test
  void servesStaticAssetsWithWorkerCspThroughTransport() throws Exception {
    NettyTransportServer server = newStaticServer();
    try {
      server.start();
      String response =
          rawRequest(
              server.port(), "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
      assertTrue(response.startsWith("HTTP/1.1 200"), response);
      assertTrue(response.contains("WEB_INDEX"), response);
      assertTrue(response.contains("text/html; charset=utf-8"), response);
      assertTrue(response.contains("content-security-policy:"), response);
      assertTrue(response.contains("worker-src 'self' blob:"), response);
      assertTrue(response.contains("X-Content-Type-Options: nosniff"), response);
      assertTrue(response.contains("cache-control: no-store"), response);

      String asset =
          rawRequest(
              server.port(),
              "GET /assets/index-abcdef12.js HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
      assertTrue(asset.startsWith("HTTP/1.1 200"), asset);
      assertTrue(asset.contains("WEB_ASSET"), asset);
      assertTrue(asset.contains("cache-control: public, max-age=31536000, immutable"), asset);
    } finally {
      server.stop();
    }
  }

  @Test
  void rejectsStaticAssetTraversalThroughTransport() throws Exception {
    NettyTransportServer server = newStaticServer();
    try {
      server.start();
      String[] paths = {"/../../plugin.yml", "/%2e%2e%2fplugin.yml", "/..%2f..%2fplugin.yml"};
      for (String path : paths) {
        String response =
            rawRequest(
                server.port(),
                "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 404"), path + ": " + response);
        assertFalse(response.contains("PLUGIN_YML_MARKER"), path + ": " + response);
        assertFalse(response.contains("WEB_INDEX"), path + ": " + response);
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void rejectsOversizedBody() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .maxBodyBytes(1024)
            .build();
    NettyTransportServer server =
        new Server(
                config,
                request -> HttpResponse.json(200, "{}"),
                echoListener(),
                new DefaultTransportPolicy(config),
                new NoopLogger())
            .netty();
    try {
      server.start();
      byte[] body = new byte[2048];
      String request =
          "POST /api/v1/test HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 2048\r\n\r\n";
      String response = rawRequest(server.port(), request, body);
      assertTrue(response.startsWith("HTTP/1.1 413"), response);
      assertTrue(response.contains("oversized_body"), response);
    } finally {
      server.stop();
    }
  }

  @Test
  void closesIdleConnections() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .idleTimeoutMillis(1000)
            .build();
    NettyTransportServer server =
        new Server(
                config,
                request -> HttpResponse.json(200, "{}"),
                echoListener(),
                new DefaultTransportPolicy(config),
                new NoopLogger())
            .netty();
    try {
      server.start();
      try (Socket socket = new Socket("127.0.0.1", server.port())) {
        socket.setSoTimeout(5000);
        OutputStream output = socket.getOutputStream();
        output.write("GET /slow HTTP/1.1\r\nHost: 127.0.0.1\r\n".getBytes(StandardCharsets.UTF_8));
        output.flush();
        InputStream input = socket.getInputStream();
        int first = input.read();
        assertTrue(first == -1, "idle connection should be closed without a response");
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void reportsPortInUseClearly() {
    NettyTransportServer first = newServer(0, HttpResponse.json(200, "{}"), echoListener());
    try {
      first.start();
      NettyTransportServer second =
          newServer(first.port(), HttpResponse.json(200, "{}"), echoListener());
      TransportException failure = assertThrows(TransportException.class, second::start);
      assertEquals(TransportError.PORT_IN_USE, failure.error());
    } finally {
      first.stop();
    }
  }

  @Test
  void rejectsInvalidHostAndOrigin() throws Exception {
    NettyTransportServer server = newServer(0, HttpResponse.json(200, "{}"), echoListener());
    try {
      server.start();
      String badHost =
          rawRequest(server.port(), "GET /api/v1/test HTTP/1.1\r\nHost: example.invalid\r\n\r\n");
      assertTrue(badHost.startsWith("HTTP/1.1 403"), badHost);
      String badOrigin =
          rawRequest(
              server.port(),
              "GET /api/v1/test HTTP/1.1\r\nHost: 127.0.0.1\r\nOrigin: https://example.invalid\r\n\r\n");
      assertTrue(badOrigin.startsWith("HTTP/1.1 403"), badOrigin);
    } finally {
      server.stop();
    }
  }

  @Test
  void echoesWebSocketTextFrames() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
    NettyTransportServer server =
        new Server(
                config,
                request -> HttpResponse.json(200, "{}"),
                echoListener(),
                permissivePolicy(),
                new NoopLogger())
            .netty();
    EventLoopGroup clientGroup = new NioEventLoopGroup(1);
    try {
      server.start();
      EchoResult result = webSocketEcho(clientGroup, server.port(), "hello");
      assertTrue(result.handshakeComplete, "WebSocket handshake did not complete");
      assertTrue(result.echoReceived, "WebSocket echo was not received");
    } finally {
      clientGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
      server.stop();
    }
  }

  @Test
  void enforcesWebSocketQueueBounds() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .maxWebSocketQueuedMessages(1)
            .maxWebSocketQueuedBytes(1024 * 1024)
            .build();
    CountDownLatch attempted = new CountDownLatch(5);
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    WebSocketListener listener =
        new WebSocketListener() {
          @Override
          public void onOpen(WebSocketSession session) {
            for (int index = 0; index < 5; index++) {
              if (session.sendText("bounded")) {
                accepted.incrementAndGet();
              } else {
                rejected.incrementAndGet();
              }
              attempted.countDown();
            }
          }

          @Override
          public void onText(WebSocketSession session, String text) {}

          @Override
          public void onClose(WebSocketSession session) {}
        };
    NettyTransportServer server =
        new Server(
                config,
                request -> HttpResponse.json(200, "{}"),
                listener,
                permissivePolicy(),
                new NoopLogger())
            .netty();
    EventLoopGroup clientGroup = new NioEventLoopGroup(1);
    try {
      server.start();
      webSocketHandshakeOnly(clientGroup, server.port());
      assertTrue(attempted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
      assertEquals(1, accepted.get());
      assertEquals(4, rejected.get());
    } finally {
      clientGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
      server.stop();
    }
  }

  @Test
  void stopLeavesNoNamedTransportThreads() throws InterruptedException {
    NettyTransportServer server = newServer(0, HttpResponse.json(200, "{}"), echoListener());
    server.start();
    server.stop();
    awaitNoNamedThreads();
  }

  private static TransportPolicy permissivePolicy() {
    return new TransportPolicy() {
      @Override
      public boolean allowHost(String host) {
        return true;
      }

      @Override
      public boolean allowOrigin(String origin) {
        return true;
      }
    };
  }

  private static NettyTransportServer newStaticServer() {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
    return new Server(
            config,
            new StaticAssetHandler(),
            echoListener(),
            new DefaultTransportPolicy(config),
            new NoopLogger())
        .netty();
  }

  private static NettyTransportServer newServer(
      int port, HttpResponse response, WebSocketListener listener) {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", port, false)).build();
    return new Server(
            config,
            request -> response,
            listener,
            new DefaultTransportPolicy(config),
            new NoopLogger())
        .netty();
  }

  private static WebSocketListener echoListener() {
    return new WebSocketListener() {
      @Override
      public void onOpen(WebSocketSession session) {}

      @Override
      public void onText(WebSocketSession session, String text) {
        session.sendText(text);
      }

      @Override
      public void onClose(WebSocketSession session) {}
    };
  }

  private static void awaitNoNamedThreads() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!namedThreads().isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertTrue(
        namedThreads().isEmpty(), "named transport threads survived stop: " + namedThreads());
  }

  private static String namedThreads() {
    StringBuilder names = new StringBuilder();
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.isAlive() && thread.getName().startsWith("DeployMC-netty-")) {
        if (names.length() > 0) {
          names.append(", ");
        }
        names.append(thread.getName());
      }
    }
    return names.toString();
  }

  private static String rawRequest(int port, String request) throws IOException {
    return rawRequest(port, request, new byte[0]);
  }

  private static String rawRequest(int port, String request, byte[] body) throws IOException {
    return rawSocketRequest(port, authenticated(request), body);
  }

  private static String authenticated(String request) {
    if (request.contains("Authorization:")) {
      return request;
    }
    int end = request.indexOf("\r\n\r\n");
    if (end < 0) {
      return request;
    }
    return request.substring(0, end)
        + "\r\nAuthorization: Bearer "
        + TEST_TOKEN
        + request.substring(end);
  }

  private static String rawRequestUnauthenticated(int port, String request) throws IOException {
    return rawSocketRequest(port, request, new byte[0]);
  }

  private static String rawSocketRequest(int port, String request, byte[] body) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5000);
      OutputStream output = socket.getOutputStream();
      output.write(request.getBytes(StandardCharsets.UTF_8));
      output.write(body);
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

  private static EchoResult webSocketEcho(EventLoopGroup group, int port, String message)
      throws Exception {
    CountDownLatch handshake = new CountDownLatch(1);
    CountDownLatch echo = new CountDownLatch(1);
    URI uri = URI.create("ws://127.0.0.1:" + port + "/api/v1/ws");
    WebSocketClientHandshaker handshaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            uri,
            WebSocketVersion.V13,
            null,
            true,
            new DefaultHttpHeaders().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + TEST_TOKEN),
            8192);
    Bootstrap client = new Bootstrap();
    client
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel channel) {
                channel
                    .pipeline()
                    .addLast(new HttpClientCodec())
                    .addLast(new HttpObjectAggregator(8192))
                    .addLast(new WebSocketClientProtocolHandler(handshaker))
                    .addLast(
                        new ChannelInboundHandlerAdapter() {
                          @Override
                          public void userEventTriggered(
                              ChannelHandlerContext context, Object event) {
                            if (event
                                == WebSocketClientProtocolHandler.ClientHandshakeStateEvent
                                    .HANDSHAKE_COMPLETE) {
                              handshake.countDown();
                            }
                            context.fireUserEventTriggered(event);
                          }
                        })
                    .addLast(
                        new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                          @Override
                          protected void channelRead0(
                              ChannelHandlerContext context, TextWebSocketFrame frame) {
                            if (message.equals(frame.text())) {
                              echo.countDown();
                            }
                          }
                        });
              }
            });
    Channel channel = client.connect("127.0.0.1", port).sync().channel();
    try {
      boolean handshakeComplete = handshake.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      channel.writeAndFlush(
          new TextWebSocketFrame(Unpooled.copiedBuffer(message, StandardCharsets.UTF_8)));
      boolean echoReceived = echo.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return new EchoResult(handshakeComplete, echoReceived);
    } finally {
      channel.close().syncUninterruptibly();
    }
  }

  private static void webSocketHandshakeOnly(EventLoopGroup group, int port) throws Exception {
    CountDownLatch handshake = new CountDownLatch(1);
    URI uri = URI.create("ws://127.0.0.1:" + port + "/api/v1/ws");
    WebSocketClientHandshaker handshaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            uri,
            WebSocketVersion.V13,
            null,
            true,
            new DefaultHttpHeaders().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + TEST_TOKEN),
            8192);
    Bootstrap client = new Bootstrap();
    client
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel channel) {
                channel
                    .pipeline()
                    .addLast(new HttpClientCodec())
                    .addLast(new HttpObjectAggregator(8192))
                    .addLast(new WebSocketClientProtocolHandler(handshaker))
                    .addLast(
                        new ChannelInboundHandlerAdapter() {
                          @Override
                          public void userEventTriggered(
                              ChannelHandlerContext context, Object event) {
                            if (event
                                == WebSocketClientProtocolHandler.ClientHandshakeStateEvent
                                    .HANDSHAKE_COMPLETE) {
                              handshake.countDown();
                            }
                            context.fireUserEventTriggered(event);
                          }
                        });
              }
            });
    Channel channel = client.connect("127.0.0.1", port).sync().channel();
    try {
      assertTrue(handshake.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    } finally {
      channel.close().syncUninterruptibly();
    }
  }

  private static final class EchoResult {

    private final boolean handshakeComplete;
    private final boolean echoReceived;

    private EchoResult(boolean handshakeComplete, boolean echoReceived) {
      this.handshakeComplete = handshakeComplete;
      this.echoReceived = echoReceived;
    }
  }

  private static final class Server {

    private final TransportConfig config;
    private final HttpHandler httpHandler;
    private final WebSocketListener listener;
    private final TransportPolicy policy;
    private final RequestLogger logger;

    private Server(
        TransportConfig config,
        HttpHandler httpHandler,
        WebSocketListener listener,
        TransportPolicy policy,
        RequestLogger logger) {
      this.config = config;
      this.httpHandler = httpHandler;
      this.listener = listener;
      this.policy = policy;
      this.logger = logger;
    }

    private NettyTransportServer netty() {
      Authenticator authenticator =
          header ->
              ("Bearer " + TEST_TOKEN).equals(header)
                  ? AuthenticationResult.authenticated(
                      new Identity("test", "test", "token", true, null))
                  : AuthenticationResult.failed(AuthenticationFailure.INVALID);
      return new NettyTransportServer(
          config,
          httpHandler,
          listener,
          policy,
          logger,
          authenticator,
          TransportMessages.of(Locale.ENGLISH));
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
