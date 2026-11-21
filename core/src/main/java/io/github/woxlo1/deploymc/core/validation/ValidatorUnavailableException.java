package io.github.woxlo1.deploymc.core.validation;

/** A registered validator failed unexpectedly. The caller must fail closed. */
public final class ValidatorUnavailableException extends ValidationException {

  private static final long serialVersionUID = 1L;

  public ValidatorUnavailableException(String validatorId, Throwable cause) {
    super(
        "validation.validator-unavailable",
        cause,
        "code.validation.validator-unavailable",
        validatorId);
  }
}
