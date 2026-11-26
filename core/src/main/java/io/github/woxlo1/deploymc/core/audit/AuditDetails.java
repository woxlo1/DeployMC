package io.github.woxlo1.deploymc.core.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rejects sensitive audit details before the record is hashed.
 *
 * <p>The guard is deliberately conservative: a suspicious key or value is a programming error and
 * no line is written. It never removes a field, because silent redaction would break the chain and
 * hide the bug that produced the field.
 */
public final class AuditDetails {

  private static final int MAX_KEYS = 64;
  private static final int MAX_DEPTH = 8;
  private static final int MAX_STRING = 4096;
  private static final Pattern SENSITIVE_KEY =
      Pattern.compile(
          "(?i).*(password|passwd|secret|credential|token|jwt|cookie|authorization|"
              + "api[-_]?key|private[-_]?key|body|content|excerpt).*");
  private static final Pattern SENSITIVE_VALUE =
      Pattern.compile("(?i)(bearer\\s+|eyJ[A-Za-z0-9_-]{8,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)");

  private AuditDetails() {}

  /** Returns a deep, bounded copy, or throws when any name or value is forbidden. */
  public static Map<String, Object> validate(Map<String, Object> details) {
    Map<String, Object> copy = new LinkedHashMap<String, Object>();
    if (details == null || details.isEmpty()) {
      return copy;
    }
    copy(details, copy, 1);
    return copy;
  }

  @SuppressWarnings("unchecked")
  private static void copy(Map<String, Object> source, Map<String, Object> target, int depth) {
    if (depth > MAX_DEPTH || source.size() > MAX_KEYS) {
      throw new IllegalArgumentException("audit details exceed bounded structure");
    }
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String key = entry.getKey();
      if (key == null
          || key.isEmpty()
          || key.length() > 128
          || SENSITIVE_KEY.matcher(key).matches()) {
        throw new IllegalArgumentException("sensitive audit detail name");
      }
      for (int index = 0; index < key.length(); index++) {
        if (Character.isISOControl(key.charAt(index))) {
          throw new IllegalArgumentException("invalid audit detail name");
        }
      }
      target.put(key, value(entry.getValue(), depth));
    }
  }

  @SuppressWarnings("unchecked")
  private static Object value(Object source, int depth) {
    if (source == null || source instanceof Boolean) {
      return source;
    }
    if (source instanceof String) {
      String text = (String) source;
      if (text.length() > MAX_STRING || SENSITIVE_VALUE.matcher(text).find()) {
        throw new IllegalArgumentException("sensitive audit detail value");
      }
      return text;
    }
    if (source instanceof Integer || source instanceof Short || source instanceof Byte) {
      return Long.valueOf(((Number) source).longValue());
    }
    if (source instanceof Long) {
      return source;
    }
    if (source instanceof Map) {
      Map<String, Object> nested = new LinkedHashMap<String, Object>();
      copy((Map<String, Object>) source, nested, depth + 1);
      return nested;
    }
    if (source instanceof List) {
      List<?> list = (List<?>) source;
      if (list.size() > MAX_KEYS) {
        throw new IllegalArgumentException("audit detail list exceeds bound");
      }
      List<Object> result = new ArrayList<Object>(list.size());
      for (Object item : list) {
        result.add(value(item, depth + 1));
      }
      return result;
    }
    throw new IllegalArgumentException("unsupported audit detail value");
  }
}
