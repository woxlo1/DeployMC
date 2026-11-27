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

  /**
   * Combines two evaluated buckets into the most constraining single decision.
   *
   * <p>The lower remaining count wins; ties are resolved by the earlier reset. The result is denied
   * when either input is denied, and its {@code Retry-After} is the shortest wait that clears any
   * denial. No identity or address data is retained.
   */
  static RateLimitDecision mostConstraining(RateLimitDecision first, RateLimitDecision second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    RateLimitDecision selected = first;
    if (second.remaining < first.remaining
        || second.remaining == first.remaining
            && second.resetEpochSeconds < first.resetEpochSeconds) {
      selected = second;
    }
    boolean allowed = first.allowed && second.allowed;
    long retry = 0L;
    if (!allowed) {
      retry = retryAfter(first);
      long other = retryAfter(second);
      if (other > 0L && (retry == 0L || other < retry)) {
        retry = other;
      }
    }
    return new RateLimitDecision(
        allowed,
        selected.limit,
        selected.remaining,
        selected.resetEpochSeconds,
        Math.max(0L, retry));
  }

  private static long retryAfter(RateLimitDecision decision) {
    return decision.allowed ? 0L : Math.max(1L, decision.retryAfterSeconds);
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
