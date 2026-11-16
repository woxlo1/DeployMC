package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpBindConfigTest {

  @Test
  void acceptsLoopbackByDefault() {
    HttpBindConfig config = HttpBindConfig.of("127.0.0.1", 8080, false);

    assertEquals("127.0.0.1", config.address());
    assertEquals(8080, config.port());
    assertTrue(config.isLoopbackOnly());
    assertEquals("127.0.0.1:8080", config.describe());
  }

  @Test
  void recognizesIpv6AndIpv4Loopback() {
    assertTrue(HttpBindConfig.isLoopbackAddress("127.0.0.1"));
    assertTrue(HttpBindConfig.isLoopbackAddress("127.12.34.56"));
    assertTrue(HttpBindConfig.isLoopbackAddress("::1"));
    assertFalse(HttpBindConfig.isLoopbackAddress("0.0.0.0"));
    assertFalse(HttpBindConfig.isLoopbackAddress("192.0.2.10"));
  }

  @Test
  void refusesWildcardBindUnlessExplicitlyAllowed() {
    assertThrows(IllegalArgumentException.class, () -> HttpBindConfig.of("0.0.0.0", 8080, false));

    HttpBindConfig config = HttpBindConfig.of("0.0.0.0", 8080, true);
    assertTrue(config.allowNonLoopback());
    assertFalse(config.isLoopbackOnly());
  }

  @Test
  void rejectsInvalidPorts() {
    assertThrows(IllegalArgumentException.class, () -> HttpBindConfig.of("127.0.0.1", -1, false));
    assertThrows(
        IllegalArgumentException.class, () -> HttpBindConfig.of("127.0.0.1", 65536, false));
  }

  @Test
  void rejectsBlankAndControlCharacterAddresses() {
    assertThrows(IllegalArgumentException.class, () -> HttpBindConfig.of("  ", 8080, false));
    assertThrows(
        IllegalArgumentException.class, () -> HttpBindConfig.of("127.0.0.1\n", 8080, false));
  }
}
