package io.github.woxlo1.deploymc.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable read-only view of a validated configuration.
 *
 * <p>This type deliberately has no write methods. API modules receive only this view, so an
 * authenticated request cannot mutate or persist the host-controlled configuration file.
 */
public final class ConfigView {

  private final Map<String, Object> values;

  @SuppressWarnings("unchecked")
  ConfigView(Map<String, Object> values) {
    this.values = (Map<String, Object>) deepFreeze(values);
  }

  public int configVersion() {
    Object version = get("config-version");
    return version instanceof Number ? ((Number) version).intValue() : -1;
  }

  public String language() {
    return getString("language", "en");
  }

  public Object get(String dottedPath) {
    if (dottedPath == null || dottedPath.isEmpty()) {
      return values;
    }
    Object current = values;
    for (String segment : dottedPath.split("\\.")) {
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<?, ?>) current).get(segment);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  public String getString(String dottedPath, String fallback) {
    Object value = get(dottedPath);
    return value instanceof String ? (String) value : fallback;
  }

  public int getInt(String dottedPath, int fallback) {
    Object value = get(dottedPath);
    return value instanceof Number ? ((Number) value).intValue() : fallback;
  }

  public boolean getBoolean(String dottedPath, boolean fallback) {
    Object value = get(dottedPath);
    return value instanceof Boolean ? (Boolean) value : fallback;
  }

  /** Returns a deeply immutable copy of the validated values. */
  public Map<String, Object> raw() {
    return values;
  }

  @SuppressWarnings("unchecked")
  private static Object deepFreeze(Object value) {
    if (value instanceof Map) {
      Map<String, Object> copy = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
        copy.put(entry.getKey(), deepFreeze(entry.getValue()));
      }
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof List) {
      List<Object> copy = new ArrayList<Object>();
      for (Object item : (List<Object>) value) {
        copy.add(deepFreeze(item));
      }
      return Collections.unmodifiableList(copy);
    }
    return value;
  }
}
