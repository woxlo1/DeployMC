package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One immutable validation finding with a stable code and a localizable detail. */
public final class ValidationIssue {

  private static final int MAX_PARAMETERS = 8;
  private static final int MAX_PARAMETER_LENGTH = 256;

  /** Severity first (ERROR before WARNING), then code, path, position, and safe parameters. */
  public static final Comparator<ValidationIssue> ORDER =
      new Comparator<ValidationIssue>() {
        @Override
        public int compare(ValidationIssue left, ValidationIssue right) {
          int result = left.severity.compareTo(right.severity);
          if (result != 0) {
            return result;
          }
          result = left.code.compareTo(right.code);
          if (result != 0) {
            return result;
          }
          result = left.pointer.compareTo(right.pointer);
          if (result != 0) {
            return result;
          }
          result = Integer.compare(left.line, right.line);
          if (result != 0) {
            return result;
          }
          result = Integer.compare(left.column, right.column);
          if (result != 0) {
            return result;
          }
          result = Long.compare(left.offset, right.offset);
          if (result != 0) {
            return result;
          }
          return join(left.parameters).compareTo(join(right.parameters));
        }
      };

  private final Severity severity;
  private final String code;
  private final String pointer;
  private final int line;
  private final int column;
  private final long offset;
  private final List<String> parameters;

  public ValidationIssue(
      Severity severity,
      String code,
      String pointer,
      int line,
      int column,
      long offset,
      List<String> parameters) {
    this.severity = Objects.requireNonNull(severity, "severity");
    this.code = Objects.requireNonNull(code, "code");
    this.pointer = Objects.requireNonNull(pointer, "pointer");
    this.line = Math.max(0, line);
    this.column = Math.max(0, column);
    this.offset = Math.max(-1L, offset);
    this.parameters = safeParameters(parameters);
  }

  public Severity severity() {
    return severity;
  }

  public String code() {
    return code;
  }

  public String pointer() {
    return pointer;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public long offset() {
    return offset;
  }

  public List<String> parameters() {
    return parameters;
  }

  public String detail(Locale locale) {
    return ValidationMessages.detail(locale, this);
  }

  @Override
  public String toString() {
    return severity + " " + code + " " + pointer + " " + line + ":" + column;
  }

  private static List<String> safeParameters(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> safe = new ArrayList<String>(Math.min(values.size(), MAX_PARAMETERS));
    for (String value : values) {
      if (safe.size() == MAX_PARAMETERS) {
        break;
      }
      String sanitized = sanitize(value == null ? "null" : value);
      safe.add(sanitized);
    }
    return Collections.unmodifiableList(safe);
  }

  private static String sanitize(String value) {
    int length = Math.min(value.length(), MAX_PARAMETER_LENGTH);
    StringBuilder result = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char current = value.charAt(i);
      result.append(current < 0x20 || current == 0x7f ? '?' : current);
    }
    return result.toString();
  }

  private static String join(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      result.append(value).append('\u001f');
    }
    return result.toString();
  }
}
