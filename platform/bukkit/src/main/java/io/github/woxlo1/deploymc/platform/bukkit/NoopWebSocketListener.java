package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.transport.WebSocketListener;
import io.github.woxlo1.deploymc.transport.WebSocketSession;

/** v1 has no WebSocket application behavior; the transport still authenticates upgrades. */
public final class NoopWebSocketListener implements WebSocketListener {

  @Override
  public void onOpen(WebSocketSession session) {}

  @Override
  public void onText(WebSocketSession session, String text) {}

  @Override
  public void onClose(WebSocketSession session) {}
}
