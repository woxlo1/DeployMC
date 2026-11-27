package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import io.github.woxlo1.deploymc.transport.LoggingFormat;
import io.github.woxlo1.deploymc.transport.TransportConfig;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Server-free coverage for the host observability configuration adapter. */
class HostObservabilityConfigTest {

  private static final HttpBindConfig BIND = HttpBindConfig.of("127.0.0.1", 8080, false);

  @Test
  void usesFailClosedDefaultsWhenObservabilityKeysAreAbsent() {
    HostObservabilityConfig host = HostObservabilityConfig.from(emptyView());
    TransportConfig transport = host.transportConfig(BIND);

    assertEquals(LoggingFormat.TEXT, host.observability().loggingFormat());
    assertFalse(host.observability().metricsEnabled());
    assertEquals("server.read", host.observability().metricsPermission());
    assertEquals(300, host.observability().addressRateLimitPerMinute());
    assertEquals(300, host.observability().identityRateLimitPerMinute());
    assertEquals(60, host.observability().authFailureRateLimitPerMinute());
    assertEquals(50, host.maxWebSocketConnections());
    assertEquals(1024 * 1024, transport.maxBodyBytes());
    assertEquals(64 * 1024 * 1024, transport.maxUploadBodyBytes());
    assertTrue(transport.trustedProxies().isEmpty());
    assertTrue(transport.allowedOrigins().isEmpty());
    assertFalse(transport.tokenLockoutEnabled());
  }

  @Test
  void appliesValidatedHostValuesToTransportConfig() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    set(values, "observability.logging.format", "json");
    set(values, "observability.metrics.enabled", Boolean.TRUE);
    set(values, "observability.metrics.permission", "server.read");
    set(values, "limits.rate-limit-per-minute", Integer.valueOf(120));
    set(values, "limits.auth-failure-rate-limit-per-minute", Integer.valueOf(12));
    set(values, "limits.request-body-limit-kb", Integer.valueOf(2048));
    set(values, "limits.max-websocket-connections", Integer.valueOf(25));
    set(values, "sandbox.max-upload-size-mb", Integer.valueOf(128));
    set(values, "bind.trusted-proxies", Arrays.asList("192.0.2.8/32"));
    set(values, "bind.allowed-origins", Arrays.asList("https://admin.example.invalid"));

    HostObservabilityConfig host = HostObservabilityConfig.from(view(values));
    TransportConfig transport = host.transportConfig(BIND, true);

    assertEquals(LoggingFormat.JSON, host.observability().loggingFormat());
    assertTrue(host.observability().metricsEnabled());
    assertEquals("server.read", host.observability().metricsPermission());
    assertEquals(120, host.observability().addressRateLimitPerMinute());
    assertEquals(120, host.observability().identityRateLimitPerMinute());
    assertEquals(12, host.observability().authFailureRateLimitPerMinute());
    assertEquals(25, transport.maxWebSocketConnections());
    assertEquals(2048 * 1024, transport.maxBodyBytes());
    assertEquals(64 * 1024 * 1024, transport.maxUploadBodyBytes());
    assertEquals(1, transport.trustedProxies().size());
    assertTrue(transport.trustedProxies().contains("192.0.2.8/32"));
    assertEquals(1, transport.allowedOrigins().size());
    assertTrue(transport.allowedOrigins().contains("https://admin.example.invalid"));
    assertTrue(transport.tokenLockoutEnabled());
    assertEquals(host.observability(), transport.observability());
    assertEquals(BIND, transport.bind());
  }

  @Test
  void rejectsUnknownTypesAndOutOfRangeValues() {
    Object[][] invalidValues = {
      {"observability.logging.format", "yaml"},
      {"observability.logging.format", Integer.valueOf(1)},
      {"observability.metrics.enabled", "true"},
      {"observability.metrics.permission", "admin.read"},
      {"observability.metrics.permission", Integer.valueOf(1)},
      {"limits.rate-limit-per-minute", Integer.valueOf(0)},
      {"limits.rate-limit-per-minute", Integer.valueOf(100001)},
      {"limits.rate-limit-per-minute", Double.valueOf(300.5)},
      {"limits.rate-limit-per-minute", "300"},
      {"limits.auth-failure-rate-limit-per-minute", Integer.valueOf(0)},
      {"limits.auth-failure-rate-limit-per-minute", Integer.valueOf(100001)},
      {"limits.auth-failure-rate-limit-per-minute", "60"},
      {"limits.request-body-limit-kb", Integer.valueOf(0)},
      {"limits.request-body-limit-kb", Integer.valueOf(16385)},
      {"limits.request-body-limit-kb", "1024"},
      {"limits.max-websocket-connections", Integer.valueOf(0)},
      {"limits.max-websocket-connections", Integer.valueOf(10001)},
      {"limits.max-websocket-connections", Boolean.TRUE},
      {"sandbox.max-upload-size-mb", Integer.valueOf(0)},
      {"sandbox.max-upload-size-mb", Integer.valueOf(4097)},
      {"sandbox.max-upload-size-mb", "64"},
      {"bind.trusted-proxies", "192.0.2.8"},
      {"bind.trusted-proxies", Arrays.asList(Integer.valueOf(1))},
      {"bind.trusted-proxies", Arrays.asList("proxy.example.invalid")},
      {"bind.trusted-proxies", Arrays.asList("192.0.2.0/0.5")},
      {"bind.allowed-origins", "https://admin.example.invalid"},
      {"bind.allowed-origins", Arrays.asList(Integer.valueOf(1))},
      {"bind.allowed-origins", Arrays.asList("https://*.example.invalid")},
      {"bind.allowed-origins", Arrays.asList("https://example.invalid/path")}
    };
    for (Object[] invalid : invalidValues) {
      String setting = (String) invalid[0];
      StartupConfigurationException exception =
          assertThrows(
              StartupConfigurationException.class,
              () -> HostObservabilityConfig.from(view(setting, invalid[1])),
              setting);
      assertEquals(MessageKeys.STARTUP_OBSERVABILITY_INVALID_CONFIG, exception.messageKey());
      assertEquals(setting, exception.values().get("setting"));
    }
  }

  private static ConfigView emptyView() {
    return view(new LinkedHashMap<String, Object>());
  }

  private static ConfigView view(String setting, Object value) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    set(values, setting, value);
    return view(values);
  }

  private static ConfigView view(Map<String, Object> values) {
    try {
      Constructor<ConfigView> constructor = ConfigView.class.getDeclaredConstructor(Map.class);
      constructor.setAccessible(true);
      return constructor.newInstance(values);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("ConfigView test factory is unavailable", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static void set(Map<String, Object> root, String path, Object value) {
    Map<String, Object> current = root;
    String[] segments = path.split("\\.");
    for (int index = 0; index < segments.length - 1; index++) {
      Object child = current.get(segments[index]);
      if (!(child instanceof Map)) {
        child = new LinkedHashMap<String, Object>();
        current.put(segments[index], child);
      }
      current = (Map<String, Object>) child;
    }
    current.put(segments[segments.length - 1], value);
  }
}
