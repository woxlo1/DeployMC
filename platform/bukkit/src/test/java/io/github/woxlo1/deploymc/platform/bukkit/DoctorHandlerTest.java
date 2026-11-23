package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free coverage for the authenticated doctor route. */
class DoctorHandlerTest {

  private static final String REQUEST_ID = "doctor-request";
  private static final Identity ADMIN =
      new Identity("admin-id", "admin", "token", true, Collections.<String>emptyList());
  private static final Identity LIMITED =
      new Identity("limited-id", "observer", "token", false, Collections.<String>emptyList());

  @TempDir Path temporaryDirectory;

  private DoctorHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    Path configFile = temporaryDirectory.resolve("config.yml");
    DefaultConfigWriter.writeIfMissing(configFile);
    ConfigManager manager = new ConfigManager(configFile);
    ConfigView view = manager.load();
    StartupPlan plan = StartupPlan.fromConfig(view, "0.1.0-test");
    DoctorRuntime runtime =
        new DoctorRuntime(
            manager,
            plan,
            new PlatformInfo("Paper", "1.21.1", "1.21.1", "21", "test-os"),
            PlatformCapabilities.empty(),
            Locale.ENGLISH,
            "en",
            null,
            LocalizedMessages.of(Locale.ENGLISH),
            Clock.systemUTC());
    handler = new DoctorHandler(runtime, LocalizedMessages.of(Locale.ENGLISH));
  }

  @Test
  void requiresABearerIdentity() throws Exception {
    HttpResponse response = handler.handle(request("GET", DoctorHandler.PATH, null));

    assertEquals(401, response.status());
    assertTrue(body(response).contains("\"code\":\"auth.required\""));
    assertEquals("Bearer", header(response, "www-authenticate"));
    assertCommonHeaders(response);
  }

  @Test
  void forbidsIdentitiesThatAreNotAdminEquivalent() throws Exception {
    HttpResponse response = handler.handle(request("GET", DoctorHandler.PATH, LIMITED));

    assertEquals(403, response.status());
    assertTrue(body(response).contains("\"code\":\"doctor.forbidden\""));
    assertCommonHeaders(response);
  }

  @Test
  void returnsTheRedactedThirteenCheckReportToAdminEquivalentIdentities() throws Exception {
    HttpResponse response = handler.handle(request("GET", DoctorHandler.PATH, ADMIN));
    String json = body(response);

    assertEquals(200, response.status());
    assertTrue(json.contains("\"redacted\":true"));
    assertTrue(json.contains("\"scope\":\"limited\""));
    assertEquals(13, count(json, "\"id\":"));
    assertFalse(json.contains("\"evidence\""));
    assertCommonHeaders(response);
  }

  @Test
  void rejectsOtherPathsAndMethodsWithoutReachingTheReport() throws Exception {
    HttpResponse wrongPath = handler.handle(request("GET", "/api/v1/server/doctor/other", ADMIN));
    assertEquals(404, wrongPath.status());
    assertTrue(body(wrongPath).contains("\"code\":\"not_found\""));
    assertCommonHeaders(wrongPath);

    HttpResponse wrongMethod = handler.handle(request("POST", DoctorHandler.PATH, ADMIN));
    assertEquals(405, wrongMethod.status());
    assertTrue(body(wrongMethod).contains("\"code\":\"method_not_allowed\""));
    assertEquals("GET", header(wrongMethod, "allow"));
    assertCommonHeaders(wrongMethod);
  }

  private static HttpRequest request(String method, String path, Identity identity) {
    return new HttpRequest(
        REQUEST_ID,
        method,
        path,
        "",
        Collections.<String, List<String>>emptyMap(),
        new byte[0],
        identity);
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static String header(HttpResponse response, String name) {
    List<String> values = response.headers().get(name.toLowerCase(Locale.ROOT));
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static void assertCommonHeaders(HttpResponse response) {
    assertEquals(REQUEST_ID, header(response, "x-request-id"));
    assertEquals("no-store", header(response, "cache-control"));
    assertNotNull(header(response, "content-type"));
  }

  private static int count(String value, String fragment) {
    int count = 0;
    int index = value.indexOf(fragment);
    while (index >= 0) {
      count++;
      index = value.indexOf(fragment, index + fragment.length());
    }
    return count;
  }
}
