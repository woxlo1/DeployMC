package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class WebProtectionConfigTest {

  @Test
  void defaultsMatchTheWebProtectionContract() {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();

    assertEquals(8 * 1024, config.maxHeaderBytes());
    assertEquals(1024 * 1024, config.maxBodyBytes());
    assertEquals(64 * 1024 * 1024, config.maxUploadBodyBytes());
    assertEquals(64 * 1024, config.maxWebSocketFrameBytes());
    assertEquals(1_000, config.maxWebSocketQueuedMessages());
    assertEquals(2 * 1024 * 1024, config.maxWebSocketQueuedBytes());
    assertTrue(config.trustedProxies().isEmpty());
    assertFalse(config.tokenLockoutEnabled());
  }

  @Test
  void trustedProxiesAndOriginsAreValidatedAtBuild() {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 8080, false))
            .trustedProxies(Collections.singletonList("10.0.0.0/8"))
            .allowedOrigins(Collections.singletonList("https://app.example.test"))
            .tokenLockoutEnabled(true)
            .build();

    assertTrue(config.trustedProxies().contains("10.0.0.0/8"));
    assertTrue(config.allowedOrigins().contains("https://app.example.test"));
    assertTrue(config.tokenLockoutEnabled());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
                .trustedProxies(Collections.singletonList("proxy.example.test"))
                .build());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
                .allowedOrigins(Collections.singletonList("https://app.example.test/*"))
                .build());
  }

  @Test
  void addressShedScopeHasClosedWireLabel() {
    assertEquals("address", ShedScope.ADDRESS.wireLabel());
    assertEquals("address", ShedScope.ADDRESS.label());
  }
}
