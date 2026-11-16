package io.github.woxlo1.deploymc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlatformInfoTest {

  @Test
  void exposesAllFields() {
    PlatformInfo info = new PlatformInfo("Paper", "1.21.1-123", "1.21.1", "21.0.11", "Windows 11");

    assertEquals("Paper", info.softwareName());
    assertEquals("1.21.1-123", info.softwareVersion());
    assertEquals("1.21.1", info.minecraftVersion());
    assertEquals("21.0.11", info.javaVersion());
    assertEquals("Windows 11", info.operatingSystem());
  }

  @Test
  void rejectsNullAndBlankValues() {
    assertThrows(
        NullPointerException.class, () -> new PlatformInfo(null, "1", "1.21.1", "21", "Windows"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlatformInfo("Paper", " ", "1.21.1", "21", "Windows"));
  }

  @Test
  void equalityUsesAllFields() {
    PlatformInfo first = new PlatformInfo("Paper", "1", "1.21.1", "21", "Windows");
    PlatformInfo second = new PlatformInfo("Paper", "1", "1.21.1", "21", "Windows");
    PlatformInfo third = new PlatformInfo("Spigot", "1", "1.21.1", "21", "Windows");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals(false, first.equals(third));
  }
}
