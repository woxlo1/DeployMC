package io.github.woxlo1.deploymc.core.rbac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The closed WP-3.4 permission registry. The wildcard is not a permission. */
public final class PermissionRegistry {

  public static final List<String> ALL =
      Collections.unmodifiableList(
          Arrays.asList(
              "server.read",
              "files.read",
              "files.write",
              "files.delete",
              "files.upload",
              "snapshots.read",
              "snapshots.restore",
              "snapshots.prune",
              "deployments.read",
              "deployments.create",
              "deployments.create.jar",
              "deployments.approve",
              "deployments.cancel",
              "deployments.rollback",
              "server.restart",
              "console.read",
              "console.command",
              "audit.read",
              "audit.export",
              "tokens.manage.self",
              "tokens.manage.all",
              "webhooks.manage",
              "users.read"));

  private static final Set<String> KNOWN =
      Collections.unmodifiableSet(new LinkedHashSet<String>(ALL));

  private PermissionRegistry() {}

  public static boolean isKnown(String permission) {
    return permission != null && KNOWN.contains(permission);
  }

  public static int indexOf(String permission) {
    return isKnown(permission) ? ALL.indexOf(permission) : -1;
  }

  /** Returns unique known permissions in canonical registry order. */
  public static List<String> canonical(Collection<String> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return Collections.emptyList();
    }
    Set<String> unique = new LinkedHashSet<String>();
    for (String permission : permissions) {
      if (isKnown(permission)) {
        unique.add(permission);
      }
    }
    List<String> ordered = new ArrayList<String>();
    for (String permission : ALL) {
      if (unique.remove(permission)) {
        ordered.add(permission);
      }
    }
    return Collections.unmodifiableList(ordered);
  }
}
