package io.github.woxlo1.deploymc.core.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, verified identity information attached to an audit record. */
public final class AuditActor {

  private static final int MAX_ID = 256;
  private static final int MAX_METHOD = 32;
  private static final int MAX_IP = 128;
  private static final int MAX_ROLES = 64;
  private static final int MAX_ROLE = 128;

  private final String id;
  private final String method;
  private final List<String> roles;
  private final String ip;

  private AuditActor(String id, String method, Collection<String> roles, String ip) {
    this.id = require(id, "id", MAX_ID);
    this.method = require(method, "method", MAX_METHOD);
    Set<String> sorted = new java.util.TreeSet<String>();
    if (roles != null) {
      for (String role : roles) {
        sorted.add(require(role, "role", MAX_ROLE));
      }
    }
    if (sorted.size() > MAX_ROLES) {
      throw new IllegalArgumentException("too many roles");
    }
    this.roles = Collections.unmodifiableList(new ArrayList<String>(sorted));
    this.ip = ip == null ? null : require(ip, "ip", MAX_IP);
  }

  public static AuditActor system() {
    return new AuditActor("system", "system", Collections.<String>emptyList(), null);
  }

  public static AuditActor of(String id, String method, Collection<String> roles, String ip) {
    return new AuditActor(id, method, roles, ip);
  }

  /** Rebuilds an actor from a parsed record. Unknown fields are ignored. */
  public static AuditActor fromJson(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("actor must be an object");
    }
    Map<?, ?> source = (Map<?, ?>) value;
    Object id = source.get("id");
    Object method = source.get("method");
    Object roles = source.get("roles");
    Object ip = source.get("ip");
    if (!(id instanceof String) || !(method instanceof String) || !(roles instanceof List)) {
      throw new IllegalArgumentException("invalid actor");
    }
    List<String> parsed = new ArrayList<String>();
    for (Object role : (List<?>) roles) {
      if (!(role instanceof String)) {
        throw new IllegalArgumentException("invalid actor role");
      }
      parsed.add((String) role);
    }
    if (ip != null && !(ip instanceof String)) {
      throw new IllegalArgumentException("invalid actor ip");
    }
    return new AuditActor((String) id, (String) method, parsed, (String) ip);
  }

  public String id() {
    return id;
  }

  public String method() {
    return method;
  }

  public List<String> roles() {
    return roles;
  }

  public String ip() {
    return ip;
  }

  /** Canonical object form used for hashing and REST responses. */
  public Map<String, Object> toJsonObject() {
    Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("method", method);
    result.put("roles", new ArrayList<Object>(roles));
    if (ip != null) {
      result.put("ip", ip);
    }
    return result;
  }

  private static String require(String value, String name, int maximum) {
    if (value == null || value.trim().isEmpty() || value.length() > maximum) {
      throw new IllegalArgumentException("invalid audit actor " + name);
    }
    for (int index = 0; index < value.length(); index++) {
      if (Character.isISOControl(value.charAt(index))) {
        throw new IllegalArgumentException("invalid audit actor " + name);
      }
    }
    return value;
  }
}
