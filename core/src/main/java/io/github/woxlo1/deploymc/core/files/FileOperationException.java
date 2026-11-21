package io.github.woxlo1.deploymc.core.files;

import io.github.woxlo1.deploymc.core.sandbox.SandboxErrorCode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.validation.ValidationLimitException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A file-operation failure with a stable API code and a localized diagnostic. */
public final class FileOperationException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Stable reasons. Codes follow {@code specification/api/errors.md}. */
  public enum Reason {
    PATH_REJECTED("files.path-rejected", "files.error.pathRejected"),
    PROTECTED("files.protected", "files.error.protected"),
    NOT_FOUND("files.not-found", "files.error.notFound"),
    NOT_DIRECTORY("files.not-directory", "files.error.notDirectory"),
    ALREADY_EXISTS("files.already-exists", "files.error.alreadyExists"),
    REVISION_CONFLICT("files.revision-conflict", "files.error.revisionConflict"),
    PRECONDITION_REQUIRED("request.precondition-required", "files.error.preconditionRequired"),
    TOO_LARGE("files.too-large", "files.error.tooLarge"),
    INVALID_RANGE("files.invalid-range", "files.error.invalidRange"),
    DIRECTORY_NOT_EMPTY("files.directory-not-empty", "files.error.directoryNotEmpty"),
    DELETE_CONFIRMATION("files.delete-confirmation-required", "files.error.deleteConfirmation"),
    DELETE_LIMIT("files.delete-limit-exceeded", "files.error.deleteLimit"),
    SNAPSHOT_REQUIRED("files.snapshot-required", "files.error.snapshotRequired"),
    SNAPSHOT_FAILED("files.snapshot-failed", "files.error.snapshotFailed"),
    ATOMIC_MOVE_UNSUPPORTED("files.atomic-move-unsupported", "files.error.atomicMoveUnsupported"),
    INVALID_OPERATION("files.invalid-operation", "files.error.invalidOperation"),
    VALIDATION_FAILED("validation.failed", "files.error.validationFailed"),
    VALIDATION_LIMIT("validation.limit-exceeded", "files.error.validationLimit"),
    VALIDATOR_UNAVAILABLE("validation.validator-unavailable", "files.error.validatorUnavailable"),
    IO("files.io", "files.error.io");

    private final String code;
    private final String messageKey;

    Reason(String code, String messageKey) {
      this.code = code;
      this.messageKey = messageKey;
    }

    public String code() {
      return code;
    }

    public String messageKey() {
      return messageKey;
    }
  }

  private final Reason reason;
  private final String issueCode;
  private final String[] parameters;

  public FileOperationException(Reason reason, Object... parameters) {
    this(reason, null, null, parameters);
  }

  public FileOperationException(Reason reason, Throwable cause, Object... parameters) {
    this(reason, cause, null, parameters);
  }

  private FileOperationException(
      Reason reason, Throwable cause, String issueCode, Object... parameters) {
    super(message(reason, parameters), cause);
    this.reason = reason;
    this.issueCode = issueCode;
    this.parameters = copy(parameters);
  }

  /** Preserves the format-specific issue code from a validation structural budget. */
  public static FileOperationException validationLimit(
      String relativePath, ValidationLimitException cause) {
    return new FileOperationException(
        Reason.VALIDATION_LIMIT,
        cause,
        cause.issueCode(),
        relativePath,
        cause.limitName(),
        Long.valueOf(cause.observed()),
        Long.valueOf(cause.maximum()));
  }

  /** Builds a generic validation limit failure that has no format-specific issue code. */
  public static FileOperationException validationLimit(
      String relativePath, String limitName, long observed, long maximum) {
    return new FileOperationException(
        Reason.VALIDATION_LIMIT,
        relativePath,
        limitName,
        Long.valueOf(observed),
        Long.valueOf(maximum));
  }

  /** Maps a sandbox rejection without exposing an absolute host path. */
  public static FileOperationException fromSandbox(
      SandboxRejectionException rejection, String requestedPath) {
    Reason reason =
        rejection.code() == SandboxErrorCode.SANDBOX_PROTECTED
            ? Reason.PROTECTED
            : Reason.PATH_REJECTED;
    return new FileOperationException(reason, rejection, requestedPath, rejection.code().name());
  }

  public Reason reason() {
    return reason;
  }

  public String code() {
    return reason.code();
  }

  public String messageKey() {
    return reason.messageKey();
  }

  /** The validation issue code carried in an HTTP {@code errors[]} entry, or null. */
  public String issueCode() {
    return issueCode;
  }

  public List<String> parameters() {
    return Collections.unmodifiableList(Arrays.asList(parameters));
  }

  private static String message(Reason reason, Object... parameters) {
    return FileMessages.format(Locale.ROOT, reason.messageKey(), parameters);
  }

  private static String[] copy(Object... parameters) {
    List<String> copied = new ArrayList<String>();
    if (parameters != null) {
      for (Object parameter : parameters) {
        copied.add(String.valueOf(parameter));
      }
    }
    return copied.toArray(new String[0]);
  }
}
