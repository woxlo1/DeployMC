package io.github.woxlo1.deploymc.transport;

/** Closed, low-cardinality classification of transport endpoints. */
public enum EndpointClass {
  HEALTH("health"),
  ME("me"),
  SERVER("server"),
  DOCTOR("doctor"),
  FILES("files"),
  VALIDATION("validation"),
  SNAPSHOTS("snapshots"),
  METRICS("metrics"),
  WEBSOCKET("websocket"),
  STATIC("static"),
  OTHER("other");

  private static final String DEFAULT_WEBSOCKET_PATH = "/api/v1/ws";

  private final String wireLabel;

  EndpointClass(String wireLabel) {
    this.wireLabel = wireLabel;
  }

  /** Stable Prometheus and structured-log label. */
  public String wireLabel() {
    return wireLabel;
  }

  /** Alias for {@link #wireLabel()} for callers using shorter naming. */
  public String label() {
    return wireLabel;
  }

  /** Classifies a path using the default WebSocket route. */
  public static EndpointClass fromPath(String path) {
    return fromPath(path, DEFAULT_WEBSOCKET_PATH);
  }

  /**
   * Classifies a path without retaining any client-supplied text. Unrecognized paths are always
   * {@link #OTHER}.
   */
  public static EndpointClass fromPath(String path, String webSocketPath) {
    if (path == null) {
      return OTHER;
    }
    String clean = path;
    int query = clean.indexOf('?');
    if (query >= 0) {
      clean = clean.substring(0, query);
    }
    int fragment = clean.indexOf('#');
    if (fragment >= 0) {
      clean = clean.substring(0, fragment);
    }
    if ("/healthz".equals(clean) || "/readyz".equals(clean)) {
      return HEALTH;
    }
    if ("/api/v1/me".equals(clean)) {
      return ME;
    }
    if ("/api/v1/server/doctor".equals(clean)) {
      return DOCTOR;
    }
    if ("/api/v1/server".equals(clean)) {
      return SERVER;
    }
    if (startsWithSegment(clean, "/api/v1/files")) {
      return FILES;
    }
    if (startsWithSegment(clean, "/api/v1/validation")) {
      return VALIDATION;
    }
    if (startsWithSegment(clean, "/api/v1/snapshots")) {
      return SNAPSHOTS;
    }
    if ("/api/v1/metrics".equals(clean)) {
      return METRICS;
    }
    if (clean.equals(webSocketPath == null ? DEFAULT_WEBSOCKET_PATH : webSocketPath)) {
      return WEBSOCKET;
    }
    if ("/".equals(clean)
        || "/index.html".equals(clean)
        || "/favicon.ico".equals(clean)
        || clean.startsWith("/assets/")) {
      return STATIC;
    }
    return OTHER;
  }

  private static boolean startsWithSegment(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }
}
