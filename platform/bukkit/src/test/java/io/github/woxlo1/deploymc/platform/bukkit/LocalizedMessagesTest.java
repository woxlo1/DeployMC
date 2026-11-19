package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalizedMessagesTest {

  private final LocalizedMessages messages = LocalizedMessages.of(Locale.ENGLISH);

  @Test
  void catalogContainsEveryDeclaredKey() throws Exception {
    assertEveryDeclaredKeyIsPresent(messages);
  }

  @Test
  void japaneseCatalogContainsEveryDeclaredKey() throws Exception {
    assertEveryDeclaredKeyIsPresent(LocalizedMessages.of(Locale.JAPANESE));
  }

  @Test
  void missingTranslationsFallBackOneKeyAtATime() {
    LocalizedMessages catalog =
        LocalizedMessages.of(Locale.forLanguageTag("zz"), Locale.forLanguageTag("yy"));
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("version", "1.2.3-test");

    assertEquals("Partial title 1.2.3-test", catalog.format(MessageKeys.STARTUP_TITLE, values));
    assertEquals("Server default usage", catalog.format(MessageKeys.COMMAND_USAGE));
    assertEquals(
        LocalizedMessages.of(Locale.ENGLISH).format(MessageKeys.COMMAND_RELOAD_SUCCESS),
        catalog.format(MessageKeys.COMMAND_RELOAD_SUCCESS));
  }

  @Test
  void requestedCatalogIgnoresJvmDefaultLocale() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("version", "1.2.3-test");
    String english = messages.format(MessageKeys.STARTUP_TITLE, values);
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.JAPANESE);

      assertEquals(
          english, LocalizedMessages.of(Locale.ENGLISH).format(MessageKeys.STARTUP_TITLE, values));
    } finally {
      Locale.setDefault(original);
    }
  }

  private static void assertEveryDeclaredKeyIsPresent(LocalizedMessages catalog) throws Exception {
    for (Field field : MessageKeys.class.getFields()) {
      if (String.class.equals(field.getType())) {
        String key = (String) field.get(null);
        assertTrue(catalog.contains(key), field.getName() + " -> " + key);
      }
    }
  }

  @Test
  void configurationLanguageSelectsCatalogLocale() {
    assertEquals(Locale.JAPANESE, ConfigLanguage.toLocale("ja"));
    assertEquals(Locale.forLanguageTag("ja-JP"), ConfigLanguage.toLocale("ja_JP"));
    assertEquals(Locale.ENGLISH, ConfigLanguage.toLocale(""));
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
  void missingCatalogKeyUsesSelectedCatalogDiagnostic() {
    String result = LocalizedMessages.of(Locale.JAPANESE).format("key.does.not.exist");

    assertTrue(result.contains("key.does.not.exist"));
    assertFalse(result.startsWith("Missing localized message key:"));
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
