package io.github.woxlo1.deploymc.core.audit;

/** Fail-closed audit failure. Details never contain host paths or raw disk errors. */
public final class AuditException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Stable problem codes. Each maps to a catalogued REST code. */
  public enum Code {
    UNAVAILABLE("audit.unavailable"),
    WRITE_FAILED("audit.write-failed"),
    INVALID("request.invalid"),
    INVALID_CURSOR("request.invalid-cursor"),
    EXPORT_LIMIT("audit.export-limit-exceeded");

    private final String problemCode;

    Code(String problemCode) {
      this.problemCode = problemCode;
    }

    public String problemCode() {
      return problemCode;
    }
  }

  private final Code code;

  public AuditException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public AuditException(Code code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public String problemCode() {
    return code.problemCode();
  }

  public static AuditException unavailable(String message) {
    return new AuditException(Code.UNAVAILABLE, message);
  }

  public static AuditException writeFailed(String message) {
    return new AuditException(Code.WRITE_FAILED, message);
  }

  public static AuditException invalid(String message) {
    return new AuditException(Code.INVALID, message);
  }

  public static AuditException invalidCursor(String message) {
    return new AuditException(Code.INVALID_CURSOR, message);
  }

  public static AuditException exportLimit(String message) {
    return new AuditException(Code.EXPORT_LIMIT, message);
  }
}
