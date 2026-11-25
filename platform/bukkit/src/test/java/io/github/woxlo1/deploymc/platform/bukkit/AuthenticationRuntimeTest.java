package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationRequest;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthenticationRuntimeTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void cloudflareRuntimeVerifiesOnlyTheJwtAssertion() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC);
    CloudflareConfig config =
        CloudflareConfig.of(
            "https://example.cloudflareaccess.com",
            Collections.singleton("audience-fake"),
            60,
            3600,
            86400);
    AuthenticationRuntime runtime = AuthenticationRuntime.createCloudflare(config, clock);

    assertFalse(runtime.tokenProvisioningEnabled());
    assertNotNull(runtime.jwtDiagnostics());
    Map<String, java.util.List<String>> headers =
        new java.util.LinkedHashMap<String, java.util.List<String>>();
    headers.put(
        "cf-access-authenticated-user-email",
        Collections.singletonList("attacker@example.invalid"));
    AuthenticationResult result =
        runtime
            .authenticator()
            .authenticate(new AuthenticationRequest("r-1", "GET", "/api/v1/server", headers));

    assertFalse(result.authenticated());
    assertEquals(AuthenticationFailure.REQUIRED, result.failure());
    assertEquals(1L, runtime.jwtDiagnosticsSnapshot().countsByReason().get("MISSING_ASSERTION"));
  }

  @Test
  void tokenRuntimeKeepsProvisioningEnabled() throws Exception {
    AuthenticationRuntime runtime =
        AuthenticationRuntime.create(
            TokenStore.load(temporaryDirectory.resolve("tokens.d")),
            Clock.systemUTC(),
            new java.security.SecureRandom(),
            java.time.Duration.ofDays(1));

    assertTrue(runtime.tokenProvisioningEnabled());
    assertEquals(null, runtime.jwtDiagnostics());
    assertEquals(null, runtime.jwtDiagnosticsSnapshot());
  }
}
