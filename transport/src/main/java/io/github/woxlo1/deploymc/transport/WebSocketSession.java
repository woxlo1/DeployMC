package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.AuthContext;

/** Bounded outbound view of one WebSocket connection. */
public interface WebSocketSession {

  String id();

  String remoteAddress();

  /** Explicit identity context captured at the authenticated upgrade, safe for async callbacks. */
  AuthContext context();

  /** Queues one text frame. Returns false when the bounded queue is full. */
  boolean sendText(String text);

  /** Requests an orderly close. */
  void close();
}
