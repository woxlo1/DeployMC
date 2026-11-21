package io.github.woxlo1.deploymc.core.validation;

/**
 * A host-controlled input or work limit was exceeded. Maps to {@code validation.limit-exceeded}.
 *
 * <p>A format-specific structural budget also carries the issue code that the HTTP adapter places
 * in {@code errors[]}. Non-structural limits leave that code null.
 */
public final class ValidationLimitException extends ValidationException {

  private static final long serialVersionUID = 1L;

  private final String issueCode;
  private final String limitName;
  private final long observed;
  private final long maximum;

  public ValidationLimitException(String limitName, long observed, long maximum) {
    this(null, limitName, observed, maximum);
  }

  public ValidationLimitException(String issueCode, String limitName, long observed, long maximum) {
    super(
        "validation.limit-exceeded",
        "code.validation.limit-exceeded",
        limitName,
        Long.valueOf(observed),
        Long.valueOf(maximum));
    this.issueCode = issueCode;
    this.limitName = limitName;
    this.observed = observed;
    this.maximum = maximum;
  }

  /** The format-specific issue code for a structural budget, or null for a generic input limit. */
  public String issueCode() {
    return issueCode;
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
