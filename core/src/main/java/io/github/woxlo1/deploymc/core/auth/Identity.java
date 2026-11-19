package io.github.woxlo1.deploymc.core.auth;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Minimal authenticated identity. The final RBAC form arrives in WP-3.1. */
public final class Identity {

  private final String id;
  private final String label;
  private final String method;
  private final boolean adminEquivalent;
  private final Set<String> groups;

  public Identity(
      String id, String label, String method, boolean adminEquivalent, Collection<String> groups) {
    this.id = requireText(id, "id");
    this.label = requireText(label, "label");
    this.method = requireText(method, "method");
    this.adminEquivalent = adminEquivalent;
    Set<String> copy = new LinkedHashSet<String>();
    if (groups != null) {
      for (String group : groups) {
        copy.add(requireText(group, "group"));
      }
    }
    this.groups = Collections.unmodifiableSet(copy);
  }

  /** Opaque identifier. Clients must not parse it. */
  public String id() {
    return id;
  }

  /** Human-readable label from the host-provisioned token record. */
  public String label() {
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

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }
}
