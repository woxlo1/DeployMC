package io.github.woxlo1.deploymc.core.audit;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, immutable query filters for audit records. */
public final class AuditQuery {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 500;
  private static final String CURSOR_PREFIX = "audit:";

  private final Instant from;
  private final Instant to;
  private final String actor;
  private final String action;
  private final AuditResult result;
  private final String deploymentId;
  private final String path;
  private final String cursor;
  private final int limit;
  private final Long afterSeq;

  private AuditQuery(
      Instant from,
      Instant to,
      String actor,
      String action,
      AuditResult result,
      String deploymentId,
      String path,
      String cursor,
      int limit,
      Long afterSeq) {
    this.from = from;
    this.to = to;
    this.actor = actor;
    this.action = action;
    this.result = result;
    this.deploymentId = deploymentId;
    this.path = path;
    this.cursor = cursor;
    this.limit = limit;
    this.afterSeq = afterSeq;
  }

  public static AuditQuery defaults() {
    return new AuditQuery(null, null, null, null, null, null, null, null, DEFAULT_LIMIT, null);
  }

  /** Parses an already-decoded query parameter map. Unknown keys are rejected. */
  public static AuditQuery parse(Map<String, String> raw) throws AuditException {
    if (raw == null) {
      return defaults();
    }
    for (String key : raw.keySet()) {
      if (!known(key)) {
        throw AuditException.invalid("unknown audit filter");
      }
    }
    Instant from = timestamp(raw.get("from"), "from");
    Instant to = timestamp(raw.get("to"), "to");
    if (from != null && to != null && from.isAfter(to)) {
      throw AuditException.invalid("invalid audit time range");
    }
    String actor = text(raw.get("actor"), 256, "actor", false);
    String action = text(raw.get("action"), 128, "action", false);
    if (action != null && !AuditActions.valid(action)) {
      throw AuditException.invalid("invalid audit action filter");
    }
    AuditResult result = null;
    String resultText = raw.get("result");
    if (resultText != null) {
      result = AuditResult.parse(resultText);
      if (result == null) {
        throw AuditException.invalid("invalid audit result filter");
      }
    }
    String deploymentId = text(raw.get("deployment-id"), 256, "deployment-id", false);
    String path = text(raw.get("path"), 1024, "path", false);
    String cursor = text(raw.get("cursor"), 512, "cursor", false);
    int limit = DEFAULT_LIMIT;
    if (raw.get("limit") != null) {
      try {
        limit = Integer.parseInt(raw.get("limit"));
      } catch (NumberFormatException invalid) {
        throw AuditException.invalid("invalid audit limit");
      }
      if (limit < 1 || limit > MAX_LIMIT) {
        throw AuditException.invalid("audit limit is out of range");
      }
    }
    Long afterSeq = cursor == null ? null : decodeCursor(cursor);
    return new AuditQuery(
        from, to, actor, action, result, deploymentId, path, cursor, limit, afterSeq);
  }

  public boolean matches(AuditRecord record) {
    if (from != null && record.timestamp().isBefore(from)) {
      return false;
    }
    if (to != null && record.timestamp().isAfter(to)) {
      return false;
    }
    if (actor != null && !actor.equals(record.actor().id())) {
      return false;
    }
    if (action != null && !action.equals(record.action())) {
      return false;
    }
    if (result != null && result != record.result()) {
      return false;
    }
    if (deploymentId != null && !deploymentId.equals(record.deploymentId())) {
      return false;
    }
    if (path != null && (record.target() == null || !path.equals(record.target().path()))) {
      return false;
    }
    return true;
  }

  public int limit() {
    return limit;
  }

  public Long afterSeq() {
    return afterSeq;
  }

  public String cursor() {
    return cursor;
  }

  /** Only explicitly supplied filters, with no record bodies or values from returned rows. */
  public Map<String, Object> filterSummary() {
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    put(summary, "from", from == null ? null : from.toString());
    put(summary, "to", to == null ? null : to.toString());
    put(summary, "actor", actor);
    put(summary, "action", action);
    put(summary, "result", result == null ? null : result.wire());
    put(summary, "deployment-id", deploymentId);
    put(summary, "path", path);
    if (cursor != null) {
      put(summary, "cursor-present", Boolean.TRUE);
    }
    put(summary, "limit", Long.valueOf(limit));
    return Collections.unmodifiableMap(summary);
  }

  public static String encodeCursor(long lastSeq) {
    String value = CURSOR_PREFIX + Long.toString(lastSeq);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static Long decodeCursor(String cursor) throws AuditException {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cursor);
      String value = new String(decoded, StandardCharsets.UTF_8);
      if (!value.startsWith(CURSOR_PREFIX)) {
        throw new IllegalArgumentException("bad prefix");
      }
      long seq = Long.parseLong(value.substring(CURSOR_PREFIX.length()));
      if (seq < 1L) {
        throw new IllegalArgumentException("bad sequence");
      }
      return Long.valueOf(seq);
    } catch (IllegalArgumentException invalid) {
      throw AuditException.invalidCursor("invalid audit cursor");
    }
  }

  private static boolean known(String key) {
    return "from".equals(key)
        || "to".equals(key)
        || "actor".equals(key)
        || "action".equals(key)
        || "result".equals(key)
        || "deployment-id".equals(key)
        || "path".equals(key)
        || "cursor".equals(key)
        || "limit".equals(key);
  }

  private static Instant timestamp(String value, String name) throws AuditException {
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (RuntimeException invalid) {
      throw AuditException.invalid("invalid audit " + name);
    }
  }

  private static String text(String value, int maximum, String name, boolean required)
      throws AuditException {
    if (value == null) {
      if (required) {
        throw AuditException.invalid("missing audit " + name);
      }
      return null;
    }
    if (value.isEmpty() || value.length() > maximum) {
      throw AuditException.invalid("invalid audit " + name);
    }
    return value;
  }

  private static void put(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
