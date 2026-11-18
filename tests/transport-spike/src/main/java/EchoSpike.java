import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class EchoSpike {
  private static final int TIMEOUT_SECONDS = 30;

  private EchoSpike() {}

  public static void main(String[] args) throws Exception {
    int messages = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
    int payloadBytes = args.length > 1 ? Integer.parseInt(args[1]) : 256;
    EventLoopGroup serverBoss = new NioEventLoopGroup(1);
    EventLoopGroup serverWorkers = new NioEventLoopGroup(2);
    EventLoopGroup clientWorkers = new NioEventLoopGroup(2);
    Channel serverChannel = null;
    Channel clientChannel = null;
    try {
      ServerBootstrap server = new ServerBootstrap();
      server
          .group(serverBoss, serverWorkers)
          .channel(NioServerSocketChannel.class)
          .childHandler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                  ChannelPipeline pipeline = channel.pipeline();
                  pipeline.addLast(new HttpServerCodec(4096, 8192, 8192));
                  pipeline.addLast(new HttpObjectAggregator(8192));
                  pipeline.addLast(new WebSocketServerProtocolHandler("/echo", null, true, 8192));
                  pipeline.addLast(
                      new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                        @Override
                        protected void channelRead0(
                            ChannelHandlerContext context, TextWebSocketFrame frame) {
                          context.writeAndFlush(new TextWebSocketFrame(frame.content().retain()));
                        }
                      });
                }
              });
      serverChannel = server.bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
      int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

      CountDownLatch handshake = new CountDownLatch(1);
      CountDownLatch echoes = new CountDownLatch(messages);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Bootstrap client = new Bootstrap();
      client
          .group(clientWorkers)
          .channel(NioSocketChannel.class)
          .handler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                  ChannelPipeline pipeline = channel.pipeline();
                  pipeline.addLast(new HttpClientCodec());
                  pipeline.addLast(new HttpObjectAggregator(8192));
                  pipeline.addLast(
                      new WebSocketClientProtocolHandler(
                          WebSocketClientHandshakerFactory.newHandshaker(
                              URI.create("ws://127.0.0.1:" + port + "/echo"),
                              WebSocketVersion.V13,
                              null,
                              true,
                              new DefaultHttpHeaders(),
                              8192)));
                  pipeline.addLast(
                      new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(
                            ChannelHandlerContext context, Object event) throws Exception {
                          if (event == ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                            handshake.countDown();
                          }
                          super.userEventTriggered(context, event);
                        }

                        @Override
                        public void exceptionCaught(
                            ChannelHandlerContext context, Throwable cause) {
                          failure.compareAndSet(null, cause);
                          handshake.countDown();
                          context.close();
                        }
                      });
                  pipeline.addLast(
                      new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                        @Override
                        protected void channelRead0(
                            ChannelHandlerContext context, TextWebSocketFrame frame) {
                          echoes.countDown();
                        }

                        @Override
                        public void exceptionCaught(
                            ChannelHandlerContext context, Throwable cause) {
                          failure.compareAndSet(null, cause);
                          context.close();
                        }
                      });
                }
              });
      clientChannel = client.connect("127.0.0.1", port).sync().channel();
      if (!handshake.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("WebSocket handshake timed out");
      }
      if (failure.get() != null) {
        throw new IllegalStateException("WebSocket handshake failed", failure.get());
      }

      byte[] payload = new byte[payloadBytes];
      for (int index = 0; index < payload.length; index++) {
        payload[index] = (byte) ('a' + (index % 26));
      }
      long started = System.nanoTime();
      for (int index = 0; index < messages; index++) {
        clientChannel.writeAndFlush(new TextWebSocketFrame(Unpooled.wrappedBuffer(payload)));
      }
      if (!echoes.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("WebSocket echo timed out");
      }
      long elapsedNanos = System.nanoTime() - started;
      if (failure.get() != null) {
        throw new IllegalStateException("WebSocket echo failed", failure.get());
      }
      double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
      double msgPerSecond = messages / elapsedSeconds;
      double mibPerSecond = (messages * (double) payloadBytes) / elapsedSeconds / 1048576.0;
      System.out.printf(
          "echo messages=%d payloadBytes=%d elapsedMs=%.1f messagesPerSecond=%.0f mibPerSecond=%.2f%n",
          messages, payloadBytes, elapsedSeconds * 1000.0, msgPerSecond, mibPerSecond);
    } finally {
      if (clientChannel != null) {
        clientChannel.close().sync();
      }
      if (serverChannel != null) {
        serverChannel.close().sync();
      }
      clientWorkers.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
      serverWorkers.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
      serverBoss.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
    }
  }
}
