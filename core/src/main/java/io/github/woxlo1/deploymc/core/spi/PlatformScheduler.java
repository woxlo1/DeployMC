package io.github.woxlo1.deploymc.core.spi;

import java.util.concurrent.TimeUnit;

/** Platform-independent scheduling abstraction. */
public interface PlatformScheduler {

  /** Runs a task on the platform's main/global thread. */
  void runGlobal(Runnable task);

  /** Runs a task on a background executor that must never be a server thread. */
  void runAsync(Runnable task);

  /**
   * Runs a task after a delay.
   *
   * @return a handle that can be cancelled
   */
  ScheduledTask runLater(Runnable task, long delay, TimeUnit unit);

  /** Cancels all tasks owned by this scheduler and rejects new submissions. */
  void shutdown();
}
