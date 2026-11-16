package io.github.woxlo1.deploymc.core.spi;

import java.util.Objects;

/** Immutable console/log line captured from the server. */
public final class ConsoleLine {

  private final long timestampMillis;
  private final String level;
  private final String loggerName;
  private final String threadName;
  private final String message;

  public ConsoleLine(
      long timestampMillis, String level, String loggerName, String threadName, String message) {
    this.timestampMillis = timestampMillis;
    this.level = Objects.requireNonNull(level, "level");
    this.loggerName = Objects.requireNonNull(loggerName, "loggerName");
    this.threadName = Objects.requireNonNull(threadName, "threadName");
    this.message = Objects.requireNonNull(message, "message");
  }

  public long timestampMillis() {
    return timestampMillis;
  }

  public String level() {
    return level;
  }

  public String loggerName() {
    return loggerName;
  }

  public String threadName() {
    return threadName;
  }

  public String message() {
    return message;
  }
}
