package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, bounded JSON reader for signed JWT segments and JWKS documents.
 *
 * <p>This reader deliberately accepts only RFC 8259 JSON. It rejects duplicate keys, comments,
 * trailing characters, unescaped controls, excessive nesting, and oversized input so that an
 * untrusted assertion cannot turn into unbounded allocation. It never reports input text in an
 * exception message.
 */
final class JwtJson {

  static final int MAX_INPUT_CHARACTERS = 32 * 1024;
  private static final int MAX_NODES = 512;
  private static final int MAX_DEPTH = 8;
  private static final int MAX_STRING_CHARACTERS = 4096;

  private JwtJson() {}

  /** Parses exactly one JSON value. Throws {@link IllegalArgumentException} on any violation. */
  static Object parse(String source) {
    if (source == null || source.length() > MAX_INPUT_CHARACTERS) {
      throw invalid("input is missing or too large");
    }
    Reader reader = new Reader(source);
    Object value = reader.value(0);
    reader.whitespace();
    if (!reader.atEnd()) {
      throw invalid("trailing characters");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> asObject(Object value) {
    if (!(value instanceof Map)) {
      throw invalid("expected an object");
    }
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  static List<Object> asArray(Object value) {
    if (!(value instanceof List)) {
      throw invalid("expected an array");
    }
    return (List<Object>) value;
  }

  static String asString(Object value) {
    if (!(value instanceof String)) {
      throw invalid("expected a string");
    }
    return (String) value;
  }

  static long asInteger(Object value) {
    if (!(value instanceof Long)) {
      throw invalid("expected an integer");
    }
    return ((Long) value).longValue();
  }

  static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("invalid JSON: " + reason);
  }

  private static final class Reader {
    private final String text;
    private int index;
    private int nodes;

    Reader(String text) {
      this.text = text;
    }

    boolean atEnd() {
      return index >= text.length();
    }

    Object value(int depth) {
      if (depth > MAX_DEPTH || ++nodes > MAX_NODES) {
        throw invalid("input is too complex");
      }
      whitespace();
      if (atEnd()) {
        throw invalid("unexpected end of input");
      }
      char current = text.charAt(index);
      switch (current) {
        case '{':
          return objectValue(depth);
        case '[':
          return arrayValue(depth);
        case '"':
          return stringValue();
        case 't':
          return literal("true", Boolean.TRUE);
        case 'f':
          return literal("false", Boolean.FALSE);
        case 'n':
          return literal("null", null);
        default:
          return numberValue();
      }
    }

    private Map<String, Object> objectValue(int depth) {
      expect('{');
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      whitespace();
      if (consume('}')) {
        return result;
      }
      while (true) {
        whitespace();
        String key = stringValue();
        if (result.containsKey(key)) {
          throw invalid("duplicate key");
        }
        whitespace();
        expect(':');
        result.put(key, value(depth + 1));
        whitespace();
        if (consume('}')) {
          return result;
        }
        expect(',');
      }
    }

    private List<Object> arrayValue(int depth) {
      expect('[');
      List<Object> result = new ArrayList<Object>();
      whitespace();
      if (consume(']')) {
        return result;
      }
      while (true) {
        result.add(value(depth + 1));
        whitespace();
        if (consume(']')) {
          return result;
        }
        expect(',');
      }
    }

    private String stringValue() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (!atEnd()) {
        char current = text.charAt(index++);
        if (current == '"') {
          return result.toString();
        }
        if (current < 0x20) {
          throw invalid("unescaped control character");
        }
        if (current != '\\') {
          result.append(current);
          if (result.length() > MAX_STRING_CHARACTERS) {
            throw invalid("string is too long");
          }
          continue;
        }
        if (atEnd()) {
          throw invalid("bad escape");
        }
        char escaped = text.charAt(index++);
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
            result.append(unicodeEscape());
            break;
          default:
            throw invalid("bad escape");
        }
        if (result.length() > MAX_STRING_CHARACTERS) {
          throw invalid("string is too long");
        }
      }
      throw invalid("unterminated string");
    }

    private char unicodeEscape() {
      if (index + 4 > text.length()) {
        throw invalid("bad unicode escape");
      }
      int code = 0;
      for (int offset = 0; offset < 4; offset++) {
        char digit = text.charAt(index + offset);
        int value;
        if (digit >= '0' && digit <= '9') {
          value = digit - '0';
        } else if (digit >= 'a' && digit <= 'f') {
          value = digit - 'a' + 10;
        } else if (digit >= 'A' && digit <= 'F') {
          value = digit - 'A' + 10;
        } else {
          throw invalid("bad unicode escape");
        }
        code = code * 16 + value;
      }
      index += 4;
      return (char) code;
    }

    private Object numberValue() {
      int start = index;
      if (consume('-')) {
        // Sign consumed.
      }
      if (atEnd()) {
        throw invalid("bad number");
      }
      if (text.charAt(index) == '0') {
        index++;
        if (!atEnd() && Character.isDigit(text.charAt(index))) {
          throw invalid("leading zero");
        }
      } else {
        if (text.charAt(index) < '1' || text.charAt(index) > '9') {
          throw invalid("bad number");
        }
        while (!atEnd() && Character.isDigit(text.charAt(index))) {
          index++;
        }
      }
      boolean decimal = false;
      if (!atEnd() && text.charAt(index) == '.') {
        decimal = true;
        index++;
        if (atEnd() || !Character.isDigit(text.charAt(index))) {
          throw invalid("bad fraction");
        }
        while (!atEnd() && Character.isDigit(text.charAt(index))) {
          index++;
        }
      }
      if (!atEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
        decimal = true;
        index++;
        if (!atEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
          index++;
        }
        if (atEnd() || !Character.isDigit(text.charAt(index))) {
          throw invalid("bad exponent");
        }
        while (!atEnd() && Character.isDigit(text.charAt(index))) {
          index++;
        }
      }
      String number = text.substring(start, index);
      try {
        return decimal ? (Object) Double.valueOf(number) : (Object) Long.valueOf(number);
      } catch (NumberFormatException exception) {
        throw invalid("number is out of range");
      }
    }

    private Object literal(String token, Object value) {
      if (!text.startsWith(token, index)) {
        throw invalid("bad literal");
      }
      index += token.length();
      return value;
    }

    void whitespace() {
      while (!atEnd()) {
        char current = text.charAt(index);
        if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
          break;
        }
        index++;
      }
    }

    boolean consume(char expected) {
      if (!atEnd() && text.charAt(index) == expected) {
        index++;
        return true;
      }
      return false;
    }

    void expect(char expected) {
      if (!consume(expected)) {
        throw invalid("expected " + expected);
      }
    }
  }
}
