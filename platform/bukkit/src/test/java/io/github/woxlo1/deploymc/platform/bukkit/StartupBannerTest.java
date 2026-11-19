package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StartupBannerTest {

  private final LocalizedMessages messages = LocalizedMessages.of(Locale.ENGLISH);
  private final StartupBanner banner = new StartupBanner(messages);

  @Test
  void rendersIdentityBindAuthAndWarnings() {
    PlatformInfo info = new PlatformInfo("Paper", "1.21.1", "1.21.1", "21.0.1", "fake-os");
    StartupPlan plan = StartupPlan.defaults("0.1.0-SNAPSHOT");

    List<String> lines = banner.render(info, plan);

    assertEquals(6, lines.size());
    assertEquals("DeployMC 0.1.0-SNAPSHOT", lines.get(0));
    assertTrue(lines.get(1).contains("Paper"));
    assertTrue(lines.get(2).contains("21.0.1"));
    assertEquals("Bind: 127.0.0.1:8080", lines.get(3));
    assertEquals("Auth mode: token", lines.get(4));
    assertEquals(messages.format(MessageKeys.WARNING_NO_RESTART_SCRIPT), lines.get(5));
    for (String line : lines) {
      assertFalse(line.contains("{"), line);
      assertFalse(line.contains("}"), line);
    }
  }

  @Test
  void addsNonLoopbackWarningWhenApplicable() {
    PlatformInfo info = new PlatformInfo("Spigot", "1.8.8", "1.8.8", "1.8.0", "fake-os");
    StartupPlan plan = StartupPlan.of("0.0.0.0", 25565, true, "token", "0.1.0-SNAPSHOT");

    List<String> lines = banner.render(info, plan);

    assertEquals(7, lines.size());
    assertEquals(messages.format(MessageKeys.WARNING_NON_LOOPBACK), lines.get(5));
    assertEquals(messages.format(MessageKeys.WARNING_NO_RESTART_SCRIPT), lines.get(6));
  }

  @Test
  void bannerLinesAreImmutable() {
    List<String> lines =
        banner.render(
            new PlatformInfo("Paper", "1.21.1", "1.21.1", "21", "fake-os"),
            StartupPlan.defaults("0.1.0-SNAPSHOT"));

    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> lines.clear());
  }
}
