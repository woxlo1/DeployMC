package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.transport.HttpHandler;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Minimal application handler for the documented {@code GET /api/v1/server} resource. */
public final class ServerInfoHandler implements HttpHandler {

  public static final String API_VERSION = "v1";
  private static final String PATH = "/api/v1/server";

  private final String body;
  private final String etag;

  public ServerInfoHandler(
      String deployMcVersion, PlatformInfo platform, PlatformCapabilities capabilities) {
    Objects.requireNonNull(deployMcVersion, "deployMcVersion");
    Objects.requireNonNull(platform, "platform");
    Objects.requireNonNull(capabilities, "capabilities");
    this.body = serialize(deployMcVersion, platform, capabilities);
    this.etag = "\"" + sha256(body) + "\"";
  }

  @Override
  public HttpResponse handle(HttpRequest request) {
    Objects.requireNonNull(request, "request");
    if (!PATH.equals(request.path())) {
      return HttpResponse.error(404, "not_found");
    }
    if (!"GET".equals(request.method())) {
      return HttpResponse.error(405, "method_not_allowed").withHeader("Allow", "GET");
    }
    if (etag.equals(request.header("if-none-match"))) {
      return HttpResponse.status(304)
          .withHeader("ETag", etag)
          .withHeader("X-Request-Id", request.id());
    }
    return HttpResponse.json(200, body)
        .withHeader("ETag", etag)
        .withHeader("X-Request-Id", request.id());
  }

  private static String serialize(
      String deployMcVersion, PlatformInfo platform, PlatformCapabilities capabilities) {
    StringBuilder result = new StringBuilder();
    result.append("{\"deployMcVersion\":").append(quote(deployMcVersion));
    result.append(",\"apiVersion\":").append(quote(API_VERSION));
    result.append(",\"platform\":{");
    result.append("\"softwareName\":").append(quote(platform.softwareName()));
    result.append(",\"softwareVersion\":").append(quote(platform.softwareVersion()));
    result.append(",\"minecraftVersion\":").append(quote(platform.minecraftVersion()));
    result.append(",\"javaVersion\":").append(quote(platform.javaVersion()));
    result.append(",\"operatingSystem\":").append(quote(platform.operatingSystem()));
    result.append("},\"capabilities\":{");
    boolean first = true;
    for (Map.Entry<String, io.github.woxlo1.deploymc.api.CapabilityState> entry :
        capabilities.asMap().entrySet()) {
      if (!first) {
        result.append(',');
      }
      first = false;
      result.append(quote(entry.getKey())).append(':').append(quote(entry.getValue().name()));
    }
    result.append("}}");
    return result.toString();
  }

  private static String quote(String value) {
    StringBuilder result = new StringBuilder(value.length() + 2);
    result.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        result.append('\\');
      } else if (character < 0x20) {
        result.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character)));
        continue;
      }
      result.append(character);
    }
    return result.append('"').toString();
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        result.append(String.format(Locale.ROOT, "%02x", Byte.valueOf(item)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
