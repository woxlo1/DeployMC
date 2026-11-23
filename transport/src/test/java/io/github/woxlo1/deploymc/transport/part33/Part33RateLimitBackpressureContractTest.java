package io.github.woxlo1.deploymc.transport.part33;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import io.github.woxlo1.deploymc.transport.NettyTransportServer;
import io.github.woxlo1.deploymc.transport.RequestLogEntry;
import io.github.woxlo1.deploymc.transport.RequestLogger;
import io.github.woxlo1.deploymc.transport.TransportConfig;
import io.github.woxlo1.deploymc.transport.TransportMessages;
import io.github.woxlo1.deploymc.transport.TransportPolicy;
import io.github.woxlo1.deploymc.transport.WebSocketListener;
import io.github.woxlo1.deploymc.transport.WebSocketSession;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** PART 33.8 contract: declared limits, real bounded WebSocket queue, absent HTTP rate limiter. */
class Part33RateLimitBackpressureContractTest {

  private static final String SPEC =
      "docs/development/DeployMC_Complete_Master_Development_Specification.md";
  private static final String TOKEN = "dmc_part33_contract_token_0123456789";

  @Test
  void openApiDeclares429AndRetryAfter() throws Exception {
    String openApi = repositoryText("specification/api/openapi.yaml");

    assertTrue(openApi.contains("'429':"), "openapi.yaml must declare 429 responses");
    assertTrue(
        openApi.contains("$ref: '#/components/responses/TooManyRequests'"),
        "429 responses must use TooManyRequests");
    assertTrue(openApi.contains("    TooManyRequests:"));
    assertTrue(openApi.contains("        Retry-After:"));
    assertTrue(openApi.contains("$ref: '#/components/headers/RetryAfter'"));
    assertTrue(openApi.contains("        RateLimit-Limit:"));
    assertTrue(openApi.contains("        RateLimit-Remaining:"));
    assertTrue(openApi.contains("        RateLimit-Reset:"));
  }

  @Test
  void defaultConfigDeclaresRuntimeLimits() throws Exception {
    String config = repositoryText("core/src/main/resources/config/default-config.yml");

    assertTrue(config.contains("rate-limit-per-minute: 300"));
    assertTrue(config.contains("max-websocket-connections: 50"));
  }

  @Test
  void transportConfigDefaultsMatchQueuedLimits() {
    TransportConfig defaults =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();

    assertEquals(1000, defaults.maxWebSocketQueuedMessages());
    assertEquals(2 * 1024 * 1024, defaults.maxWebSocketQueuedBytes());
  }

  @Test
  void websocketSendTextIsNonBlockingAndBounded() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .maxWebSocketQueuedMessages(1)
            .maxWebSocketQueuedBytes(1024)
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
    NettyTransportServer server = newServer(config, listener);
    EventLoopGroup clientGroup = new NioEventLoopGroup(1);
    try {
      server.start();
      webSocketHandshakeOnly(clientGroup, server.port());
      assertTrue(
          attempted.await(10L, TimeUnit.SECONDS), "sendText must not block producer threads");
      assertEquals(1, accepted.get());
      assertEquals(4, rejected.get());
    } finally {
      clientGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
      server.stop();
    }
  }

  @Test
  void specRequiresRateLimitingButNoProductImplementationExists() throws Exception {
    String section = part38();
    String[] required = {
      "token bucket per (identity, endpoint-class) and per (ip, auth-failure)",
      "refill continuously",
      "respond 429 with Retry-After",
      "bounded key count (LRU eviction)",
      "per-connection bounded queue",
      "producer never blocks server threads",
      "if queue > high-water: coalesce (console lines) or drop oldest",
      "send console.gap{count, fromSeq, toSeq}",
      "if queue stays > limit for N seconds -> close with slow-consumer code"
    };
    for (String fragment : required) {
      assertTrue(section.contains(fragment), "PART 33.8 must require: " + fragment);
    }

    List<String> markers = productionImplementationMarkers();
    assertTrue(markers.isEmpty(), "no product rate limiter is implemented: " + markers);
  }

  private static NettyTransportServer newServer(
      TransportConfig config, WebSocketListener listener) {
    Authenticator authenticator =
        header ->
            ("Bearer " + TOKEN).equals(header)
                ? AuthenticationResult.authenticated(
                    new Identity("test", "test", "token", true, null))
                : AuthenticationResult.failed(AuthenticationFailure.INVALID);
    return new NettyTransportServer(
        config,
        request -> HttpResponse.json(200, "{}"),
        listener,
        permissivePolicy(),
        new NoopLogger(),
        authenticator,
        TransportMessages.of(Locale.ENGLISH));
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

  private static void webSocketHandshakeOnly(EventLoopGroup group, int port) throws Exception {
    CountDownLatch handshake = new CountDownLatch(1);
    URI uri = URI.create("ws://127.0.0.1:" + port + "/api/v1/ws");
    WebSocketClientHandshaker handshaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            uri,
            WebSocketVersion.V13,
            null,
            true,
            new DefaultHttpHeaders().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + TOKEN),
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
      assertTrue(handshake.await(10L, TimeUnit.SECONDS), "WebSocket handshake did not complete");
    } finally {
      channel.close().syncUninterruptibly();
    }
  }

  private static String part38() throws IOException {
    String spec = repositoryText(SPEC);
    int start = spec.indexOf("## 33.8 Rate Limiting and Backpressure");
    assertTrue(start >= 0, "missing PART 33.8 section");
    int end = spec.indexOf("## 33.9 YAML and Archive Safety Limits", start);
    assertTrue(end > start, "missing PART 33.9 section boundary");
    return spec.substring(start, end);
  }

  private static List<String> productionImplementationMarkers() throws IOException {
    String[] markers = {
      "TokenBucket", "RateLimiter", "console.gap", "slow-consumer", "Retry-After"
    };
    List<String> matches = new ArrayList<String>();
    for (String module : new String[] {"core", "transport"}) {
      Path root = repoRoot().resolve(module).resolve("src").resolve("main").resolve("java");
      try (Stream<Path> paths = Files.walk(root)) {
        List<Path> files = new ArrayList<Path>();
        paths.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
        for (Path file : files) {
          String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
          for (String marker : markers) {
            if (text.contains(marker)) {
              matches.add(module + ":" + root.relativize(file) + " (" + marker + ")");
            }
          }
        }
      }
    }
    return matches;
  }

  private static String repositoryText(String relativePath) throws IOException {
    Path path = repoRoot().resolve(relativePath);
    assertTrue(Files.isRegularFile(path), "Missing repository file: " + relativePath);
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private static Path repoRoot() {
    Path current = Paths.get("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("specification/api/openapi.yaml"))
          && Files.isDirectory(current.resolve("transport"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("Repository root not found from " + Paths.get("").toAbsolutePath());
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
