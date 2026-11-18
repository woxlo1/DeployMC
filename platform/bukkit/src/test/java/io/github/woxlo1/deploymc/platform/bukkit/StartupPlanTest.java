package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class StartupPlanTest {

  @Test
  void defaultsUseLoopbackAndCloudflareMode() {
    StartupPlan plan = StartupPlan.defaults("0.1.0-SNAPSHOT");

    assertEquals("127.0.0.1", plan.bind().address());
    assertEquals(8080, plan.bind().port());
    assertFalse(plan.bind().allowNonLoopback());
    assertEquals("cloudflare", plan.authMode());
    assertEquals(
        Collections.singletonList(MessageKeys.WARNING_NO_RESTART_SCRIPT), plan.warningKeys());
  }

  @Test
  void nonLoopbackBindAddsSecurityWarning() {
    StartupPlan plan = StartupPlan.of("0.0.0.0", 8080, true, "token", "0.1.0-SNAPSHOT");

    assertEquals(
        Arrays.asList(MessageKeys.WARNING_NON_LOOPBACK, MessageKeys.WARNING_NO_RESTART_SCRIPT),
        plan.warningKeys());
  }

  @Test
  void unsupportedAuthModeFailsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StartupPlan.of("127.0.0.1", 8080, false, "none", "0.1.0-SNAPSHOT"));
  }

  @Test
  void unsafeBindStillFailsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StartupPlan.of("0.0.0.0", 8080, false, "token", "0.1.0-SNAPSHOT"));
  }

  @Test
  void rejectsEmptyPluginVersion() {
    assertThrows(IllegalArgumentException.class, () -> StartupPlan.defaults(" "));
  }

  @Test
  void warningKeysAreImmutable() {
    StartupPlan plan = StartupPlan.defaults("0.1.0-SNAPSHOT");

    assertThrows(UnsupportedOperationException.class, () -> plan.warningKeys().clear());
  }
}
