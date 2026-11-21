package io.github.woxlo1.deploymc.transport;

/** Catalog keys for transport-side log messages. */
public final class MessageKeys {

  public static final String STARTED = "transport.started";
  public static final String STOPPED = "transport.stopped";
  public static final String WARNING_NON_LOOPBACK = "transport.warning.nonLoopback";
  public static final String REQUEST_COMPLETED = "transport.request.completed";
  public static final String REQUEST_REJECTED = "transport.request.rejected";
  public static final String WEBSOCKET_OPENED = "transport.websocket.opened";
  public static final String WEBSOCKET_CLOSED = "transport.websocket.closed";
  public static final String WEBSOCKET_REJECTED = "transport.websocket.rejected";
  public static final String WEBSOCKET_QUEUE_FULL = "transport.websocket.queueFull";
  public static final String AUTH_REQUIRED_TITLE = "problem.auth.required.title";
  public static final String AUTH_REQUIRED_DETAIL = "problem.auth.required.detail";
  public static final String AUTH_INVALID_TITLE = "problem.auth.invalid.title";
  public static final String AUTH_INVALID_DETAIL = "problem.auth.invalid.detail";
  public static final String AUTH_EXPIRED_TITLE = "problem.auth.expired.title";
  public static final String AUTH_EXPIRED_DETAIL = "problem.auth.expired.detail";

  public static final String REQUEST_INVALID_TITLE = "problem.request.invalid.title";
  public static final String REQUEST_INVALID_DETAIL = "problem.request.invalid.detail";
  public static final String REQUEST_UNSUPPORTED_MEDIA_TYPE_TITLE =
      "problem.request.unsupported-media-type.title";
  public static final String REQUEST_UNSUPPORTED_MEDIA_TYPE_DETAIL =
      "problem.request.unsupported-media-type.detail";
  public static final String REQUEST_TOO_LARGE_TITLE = "problem.request.too-large.title";
  public static final String REQUEST_TOO_LARGE_DETAIL = "problem.request.too-large.detail";
  public static final String REQUEST_INVALID_CURSOR_TITLE = "problem.request.invalid-cursor.title";
  public static final String REQUEST_INVALID_CURSOR_DETAIL =
      "problem.request.invalid-cursor.detail";
  public static final String REQUEST_METHOD_NOT_ALLOWED_TITLE =
      "problem.request.method-not-allowed.title";
  public static final String REQUEST_METHOD_NOT_ALLOWED_DETAIL =
      "problem.request.method-not-allowed.detail";
  public static final String REQUEST_PRECONDITION_REQUIRED_TITLE =
      "problem.request.precondition-required.title";
  public static final String REQUEST_PRECONDITION_REQUIRED_DETAIL =
      "problem.request.precondition-required.detail";

  public static final String FILES_PATH_REJECTED_TITLE = "problem.files.path-rejected.title";
  public static final String FILES_PATH_REJECTED_DETAIL = "problem.files.path-rejected.detail";
  public static final String FILES_NOT_FOUND_TITLE = "problem.files.not-found.title";
  public static final String FILES_NOT_FOUND_DETAIL = "problem.files.not-found.detail";
  public static final String FILES_NOT_DIRECTORY_TITLE = "problem.files.not-directory.title";
  public static final String FILES_NOT_DIRECTORY_DETAIL = "problem.files.not-directory.detail";
  public static final String FILES_ALREADY_EXISTS_TITLE = "problem.files.already-exists.title";
  public static final String FILES_ALREADY_EXISTS_DETAIL = "problem.files.already-exists.detail";
  public static final String FILES_REVISION_CONFLICT_TITLE =
      "problem.files.revision-conflict.title";
  public static final String FILES_REVISION_CONFLICT_DETAIL =
      "problem.files.revision-conflict.detail";
  public static final String FILES_TOO_LARGE_TITLE = "problem.files.too-large.title";
  public static final String FILES_TOO_LARGE_DETAIL = "problem.files.too-large.detail";
  public static final String FILES_PROTECTED_TITLE = "problem.files.protected.title";
  public static final String FILES_PROTECTED_DETAIL = "problem.files.protected.detail";
  public static final String FILES_INVALID_RANGE_TITLE = "problem.files.invalid-range.title";
  public static final String FILES_INVALID_RANGE_DETAIL = "problem.files.invalid-range.detail";
  public static final String FILES_DIRECTORY_NOT_EMPTY_TITLE =
      "problem.files.directory-not-empty.title";
  public static final String FILES_DIRECTORY_NOT_EMPTY_DETAIL =
      "problem.files.directory-not-empty.detail";
  public static final String FILES_DELETE_CONFIRMATION_TITLE =
      "problem.files.delete-confirmation-required.title";
  public static final String FILES_DELETE_CONFIRMATION_DETAIL =
      "problem.files.delete-confirmation-required.detail";
  public static final String FILES_DELETE_LIMIT_TITLE = "problem.files.delete-limit-exceeded.title";
  public static final String FILES_DELETE_LIMIT_DETAIL =
      "problem.files.delete-limit-exceeded.detail";
  public static final String FILES_SNAPSHOT_REQUIRED_TITLE =
      "problem.files.snapshot-required.title";
  public static final String FILES_SNAPSHOT_REQUIRED_DETAIL =
      "problem.files.snapshot-required.detail";
  public static final String FILES_SNAPSHOT_FAILED_TITLE = "problem.files.snapshot-failed.title";
  public static final String FILES_SNAPSHOT_FAILED_DETAIL = "problem.files.snapshot-failed.detail";
  public static final String FILES_ATOMIC_MOVE_UNSUPPORTED_TITLE =
      "problem.files.atomic-move-unsupported.title";
  public static final String FILES_ATOMIC_MOVE_UNSUPPORTED_DETAIL =
      "problem.files.atomic-move-unsupported.detail";
  public static final String FILES_INVALID_OPERATION_TITLE =
      "problem.files.invalid-operation.title";
  public static final String FILES_INVALID_OPERATION_DETAIL =
      "problem.files.invalid-operation.detail";
  public static final String FILES_IO_TITLE = "problem.files.io.title";
  public static final String FILES_IO_DETAIL = "problem.files.io.detail";

  public static final String VALIDATION_FAILED_TITLE = "problem.validation.failed.title";
  public static final String VALIDATION_FAILED_DETAIL = "problem.validation.failed.detail";
  public static final String VALIDATION_LIMIT_TITLE = "problem.validation.limit-exceeded.title";
  public static final String VALIDATION_LIMIT_DETAIL = "problem.validation.limit-exceeded.detail";
  public static final String VALIDATOR_UNAVAILABLE_TITLE =
      "problem.validation.validator-unavailable.title";
  public static final String VALIDATOR_UNAVAILABLE_DETAIL =
      "problem.validation.validator-unavailable.detail";
  public static final String SERVER_INTERNAL_TITLE = "problem.server.internal.title";
  public static final String SERVER_INTERNAL_DETAIL = "problem.server.internal.detail";
  public static final String ISSUE_VALIDATION_FAILED = "issue.validation.failed";
  public static final String ISSUE_VALIDATION_LIMIT = "issue.validation.limit";

  private MessageKeys() {}
}
