package io.github.woxlo1.deploymc.core.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable request data passed to an {@link Authenticator}.
 *
 * <p>This is the final WP-3.1 authentication boundary. It deliberately carries no application
 * identity: an identity may only be produced by an authenticator from verified credential material.
 * Header values are copied so a transport buffer cannot change during verification.
 */
public final class AuthenticationRequest {

  private static final int MAX_REQUEST_ID_LENGTH = 128;
  private static final int MAX_METHOD_LENGTH = 32;
  private static final int MAX_PATH_LENGTH = 4096;
  private static final int MAX_HEADERS = 64;
  private static final int MAX_HEADER_VALUES = 8;
  private static final int MAX_HEADER_NAME_LENGTH = 256;
  private static final int MAX_HEADER_VALUE_LENGTH = 8192;

  private final String requestId;
  private final String method;
  private final String path;
  private final Map<String, List<String>> headers;

  public AuthenticationRequest(
      String requestId, String method, String path, Map<String, List<String>> headers) {
    this.requestId = requireNonBlank(requestId, "requestId", MAX_REQUEST_ID_LENGTH);
    this.method = requireNonBlank(method, "method", MAX_METHOD_LENGTH).toUpperCase(Locale.ROOT);
    this.path = requireNonBlank(path, "path", MAX_PATH_LENGTH);
    Objects.requireNonNull(headers, "headers");
    Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      String name = requireNonBlank(entry.getKey(), "header name", MAX_HEADER_NAME_LENGTH);
      String normalized = name.toLowerCase(Locale.ROOT);
      List<String> values = copy.get(normalized);
      if (values == null) {
        values = new ArrayList<String>();
        copy.put(normalized, values);
      }
      if (values.size() > MAX_HEADER_VALUES) {
        throw new IllegalArgumentException("too many values for one header");
      }
      if (entry.getValue() == null) {
        throw new IllegalArgumentException("header values must not be null");
      }
      for (String value : entry.getValue()) {
        if (values.size() >= MAX_HEADER_VALUES) {
          throw new IllegalArgumentException("too many values for one header");
        }
        values.add(requireBounded(value, "header value", MAX_HEADER_VALUE_LENGTH));
      }
    }
    if (copy.size() > MAX_HEADERS) {
      throw new IllegalArgumentException("too many headers");
    }
    Map<String, List<String>> immutable = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, List<String>> entry : copy.entrySet()) {
      immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
    }
    this.headers = Collections.unmodifiableMap(immutable);
  }

  public String requestId() {
    return requestId;
  }

  public String method() {
    return method;
  }

  public String path() {
    return path;
  }

  /** Returns every header as an immutable map with lower-case names. */
  public Map<String, List<String>> headers() {
    return headers;
  }

  /** Returns the first value of one header, or {@code null}. Name matching is case-insensitive. */
  public String header(String name) {
    Objects.requireNonNull(name, "name");
    List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  /** Never prints header values; credential material must not enter logs through this object. */
  @Override
  public String toString() {
    return "AuthenticationRequest{requestId="
        + requestId
        + ", method="
        + method
        + ", path="
        + path
        + ", headerCount="
        + headers.size()
        + "}";
  }

  private static String requireNonBlank(String value, String name, int maximum) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    if (value.length() > maximum) {
      throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
    }
    return value;
  }

  private static String requireBounded(String value, String name, int maximum) {
    Objects.requireNonNull(value, name);
    if (value.length() > maximum) {
      throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
    }
    return value;
  }
}
