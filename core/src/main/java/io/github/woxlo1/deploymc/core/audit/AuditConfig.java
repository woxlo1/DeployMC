package io.github.woxlo1.deploymc.core.audit;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Validated, host-controlled audit settings. No request input reaches this type. */
public final class AuditConfig {

  public enum FsyncPolicy {
    RECORD("record"),
    BATCH("batch");

    private final String wire;

    FsyncPolicy(String wire) {
      this.wire = wire;
    }

    public String wire() {
      return wire;
    }

    public static FsyncPolicy parse(String value) {
      for (FsyncPolicy candidate : values()) {
        if (candidate.wire.equals(value)) {
          return candidate;
        }
      }
      return null;
    }
  }

  private static final String DEFAULT_DIRECTORY = ".deploymc/audit";
  private static final long MEGABYTE = 1024L * 1024L;

  private final String directory;
  private final long rotateBytes;
  private final int retentionDays;
  private final FsyncPolicy fsyncPolicy;
  private final int maxBatchRecords;

  private AuditConfig(
      String directory,
      long rotateBytes,
      int retentionDays,
      FsyncPolicy fsyncPolicy,
      int maxBatchRecords) {
    this.directory = requireDirectory(directory);
    if (rotateBytes < MEGABYTE || rotateBytes > 1024L * MEGABYTE) {
      throw new IllegalArgumentException("audit rotation size is out of range");
    }
    if (retentionDays < 1 || retentionDays > 3650) {
      throw new IllegalArgumentException("audit retention is out of range");
    }
    this.rotateBytes = rotateBytes;
    this.retentionDays = retentionDays;
    this.fsyncPolicy = Objects.requireNonNull(fsyncPolicy, "fsyncPolicy");
    if (maxBatchRecords < 1 || maxBatchRecords > 256) {
      throw new IllegalArgumentException("audit batch size is out of range");
    }
    this.maxBatchRecords = maxBatchRecords;
  }

  public static AuditConfig defaults() {
    return new AuditConfig(DEFAULT_DIRECTORY, 50L * MEGABYTE, 365, FsyncPolicy.RECORD, 1);
  }

  /** Explicit host-controlled configuration, primarily for deterministic tests and embedders. */
  public static AuditConfig of(
      String directory,
      long rotateBytes,
      int retentionDays,
      FsyncPolicy fsyncPolicy,
      int maxBatchRecords) {
    return new AuditConfig(directory, rotateBytes, retentionDays, fsyncPolicy, maxBatchRecords);
  }

  /** Reads only host-controlled dotted settings and fails closed on an invalid value. */
  public static AuditConfig fromConfig(ConfigView view) {
    Objects.requireNonNull(view, "view");
    int rotateMb = view.getInt("audit.rotate-size-mb", 50);
    int retentionDays = view.getInt("audit.retention-days", 365);
    int maxBatchRecords = view.getInt("audit.max-batch-records", 1);
    FsyncPolicy policy = FsyncPolicy.parse(view.getString("audit.fsync-policy", "record"));
    if (policy == null) {
      throw new IllegalArgumentException("invalid audit fsync policy");
    }
    return new AuditConfig(
        view.getString("audit.directory", DEFAULT_DIRECTORY),
        rotateMb * MEGABYTE,
        retentionDays,
        policy,
        maxBatchRecords);
  }

  public String directory() {
    return directory;
  }

  public long rotateBytes() {
    return rotateBytes;
  }

  public int retentionDays() {
    return retentionDays;
  }

  public FsyncPolicy fsyncPolicy() {
    return fsyncPolicy;
  }

  public int maxBatchRecords() {
    return maxBatchRecords;
  }

  /** Resolves the host directory string against a host-controlled base path. */
  public Path resolveAgainst(Path base) {
    Objects.requireNonNull(base, "base");
    Path relative = Paths.get(directory);
    Path resolved = base.toAbsolutePath().normalize().resolve(relative).normalize();
    if (!resolved.startsWith(base.toAbsolutePath().normalize())) {
      throw new IllegalArgumentException("audit directory escapes its base");
    }
    return resolved;
  }

  private static String requireDirectory(String value) {
    if (value == null || value.trim().isEmpty() || value.length() > 1024) {
      throw new IllegalArgumentException("invalid audit directory");
    }
    if (value.startsWith("/") || value.startsWith("\\") || value.indexOf(':') >= 0) {
      throw new IllegalArgumentException("audit directory must be relative");
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isISOControl(character)) {
        throw new IllegalArgumentException("invalid audit directory");
      }
    }
    Path relative = Paths.get(value.replace('\\', '/'));
    for (Path segment : relative) {
      if ("..".equals(segment.toString())) {
        throw new IllegalArgumentException("audit directory must not traverse");
      }
    }
    return value;
  }
}
