package io.github.woxlo1.deploymc.core.snapshot;

/** Normalized metadata reason mapped from an internal hook reason. */
public enum SnapshotReason {
  EDIT("edit"),
  DELETE("delete"),
  RENAME("rename"),
  DEPLOY("deploy"),
  RESTORE("restore");

  private final String value;

  SnapshotReason(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static SnapshotReason fromValue(String value) {
    for (SnapshotReason reason : values()) {
      if (reason.value.equals(value)) {
        return reason;
      }
    }
    return null;
  }

  /** Maps internal file-operation reasons without guessing unknown values. */
  public static SnapshotReason fromHookReason(String hookReason) {
    if (hookReason == null) {
      return null;
    }
    if ("before-overwrite".equals(hookReason) || "before-edit".equals(hookReason)) {
      return EDIT;
    }
    if ("before-delete".equals(hookReason)) {
      return DELETE;
    }
    if ("before-move".equals(hookReason)) {
      return RENAME;
    }
    if ("before-restore".equals(hookReason)) {
      return RESTORE;
    }
    if ("deploy".equals(hookReason)
        || "before-deploy".equals(hookReason)
        || hookReason.startsWith("deploy-")) {
      return DEPLOY;
    }
    return null;
  }
}
