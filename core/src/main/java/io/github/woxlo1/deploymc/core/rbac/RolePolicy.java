package io.github.woxlo1.deploymc.core.rbac;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable role grant and path policy. */
public final class RolePolicy {

  private final String name;
  private final boolean wildcard;
  private final Set<String> permissions;
  private final PathScope pathScope;

  public RolePolicy(
      String name, boolean wildcard, Collection<String> permissions, PathScope pathScope) {
    this.name = Objects.requireNonNull(name, "name");
    this.wildcard = wildcard;
    Set<String> unique = new LinkedHashSet<String>();
    if (permissions != null) {
      for (String permission : permissions) {
        if (PermissionRegistry.isKnown(permission)) {
          unique.add(permission);
        }
      }
    }
    this.permissions = Collections.unmodifiableSet(unique);
    this.pathScope = Objects.requireNonNull(pathScope, "pathScope");
  }

  public String name() {
    return name;
  }

  public boolean wildcard() {
    return wildcard;
  }

  public boolean grants(String permission) {
    return wildcard || PermissionRegistry.isKnown(permission) && permissions.contains(permission);
  }

  public List<String> grantedPermissions() {
    return wildcard ? PermissionRegistry.ALL : PermissionRegistry.canonical(permissions);
  }

  public PathScope pathScope() {
    return pathScope;
  }
}
