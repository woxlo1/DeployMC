package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Authenticated Prometheus metrics route. The host registers this handler only when metrics are
 * enabled; until RBAC exists, only admin-equivalent identities pass the permission check.
 */
public final class MetricsHandler implements HttpHandler {

  static final String PATH = "/api/v1/metrics";
  static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

  private final MetricsRegistry registry;
  private final String permission;
  private final TransportMessages messages;

  public MetricsHandler(MetricsRegistry registry, String permission) {
    this(registry, permission, TransportMessages.of(Locale.ENGLISH));
  }

  MetricsHandler(MetricsRegistry registry, String permission, TransportMessages messages) {
    this.registry = Objects.requireNonNull(registry, "registry");
    if (!ObservabilityConfig.isSupportedPermission(permission)) {
      throw new IllegalArgumentException("permission is not a supported stable identifier");
    }
    this.permission = permission;
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  /** Stable permission identifier required by this route until RBAC evaluation exists. */
  public String permission() {
    return permission;
  }

  @Override
  public HttpResponse handle(HttpRequest request) {
    Objects.requireNonNull(request, "request");
    if (!PATH.equals(request.path())) {
      return TransportProblems.metricsNotFound(request.id(), messages);
    }
    if (!"GET".equals(request.method())) {
      return TransportProblems.problem(
              405,
              "request.method-not-allowed",
              MessageKeys.REQUEST_METHOD_NOT_ALLOWED_TITLE,
              MessageKeys.REQUEST_METHOD_NOT_ALLOWED_DETAIL,
              request.id(),
              messages,
              false)
          .withHeader("Allow", "GET");
    }
    AuthContext context = request.context();
    Identity identity = context.identity();
    if (identity == null) {
      return TransportProblems.problem(
          401,
          "auth.required",
          MessageKeys.AUTH_REQUIRED_TITLE,
          MessageKeys.AUTH_REQUIRED_DETAIL,
          context.requestId(),
          messages,
          false);
    }
    if (!identity.adminEquivalent()) {
      return TransportProblems.metricsForbidden(context.requestId(), messages);
    }
    return HttpResponse.of(
            200, CONTENT_TYPE, registry.renderPrometheus().getBytes(StandardCharsets.UTF_8))
        .withHeader(TransportProblems.REQUEST_ID, context.requestId());
  }
}
