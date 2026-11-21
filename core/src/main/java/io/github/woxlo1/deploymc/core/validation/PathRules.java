package io.github.woxlo1.deploymc.core.validation;

/** Validates a sandbox-relative path used for rule matching. It never constructs a file path. */
final class PathRules {

  private PathRules() {}

  static String normalize(String relativePath) {
    if (relativePath == null || relativePath.isEmpty()) {
      throw new IllegalArgumentException("relativePath must not be empty");
    }
    if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
      throw new IllegalArgumentException("relativePath must be relative");
    }
    if (relativePath.indexOf('\\') >= 0 || relativePath.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("relativePath contains an invalid character");
    }
    String[] segments = relativePath.split("/", -1);
    for (String segment : segments) {
      if (segment.isEmpty()
          || ".".equals(segment)
          || "..".equals(segment)
          || segment.indexOf(':') >= 0) {
        throw new IllegalArgumentException("relativePath contains an invalid segment");
      }
    }
    return relativePath;
  }
}
