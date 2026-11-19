package io.github.woxlo1.deploymc.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All findings from one configuration validation pass. */
public final class ConfigValidationResult {

  private final List<ConfigIssue> issues;

  ConfigValidationResult(List<ConfigIssue> issues) {
    this.issues = Collections.unmodifiableList(new ArrayList<ConfigIssue>(issues));
  }

  public List<ConfigIssue> issues() {
    return issues;
  }

  public List<ConfigIssue> errors() {
    List<ConfigIssue> result = new ArrayList<ConfigIssue>();
    for (ConfigIssue issue : issues) {
      if (issue.isError()) {
        result.add(issue);
      }
    }
    return Collections.unmodifiableList(result);
  }

  public List<ConfigIssue> warnings() {
    List<ConfigIssue> result = new ArrayList<ConfigIssue>();
    for (ConfigIssue issue : issues) {
      if (!issue.isError()) {
        result.add(issue);
      }
    }
    return Collections.unmodifiableList(result);
  }

  public boolean isValid() {
    return errors().isEmpty();
  }
}
