package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthenticationLockoutTest {

  @Test
  void locksAfterFiveFailuresAndIgnoresFailuresDuringLockout() {
    MutableClock clock = new MutableClock(1_000_000L);
    AuthenticationLockout lockout = new AuthenticationLockout(16, clock);

    for (int index = 0; index < 5; index++) {
      assertFalse(lockout.isLocked("198.51.100.7"));
      lockout.recordFailure("198.51.100.7");
    }
    assertTrue(lockout.isLocked("198.51.100.7"));
    assertEquals(300L, lockout.retryAfterSeconds("198.51.100.7"));

    clock.advance(60_000L);
    lockout.recordFailure("198.51.100.7");
    assertEquals(240L, lockout.retryAfterSeconds("198.51.100.7"));
  }

  @Test
  void doublesBackoffAfterExpiryAndSuccessClearsState() {
    MutableClock clock = new MutableClock(1_000_000L);
    AuthenticationLockout lockout = new AuthenticationLockout(16, clock);

    for (int index = 0; index < 5; index++) {
      lockout.recordFailure("198.51.100.7");
    }
    clock.advance(300_001L);
    assertFalse(lockout.isLocked("198.51.100.7"));
    lockout.recordFailure("198.51.100.7");
    assertTrue(lockout.isLocked("198.51.100.7"));
    assertEquals(600L, lockout.retryAfterSeconds("198.51.100.7"));

    lockout.recordSuccess("198.51.100.7");
    assertFalse(lockout.isLocked("198.51.100.7"));
    assertEquals(0L, lockout.retryAfterSeconds("198.51.100.7"));
  }

  @Test
  void prunesIdleStateAndDeniesMissingPeer() {
    MutableClock clock = new MutableClock(1_000_000L);
    AuthenticationLockout lockout = new AuthenticationLockout(16, clock);

    assertTrue(lockout.isLocked(null));
    lockout.recordFailure("198.51.100.7");
    assertEquals(0, lockout.prune());
    clock.advance(60_001L);
    assertEquals(1, lockout.prune());
    assertFalse(lockout.isLocked("198.51.100.7"));
  }

  private static final class MutableClock extends Clock {

    private long epochMillis;

    private MutableClock(long epochMillis) {
      this.epochMillis = epochMillis;
    }

    private void advance(long delta) {
      epochMillis += delta;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(epochMillis);
    }
  }
}
