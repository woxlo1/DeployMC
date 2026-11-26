package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationFilter;
import io.github.woxlo1.deploymc.core.auth.AuthenticationRequest;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.AuthorizationDeniedException;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.AuthorizationDecision;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Netty-backed HTTP and WebSocket transport. No Netty type crosses the internal interfaces. */
public final class NettyTransportServer implements HttpTransport, WebSocketTransport {

  private static final AttributeKey<String> REQUEST_ID =
      AttributeKey.valueOf("deploymc.transport.requestId");
  private static final AttributeKey<NettyWebSocketSession> SESSION =
      AttributeKey.valueOf("deploymc.transport.websocketSession");
  private static final AttributeKey<AuthContext> AUTH_CONTEXT =
      AttributeKey.valueOf("deploymc.transport.authContext");

  private static final int WEBSOCKET_PERMISSION_REVOKED = 4403;
  private static final String PERMISSION_REVOKED_REASON = "rbac.permission-revoked";
  private static final String FORBIDDEN = "forbidden";
  private static final String OVERSIZED_BODY = "oversized_body";
  private static final String INTERNAL_ERROR = "internal_error";
  private static final String TRANSPORT_ERROR = "transport_error";
  private static final String HEALTHZ = "/healthz";
  private static final String READYZ = "/readyz";
  private static final String REDACTED = "<redacted>";
  private static final Logger WATCHDOG_LOGGER =
      Logger.getLogger("io.github.woxlo1.deploymc.transport.watchdog");

  private final TransportConfig config;
  private final HttpHandler httpHandler;
  private final WebSocketListener webSocketListener;
  private final TransportPolicy policy;
  private final RequestLogger logger;
  private final AuthenticationFilter authenticationFilter;
  private final TransportMessages messages;
  private final RbacGate gate;
  private final ObservabilityConfig observability;
  private final MetricsRegistry metrics;
  private final RateLimiter rateLimiter;
  private final MetricsHandler metricsHandler;
  private final AtomicInteger webSocketConnections = new AtomicInteger();
  private final Set<Channel> channels =
      Collections.newSetFromMap(new ConcurrentHashMap<Channel, Boolean>());

  private volatile ExecutorWatchdog watchdog;
  private volatile ScheduledExecutorService watchdogScheduler;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;
  private volatile boolean running;
  private volatile int port = -1;

  public NettyTransportServer(
      TransportConfig config,
      HttpHandler httpHandler,
      WebSocketListener webSocketListener,
      TransportPolicy policy,
      RequestLogger logger) {
    this(
        config,
        httpHandler,
        webSocketListener,
        policy,
        logger,
        null,
        TransportMessages.of(Locale.ENGLISH));
  }

  public NettyTransportServer(
      TransportConfig config,
      HttpHandler httpHandler,
      WebSocketListener webSocketListener,
      TransportPolicy policy,
      RequestLogger logger,
      Authenticator authenticator,
      TransportMessages messages) {
    this(
        config,
        httpHandler,
        webSocketListener,
        policy,
        logger,
        authenticator,
        messages,
        null,
        new MetricsRegistry());
  }

  public NettyTransportServer(
      TransportConfig config,
      HttpHandler httpHandler,
      WebSocketListener webSocketListener,
      TransportPolicy policy,
      RequestLogger logger,
      Authenticator authenticator,
      TransportMessages messages,
      MetricsRegistry metrics) {
    this(
        config,
        httpHandler,
        webSocketListener,
        policy,
        logger,
        authenticator,
        messages,
        null,
        metrics);
  }

  /** Creates a transport whose authorization decisions use the supplied evaluator. */
  public NettyTransportServer(
      TransportConfig config,
      HttpHandler httpHandler,
      WebSocketListener webSocketListener,
      TransportPolicy policy,
      RequestLogger logger,
      Authenticator authenticator,
      TransportMessages messages,
      RbacEvaluator evaluator) {
    this(
        config,
        httpHandler,
        webSocketListener,
        policy,
        logger,
        authenticator,
        messages,
        evaluator,
        new MetricsRegistry());
  }

  public NettyTransportServer(
      TransportConfig config,
      HttpHandler httpHandler,
      WebSocketListener webSocketListener,
      TransportPolicy policy,
      RequestLogger logger,
      Authenticator authenticator,
      TransportMessages messages,
      RbacEvaluator evaluator,
      MetricsRegistry metrics) {
    this.config = Objects.requireNonNull(config, "config");
    this.httpHandler = Objects.requireNonNull(httpHandler, "httpHandler");
    this.webSocketListener = Objects.requireNonNull(webSocketListener, "webSocketListener");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.authenticationFilter = new AuthenticationFilter(authenticator);
    this.messages = Objects.requireNonNull(messages, "messages");
    this.observability = config.observability();
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.gate = new RbacGate(evaluator, observability.metricsPermission());
    this.rateLimiter =
        new RateLimiter(
            observability.identityRateLimitPerMinute(),
            observability.authFailureRateLimitPerMinute(),
            observability.maxRateLimitKeys(),
            Clock.systemUTC());
    this.metricsHandler =
        new MetricsHandler(metrics, observability.metricsPermission(), evaluator, messages);
  }

  /** Registry used by this transport instance, for host-supplied gauges. */
  public MetricsRegistry metrics() {
    return metrics;
  }

  public HttpTransport http() {
    return this;
  }

  public WebSocketTransport webSocket() {
    return this;
  }

  @Override
  public synchronized void start() {
    if (running) {
      throw new TransportException(TransportError.ALREADY_STARTED);
    }
    EventLoopGroup boss = namedGroup("boss", 1);
    EventLoopGroup worker = namedGroup("worker", config.workerThreads());
    bossGroup = boss;
    workerGroup = worker;
    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap
          .group(boss, worker)
          .channel(NioServerSocketChannel.class)
          .option(ChannelOption.SO_BACKLOG, 128)
          .childOption(ChannelOption.SO_KEEPALIVE, true)
          .childOption(ChannelOption.TCP_NODELAY, true)
          .childHandler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                  channel
                      .pipeline()
                      .addLast(
                          new IdleStateHandler(
                              config.idleTimeoutMillis(), 0, 0, TimeUnit.MILLISECONDS))
                      .addLast(new ConnectionTrackingHandler())
                      .addLast(
                          new HttpServerCodec(
                              config.maxHeaderBytes(),
                              config.maxHeaderBytes(),
                              config.maxHeaderBytes()))
                      .addLast(new LimitedHttpObjectAggregator(config.maxBodyBytes()))
                      .addLast(new ValidationHandler())
                      .addLast(new AuthenticationHandler())
                      .addLast(
                          new WebSocketServerProtocolHandler(
                              config.webSocketPath(), null, false, config.maxWebSocketFrameBytes()))
                      .addLast(new HttpRequestHandler())
                      .addLast(new WebSocketFrameHandler());
                }
              });
      ChannelFuture future =
          bootstrap
              .bind(new InetSocketAddress(config.bind().address(), config.bind().port()))
              .sync();
      serverChannel = future.channel();
      InetSocketAddress local = (InetSocketAddress) serverChannel.localAddress();
      port = local.getPort();
      startWatchdog(worker);
      running = true;
      logger.started(config.bind().address() + ":" + port);
      if (!config.bind().isLoopbackOnly()) {
        logger.nonLoopbackWarning(config.bind().address());
      }
    } catch (Exception exception) {
      stopWatchdog();
      shutdownGroups(boss, worker);
      bossGroup = null;
      workerGroup = null;
      if (hasBindException(exception)) {
        throw new TransportException(TransportError.PORT_IN_USE, exception);
      }
      throw new TransportException(TransportError.START_FAILED, exception);
    }
  }

  private void startWatchdog(EventLoopGroup worker) {
    watchdogScheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "DeployMC-watchdog-transport");
              thread.setDaemon(false);
              return thread;
            });
    final ExecutorWatchdog[] holder = new ExecutorWatchdog[1];
    holder[0] =
        new ExecutorWatchdog(
            "transport",
            watchdogScheduler,
            token -> worker.execute(() -> holder[0].acknowledge(token)),
            metrics,
            WATCHDOG_LOGGER,
            messages,
            observability.watchdogIntervalMillis(),
            observability.watchdogTimeoutMillis());
    watchdog = holder[0];
    holder[0].start();
  }

  private void stopWatchdog() {
    ExecutorWatchdog current = watchdog;
    if (current != null) {
      current.stop();
      watchdog = null;
    }
    ScheduledExecutorService scheduler = watchdogScheduler;
    if (scheduler != null) {
      scheduler.shutdownNow();
      try {
        scheduler.awaitTermination(1L, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      watchdogScheduler = null;
    }
  }

  @Override
  public synchronized void stop() {
    if (!running && bossGroup == null && workerGroup == null) {
      return;
    }
    stopWatchdog();
    if (serverChannel != null) {
      serverChannel.close().syncUninterruptibly();
      serverChannel = null;
    }
    for (Channel channel : channels) {
      channel.close();
    }
    channels.clear();
    boolean clean = shutdownGroups(bossGroup, workerGroup);
    bossGroup = null;
    workerGroup = null;
    running = false;
    port = -1;
    if (clean) {
      logger.stopped();
    } else {
      throw new TransportException(TransportError.SHUTDOWN_FAILED);
    }
  }

  @Override
  public int port() {
    return port;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private static boolean hasBindException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof BindException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private EventLoopGroup namedGroup(String role, int threads) {
    final AtomicLong sequence = new AtomicLong();
    return new NioEventLoopGroup(
        threads,
        runnable -> {
          Thread thread =
              new Thread(runnable, "DeployMC-netty-" + role + "-" + sequence.incrementAndGet());
          thread.setDaemon(false);
          return thread;
        });
  }

  private boolean shutdownGroups(EventLoopGroup boss, EventLoopGroup worker) {
    boolean clean = true;
    if (worker != null) {
      worker.shutdownGracefully(0, config.shutdownTimeoutMillis(), TimeUnit.MILLISECONDS);
    }
    if (boss != null) {
      boss.shutdownGracefully(0, config.shutdownTimeoutMillis(), TimeUnit.MILLISECONDS);
    }
    if (worker != null) {
      clean &= awaitTermination(worker);
    }
    if (boss != null) {
      clean &= awaitTermination(boss);
    }
    return clean;
  }

  private boolean awaitTermination(EventLoopGroup group) {
    try {
      return group.awaitTermination(config.shutdownTimeoutMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void writeResponse(ChannelHandlerContext context, HttpResponse original) {
    HttpResponse response = original;
    if (!hasHeader(response, TransportProblems.REQUEST_ID)) {
      response = response.withHeader(TransportProblems.REQUEST_ID, requestId(context));
    }
    int status = response.status();
    if (status < 100 || status > 599) {
      status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
    }
    FullHttpResponse outbound =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(status),
            Unpooled.copiedBuffer(response.body()));
    for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
      for (String value : entry.getValue()) {
        outbound.headers().add(entry.getKey(), value);
      }
    }
    applySecurityHeaders(outbound.headers());
    HttpUtil.setContentLength(outbound, outbound.content().readableBytes());
    context.writeAndFlush(outbound).addListener(ChannelFutureListener.CLOSE);
  }

  private static boolean hasHeader(HttpResponse response, String name) {
    for (String key : response.headers().keySet()) {
      if (name.equalsIgnoreCase(key)) {
        return true;
      }
    }
    return false;
  }

  private static void applySecurityHeaders(HttpHeaders headers) {
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("X-Frame-Options", "DENY");
    headers.set("Referrer-Policy", "no-referrer");
    if (!headers.contains(HttpHeaderNames.CACHE_CONTROL)) {
      headers.set(HttpHeaderNames.CACHE_CONTROL, "no-store");
    }
    headers.set("Cross-Origin-Opener-Policy", "same-origin");
    headers.set("Cross-Origin-Resource-Policy", "same-origin");
    if (!headers.contains("Content-Security-Policy")) {
      headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    }
  }

  private HttpRequest toInternal(FullHttpRequest request, String id, AuthContext context) {
    String uri = request.uri();
    int queryIndex = uri.indexOf('?');
    String path = queryIndex < 0 ? uri : uri.substring(0, queryIndex);
    String query = queryIndex < 0 ? "" : uri.substring(queryIndex + 1);
    ByteBuf content = request.content();
    byte[] body = new byte[content.readableBytes()];
    content.getBytes(content.readerIndex(), body);
    return HttpRequest.withContext(
        id, request.method().name(), path, query, copyHeaders(request), body, context);
  }

  private AuthenticationRequest toAuthenticationRequest(FullHttpRequest request, String id) {
    String uri = request.uri();
    int queryIndex = uri.indexOf('?');
    String path = queryIndex < 0 ? uri : uri.substring(0, queryIndex);
    return new AuthenticationRequest(id, request.method().name(), path, copyHeaders(request));
  }

  private static Map<String, List<String>> copyHeaders(FullHttpRequest request) {
    Map<String, List<String>> headers = new HashMap<>();
    for (String name : request.headers().names()) {
      List<String> values = new ArrayList<>();
      for (String value : request.headers().getAll(name)) {
        values.add(value);
      }
      headers.put(name.toLowerCase(Locale.ROOT), values);
    }
    return headers;
  }

  private boolean isWebSocketUpgrade(FullHttpRequest request) {
    return request.uri().equals(config.webSocketPath())
        && request.headers().contains(HttpHeaderNames.UPGRADE);
  }

  private static String path(String uri) {
    int queryIndex = uri.indexOf('?');
    return queryIndex < 0 ? uri : uri.substring(0, queryIndex);
  }

  private EndpointClass classify(String requestPath) {
    return EndpointClass.fromPath(requestPath, config.webSocketPath());
  }

  private HttpResponse unauthorized(String requestId, AuthenticationFailure failure) {
    if (failure == AuthenticationFailure.INTERNAL_ERROR) {
      return HttpResponse.problem(
          HttpResponseStatus.INTERNAL_SERVER_ERROR.code(),
          failure.code(),
          messages.format(MessageKeys.INTERNAL_ERROR_TITLE),
          messages.format(MessageKeys.INTERNAL_ERROR_DETAIL),
          requestId,
          true,
          Collections.<HttpResponse.ProblemIssue>emptyList());
    }
    if (failure == AuthenticationFailure.JWKS_UNAVAILABLE) {
      HttpResponse unavailable =
          HttpResponse.problem(
              HttpResponseStatus.SERVICE_UNAVAILABLE.code(),
              failure.code(),
              messages.format(MessageKeys.AUTH_JWKS_UNAVAILABLE_TITLE),
              messages.format(MessageKeys.AUTH_JWKS_UNAVAILABLE_DETAIL),
              requestId,
              true,
              Collections.<HttpResponse.ProblemIssue>emptyList());
      return unavailable.withHeader(HttpHeaderNames.RETRY_AFTER.toString(), "5");
    }
    String titleKey;
    String detailKey;
    switch (failure) {
      case INVALID:
        titleKey = MessageKeys.AUTH_INVALID_TITLE;
        detailKey = MessageKeys.AUTH_INVALID_DETAIL;
        break;
      case REVOKED:
        titleKey = MessageKeys.AUTH_REVOKED_TITLE;
        detailKey = MessageKeys.AUTH_REVOKED_DETAIL;
        break;
      case EXPIRED:
        titleKey = MessageKeys.AUTH_EXPIRED_TITLE;
        detailKey = MessageKeys.AUTH_EXPIRED_DETAIL;
        break;
      case NOT_YET_VALID:
        titleKey = MessageKeys.AUTH_NOT_YET_VALID_TITLE;
        detailKey = MessageKeys.AUTH_NOT_YET_VALID_DETAIL;
        break;
      case ONE_TIME_CONSUMED:
        titleKey = MessageKeys.AUTH_ONE_TIME_CONSUMED_TITLE;
        detailKey = MessageKeys.AUTH_ONE_TIME_CONSUMED_DETAIL;
        break;
      case ONE_TIME_EXPIRED:
        titleKey = MessageKeys.AUTH_ONE_TIME_EXPIRED_TITLE;
        detailKey = MessageKeys.AUTH_ONE_TIME_EXPIRED_DETAIL;
        break;
      case REQUIRED:
      default:
        titleKey = MessageKeys.AUTH_REQUIRED_TITLE;
        detailKey = MessageKeys.AUTH_REQUIRED_DETAIL;
        break;
    }
    HttpResponse response =
        HttpResponse.problem(
            HttpResponseStatus.UNAUTHORIZED.code(),
            failure.code(),
            messages.format(titleKey),
            messages.format(detailKey),
            requestId);
    return response.withHeader(HttpHeaderNames.WWW_AUTHENTICATE.toString(), "Bearer");
  }

  private String requestId(ChannelHandlerContext context) {
    String id = context.channel().attr(REQUEST_ID).get();
    return id == null ? UUID.randomUUID().toString() : id;
  }

  private final class ConnectionTrackingHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext context) {
      if (channels.size() >= config.maxConnections() || !channels.add(context.channel())) {
        logger.rejected(requestId(context), TransportProblems.SERVER_BUSY_CODE);
        writeResponse(context, TransportProblems.serverBusy(requestId(context), messages));
        return;
      }
      context.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
      channels.remove(context.channel());
      NettyWebSocketSession session = context.channel().attr(SESSION).get();
      if (session != null) {
        context.channel().attr(SESSION).set(null);
        session.markClosed();
        webSocketConnections.decrementAndGet();
        metrics.setWebSocketConnections(Math.max(0, webSocketConnections.get()));
        logger.websocketClosed(session.id());
      }
      context.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) {
      if (event instanceof IdleStateEvent) {
        context.close();
        return;
      }
      context.fireUserEventTriggered(event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
      logger.rejected(requestId(context), TRANSPORT_ERROR);
      context.close();
    }
  }

  private final class ValidationHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
      if (!(message instanceof FullHttpRequest)) {
        context.fireChannelRead(message);
        return;
      }
      FullHttpRequest request = (FullHttpRequest) message;
      String id = UUID.randomUUID().toString();
      context.channel().attr(REQUEST_ID).set(id);
      String host = request.headers().get(HttpHeaderNames.HOST);
      if (!policy.allowHost(host)) {
        rejectRequest(context, request, id, HttpResponseStatus.FORBIDDEN.code(), FORBIDDEN);
        return;
      }
      String origin = request.headers().get(HttpHeaderNames.ORIGIN);
      if (!policy.allowOrigin(origin)) {
        rejectRequest(context, request, id, HttpResponseStatus.FORBIDDEN.code(), FORBIDDEN);
        return;
      }
      context.fireChannelRead(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
      String id = requestId(context);
      if (cause instanceof TooLongFrameException) {
        logger.rejected(id, OVERSIZED_BODY);
        writeResponse(
            context,
            HttpResponse.error(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.code(), OVERSIZED_BODY));
        return;
      }
      logger.rejected(id, TRANSPORT_ERROR);
      context.close();
    }

    private void rejectRequest(
        ChannelHandlerContext context,
        FullHttpRequest request,
        String id,
        int status,
        String code) {
      if (isWebSocketUpgrade(request)) {
        logger.websocketRejected(REDACTED, code);
      } else {
        logger.rejected(id, code);
      }
      ReferenceCountUtil.release(request);
      writeResponse(context, HttpResponse.error(status, code));
    }
  }

  private final class AuthenticationHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
      if (!(message instanceof FullHttpRequest)) {
        context.fireChannelRead(message);
        return;
      }
      FullHttpRequest request = (FullHttpRequest) message;
      String id = requestId(context);
      context.channel().attr(AUTH_CONTEXT).set(null);
      String requestPath = path(request.uri());
      if (HEALTHZ.equals(requestPath) || READYZ.equals(requestPath)) {
        context.channel().attr(AUTH_CONTEXT).set(AuthContext.anonymous(id));
        context.fireChannelRead(message);
        return;
      }
      EndpointClass endpoint = classify(requestPath);
      AuthenticationResult result;
      try {
        result = authenticationFilter.filter(toAuthenticationRequest(request, id));
      } catch (RuntimeException exception) {
        result = AuthenticationResult.failed(AuthenticationFailure.INVALID);
      }
      if (result.authenticated()) {
        Identity identity = result.identity();
        RateLimitDecision limit = rateLimiter.tryIdentity(identity.id(), endpoint);
        if (!limit.allowed()) {
          shed(context, request, limit, ShedScope.IDENTITY);
          return;
        }
        AuthContext authContext = authenticationFilter.context(id, result);
        AuthorizationDecision authorization =
            endpoint == EndpointClass.WEBSOCKET
                ? gate.authorizeWebSocket(identity)
                : gate.authorize(toInternal(request, id, authContext));
        if (!authorization.allowed()) {
          rejectAuthorization(context, request, authorization);
          return;
        }
        if (endpoint == EndpointClass.WEBSOCKET
            && webSocketConnections.get() >= config.maxWebSocketConnections()) {
          rejectBusy(context, request);
          return;
        }
        context.channel().attr(AUTH_CONTEXT).set(authContext);
        context.fireChannelRead(message);
        return;
      }
      rejectAuthentication(context, request, result.failure());
    }

    private void rejectAuthentication(
        ChannelHandlerContext context, FullHttpRequest request, AuthenticationFailure failure) {
      AuthenticationFailure reason = failure == null ? AuthenticationFailure.REQUIRED : failure;
      metrics.recordAuthFailure(AuthFailureReason.from(reason));
      String remoteKey = String.valueOf(context.channel().remoteAddress());
      RateLimitDecision decision = rateLimiter.tryAuthFailure(remoteKey);
      if (!decision.allowed()) {
        shed(context, request, decision, ShedScope.AUTH_FAILURE);
        return;
      }
      String id = requestId(context);
      if (isWebSocketUpgrade(request)) {
        logger.websocketRejected(REDACTED, reason.code());
      } else {
        logger.rejected(id, reason.code());
      }
      ReferenceCountUtil.release(request);
      writeResponse(context, unauthorized(id, reason));
    }

    private void rejectAuthorization(
        ChannelHandlerContext context, FullHttpRequest request, AuthorizationDecision decision) {
      String code =
          decision.errorCode() == null
              ? TransportProblems.AUTHORIZATION_DENIED_CODE
              : decision.errorCode();
      if (isWebSocketUpgrade(request)) {
        logger.websocketRejected(REDACTED, code);
      } else {
        logger.rejected(requestId(context), code);
      }
      ReferenceCountUtil.release(request);
      writeResponse(
          context, TransportProblems.authorizationDenied(requestId(context), code, messages));
    }

    private void shed(
        ChannelHandlerContext context,
        FullHttpRequest request,
        RateLimitDecision decision,
        ShedScope scope) {
      metrics.recordShed(scope);
      String id = requestId(context);
      if (isWebSocketUpgrade(request)) {
        logger.websocketRejected(REDACTED, TransportProblems.RATE_LIMITED_CODE);
      } else {
        logger.rejected(id, TransportProblems.RATE_LIMITED_CODE);
      }
      ReferenceCountUtil.release(request);
      writeResponse(context, TransportProblems.rateLimited(decision, id, messages));
    }

    private void rejectBusy(ChannelHandlerContext context, FullHttpRequest request) {
      String id = requestId(context);
      logger.websocketRejected(REDACTED, TransportProblems.SERVER_BUSY_CODE);
      ReferenceCountUtil.release(request);
      writeResponse(context, TransportProblems.serverBusy(id, messages));
    }
  }

  private final class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
      String id = requestId(context);
      long started = System.nanoTime();
      HttpRequest internal;
      try {
        AuthContext authContext = context.channel().attr(AUTH_CONTEXT).get();
        context.channel().attr(AUTH_CONTEXT).set(null);
        if (authContext == null || !id.equals(authContext.requestId())) {
          authContext = AuthContext.anonymous(id);
        }
        internal = toInternal(request, id, authContext);
      } finally {
        ReferenceCountUtil.release(request);
      }
      EndpointClass endpoint = classify(internal.path());
      HttpResponse response;
      try {
        if (endpoint == EndpointClass.METRICS && observability.metricsEnabled()) {
          response = metricsHandler.handle(internal);
        } else {
          response = httpHandler.handle(internal);
        }
      } catch (AuthorizationDeniedException denied) {
        response = TransportProblems.authorizationDenied(id, denied.code(), messages);
      } catch (Exception exception) {
        response =
            HttpResponse.error(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), INTERNAL_ERROR);
      }
      if (response == null) {
        response =
            HttpResponse.error(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), INTERNAL_ERROR);
      }
      long durationNanos = System.nanoTime() - started;
      metrics.recordRequest(endpoint, durationNanos, response.status());
      writeResponse(context, response);
      logger.log(
          new RequestLogEntry(
              id,
              internal.method(),
              endpoint.wireLabel(),
              response.status(),
              durationNanos / 1_000_000L,
              "completed"));
    }
  }

  private final class WebSocketFrameHandler
      extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private WebSocketFrameHandler() {
      super(false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void userEventTriggered(ChannelHandlerContext context, Object event) {
      if (event == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
        String id = "ws-" + UUID.randomUUID();
        AuthContext authContext = context.channel().attr(AUTH_CONTEXT).get();
        context.channel().attr(AUTH_CONTEXT).set(null);
        if (authContext == null) {
          authContext = AuthContext.anonymous(id);
        }
        AuthorizationDecision decision = gate.authorizeWebSocket(authContext.identity());
        if (!decision.allowed()) {
          closeForPermissionLoss(context, decision);
          return;
        }
        NettyWebSocketSession session = new NettyWebSocketSession(context, id, authContext);
        context.channel().attr(SESSION).set(session);
        webSocketConnections.incrementAndGet();
        metrics.setWebSocketConnections(webSocketConnections.get());
        logger.websocketOpened(id, REDACTED);
        webSocketListener.onOpen(session);
        return;
      }
      context.fireUserEventTriggered(event);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
      NettyWebSocketSession session = context.channel().attr(SESSION).get();
      AuthorizationDecision decision =
          session == null ? null : gate.authorizeWebSocket(session.context().identity());
      if (session == null || !decision.allowed()) {
        ReferenceCountUtil.release(frame);
        closeForPermissionLoss(context, decision);
        return;
      }
      try {
        webSocketListener.onText(session, frame.text());
      } finally {
        ReferenceCountUtil.release(frame);
      }
    }

    private void closeForPermissionLoss(
        ChannelHandlerContext context, AuthorizationDecision decision) {
      String code =
          decision == null || decision.errorCode() == null
              ? TransportProblems.AUTHORIZATION_DENIED_CODE
              : decision.errorCode();
      logger.websocketRejected(REDACTED, code);
      CloseWebSocketFrame close =
          new CloseWebSocketFrame(WEBSOCKET_PERMISSION_REVOKED, PERMISSION_REVOKED_REASON);
      context.writeAndFlush(close).addListener(ChannelFutureListener.CLOSE);
      context.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
      context.close();
    }
  }

  private final class LimitedHttpObjectAggregator extends HttpObjectAggregator {

    private LimitedHttpObjectAggregator(int maxContentLength) {
      super(maxContentLength);
    }

    @Override
    protected void handleOversizedMessage(ChannelHandlerContext context, HttpMessage oversized) {
      logger.rejected(requestId(context), OVERSIZED_BODY);
      writeResponse(
          context,
          HttpResponse.error(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.code(), OVERSIZED_BODY));
    }
  }

  private final class NettyWebSocketSession implements WebSocketSession {

    private final ChannelHandlerContext context;
    private final String id;
    private final AuthContext authContext;
    private final WebSocketOutboundQueue outbound;

    private NettyWebSocketSession(
        ChannelHandlerContext context, String id, AuthContext authContext) {
      this.context = context;
      this.id = id;
      this.authContext = Objects.requireNonNull(authContext, "authContext");
      this.outbound =
          new WebSocketOutboundQueue(
              config,
              metrics,
              context.channel().eventLoop(),
              frame ->
                  context
                      .writeAndFlush(new TextWebSocketFrame(frame))
                      .addListener(ChannelFutureListener.CLOSE_ON_FAILURE),
              context.channel()::close,
              () -> logger.websocketQueueFull(id),
              () -> logger.rejected(id, MessageKeys.WEBSOCKET_SLOW_CONSUMER),
              System::nanoTime);
    }

    @Override
    public String id() {
      return id;
    }

    /** Redacted: remote addresses are internal rate-limit keys and never leave the transport. */
    @Override
    public String remoteAddress() {
      return REDACTED;
    }

    @Override
    public AuthContext context() {
      return authContext;
    }

    @Override
    public boolean sendText(String text) {
      return outbound.sendText(text);
    }

    @Override
    public void close() {
      outbound.close();
    }

    private void markClosed() {
      outbound.markClosed();
    }
  }
}
