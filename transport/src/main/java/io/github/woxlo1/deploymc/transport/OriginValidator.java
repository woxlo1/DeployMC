package io.github.woxlo1.deploymc.transport;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

/** Exact browser-origin validation for the embedded HTTP and WebSocket transport. */
public final class OriginValidator {

  private static final Set<String> LOOPBACK_HOSTS =
      Collections.unmodifiableSet(
          new HashSet<String>(Arrays.asList("localhost", "127.0.0.1", "[::1]", "::1")));

  private final Set<String> allowed;
  private final IntSupplier port;

  /** Creates a validator with explicit origins and a supplier for the effective bind port. */
  public OriginValidator(Collection<String> explicitOrigins, IntSupplier port) {
    Objects.requireNonNull(explicitOrigins, "explicitOrigins");
    this.port = Objects.requireNonNull(port, "port");
    if (explicitOrigins.size() > 64) {
      throw new IllegalArgumentException("allowedOrigins must contain at most 64 entries");
    }
    Set<String> canonical = new HashSet<String>();
    for (String origin : explicitOrigins) {
      if (!isWellFormed(origin)) {
        throw new IllegalArgumentException("allowedOrigins contains an invalid origin");
      }
      canonical.add(canonical(URI.create(origin.trim())));
    }
    this.allowed = Collections.unmodifiableSet(canonical);
  }

  /** True when the supplied origin is an absolute, exact HTTP(S) origin. */
  public static boolean isWellFormed(String value) {
    if (value == null || value.trim().isEmpty() || value.length() > 255) {
      return false;
    }
    try {
      URI uri = URI.create(value.trim());
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
        return false;
      }
      if (uri.getHost() == null || uri.getHost().isEmpty() || uri.getUserInfo() != null) {
        return false;
      }
      if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
        return false;
      }
      String rawPath = uri.getRawPath();
      if (rawPath != null && !rawPath.isEmpty()) {
        return false;
      }
      return uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  /** Checks one optional HTTP Origin header value. */
  public boolean allows(String origin) {
    ParsedOrigin parsed = parse(origin);
    if (parsed == null) {
      return false;
    }
    if (allowed.contains(parsed.canonical)) {
      return true;
    }
    if (!LOOPBACK_HOSTS.contains(parsed.host)) {
      return false;
    }
    int effectivePort = port.getAsInt();
    return effectivePort <= 0 || effectivePort == parsed.resolvedPort;
  }

  /** Checks that a WebSocket upgrade has exactly one well-formed, allowlisted Origin header. */
  public boolean allowsUpgrade(List<String> values) {
    return values != null && values.size() == 1 && allows(values.get(0));
  }

  private static ParsedOrigin parse(String value) {
    if (!isWellFormed(value)) {
      return null;
    }
    URI uri = URI.create(value.trim());
    return new ParsedOrigin(
        canonical(uri),
        normalizeHost(uri.getHost()),
        uri.getPort() < 0 ? defaultPort(uri) : uri.getPort());
  }

  private static String canonical(URI uri) {
    String host = normalizeHost(uri.getHost());
    int resolvedPort = uri.getPort() < 0 ? defaultPort(uri) : uri.getPort();
    return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + ":" + resolvedPort;
  }

  private static String normalizeHost(String host) {
    return host.toLowerCase(Locale.ROOT);
  }

  private static int defaultPort(URI uri) {
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private static final class ParsedOrigin {

    private final String canonical;
    private final String host;
    private final int resolvedPort;

    private ParsedOrigin(String canonical, String host, int resolvedPort) {
      this.canonical = canonical;
      this.host = host;
      this.resolvedPort = resolvedPort;
    }
  }
}
