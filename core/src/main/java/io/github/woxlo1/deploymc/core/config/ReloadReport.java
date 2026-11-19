package io.github.woxlo1.deploymc.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of a successful reload, including keys that cannot take effect until restart. */
public final class ReloadReport {

  private final List<String> changedPaths;
  private final List<String> restartRequiredPaths;
  private final List<ConfigIssue> warnings;

  ReloadReport(
      List<String> changedPaths, List<String> restartRequiredPaths, List<ConfigIssue> warnings) {
    this.changedPaths = immutable(changedPaths);
    this.restartRequiredPaths = immutable(restartRequiredPaths);
    this.warnings = immutable(warnings);
  }

  public List<String> changedPaths() {
    return changedPaths;
  }

  public List<String> restartRequiredPaths() {
    return restartRequiredPaths;
  }

  public List<ConfigIssue> warnings() {
    return warnings;
  }

  public boolean requiresRestart() {
    return !restartRequiredPaths.isEmpty();
  }

  private static <T> List<T> immutable(List<T> source) {
    return Collections.unmodifiableList(new ArrayList<T>(source));
  }
}
