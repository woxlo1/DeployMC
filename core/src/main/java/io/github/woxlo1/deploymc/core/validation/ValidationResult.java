package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable validation outcome. Warnings are reported but never make a result invalid. */
public final class ValidationResult {

  private final String path;
  private final boolean matched;
  private final String format;
  private final boolean valid;
  private final List<ValidationIssue> issues;

  private ValidationResult(
      String path, boolean matched, String format, boolean valid, List<ValidationIssue> issues) {
    this.path = path;
    this.matched = matched;
    this.format = format;
    this.valid = valid;
    this.issues = issues;
  }

  /** Explicit non-applicability. This must never be presented as a successful validation. */
  public static ValidationResult unmatched(String path) {
    return new ValidationResult(path, false, null, true, Collections.<ValidationIssue>emptyList());
  }

  /** Sorts and truncates findings deterministically at the host-controlled issue budget. */
  public static ValidationResult matched(
      String path, String format, List<ValidationIssue> issues, ValidationLimits limits) {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(limits, "limits");
    List<ValidationIssue> sorted = new ArrayList<ValidationIssue>();
    if (issues != null) {
      sorted.addAll(issues);
    }
    Collections.sort(sorted, ValidationIssue.ORDER);
    boolean valid = true;
    for (ValidationIssue issue : sorted) {
      if (issue.severity() == Severity.ERROR) {
        valid = false;
        break;
      }
    }
    if (sorted.size() > limits.maxIssues()) {
      sorted = new ArrayList<ValidationIssue>(sorted.subList(0, limits.maxIssues()));
    }
    return new ValidationResult(path, true, format, valid, Collections.unmodifiableList(sorted));
  }

  public String path() {
    return path;
  }

  public boolean matched() {
    return matched;
  }

  public String format() {
    return format;
  }

  public boolean valid() {
    return valid;
  }

  public List<ValidationIssue> issues() {
    return issues;
  }
}
