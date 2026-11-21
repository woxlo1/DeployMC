package io.github.woxlo1.deploymc.core.validation;

/** RFC 6901 pointer token helpers. */
final class Pointers {

  private Pointers() {}

  static String escape(String token) {
    return token.replace("~", "~0").replace("/", "~1");
  }

  static String append(String pointer, String token) {
    return pointer + "/" + escape(token);
  }

  static String unescape(String token) {
    if (token == null) {
      throw new IllegalArgumentException("pointer token is null");
    }
    StringBuilder result = new StringBuilder(token.length());
    for (int i = 0; i < token.length(); i++) {
      char current = token.charAt(i);
      if (current != '~') {
        result.append(current);
        continue;
      }
      if (i + 1 >= token.length()) {
        throw new IllegalArgumentException("dangling pointer escape");
      }
      char next = token.charAt(++i);
      if (next == '0') {
        result.append('~');
      } else if (next == '1') {
        result.append('/');
      } else {
        throw new IllegalArgumentException("invalid pointer escape");
      }
    }
    return result.toString();
  }
}
