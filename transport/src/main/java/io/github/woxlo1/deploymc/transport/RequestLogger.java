package io.github.woxlo1.deploymc.transport;

/** Redacted logging hook for transport lifecycle, requests, and WebSocket events. */
public interface RequestLogger {

  void started(String address);

  void stopped();

  void nonLoopbackWarning(String address);

  void log(RequestLogEntry entry);

  void rejected(String requestId, String reason);

  void websocketRejected(String remoteAddress, String reason);

  void websocketOpened(String connectionId, String remoteAddress);

  void websocketClosed(String connectionId);

  void websocketQueueFull(String connectionId);
}
