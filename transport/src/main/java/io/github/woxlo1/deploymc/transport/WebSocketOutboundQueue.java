package io.github.woxlo1.deploymc.transport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, non-blocking WebSocket outbound queue with sequence-only gap notices. */
final class WebSocketOutboundQueue {

  private final TransportConfig config;
  private final MetricsRegistry metrics;
  private final Executor eventLoop;
  private final Consumer<String> writer;
  private final Runnable closeAction;
  private final Runnable queueFullAction;
  private final Runnable slowConsumerAction;
  private final LongSupplier nanoTime;
  private final Deque<PendingMessage> outbound = new ArrayDeque<PendingMessage>();
  private final AtomicInteger queuedBytes = new AtomicInteger();
  private final AtomicBoolean draining = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong outboundSequence = new AtomicLong();

  private long gapFrom = -1L;
  private long gapTo;
  private long gapCount;
  private long slowSinceNanos = Long.MIN_VALUE;

  WebSocketOutboundQueue(
      TransportConfig config,
      MetricsRegistry metrics,
      Executor eventLoop,
      Consumer<String> writer,
      Runnable closeAction,
      Runnable queueFullAction,
      Runnable slowConsumerAction,
      LongSupplier nanoTime) {
    this.config = Objects.requireNonNull(config, "config");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
    this.writer = Objects.requireNonNull(writer, "writer");
    this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    this.queueFullAction = Objects.requireNonNull(queueFullAction, "queueFullAction");
    this.slowConsumerAction = Objects.requireNonNull(slowConsumerAction, "slowConsumerAction");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
  }

  boolean sendText(String text) {
    Objects.requireNonNull(text, "text");
    if (closed.get()) {
      return false;
    }
    long sequence = outboundSequence.incrementAndGet();
    int bytes = text.getBytes(StandardCharsets.UTF_8).length;
    boolean rejected = false;
    boolean disconnect = false;
    synchronized (outbound) {
      if (outbound.size() >= config.maxWebSocketQueuedMessages()
          || queuedBytes.get() + bytes > config.maxWebSocketQueuedBytes()) {
        metrics.recordQueueFull();
        queueFullAction.run();
        noteGap(sequence);
        disconnect = noteSlowConsumer();
        rejected = true;
      } else {
        queueGapLocked();
        outbound.addLast(new PendingMessage(text, bytes));
        queuedBytes.addAndGet(bytes);
      }
    }
    if (rejected) {
      if (disconnect) {
        close();
      }
      return false;
    }
    drain();
    return true;
  }

  void close() {
    if (closed.compareAndSet(false, true)) {
      closeAction.run();
    }
  }

  void markClosed() {
    closed.set(true);
    synchronized (outbound) {
      outbound.clear();
      queuedBytes.set(0);
      clearGap();
      slowSinceNanos = Long.MIN_VALUE;
    }
  }

  private void noteGap(long sequence) {
    if (gapFrom < 0L) {
      gapFrom = sequence;
    }
    gapTo = sequence;
    gapCount++;
  }

  private void clearGap() {
    gapFrom = -1L;
    gapTo = 0L;
    gapCount = 0L;
  }

  private void queueGapLocked() {
    if (gapFrom < 0L) {
      return;
    }
    String notice =
        "{\"type\":\"console.gap\",\"count\":"
            + gapCount
            + ",\"fromSeq\":"
            + gapFrom
            + ",\"toSeq\":"
            + gapTo
            + "}";
    int bytes = notice.getBytes(StandardCharsets.UTF_8).length;
    if (outbound.size() + 1 > config.maxWebSocketQueuedMessages()
        || queuedBytes.get() + bytes > config.maxWebSocketQueuedBytes()) {
      return;
    }
    outbound.addLast(new PendingMessage(notice, bytes));
    queuedBytes.addAndGet(bytes);
    clearGap();
  }

  private boolean noteSlowConsumer() {
    long now = nanoTime.getAsLong();
    if (slowSinceNanos == Long.MIN_VALUE) {
      slowSinceNanos = now;
      return false;
    }
    long threshold = config.slowConsumerMillis() * 1_000_000L;
    if (now - slowSinceNanos < threshold) {
      return false;
    }
    slowConsumerAction.run();
    return true;
  }

  private void drain() {
    if (draining.compareAndSet(false, true)) {
      eventLoop.execute(this::drainOnEventLoop);
    }
  }

  private void drainOnEventLoop() {
    while (true) {
      PendingMessage pending;
      synchronized (outbound) {
        queueGapLocked();
        pending = outbound.pollFirst();
        if (pending == null) {
          if (gapFrom < 0L) {
            slowSinceNanos = Long.MIN_VALUE;
          }
          draining.set(false);
          return;
        }
        queuedBytes.addAndGet(-pending.bytes);
      }
      writer.accept(pending.text);
    }
  }

  private static final class PendingMessage {

    private final String text;
    private final int bytes;

    private PendingMessage(String text, int bytes) {
      this.text = text;
      this.bytes = bytes;
    }
  }
}
