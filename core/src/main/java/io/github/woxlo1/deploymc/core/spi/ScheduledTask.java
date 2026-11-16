package io.github.woxlo1.deploymc.core.spi;

/** Handle for a scheduled task. */
public interface ScheduledTask {

  /** Attempts to cancel the task. Returns true when it was cancelled before starting. */
  boolean cancel();

  boolean isCancelled();
}
