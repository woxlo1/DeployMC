package io.github.woxlo1.deploymc.transport;

import java.util.Objects;

/** Minimal liveness and readiness handler. Returns no state beyond the transport being up. */
public final class HealthHandler implements HttpHandler {

  @Override
  public HttpResponse handle(HttpRequest request) {
    Objects.requireNonNull(request, "request");
    String path = request.path();
    if (!"/healthz".equals(path) && !"/readyz".equals(path)) {
      return HttpResponse.error(404, "not_found");
    }
    String body = "{\"status\":\"ok\"}";
    return HttpResponse.json(200, body).withHeader("X-Request-Id", request.id());
  }
}
