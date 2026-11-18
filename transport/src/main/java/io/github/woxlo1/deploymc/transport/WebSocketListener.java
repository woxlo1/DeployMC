package io.github.woxlo1.deploymc.transport;

/** Application callback for WebSocket lifecycle and text frames. */
public interface WebSocketListener {

  void onOpen(WebSocketSession session);

  void onText(WebSocketSession session, String text);

  void onClose(WebSocketSession session);
}
