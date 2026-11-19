package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenAuthenticatorTest {

  @TempDir Path tempDir;

  @Test
  void reportsMissingMalformedAndUnknownCredentials() throws Exception {
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    TokenAuthenticator authenticator = new TokenAuthenticator(store, Clock.systemUTC());

    assertEquals(AuthenticationFailure.REQUIRED, authenticator.authenticate(null).failure());
    assertEquals(AuthenticationFailure.REQUIRED, authenticator.authenticate("   ").failure());
    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator.authenticate("Basic abcdefghijklmnop").failure());
    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator.authenticate("Bearer dmc_unknown_token_value_1234").failure());
    assertFalse(authenticator.authenticate("Bearer dmc_unknown_token_value_1234").authenticated());
  }

  @Test
  void acceptsValidBearerToken() throws Exception {
    String token = "dmc_valid_token_value_1234567890";
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    store.write(new TokenRecord("t-valid", "admin", TestHashes.sha256(token), null));
    TokenAuthenticator authenticator = new TokenAuthenticator(store, Clock.systemUTC());

    AuthenticationResult result = authenticator.authenticate("bearer " + token);
    assertTrue(result.authenticated());
    assertEquals("t-valid", result.identity().id());
    assertEquals("admin", result.identity().label());
    assertEquals("token", result.identity().method());
    assertTrue(result.identity().adminEquivalent());
  }

  @Test
  void reportsExpiredToken() throws Exception {
    String token = "dmc_expired_token_value_1234567890";
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    store.write(
        new TokenRecord(
            "t-expired",
            "old admin",
            TestHashes.sha256(token),
            Instant.parse("2020-01-01T00:00:00Z")));
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    TokenAuthenticator authenticator = new TokenAuthenticator(store, clock);

    assertEquals(
        AuthenticationFailure.EXPIRED, authenticator.authenticate("Bearer " + token).failure());
  }

  @Test
  void rejectsControlCharactersInCredential() throws Exception {
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    TokenAuthenticator authenticator = new TokenAuthenticator(store, Clock.systemUTC());

    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator.authenticate("Bearer dmc_token\r\nX-Evil: 1").failure());
  }
}
