package io.github.woxlo1.deploymc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerInfoTest {

  private static final PlatformInfo PLATFORM =
      new PlatformInfo("Paper", "1.21.1-123", "1.21.1", "21.0.11", "Windows 11");

  @Test
  void copiesCapabilitiesDefensively() {
    Map<String, CapabilityState> source = new LinkedHashMap<String, CapabilityState>();
    source.put("log.capture", CapabilityState.AVAILABLE);

    ServerInfo info = new ServerInfo("0.1.0", "v1", PLATFORM, source);
    source.put("server.restart.script", CapabilityState.UNAVAILABLE);

    assertEquals(1, info.capabilities().size());
    assertEquals(CapabilityState.AVAILABLE, info.capability("log.capture"));
  }

  @Test
  void capabilitiesMapIsUnmodifiable() {
    Map<String, CapabilityState> source = new LinkedHashMap<String, CapabilityState>();
    ServerInfo info = new ServerInfo("0.1.0", "v1", PLATFORM, source);

    assertThrows(
        UnsupportedOperationException.class,
        () -> info.capabilities().put("log.capture", CapabilityState.AVAILABLE));
  }

  @Test
  void unknownCapabilityIsNotAssumedAvailable() {
    ServerInfo info =
        new ServerInfo("0.1.0", "v1", PLATFORM, new LinkedHashMap<String, CapabilityState>());

    assertEquals(CapabilityState.UNKNOWN, info.capability("plugins.enumerate"));
  }

  @Test
  void rejectsBlankVersion() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ServerInfo(" ", "v1", PLATFORM, new LinkedHashMap<String, CapabilityState>()));
  }
}
