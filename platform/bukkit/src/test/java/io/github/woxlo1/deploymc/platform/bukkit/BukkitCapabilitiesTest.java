package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.CapabilityState;
import io.github.woxlo1.deploymc.core.CapabilityCatalog;
import io.github.woxlo1.deploymc.core.CapabilityDefinition;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import org.junit.jupiter.api.Test;

class BukkitCapabilitiesTest {

  private final PlatformCapabilities capabilities = BukkitCapabilities.create();

  @Test
  void reportsOnlyImplementedCapabilitiesAsAvailable() {
    assertTrue(capabilities.isAvailable(CapabilityKeys.COMMAND_DISPATCH));
    assertTrue(capabilities.isAvailable(CapabilityKeys.SERVER_SAVE));
    assertTrue(capabilities.isAvailable(CapabilityKeys.SERVER_SHUTDOWN));
    assertTrue(capabilities.isAvailable(CapabilityKeys.PLUGINS_ENUMERATE));
    assertTrue(capabilities.isAvailable(CapabilityKeys.PLUGINS_ENABLE_STATE));

    assertFalse(capabilities.isAvailable(CapabilityKeys.LOG_CAPTURE));
    assertFalse(capabilities.isAvailable(CapabilityKeys.LOG_CAPTURE_STRUCTURED));
    assertFalse(capabilities.isAvailable(CapabilityKeys.COMMAND_OUTPUT));
    assertFalse(capabilities.isAvailable(CapabilityKeys.SERVER_RESTART_SCRIPT));
    assertFalse(capabilities.isAvailable(CapabilityKeys.PLUGINS_LOAD_ERRORS));
    assertFalse(capabilities.isAvailable(CapabilityKeys.UPDATE_FOLDER));
    assertFalse(capabilities.isAvailable(CapabilityKeys.SCHEDULER_REGION));
    assertFalse(capabilities.isAvailable(CapabilityKeys.PLAYERS_LOCALE));
    assertFalse(capabilities.isAvailable(CapabilityKeys.PLAYERS_BROADCAST));
  }

  @Test
  void declaresEveryCatalogKeyExplicitly() {
    for (CapabilityDefinition definition : CapabilityCatalog.all()) {
      assertTrue(
          capabilities.asMap().containsKey(definition.key()),
          "missing explicit state for " + definition.key());
    }
    assertEquals(CapabilityCatalog.keys().size(), capabilities.asMap().size());
  }

  @Test
  void unknownCapabilitiesRemainUnknown() {
    assertEquals(CapabilityState.UNKNOWN, capabilities.state("capability.not-in-catalog"));
    assertEquals(CapabilityState.UNKNOWN, capabilities.state(CapabilityKeys.PLAYERS_LOCALE));
  }
}
