package io.github.woxlo1.deploymc.core.spi;

import java.util.function.Consumer;

/** Platform-independent access to server logs and command dispatch. */
public interface ConsoleBridge {

  /**
   * Subscribes to new console lines.
   *
   * @return a subscription that must be closed to detach the listener
   */
  AutoCloseable subscribe(Consumer<ConsoleLine> listener);

  /** Dispatches a command on the correct platform thread. */
  void dispatchCommand(String command);
}
