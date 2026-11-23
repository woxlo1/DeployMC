package io.github.woxlo1.deploymc.transport;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe, bounded registry of the fixed metrics in the observability contract. */
public final class MetricsRegistry {

  private static final double[] BUCKETS = {
    0.005d, 0.01d, 0.025d, 0.05d, 0.1d, 0.25d, 0.5d, 1.0d, 2.5d, 5.0d, 10.0d
  };

  private final Map<String, AtomicLong> requests = new ConcurrentHashMap<String, AtomicLong>();
  private final Map<String, AtomicLong> durationCounts =
      new ConcurrentHashMap<String, AtomicLong>();
  private final Map<String, AtomicLong> durationBuckets =
      new ConcurrentHashMap<String, AtomicLong>();
  private final Map<String, AtomicLong> durationSums = new ConcurrentHashMap<String, AtomicLong>();
  private final Map<String, AtomicLong> authFailures = new ConcurrentHashMap<String, AtomicLong>();
  private final Map<String, AtomicLong> shed = new ConcurrentHashMap<String, AtomicLong>();
  private final AtomicLong webSocketConnections = new AtomicLong();
  private final AtomicLong queueFull = new AtomicLong();
  private final AtomicLong executorQueueDepth = new AtomicLong();
  private final AtomicLong executorStalls = new AtomicLong();
  private volatile boolean snapshotBytesSet;
  private volatile long snapshotBytes;

  /** Records one completed HTTP request. The status is not a label and is never exported. */
  public void recordRequest(EndpointClass endpoint, long durationNanos, int status) {
    Objects.requireNonNull(endpoint, "endpoint");
    String label = endpoint.wireLabel();
    long nanos = Math.max(0L, durationNanos);
    increment(requests, label);
    increment(durationCounts, label);
    increment(durationSums, label, nanos);
    for (int bucket = 0; bucket < BUCKETS.length; bucket++) {
      if (nanos <= (long) (BUCKETS[bucket] * 1_000_000_000d)) {
        increment(durationBuckets, bucketKey(label, bucket));
      }
    }
  }

  /** Records one authentication failure by closed reason. */
  public void recordAuthFailure(AuthFailureReason reason) {
    Objects.requireNonNull(reason, "reason");
    increment(authFailures, reason.wireLabel());
  }

  /** Sets the current open WebSocket count. */
  public void setWebSocketConnections(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("value must not be negative");
    }
    webSocketConnections.set(value);
  }

  /** Records a non-blocking outbound queue rejection. */
  public void recordQueueFull() {
    queueFull.incrementAndGet();
  }

  /** Records one load-shedding decision by closed scope. */
  public void recordShed(ShedScope scope) {
    Objects.requireNonNull(scope, "scope");
    increment(shed, scope.wireLabel());
  }

  /** Sets the current bounded executor queue depth supplied by the host. */
  public void setExecutorQueueDepth(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("value must not be negative");
    }
    executorQueueDepth.set(value);
  }

  /** Records one watchdog observation of an unresponsive executor. */
  public void recordExecutorStall() {
    executorStalls.incrementAndGet();
  }

  /** Sets the current snapshot store size; the gauge is omitted until a host supplies it. */
  public void setSnapshotStoreBytes(long value) {
    if (value < 0L) {
      throw new IllegalArgumentException("value must not be negative");
    }
    snapshotBytes = value;
    snapshotBytesSet = true;
  }

  /** Renders Prometheus text format 0.0.4 with only fixed names and closed label values. */
  public String renderPrometheus() {
    StringBuilder out = new StringBuilder();
    out.append("# TYPE deploymc_http_requests_total counter\n");
    for (EndpointClass endpoint : EndpointClass.values()) {
      out.append("deploymc_http_requests_total{endpoint=\"")
          .append(escapeLabelValue(endpoint.wireLabel()))
          .append("\"} ")
          .append(value(requests, endpoint.wireLabel()))
          .append('\n');
    }
    out.append("# TYPE deploymc_http_request_duration_seconds histogram\n");
    for (EndpointClass endpoint : EndpointClass.values()) {
      String label = endpoint.wireLabel();
      long count = value(durationCounts, label);
      for (int bucket = 0; bucket < BUCKETS.length; bucket++) {
        out.append("deploymc_http_request_duration_seconds_bucket{endpoint=\"")
            .append(escapeLabelValue(label))
            .append("\",le=\"")
            .append(formatBucket(BUCKETS[bucket]))
            .append("\"} ")
            .append(value(durationBuckets, bucketKey(label, bucket)))
            .append('\n');
      }
      out.append("deploymc_http_request_duration_seconds_bucket{endpoint=\"")
          .append(escapeLabelValue(label))
          .append("\",le=\"+Inf\"} ")
          .append(count)
          .append('\n');
      out.append("deploymc_http_request_duration_seconds_sum{endpoint=\"")
          .append(escapeLabelValue(label))
          .append("\"} ")
          .append(seconds(value(durationSums, label)))
          .append('\n');
      out.append("deploymc_http_request_duration_seconds_count{endpoint=\"")
          .append(escapeLabelValue(label))
          .append("\"} ")
          .append(count)
          .append('\n');
    }
    out.append("# TYPE deploymc_auth_failures_total counter\n");
    for (AuthFailureReason reason : AuthFailureReason.values()) {
      out.append("deploymc_auth_failures_total{reason=\"")
          .append(escapeLabelValue(reason.wireLabel()))
          .append("\"} ")
          .append(value(authFailures, reason.wireLabel()))
          .append('\n');
    }
    out.append("# TYPE deploymc_websocket_connections_active gauge\n");
    out.append("deploymc_websocket_connections_active ")
        .append(webSocketConnections.get())
        .append('\n');
    out.append("# TYPE deploymc_websocket_queue_full_total counter\n");
    out.append("deploymc_websocket_queue_full_total ").append(queueFull.get()).append('\n');
    out.append("# TYPE deploymc_requests_shed_total counter\n");
    for (ShedScope scope : ShedScope.values()) {
      out.append("deploymc_requests_shed_total{scope=\"")
          .append(escapeLabelValue(scope.wireLabel()))
          .append("\"} ")
          .append(value(shed, scope.wireLabel()))
          .append('\n');
    }
    out.append("# TYPE deploymc_executor_queue_depth gauge\n");
    out.append("deploymc_executor_queue_depth ").append(executorQueueDepth.get()).append('\n');
    out.append("# TYPE deploymc_executor_stalls_total counter\n");
    out.append("deploymc_executor_stalls_total ").append(executorStalls.get()).append('\n');
    if (snapshotBytesSet) {
      out.append("# TYPE deploymc_snapshot_store_bytes gauge\n");
      out.append("deploymc_snapshot_store_bytes ").append(snapshotBytes).append('\n');
    }
    return out.toString();
  }

  /** Prometheus 0.0.4 label-value escaping. Exposed for deterministic contract tests. */
  static String escapeLabelValue(String value) {
    Objects.requireNonNull(value, "value");
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private static String bucketKey(String label, int bucket) {
    return label + '|' + bucket;
  }

  private static void increment(Map<String, AtomicLong> map, String key) {
    increment(map, key, 1L);
  }

  private static void increment(Map<String, AtomicLong> map, String key, long delta) {
    map.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(delta);
  }

  private static long value(Map<String, AtomicLong> map, String key) {
    AtomicLong value = map.get(key);
    return value == null ? 0L : value.get();
  }

  private static String formatBucket(double value) {
    if (value == Math.rint(value) && value < 1_000_000d) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }

  private static String seconds(long nanos) {
    return Double.toString(nanos / 1_000_000_000d);
  }
}
