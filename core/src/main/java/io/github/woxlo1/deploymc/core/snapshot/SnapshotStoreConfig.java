package io.github.woxlo1.deploymc.core.snapshot;

/** Host-controlled limits and policies for one snapshot store. */
public final class SnapshotStoreConfig {

  public static final int DEFAULT_MAX_VERSIONS_PER_PATH = 50;
  public static final long DEFAULT_MIN_AGE_DAYS = 90L;
  public static final long DEFAULT_MAX_STORE_BYTES = 2L * 1024L * 1024L * 1024L;
  public static final long DEFAULT_GC_GRACE_HOURS = 24L;
  public static final long DEFAULT_LOCK_TIMEOUT_MILLIS = 5000L;

  private final int maxVersionsPerPath;
  private final long minAgeDays;
  private final long maxStoreBytes;
  private final long gcGraceHours;
  private final long lockTimeoutMillis;

  private SnapshotStoreConfig(Builder builder) {
    this.maxVersionsPerPath = builder.maxVersionsPerPath;
    this.minAgeDays = builder.minAgeDays;
    this.maxStoreBytes = builder.maxStoreBytes;
    this.gcGraceHours = builder.gcGraceHours;
    this.lockTimeoutMillis = builder.lockTimeoutMillis;
  }

  public static SnapshotStoreConfig defaults() {
    return new Builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public int maxVersionsPerPath() {
    return maxVersionsPerPath;
  }

  public long minAgeDays() {
    return minAgeDays;
  }

  public long maxStoreBytes() {
    return maxStoreBytes;
  }

  public long gcGraceHours() {
    return gcGraceHours;
  }

  public long lockTimeoutMillis() {
    return lockTimeoutMillis;
  }

  public Builder toBuilder() {
    return new Builder()
        .maxVersionsPerPath(maxVersionsPerPath)
        .minAgeDays(minAgeDays)
        .maxStoreBytes(maxStoreBytes)
        .gcGraceHours(gcGraceHours)
        .lockTimeoutMillis(lockTimeoutMillis);
  }

  /** Mutable builder that validates every host-supplied limit before a store can be opened. */
  public static final class Builder {

    private int maxVersionsPerPath = DEFAULT_MAX_VERSIONS_PER_PATH;
    private long minAgeDays = DEFAULT_MIN_AGE_DAYS;
    private long maxStoreBytes = DEFAULT_MAX_STORE_BYTES;
    private long gcGraceHours = DEFAULT_GC_GRACE_HOURS;
    private long lockTimeoutMillis = DEFAULT_LOCK_TIMEOUT_MILLIS;

    public Builder maxVersionsPerPath(int value) {
      if (value < 3) {
        throw new IllegalArgumentException("maxVersionsPerPath must be at least 3");
      }
      this.maxVersionsPerPath = value;
      return this;
    }

    public Builder minAgeDays(long value) {
      if (value < 0L) {
        throw new IllegalArgumentException("minAgeDays must not be negative");
      }
      this.minAgeDays = value;
      return this;
    }

    public Builder maxStoreBytes(long value) {
      if (value <= 0L) {
        throw new IllegalArgumentException("maxStoreBytes must be positive");
      }
      this.maxStoreBytes = value;
      return this;
    }

    public Builder gcGraceHours(long value) {
      if (value < 0L) {
        throw new IllegalArgumentException("gcGraceHours must not be negative");
      }
      this.gcGraceHours = value;
      return this;
    }

    public Builder lockTimeoutMillis(long value) {
      if (value <= 0L) {
        throw new IllegalArgumentException("lockTimeoutMillis must be positive");
      }
      this.lockTimeoutMillis = value;
      return this;
    }

    public SnapshotStoreConfig build() {
      return new SnapshotStoreConfig(this);
    }
  }
}
