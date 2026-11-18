package io.github.woxlo1.deploymc.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Serves the built Web UI from a fixed classpath resource root.
 *
 * <p>Only names below {@code deploymc-web/} are resolved. Request paths are decoded once, encoded
 * separators are rejected, and dot segments are removed before lookup, so a request can never reach
 * another JAR entry. Unknown non-API paths without a file extension fall back to {@code index.html}
 * for client-side routing. Paths under {@code /api} are delegated or return 404 and are never used
 * for the SPA fallback.
 */
public final class StaticAssetHandler implements HttpHandler {

  private static final String RESOURCE_ROOT = "deploymc-web";
  private static final String INDEX = "index.html";
  private static final int MAX_ASSET_BYTES = 8 * 1024 * 1024;
  private static final long IMMUTABLE_MAX_AGE_SECONDS = 365L * 24 * 60 * 60;
  private static final long SHORT_MAX_AGE_SECONDS = 60L * 60;
  private static final Pattern HASHED_ASSET = Pattern.compile("-[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9]+$");

  private static final String WEB_CSP =
      "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; "
          + "script-src 'self' 'wasm-unsafe-eval'; worker-src 'self' blob:; child-src 'self' blob:; "
          + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
          + "font-src 'self' data:; connect-src 'self'";

  private static final Map<String, String> CONTENT_TYPES = contentTypes();

  private final HttpHandler apiHandler;
  private final ClassLoader classLoader;

  public StaticAssetHandler() {
    this(null, StaticAssetHandler.class.getClassLoader());
  }

  public StaticAssetHandler(HttpHandler apiHandler) {
    this(apiHandler, StaticAssetHandler.class.getClassLoader());
  }

  public StaticAssetHandler(HttpHandler apiHandler, ClassLoader classLoader) {
    this.apiHandler = apiHandler;
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    String decoded = decode(request.path());
    if (decoded == null) {
      return notFound();
    }
    if (isApiPath(decoded)) {
      return apiHandler == null ? notFound() : apiHandler.handle(request);
    }
    String relative = normalize(decoded);
    if (relative == null) {
      return notFound();
    }
    boolean spaRoute = relative.isEmpty() || !hasFileExtension(relative);
    if (relative.isEmpty() || relative.equals(INDEX)) {
      relative = INDEX;
    } else if (spaRoute) {
      relative = INDEX;
    }

    byte[] bytes = load(RESOURCE_ROOT + "/" + relative);
    if (bytes == null) {
      return notFound();
    }

    String encoding = null;
    if (!spaRoute && isCompressible(relative)) {
      if (acceptsEncoding(request, "br")) {
        byte[] brotli = load(RESOURCE_ROOT + "/" + relative + ".br");
        if (brotli != null) {
          bytes = brotli;
          encoding = "br";
        }
      }
      if (encoding == null && acceptsEncoding(request, "gzip")) {
        byte[] gzip = load(RESOURCE_ROOT + "/" + relative + ".gz");
        if (gzip != null) {
          bytes = gzip;
          encoding = "gzip";
        }
      }
    }

    HttpResponse response = HttpResponse.of(200, contentType(relative), bytes);
    response = response.withHeader("Cache-Control", cacheControl(relative, spaRoute));
    response = response.withHeader("Vary", "Accept-Encoding");
    if (encoding != null) {
      response = response.withHeader("Content-Encoding", encoding);
    }
    if (isHtml(relative)) {
      response = response.withHeader("Content-Security-Policy", WEB_CSP);
    }
    return response;
  }

  private static boolean isApiPath(String decoded) {
    return decoded.equals("/api") || decoded.startsWith("/api/");
  }

  private static String decode(String raw) {
    if (raw == null || raw.isEmpty() || raw.charAt(0) != '/') {
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
        int value = (high << 4) | low;
        if (value == '/' || value == '\\' || value == 0) {
          return null;
        }
        bytes.write(value);
        index += 2;
        continue;
      }
      if (character == '\\' || character < 0x20 || character == 0x7f) {
        return null;
      }
      byte[] encoded = String.valueOf(character).getBytes(StandardCharsets.UTF_8);
      bytes.write(encoded, 0, encoded.length);
    }
    return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String normalize(String decoded) {
    if (decoded.equals("/")) {
      return "";
    }
    String[] segments = decoded.substring(1).split("/", -1);
    List<String> safe = new ArrayList<>();
    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      if (segment.isEmpty()) {
        if (index == segments.length - 1) {
          continue;
        }
        return null;
      }
      if (segment.equals(".") || segment.equals("..") || segment.indexOf('%') >= 0) {
        return null;
      }
      safe.add(segment);
    }
    StringBuilder joined = new StringBuilder();
    for (String segment : safe) {
      if (joined.length() > 0) {
        joined.append('/');
      }
      joined.append(segment);
    }
    return joined.toString();
  }

  private static boolean hasFileExtension(String relative) {
    int slash = relative.lastIndexOf('/');
    int dot = relative.lastIndexOf('.');
    return dot > slash && dot < relative.length() - 1;
  }

  private static String cacheControl(String relative, boolean spaRoute) {
    if (spaRoute || relative.equals(INDEX)) {
      return "no-store";
    }
    String name = relative.substring(relative.lastIndexOf('/') + 1);
    if (HASHED_ASSET.matcher(name).find()) {
      return "public, max-age=" + IMMUTABLE_MAX_AGE_SECONDS + ", immutable";
    }
    return "public, max-age=" + SHORT_MAX_AGE_SECONDS;
  }

  private static boolean isCompressible(String relative) {
    String extension = extension(relative);
    return "js".equals(extension)
        || "mjs".equals(extension)
        || "css".equals(extension)
        || "json".equals(extension)
        || "svg".equals(extension)
        || "txt".equals(extension)
        || "wasm".equals(extension);
  }

  private static boolean isHtml(String relative) {
    return "html".equals(extension(relative));
  }

  private static boolean acceptsEncoding(HttpRequest request, String encoding) {
    String header = request.header("accept-encoding");
    if (header == null) {
      return false;
    }
    for (String entry : header.split(",")) {
      String[] parts = entry.split(";");
      String name = parts[0].trim().toLowerCase(Locale.ROOT);
      double quality = 1.0;
      for (int index = 1; index < parts.length; index++) {
        String parameter = parts[index].trim();
        if (parameter.startsWith("q=")) {
          try {
            quality = Double.parseDouble(parameter.substring(2).trim());
          } catch (NumberFormatException ignored) {
            quality = 0.0;
          }
        }
      }
      if (quality > 0 && (encoding.equals(name) || "*".equals(name))) {
        return true;
      }
    }
    return false;
  }

  private byte[] load(String resource) {
    try (InputStream stream = classLoader.getResourceAsStream(resource)) {
      if (stream == null) {
        return null;
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        if (output.size() + read > MAX_ASSET_BYTES) {
          return null;
        }
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    } catch (IOException ignored) {
      return null;
    }
  }

  private static HttpResponse notFound() {
    return HttpResponse.error(404, "not_found");
  }

  private static String contentType(String relative) {
    String contentType = CONTENT_TYPES.get(extension(relative));
    return contentType == null ? "application/octet-stream" : contentType;
  }

  private static String extension(String relative) {
    int slash = relative.lastIndexOf('/');
    int dot = relative.lastIndexOf('.');
    if (dot <= slash || dot == relative.length() - 1) {
      return "";
    }
    return relative.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static Map<String, String> contentTypes() {
    Map<String, String> types = new HashMap<>();
    types.put("html", "text/html; charset=utf-8");
    types.put("js", "text/javascript; charset=utf-8");
    types.put("mjs", "text/javascript; charset=utf-8");
    types.put("css", "text/css; charset=utf-8");
    types.put("json", "application/json; charset=utf-8");
    types.put("map", "application/json; charset=utf-8");
    types.put("svg", "image/svg+xml");
    types.put("png", "image/png");
    types.put("jpg", "image/jpeg");
    types.put("jpeg", "image/jpeg");
    types.put("gif", "image/gif");
    types.put("webp", "image/webp");
    types.put("ico", "image/x-icon");
    types.put("woff", "font/woff");
    types.put("woff2", "font/woff2");
    types.put("ttf", "font/ttf");
    types.put("wasm", "application/wasm");
    types.put("txt", "text/plain; charset=utf-8");
    return Collections.unmodifiableMap(types);
  }
}
