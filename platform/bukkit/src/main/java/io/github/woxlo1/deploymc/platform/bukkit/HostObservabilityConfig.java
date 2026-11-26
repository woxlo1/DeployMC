package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import io.github.woxlo1.deploymc.transport.LoggingFormat;
import io.github.woxlo1.deploymc.transport.ObservabilityConfig;
import io.github.woxlo1.deploymc.transport.TransportConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Host configuration adapter for the merged transport observability contract. */
public final class HostObservabilityConfig {

  private static final String LOGGING_FORMAT = "observability.logging.format";
  private static final String METRICS_ENABLED = "observability.metrics.enabled";
  private static final String METRICS_PERMISSION = "observability.metrics.permission";
  private static final String RATE_LIMIT = "limits.rate-limit-per-minute";
  private static final String WEB_SOCKET_CONNECTIONS = "limits.max-websocket-connections";
  private static final int MAX_RATE_LIMIT = 100_000;
  private static final int MAX_WEB_SOCKET_CONNECTIONS = 10_000;

  private final ObservabilityConfig observability;
  private final int maxWebSocketConnections;

  private HostObservabilityConfig(ObservabilityConfig observability, int maxWebSocketConnections) {
    this.observability = observability;
    this.maxWebSocketConnections = maxWebSocketConnections;
  }

  /** Reads all values from an already validated host view. Invalid types or enums fail closed. */
  public static HostObservabilityConfig from(ConfigView view) {
    Objects.requireNonNull(view, "view");
    LoggingFormat format = loggingFormat(view);
    boolean metricsEnabled = booleanValue(view, METRICS_ENABLED, false);
    String permission = permission(view);
    int rateLimit = integerValue(view, RATE_LIMIT, 300, MAX_RATE_LIMIT);
    int connections = integerValue(view, WEB_SOCKET_CONNECTIONS, 50, MAX_WEB_SOCKET_CONNECTIONS);
    ObservabilityConfig observability;
    try {
      observability =
          ObservabilityConfig.builder()
              .loggingFormat(format)
              .metricsEnabled(metricsEnabled)
              .metricsPermission(permission)
              .identityRateLimitPerMinute(rateLimit)
              .build();
    } catch (IllegalArgumentException invalid) {
      throw invalid(RATE_LIMIT);
    }
    return new HostObservabilityConfig(observability, connections);
  }

  public ObservabilityConfig observability() {
    return observability;
  }

  public int maxWebSocketConnections() {
    return maxWebSocketConnections;
  }

  /** Applies host-controlled observability limits to an otherwise default transport config. */
  public TransportConfig transportConfig(HttpBindConfig bind) {
    Objects.requireNonNull(bind, "bind");
    return TransportConfig.builder(bind)
        .observability(observability)
        .maxWebSocketConnections(maxWebSocketConnections)
        .build();
  }

  private static LoggingFormat loggingFormat(ConfigView view) {
    Object raw = view.get(LOGGING_FORMAT);
    if (raw == null) {
      return LoggingFormat.TEXT;
    }
    if (!(raw instanceof String)) {
      throw invalid(LOGGING_FORMAT);
    }
    String value = ((String) raw).trim();
    if ("text".equals(value)) {
      return LoggingFormat.TEXT;
    }
    if ("json".equals(value)) {
      return LoggingFormat.JSON;
    }
    throw invalid(LOGGING_FORMAT);
  }

  private static boolean booleanValue(ConfigView view, String setting, boolean fallback) {
    Object raw = view.get(setting);
    if (raw == null) {
      return fallback;
    }
    if (!(raw instanceof Boolean)) {
      throw invalid(setting);
    }
    return ((Boolean) raw).booleanValue();
  }

  private static String permission(ConfigView view) {
    Object raw = view.get(METRICS_PERMISSION);
    if (raw == null) {
      return "server.read";
    }
    if (!(raw instanceof String) || !ObservabilityConfig.isSupportedPermission((String) raw)) {
      throw invalid(METRICS_PERMISSION);
    }
    return (String) raw;
  }

  private static int integerValue(ConfigView view, String setting, int fallback, int maximum) {
    Object raw = view.get(setting);
    if (raw == null) {
      return fallback;
    }
    if (!(raw instanceof Integer)
        && !(raw instanceof Long)
        && !(raw instanceof Short)
        && !(raw instanceof Byte)) {
      throw invalid(setting);
    }
    long value = ((Number) raw).longValue();
    if (value < 1L || value > (long) maximum) {
      throw invalid(setting);
    }
    return (int) value;
  }

  private static StartupConfigurationException invalid(String setting) {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("setting", setting);
    return new StartupConfigurationException(
        MessageKeys.STARTUP_OBSERVABILITY_INVALID_CONFIG, values);
  }
}
