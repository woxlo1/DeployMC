package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyColorFormatterTest {

  @Test
  void translatesValidLegacyCodes() {
    assertEquals("§aGreen §lbold §r", LegacyColorFormatter.translate("&aGreen &lbold &r"));
  }

  @Test
  void preservesSectionCodesAndUnknownAmpersandSequences() {
    assertEquals("§b keep &z & &§a", LegacyColorFormatter.translate("§b keep &z & &&a"));
  }

  @Test
  void leavesShortMessagesUnchanged() {
    assertEquals("", LegacyColorFormatter.translate(""));
    assertEquals("&", LegacyColorFormatter.translate("&"));
  }
}
