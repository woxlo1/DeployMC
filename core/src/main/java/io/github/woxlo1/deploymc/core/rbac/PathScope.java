package io.github.woxlo1.deploymc.core.rbac;

import io.github.woxlo1.deploymc.core.sandbox.SandboxPathGlob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Immutable role path scope. An absent allow list means the whole sandbox; an explicit empty allow
 * list means no path is in scope. Deny rules always win.
 */
public final class PathScope {

  private final boolean allowConfigured;
  private final List<SandboxPathGlob> allow;
  private final List<SandboxPathGlob> deny;

  private PathScope(boolean allowConfigured, Collection<String> allow, Collection<String> deny) {
    this.allowConfigured = allowConfigured;
    this.allow = compile(allow);
    this.deny = compile(deny);
  }

  /** Role without a {@code paths.allow} key: whole sandbox minus protected paths. */
  public static PathScope wholeSandbox(Collection<String> deny) {
    return new PathScope(false, Collections.<String>emptyList(), deny);
  }

  /** Role with an explicit allow list, which may be empty. */
  public static PathScope restricted(Collection<String> allow, Collection<String> deny) {
    return new PathScope(true, allow, deny);
  }

  /** Deny-only default used by invalid or missing policy snapshots. */
  public static PathScope none() {
    return new PathScope(true, Collections.<String>emptyList(), Collections.<String>emptyList());
  }

  public boolean allowConfigured() {
    return allowConfigured;
  }

  public boolean matchesAllow(String normalizedRelativePath) {
    if (!allowConfigured) {
      return true;
    }
    for (SandboxPathGlob rule : allow) {
      if (rule.matches(normalizedRelativePath)) {
        return true;
      }
    }
    return false;
  }

  public boolean matchesDeny(String normalizedRelativePath) {
    for (SandboxPathGlob rule : deny) {
      if (rule.matches(normalizedRelativePath)) {
        return true;
      }
    }
    return false;
  }

  private static List<SandboxPathGlob> compile(Collection<String> globs) {
    if (globs == null || globs.isEmpty()) {
      return Collections.emptyList();
    }
    List<SandboxPathGlob> compiled = new ArrayList<SandboxPathGlob>(globs.size());
    for (String glob : globs) {
      compiled.add(SandboxPathGlob.compile(glob));
    }
    return Collections.unmodifiableList(compiled);
  }
}
