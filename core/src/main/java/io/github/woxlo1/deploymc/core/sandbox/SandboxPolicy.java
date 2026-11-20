package io.github.woxlo1.deploymc.core.sandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Host-controlled sandbox settings. Request data can never change a policy instance. */
public final class SandboxPolicy {

  /** Maximum request-path length enforced before any file-system access. */
  public static final int DEFAULT_MAX_PATH_LENGTH = 4096;

  /** Paths matching these patterns are never writable through the API, even by an admin. */
  public static final List<String> DEFAULT_PROTECTED_PATTERNS =
      Collections.unmodifiableList(Arrays.asList(".deploymc/**", "DeployMC/**"));

  private final int maxPathLength;
  private final List<SandboxPathGlob> protectedPaths;
  private final List<SandboxPathGlob> denyPatterns;
  private final boolean followSymlinks;

  public SandboxPolicy() {
    this(
        DEFAULT_MAX_PATH_LENGTH,
        DEFAULT_PROTECTED_PATTERNS,
        Collections.<String>emptyList(),
        false);
  }

  public SandboxPolicy(
      int maxPathLength,
      Collection<String> protectedPatterns,
      Collection<String> denyPatterns,
      boolean followSymlinks) {
    if (maxPathLength < 1 || maxPathLength > 16384) {
      throw new IllegalArgumentException("Sandbox max path length is out of range");
    }
    this.maxPathLength = maxPathLength;
    this.protectedPaths = compile(protectedPatterns, "protected");
    this.denyPatterns = compile(denyPatterns, "deny");
    this.followSymlinks = followSymlinks;
  }

  public int maxPathLength() {
    return maxPathLength;
  }

  public boolean followSymlinks() {
    return followSymlinks;
  }

  boolean matchesProtected(String normalizedRelativePath) {
    return matches(protectedPaths, normalizedRelativePath);
  }

  boolean matchesDenied(String normalizedRelativePath) {
    return matches(denyPatterns, normalizedRelativePath);
  }

  private static List<SandboxPathGlob> compile(Collection<String> patterns, String kind) {
    if (patterns == null) {
      throw new IllegalArgumentException("Sandbox " + kind + " patterns must not be null");
    }
    List<SandboxPathGlob> compiled = new ArrayList<SandboxPathGlob>();
    for (String pattern : patterns) {
      if (pattern == null || pattern.isEmpty()) {
        throw new IllegalArgumentException("Sandbox " + kind + " pattern must not be empty");
      }
      compiled.add(SandboxPathGlob.compile(pattern));
    }
    return Collections.unmodifiableList(compiled);
  }

  private static boolean matches(List<SandboxPathGlob> globs, String path) {
    for (SandboxPathGlob glob : globs) {
      if (glob.matches(path)) {
        return true;
      }
    }
    return false;
  }
}
