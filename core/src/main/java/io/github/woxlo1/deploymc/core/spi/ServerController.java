package io.github.woxlo1.deploymc.core.spi;

/** Platform-independent server lifecycle operations that a platform can support. */
public interface ServerController {

  /** Requests a world save on the correct platform thread. */
  void saveAll();

  /**
   * Requests a shutdown. The method must not return until the request has been accepted by the
   * platform, not until the process has exited.
   */
  void shutdown(String reason);

  /** Returns true when an external restart mechanism is known to be configured. */
  boolean restartSupported();
}
