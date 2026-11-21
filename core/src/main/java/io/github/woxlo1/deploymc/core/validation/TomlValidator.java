package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, deterministic validator for the supported TOML profile (not full TOML 1.0). */
public final class TomlValidator implements ContentValidator {

  private static final String CODE_SYNTAX = "validation.toml-syntax";
  private static final String CODE_DUPLICATE = "validation.toml-duplicate-key";
  private static final String CODE_UNSUPPORTED = "validation.toml-unsupported";

  private static final int IMPLICIT = 0;
  private static final int TABLE = 1;
  private static final int ARRAY_TABLE = 2;
  private static final int INLINE = 3;

  @Override
  public String id() {
    return "toml";
  }

  @Override
  public ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits)
      throws ValidationException {
    String path = PathRules.normalize(relativePath);
    TextInput input = TextInput.decode(content, limits);
    if (input.hasEncodingIssue()) {
      return ValidationResult.matched(
          path, "toml", Collections.singletonList(input.encodingIssue()), limits);
    }
    return new Parser(input, limits).parse(path);
  }

  /** One table scope used for duplicate detection and pointer construction. */
  private static final class Scope {
    final Map<String, Scope> children = new LinkedHashMap<String, Scope>();
    final Set<String> scalars = new HashSet<String>();
    final int kind;
    Scope element;

    Scope(int kind) {
      this.kind = kind;
    }
  }

  /** Result of parsing one value; an inline table carries its own scope. */
  private static final class Value {
    final String type;
    final Scope inline;

    private Value(String type, Scope inline) {
      this.type = type;
      this.inline = inline;
    }

    static Value of(String type) {
      return new Value(type, null);
    }

    static Value inline(Scope scope) {
      return new Value("inline-table", scope);
    }
  }

  /** Internal stop signal; always converted to a ValidationIssue inside parse. */
  private static final class ParseFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    final transient ValidationIssue issue;

    ParseFailure(ValidationIssue issue) {
      this.issue = issue;
    }
  }

  private static final class Parser {
    private final TextInput input;
    private final String text;
    private final ValidationLimits limits;
    private final Scope root = new Scope(TABLE);
    private int pos;
    private int nodes;
    private Scope current;
    private String currentPointer = "";
    private String reportPointer = "";

    Parser(TextInput input, ValidationLimits limits) {
      this.input = input;
      this.text = input.text();
      this.limits = limits;
      this.current = root;
    }

    ValidationResult parse(String path) throws ValidationLimitException {
      try {
        parseDocument();
        return ValidationResult.matched(
            path, "toml", Collections.<ValidationIssue>emptyList(), limits);
      } catch (ParseFailure failure) {
        return ValidationResult.matched(
            path, "toml", Collections.singletonList(failure.issue), limits);
      }
    }

    private void parseDocument() throws ValidationLimitException {
      while (pos < text.length()) {
        skipHorizontal();
        if (pos >= text.length()) {
          return;
        }
        char c = text.charAt(pos);
        if (c == '#') {
          skipComment();
        } else if (isNewline(c)) {
          consumeNewline();
        } else if (c == '[') {
          parseHeader();
        } else {
          parseKeyValue();
        }
      }
    }

    private void parseHeader() throws ValidationLimitException {
      int start = pos;
      pos++; // opening '['
      currentPointer = ""; // header keys are absolute, not relative to the previous table
      skipHorizontal();
      boolean arrayTable = false;
      if (pos < text.length() && text.charAt(pos) == '[') {
        arrayTable = true;
        pos++;
        skipHorizontal();
      }
      List<String> keys = parseKeyPath(']');
      skipHorizontal();
      if (!consume(']')) {
        throw fail(CODE_SYNTAX, "unclosed-header", start);
      }
      if (arrayTable && !consume(']')) {
        throw fail(CODE_SYNTAX, "unclosed-header", start);
      }
      String pointer = buildPointer(currentPointer, keys);
      reportPointer = pointer;
      current = declareTable(keys, arrayTable, pointer, start);
      currentPointer = pointer;
    }

    private void parseKeyValue() throws ValidationLimitException {
      int start = pos;
      List<String> keys = parseKeyPath('=');
      skipHorizontal();
      if (!consume('=')) {
        throw fail(CODE_SYNTAX, "expected-equals", pos);
      }
      String pointer = buildPointer(currentPointer, keys);
      reportPointer = pointer;
      skipHorizontal();
      if (pos >= text.length() || isNewline(text.charAt(pos))) {
        throw fail(CODE_SYNTAX, "expected-value", pos);
      }
      Value value = parseValue(pointer, 1);
      defineKeys(current, keys, value, currentPointer, start);
      skipHorizontal();
      if (pos < text.length() && text.charAt(pos) == '#') {
        skipComment();
      }
      if (pos >= text.length()) {
        return;
      }
      if (isNewline(text.charAt(pos))) {
        consumeNewline();
        return;
      }
      throw fail(CODE_SYNTAX, "junk-after-value", pos);
    }

    private List<String> parseKeyPath(char terminator) throws ValidationLimitException {
      List<String> keys = new ArrayList<String>();
      while (true) {
        skipHorizontal();
        if (pos >= text.length()) {
          throw fail(CODE_SYNTAX, "unterminated-key", pos);
        }
        char c = text.charAt(pos);
        if (c == terminator || c == '=' || isNewline(c)) {
          throw fail(CODE_SYNTAX, "empty-key", pos);
        }
        String segment = parseKeySegment();
        keys.add(segment);
        reportPointer = buildPointer(currentPointer, keys);
        skipHorizontal();
        if (pos < text.length() && text.charAt(pos) == '.') {
          pos++;
          continue;
        }
        return keys;
      }
    }

    private String parseKeySegment() throws ValidationLimitException {
      int start = pos;
      char c = text.charAt(start);
      if (c == '"' || c == '\'') {
        if (isTripleQuote(c)) {
          throw fail(CODE_UNSUPPORTED, "multiline-string", start);
        }
        String segment = parseQuoted(c, start);
        checkKeyLength(segment.length());
        return segment;
      }
      while (pos < text.length() && isBareKeyChar(text.charAt(pos))) {
        pos++;
      }
      if (pos == start) {
        throw fail(CODE_SYNTAX, "invalid-key", start);
      }
      String segment = text.substring(start, pos);
      checkKeyLength(segment.length());
      return segment;
    }

    private String parseQuoted(char quote, int start) {
      pos++; // opening quote
      StringBuilder value = new StringBuilder();
      while (pos < text.length()) {
        char c = text.charAt(pos);
        if (c == quote) {
          pos++;
          return value.toString();
        }
        if (isNewline(c)) {
          throw fail(CODE_SYNTAX, "unterminated-string", start);
        }
        if (c < 0x20 && c != '\t') {
          throw fail(CODE_SYNTAX, "invalid-character", pos);
        }
        if (quote == '"' && c == '\\') {
          appendEscape(value, pos);
          continue;
        }
        value.append(c);
        pos++;
      }
      throw fail(CODE_SYNTAX, "unterminated-string", start);
    }

    private void appendEscape(StringBuilder out, int escapeStart) {
      pos++; // backslash
      if (pos >= text.length()) {
        throw fail(CODE_SYNTAX, "unterminated-escape", escapeStart);
      }
      char escape = text.charAt(pos++);
      switch (escape) {
        case 'b':
          out.append('\b');
          return;
        case 't':
          out.append('\t');
          return;
        case 'n':
          out.append('\n');
          return;
        case 'f':
          out.append('\f');
          return;
        case 'r':
          out.append('\r');
          return;
        case '"':
          out.append('"');
          return;
        case '\\':
          out.append('\\');
          return;
        case 'u':
          long basic = readHex(4, escapeStart);
          if (basic >= 0xd800 && basic <= 0xdfff) {
            throw fail(CODE_SYNTAX, "bad-escape", escapeStart);
          }
          out.append((char) basic);
          return;
        case 'U':
          long scalar = readHex(8, escapeStart);
          if (scalar > 0x10ffff || (scalar >= 0xd800 && scalar <= 0xdfff)) {
            throw fail(CODE_SYNTAX, "bad-escape", escapeStart);
          }
          out.appendCodePoint((int) scalar);
          return;
        default:
          throw fail(CODE_SYNTAX, "bad-escape", escapeStart);
      }
    }

    private long readHex(int digits, int escapeStart) {
      if (pos + digits > text.length()) {
        throw fail(CODE_SYNTAX, "bad-escape", escapeStart);
      }
      long value = 0;
      for (int i = 0; i < digits; i++) {
        int digit = digitValue(text.charAt(pos++), 16);
        if (digit < 0) {
          throw fail(CODE_SYNTAX, "bad-escape", escapeStart);
        }
        value = value * 16 + digit;
      }
      return value;
    }

    private Value parseValue(String pointer, int depth) throws ValidationLimitException {
      countNode();
      skipHorizontal();
      if (pos >= text.length()) {
        throw fail(CODE_SYNTAX, "expected-value", pos);
      }
      char c = text.charAt(pos);
      if (c == '"' || c == '\'') {
        if (isTripleQuote(c)) {
          throw fail(CODE_UNSUPPORTED, "multiline-string", pos);
        }
        parseQuoted(c, pos);
        return Value.of("string");
      }
      if (c == '[') {
        parseArray(pointer, depth);
        return Value.of("array");
      }
      if (c == '{') {
        return Value.inline(parseInlineTable(pointer, depth));
      }
      if (c == 't' || c == 'f') {
        parseBoolean();
        return Value.of("boolean");
      }
      if (isAsciiDigit(c) || c == '+' || c == '-') {
        return parseNumber();
      }
      if (c == 'i' || c == 'n') {
        throw fail(CODE_UNSUPPORTED, "non-finite-float", pos);
      }
      throw fail(CODE_SYNTAX, "invalid-token", pos);
    }

    private void parseArray(String pointer, int depth) throws ValidationLimitException {
      checkDepth(depth);
      int start = pos;
      pos++; // opening '['
      String elementType = null;
      int index = 0;
      while (true) {
        skipContainerWhitespace();
        if (pos >= text.length()) {
          throw fail(CODE_SYNTAX, "unclosed-array", start);
        }
        if (text.charAt(pos) == ']') {
          pos++;
          return;
        }
        int elementStart = pos;
        String elementPointer = Pointers.append(pointer, Integer.toString(index));
        reportPointer = elementPointer;
        Value element = parseValue(elementPointer, depth + 1);
        if (elementType == null) {
          elementType = element.type;
        } else if (!elementType.equals(element.type)) {
          throw fail(CODE_UNSUPPORTED, "heterogeneous-array", elementStart);
        }
        index++;
        skipContainerWhitespace();
        if (pos >= text.length()) {
          throw fail(CODE_SYNTAX, "unclosed-array", start);
        }
        char c = text.charAt(pos);
        if (c == ',') {
          pos++;
          continue;
        }
        if (c == ']') {
          pos++;
          return;
        }
        throw fail(CODE_SYNTAX, "bad-array", pos);
      }
    }

    private Scope parseInlineTable(String pointer, int depth) throws ValidationLimitException {
      checkDepth(depth);
      int start = pos;
      pos++; // opening '{'
      Scope scope = new Scope(INLINE);
      skipHorizontal();
      if (pos < text.length() && text.charAt(pos) == '}') {
        pos++;
        return scope;
      }
      while (true) {
        int keyStart = pos;
        List<String> keys = parseKeyPath('=');
        String childPointer = buildPointer(pointer, keys);
        reportPointer = childPointer;
        skipHorizontal();
        if (!consume('=')) {
          throw fail(CODE_SYNTAX, "expected-equals", pos);
        }
        skipHorizontal();
        if (pos >= text.length() || isNewline(text.charAt(pos))) {
          throw fail(CODE_SYNTAX, "expected-value", pos);
        }
        Value value = parseValue(childPointer, depth + 1);
        defineKeys(scope, keys, value, pointer, keyStart);
        skipHorizontal();
        if (pos >= text.length()) {
          throw fail(CODE_SYNTAX, "unclosed-inline-table", start);
        }
        char c = text.charAt(pos);
        if (c == ',') {
          pos++;
          skipHorizontal();
          if (pos < text.length() && text.charAt(pos) == '}') {
            throw fail(CODE_SYNTAX, "trailing-comma", pos);
          }
          continue;
        }
        if (c == '}') {
          pos++;
          return scope;
        }
        if (isNewline(c)) {
          throw fail(CODE_SYNTAX, "unclosed-inline-table", start);
        }
        throw fail(CODE_SYNTAX, "bad-inline-table", pos);
      }
    }

    private void parseBoolean() {
      int start = pos;
      while (pos < text.length() && !isTokenDelimiter(text.charAt(pos))) {
        pos++;
      }
      String token = text.substring(start, pos);
      if (!"true".equals(token) && !"false".equals(token)) {
        throw fail(CODE_SYNTAX, "invalid-token", start);
      }
    }

    private Value parseNumber() {
      int start = pos;
      while (pos < text.length() && !isTokenDelimiter(text.charAt(pos))) {
        pos++;
      }
      String token = text.substring(start, pos);
      if (token.isEmpty()) {
        throw fail(CODE_SYNTAX, "invalid-number", start);
      }
      int offset = (token.charAt(0) == '+' || token.charAt(0) == '-') ? 1 : 0;
      if (offset == token.length()) {
        throw fail(CODE_SYNTAX, "invalid-number", start);
      }
      char first = token.charAt(offset);
      if (first == 'i' || first == 'n') {
        String word = token.substring(offset);
        if ("inf".equals(word) || "nan".equals(word)) {
          throw fail(CODE_UNSUPPORTED, "non-finite-float", start);
        }
        throw fail(CODE_SYNTAX, "invalid-number", start);
      }
      if (isDateTimeToken(token)) {
        return Value.of("datetime");
      }
      if (first == '0' && offset + 1 < token.length()) {
        char prefix = token.charAt(offset + 1);
        int radix;
        if (prefix == 'x' || prefix == 'X') {
          radix = 16;
        } else if (prefix == 'o' || prefix == 'O') {
          radix = 8;
        } else if (prefix == 'b' || prefix == 'B') {
          radix = 2;
        } else {
          radix = 0;
        }
        if (radix != 0) {
          int end = scanDigits(token, offset + 2, radix, start);
          if (end != token.length()) {
            throw fail(CODE_SYNTAX, "invalid-number", start);
          }
          return Value.of("integer");
        }
      }
      int index = scanDigits(token, offset, 10, start);
      boolean floating = false;
      if (index < token.length() && token.charAt(index) == '.') {
        floating = true;
        index = scanDigits(token, index + 1, 10, start);
      }
      if (index < token.length() && (token.charAt(index) == 'e' || token.charAt(index) == 'E')) {
        floating = true;
        index++;
        if (index < token.length() && (token.charAt(index) == '+' || token.charAt(index) == '-')) {
          index++;
        }
        index = scanDigits(token, index, 10, start);
      }
      if (index != token.length()) {
        throw fail(CODE_SYNTAX, "invalid-number", start);
      }
      return Value.of(floating ? "float" : "integer");
    }

    private int scanDigits(String token, int start, int radix, int tokenStart) {
      if (start >= token.length()) {
        throw fail(CODE_SYNTAX, "invalid-number", tokenStart);
      }
      boolean previousUnderscore = false;
      boolean sawDigit = false;
      int index = start;
      while (index < token.length()) {
        char c = token.charAt(index);
        if (c == '_') {
          if (!sawDigit
              || previousUnderscore
              || index + 1 >= token.length()
              || digitValue(token.charAt(index + 1), radix) < 0) {
            throw fail(CODE_SYNTAX, "invalid-number", tokenStart);
          }
          previousUnderscore = true;
        } else {
          if (digitValue(c, radix) < 0) {
            break;
          }
          sawDigit = true;
          previousUnderscore = false;
        }
        index++;
      }
      if (!sawDigit || previousUnderscore) {
        throw fail(CODE_SYNTAX, "invalid-number", tokenStart);
      }
      return index;
    }

    private Scope declareTable(List<String> keys, boolean arrayTable, String pointer, int start) {
      Scope scope = root;
      String prefix = "";
      for (int i = 0; i < keys.size() - 1; i++) {
        String segment = keys.get(i);
        if (scope.scalars.contains(segment)) {
          throw duplicate(Pointers.append(prefix, segment), start, "key");
        }
        Scope child = scope.children.get(segment);
        if (child == null) {
          child = new Scope(IMPLICIT);
          scope.children.put(segment, child);
        }
        if (child.kind == ARRAY_TABLE) {
          if (child.element == null) {
            child.element = new Scope(TABLE);
          }
          child = child.element;
        }
        prefix = Pointers.append(prefix, segment);
        scope = child;
      }
      String last = keys.get(keys.size() - 1);
      if (scope.scalars.contains(last) || scope.children.containsKey(last)) {
        throw duplicate(pointer, start, arrayTable ? "array-of-tables" : "table");
      }
      Scope child = new Scope(arrayTable ? ARRAY_TABLE : TABLE);
      scope.children.put(last, child);
      if (arrayTable) {
        child.element = new Scope(TABLE);
        return child.element;
      }
      return child;
    }

    private void defineKeys(
        Scope scope, List<String> keys, Value value, String basePointer, int start) {
      String prefix = basePointer;
      for (int i = 0; i < keys.size() - 1; i++) {
        String segment = keys.get(i);
        if (scope.scalars.contains(segment)) {
          throw duplicate(Pointers.append(prefix, segment), start, "key");
        }
        Scope child = scope.children.get(segment);
        if (child == null) {
          child = new Scope(IMPLICIT);
          scope.children.put(segment, child);
        }
        if (child.kind == ARRAY_TABLE) {
          if (child.element == null) {
            child.element = new Scope(TABLE);
          }
          child = child.element;
        }
        prefix = Pointers.append(prefix, segment);
        scope = child;
      }
      String last = keys.get(keys.size() - 1);
      if (scope.scalars.contains(last) || scope.children.containsKey(last)) {
        throw duplicate(Pointers.append(prefix, last), start, "key");
      }
      if (value.inline != null) {
        scope.children.put(last, value.inline);
      } else {
        scope.scalars.add(last);
      }
    }

    private void skipHorizontal() {
      while (pos < text.length() && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t')) {
        pos++;
      }
    }

    private void skipContainerWhitespace() {
      while (pos < text.length()) {
        char c = text.charAt(pos);
        if (c == ' ' || c == '\t') {
          pos++;
        } else if (c == '#') {
          skipComment();
        } else if (isNewline(c)) {
          consumeNewline();
        } else {
          return;
        }
      }
    }

    private void skipComment() {
      while (pos < text.length() && !isNewline(text.charAt(pos))) {
        pos++;
      }
    }

    private void consumeNewline() {
      if (text.charAt(pos) == '\r') {
        if (pos + 1 >= text.length() || text.charAt(pos + 1) != '\n') {
          throw fail(CODE_SYNTAX, "bad-newline", pos);
        }
        pos += 2;
        return;
      }
      pos++;
    }

    private boolean consume(char expected) {
      if (pos < text.length() && text.charAt(pos) == expected) {
        pos++;
        return true;
      }
      return false;
    }

    private void countNode() throws ValidationLimitException {
      nodes++;
      if (nodes > limits.maxNodes()) {
        throw new ValidationLimitException("maxNodes", nodes, limits.maxNodes());
      }
    }

    private void checkDepth(int depth) throws ValidationLimitException {
      if (depth > limits.maxDepth()) {
        throw new ValidationLimitException("maxDepth", depth, limits.maxDepth());
      }
    }

    private void checkKeyLength(int length) throws ValidationLimitException {
      if (length > limits.maxKeyLength()) {
        throw new ValidationLimitException("maxKeyLength", length, limits.maxKeyLength());
      }
    }

    private boolean isTripleQuote(char quote) {
      return pos + 2 < text.length()
          && text.charAt(pos + 1) == quote
          && text.charAt(pos + 2) == quote;
    }

    private ParseFailure duplicate(String pointer, int start, String token) {
      return issue(CODE_DUPLICATE, token, pointer, start);
    }

    private ParseFailure fail(String code, String token, int index) {
      return issue(code, token, reportPointer, index);
    }

    private ParseFailure issue(String code, String token, String pointer, int index) {
      int[] lineColumn = input.position(index);
      ValidationIssue issue =
          new ValidationIssue(
              Severity.ERROR,
              code,
              pointer,
              lineColumn[0],
              lineColumn[1],
              input.byteOffset(index),
              Collections.singletonList(token));
      return new ParseFailure(issue);
    }

    private static String buildPointer(String base, List<String> keys) {
      String pointer = base;
      for (String key : keys) {
        pointer = Pointers.append(pointer, key);
      }
      return pointer;
    }

    private static boolean isBareKeyChar(char c) {
      return (c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '_'
          || c == '-';
    }

    private static boolean isAsciiDigit(char c) {
      return c >= '0' && c <= '9';
    }

    private static boolean isNewline(char c) {
      return c == '\n' || c == '\r';
    }

    private static boolean isTokenDelimiter(char c) {
      return c == ' ' || c == '\t' || c == ',' || c == ']' || c == '}' || c == '#' || isNewline(c);
    }

    private static int digitValue(char c, int radix) {
      if (c >= '0' && c <= '9') {
        return c - '0';
      }
      if (radix == 16) {
        if (c >= 'a' && c <= 'f') {
          return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
          return c - 'A' + 10;
        }
      }
      return -1;
    }

    private static boolean isDigits(String token, int start, int end) {
      if (start < 0 || end > token.length() || start >= end) {
        return false;
      }
      for (int i = start; i < end; i++) {
        if (!isAsciiDigit(token.charAt(i))) {
          return false;
        }
      }
      return true;
    }

    private static boolean isTime(String token, int start) {
      return start + 8 <= token.length()
          && isDigits(token, start, start + 2)
          && token.charAt(start + 2) == ':'
          && isDigits(token, start + 3, start + 5)
          && token.charAt(start + 5) == ':'
          && isDigits(token, start + 6, start + 8);
    }

    private static boolean isDateTimeToken(String token) {
      int index = 0;
      if (token.length() >= 10
          && isDigits(token, 0, 4)
          && token.charAt(4) == '-'
          && isDigits(token, 5, 7)
          && token.charAt(7) == '-'
          && isDigits(token, 8, 10)) {
        index = 10;
        if (token.length() == 10) {
          return true;
        }
        char separator = token.charAt(index);
        if (separator != 'T' && separator != 't') {
          return false;
        }
        index++;
      }
      if (!isTime(token, index)) {
        return false;
      }
      index += 8;
      if (index < token.length() && token.charAt(index) == '.') {
        index++;
        int fractionStart = index;
        while (index < token.length() && isAsciiDigit(token.charAt(index))) {
          index++;
        }
        if (index == fractionStart) {
          return false;
        }
      }
      if (index < token.length()) {
        char zone = token.charAt(index);
        if (zone == 'Z' || zone == 'z') {
          index++;
        } else if (zone == '+' || zone == '-') {
          if (index + 6 > token.length()
              || !isDigits(token, index + 1, index + 3)
              || token.charAt(index + 3) != ':'
              || !isDigits(token, index + 4, index + 6)) {
            return false;
          }
          index += 6;
        } else {
          return false;
        }
      }
      return index == token.length();
    }
  }
}
