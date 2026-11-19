package io.github.woxlo1.deploymc.core.auth;

/** Failure to load or write the host-controlled token provisioning directory. */
public final class TokenStoreException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Stable categories used by localized startup and command messages. */
  public enum Reason {
    DIRECTORY,
    RECORD,
    IO
  }

  private final Reason reason;
  private final String detail;

  public TokenStoreException(Reason reason, String detail) {
    super(detail);
    this.reason = reason;
    this.detail = detail == null ? "" : detail;
  }

  public TokenStoreException(Reason reason, String detail, Throwable cause) {
    super(detail, cause);
    this.reason = reason;
    this.detail = detail == null ? "" : detail;
  }

  public Reason reason() {
    return reason;
  }

  public String detail() {
    return detail;
  }
}
