package io.github.woxlo1.deploymc.transport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, continuously refilling token buckets for effective-peer, identity, and
 * authentication-failure budgets. Raw identifiers are hashed before use as map keys and are never
 * logged or exported.
 */
public final class RateLimiter {

  private static final long WINDOW_MILLIS = 60_000L;

  private final int addressPerMinute;
  private final int identityPerMinute;
  private final int authFailurePerMinute;
  private final int maxKeys;
  private final Clock clock;
  private final Map<String, Bucket> addressBuckets;
  private final Map<String, Bucket> identityBuckets;
  private final Map<String, Bucket> authFailureBuckets;

  /** Compatibility constructor that gives the address and identity buckets the same budget. */
  public RateLimiter(int identityPerMinute, int authFailurePerMinute, int maxKeys, Clock clock) {
    this(identityPerMinute, identityPerMinute, authFailurePerMinute, maxKeys, clock);
  }

  public RateLimiter(
      int addressPerMinute,
      int identityPerMinute,
      int authFailurePerMinute,
      int maxKeys,
      Clock clock) {
    requirePositive(addressPerMinute, "addressPerMinute");
    requirePositive(identityPerMinute, "identityPerMinute");
    requirePositive(authFailurePerMinute, "authFailurePerMinute");
    if (maxKeys < 16 || maxKeys > 100_000) {
      throw new IllegalArgumentException("maxKeys must be between 16 and 100000");
    }
    this.addressPerMinute = addressPerMinute;
    this.identityPerMinute = identityPerMinute;
    this.authFailurePerMinute = authFailurePerMinute;
    this.maxKeys = maxKeys;
    this.clock = Objects.requireNonNull(clock, "clock");
    this.addressBuckets = new LinkedHashMap<String, Bucket>(maxKeys, 0.75f, true);
    this.identityBuckets = new LinkedHashMap<String, Bucket>(maxKeys, 0.75f, true);
    this.authFailureBuckets = new LinkedHashMap<String, Bucket>(maxKeys, 0.75f, true);
  }

  /** Checks the request bucket keyed by the canonical effective peer address. */
  public synchronized RateLimitDecision tryAddress(String effectivePeer) {
    if (isBlank(effectivePeer)) {
      return denyClosed(addressPerMinute, clock.millis());
    }
    String key = hash("address", effectivePeer);
    return consume(addressBuckets, key, addressPerMinute, clock.millis());
  }

  /** Checks the bucket keyed by (identity id, endpoint class). */
  public synchronized RateLimitDecision tryIdentity(String identityId, EndpointClass endpoint) {
    return tryIdentity(identityId, endpoint, identityPerMinute);
  }

  /**
   * Checks the identity bucket with a role-derived budget that can only lower the global budget.
   */
  public synchronized RateLimitDecision tryIdentity(
      String identityId, EndpointClass endpoint, int limitOverride) {
    if (isBlank(identityId) || endpoint == null || limitOverride < 1) {
      return denyClosed(identityPerMinute, clock.millis());
    }
    int effectiveLimit = Math.min(identityPerMinute, limitOverride);
    String key =
        hash("identity", identityId + '\u0000' + endpoint.wireLabel() + '\u0000' + effectiveLimit);
    return consume(identityBuckets, key, effectiveLimit, clock.millis());
  }

  /** Checks the auth-failure bucket keyed by the effective peer address. */
  public synchronized RateLimitDecision tryAuthFailure(String effectivePeer) {
    if (isBlank(effectivePeer)) {
      return denyClosed(authFailurePerMinute, clock.millis());
    }
    String key = hash("auth-failure", effectivePeer);
    return consume(authFailureBuckets, key, authFailurePerMinute, clock.millis());
  }

  /** Removes buckets that have been idle for a full window. Returns the number removed. */
  public synchronized int prune() {
    long now = clock.millis();
    return prune(addressBuckets, now)
        + prune(identityBuckets, now)
        + prune(authFailureBuckets, now);
  }

  private RateLimitDecision consume(
      Map<String, Bucket> buckets, String key, int limit, long nowMillis) {
    Bucket bucket = buckets.get(key);
    if (bucket == null) {
      if (buckets.size() >= maxKeys) {
        Iterator<String> iterator = buckets.keySet().iterator();
        if (iterator.hasNext()) {
          iterator.next();
          iterator.remove();
        }
      }
      bucket = new Bucket(limit, nowMillis);
      buckets.put(key, bucket);
    }
    refill(bucket, nowMillis);
    int remaining;
    RateLimitDecision decision;
    if (bucket.tokens >= 1.0d) {
      bucket.tokens -= 1.0d;
      remaining = (int) Math.floor(bucket.tokens);
      decision =
          RateLimitDecision.allowed(limit, remaining, resetEpochSeconds(bucket, limit, nowMillis));
    } else {
      remaining = (int) Math.floor(bucket.tokens);
      double refillPerSecond = limit / 60.0d;
      long retry = (long) Math.ceil((1.0d - bucket.tokens) / refillPerSecond);
      decision =
          RateLimitDecision.denied(
              limit, remaining, resetEpochSeconds(bucket, limit, nowMillis), Math.max(1L, retry));
    }
    bucket.lastRefillMillis = nowMillis;
    return decision;
  }

  private static void refill(Bucket bucket, long nowMillis) {
    long elapsed = Math.max(0L, nowMillis - bucket.lastRefillMillis);
    double refillPerMillis = bucket.limit / (double) WINDOW_MILLIS;
    bucket.tokens = Math.min(bucket.limit, bucket.tokens + elapsed * refillPerMillis);
  }

  private static long resetEpochSeconds(Bucket bucket, int limit, long nowMillis) {
    double refillPerSecond = limit / 60.0d;
    long seconds = (long) Math.ceil((limit - bucket.tokens) / refillPerSecond);
    return Math.floorDiv(nowMillis, 1000L) + Math.max(1L, seconds);
  }

  private static int prune(Map<String, Bucket> buckets, long nowMillis) {
    int removed = 0;
    Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
    while (iterator.hasNext()) {
      Bucket bucket = iterator.next().getValue();
      if (nowMillis - bucket.lastRefillMillis >= WINDOW_MILLIS) {
        iterator.remove();
        removed++;
      }
    }
    return removed;
  }

  private static RateLimitDecision denyClosed(int limit, long nowMillis) {
    long reset = Math.floorDiv(nowMillis, 1000L) + 60L;
    return RateLimitDecision.denied(limit, 0, reset, 60L);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String hash(String domain, String value) {
    byte[] bytes = (domain + '\u0000' + value).getBytes(StandardCharsets.UTF_8);
    byte[] digest;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
    StringBuilder result = new StringBuilder(digest.length * 2);
    for (byte valueByte : digest) {
      result.append(String.format("%02x", Integer.valueOf(valueByte & 0xff)));
    }
    return result.toString();
  }

  private static void requirePositive(int value, String name) {
    if (value < 1 || value > 100_000) {
      throw new IllegalArgumentException(name + " must be between 1 and 100000");
    }
  }

  private static final class Bucket {

    private final int limit;
    private double tokens;
    private long lastRefillMillis;

    private Bucket(int limit, long nowMillis) {
      this.limit = limit;
      this.tokens = limit;
      this.lastRefillMillis = nowMillis;
    }
  }
}
