package io.github.woxlo1.deploymc.core.auth;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable authenticated identity produced only by an {@link Authenticator}. */
public final class Identity {

  private static final int MAX_CLAIMS = 32;
  private static final int MAX_CLAIM_KEY_LENGTH = 128;
  private static final int MAX_CLAIM_VALUE_LENGTH = 1024;

  private final String id;
  private final String label;
  private final String method;
  private final boolean adminEquivalent;
  private final Set<String> groups;
  private final Map<String, String> claims;

  public Identity(
      String id, String label, String method, boolean adminEquivalent, Collection<String> groups) {
    this(id, label, method, adminEquivalent, groups, Collections.<String, String>emptyMap());
  }

  /** Creates an identity with the bounded, non-secret raw claim subset selected by the verifier. */
  public Identity(
      String id,
      String label,
      String method,
      boolean adminEquivalent,
      Collection<String> groups,
      Map<String, String> claims) {
    this.id = requireText(id, "id");
    this.label = requireText(label, "label");
    this.method = requireText(method, "method");
    this.adminEquivalent = adminEquivalent;
    Set<String> groupCopy = new LinkedHashSet<String>();
    if (groups != null) {
      for (String group : groups) {
        groupCopy.add(requireText(group, "group"));
      }
    }
    this.groups = Collections.unmodifiableSet(groupCopy);
    Objects.requireNonNull(claims, "claims");
    if (claims.size() > MAX_CLAIMS) {
      throw new IllegalArgumentException("too many claims");
    }
    Map<String, String> claimCopy = new TreeMap<String, String>();
    for (Map.Entry<String, String> entry : claims.entrySet()) {
      claimCopy.put(
          requireBounded(entry.getKey(), "claim key", MAX_CLAIM_KEY_LENGTH),
          requireBounded(entry.getValue(), "claim value", MAX_CLAIM_VALUE_LENGTH));
    }
    this.claims = Collections.unmodifiableMap(claimCopy);
  }

  /** Opaque identifier. Clients must not parse it. */
  public String id() {
    return id;
  }

  /** Human-readable display name from the verified credential. */
  public String label() {
    return label;
  }

  /** Alias for {@link #label()} using the terminology of the master specification. */
  public String displayName() {
    return label;
  }

  /** Authentication method, for example {@code token}. */
  public String method() {
    return method;
  }

  /**
   * Whether this identity has every permission until RBAC arrives in 0.4.0. This is a temporary
   * compatibility flag, not a grant that services should interpret as a role.
   */
  public boolean adminEquivalent() {
    return adminEquivalent;
  }

  public Set<String> groups() {
    return groups;
  }

  /** Immutable raw claim subset. Never contains credential material. */
  public Map<String, String> claims() {
    return claims;
  }

  /** Never prints claim values into logs. */
  @Override
  public String toString() {
    return "Identity{id="
        + id
        + ", method="
        + method
        + ", groupCount="
        + groups.size()
        + ", claimCount="
        + claims.size()
        + "}";
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }

  private static String requireBounded(String value, String name, int maximum) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    if (value.length() > maximum) {
      throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
    }
    return value;
  }
}
