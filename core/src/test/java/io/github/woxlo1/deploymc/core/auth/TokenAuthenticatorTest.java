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

    assertEquals(
        AuthenticationFailure.REQUIRED,
        authenticator
            .authenticate(
                new AuthenticationRequest(
                    "test-request",
                    "GET",
                    "/api/v1/server",
                    java.util.Collections.<String, java.util.List<String>>emptyMap()))
            .failure());
    assertEquals(
        AuthenticationFailure.REQUIRED,
        authenticator.authenticate(TestRequests.header("Authorization", "   ")).failure());
    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator
            .authenticate(TestRequests.header("Authorization", "Basic abcdefghijklmnop"))
            .failure());
    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator.authenticate(TestRequests.bearer("dmc_unknown_token_value_1234")).failure());
    assertFalse(
        authenticator
            .authenticate(TestRequests.bearer("dmc_unknown_token_value_1234"))
            .authenticated());
  }

  @Test
  void acceptsValidBearerTokenWithBoundedClaims() throws Exception {
    String token = "dmc_valid_token_value_1234567890";
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    store.write(new TokenRecord("t-valid", "admin", TestHashes.sha256(token), null));
    TokenAuthenticator authenticator = new TokenAuthenticator(store, Clock.systemUTC());

    AuthenticationResult result = authenticator.authenticate(TestRequests.bearer(token));
    assertTrue(result.authenticated());
    assertEquals("t-valid", result.identity().id());
    assertEquals("admin", result.identity().label());
    assertEquals("admin", result.identity().displayName());
    assertEquals("token", result.identity().method());
    assertTrue(result.identity().adminEquivalent());
    assertEquals("t-valid", result.identity().claims().get("token.id"));
    assertEquals("token", result.identity().claims().get("auth.method"));
    assertFalse(result.identity().toString().contains(token));
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
        AuthenticationFailure.EXPIRED,
        authenticator.authenticate(TestRequests.bearer(token)).failure());
  }

  @Test
  void rejectsControlCharactersInCredential() throws Exception {
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    TokenAuthenticator authenticator = new TokenAuthenticator(store, Clock.systemUTC());

    assertEquals(
        AuthenticationFailure.INVALID,
        authenticator
            .authenticate(TestRequests.header("Authorization", "Bearer dmc_token\r\nX-Evil: 1"))
            .failure());
  }

  @Test
  void ignoresClientSuppliedIdentityHeaders() throws Exception {
    String token = "dmc_valid_token_value_1234567890";
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    store.write(new TokenRecord("t-valid", "admin", TestHashes.sha256(token), null));
    TokenAuthenticator authenticator = new TokenAuthenticator(store, Clock.systemUTC());
    java.util.Map<String, java.util.List<String>> headers =
        new java.util.LinkedHashMap<String, java.util.List<String>>();
    headers.put("authorization", java.util.Collections.singletonList("Bearer " + token));
    headers.put("X-Identity", java.util.Collections.singletonList("attacker"));
    headers.put(
        "Cf-Access-Authenticated-User-Email",
        java.util.Collections.singletonList("evil@example.invalid"));

    AuthenticationResult result =
        authenticator.authenticate(
            new AuthenticationRequest("test-request", "GET", "/api/v1/server", headers));

    assertTrue(result.authenticated());
    assertEquals("t-valid", result.identity().id());
    assertFalse(result.identity().claims().containsKey("X-Identity"));
    assertFalse(result.identity().claims().containsKey("email"));
  }
}
