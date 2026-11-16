package io.github.woxlo1.deploymc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.CapabilityState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformCapabilitiesTest {

  @Test
  void reportsKnownStates() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put(CapabilityKeys.LOG_CAPTURE, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.SERVER_RESTART_SCRIPT, CapabilityState.UNAVAILABLE);

    PlatformCapabilities capabilities = PlatformCapabilities.of(states);

    assertTrue(capabilities.isAvailable(CapabilityKeys.LOG_CAPTURE));
    assertFalse(capabilities.isAvailable(CapabilityKeys.SERVER_RESTART_SCRIPT));
    assertEquals(CapabilityState.AVAILABLE, capabilities.state(CapabilityKeys.LOG_CAPTURE));
  }

  @Test
  void unknownCapabilitiesAreNotAvailable() {
    PlatformCapabilities capabilities = PlatformCapabilities.empty();

    assertEquals(CapabilityState.UNKNOWN, capabilities.state(CapabilityKeys.PLAYERS_LOCALE));
    assertFalse(capabilities.isAvailable(CapabilityKeys.PLAYERS_LOCALE));
  }

  @Test
  void copiesInputDefensively() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put(CapabilityKeys.UPDATE_FOLDER, CapabilityState.AVAILABLE);
    PlatformCapabilities capabilities = PlatformCapabilities.of(states);

    states.put(CapabilityKeys.UPDATE_FOLDER, CapabilityState.UNAVAILABLE);

    assertEquals(CapabilityState.AVAILABLE, capabilities.state(CapabilityKeys.UPDATE_FOLDER));
    assertThrows(
        UnsupportedOperationException.class,
        () -> capabilities.asMap().put(CapabilityKeys.UPDATE_FOLDER, CapabilityState.UNKNOWN));
  }
}
