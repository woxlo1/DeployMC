package io.github.woxlo1.deploymc.core.snapshot;

import java.util.Objects;

/** A stable integrity issue with a store-relative detail; never an absolute host path. */
public final class VerifyIssue {

  private final String code;
  private final String detail;

  public VerifyIssue(String code, String detail) {
    this.code = Objects.requireNonNull(code, "code");
    this.detail = Objects.requireNonNull(detail, "detail");
  }

  public String code() {
    return code;
  }

  public String detail() {
    return detail;
  }

  @Override
  public String toString() {
    return code + ": " + detail;
  }
}
