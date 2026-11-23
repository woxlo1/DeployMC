package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObservabilityConfigTest {

  @Test
  void defaultsMatchTheNormativeBaseline() {
    ObservabilityConfig defaults = ObservabilityConfig.defaults();

    assertEquals(LoggingFormat.TEXT, defaults.loggingFormat());
    assertEquals("text", defaults.loggingFormat().wireLabel());
    assertFalse(defaults.metricsEnabled());
    assertEquals("metrics.read", defaults.metricsPermission());
    assertEquals(300, defaults.identityRateLimitPerMinute());
    assertEquals(30, defaults.authFailureRateLimitPerMinute());
    assertEquals(4096, defaults.maxRateLimitKeys());
    assertEquals(5_000L, defaults.watchdogIntervalMillis());
    assertEquals(2_000L, defaults.watchdogTimeoutMillis());
  }

  @Test
  void transportConfigExposesObservabilityAndNewCaps() {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();

    assertEquals(50, config.maxWebSocketConnections());
    assertEquals(5_000, config.slowConsumerMillis());
    assertEquals(
        ObservabilityConfig.defaults().metricsPermission(),
        config.observability().metricsPermission());
    assertFalse(config.observability().metricsEnabled());
  }

  @Test
  void builderAcceptsOnlyStablePermissionIdentifiers() {
    assertTrue(ObservabilityConfig.isSupportedPermission("metrics.read"));
    assertTrue(ObservabilityConfig.isSupportedPermission("server.read"));
    assertFalse(ObservabilityConfig.isSupportedPermission("admin.all"));
    assertFalse(ObservabilityConfig.isSupportedPermission(null));

    ObservabilityConfig config =
        ObservabilityConfig.builder()
            .metricsEnabled(true)
            .metricsPermission("server.read")
            .loggingFormat(LoggingFormat.JSON)
            .build();
    assertTrue(config.metricsEnabled());
    assertEquals("server.read", config.metricsPermission());
    assertEquals("json", config.loggingFormat().label());
    assertThrows(
        IllegalArgumentException.class,
        () -> ObservabilityConfig.builder().metricsPermission("admin.all").build());
  }

  @Test
  void builderFailsClosedOnInvalidBudgets() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ObservabilityConfig.builder().identityRateLimitPerMinute(0).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ObservabilityConfig.builder().authFailureRateLimitPerMinute(0).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ObservabilityConfig.builder().maxRateLimitKeys(15).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ObservabilityConfig.builder().watchdogIntervalMillis(99L).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ObservabilityConfig.builder().watchdogTimeoutMillis(5_000L).build());
  }

  @Test
  void classificationUsesAClosedLowCardinalitySet() {
    assertEquals(EndpointClass.HEALTH, EndpointClass.fromPath("/healthz"));
    assertEquals(EndpointClass.HEALTH, EndpointClass.fromPath("/readyz?probe=1"));
    assertEquals(EndpointClass.SERVER, EndpointClass.fromPath("/api/v1/server"));
    assertEquals(EndpointClass.DOCTOR, EndpointClass.fromPath("/api/v1/server/doctor"));
    assertEquals(EndpointClass.FILES, EndpointClass.fromPath("/api/v1/files/a/b.yml?x=1"));
    assertEquals(EndpointClass.VALIDATION, EndpointClass.fromPath("/api/v1/validation/config"));
    assertEquals(EndpointClass.SNAPSHOTS, EndpointClass.fromPath("/api/v1/snapshots/snap-1"));
    assertEquals(EndpointClass.METRICS, EndpointClass.fromPath("/api/v1/metrics"));
    assertEquals(EndpointClass.WEBSOCKET, EndpointClass.fromPath("/api/v1/ws"));
    assertEquals(EndpointClass.STATIC, EndpointClass.fromPath("/assets/index-abcdef12.js"));
    assertEquals(EndpointClass.STATIC, EndpointClass.fromPath("/"));
    assertEquals(EndpointClass.OTHER, EndpointClass.fromPath("/api/v1/filesx"));
    assertEquals(EndpointClass.OTHER, EndpointClass.fromPath("/api/v1/unknown#fragment"));
    assertEquals(EndpointClass.OTHER, EndpointClass.fromPath(null));
    assertEquals(
        EndpointClass.WEBSOCKET, EndpointClass.fromPath("/custom/socket", "/custom/socket"));
    assertEquals(EndpointClass.OTHER, EndpointClass.fromPath("/custom/socket"));
  }

  @Test
  void allEndpointLabelsAreStable() {
    for (EndpointClass endpoint : EndpointClass.values()) {
      assertEquals(endpoint.wireLabel(), endpoint.label());
    }
    assertEquals("auth-failure", ShedScope.AUTH_FAILURE.label());
    assertEquals("expired", AuthFailureReason.EXPIRED.wireLabel());
  }
}
