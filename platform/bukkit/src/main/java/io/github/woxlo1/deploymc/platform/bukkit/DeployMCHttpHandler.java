package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.transport.HealthHandler;
import io.github.woxlo1.deploymc.transport.HttpHandler;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import io.github.woxlo1.deploymc.transport.StaticAssetHandler;
import java.util.Objects;

/** Combines genuine health probes, the authenticated API, and the built Web UI. */
public final class DeployMCHttpHandler implements HttpHandler {

  private static final String FILE_API_PREFIX = "/api/v1/files";

  private final HealthHandler health = new HealthHandler();
  private final HttpHandler api;
  private final HttpHandler files;
  private final HttpHandler assets;

  public DeployMCHttpHandler(HttpHandler api) {
    this(api, null);
  }

  public DeployMCHttpHandler(HttpHandler api, HttpHandler files) {
    this.api = Objects.requireNonNull(api, "api");
    this.files = files;
    this.assets = new StaticAssetHandler(api);
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    Objects.requireNonNull(request, "request");
    String path = request.path();
    if ("/healthz".equals(path) || "/readyz".equals(path)) {
      return health.handle(request);
    }
    if (isFileApiPath(path) && files != null) {
      return files.handle(request);
    }
    if (path.startsWith("/api/")) {
      return api.handle(request);
    }
    return assets.handle(request);
  }

  private static boolean isFileApiPath(String path) {
    return FILE_API_PREFIX.equals(path) || path.startsWith(FILE_API_PREFIX + "/");
  }
}
