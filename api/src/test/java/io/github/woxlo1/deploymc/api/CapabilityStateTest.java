package io.github.woxlo1.deploymc.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapabilityStateTest {

  @Test
  void onlyAvailableIsAvailable() {
    assertTrue(CapabilityState.AVAILABLE.isAvailable());
    assertFalse(CapabilityState.UNAVAILABLE.isAvailable());
    assertFalse(CapabilityState.UNKNOWN.isAvailable());
  }
}
