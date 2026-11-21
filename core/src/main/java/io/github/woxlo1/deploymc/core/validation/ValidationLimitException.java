package io.github.woxlo1.deploymc.core.validation;

/** A host-controlled input or work limit was exceeded. Maps to validation.limit-exceeded. */
public final class ValidationLimitException extends ValidationException {

  private static final long serialVersionUID = 1L;

  private final String limitName;
  private final long observed;
  private final long maximum;

  public ValidationLimitException(String limitName, long observed, long maximum) {
    super(
        "validation.limit-exceeded",
        "code.validation.limit-exceeded",
        limitName,
        Long.valueOf(observed),
        Long.valueOf(maximum));
    this.limitName = limitName;
    this.observed = observed;
    this.maximum = maximum;
  }

  public String limitName() {
    return limitName;
  }

  public long observed() {
    return observed;
  }

  public long maximum() {
    return maximum;
  }
}
