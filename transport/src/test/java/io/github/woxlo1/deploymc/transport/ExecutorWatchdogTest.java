package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ExecutorWatchdogTest {

  @Test
  void recordsAStallAndRedactedDiagnosticWithoutProcessAction() throws Exception {
    ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
    MetricsRegistry metrics = new MetricsRegistry();
    CapturingLogger capturing = new CapturingLogger();
    CountDownLatch warned = new CountDownLatch(1);
    capturing.onPublish = warned::countDown;
    AtomicLong observedToken = new AtomicLong();
    ExecutorWatchdog watchdog =
        new ExecutorWatchdog(
            "transport",
            scheduler,
            observedToken::set,
            metrics,
            capturing.logger,
            TransportMessages.of(Locale.ENGLISH),
            100L,
            1L);
    try {
      watchdog.start();
      assertTrue(warned.await(3L, TimeUnit.SECONDS), "watchdog did not record a stall");
      watchdog.stop();

      assertTrue(observedToken.get() > 0L);
      String rendered = metrics.renderPrometheus();
      assertTrue(rendered.contains("deploymc_executor_stalls_total "));
      assertFalse(rendered.contains("deploymc_executor_stalls_total 0\n"));
      assertFalse(capturing.records.isEmpty());
      String warning = capturing.records.get(0);
      assertTrue(warning.contains("Watchdog transport"));
      assertTrue(warning.contains("no process action was taken"));
      assertFalse(warning.contains("127.0.0.1"));
    } finally {
      watchdog.stop();
      scheduler.shutdownNow();
    }
  }

  @Test
  void acknowledgementPreventsAStallRecord() throws Exception {
    ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
    MetricsRegistry metrics = new MetricsRegistry();
    CapturingLogger capturing = new CapturingLogger();
    CountDownLatch acknowledged = new CountDownLatch(3);
    long intervalMillis = 100L;
    long timeoutMillis = 50L;
    ExecutorWatchdog[] holder = new ExecutorWatchdog[1];
    holder[0] =
        new ExecutorWatchdog(
            "transport",
            scheduler,
            token -> {
              holder[0].acknowledge(token);
              acknowledged.countDown();
            },
            metrics,
            capturing.logger,
            TransportMessages.of(Locale.ENGLISH),
            intervalMillis,
            timeoutMillis);
    try {
      holder[0].start();
      assertTrue(acknowledged.await(3L, TimeUnit.SECONDS), "probes were not acknowledged");
      Thread.sleep(timeoutMillis + 70L);
      holder[0].stop();

      String rendered = metrics.renderPrometheus();
      assertTrue(
          rendered.contains("deploymc_executor_stalls_total 0\n"),
          "unexpected stall count: " + rendered);
      assertTrue(capturing.records.isEmpty(), "unexpected stall warning: " + capturing.records);
    } finally {
      holder[0].stop();
      scheduler.shutdownNow();
    }
  }

  @Test
  void stopCancelsPendingObservations() throws Exception {
    ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
    MetricsRegistry metrics = new MetricsRegistry();
    CapturingLogger capturing = new CapturingLogger();
    long timeoutMillis = 50L;
    ExecutorWatchdog watchdog =
        new ExecutorWatchdog(
            "transport",
            scheduler,
            token -> {},
            metrics,
            capturing.logger,
            TransportMessages.of(Locale.ENGLISH),
            100L,
            timeoutMillis);
    watchdog.start();
    watchdog.stop();
    Thread.sleep(timeoutMillis + 70L);
    scheduler.shutdownNow();

    assertTrue(metrics.renderPrometheus().contains("deploymc_executor_stalls_total 0\n"));
    assertTrue(capturing.records.isEmpty());
  }

  @Test
  void rejectsInvalidNamesAndTimeouts() {
    ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    MetricsRegistry metrics = new MetricsRegistry();
    Logger logger = Logger.getAnonymousLogger();
    try {
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class,
          () ->
              new ExecutorWatchdog(
                  "Bad Name",
                  scheduler,
                  token -> {},
                  metrics,
                  logger,
                  TransportMessages.of(Locale.ENGLISH),
                  100L,
                  1L));
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class,
          () ->
              new ExecutorWatchdog(
                  "transport",
                  scheduler,
                  token -> {},
                  metrics,
                  logger,
                  TransportMessages.of(Locale.ENGLISH),
                  100L,
                  100L));
    } finally {
      scheduler.shutdownNow();
    }
  }

  private static final class CapturingLogger {

    private final Logger logger = Logger.getAnonymousLogger();
    private final List<String> records = new CopyOnWriteArrayList<String>();
    private Runnable onPublish = () -> {};

    private CapturingLogger() {
      logger.setUseParentHandlers(false);
      logger.addHandler(
          new Handler() {
            @Override
            public void publish(LogRecord record) {
              records.add(record.getMessage());
              onPublish.run();
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          });
    }
  }
}
