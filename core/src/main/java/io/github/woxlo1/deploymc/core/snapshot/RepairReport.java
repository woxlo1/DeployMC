package io.github.woxlo1.deploymc.core.snapshot;

import java.util.Collections;
import java.util.List;

/** Actions taken by repair and issues that intentionally remain for administrator review. */
public final class RepairReport {

  private final List<RepairAction> actions;
  private final List<VerifyIssue> unresolved;

  public RepairReport(List<RepairAction> actions, List<VerifyIssue> unresolved) {
    this.actions = Collections.unmodifiableList(actions);
    this.unresolved = Collections.unmodifiableList(unresolved);
  }

  public List<RepairAction> actions() {
    return actions;
  }

  public List<VerifyIssue> unresolved() {
    return unresolved;
  }
}
