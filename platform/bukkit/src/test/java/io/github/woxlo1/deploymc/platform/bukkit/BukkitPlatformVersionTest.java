package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BukkitPlatformVersionTest {

  @Test
  void parsesPaperVersionString() {
    BukkitPlatformVersion version =
        BukkitPlatformVersion.parse("git-Paper-196 (MC: 1.21.1)", "1.21.1");

    assertEquals("Paper", version.software());
    assertEquals("1.21.1", version.minecraftVersion());
    assertEquals("git-Paper-196 (MC: 1.21.1)", version.implementation());
  }

  @Test
  void detectsForksBeforePaper() {
    assertEquals(
        "Purpur", BukkitPlatformVersion.parse("git-Purpur-2062 (MC: 1.21.1)", null).software());
    assertEquals(
        "Pufferfish",
        BukkitPlatformVersion.parse("git-Pufferfish-100 (MC: 1.21.1)", null).software());
    assertEquals("Folia", BukkitPlatformVersion.parse("git-Folia-1 (MC: 1.21.1)", null).software());
  }

  @Test
  void extractsMinecraftVersionFromParentheses() {
    BukkitPlatformVersion version =
        BukkitPlatformVersion.parse("git-Spigot-1234 (MC: 1.20.6)", null);

    assertEquals("Spigot", version.software());
    assertEquals("1.20.6", version.minecraftVersion());
  }

  @Test
  void extractsLeadingMinecraftVersion() {
    BukkitPlatformVersion version = BukkitPlatformVersion.parse("1.8.8-R0.1-SNAPSHOT", "");

    assertEquals("1.8.8", version.minecraftVersion());
  }

  @Test
  void unknownSoftwareIsReportedHonestly() {
    assertEquals("Unknown", BukkitPlatformVersion.parse("custom-server", "1.21.1").software());
  }

  @Test
  void rejectsBlankBukkitVersion() {
    assertThrows(IllegalArgumentException.class, () -> BukkitPlatformVersion.parse(" ", "1.21.1"));
  }
}
