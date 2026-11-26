package io.github.woxlo1.deploymc.core.rbac;

/** Bounded, redacted diagnostics for explain mode. No raw path, glob, or identity is stored. */
public final class DecisionExplanation {

  private final boolean pathRequested;
  private final boolean pathResolved;
  private final boolean pathWithinScope;
  private final boolean tokenPresent;
  private final boolean tokenContainsPermission;
  private final int rolesAssigned;
  private final int rolesGranting;
  private final int rolesDenyRuleMatches;
  private final int rolesAllowRuleMatches;

  DecisionExplanation(
      boolean pathRequested,
      boolean pathResolved,
      boolean pathWithinScope,
      boolean tokenPresent,
      boolean tokenContainsPermission,
      int rolesAssigned,
      int rolesGranting,
      int rolesDenyRuleMatches,
      int rolesAllowRuleMatches) {
    this.pathRequested = pathRequested;
    this.pathResolved = pathResolved;
    this.pathWithinScope = pathWithinScope;
    this.tokenPresent = tokenPresent;
    this.tokenContainsPermission = tokenContainsPermission;
    this.rolesAssigned = rolesAssigned;
    this.rolesGranting = rolesGranting;
    this.rolesDenyRuleMatches = rolesDenyRuleMatches;
    this.rolesAllowRuleMatches = rolesAllowRuleMatches;
  }

  public boolean pathRequested() {
    return pathRequested;
  }

  public boolean pathResolved() {
    return pathResolved;
  }

  public boolean pathWithinScope() {
    return pathWithinScope;
  }

  public boolean tokenPresent() {
    return tokenPresent;
  }

  public boolean tokenContainsPermission() {
    return tokenContainsPermission;
  }

  public int rolesAssigned() {
    return rolesAssigned;
  }

  public int rolesGranting() {
    return rolesGranting;
  }

  public int rolesDenyRuleMatches() {
    return rolesDenyRuleMatches;
  }

  public int rolesAllowRuleMatches() {
    return rolesAllowRuleMatches;
  }
}
