package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class OriginValidatorTest {

  @Test
  void allowsExactExplicitOriginWithNormalizedDefaultPort() {
    OriginValidator validator =
        new OriginValidator(Collections.singletonList("https://app.example.test"), () -> 8443);

    assertTrue(validator.allows("https://app.example.test"));
    assertTrue(validator.allows("HTTPS://APP.EXAMPLE.TEST:443"));
    assertFalse(validator.allows("http://app.example.test"));
    assertFalse(validator.allows("https://app.example.test.evil.test"));
  }

  @Test
  void allowsImplicitLoopbackOriginsForBindPortOnly() {
    OriginValidator validator = new OriginValidator(Collections.<String>emptyList(), () -> 8080);

    assertTrue(validator.allows("http://127.0.0.1:8080"));
    assertTrue(validator.allows("http://localhost:8080"));
    assertTrue(validator.allows("http://[::1]:8080"));
    assertFalse(validator.allows("http://127.0.0.2:8080"));
    assertFalse(validator.allows("http://127.0.0.1:8081"));
  }

  @Test
  void upgradeRequiresExactlyOneAllowlistedOrigin() {
    OriginValidator validator = new OriginValidator(Collections.<String>emptyList(), () -> 8080);

    assertTrue(validator.allowsUpgrade(Collections.singletonList("http://localhost:8080")));
    assertFalse(validator.allowsUpgrade(Collections.<String>emptyList()));
    assertFalse(
        validator.allowsUpgrade(Arrays.asList("http://localhost:8080", "http://localhost:8080")));
    assertFalse(validator.allowsUpgrade(Collections.singletonList("http://localhost:8080/")));
  }

  @Test
  void rejectsWildcardUserinfoAndQueryOrigins() {
    OriginValidator validator = new OriginValidator(Collections.<String>emptyList(), () -> 8080);

    assertFalse(validator.allows("http://*.example.test"));
    assertFalse(validator.allows("http://user@localhost:8080"));
    assertFalse(validator.allows("http://localhost:8080?next=/"));
    assertFalse(validator.allows(null));
  }

  @Test
  void invalidExplicitOriginFailsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OriginValidator(Collections.singletonList("http://localhost:8080/*"), () -> 8080));
  }
}
