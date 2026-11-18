package io.github.woxlo1.deploymc.transport;

/** Catalog keys for transport-side log messages. */
public final class MessageKeys {

  public static final String STARTED = "transport.started";
  public static final String STOPPED = "transport.stopped";
  public static final String WARNING_NON_LOOPBACK = "transport.warning.nonLoopback";
  public static final String REQUEST_COMPLETED = "transport.request.completed";
  public static final String REQUEST_REJECTED = "transport.request.rejected";
  public static final String WEBSOCKET_OPENED = "transport.websocket.opened";
  public static final String WEBSOCKET_CLOSED = "transport.websocket.closed";
  public static final String WEBSOCKET_REJECTED = "transport.websocket.rejected";
  public static final String WEBSOCKET_QUEUE_FULL = "transport.websocket.queueFull";

  private MessageKeys() {}
}
