package io.github.woxlo1.deploymc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapabilityCatalogTest {

  @Test
  void exposesEveryStableKeyExactlyOnce() {
    Set<String> expected =
        new HashSet<String>(
            Arrays.asList(
                "log.capture",
                "log.capture.structured",
                "command.dispatch",
                "command.output",
                "server.save",
                "server.shutdown",
                "server.restart.script",
                "plugins.enumerate",
                "plugins.enable-state",
                "plugins.load-errors",
                "update-folder",
                "scheduler.region",
                "players.locale",
                "players.broadcast"));

    assertEquals(14, CapabilityCatalog.all().size());
    assertEquals(expected, new HashSet<String>(CapabilityCatalog.keys()));
    assertEquals(14, new HashSet<String>(CapabilityCatalog.keys()).size());
  }

  @Test
  void keyConstantsMatchWireValues() {
    assertEquals("log.capture", CapabilityKeys.LOG_CAPTURE);
    assertEquals("log.capture.structured", CapabilityKeys.LOG_CAPTURE_STRUCTURED);
    assertEquals("command.dispatch", CapabilityKeys.COMMAND_DISPATCH);
    assertEquals("command.output", CapabilityKeys.COMMAND_OUTPUT);
    assertEquals("server.save", CapabilityKeys.SERVER_SAVE);
    assertEquals("server.shutdown", CapabilityKeys.SERVER_SHUTDOWN);
    assertEquals("server.restart.script", CapabilityKeys.SERVER_RESTART_SCRIPT);
    assertEquals("plugins.enumerate", CapabilityKeys.PLUGINS_ENUMERATE);
    assertEquals("plugins.enable-state", CapabilityKeys.PLUGINS_ENABLE_STATE);
    assertEquals("plugins.load-errors", CapabilityKeys.PLUGINS_LOAD_ERRORS);
    assertEquals("update-folder", CapabilityKeys.UPDATE_FOLDER);
    assertEquals("scheduler.region", CapabilityKeys.SCHEDULER_REGION);
    assertEquals("players.locale", CapabilityKeys.PLAYERS_LOCALE);
    assertEquals("players.broadcast", CapabilityKeys.PLAYERS_BROADCAST);
  }

  @Test
  void everyCapabilityDocumentsDescriptionAndFallback() {
    for (CapabilityDefinition definition : CapabilityCatalog.all()) {
      assertFalse(definition.description().trim().isEmpty(), definition.key());
      assertFalse(definition.fallback().trim().isEmpty(), definition.key());
      assertEquals(definition.key(), CapabilityCatalog.get(definition.key()).key());
    }
  }

  @Test
  void unknownKeyIsNotCataloged() {
    assertNull(CapabilityCatalog.get("capability.does-not-exist"));
    assertThrows(NullPointerException.class, () -> CapabilityCatalog.get(null));
  }

  @Test
  void catalogIsImmutable() {
    assertThrows(UnsupportedOperationException.class, () -> CapabilityCatalog.all().clear());
    assertThrows(UnsupportedOperationException.class, () -> CapabilityCatalog.keys().clear());
  }

  @Test
  void definitionRejectsEmptyMetadata() {
    assertThrows(IllegalArgumentException.class, () -> new CapabilityDefinition("", "d", "f"));
    assertThrows(IllegalArgumentException.class, () -> new CapabilityDefinition("key", " ", "f"));
    assertThrows(IllegalArgumentException.class, () -> new CapabilityDefinition("key", "d", " "));
    assertEquals("update-folder", CapabilityCatalog.get(CapabilityKeys.UPDATE_FOLDER).key());
  }
}
