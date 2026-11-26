package io.github.woxlo1.deploymc.core.rbac;

/** Stable, detail-free reasons produced by the WP-3.4 evaluator. */
public enum DecisionReason {
  NO_ROLE("rbac.denied"),
  NOT_GRANTED("rbac.denied"),
  TOKEN_SCOPE_EXCEEDED("rbac.token-scope-exceeded"),
  PATH_DENIED("rbac.path-denied"),
  PATH_SCOPE("rbac.path-denied");

  private final String problemCode;

  DecisionReason(String problemCode) {
    this.problemCode = problemCode;
  }

  /** Stable Problem Details code; never includes identity, path, or role details. */
  public String problemCode() {
    return problemCode;
  }
}
