package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.transport.EffectivePeerResolver;
import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import io.github.woxlo1.deploymc.transport.LoggingFormat;
import io.github.woxlo1.deploymc.transport.ObservabilityConfig;
import io.github.woxlo1.deploymc.transport.OriginValidator;
import io.github.woxlo1.deploymc.transport.TransportConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Host configuration adapter for the merged transport observability and protection contracts. */
public final class HostObservabilityConfig {

  private static final String LOGGING_FORMAT = "observability.logging.format";
  private static final String METRICS_ENABLED = "observability.metrics.enabled";
  private static final String METRICS_PERMISSION = "observability.metrics.permission";
  private static final String RATE_LIMIT = "limits.rate-limit-per-minute";
  private static final String AUTH_FAILURE_RATE_LIMIT = "limits.auth-failure-rate-limit-per-minute";
  private static final String REQUEST_BODY_LIMIT_KB = "limits.request-body-limit-kb";
  private static final String WEB_SOCKET_CONNECTIONS = "limits.max-websocket-connections";
  private static final String MAX_UPLOAD_SIZE_MB = "sandbox.max-upload-size-mb";
  private static final String TRUSTED_PROXIES = "bind.trusted-proxies";
  private static final String ALLOWED_ORIGINS = "bind.allowed-origins";
  private static final int MAX_RATE_LIMIT = 100_000;
  private static final int MAX_WEB_SOCKET_CONNECTIONS = 10_000;
  private static final int MAX_REQUEST_BODY_KB = 16_384;
  private static final int MAX_UPLOAD_SIZE_MB_VALUE = 4_096;
  private static final int MAX_TRANSPORT_UPLOAD_MB = 64;
  private static final int MAX_ORIGIN_OR_PROXY_ENTRIES = 64;
  private static final int KIBIBYTE = 1024;
  private static final int MEBIBYTE = 1024 * 1024;

  private final ObservabilityConfig observability;
  private final int maxWebSocketConnections;
  private final int requestBodyLimitBytes;
  private final int maxUploadBodyBytes;
  private final List<String> trustedProxies;
  private final List<String> allowedOrigins;

  private HostObservabilityConfig(
      ObservabilityConfig observability,
      int maxWebSocketConnections,
      int requestBodyLimitBytes,
      int maxUploadBodyBytes,
      List<String> trustedProxies,
      List<String> allowedOrigins) {
    this.observability = observability;
    this.maxWebSocketConnections = maxWebSocketConnections;
    this.requestBodyLimitBytes = requestBodyLimitBytes;
    this.maxUploadBodyBytes = maxUploadBodyBytes;
    this.trustedProxies = trustedProxies;
    this.allowedOrigins = allowedOrigins;
  }

  /** Reads all values from an already validated host view. Invalid types or enums fail closed. */
  public static HostObservabilityConfig from(ConfigView view) {
    Objects.requireNonNull(view, "view");
    LoggingFormat format = loggingFormat(view);
    boolean metricsEnabled = booleanValue(view, METRICS_ENABLED, false);
    String permission = permission(view);
    int rateLimit = integerValue(view, RATE_LIMIT, 300, MAX_RATE_LIMIT);
    int authFailureRateLimit = integerValue(view, AUTH_FAILURE_RATE_LIMIT, 60, MAX_RATE_LIMIT);
    int requestBodyLimitKb = integerValue(view, REQUEST_BODY_LIMIT_KB, 1024, MAX_REQUEST_BODY_KB);
    int connections = integerValue(view, WEB_SOCKET_CONNECTIONS, 50, MAX_WEB_SOCKET_CONNECTIONS);
    int uploadMb = integerValue(view, MAX_UPLOAD_SIZE_MB, 64, MAX_UPLOAD_SIZE_MB_VALUE);
    List<String> trustedProxies = stringList(view, TRUSTED_PROXIES);
    List<String> allowedOrigins = stringList(view, ALLOWED_ORIGINS);
    for (String entry : trustedProxies) {
      if (!EffectivePeerResolver.isWellFormedTrustedProxy(entry)) {
        throw invalid(TRUSTED_PROXIES);
      }
    }
    for (String entry : allowedOrigins) {
      if (!OriginValidator.isWellFormed(entry)) {
        throw invalid(ALLOWED_ORIGINS);
      }
    }
    ObservabilityConfig observability;
    try {
      observability =
          ObservabilityConfig.builder()
              .loggingFormat(format)
              .metricsEnabled(metricsEnabled)
              .metricsPermission(permission)
              .addressRateLimitPerMinute(rateLimit)
              .identityRateLimitPerMinute(rateLimit)
              .authFailureRateLimitPerMinute(authFailureRateLimit)
              .build();
    } catch (IllegalArgumentException invalid) {
      throw invalid(RATE_LIMIT);
    }
    return new HostObservabilityConfig(
        observability,
        connections,
        requestBodyLimitKb * KIBIBYTE,
        Math.min(uploadMb, MAX_TRANSPORT_UPLOAD_MB) * MEBIBYTE,
        trustedProxies,
        allowedOrigins);
  }

  public ObservabilityConfig observability() {
    return observability;
  }

  public int maxWebSocketConnections() {
    return maxWebSocketConnections;
  }

  public int maxUploadBodyBytes() {
    return maxUploadBodyBytes;
  }

  public List<String> trustedProxies() {
    return trustedProxies;
  }

  public List<String> allowedOrigins() {
    return allowedOrigins;
  }

  /** Applies host-controlled protection limits to an otherwise default transport config. */
  public TransportConfig transportConfig(HttpBindConfig bind) {
    return transportConfig(bind, false);
  }

  /** Applies host-controlled protection limits and the startup-selected auth mode. */
  public TransportConfig transportConfig(HttpBindConfig bind, boolean tokenLockoutEnabled) {
    Objects.requireNonNull(bind, "bind");
    try {
      return TransportConfig.builder(bind)
          .observability(observability)
          .maxWebSocketConnections(maxWebSocketConnections)
          .maxBodyBytes(requestBodyLimitBytes)
          .maxUploadBodyBytes(maxUploadBodyBytes)
          .trustedProxies(trustedProxies)
          .allowedOrigins(allowedOrigins)
          .tokenLockoutEnabled(tokenLockoutEnabled)
          .build();
    } catch (IllegalArgumentException invalid) {
      throw invalid("transport");
    }
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

  private static List<String> stringList(ConfigView view, String setting) {
    Object raw = view.get(setting);
    if (raw == null) {
      return Collections.emptyList();
    }
    if (!(raw instanceof List)) {
      throw invalid(setting);
    }
    List<?> values = (List<?>) raw;
    if (values.size() > MAX_ORIGIN_OR_PROXY_ENTRIES) {
      throw invalid(setting);
    }
    List<String> result = new ArrayList<String>();
    for (Object item : values) {
      if (!(item instanceof String)) {
        throw invalid(setting);
      }
      String value = ((String) item).trim();
      if (value.isEmpty()) {
        throw invalid(setting);
      }
      for (int index = 0; index < value.length(); index++) {
        char character = value.charAt(index);
        if (character < 0x20 || character == 0x7f) {
          throw invalid(setting);
        }
      }
      result.add(value);
    }
    return Collections.unmodifiableList(result);
  }

  private static StartupConfigurationException invalid(String setting) {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("setting", setting);
    return new StartupConfigurationException(
        MessageKeys.STARTUP_OBSERVABILITY_INVALID_CONFIG, values);
  }
}
