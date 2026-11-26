package io.github.woxlo1.deploymc.core.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFC 8785 canonicalization for the bounded integer-only profile used by audit records.
 *
 * <p>Only signed 64-bit integers, strings, booleans, null, arrays, and objects are accepted.
 * Decimals, exponents, duplicate keys, and unpaired surrogates are format errors instead of being
 * silently normalized.
 */
public final class AuditJson {

  private static final int MAX_DEPTH = 32;

  private AuditJson() {}

  /** Parses strict JSON into maps, lists, strings, longs, booleans, and null. */
  public static Object parse(String source) {
    if (source == null) {
      throw new IllegalArgumentException("json source is null");
    }
    Parser parser = new Parser(source);
    Object value = parser.value(1);
    parser.whitespace();
    if (!parser.finished()) {
      throw parser.error("trailing content");
    }
    return value;
  }

  /** Parses and requires an object at the top level. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String source) {
    Object value = parse(source);
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("json value is not an object");
    }
    return (Map<String, Object>) value;
  }

  /** Writes RFC 8785 canonical JSON for this profile. */
  public static String canonical(Object value) {
    StringBuilder result = new StringBuilder();
    write(value, result, 1);
    return result.toString();
  }

  private static void write(Object value, StringBuilder out, int depth) {
    if (depth > MAX_DEPTH) {
      throw new IllegalArgumentException("json value is too deeply nested");
    }
    if (value == null) {
      out.append("null");
      return;
    }
    if (value instanceof String) {
      string((String) value, out);
      return;
    }
    if (value instanceof Boolean) {
      out.append(((Boolean) value).booleanValue() ? "true" : "false");
      return;
    }
    if (value instanceof Long
        || value instanceof Integer
        || value instanceof Short
        || value instanceof Byte) {
      out.append(((Number) value).longValue());
      return;
    }
    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      List<String> keys = new ArrayList<String>(map.size());
      for (Object key : map.keySet()) {
        if (!(key instanceof String)) {
          throw new IllegalArgumentException("json object key is not a string");
        }
        keys.add((String) key);
      }
      Collections.sort(keys);
      out.append('{');
      for (int index = 0; index < keys.size(); index++) {
        if (index > 0) {
          out.append(',');
        }
        string(keys.get(index), out);
        out.append(':');
        write(map.get(keys.get(index)), out, depth + 1);
      }
      out.append('}');
      return;
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      out.append('[');
      for (int index = 0; index < list.size(); index++) {
        if (index > 0) {
          out.append(',');
        }
        write(list.get(index), out, depth + 1);
      }
      out.append(']');
      return;
    }
    throw new IllegalArgumentException("unsupported json value");
  }

  private static void string(String value, StringBuilder out) {
    validateSurrogates(value);
    out.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\b':
          out.append("\\b");
          break;
        case '\f':
          out.append("\\f");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (character < 0x20) {
            out.append("\\u00");
            out.append(Character.forDigit((character >> 4) & 0xf, 16));
            out.append(Character.forDigit(character & 0xf, 16));
          } else {
            out.append(character);
          }
      }
    }
    out.append('"');
  }

  private static void validateSurrogates(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isHighSurrogate(character)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException("unpaired json surrogate");
        }
        index++;
      } else if (Character.isLowSurrogate(character)) {
        throw new IllegalArgumentException("unpaired json surrogate");
      }
    }
  }

  private static final class Parser {

    private final String source;
    private int index;

    private Parser(String source) {
      this.source = source;
    }

    private boolean finished() {
      return index >= source.length();
    }

    private void whitespace() {
      while (!finished()) {
        char character = source.charAt(index);
        if (character == ' ' || character == '\n' || character == '\r' || character == '\t') {
          index++;
        } else {
          break;
        }
      }
    }

    private Object value(int depth) {
      if (depth > MAX_DEPTH) {
        throw error("json value is too deeply nested");
      }
      whitespace();
      if (finished()) {
        throw error("unexpected end of json");
      }
      char character = source.charAt(index);
      if (character == '{') {
        return object(depth);
      }
      if (character == '[') {
        return array(depth);
      }
      if (character == '"') {
        return string();
      }
      if (character == 't') {
        literal("true", Boolean.TRUE);
        return Boolean.TRUE;
      }
      if (character == 'f') {
        literal("false", Boolean.FALSE);
        return Boolean.FALSE;
      }
      if (character == 'n') {
        literal("null", null);
        return null;
      }
      if (character == '-' || (character >= '0' && character <= '9')) {
        return number();
      }
      throw error("unexpected json character");
    }

    private Map<String, Object> object(int depth) {
      index++;
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      whitespace();
      if (!finished() && source.charAt(index) == '}') {
        index++;
        return result;
      }
      while (true) {
        whitespace();
        if (finished() || source.charAt(index) != '"') {
          throw error("expected object key");
        }
        String key = string();
        if (result.containsKey(key)) {
          throw error("duplicate object key");
        }
        whitespace();
        if (finished() || source.charAt(index) != ':') {
          throw error("expected colon");
        }
        index++;
        result.put(key, value(depth + 1));
        whitespace();
        if (finished()) {
          throw error("unterminated object");
        }
        char separator = source.charAt(index++);
        if (separator == '}') {
          return result;
        }
        if (separator != ',') {
          throw error("expected comma");
        }
      }
    }

    private List<Object> array(int depth) {
      index++;
      List<Object> result = new ArrayList<Object>();
      whitespace();
      if (!finished() && source.charAt(index) == ']') {
        index++;
        return result;
      }
      while (true) {
        result.add(value(depth + 1));
        whitespace();
        if (finished()) {
          throw error("unterminated array");
        }
        char separator = source.charAt(index++);
        if (separator == ']') {
          return result;
        }
        if (separator != ',') {
          throw error("expected comma");
        }
      }
    }

    private String string() {
      index++;
      StringBuilder result = new StringBuilder();
      while (!finished()) {
        char character = source.charAt(index++);
        if (character == '"') {
          String value = result.toString();
          validateSurrogates(value);
          return value;
        }
        if (character < 0x20) {
          throw error("unescaped control character");
        }
        if (character != '\\') {
          result.append(character);
          continue;
        }
        if (finished()) {
          throw error("unterminated escape");
        }
        char escaped = source.charAt(index++);
        switch (escaped) {
          case '"':
          case '\\':
          case '/':
            result.append(escaped);
            break;
          case 'b':
            result.append('\b');
            break;
          case 'f':
            result.append('\f');
            break;
          case 'n':
            result.append('\n');
            break;
          case 'r':
            result.append('\r');
            break;
          case 't':
            result.append('\t');
            break;
          case 'u':
            result.append(unicode());
            break;
          default:
            throw error("invalid escape");
        }
      }
      throw error("unterminated string");
    }

    private char unicode() {
      if (index + 4 > source.length()) {
        throw error("incomplete unicode escape");
      }
      int value = 0;
      for (int count = 0; count < 4; count++) {
        int digit = Character.digit(source.charAt(index++), 16);
        if (digit < 0) {
          throw error("invalid unicode escape");
        }
        value = value << 4 | digit;
      }
      return (char) value;
    }

    private Long number() {
      int start = index;
      if (source.charAt(index) == '-') {
        index++;
      }
      if (finished()) {
        throw error("invalid number");
      }
      char first = source.charAt(index);
      if (first < '0' || first > '9') {
        throw error("invalid number");
      }
      if (first == '0') {
        index++;
        if (!finished()) {
          char next = source.charAt(index);
          if (next >= '0' && next <= '9') {
            throw error("leading zero");
          }
        }
      } else {
        while (!finished()) {
          char next = source.charAt(index);
          if (next < '0' || next > '9') {
            break;
          }
          index++;
        }
      }
      if (!finished()) {
        char next = source.charAt(index);
        if (next == '.' || next == 'e' || next == 'E') {
          throw error("non-integer number");
        }
      }
      try {
        return Long.valueOf(source.substring(start, index));
      } catch (NumberFormatException overflow) {
        throw error("integer out of range");
      }
    }

    private void literal(String expected, Object ignored) {
      if (!source.startsWith(expected, index)) {
        throw error("invalid literal");
      }
      index += expected.length();
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(message + " at offset " + index);
    }
  }
}
