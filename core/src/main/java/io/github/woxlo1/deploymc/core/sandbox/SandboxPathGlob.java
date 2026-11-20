package io.github.woxlo1.deploymc.core.sandbox;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compiles sandbox-relative path globs. {@code *} stays inside a segment; {@code **} crosses them.
 */
final class SandboxPathGlob {

  private final Pattern pattern;

  private SandboxPathGlob(Pattern pattern) {
    this.pattern = pattern;
  }

  static SandboxPathGlob compile(String glob) {
    if (glob == null || glob.isEmpty() || glob.charAt(0) == '/') {
      throw new IllegalArgumentException("Sandbox path glob must be relative and non-empty");
    }
    String normalized = glob.replace('\\', '/').toLowerCase(Locale.ROOT);
    StringBuilder expression = new StringBuilder("^");
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c == '*' && i + 1 < normalized.length() && normalized.charAt(i + 1) == '*') {
        if (i + 2 < normalized.length() && normalized.charAt(i + 2) == '/') {
          expression.append("(?:.*/)?");
          i += 2;
        } else {
          expression.append(".*");
          i += 1;
        }
      } else if (c == '*') {
        expression.append("[^/]*");
      } else if (c == '?') {
        expression.append("[^/]");
      } else {
        expression.append(Pattern.quote(String.valueOf(c)));
      }
    }
    expression.append('$');
    return new SandboxPathGlob(Pattern.compile(expression.toString()));
  }

  boolean matches(String normalizedRelativePath) {
    return pattern.matcher(normalizedRelativePath.toLowerCase(Locale.ROOT)).matches();
  }
}
