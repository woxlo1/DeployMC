package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenProvisionerTest {

  @TempDir Path tempDir;

  @Test
  void storesOnlyHashAndTokenAuthenticates() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    Instant now = Instant.parse("2026-11-19T00:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    TokenProvisioner provisioner = new TokenProvisioner(store, clock, new SecureRandom());

    CreatedToken created = provisioner.create("console \"one\"", Duration.ofDays(30));
    assertTrue(created.token().startsWith("dmc_"));
    assertEquals(now.plus(Duration.ofDays(30)), created.record().expiresAt());
    assertFalse(created.toString().contains(created.token()));

    String fileContent =
        new String(
            Files.readAllBytes(directory.resolve(created.record().id() + ".json")),
            StandardCharsets.UTF_8);
    assertFalse(fileContent.contains(created.token()));

    TokenStore reloaded = TokenStore.load(directory);
    AuthenticationResult result =
        new TokenAuthenticator(reloaded, clock).authenticate(TestRequests.bearer(created.token()));
    assertTrue(result.authenticated());
    assertEquals(created.record().id(), result.identity().id());
    assertTrue(fileContent.contains("secretHash"));
    assertNotNull(created.record().secretHash());
  }

  @Test
  void createsTokenWithoutExpiryWhenDurationIsNull() throws Exception {
    TokenStore store = TokenStore.load(tempDir.resolve("tokens.d"));
    TokenProvisioner provisioner =
        new TokenProvisioner(store, Clock.systemUTC(), new SecureRandom());

    CreatedToken created = provisioner.create("no expiry", null);
    assertEquals(null, created.record().expiresAt());
  }
}
