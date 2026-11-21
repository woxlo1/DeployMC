package io.github.woxlo1.deploymc.core.snapshot;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A snapshot-store failure with a stable API code and a localized diagnostic. */
public final class SnapshotStoreException extends IOException {

  private static final long serialVersionUID = 1L;

  /** Stable reasons. Codes follow {@code specification/api/errors.md}. */
  public enum Reason {
    NOT_FOUND("snapshots.not-found", "snapshots.error.notFound"),
    CORRUPT("snapshots.corrupt", "snapshots.error.corrupt"),
    RESTORE_CONFLICT("snapshots.restore-conflict", "snapshots.error.restoreConflict"),
    STORE_UNAVAILABLE("snapshots.store-unavailable", "snapshots.error.storeUnavailable"),
    PATH_REJECTED("files.path-rejected", "snapshots.error.pathRejected"),
    IO("files.io", "snapshots.error.io");

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
  private final Serializable[] parameters;

  public SnapshotStoreException(Reason reason, Object... parameters) {
    this(reason, null, parameters);
  }

  public SnapshotStoreException(Reason reason, Throwable cause, Object... parameters) {
    super(message(reason, parameters), cause);
    this.reason = reason;
    this.parameters = copy(parameters);
  }

  public Reason reason() {
    return reason;
  }

  public String code() {
    return reason.code();
  }

  public String localizedMessage(Locale locale) {
    return SnapshotStoreMessages.format(locale, reason.messageKey(), (Object[]) parameters);
  }

  public List<String> parameters() {
    List<String> result = new ArrayList<String>();
    for (Object parameter : parameters) {
      result.add(String.valueOf(parameter));
    }
    return Collections.unmodifiableList(result);
  }

  private static String message(Reason reason, Object... parameters) {
    return SnapshotStoreMessages.format(Locale.ROOT, reason.messageKey(), parameters);
  }

  private static Serializable[] copy(Object... parameters) {
    List<Serializable> copied = new ArrayList<Serializable>();
    if (parameters != null) {
      for (Object parameter : parameters) {
        copied.add(
            parameter instanceof LocalizedDetail
                ? (LocalizedDetail) parameter
                : String.valueOf(parameter));
      }
    }
    return copied.toArray(new Serializable[0]);
  }
}
