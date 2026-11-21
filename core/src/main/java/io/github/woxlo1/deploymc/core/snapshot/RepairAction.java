package io.github.woxlo1.deploymc.core.snapshot;

import java.util.Objects;

/** A conservative repair action identified by a stable code and a store-relative detail. */
public final class RepairAction {

  private final String code;
  private final String detail;

  public RepairAction(String code, String detail) {
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
