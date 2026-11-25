package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfig;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfigException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloudflareConfigFromConfigTest {

  @Test
  void createsVerifierConfigFromHostValues() throws Exception {
    CloudflareConfig config = CloudflareConfig.fromConfig(view("audience-fake", ""));

    assertEquals("https://example.cloudflareaccess.com", config.issuer());
    assertEquals(
        "https://example.cloudflareaccess.com/cdn-cgi/access/certs", config.jwksEndpoint());
    assertEquals(java.util.Collections.singleton("audience-fake"), config.audiences());
    assertEquals(30, config.clockSkewSeconds());
    assertEquals(1200, config.cacheMaxAgeSeconds());
    assertEquals(3600, config.maxStaleSeconds());
  }

  @Test
  void rejectsConfiguredSharedSecretHeaderName() {
    CloudflareConfigException failure =
        assertThrows(
            CloudflareConfigException.class,
            () -> CloudflareConfig.fromConfig(view("audience-fake", "CF-Access-Shared-Secret")));

    assertEquals(
        CloudflareConfigException.Reason.UNSUPPORTED_SHARED_SECRET_HEADER, failure.reason());
    assertEquals("unsupported-shared-secret-header", failure.code());
  }

  @Test
  void rejectsMissingCloudflareSection() {
    CloudflareConfigException failure =
        assertThrows(
            CloudflareConfigException.class,
            () -> CloudflareConfig.fromConfig(new ConfigView(new LinkedHashMap<String, Object>())));

    assertEquals(CloudflareConfigException.Reason.MISSING_SECTION, failure.reason());
  }

  private static ConfigView view(String audience, String sharedSecretHeader) {
    Map<String, Object> cloudflare = new LinkedHashMap<String, Object>();
    cloudflare.put("team-domain", "example.cloudflareaccess.com");
    cloudflare.put("audience", Arrays.asList(audience));
    cloudflare.put("clock-skew-seconds", 30);
    cloudflare.put("jwks-cache-max-age-seconds", 1200);
    cloudflare.put("jwks-max-stale-seconds", 3600);
    cloudflare.put("require-shared-secret-header", sharedSecretHeader);
    Map<String, Object> auth = new LinkedHashMap<String, Object>();
    auth.put("mode", "cloudflare");
    auth.put("cloudflare", cloudflare);
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("auth", auth);
    return new ConfigView(root);
  }
}
