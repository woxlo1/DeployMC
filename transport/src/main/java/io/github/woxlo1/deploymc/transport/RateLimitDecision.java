package io.github.woxlo1.deploymc.transport;

/** Immutable result of one token-bucket check. Contains no identity or address data. */
public final class RateLimitDecision {

  private final boolean allowed;
  private final int limit;
  private final int remaining;
  private final long resetEpochSeconds;
  private final long retryAfterSeconds;

  private RateLimitDecision(
      boolean allowed, int limit, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    this.allowed = allowed;
    this.limit = limit;
    this.remaining = remaining;
    this.resetEpochSeconds = resetEpochSeconds;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  static RateLimitDecision allowed(int limit, int remaining, long resetEpochSeconds) {
    return new RateLimitDecision(true, limit, remaining, resetEpochSeconds, 0L);
  }

  static RateLimitDecision denied(
      int limit, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    return new RateLimitDecision(false, limit, remaining, resetEpochSeconds, retryAfterSeconds);
  }

  public boolean allowed() {
    return allowed;
  }

  public int limit() {
    return limit;
  }

  public int remaining() {
    return remaining;
  }

  public long resetEpochSeconds() {
    return resetEpochSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
