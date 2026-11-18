package io.github.woxlo1.deploymc.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Internal, immutable request representation. No transport library type is exposed. */
public final class HttpRequest {

  private final String id;
  private final String method;
  private final String path;
  private final String query;
  private final Map<String, List<String>> headers;
  private final byte[] body;

  public HttpRequest(
      String id,
      String method,
      String path,
      String query,
      Map<String, List<String>> headers,
      byte[] body) {
    this.id = Objects.requireNonNull(id, "id");
    this.method = Objects.requireNonNull(method, "method");
    this.path = Objects.requireNonNull(path, "path");
    this.query = query == null ? "" : query;
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      copy.put(key, Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
    }
    this.headers = Collections.unmodifiableMap(copy);
    this.body = Objects.requireNonNull(body, "body").clone();
  }

  public String id() {
    return id;
  }

  public String method() {
    return method;
  }

  public String path() {
    return path;
  }

  public String query() {
    return query;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public byte[] body() {
    return body.clone();
  }

  public String header(String name) {
    List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
    return values == null || values.isEmpty() ? null : values.get(0);
  }
}
