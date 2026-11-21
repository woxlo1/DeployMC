package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict RFC 8259 JSON validator with bounded structural work. */
public final class JsonValidator implements ContentValidator {

  private static final String FORMAT = "json";
  private static final String SYNTAX = "validation.json-syntax";
  private static final String DUPLICATE_KEY = "validation.json-duplicate-key";
  private static final String DEPTH_ISSUE = "validation.json-depth";

  @Override
  public String id() {
    return FORMAT;
  }

  @Override
  public ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits)
      throws ValidationException {
    String path = PathRules.normalize(relativePath);
    TextInput input = TextInput.decode(content, limits);
    if (input.hasEncodingIssue()) {
      return ValidationResult.matched(
          path, FORMAT, Collections.singletonList(input.encodingIssue()), limits);
    }
    Parser parser = new Parser(input, limits);
    parser.parseDocument();
    return ValidationResult.matched(path, FORMAT, parser.issues(), limits);
  }

  private static final class Parser {

    private final TextInput input;
    private final String text;
    private final ValidationLimits limits;
    private final List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
    private int index;
    private int nodes;
    private boolean aborted;

    Parser(TextInput input, ValidationLimits limits) {
      this.input = input;
      this.text = input.text();
      this.limits = limits;
    }

    List<ValidationIssue> issues() {
      return issues;
    }

    void parseDocument() throws ValidationLimitException {
      skipWhitespace();
      if (index >= text.length()) {
        addIssue(SYNTAX, index, "");
        return;
      }
      parseValue(1, "");
      if (aborted) {
        return;
      }
      skipWhitespace();
      if (index < text.length()) {
        addIssue(SYNTAX, index, "");
      }
    }

    private void parseValue(int depth, String pointer) throws ValidationLimitException {
      if (depth > limits.maxDepth()) {
        throw new ValidationLimitException(DEPTH_ISSUE, "maxDepth", depth, limits.maxDepth());
      }
      countNode();
      if (index >= text.length()) {
        addIssue(SYNTAX, index, pointer);
        aborted = true;
        return;
      }
      char current = text.charAt(index);
      switch (current) {
        case '{':
          parseObject(depth, pointer);
          return;
        case '[':
          parseArray(depth, pointer);
          return;
        case '"':
          parseString(pointer);
          return;
        case 't':
          parseLiteral("true", pointer);
          return;
        case 'f':
          parseLiteral("false", pointer);
          return;
        case 'n':
          parseLiteral("null", pointer);
          return;
        default:
          if (current == '-' || isDigit(current)) {
            parseNumber(pointer);
          } else {
            addIssue(SYNTAX, index, pointer);
            aborted = true;
          }
      }
    }

    private void parseObject(int depth, String pointer) throws ValidationLimitException {
      index++;
      Set<String> keys = new HashSet<String>();
      skipWhitespace();
      if (index < text.length() && text.charAt(index) == '}') {
        index++;
        return;
      }
      while (true) {
        skipWhitespace();
        if (index >= text.length() || text.charAt(index) != '"') {
          addIssue(SYNTAX, index, pointer);
          aborted = true;
          return;
        }
        int keyStart = index;
        String key = parseString(pointer);
        if (aborted || key == null) {
          return;
        }
        if (key.length() > limits.maxKeyLength()) {
          throw new ValidationLimitException("maxKeyLength", key.length(), limits.maxKeyLength());
        }
        String keyPointer = Pointers.append(pointer, key);
        if (!keys.add(key)) {
          addIssue(DUPLICATE_KEY, keyStart, keyPointer);
        }
        skipWhitespace();
        if (index >= text.length() || text.charAt(index) != ':') {
          addIssue(SYNTAX, index, pointer);
          aborted = true;
          return;
        }
        index++;
        skipWhitespace();
        parseValue(depth + 1, keyPointer);
        if (aborted) {
          return;
        }
        skipWhitespace();
        if (index >= text.length()) {
          addIssue(SYNTAX, index, pointer);
          aborted = true;
          return;
        }
        char current = text.charAt(index);
        if (current == '}') {
          index++;
          return;
        }
        if (current != ',') {
          addIssue(SYNTAX, index, pointer);
          aborted = true;
          return;
        }
        index++;
      }
    }

    private void parseArray(int depth, String pointer) throws ValidationLimitException {
      index++;
      int elementIndex = 0;
      skipWhitespace();
      if (index < text.length() && text.charAt(index) == ']') {
        index++;
        return;
      }
      while (true) {
        skipWhitespace();
        String elementPointer = Pointers.append(pointer, Integer.toString(elementIndex));
        parseValue(depth + 1, elementPointer);
        if (aborted) {
          return;
        }
        elementIndex++;
        skipWhitespace();
        if (index >= text.length()) {
          addIssue(SYNTAX, index, pointer);
          aborted = true;
          return;
        }
        char current = text.charAt(index);
        if (current == ']') {
          index++;
          return;
        }
        if (current != ',') {
          addIssue(SYNTAX, index, pointer);
          aborted = true;
          return;
        }
        index++;
      }
    }

    private String parseString(String parentPointer) {
      index++;
      StringBuilder value = new StringBuilder();
      while (index < text.length()) {
        char current = text.charAt(index);
        if (current == '"') {
          index++;
          return value.toString();
        }
        if (current < 0x20) {
          addIssue(SYNTAX, index, parentPointer);
          aborted = true;
          return null;
        }
        if (current != '\\') {
          value.append(current);
          index++;
          continue;
        }
        int escapeIndex = index;
        if (escapeIndex + 1 >= text.length()) {
          addIssue(SYNTAX, escapeIndex, parentPointer);
          aborted = true;
          return null;
        }
        char escape = text.charAt(escapeIndex + 1);
        switch (escape) {
          case '"':
            value.append('"');
            index += 2;
            break;
          case '\\':
            value.append('\\');
            index += 2;
            break;
          case '/':
            value.append('/');
            index += 2;
            break;
          case 'b':
            value.append('\b');
            index += 2;
            break;
          case 'f':
            value.append('\f');
            index += 2;
            break;
          case 'n':
            value.append('\n');
            index += 2;
            break;
          case 'r':
            value.append('\r');
            index += 2;
            break;
          case 't':
            value.append('\t');
            index += 2;
            break;
          case 'u':
            if (!parseUnicodeEscape(escapeIndex, value, parentPointer)) {
              aborted = true;
              return null;
            }
            break;
          default:
            addIssue(SYNTAX, escapeIndex + 1, parentPointer);
            aborted = true;
            return null;
        }
      }
      addIssue(SYNTAX, index, parentPointer);
      aborted = true;
      return null;
    }

    private boolean parseUnicodeEscape(int escapeIndex, StringBuilder value, String parentPointer) {
      int high = decodeHexUnit(escapeIndex, parentPointer);
      if (high < 0) {
        return false;
      }
      char unit = (char) high;
      if (Character.isLowSurrogate(unit)) {
        addIssue(SYNTAX, escapeIndex, parentPointer);
        return false;
      }
      if (Character.isHighSurrogate(unit)) {
        int lowEscapeIndex = escapeIndex + 6;
        if (!hasUnicodeEscapeAt(lowEscapeIndex)) {
          addIssue(SYNTAX, lowEscapeIndex, parentPointer);
          return false;
        }
        int low = decodeHexUnit(lowEscapeIndex, parentPointer);
        if (low < 0) {
          return false;
        }
        if (!Character.isLowSurrogate((char) low)) {
          addIssue(SYNTAX, lowEscapeIndex, parentPointer);
          return false;
        }
        value.append(unit).append((char) low);
        index = lowEscapeIndex + 6;
        return true;
      }
      value.append(unit);
      index = escapeIndex + 6;
      return true;
    }

    private int decodeHexUnit(int escapeIndex, String parentPointer) {
      if (!hasUnicodeEscapeAt(escapeIndex) || escapeIndex + 5 >= text.length()) {
        addIssue(SYNTAX, Math.min(escapeIndex + 2, text.length()), parentPointer);
        return -1;
      }
      int result = 0;
      for (int offset = 2; offset <= 5; offset++) {
        int digit = hexValue(text.charAt(escapeIndex + offset));
        if (digit < 0) {
          addIssue(SYNTAX, escapeIndex + offset, parentPointer);
          return -1;
        }
        result = (result << 4) | digit;
      }
      return result;
    }

    private boolean hasUnicodeEscapeAt(int charIndex) {
      return charIndex >= 0
          && charIndex + 1 < text.length()
          && text.charAt(charIndex) == '\\'
          && text.charAt(charIndex + 1) == 'u';
    }

    private void parseLiteral(String literal, String parentPointer) {
      for (int offset = 0; offset < literal.length(); offset++) {
        if (index >= text.length() || text.charAt(index) != literal.charAt(offset)) {
          addIssue(SYNTAX, index, parentPointer);
          aborted = true;
          return;
        }
        index++;
      }
    }

    private void parseNumber(String parentPointer) {
      if (text.charAt(index) == '-') {
        index++;
        if (index >= text.length()) {
          addIssue(SYNTAX, index, parentPointer);
          aborted = true;
          return;
        }
      }
      char current = text.charAt(index);
      if (current == '0') {
        index++;
        if (index < text.length() && isDigit(text.charAt(index))) {
          addIssue(SYNTAX, index, parentPointer);
          aborted = true;
        }
      } else if (current >= '1' && current <= '9') {
        while (index < text.length() && isDigit(text.charAt(index))) {
          index++;
        }
      } else {
        addIssue(SYNTAX, index, parentPointer);
        aborted = true;
        return;
      }
      if (aborted) {
        return;
      }
      if (index < text.length() && text.charAt(index) == '.') {
        index++;
        if (index >= text.length() || !isDigit(text.charAt(index))) {
          addIssue(SYNTAX, index, parentPointer);
          aborted = true;
          return;
        }
        while (index < text.length() && isDigit(text.charAt(index))) {
          index++;
        }
      }
      if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
        index++;
        if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
          index++;
        }
        if (index >= text.length() || !isDigit(text.charAt(index))) {
          addIssue(SYNTAX, index, parentPointer);
          aborted = true;
          return;
        }
        while (index < text.length() && isDigit(text.charAt(index))) {
          index++;
        }
      }
    }

    private void countNode() throws ValidationLimitException {
      nodes++;
      if (nodes > limits.maxNodes()) {
        throw new ValidationLimitException("maxNodes", nodes, limits.maxNodes());
      }
    }

    private void skipWhitespace() {
      while (index < text.length()) {
        char current = text.charAt(index);
        if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
          return;
        }
        index++;
      }
    }

    private void addIssue(String code, int charIndex, String pointer) {
      int[] position = input.position(charIndex);
      issues.add(
          new ValidationIssue(
              Severity.ERROR,
              code,
              pointer,
              position[0],
              position[1],
              input.byteOffset(charIndex),
              Collections.<String>emptyList()));
    }

    private static boolean isDigit(char value) {
      return value >= '0' && value <= '9';
    }

    private static int hexValue(char value) {
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
  }
}
