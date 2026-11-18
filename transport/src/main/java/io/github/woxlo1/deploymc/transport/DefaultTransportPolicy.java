package io.github.woxlo1.deploymc.transport;

import java.util.Locale;

/** Default fail-closed policy using loopback hosts and an explicit Origin allowlist. */
public final class DefaultTransportPolicy implements TransportPolicy {

  private final TransportConfig config;

  public DefaultTransportPolicy(TransportConfig config) {
    this.config = config;
  }

  @Override
  public boolean allowHost(String host) {
    if (host == null || host.trim().isEmpty()) {
      return false;
    }
    return config.allowedHosts().contains(normalizeHost(host));
  }

  @Override
  public boolean allowOrigin(String origin) {
    if (origin == null || origin.trim().isEmpty()) {
      return true;
    }
    return config.allowedOrigins().contains(origin.trim());
  }

  /** Extracts a comparable host from a Host header without DNS resolution. */
  public static String normalizeHost(String hostHeader) {
    String value = hostHeader.trim().toLowerCase(Locale.ROOT);
    if (value.startsWith("[")) {
      int end = value.indexOf(']');
      return end < 0 ? value : value.substring(0, end + 1);
    }
    int colon = value.lastIndexOf(':');
    return colon < 0 ? value : value.substring(0, colon);
  }
}
