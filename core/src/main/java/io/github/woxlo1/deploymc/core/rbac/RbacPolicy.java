package io.github.woxlo1.deploymc.core.rbac;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable policy snapshot derived only from host-controlled configuration. */
public final class RbacPolicy {

  public static final int REGISTRY_VERSION = 1;

  private final Map<String, RolePolicy> roles;
  private final Map<String, List<String>> users;
  private final String defaultRole;
  private final boolean valid;

  private RbacPolicy(
      Map<String, RolePolicy> roles,
      Map<String, List<String>> users,
      String defaultRole,
      boolean valid) {
    this.roles = Collections.unmodifiableMap(new LinkedHashMap<String, RolePolicy>(roles));
    Map<String, List<String>> copiedUsers = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, List<String>> entry : users.entrySet()) {
      copiedUsers.put(
          entry.getKey(), Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
    }
    this.users = Collections.unmodifiableMap(copiedUsers);
    this.defaultRole = Objects.requireNonNull(defaultRole, "defaultRole");
    this.valid = valid;
  }

  /** Creates an immutable snapshot from explicit policies. */
  public static RbacPolicy of(
      Map<String, RolePolicy> roles, Map<String, List<String>> users, String defaultRole) {
    if (roles == null || users == null || defaultRole == null) {
      return denyAll();
    }
    if (!"none".equals(defaultRole) && !roles.containsKey(defaultRole)) {
      return denyAll();
    }
    if ("none".equals(defaultRole) && roles.containsKey("none")) {
      return denyAll();
    }
    return new RbacPolicy(roles, users, defaultRole, true);
  }

  /** Fail-closed snapshot for startup or reload failure. */
  public static RbacPolicy denyAll() {
    return new RbacPolicy(
        Collections.<String, RolePolicy>emptyMap(),
        Collections.<String, List<String>>emptyMap(),
        "none",
        false);
  }

  /** Builds an immutable snapshot. Any malformed input yields deny-all. */
  @SuppressWarnings("unchecked")
  public static RbacPolicy fromConfig(ConfigView view) {
    Objects.requireNonNull(view, "view");
    try {
      Map<String, Object> raw = view.raw();
      Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
      Object rolesValue = raw.get("roles");
      if (rolesValue instanceof Map) {
        for (Map.Entry<String, Object> entry : asStringMap(rolesValue).entrySet()) {
          Map<String, Object> role = asMap(entry.getValue());
          boolean wildcard = false;
          List<String> permissions = new ArrayList<String>();
          Object permissionsValue = role.get("permissions");
          if (permissionsValue instanceof List) {
            for (Object value : (List<Object>) permissionsValue) {
              if ("*".equals(value)) {
                wildcard = true;
              } else if (value instanceof String) {
                permissions.add((String) value);
              } else {
                return denyAll();
              }
            }
          } else {
            return denyAll();
          }
          roles.put(
              entry.getKey(),
              new RolePolicy(entry.getKey(), wildcard, permissions, readPathScope(role)));
        }
      }
      Map<String, List<String>> users = new LinkedHashMap<String, List<String>>();
      Object usersValue = raw.get("users");
      if (usersValue instanceof Map) {
        for (Map.Entry<String, Object> entry : asStringMap(usersValue).entrySet()) {
          Map<String, Object> user = asMap(entry.getValue());
          Object assigned = user.get("roles");
          if (!(assigned instanceof List)) {
            return denyAll();
          }
          List<String> names = new ArrayList<String>();
          for (Object value : (List<Object>) assigned) {
            if (!(value instanceof String)) {
              return denyAll();
            }
            names.add((String) value);
          }
          users.put(entry.getKey(), Collections.unmodifiableList(names));
        }
      }
      String defaultRole = view.getString("default-role", "none");
      if ("none".equals(defaultRole) && roles.containsKey("none")) {
        return denyAll();
      }
      if (!"none".equals(defaultRole) && !roles.containsKey(defaultRole)) {
        return denyAll();
      }
      return new RbacPolicy(roles, users, defaultRole, true);
    } catch (RuntimeException exception) {
      return denyAll();
    }
  }

  public boolean valid() {
    return valid;
  }

  public int registryVersion() {
    return REGISTRY_VERSION;
  }

  public int roleCount() {
    return roles.size();
  }

  public int userCount() {
    return users.size();
  }

  public String defaultRole() {
    return defaultRole;
  }

  public RolePolicy role(String name) {
    return name == null ? null : roles.get(name);
  }

  /** Assigned roles or the default role; never token id or token label. */
  public List<String> rolesFor(String identityId) {
    List<String> assigned = identityId == null ? null : users.get(identityId);
    if (assigned == null || assigned.isEmpty()) {
      return Collections.singletonList(defaultRole);
    }
    return assigned;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asStringMap(Object value) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
      if (!(entry.getKey() instanceof String)) {
        throw new IllegalArgumentException("non-string map key");
      }
      result.put((String) entry.getKey(), entry.getValue());
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("expected mapping");
    }
    return (Map<String, Object>) value;
  }

  private static PathScope readPathScope(Map<String, Object> role) {
    Object pathsValue = role.get("paths");
    if (pathsValue == null) {
      return PathScope.wholeSandbox(Collections.<String>emptyList());
    }
    Map<String, Object> paths = asMap(pathsValue);
    List<String> deny = stringList(paths.get("deny"));
    if (!paths.containsKey("allow")) {
      return PathScope.wholeSandbox(deny);
    }
    return PathScope.restricted(stringList(paths.get("allow")), deny);
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(Object value) {
    if (value == null) {
      return Collections.emptyList();
    }
    if (!(value instanceof List)) {
      throw new IllegalArgumentException("expected list");
    }
    List<String> result = new ArrayList<String>();
    for (Object item : (List<Object>) value) {
      if (!(item instanceof String)) {
        throw new IllegalArgumentException("expected string list");
      }
      result.add((String) item);
    }
    return result;
  }
}
