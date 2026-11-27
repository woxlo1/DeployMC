package io.github.woxlo1.deploymc.transport;

import java.util.Objects;

/** Immutable, fail-closed observability and self-protection settings. */
public final class ObservabilityConfig {

  private final LoggingFormat loggingFormat;
  private final boolean metricsEnabled;
  private final String metricsPermission;
  private final int addressRateLimitPerMinute;
  private final int identityRateLimitPerMinute;
  private final int authFailureRateLimitPerMinute;
  private final int maxRateLimitKeys;
  private final long watchdogIntervalMillis;
  private final long watchdogTimeoutMillis;

  private ObservabilityConfig(Builder builder) {
    this.loggingFormat = builder.loggingFormat;
    this.metricsEnabled = builder.metricsEnabled;
    this.metricsPermission = requirePermission(builder.metricsPermission);
    this.addressRateLimitPerMinute = builder.addressRateLimitPerMinute;
    this.identityRateLimitPerMinute = builder.identityRateLimitPerMinute;
    this.authFailureRateLimitPerMinute = builder.authFailureRateLimitPerMinute;
    this.maxRateLimitKeys = builder.maxRateLimitKeys;
    this.watchdogIntervalMillis = builder.watchdogIntervalMillis;
    this.watchdogTimeoutMillis = builder.watchdogTimeoutMillis;
    validate();
  }

  /** Safe defaults: text logging, metrics off, and conservative in-memory budgets. */
  public static ObservabilityConfig defaults() {
    return new Builder().build();
  }

  /** Builder for {@link ObservabilityConfig}. */
  public static Builder builder() {
    return new Builder();
  }

  public LoggingFormat loggingFormat() {
    return loggingFormat;
  }

  public boolean metricsEnabled() {
    return metricsEnabled;
  }

  public String metricsPermission() {
    return metricsPermission;
  }

  public int addressRateLimitPerMinute() {
    return addressRateLimitPerMinute;
  }

  public int identityRateLimitPerMinute() {
    return identityRateLimitPerMinute;
  }

  public int authFailureRateLimitPerMinute() {
    return authFailureRateLimitPerMinute;
  }

  public int maxRateLimitKeys() {
    return maxRateLimitKeys;
  }

  public long watchdogIntervalMillis() {
    return watchdogIntervalMillis;
  }

  public long watchdogTimeoutMillis() {
    return watchdogTimeoutMillis;
  }

  /** The only supported metrics permission is the closed-registry {@code server.read}. */
  public static boolean isSupportedPermission(String value) {
    return "server.read".equals(value);
  }

  private static String requirePermission(String value) {
    if (!isSupportedPermission(value)) {
      throw new IllegalArgumentException("metricsPermission is not a supported stable identifier");
    }
    return value;
  }

  private void validate() {
    requireRange(addressRateLimitPerMinute, 1, 100_000, "addressRateLimitPerMinute");
    requireRange(identityRateLimitPerMinute, 1, 100_000, "identityRateLimitPerMinute");
    requireRange(authFailureRateLimitPerMinute, 1, 100_000, "authFailureRateLimitPerMinute");
    requireRange(maxRateLimitKeys, 16, 100_000, "maxRateLimitKeys");
    requireRange(watchdogIntervalMillis, 100L, 60_000L, "watchdogIntervalMillis");
    requireRange(watchdogTimeoutMillis, 1L, 60_000L, "watchdogTimeoutMillis");
    if (watchdogTimeoutMillis >= watchdogIntervalMillis) {
      throw new IllegalArgumentException("watchdogTimeoutMillis must be less than the interval");
    }
  }

  private static void requireRange(long value, long minimum, long maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
  }

  /** Builder with validation at {@link #build()}. */
  public static final class Builder {

    private LoggingFormat loggingFormat = LoggingFormat.TEXT;
    private boolean metricsEnabled = false;
    private String metricsPermission = "server.read";
    private int addressRateLimitPerMinute = 300;
    private int identityRateLimitPerMinute = 300;
    private int authFailureRateLimitPerMinute = 60;
    private int maxRateLimitKeys = 4096;
    private long watchdogIntervalMillis = 5_000L;
    private long watchdogTimeoutMillis = 2_000L;

    private Builder() {}

    public Builder loggingFormat(LoggingFormat value) {
      this.loggingFormat = Objects.requireNonNull(value, "loggingFormat");
      return this;
    }

    public Builder metricsEnabled(boolean value) {
      this.metricsEnabled = value;
      return this;
    }

    public Builder metricsPermission(String value) {
      this.metricsPermission = value;
      return this;
    }

    public Builder addressRateLimitPerMinute(int value) {
      this.addressRateLimitPerMinute = value;
      return this;
    }

    public Builder identityRateLimitPerMinute(int value) {
      this.identityRateLimitPerMinute = value;
      return this;
    }

    public Builder authFailureRateLimitPerMinute(int value) {
      this.authFailureRateLimitPerMinute = value;
      return this;
    }

    public Builder maxRateLimitKeys(int value) {
      this.maxRateLimitKeys = value;
      return this;
    }

    public Builder watchdogIntervalMillis(long value) {
      this.watchdogIntervalMillis = value;
      return this;
    }

    public Builder watchdogTimeoutMillis(long value) {
      this.watchdogTimeoutMillis = value;
      return this;
    }

    public ObservabilityConfig build() {
      return new ObservabilityConfig(this);
    }
  }
}
