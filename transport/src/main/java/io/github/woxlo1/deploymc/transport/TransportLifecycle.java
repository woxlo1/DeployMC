package io.github.woxlo1.deploymc.transport;

/** Internal lifecycle contract shared by the HTTP and WebSocket transport views. */
public interface TransportLifecycle {

  /** Starts the listener. */
  void start();

  /** Stops the listener and releases all owned threads and sockets. */
  void stop();

  /** Returns the bound port, or {@code -1} when the listener is not bound. */
  int port();

  /** Returns whether the listener is running. */
  boolean isRunning();
}
