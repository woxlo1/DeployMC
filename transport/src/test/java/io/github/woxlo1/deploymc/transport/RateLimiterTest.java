package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  @Test
  void tokenBucketsRefillContinuously() {
    MutableClock clock = new MutableClock(1_000_000L);
    RateLimiter limiter = new RateLimiter(60, 60, 16, clock);

    for (int index = 0; index < 60; index++) {
      assertTrue(limiter.tryIdentity("identity", EndpointClass.SERVER).allowed());
    }
    assertFalse(limiter.tryIdentity("identity", EndpointClass.SERVER).allowed());

    clock.advance(30_000L);
    int refilled = 0;
    while (refilled < 100 && limiter.tryIdentity("identity", EndpointClass.SERVER).allowed()) {
      refilled++;
    }
    assertTrue(refilled >= 29 && refilled <= 31, "unexpected half-window refill: " + refilled);

    clock.advance(30_000L);
    int refilledAgain = 0;
    while (refilledAgain < 100 && limiter.tryIdentity("identity", EndpointClass.SERVER).allowed()) {
      refilledAgain++;
    }
    assertTrue(
        refilledAgain >= 29 && refilledAgain <= 31,
        "unexpected second half-window refill: " + refilledAgain);
  }

  @Test
  void identityBucketsAreSeparateForIdentityAndEndpointClass() {
    RateLimiter limiter = new RateLimiter(1, 1, 16, new MutableClock(0L));

    assertTrue(limiter.tryIdentity("identity-a", EndpointClass.SERVER).allowed());
    assertFalse(limiter.tryIdentity("identity-a", EndpointClass.SERVER).allowed());
    assertTrue(limiter.tryIdentity("identity-a", EndpointClass.FILES).allowed());
    assertTrue(limiter.tryIdentity("identity-b", EndpointClass.SERVER).allowed());
  }

  @Test
  void authFailureBucketsAreSeparateByAddress() {
    RateLimiter limiter = new RateLimiter(1, 1, 16, new MutableClock(0L));

    assertTrue(limiter.tryAuthFailure("192.0.2.10").allowed());
    assertFalse(limiter.tryAuthFailure("192.0.2.10").allowed());
    assertTrue(limiter.tryAuthFailure("192.0.2.11").allowed());
  }

  @Test
  void bucketKeysAreBoundedWithLruEviction() {
    RateLimiter limiter = new RateLimiter(1, 1, 16, new MutableClock(0L));
    for (int index = 0; index < 16; index++) {
      assertTrue(limiter.tryIdentity("identity-" + index, EndpointClass.SERVER).allowed());
    }

    assertTrue(limiter.tryIdentity("identity-16", EndpointClass.SERVER).allowed());
    assertFalse(
        limiter.tryIdentity("identity-1", EndpointClass.SERVER).allowed(),
        "a recent bucket must not be evicted");
    assertTrue(
        limiter.tryIdentity("identity-0", EndpointClass.SERVER).allowed(),
        "the least recently used bucket should have been evicted");
  }

  @Test
  void pruneRemovesBucketsIdleForAFullWindow() {
    MutableClock clock = new MutableClock(0L);
    RateLimiter limiter = new RateLimiter(1, 1, 16, clock);
    assertTrue(limiter.tryIdentity("identity", EndpointClass.SERVER).allowed());
    assertFalse(limiter.tryIdentity("identity", EndpointClass.SERVER).allowed());

    clock.advance(60_000L);
    assertEquals(1, limiter.prune());
    assertTrue(limiter.tryIdentity("identity", EndpointClass.SERVER).allowed());
  }

  @Test
  void blankKeysAreDeniedClosed() {
    RateLimiter limiter = new RateLimiter(1, 1, 16, new MutableClock(0L));

    assertFalse(limiter.tryIdentity(null, EndpointClass.SERVER).allowed());
    assertFalse(limiter.tryIdentity("  ", EndpointClass.SERVER).allowed());
    assertFalse(limiter.tryIdentity("identity", null).allowed());
    assertFalse(limiter.tryAuthFailure(" ").allowed());
    assertEquals(60L, limiter.tryAuthFailure(null).retryAfterSeconds());
  }

  @Test
  void rejectedRequestsCarryAllRateLimitHeaders() {
    RateLimitDecision decision = RateLimitDecision.denied(300, 0, 1_800L, 5L);

    HttpResponse response =
        TransportProblems.rateLimited(decision, "req-117", TransportMessages.of(Locale.ENGLISH));

    assertEquals(429, response.status());
    assertEquals("5", firstHeader(response, TransportProblems.RETRY_AFTER));
    assertEquals("300", firstHeader(response, TransportProblems.RATE_LIMIT_LIMIT));
    assertEquals("0", firstHeader(response, TransportProblems.RATE_LIMIT_REMAINING));
    assertEquals("1800", firstHeader(response, TransportProblems.RATE_LIMIT_RESET));
    assertEquals("req-117", firstHeader(response, TransportProblems.REQUEST_ID));
    String body = new String(response.body(), StandardCharsets.UTF_8);
    assertTrue(body.contains("auth.rate-limited"));
    assertTrue(body.contains("\"retryable\":true"));
  }

  @Test
  void addressBucketIsIndependentAndDeniesMissingPeer() {
    MutableClock clock = new MutableClock(1_000_000L);
    RateLimiter limiter = new RateLimiter(1, 60, 60, 16, clock);

    assertTrue(limiter.tryAddress("198.51.100.7").allowed());
    assertFalse(limiter.tryAddress("198.51.100.7").allowed());
    assertTrue(limiter.tryAddress("198.51.100.8").allowed());
    assertFalse(limiter.tryAddress(null).allowed());
  }

  @Test
  void mostConstrainingCombinationUsesLowestRemainingAndShortestRetry() {
    RateLimitDecision address = RateLimitDecision.allowed(300, 12, 1_800L);
    RateLimitDecision identity = RateLimitDecision.allowed(50, 3, 1_700L);
    RateLimitDecision combined = RateLimitDecision.mostConstraining(address, identity);

    assertTrue(combined.allowed());
    assertEquals(50, combined.limit());
    assertEquals(3, combined.remaining());
    assertEquals(1_700L, combined.resetEpochSeconds());

    RateLimitDecision denied = RateLimitDecision.denied(50, 0, 1_700L, 2L);
    RateLimitDecision combinedDenial = RateLimitDecision.mostConstraining(address, denied);
    assertFalse(combinedDenial.allowed());
    assertEquals(0, combinedDenial.remaining());
    assertEquals(2L, combinedDenial.retryAfterSeconds());
  }

  private static String firstHeader(HttpResponse response, String name) {
    return response.headers().get(name.toLowerCase(java.util.Locale.ROOT)).get(0);
  }

  private static final class MutableClock extends Clock {

    private final ZoneId zone = ZoneOffset.UTC;
    private long millis;

    private MutableClock(long millis) {
      this.millis = millis;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis);
    }

    @Override
    public long millis() {
      return millis;
    }

    private void advance(long delta) {
      millis += delta;
    }
  }
}
