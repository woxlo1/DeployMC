package io.github.woxlo1.deploymc.core.rbac;

import java.util.Objects;

/** Immutable RBAC decision. Denials carry only a stable reason and bounded diagnostics. */
public final class AuthorizationDecision {

  private final boolean allowed;
  private final DecisionReason reason;
  private final DecisionExplanation explanation;

  private AuthorizationDecision(
      boolean allowed, DecisionReason reason, DecisionExplanation explanation) {
    this.allowed = allowed;
    this.reason = reason;
    this.explanation = Objects.requireNonNull(explanation, "explanation");
  }

  static AuthorizationDecision allow(DecisionExplanation explanation) {
    return new AuthorizationDecision(true, null, explanation);
  }

  static AuthorizationDecision deny(DecisionReason reason, DecisionExplanation explanation) {
    Objects.requireNonNull(reason, "reason");
    return new AuthorizationDecision(false, reason, explanation);
  }

  public boolean allowed() {
    return allowed;
  }

  /** Null only for an allow decision. */
  public DecisionReason reason() {
    return reason;
  }

  /** Stable Problem Details code; {@code null} for an allow decision. */
  public String errorCode() {
    return reason == null ? null : reason.problemCode();
  }

  public DecisionExplanation explanation() {
    return explanation;
  }
}
