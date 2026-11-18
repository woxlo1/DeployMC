package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalizedMessagesTest {

  private final LocalizedMessages messages = LocalizedMessages.of(Locale.ENGLISH);

  @Test
  void catalogContainsEveryDeclaredKey() {
    assertTrue(messages.contains(MessageKeys.STARTUP_TITLE));
    assertTrue(messages.contains(MessageKeys.STARTUP_PLATFORM));
    assertTrue(messages.contains(MessageKeys.STARTUP_JAVA));
    assertTrue(messages.contains(MessageKeys.STARTUP_BIND));
    assertTrue(messages.contains(MessageKeys.STARTUP_AUTH));
    assertTrue(messages.contains(MessageKeys.WARNING_NON_LOOPBACK));
    assertTrue(messages.contains(MessageKeys.WARNING_NO_RESTART_SCRIPT));
    assertTrue(messages.contains(MessageKeys.MESSAGE_MISSING_KEY));
    assertTrue(messages.contains(MessageKeys.MESSAGE_MISSING_PLACEHOLDER));
    assertTrue(messages.contains(MessageKeys.MESSAGE_CAPABILITY_UNAVAILABLE));
    assertTrue(messages.contains(MessageKeys.MESSAGE_SHUTDOWN_REQUESTED));
    assertTrue(messages.contains(MessageKeys.LIFECYCLE_DISABLED));
    assertTrue(messages.contains(MessageKeys.LIFECYCLE_START_FAILED));
    assertTrue(messages.contains(MessageKeys.WARNING_DATA_FOLDER));
  }

  @Test
  void formatsNamedPlaceholders() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("version", "1.2.3-test");

    assertEquals("DeployMC 1.2.3-test", messages.format(MessageKeys.STARTUP_TITLE, values));
  }

  @Test
  void preservesPlaceholderValuesThatContainSpecialReplacementCharacters() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("key", "plugin$1.yml");

    assertEquals(
        "Missing localized message key: plugin$1.yml",
        messages.format(MessageKeys.MESSAGE_MISSING_KEY, values));
  }

  @Test
  void reportsMissingCatalogKeyThroughCatalog() {
    String result = messages.format("key.does.not.exist");

    assertTrue(result.startsWith("Missing localized message key:"));
    assertTrue(result.contains("key.does.not.exist"));
    assertFalse(result.contains("{key}"));
  }

  @Test
  void reportsMissingPlaceholderThroughCatalog() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("wrong", "value");

    String result = messages.format(MessageKeys.STARTUP_TITLE, values);

    assertTrue(result.startsWith("Missing placeholder"));
    assertTrue(result.contains("version"));
    assertTrue(result.contains(MessageKeys.STARTUP_TITLE));
    assertFalse(result.contains("{placeholder}"));
  }
}
