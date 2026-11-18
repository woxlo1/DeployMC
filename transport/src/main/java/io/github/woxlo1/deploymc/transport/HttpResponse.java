package io.github.woxlo1.deploymc.transport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Internal, immutable response representation. */
public final class HttpResponse {

  private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

  private final int status;
  private final Map<String, List<String>> headers;
  private final byte[] body;

  private HttpResponse(int status, Map<String, List<String>> headers, byte[] body) {
    this.status = status;
    this.headers = Collections.unmodifiableMap(headers);
    this.body = body.clone();
  }

  public static HttpResponse status(int status) {
    return new HttpResponse(status, new LinkedHashMap<String, List<String>>(), new byte[0]);
  }

  public static HttpResponse json(int status, String json) {
    Objects.requireNonNull(json, "json");
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("content-type", singleton(JSON_CONTENT_TYPE));
    return new HttpResponse(status, headers, json.getBytes(StandardCharsets.UTF_8));
  }

  public static HttpResponse of(int status, String contentType, byte[] body) {
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(body, "body");
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("content-type", singleton(contentType));
    return new HttpResponse(status, headers, body);
  }

  public static HttpResponse error(int status, String code) {
    return json(status, "{\"code\":\"" + code + "\"}");
  }

  public HttpResponse withHeader(String name, String value) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    List<String> values = copy.get(name.toLowerCase(Locale.ROOT));
    if (values == null) {
      values = new ArrayList<>();
      copy.put(name.toLowerCase(Locale.ROOT), values);
    }
    values.add(value);
    return new HttpResponse(status, copy, body);
  }

  public int status() {
    return status;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public byte[] body() {
    return body.clone();
  }

  private static List<String> singleton(String value) {
    List<String> values = new ArrayList<>();
    values.add(value);
    return values;
  }
}
