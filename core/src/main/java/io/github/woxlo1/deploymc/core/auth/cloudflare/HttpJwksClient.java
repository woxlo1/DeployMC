package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Host-configured JWKS client with bounded I/O, no redirects, and ETag revalidation. */
public final class HttpJwksClient implements JwksClient {

  static final int MAX_RESPONSE_BYTES = 64 * 1024;
  private static final int DEFAULT_TIMEOUT_MILLIS = 5_000;
  private static final Pattern MAX_AGE = Pattern.compile("(?i)(?:^|,)\\s*max-age\\s*=\\s*(\\d+)");
  private static final int MAX_ETAG_LENGTH = 256;

  private final URL url;
  private final int timeoutMillis;

  /** Creates a client. Plain HTTP is permitted only for a loopback test endpoint. */
  public HttpJwksClient(String endpoint) {
    this(endpoint, DEFAULT_TIMEOUT_MILLIS);
  }

  public HttpJwksClient(String endpoint, int timeoutMillis) {
    Objects.requireNonNull(endpoint, "endpoint");
    if (timeoutMillis <= 0 || timeoutMillis > 30_000) {
      throw new IllegalArgumentException("timeoutMillis must be between 1 and 30000");
    }
    URI uri;
    try {
      uri = new URI(endpoint);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("JWKS endpoint is not a valid URI");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost();
    boolean loopback =
        host != null
            && ("localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host));
    if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getRawQuery() != null) {
      throw new IllegalArgumentException(
          "JWKS endpoint must not carry user info, query, or fragment");
    }
    if (host == null || uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
      throw new IllegalArgumentException("JWKS endpoint is not an absolute HTTP URL");
    }
    if ("https".equals(scheme)) {
      // TLS endpoint with default or explicit port is valid.
    } else if ("http".equals(scheme) && loopback) {
      // Local fixture server only; no production endpoint uses plaintext.
    } else {
      throw new IllegalArgumentException("JWKS endpoint requires HTTPS unless it is loopback HTTP");
    }
    if (uri.getPort() < -1 || uri.getPort() > 65535) {
      throw new IllegalArgumentException("JWKS endpoint port is invalid");
    }
    try {
      this.url = uri.toURL();
    } catch (IllegalArgumentException | IOException exception) {
      throw new IllegalArgumentException("JWKS endpoint is not a usable URL");
    }
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public JwksResponse fetch(String etag) throws JwksFetchException {
    HttpURLConnection connection = null;
    InputStream stream = null;
    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(timeoutMillis);
      connection.setReadTimeout(timeoutMillis);
      connection.setRequestMethod("GET");
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Accept", "application/json");
      if (etag != null && isStrongETag(etag)) {
        connection.setRequestProperty("If-None-Match", etag);
      }
      int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
        return JwksResponse.notModified();
      }
      if (status != HttpURLConnection.HTTP_OK) {
        throw new JwksFetchException("JWKS endpoint returned HTTP " + status);
      }
      stream = connection.getInputStream();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int total = 0;
      int read;
      while ((read = stream.read(buffer)) != -1) {
        total += read;
        if (total > MAX_RESPONSE_BYTES) {
          throw new JwksFetchException("JWKS response exceeded the size limit");
        }
        output.write(buffer, 0, read);
      }
      if (total == 0) {
        throw new JwksFetchException("JWKS response was empty");
      }
      String reportedETag = strongETag(connection.getHeaderField("ETag"));
      return JwksResponse.cached(
          output.toByteArray(),
          reportedETag,
          maxAgeSeconds(connection.getHeaderField("Cache-Control")));
    } catch (IOException exception) {
      throw new JwksFetchException("JWKS endpoint could not be reached", exception);
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignored) {
          // Closing is best effort; the fetch result is already decided.
        }
      }
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /** Endpoint used by this client. The value is host configuration, never user input. */
  public String endpoint() {
    return url.toString();
  }

  private static int maxAgeSeconds(String cacheControl) {
    if (cacheControl == null) {
      return 0;
    }
    Matcher matcher = MAX_AGE.matcher(cacheControl);
    if (!matcher.find()) {
      return 0;
    }
    try {
      long seconds = Long.parseLong(matcher.group(1));
      return seconds > 86_400L ? 86_400 : (int) seconds;
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private static String strongETag(String value) {
    return isStrongETag(value) ? value : null;
  }

  private static boolean isStrongETag(String value) {
    if (value == null || value.length() < 3 || value.length() > MAX_ETAG_LENGTH) {
      return false;
    }
    if (value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
      return false;
    }
    for (int index = 1; index < value.length() - 1; index++) {
      char character = value.charAt(index);
      if (character == '"' || character < 0x21 || character > 0x7e) {
        return false;
      }
    }
    return true;
  }
}
