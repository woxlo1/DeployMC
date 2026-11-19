package io.github.woxlo1.deploymc.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A parsed YAML value with the source location needed for precise diagnostics. */
public final class YamlValue {

  private final Object value;
  private final int line;
  private final int column;

  public YamlValue(Object value, int line, int column) {
    this.value = value;
    this.line = line;
    this.column = column;
  }

  public Object raw() {
    if (value instanceof Map) {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, YamlValue> entry : entries().entrySet()) {
        result.put(entry.getKey(), entry.getValue().raw());
      }
      return result;
    }
    if (value instanceof List) {
      List<Object> result = new ArrayList<Object>();
      for (YamlValue item : items()) {
        result.add(item.raw());
      }
      return result;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public Map<String, YamlValue> entries() {
    if (value instanceof Map) {
      return (Map<String, YamlValue>) value;
    }
    return Collections.emptyMap();
  }

  @SuppressWarnings("unchecked")
  public List<YamlValue> items() {
    if (value instanceof List) {
      return (List<YamlValue>) value;
    }
    return Collections.emptyList();
  }

  public YamlValue get(String key) {
    return entries().get(key);
  }

  public Set<String> keys() {
    return entries().keySet();
  }

  public boolean isNull() {
    return value == null;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public boolean isObject() {
    return value instanceof Map;
  }

  public boolean isArray() {
    return value instanceof List;
  }

  public boolean isString() {
    return value instanceof String;
  }

  public boolean isInteger() {
    return value instanceof Long;
  }

  public boolean isNumber() {
    return value instanceof Number;
  }

  public boolean isBoolean() {
    return value instanceof Boolean;
  }

  public String jsonType() {
    if (isNull()) {
      return "null";
    }
    if (isObject()) {
      return "object";
    }
    if (isArray()) {
      return "array";
    }
    if (isString()) {
      return "string";
    }
    if (isInteger()) {
      return "integer";
    }
    if (isNumber()) {
      return "number";
    }
    if (isBoolean()) {
      return "boolean";
    }
    return "unknown";
  }
}
