package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class WebSocketOutboundQueueTest {

  @Test
  void fullQueueRejectsWithoutBlockingAndCountsTheEvent() {
    TransportConfig config = config(1, 5_000);
    MetricsRegistry metrics = new MetricsRegistry();
    DeferredExecutor eventLoop = new DeferredExecutor();
    List<String> written = new ArrayList<String>();
    AtomicInteger closed = new AtomicInteger();
    AtomicInteger queueFullLog = new AtomicInteger();
    MutableNanoTime time = new MutableNanoTime(0L);
    WebSocketOutboundQueue queue =
        queue(config, metrics, eventLoop, written, closed, queueFullLog, time);

    assertTrue(queue.sendText("one"));
    assertFalse(queue.sendText("two"));

    assertEquals(1, queueFullLog.get());
    assertTrue(metrics.renderPrometheus().contains("deploymc_websocket_queue_full_total 1\n"));
    assertTrue(written.isEmpty(), "the producer must not wait for the event loop");

    eventLoop.runAll();
    assertEquals("one", written.get(0));
    assertEquals(
        "{\"type\":\"console.gap\",\"count\":1,\"fromSeq\":2,\"toSeq\":2}", written.get(1));
  }

  @Test
  void gapNoticeUsesOnlyCountsAndSequenceNumbers() {
    TransportConfig config = config(3, 5_000);
    MetricsRegistry metrics = new MetricsRegistry();
    DeferredExecutor eventLoop = new DeferredExecutor();
    List<String> written = new ArrayList<String>();
    MutableNanoTime time = new MutableNanoTime(0L);
    WebSocketOutboundQueue queue =
        queue(config, metrics, eventLoop, written, new AtomicInteger(), new AtomicInteger(), time);

    assertTrue(queue.sendText("one"));
    assertTrue(queue.sendText("two"));
    assertTrue(queue.sendText("three"));
    assertFalse(queue.sendText("four"));
    assertFalse(queue.sendText("five"));
    eventLoop.runAll();

    String notice = null;
    for (String frame : written) {
      if (frame.contains("console.gap")) {
        notice = frame;
      }
    }
    assertEquals("{\"type\":\"console.gap\",\"count\":2,\"fromSeq\":4,\"toSeq\":5}", notice);
    assertFalse(notice.contains("one"));
    assertFalse(notice.contains("four"));
  }

  @Test
  void slowConsumerIsDisconnectedOnlyAfterTheConfiguredThreshold() {
    TransportConfig config = config(1, 100);
    MetricsRegistry metrics = new MetricsRegistry();
    DeferredExecutor eventLoop = new DeferredExecutor();
    List<String> written = new ArrayList<String>();
    AtomicInteger closed = new AtomicInteger();
    AtomicInteger slowLog = new AtomicInteger();
    MutableNanoTime time = new MutableNanoTime(0L);
    WebSocketOutboundQueue queue =
        new WebSocketOutboundQueue(
            config,
            metrics,
            eventLoop,
            written::add,
            closed::incrementAndGet,
            new AtomicInteger()::incrementAndGet,
            slowLog::incrementAndGet,
            time);

    assertTrue(queue.sendText("one"));
    assertFalse(queue.sendText("two"));
    assertEquals(0, closed.get());

    time.advance(99L * 1_000_000L);
    assertFalse(queue.sendText("three"));
    assertEquals(0, closed.get());

    time.advance(1L * 1_000_000L);
    assertFalse(queue.sendText("four"));
    assertEquals(1, closed.get());
    assertEquals(1, slowLog.get());
    assertFalse(queue.sendText("after-close"));
  }

  private static TransportConfig config(int queuedMessages, int slowConsumerMillis) {
    return TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
        .maxWebSocketQueuedMessages(queuedMessages)
        .maxWebSocketQueuedBytes(1024)
        .slowConsumerMillis(slowConsumerMillis)
        .build();
  }

  private static WebSocketOutboundQueue queue(
      TransportConfig config,
      MetricsRegistry metrics,
      DeferredExecutor eventLoop,
      List<String> written,
      AtomicInteger closed,
      AtomicInteger queueFullLog,
      MutableNanoTime time) {
    return new WebSocketOutboundQueue(
        config,
        metrics,
        eventLoop,
        written::add,
        closed::incrementAndGet,
        queueFullLog::incrementAndGet,
        () -> {},
        time);
  }

  private static final class DeferredExecutor implements Executor {

    private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    private void runAll() {
      while (!tasks.isEmpty()) {
        tasks.poll().run();
      }
    }
  }

  private static final class MutableNanoTime implements LongSupplier {

    private long nanos;

    private MutableNanoTime(long nanos) {
      this.nanos = nanos;
    }

    @Override
    public long getAsLong() {
      return nanos;
    }

    private void advance(long delta) {
      nanos += delta;
    }
  }
}
