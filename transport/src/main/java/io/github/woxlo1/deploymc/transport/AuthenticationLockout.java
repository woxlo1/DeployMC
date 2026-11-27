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
 * Bounded in-memory token-mode brute-force lockout keyed by a hashed effective peer address.
 *
 * <p>Raw addresses never enter the map, logs, responses, or diagnostics. State is deliberately not
 * persisted and is cleared by a restart.
 */
public final class AuthenticationLockout {

  private static final int FAILURE_THRESHOLD = 5;
  private static final long BASE_BACKOFF_MILLIS = 5L * 60L * 1000L;
  private static final long MAX_BACKOFF_MILLIS = 24L * 60L * 60L * 1000L;
  private static final long IDLE_MILLIS = 60L * 1000L;

  private final int maxKeys;
  private final Clock clock;
  private final Map<String, State> states;

  /** Creates a lockout tracker with at most {@code maxKeys} hashed peers. */
  public AuthenticationLockout(int maxKeys, Clock clock) {
    if (maxKeys < 16 || maxKeys > 100_000) {
      throw new IllegalArgumentException("maxKeys must be between 16 and 100000");
    }
    this.maxKeys = maxKeys;
    this.clock = Objects.requireNonNull(clock, "clock");
    this.states = new LinkedHashMap<String, State>(maxKeys, 0.75f, true);
  }

  /** Returns true when the peer is currently locked. Expired locks are retained for backoff. */
  public synchronized boolean isLocked(String effectivePeer) {
    if (isBlank(effectivePeer)) {
      return true;
    }
    State state = states.get(hash(effectivePeer));
    return state != null && state.lockedUntil > clock.millis();
  }

  /** Records one failed credential verification. Calls during an active lockout are ignored. */
  public synchronized void recordFailure(String effectivePeer) {
    if (isBlank(effectivePeer)) {
      return;
    }
    long now = clock.millis();
    String key = hash(effectivePeer);
    State state = states.get(key);
    if (state != null && state.lockedUntil > now) {
      return;
    }
    if (state == null) {
      if (states.size() >= maxKeys) {
        Iterator<String> iterator = states.keySet().iterator();
        if (iterator.hasNext()) {
          iterator.next();
          iterator.remove();
        }
      }
      state = new State();
      states.put(key, state);
    }
    state.failures++;
    if (state.failures >= FAILURE_THRESHOLD) {
      state.lockedUntil = now + state.backoffMillis;
      state.backoffMillis = Math.min(MAX_BACKOFF_MILLIS, state.backoffMillis * 2L);
      state.failures = FAILURE_THRESHOLD - 1;
    }
    state.lastAccessMillis = Math.max(now, state.lockedUntil);
  }

  /** Clears failure count and backoff after a successful authentication. */
  public synchronized void recordSuccess(String effectivePeer) {
    if (isBlank(effectivePeer)) {
      return;
    }
    states.remove(hash(effectivePeer));
  }

  /** Retry-After seconds for an active lockout, or zero when the peer is not locked. */
  public synchronized long retryAfterSeconds(String effectivePeer) {
    if (isBlank(effectivePeer)) {
      return 0L;
    }
    State state = states.get(hash(effectivePeer));
    if (state == null || state.lockedUntil <= clock.millis()) {
      return 0L;
    }
    return Math.max(1L, (state.lockedUntil - clock.millis() + 999L) / 1000L);
  }

  /** Removes states idle for a full minute past any active lock. Returns the number removed. */
  public synchronized int prune() {
    long now = clock.millis();
    int removed = 0;
    Iterator<Map.Entry<String, State>> iterator = states.entrySet().iterator();
    while (iterator.hasNext()) {
      State state = iterator.next().getValue();
      if (now - state.lastAccessMillis >= IDLE_MILLIS) {
        iterator.remove();
        removed++;
      }
    }
    return removed;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String hash(String value) {
    byte[] bytes = ("lockout\u0000" + value).getBytes(StandardCharsets.UTF_8);
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

  private static final class State {

    private int failures;
    private long backoffMillis = BASE_BACKOFF_MILLIS;
    private long lockedUntil;
    private long lastAccessMillis;

    private State() {}
  }
}
