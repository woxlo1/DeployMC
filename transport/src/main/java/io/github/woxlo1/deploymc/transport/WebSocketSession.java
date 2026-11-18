package io.github.woxlo1.deploymc.transport;

/** Bounded outbound view of one WebSocket connection. */
public interface WebSocketSession {

  String id();

  String remoteAddress();

  /** Queues one text frame. Returns false when the bounded queue is full. */
  boolean sendText(String text);

  /** Requests an orderly close. */
  void close();
}
