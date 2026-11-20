package io.github.woxlo1.deploymc.core.sandbox;

/** Stable, machine-readable reasons why a sandbox path was rejected. */
public enum SandboxErrorCode {
  SANDBOX_EMPTY("sandbox.error.empty"),
  SANDBOX_ABSOLUTE("sandbox.error.absolute"),
  SANDBOX_TRAVERSAL("sandbox.error.traversal"),
  SANDBOX_INVALID_NAME("sandbox.error.invalidName"),
  SANDBOX_NUL("sandbox.error.nul"),
  SANDBOX_CONTROL_CHAR("sandbox.error.controlChar"),
  SANDBOX_TOO_LONG("sandbox.error.tooLong"),
  SANDBOX_INVALID_UNICODE("sandbox.error.invalidUnicode"),
  SANDBOX_SYMLINK("sandbox.error.symlink"),
  SANDBOX_OUTSIDE_ROOT("sandbox.error.outsideRoot"),
  SANDBOX_PROTECTED("sandbox.error.protected"),
  SANDBOX_DENIED("sandbox.error.denied"),
  SANDBOX_FILE_CHANGED("sandbox.error.fileChanged"),
  SANDBOX_IO("sandbox.error.io");

  private final String messageKey;

  SandboxErrorCode(String messageKey) {
    this.messageKey = messageKey;
  }

  /** Catalog key used to localize this code for an API response. */
  public String messageKey() {
    return messageKey;
  }
}
