package io.github.woxlo1.deploymc.core.auth.cloudflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthenticationRequest;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Product-consumption tests for the WP-3.2 verifier.
 *
 * <p>Every committed JWT vector is presented to {@link CloudflareJwtVerifier} through a real
 * localhost HTTP JWKS server. Cache, rotation, ETag, stale-while-error and unavailable paths use
 * the same server's explicit control endpoints. These tests are server-free with respect to any
 * external Cloudflare service; they do not claim production compatibility.
 */
class CloudflareJwtVerifierTest {

  private static final String RESOURCE = "/jwt/";
  private static final String ISSUER = "https://team.example.test";
  private static final String AUDIENCE = "https://deploy.example.test";
  private static final Instant REFERENCE = Instant.parse("2026-11-22T00:00:00Z");
  private static final String PRIMARY_KID = "dmctest-key-primary-2026-11-22";

  private Process serverProcess;
  private int port;

  @BeforeEach
  void startMockServer() throws Exception {
    String python = pythonExecutable();
    assertNotNull(python, "Python 3 is required for the localhost JWKS fixture server");
    Path script = repositoryRoot().resolve("tests/security/jwt/mock_jwks_server.py");
    assertTrue(Files.isRegularFile(script), "Missing mock JWKS server: " + script);
    serverProcess = new ProcessBuilder(python, script.toString()).redirectErrorStream(true).start();
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.US_ASCII));
    String url = reader.readLine();
    assertNotNull(url, "Mock JWKS server did not print a URL");
    assertTrue(
        url.startsWith("http://127.0.0.1:"), "Mock JWKS server must bind to loopback: " + url);
    port = Integer.parseInt(url.substring("http://127.0.0.1:".length()).split("/", 2)[0]);
    assertTrue(port > 0);
  }

  @AfterEach
  void stopMockServer() throws Exception {
    if (serverProcess != null && serverProcess.isAlive()) {
      serverProcess.destroyForcibly();
      serverProcess.waitFor(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void everyCommittedVectorIsVerifiedByProductCode() throws Exception {
    Map<String, Map<String, Object>> byId = vectorsById();
    assertEquals(11, byId.size());

    Map<String, String> expected = new LinkedHashMap<String, String>();
    expected.put("valid", "accept");
    expected.put("expired", "auth.expired");
    expected.put("nbf-in-future", "auth.not-yet-valid");
    expected.put("wrong-aud", "auth.invalid");
    expected.put("wrong-iss", "auth.invalid");
    expected.put("alg-none", "auth.invalid");
    expected.put("alg-confusion", "auth.invalid");
    expected.put("tampered", "auth.invalid");
    expected.put("unknown-kid", "auth.invalid");
    expected.put("rotated-key", "accept");
    expected.put("retired-key", "accept");
    assertEquals(expected.keySet(), byId.keySet());

    for (Map.Entry<String, Map<String, Object>> entry : byId.entrySet()) {
      String id = entry.getKey();
      String token = (String) entry.getValue().get("token");
      CloudflareJwtVerifier current = verifier(new MutableClock(REFERENCE));
      AuthenticationResult result = verify(current, token, false);
      String actual = result.authenticated() ? "accept" : result.failure().code();
      assertEquals(
          expected.get(id),
          actual,
          "vector " + id + " reason=" + current.diagnostics().snapshot().lastReason());
      if ("valid".equals(id)) {
        assertEquals("fixture@example.test", result.identity().id());
        assertEquals("DeployMC Fixture User", result.identity().label());
        assertEquals("cloudflare-access", result.identity().method());
        assertFalse(result.identity().adminEquivalent());
        assertEquals("dmctest-valid", result.identity().claims().get("jti"));
      }
    }
  }

  @Test
  void unverifiedForwardedEmailHeaderNeverBecomesIdentity() throws Exception {
    String token = (String) vectorsById().get("valid").get("token");
    AuthenticationResult result = verify(verifier(new MutableClock(REFERENCE)), token, true);
    assertTrue(result.authenticated());
    assertEquals("fixture@example.test", result.identity().id());
    assertEquals("fixture@example.test", result.identity().claims().get("email"));
  }

  @Test
  void missingAndMultipleAssertionsFailClosed() throws Exception {
    CloudflareJwtVerifier verifier = verifier(new MutableClock(REFERENCE));
    AuthenticationRequest empty =
        new AuthenticationRequest(
            "req-empty", "GET", "/api/v1/server", Collections.<String, List<String>>emptyMap());
    assertEquals("auth.required", verifier.authenticate(empty).failure().code());

    List<String> two = new ArrayList<String>();
    two.add((String) vectorsById().get("valid").get("token"));
    two.add((String) vectorsById().get("valid").get("token"));
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put(CloudflareJwtVerifier.ASSERTION_HEADER, two);
    AuthenticationRequest duplicate =
        new AuthenticationRequest("req-dup", "GET", "/api/v1/server", headers);
    assertEquals("auth.invalid", verifier.authenticate(duplicate).failure().code());
  }

  @Test
  void etagRevalidationAvoidsRedundantDownloads() throws Exception {
    MutableClock clock = new MutableClock(REFERENCE);
    CloudflareJwtVerifier verifier = verifier(clock);
    String token = (String) vectorsById().get("valid").get("token");
    assertTrue(verify(verifier, token, false).authenticated());
    assertEquals(1, jwksRequestCount());

    clock.advanceSeconds(3_601L);
    assertTrue(verify(verifier, token, false).authenticated());
    assertEquals(2, jwksRequestCount(), "expired cache must trigger one revalidation");

    assertTrue(verify(verifier, token, false).authenticated());
    assertEquals(2, jwksRequestCount(), "a fresh cache must not re-download");
  }

  @Test
  void rotationReplacesRetiredKeysAfterRefresh() throws Exception {
    MutableClock clock = new MutableClock(REFERENCE);
    CloudflareJwtVerifier verifier = verifier(clock);
    Map<String, Map<String, Object>> byId = vectorsById();
    String primary = (String) byId.get("valid").get("token");
    String retired = (String) byId.get("retired-key").get("token");
    String replacement = (String) byId.get("rotated-key").get("token");

    assertTrue(verify(verifier, primary, false).authenticated());
    assertTrue(verify(verifier, retired, false).authenticated());

    control("/control/rotate");
    assertTrue(
        verify(verifier, primary, false).authenticated(),
        "a still-fresh cache may retain keys until its bounded refresh point");
    clock.advanceSeconds(3_601L);
    assertTrue(verify(verifier, replacement, false).authenticated());
    assertEquals("auth.invalid", verify(verifier, retired, false).failure().code());
    assertEquals("auth.invalid", verify(verifier, primary, false).failure().code());
  }

  @Test
  void staleKeysAreBoundedByMaximumStaleness() throws Exception {
    MutableClock clock = new MutableClock(REFERENCE);
    CloudflareJwtVerifier verifier = verifier(clock);
    String token = (String) vectorsById().get("valid").get("token");
    assertTrue(verify(verifier, token, false).authenticated());

    control("/control/unavailable?value=1");
    clock.advanceSeconds(3_601L);
    assertTrue(verify(verifier, token, false).authenticated(), "bounded stale keys remain usable");

    clock.advanceSeconds(700_000L);
    assertEquals(
        "auth.jwks-unavailable",
        verify(verifier, token, false).failure().code(),
        "hard staleness limit must fail closed");

    control("/control/unavailable?value=0");
    assertTrue(
        verify(verifier(new MutableClock(REFERENCE)), token, false).authenticated(),
        "a fresh verifier must recover after the endpoint becomes available");
  }

  @Test
  void unavailableEndpointWithoutCachedKeysFailsClosed() throws Exception {
    control("/control/unavailable?value=1");
    String token = (String) vectorsById().get("valid").get("token");
    assertEquals(
        "auth.jwks-unavailable",
        verify(verifier(new MutableClock(REFERENCE)), token, false).failure().code());
  }

  @Test
  void diagnosticsKeepOnlyClosedRejectionReasons() throws Exception {
    CloudflareJwtVerifier verifier = verifier(new MutableClock(REFERENCE));
    String token = (String) vectorsById().get("alg-confusion").get("token");
    AuthenticationResult result = verify(verifier, token, false);
    assertEquals("auth.invalid", result.failure().code());
    JwtDiagnosticsSnapshot snapshot = verifier.diagnostics().snapshot();
    assertEquals(1L, snapshot.countsByReason().get("UNSUPPORTED_ALGORITHM").longValue());
    assertEquals("UNSUPPORTED_ALGORITHM", snapshot.lastReason());
    assertEquals("auth.invalid", snapshot.lastErrorCode());
    assertFalse(snapshot.toString().contains(token));
    assertFalse(snapshot.countsByReason().containsKey("email"));
  }

  private CloudflareJwtVerifier verifier(MutableClock clock) {
    CloudflareConfig config =
        CloudflareConfig.fromTeamDomain(
            "team.example.test", Collections.singletonList(AUDIENCE), 60, 3_600, 604_800);
    assertEquals(ISSUER, config.issuer());
    return new CloudflareJwtVerifier(
        config,
        new HttpJwksClient("http://127.0.0.1:" + port + "/cdn-cgi/access/certs"),
        clock,
        new JwtDiagnostics(clock));
  }

  private static AuthenticationResult verify(
      CloudflareJwtVerifier verifier, String token, boolean forgedEmail) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put(CloudflareJwtVerifier.ASSERTION_HEADER, Collections.singletonList(token));
    if (forgedEmail) {
      headers.put(
          CloudflareJwtVerifier.FORWARDED_EMAIL_HEADER,
          Collections.singletonList("attacker@example.test"));
    }
    return verifier.authenticate(
        new AuthenticationRequest("req-vector", "GET", "/api/v1/server", headers));
  }

  private void control(String path) throws IOException {
    HttpURLConnection connection =
        (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
    try {
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5_000);
      connection.setReadTimeout(5_000);
      assertEquals(200, connection.getResponseCode(), path);
      readAll(connection.getInputStream());
    } finally {
      connection.disconnect();
    }
  }

  private int jwksRequestCount() throws IOException {
    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("http://127.0.0.1:" + port + "/control/requests").openConnection();
    try {
      connection.setRequestMethod("GET");
      Map<String, Object> state =
          JwtJson.asObject(
              JwtJson.parse(
                  new String(readAll(connection.getInputStream()), StandardCharsets.US_ASCII)));
      return (int) JwtJson.asInteger(state.get("jwks_requests"));
    } finally {
      connection.disconnect();
    }
  }

  private static Map<String, Map<String, Object>> vectorsById() throws IOException {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    String text = fixture("jwt-vectors.jsonl");
    for (String line : text.split("\n")) {
      if (!line.isEmpty()) {
        Map<String, Object> vector = JwtJson.asObject(JwtJson.parse(line));
        result.put((String) vector.get("id"), vector);
      }
    }
    return result;
  }

  private static String fixture(String name) throws IOException {
    String resource = RESOURCE + name;
    try (InputStream stream = CloudflareJwtVerifierTest.class.getResourceAsStream(resource)) {
      assertNotNull(stream, "Missing JWT fixture resource: " + resource);
      return new String(readAll(stream), StandardCharsets.US_ASCII);
    }
  }

  private static byte[] readAll(InputStream stream) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = stream.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private static String pythonExecutable() {
    for (String candidate : Arrays.asList("python", "python3")) {
      try {
        Process process =
            new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
        if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
          return candidate;
        }
        process.destroyForcibly();
      } catch (IOException exception) {
        continue;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  private static Path repositoryRoot() {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    for (int attempt = 0; attempt < 6; attempt++) {
      if (Files.isRegularFile(current.resolve("tests/security/jwt/mock_jwks_server.py"))
          && Files.isRegularFile(
              current.resolve(
                  "docs/development/DeployMC_Complete_Master_Development_Specification.md"))) {
        return current;
      }
      Path parent = current.getParent();
      if (parent == null) {
        break;
      }
      current = parent;
    }
    throw new AssertionError("Repository root not found from " + current);
  }

  private static final class MutableClock extends Clock {
    private final ZoneId zone;
    private Instant instant;

    MutableClock(Instant instant) {
      this(instant, ZoneOffset.UTC);
    }

    MutableClock(Instant instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId replacement) {
      return new MutableClock(instant, replacement);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
