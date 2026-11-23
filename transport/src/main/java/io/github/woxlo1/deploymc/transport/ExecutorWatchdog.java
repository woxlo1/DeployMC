package io.github.woxlo1.deploymc.transport;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Observes executor responsiveness using one caller-provided scheduler task. A missed
 * acknowledgement records a stall and a redacted diagnostic; it never terminates the server
 * process.
 */
public final class ExecutorWatchdog {

  private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

  private final String name;
  private final ScheduledExecutorService scheduler;
  private final LongConsumer probe;
  private final MetricsRegistry metrics;
  private final Logger logger;
  private final TransportMessages messages;
  private final long intervalMillis;
  private final long timeoutMillis;
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicLong sequence = new AtomicLong();
  private final Set<Long> pending = Collections.synchronizedSet(new HashSet<Long>());
  private final Map<Long, ScheduledFuture<?>> timeouts =
      new ConcurrentHashMap<Long, ScheduledFuture<?>>();
  private volatile ScheduledFuture<?> tick;

  public ExecutorWatchdog(
      String name,
      ScheduledExecutorService scheduler,
      LongConsumer probe,
      MetricsRegistry metrics,
      Logger logger,
      long intervalMillis,
      long timeoutMillis) {
    this(
        name,
        scheduler,
        probe,
        metrics,
        logger,
        TransportMessages.of(Locale.ENGLISH),
        intervalMillis,
        timeoutMillis);
  }

  /** Constructor with an injected catalog so diagnostics follow the host locale. */
  public ExecutorWatchdog(
      String name,
      ScheduledExecutorService scheduler,
      LongConsumer probe,
      MetricsRegistry metrics,
      Logger logger,
      TransportMessages messages,
      long intervalMillis,
      long timeoutMillis) {
    if (name == null || !NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("name must be a lowercase stable identifier");
    }
    if (intervalMillis < 100L || intervalMillis > 60_000L) {
      throw new IllegalArgumentException("intervalMillis must be between 100 and 60000");
    }
    if (timeoutMillis < 1L || timeoutMillis >= intervalMillis) {
      throw new IllegalArgumentException("timeoutMillis must be positive and below the interval");
    }
    this.name = name;
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.probe = Objects.requireNonNull(probe, "probe");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.messages = Objects.requireNonNull(messages, "messages");
    this.intervalMillis = intervalMillis;
    this.timeoutMillis = timeoutMillis;
  }

  /** Starts the single periodic observation task. */
  public void start() {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("watchdog is already started");
    }
    tick = scheduler.scheduleAtFixedRate(this::observe, 0L, intervalMillis, TimeUnit.MILLISECONDS);
  }

  /** Marks one probe as responsive. */
  public void acknowledge(long token) {
    if (!running.get() || !pending.remove(token)) {
      return;
    }
    ScheduledFuture<?> timeout = timeouts.remove(token);
    if (timeout != null) {
      timeout.cancel(false);
    }
  }

  /** Cancels all scheduled work before transport shutdown completes. */
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    if (tick != null) {
      tick.cancel(false);
      tick = null;
    }
    for (ScheduledFuture<?> timeout : timeouts.values()) {
      timeout.cancel(false);
    }
    timeouts.clear();
    pending.clear();
  }

  private void observe() {
    if (!running.get()) {
      return;
    }
    long token = sequence.incrementAndGet();
    pending.add(token);
    ScheduledFuture<?> timeout =
        scheduler.schedule(() -> onMissed(token), timeoutMillis, TimeUnit.MILLISECONDS);
    timeouts.put(token, timeout);
    try {
      probe.accept(token);
    } catch (RuntimeException exception) {
      // A failed submission is equivalent to a missed acknowledgement; the timeout records it.
    }
  }

  private void onMissed(long token) {
    if (!running.get() || !pending.remove(token)) {
      return;
    }
    timeouts.remove(token);
    metrics.recordExecutorStall();
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("name", name);
    values.put("timeoutMs", Long.toString(timeoutMillis));
    values.put("sequence", Long.toString(token));
    logger.warning(messages.format(MessageKeys.WATCHDOG_STALL, values));
  }
}
