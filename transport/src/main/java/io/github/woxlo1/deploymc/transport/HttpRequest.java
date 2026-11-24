package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
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
  private final AuthContext context;

  public HttpRequest(
      String id,
      String method,
      String path,
      String query,
      Map<String, List<String>> headers,
      byte[] body) {
    this(id, method, path, query, headers, body, (Identity) null);
  }

  public HttpRequest(
      String id,
      String method,
      String path,
      String query,
      Map<String, List<String>> headers,
      byte[] body,
      Identity identity) {
    this(
        id,
        method,
        path,
        query,
        headers,
        body,
        identity == null ? AuthContext.anonymous(id) : AuthContext.authenticated(id, identity));
  }

  /** Creates a request carrying the explicit identity context produced by the auth filter. */
  public static HttpRequest withContext(
      String id,
      String method,
      String path,
      String query,
      Map<String, List<String>> headers,
      byte[] body,
      AuthContext context) {
    return new HttpRequest(id, method, path, query, headers, body, context);
  }

  private HttpRequest(
      String id,
      String method,
      String path,
      String query,
      Map<String, List<String>> headers,
      byte[] body,
      AuthContext context) {
    this.id = Objects.requireNonNull(id, "id");
    this.method = Objects.requireNonNull(method, "method");
    this.path = Objects.requireNonNull(path, "path");
    this.query = query == null ? "" : query;
    this.context = Objects.requireNonNull(context, "context");
    if (!id.equals(context.requestId())) {
      throw new IllegalArgumentException("context request id must match the request id");
    }
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

  /** Explicit identity context for this request; never derived from a client header. */
  public AuthContext context() {
    return context;
  }

  /**
   * The identity established by the transport authentication gate. It is {@code null} for
   * unauthenticated probes and for direct handler tests.
   */
  public Identity identity() {
    return context.identity();
  }
}
