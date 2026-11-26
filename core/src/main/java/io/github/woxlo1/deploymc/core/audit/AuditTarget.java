package io.github.woxlo1.deploymc.core.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Sandbox-relative audit target. Absolute host paths never enter the log. */
public final class AuditTarget {

  private static final int MAX_PATH = 1024;
  private static final Pattern REVISION = Pattern.compile("^sha256:[0-9a-f]{64}$");

  private final String path;
  private final String revision;

  private AuditTarget(String path, String revision) {
    if (path != null) {
      if (path.isEmpty()
          || path.length() > MAX_PATH
          || path.startsWith("/")
          || path.startsWith("\\")
          || path.indexOf('\\') >= 0
          || path.contains("..")) {
        throw new IllegalArgumentException("invalid audit target path");
      }
      for (int index = 0; index < path.length(); index++) {
        if (Character.isISOControl(path.charAt(index))) {
          throw new IllegalArgumentException("invalid audit target path");
        }
      }
    }
    if (revision != null && !REVISION.matcher(revision).matches()) {
      throw new IllegalArgumentException("invalid audit target revision");
    }
    if (path == null && revision == null) {
      throw new IllegalArgumentException("empty audit target");
    }
    this.path = path;
    this.revision = revision;
  }

  public static AuditTarget path(String path) {
    return new AuditTarget(path, null);
  }

  public static AuditTarget pathAndRevision(String path, String revision) {
    return new AuditTarget(path, revision);
  }

  public static AuditTarget revision(String revision) {
    return new AuditTarget(null, revision);
  }

  /** Rebuilds a target from a parsed record. */
  public static AuditTarget fromJson(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("target must be an object");
    }
    Map<?, ?> source = (Map<?, ?>) value;
    Object path = source.get("path");
    Object revision = source.get("revision");
    if (path != null && !(path instanceof String)) {
      throw new IllegalArgumentException("invalid target path");
    }
    if (revision != null && !(revision instanceof String)) {
      throw new IllegalArgumentException("invalid target revision");
    }
    if (path == null && revision == null) {
      throw new IllegalArgumentException("empty audit target");
    }
    return new AuditTarget((String) path, (String) revision);
  }

  public String path() {
    return path;
  }

  public String revision() {
    return revision;
  }

  public Map<String, Object> toJsonObject() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    if (path != null) {
      result.put("path", path);
    }
    if (revision != null) {
      result.put("revision", revision);
    }
    return result;
  }
}
