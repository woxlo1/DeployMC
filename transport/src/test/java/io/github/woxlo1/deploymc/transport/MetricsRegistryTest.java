package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MetricsRegistryTest {

  private static final List<String> METRIC_NAMES =
      Arrays.asList(
          "deploymc_http_requests_total",
          "deploymc_http_request_duration_seconds",
          "deploymc_auth_failures_total",
          "deploymc_websocket_connections_active",
          "deploymc_websocket_queue_full_total",
          "deploymc_requests_shed_total",
          "deploymc_executor_queue_depth",
          "deploymc_executor_stalls_total");

  private static final Pattern LABEL = Pattern.compile("([a-z_]+)=\"[^\"]*\"");

  @Test
  void rendersAllFixedFamiliesAndClosedLabels() {
    MetricsRegistry registry = new MetricsRegistry();
    registry.recordRequest(EndpointClass.SERVER, 10_000_000L, 200);
    registry.recordAuthFailure(AuthFailureReason.INVALID);
    registry.setWebSocketConnections(2);
    registry.recordQueueFull();
    registry.recordShed(ShedScope.IDENTITY);
    registry.setExecutorQueueDepth(7);
    registry.recordExecutorStall();

    String rendered = registry.renderPrometheus();

    for (String name : METRIC_NAMES) {
      assertTrue(rendered.contains("# TYPE " + name + " "), "missing TYPE for " + name);
    }
    assertTrue(rendered.contains("deploymc_http_requests_total{endpoint=\"server\"} 1"));
    assertTrue(
        rendered.contains(
            "deploymc_http_request_duration_seconds_bucket{endpoint=\"server\",le=\"0.01\"} 1"));
    assertTrue(
        rendered.contains("deploymc_http_request_duration_seconds_sum{endpoint=\"server\"} 0.01"));
    assertTrue(
        rendered.contains("deploymc_http_request_duration_seconds_count{endpoint=\"server\"} 1"));
    assertTrue(rendered.contains("deploymc_auth_failures_total{reason=\"invalid\"} 1"));
    assertTrue(rendered.contains("deploymc_websocket_connections_active 2\n"));
    assertTrue(rendered.contains("deploymc_websocket_queue_full_total 1\n"));
    assertTrue(rendered.contains("deploymc_requests_shed_total{scope=\"identity\"} 1"));
    assertTrue(rendered.contains("deploymc_executor_queue_depth 7\n"));
    assertTrue(rendered.contains("deploymc_executor_stalls_total 1\n"));

    String[] buckets = {
      "0.005", "0.01", "0.025", "0.05", "0.1", "0.25", "0.5", "1", "2.5", "5", "10", "+Inf"
    };
    for (String bucket : buckets) {
      assertTrue(
          rendered.contains(
              "deploymc_http_request_duration_seconds_bucket{endpoint=\"server\",le=\""
                  + bucket
                  + "\"}"),
          "missing bucket " + bucket);
    }
    for (EndpointClass endpoint : EndpointClass.values()) {
      assertTrue(
          rendered.contains(
              "deploymc_http_requests_total{endpoint=\"" + endpoint.wireLabel() + "\"}"),
          "missing zero-valued counter for " + endpoint);
    }
    for (AuthFailureReason reason : AuthFailureReason.values()) {
      assertTrue(
          rendered.contains("deploymc_auth_failures_total{reason=\"" + reason.wireLabel() + "\"}"),
          "missing zero-valued counter for " + reason);
    }
    for (ShedScope scope : ShedScope.values()) {
      assertTrue(
          rendered.contains("deploymc_requests_shed_total{scope=\"" + scope.wireLabel() + "\"}"),
          "missing zero-valued counter for " + scope);
    }
  }

  @Test
  void omitsSnapshotGaugeUntilTheHostSuppliesAStore() {
    MetricsRegistry registry = new MetricsRegistry();

    assertFalse(registry.renderPrometheus().contains("deploymc_snapshot_store_bytes"));

    registry.setSnapshotStoreBytes(4096L);
    assertTrue(registry.renderPrometheus().contains("deploymc_snapshot_store_bytes 4096\n"));
  }

  @Test
  void escapesPrometheusLabelValues() {
    assertEquals("plain", MetricsRegistry.escapeLabelValue("plain"));
    assertEquals("a\\\\b\\\"c\\nd", MetricsRegistry.escapeLabelValue("a\\b\"c\nd"));
  }

  @Test
  void neverExportsHighCardinalityLabelsOrRawInput() {
    MetricsRegistry registry = new MetricsRegistry();
    String identity = "user-s3cr3t-id";
    String rawPath = "/api/v1/files/private-config.yml";
    EndpointClass endpoint = EndpointClass.fromPath(rawPath);

    registry.recordRequest(endpoint, 1_000L, 200);
    registry.recordAuthFailure(AuthFailureReason.EXPIRED);
    String rendered = registry.renderPrometheus();

    assertFalse(rendered.contains(identity));
    assertFalse(rendered.contains(rawPath));
    assertFalse(rendered.contains("private-config"));
    assertTrue(rendered.contains("endpoint=\"files\""));

    Set<String> allowed = new HashSet<String>(Arrays.asList("endpoint", "reason", "scope", "le"));
    for (String line : rendered.split("\n")) {
      if (line.startsWith("#") || line.isEmpty()) {
        continue;
      }
      Matcher matcher = LABEL.matcher(line);
      while (matcher.find()) {
        assertTrue(
            allowed.contains(matcher.group(1)),
            "unexpected high-cardinality label in line: " + line);
      }
    }
  }
}
