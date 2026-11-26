package io.github.woxlo1.deploymc.core.audit;

/** Stable action identifiers defined by the audit log format. */
public final class AuditActions {

  public static final String FILES_LIST = "files.list";
  public static final String FILES_READ = "files.read";
  public static final String FILES_WRITE = "files.write";
  public static final String FILES_UPLOAD = "files.upload";
  public static final String FILES_DELETE = "files.delete";
  public static final String FILES_RENAME = "files.rename";
  public static final String FILES_RESTORE = "files.restore";
  public static final String SNAPSHOTS_CREATE = "snapshots.create";
  public static final String SNAPSHOTS_RESTORE = "snapshots.restore";
  public static final String SNAPSHOTS_PRUNE = "snapshots.prune";
  public static final String CONSOLE_COMMAND = "console.command";
  public static final String TOKENS_CREATE = "tokens.create";
  public static final String TOKENS_REVOKE = "tokens.revoke";
  public static final String CONFIG_RELOAD = "config.reload";
  public static final String AUDIT_READ = "audit.read";
  public static final String AUDIT_ROTATE = "audit.rotate";
  public static final String AUDIT_ANCHOR = "audit.anchor";
  public static final String CLOCK_ANOMALY = "clock.anomaly";

  private static final java.util.regex.Pattern SHAPE =
      java.util.regex.Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9-]*)+$");

  private AuditActions() {}

  /** True only for identifiers in the documented dotted shape. */
  public static boolean valid(String action) {
    return action != null && action.length() <= 128 && SHAPE.matcher(action).matches();
  }
}
