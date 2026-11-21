package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded validator for Java {@code .properties} documents. */
public final class PropertiesValidator implements ContentValidator {

  @Override
  public String id() {
    return "properties";
  }

  @Override
  public ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits)
      throws ValidationException {
    String path = PathRules.normalize(relativePath);
    TextInput input = TextInput.decode(content, limits);
    if (input.hasEncodingIssue()) {
      return ValidationResult.matched(
          path, id(), Collections.singletonList(input.encodingIssue()), limits);
    }

    String text = input.text();
    int length = text.length();
    List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
    Set<String> keys = new HashSet<String>();
    int position = 0;
    while (position < length) {
      int logicalStart = position;
      int look = position;
      while (look < length && isWhitespace(text.charAt(look))) {
        look++;
      }
      if (look < length && (text.charAt(look) == '#' || text.charAt(look) == '!')) {
        position = nextLineStart(text, length, position);
        continue;
      }
      StringBuilder logical = new StringBuilder();
      boolean unterminated = false;
      while (true) {
        int end = position;
        while (end < length && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
          end++;
        }
        int contentEnd = end;
        while (contentEnd > position && text.charAt(contentEnd - 1) == '\\') {
          contentEnd--;
        }
        boolean continued = ((end - contentEnd) & 1) == 1;
        logical.append(text, position, contentEnd);
        if (continued) {
          if (end >= length) {
            unterminated = true;
            break;
          }
          position = end + 1;
          if (text.charAt(end) == '\r' && position < length && text.charAt(position) == '\n') {
            position++;
          }
          if (position >= length) {
            unterminated = true;
            break;
          }
          continue;
        }
        position = end;
        if (position < length) {
          char terminator = text.charAt(position);
          position++;
          if (terminator == '\r' && position < length && text.charAt(position) == '\n') {
            position++;
          }
        }
        break;
      }
      if (unterminated) {
        issues.add(issue(Severity.ERROR, "validation.properties-syntax", "", logicalStart, input));
        break;
      }
      parseLogicalLine(logical.toString(), logicalStart, input, limits, keys, issues);
    }
    return ValidationResult.matched(path, id(), issues, limits);
  }

  private static int nextLineStart(String text, int length, int position) {
    int end = position;
    while (end < length && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
      end++;
    }
    if (end >= length) {
      return length;
    }
    char terminator = text.charAt(end);
    int next = end + 1;
    if (terminator == '\r' && next < length && text.charAt(next) == '\n') {
      next++;
    }
    return next;
  }

  private static void parseLogicalLine(
      String line,
      int logicalStart,
      TextInput input,
      ValidationLimits limits,
      Set<String> keys,
      List<ValidationIssue> issues)
      throws ValidationLimitException {
    int length = line.length();
    int index = 0;
    while (index < length && isWhitespace(line.charAt(index))) {
      index++;
    }
    if (index >= length) {
      return;
    }
    char first = line.charAt(index);
    if (first == '#' || first == '!') {
      return;
    }

    StringBuilder key = new StringBuilder();
    boolean keyComplete = false;
    while (index < length) {
      char current = line.charAt(index);
      if (current == '\\') {
        Escape escape = decodeEscape(line, index, key, !keyComplete);
        if (!escape.valid) {
          issues.add(
              issue(
                  Severity.ERROR,
                  "validation.properties-invalid-escape",
                  keyPointer(key),
                  logicalStart,
                  input));
          return;
        }
        index = escape.nextIndex;
        continue;
      }
      if (!keyComplete) {
        if (current == '=' || current == ':') {
          keyComplete = true;
          index++;
          while (index < length && isWhitespace(line.charAt(index))) {
            index++;
          }
          continue;
        }
        if (isWhitespace(current)) {
          keyComplete = true;
          while (index < length && isWhitespace(line.charAt(index))) {
            index++;
          }
          if (index < length && (line.charAt(index) == '=' || line.charAt(index) == ':')) {
            index++;
            while (index < length && isWhitespace(line.charAt(index))) {
              index++;
            }
          }
          continue;
        }
        key.append(current);
      }
      index++;
    }

    if (key.length() == 0) {
      issues.add(issue(Severity.ERROR, "validation.properties-syntax", "", logicalStart, input));
      return;
    }
    String canonical = key.toString();
    if (canonical.length() > limits.maxKeyLength()) {
      throw new ValidationLimitException("maxKeyLength", canonical.length(), limits.maxKeyLength());
    }
    if (!keys.add(canonical)) {
      issues.add(
          issue(
              Severity.WARNING,
              "validation.properties-duplicate-key",
              "/" + Pointers.escape(canonical),
              logicalStart,
              input));
    }
  }

  private static Escape decodeEscape(
      String line, int index, StringBuilder key, boolean appendToKey) {
    int next = index + 1;
    if (next >= line.length()) {
      return Escape.invalid();
    }
    char escaped = line.charAt(next);
    switch (escaped) {
      case 't':
        append(key, appendToKey, '\t');
        return Escape.valid(next + 1);
      case 'n':
        append(key, appendToKey, '\n');
        return Escape.valid(next + 1);
      case 'r':
        append(key, appendToKey, '\r');
        return Escape.valid(next + 1);
      case 'f':
        append(key, appendToKey, '\f');
        return Escape.valid(next + 1);
      case '\\':
        append(key, appendToKey, '\\');
        return Escape.valid(next + 1);
      case '=':
        append(key, appendToKey, '=');
        return Escape.valid(next + 1);
      case ':':
        append(key, appendToKey, ':');
        return Escape.valid(next + 1);
      case '#':
        append(key, appendToKey, '#');
        return Escape.valid(next + 1);
      case '!':
        append(key, appendToKey, '!');
        return Escape.valid(next + 1);
      case ' ':
        append(key, appendToKey, ' ');
        return Escape.valid(next + 1);
      case 'u':
        return decodeUnicode(line, next + 1, key, appendToKey);
      default:
        return Escape.invalid();
    }
  }

  private static Escape decodeUnicode(
      String line, int hexStart, StringBuilder key, boolean appendToKey) {
    int value = hex4(line, hexStart);
    if (value < 0) {
      return Escape.invalid();
    }
    int after = hexStart + 4;
    if (Character.isHighSurrogate((char) value)) {
      if (after + 6 <= line.length()
          && line.charAt(after) == '\\'
          && line.charAt(after + 1) == 'u') {
        int low = hex4(line, after + 2);
        if (low >= 0 && Character.isLowSurrogate((char) low)) {
          append(key, appendToKey, (char) value);
          append(key, appendToKey, (char) low);
          return Escape.valid(after + 6);
        }
      }
      return Escape.invalid();
    }
    if (Character.isLowSurrogate((char) value)) {
      return Escape.invalid();
    }
    append(key, appendToKey, (char) value);
    return Escape.valid(after);
  }

  private static int hex4(String line, int start) {
    if (start < 0 || start + 4 > line.length()) {
      return -1;
    }
    int result = 0;
    for (int offset = 0; offset < 4; offset++) {
      int digit = hexDigit(line.charAt(start + offset));
      if (digit < 0) {
        return -1;
      }
      result = (result << 4) | digit;
    }
    return result;
  }

  private static int hexDigit(char value) {
    if (value >= '0' && value <= '9') {
      return value - '0';
    }
    if (value >= 'a' && value <= 'f') {
      return value - 'a' + 10;
    }
    if (value >= 'A' && value <= 'F') {
      return value - 'A' + 10;
    }
    return -1;
  }

  private static void append(StringBuilder key, boolean appendToKey, char value) {
    if (appendToKey) {
      key.append(value);
    }
  }

  private static boolean isWhitespace(char value) {
    return value == ' ' || value == '\t' || value == '\f';
  }

  private static String keyPointer(StringBuilder key) {
    if (key.length() == 0) {
      return "";
    }
    return "/" + Pointers.escape(key.toString());
  }

  private static ValidationIssue issue(
      Severity severity, String code, String pointer, int charIndex, TextInput input) {
    int[] position = input.position(charIndex);
    return new ValidationIssue(
        severity,
        code,
        pointer,
        position[0],
        position[1],
        input.byteOffset(charIndex),
        Collections.<String>emptyList());
  }

  private static final class Escape {
    private final boolean valid;
    private final int nextIndex;

    private Escape(boolean valid, int nextIndex) {
      this.valid = valid;
      this.nextIndex = nextIndex;
    }

    static Escape valid(int nextIndex) {
      return new Escape(true, nextIndex);
    }

    static Escape invalid() {
      return new Escape(false, -1);
    }
  }
}
