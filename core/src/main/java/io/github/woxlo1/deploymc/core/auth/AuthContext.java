package io.github.woxlo1.deploymc.core.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit, immutable identity context for one request or asynchronous task.
 *
 * <p>Services receive this value as an argument. It is safe to hand to an executor because it does
 * not rely on the executing thread's local state; a task that keeps the context keeps the identity.
 * No thread-local is used to establish or propagate an identity.
 */
public final class AuthContext {

  private static final int MAX_ATTRIBUTES = 16;
  private static final int MAX_KEY_LENGTH = 128;
  private static final int MAX_VALUE_LENGTH = 1024;

  private final String requestId;
  private final Identity identity;
  private final Map<String, String> attributes;

  private AuthContext(String requestId, Identity identity, Map<String, String> attributes) {
    this.requestId = requireBounded(requestId, "requestId", 128);
    this.identity = identity;
    this.attributes = Collections.unmodifiableMap(copyAttributes(attributes));
  }

  /** Creates an unauthenticated context. Only health and readiness probes may use it. */
  public static AuthContext anonymous(String requestId) {
    return new AuthContext(requestId, null, Collections.<String, String>emptyMap());
  }

  /** Creates an authenticated context for an established {@link Identity}. */
  public static AuthContext authenticated(String requestId, Identity identity) {
    return new AuthContext(
        requestId,
        Objects.requireNonNull(identity, "identity"),
        Collections.<String, String>emptyMap());
  }

  /** Creates an authenticated context with bounded, non-secret diagnostic attributes. */
  public static AuthContext of(
      String requestId, Identity identity, Map<String, String> attributes) {
    return new AuthContext(requestId, Objects.requireNonNull(identity, "identity"), attributes);
  }

  public String requestId() {
    return requestId;
  }

  /** Returns the verified identity, or {@code null} for an unauthenticated probe context. */
  public Identity identity() {
    return identity;
  }

  public boolean authenticated() {
    return identity != null;
  }

  public Map<String, String> attributes() {
    return attributes;
  }

  /** Returns a new context with one additional attribute; this context is unchanged. */
  public AuthContext withAttribute(String key, String value) {
    Map<String, String> updated = new LinkedHashMap<String, String>(attributes);
    updated.put(
        requireBounded(key, "attribute key", MAX_KEY_LENGTH),
        requireBounded(value, "attribute value", MAX_VALUE_LENGTH));
    return new AuthContext(requestId, identity, updated);
  }

  /** Never prints claim or attribute values into logs. */
  @Override
  public String toString() {
    return "AuthContext{requestId="
        + requestId
        + ", authenticated="
        + authenticated()
        + ", attributeCount="
        + attributes.size()
        + "}";
  }

  private static Map<String, String> copyAttributes(Map<String, String> attributes) {
    Objects.requireNonNull(attributes, "attributes");
    if (attributes.size() > MAX_ATTRIBUTES) {
      throw new IllegalArgumentException("too many context attributes");
    }
    Map<String, String> copy = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      copy.put(
          requireBounded(entry.getKey(), "attribute key", MAX_KEY_LENGTH),
          requireBounded(entry.getValue(), "attribute value", MAX_VALUE_LENGTH));
    }
    return copy;
  }

  private static String requireBounded(String value, String name, int maximum) {
    Objects.requireNonNull(value, name);
    if (value.length() > maximum) {
      throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
    }
    return value;
  }
}
