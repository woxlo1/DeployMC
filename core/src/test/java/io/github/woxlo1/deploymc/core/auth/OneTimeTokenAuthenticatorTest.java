package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class OneTimeTokenAuthenticatorTest {

  private static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");

  @Test
  void issuesHighEntropyDmcTokenWithBoundedLifetime() {
    OneTimeToken token =
        OneTimeToken.issue(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));

    assertTrue(token.secret().startsWith("dmc_"));
    assertTrue(token.secret().length() >= 43);
    assertEquals(NOW.plusSeconds(900L), token.expiresAt());
    assertFalse(token.toString().contains(token.secret()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OneTimeToken.issue(
                new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(61)));
  }

  @Test
  void acceptsExactlyOneRedemptionAndThenReportsConsumed() {
    OneTimeToken token =
        OneTimeToken.issue(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    OneTimeTokenAuthenticator authenticator =
        new OneTimeTokenAuthenticator(token, Clock.fixed(NOW, ZoneOffset.UTC), 3);

    AuthenticationResult first = authenticator.authenticate(TestRequests.bearer(token.secret()));
    assertTrue(first.authenticated());
    assertEquals("local-onetime", first.identity().method());
    assertTrue(first.identity().adminEquivalent());
    assertTrue(first.identity().claims().isEmpty());
    assertFalse(first.identity().toString().contains(token.secret()));
    assertEquals(
        AuthenticationFailure.ONE_TIME_CONSUMED,
        authenticator.authenticate(TestRequests.bearer(token.secret())).failure());
  }

  @Test
  void burnsTokenAtAttemptLimitWithoutAcceptingLaterCredential() {
    OneTimeToken token =
        OneTimeToken.issue(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    OneTimeTokenAuthenticator authenticator =
        new OneTimeTokenAuthenticator(token, Clock.fixed(NOW, ZoneOffset.UTC), 3);

    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator.authenticate(TestRequests.bearer("dmc_wrong_one")).failure());
    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator.authenticate(TestRequests.header("Authorization", "Basic abc")).failure());
    assertEquals(
        AuthenticationFailure.ONE_TIME_CONSUMED,
        authenticator.authenticate(TestRequests.bearer("dmc_wrong_two")).failure());
    assertEquals(
        AuthenticationFailure.ONE_TIME_CONSUMED,
        authenticator.authenticate(TestRequests.bearer(token.secret())).failure());
  }

  @Test
  void missingCredentialDoesNotConsumeAnAttempt() {
    OneTimeToken token =
        OneTimeToken.issue(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    OneTimeTokenAuthenticator authenticator =
        new OneTimeTokenAuthenticator(token, Clock.fixed(NOW, ZoneOffset.UTC), 1);

    AuthenticationRequest missing =
        new AuthenticationRequest(
            "test-request",
            "GET",
            "/api/v1/server",
            Collections.<String, java.util.List<String>>emptyMap());
    assertEquals(AuthenticationFailure.REQUIRED, authenticator.authenticate(missing).failure());
    assertTrue(authenticator.authenticate(TestRequests.bearer(token.secret())).authenticated());
  }

  @Test
  void reportsExpiryWithClosedCode() {
    OneTimeToken token =
        OneTimeToken.issue(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    Clock later = Clock.fixed(NOW.plusSeconds(301L), ZoneOffset.UTC);
    OneTimeTokenAuthenticator authenticator = new OneTimeTokenAuthenticator(token, later, 5);

    assertEquals(
        AuthenticationFailure.ONE_TIME_EXPIRED,
        authenticator.authenticate(TestRequests.bearer(token.secret())).failure());
    assertEquals("auth.one-time-expired", AuthenticationFailure.ONE_TIME_EXPIRED.code());
  }
}
