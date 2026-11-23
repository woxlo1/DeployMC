package io.github.woxlo1.deploymc.core.doctor;

import java.util.regex.Pattern;

/** Defensive text shaping shared by catalog formatting and redaction. */
final class DoctorText {

  static final int MAX_TEXT_LENGTH = 512;
  static final String REDACTED = "<redacted>";

  private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+\\S+");
  private static final Pattern SENSITIVE_ASSIGNMENT =
      Pattern.compile(
          "(?i)\\b(authorization|cookie|token|secret|password|api[_-]?key)\\b\\s*[:=]\\s*[^\\s,;]+");
  private static final Pattern PATH_LIKE =
      Pattern.compile("(?:[A-Za-z]:[\\\\/]|\\\\\\\\[^\\s\\\\/]+[\\\\/]|[\\\\/])[^\\s\"'<>|(),;]*");

  private DoctorText() {}

  /** Makes an untrusted host-supplied catalog parameter safe to embed in a localized detail. */
  static String parameter(String value) {
    if (value == null) {
      return "";
    }
    return limit(redact(value));
  }

  /** Removes path-like and secret-like tokens, then enforces the schema text limit. */
  static String redact(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    StringBuilder safe = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      safe.append(c < 0x20 || c == 0x7f ? ' ' : c);
    }
    String scrubbed = BEARER.matcher(safe).replaceAll(REDACTED);
    scrubbed = SENSITIVE_ASSIGNMENT.matcher(scrubbed).replaceAll(REDACTED);
    return limit(PATH_LIKE.matcher(scrubbed).replaceAll(REDACTED));
  }

  /** Trims to the schema's maximum localized text length. */
  static String limit(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
  }

  /** Escapes a string for use as a JSON value, including the surrounding quotes. */
  static String quote(String value) {
    StringBuilder json = new StringBuilder(value.length() + 2);
    json.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          json.append("\\\"");
          break;
        case '\\':
          json.append("\\\\");
          break;
        case '\b':
          json.append("\\b");
          break;
        case '\f':
          json.append("\\f");
          break;
        case '\n':
          json.append("\\n");
          break;
        case '\r':
          json.append("\\r");
          break;
        case '\t':
          json.append("\\t");
          break;
        default:
          if (c < 0x20) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
      }
    }
    return json.append('"').toString();
  }
}
