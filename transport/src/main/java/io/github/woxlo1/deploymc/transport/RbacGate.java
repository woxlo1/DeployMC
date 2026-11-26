package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.AuthorizationDecision;
import io.github.woxlo1.deploymc.core.rbac.PathScope;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicy;
import io.github.woxlo1.deploymc.core.rbac.RolePolicy;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

/**
 * Centralized, closed route-to-permission mapping. Every decision goes through the product
 * evaluator; no raw request text, header, path, or identity detail enters diagnostics.
 */
public final class RbacGate {

  public static final String PERMISSION_SERVER_READ = "server.read";
  public static final String PERMISSION_FILES_READ = "files.read";
  public static final String PERMISSION_FILES_WRITE = "files.write";
  public static final String PERMISSION_FILES_UPLOAD = "files.upload";
  public static final String PERMISSION_FILES_DELETE = "files.delete";
  public static final String PERMISSION_SNAPSHOTS_READ = "snapshots.read";
  public static final String PERMISSION_SNAPSHOTS_RESTORE = "snapshots.restore";
  public static final String PERMISSION_SNAPSHOTS_PRUNE = "snapshots.prune";
  public static final String PERMISSION_CONSOLE_READ = "console.read";

  private static final String UNKNOWN_PERMISSION = "rbac.unknown";

  /** Non-empty sentinel that no decoder can produce; the sandbox resolver always rejects it. */
  private static final String INVALID_PATH = "\u0000";

  private static final String FILES_PREFIX = "/api/v1/files";
  private static final String UPLOAD_PATH = FILES_PREFIX + "/upload";
  private static final String RESTORE_SUFFIX = "/restore";
  private static final int MAX_QUERY_LENGTH = 8192;
  private static final int MAX_QUERY_PAIRS = 32;
  private static final int MAX_PATH_LENGTH = 1024;

  private final RbacEvaluator evaluator;
  private final String metricsPermission;
  private final RbacEvaluator noPermissionEvaluator;
  private final RbacEvaluator noGrantEvaluator;

  public RbacGate(RbacEvaluator evaluator) {
    this(evaluator, ObservabilityConfig.defaults().metricsPermission());
  }

  public RbacGate(RbacEvaluator evaluator, String metricsPermission) {
    this.evaluator = evaluator == null ? failClosedEvaluator() : evaluator;
    if (!ObservabilityConfig.isSupportedPermission(metricsPermission)) {
      throw new IllegalArgumentException("metricsPermission must be server.read");
    }
    this.metricsPermission = metricsPermission;
    this.noPermissionEvaluator = permitAllEvaluator();
    this.noGrantEvaluator = noGrantEvaluator();
  }

  /** Server-free decision for a fully authenticated HTTP request. */
  public AuthorizationDecision authorize(HttpRequest request) {
    Objects.requireNonNull(request, "request");
    String path = request.path();
    EndpointClass endpoint = EndpointClass.fromPath(path);
    AuthContext context = request.context();
    if (endpoint == EndpointClass.HEALTH
        || endpoint == EndpointClass.STATIC
        || endpoint == EndpointClass.ME) {
      return noPermission(context.identity());
    }
    if (context.identity() == null) {
      return noGrantEvaluator.authorize(context, UNKNOWN_PERMISSION, null);
    }
    switch (endpoint) {
      case DOCTOR:
      case SERVER:
        return evaluator.authorize(context, PERMISSION_SERVER_READ, null);
      case FILES:
        return authorizeFiles(request);
      case VALIDATION:
        return evaluator.authorize(context, PERMISSION_FILES_READ, null);
      case SNAPSHOTS:
        return authorizeSnapshots(request);
      case METRICS:
        return evaluator.authorize(context, metricsPermission, null);
      case WEBSOCKET:
      case OTHER:
      default:
        return unknown(context);
    }
  }

  /** Server-free decision for a WebSocket handshake or an individual text frame. */
  public AuthorizationDecision authorizeWebSocket(Identity identity) {
    if (identity == null) {
      return noGrantEvaluator.authorize(identity, UNKNOWN_PERMISSION, null);
    }
    return evaluator.authorize(identity, PERMISSION_CONSOLE_READ, null);
  }

  private AuthorizationDecision authorizeFiles(HttpRequest request) {
    String method = request.method().toUpperCase(Locale.ROOT);
    String permission;
    SandboxAccessMode mode = SandboxAccessMode.READ;
    if ("GET".equals(method) || "HEAD".equals(method)) {
      permission = PERMISSION_FILES_READ;
    } else if ("DELETE".equals(method)) {
      permission = PERMISSION_FILES_DELETE;
      mode = SandboxAccessMode.WRITE;
    } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
      permission =
          UPLOAD_PATH.equals(request.path()) ? PERMISSION_FILES_UPLOAD : PERMISSION_FILES_WRITE;
      mode = SandboxAccessMode.WRITE;
    } else {
      return unknown(request.context());
    }
    return evaluator.authorize(
        request.context().identity(), permission, requestedPath(request, request.path()), mode);
  }

  private AuthorizationDecision authorizeSnapshots(HttpRequest request) {
    String method = request.method().toUpperCase(Locale.ROOT);
    if ("DELETE".equals(method)) {
      return evaluator.authorize(request.context(), PERMISSION_SNAPSHOTS_PRUNE, null);
    }
    if (request.path().endsWith(RESTORE_SUFFIX)
        && ("POST".equals(method) || "PUT".equals(method))) {
      return evaluator.authorize(request.context(), PERMISSION_SNAPSHOTS_RESTORE, null);
    }
    if ("GET".equals(method) || "HEAD".equals(method)) {
      return evaluator.authorize(request.context(), PERMISSION_SNAPSHOTS_READ, null);
    }
    return unknown(request.context());
  }

  private AuthorizationDecision unknown(AuthContext context) {
    return noGrantEvaluator.authorize(context, UNKNOWN_PERMISSION, null);
  }

  /**
   * Routes with no extra permission use a fixed synthetic grant; the identity is never expanded.
   */
  private AuthorizationDecision noPermission(Identity identity) {
    return noPermissionEvaluator.authorize(identity, PERMISSION_SERVER_READ, null);
  }

  private static String requestedPath(HttpRequest request, String routePath) {
    String fromQuery = queryValue(request.query());
    if (fromQuery != null) {
      return fromQuery;
    }
    String prefix = FILES_PREFIX + "/";
    if (!routePath.startsWith(prefix)) {
      return null;
    }
    String suffix = routePath.substring(prefix.length());
    if (suffix.isEmpty() || suffix.indexOf('/') >= 0 || isRouteSegment(suffix)) {
      return null;
    }
    String decoded = decodePath(suffix);
    return decoded == null ? INVALID_PATH : decoded;
  }

  private static boolean isRouteSegment(String value) {
    return "content".equals(value)
        || "move".equals(value)
        || "upload".equals(value)
        || "validate".equals(value);
  }

  private static String queryValue(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    if (raw.length() > MAX_QUERY_LENGTH) {
      return INVALID_PATH;
    }
    int pairs = 0;
    for (String pair : raw.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      if (++pairs > MAX_QUERY_PAIRS) {
        return INVALID_PATH;
      }
      int separator = pair.indexOf('=');
      String key = separator < 0 ? pair : pair.substring(0, separator);
      if (!"path".equals(key)) {
        continue;
      }
      String value = separator < 0 ? "" : pair.substring(separator + 1);
      String decoded = decodePath(value);
      return decoded == null ? INVALID_PATH : decoded;
    }
    return null;
  }

  static String decodePath(String raw) {
    if (raw == null || raw.isEmpty() || raw.length() > MAX_PATH_LENGTH) {
      return null;
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length());
    for (int index = 0; index < raw.length(); index++) {
      char character = raw.charAt(index);
      if (character == '%') {
        if (index + 2 >= raw.length()) {
          return null;
        }
        int high = Character.digit(raw.charAt(index + 1), 16);
        int low = Character.digit(raw.charAt(index + 2), 16);
        if (high < 0 || low < 0) {
          return null;
        }
        bytes.write(high << 4 | low);
        index += 2;
      } else {
        byte[] encoded = String.valueOf(character).getBytes(StandardCharsets.UTF_8);
        bytes.write(encoded, 0, encoded.length);
      }
    }
    String decoded = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    if (decoded.isEmpty() || decoded.length() > MAX_PATH_LENGTH || decoded.indexOf('\ufffd') >= 0) {
      return null;
    }
    for (int index = 0; index < decoded.length(); index++) {
      if (Character.isISOControl(decoded.charAt(index))) {
        return null;
      }
    }
    return decoded;
  }

  private static RbacEvaluator failClosedEvaluator() {
    return new RbacEvaluator(RbacPolicy.denyAll(), null);
  }

  /**
   * Synthetic closed route: an assigned role with no grants yields NOT_GRANTED deterministically.
   */
  private static RbacEvaluator noGrantEvaluator() {
    RbacPolicy policy =
        RbacPolicy.of(
            Collections.singletonMap(
                "no-grant",
                new RolePolicy(
                    "no-grant",
                    false,
                    Collections.<String>emptyList(),
                    PathScope.wholeSandbox(Collections.<String>emptyList()))),
            Collections.<String, java.util.List<String>>emptyMap(),
            "no-grant");
    return new RbacEvaluator(policy, null);
  }

  private static RbacEvaluator permitAllEvaluator() {
    RbacPolicy policy =
        RbacPolicy.of(
            Collections.singletonMap(
                "all",
                new RolePolicy(
                    "all",
                    true,
                    Collections.<String>emptyList(),
                    PathScope.wholeSandbox(Collections.<String>emptyList()))),
            Collections.<String, java.util.List<String>>emptyMap(),
            "all");
    return new RbacEvaluator(policy, null);
  }
}
