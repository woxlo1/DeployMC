package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the committed config corpora through the real validator and loader. */
class ConfigCorpusTest {

  private static final String RESOURCE = "/config-corpora/";
  private static final String MANIFEST = RESOURCE + "config-corpus-manifest.properties";

  @TempDir Path tempDir;

  @Test
  void everyManifestFixtureMatchesLoaderAndValidatorBehavior() throws Exception {
    Properties manifest = manifest();
    ConfigSchema schema = ConfigSchema.load();
    List<String> files = values(manifest.getProperty("fixture.list"));
    assertEquals(Integer.parseInt(required(manifest, "fixture.count")), files.size());

    for (String file : files) {
      String prefix = "fixture." + identifier(file) + ".";
      byte[] bytes = resourceBytes(RESOURCE + file);
      Path copy = tempDir.resolve(file);
      Files.write(copy, bytes);
      String text = new String(bytes, StandardCharsets.UTF_8);
      ConfigValidationResult validation =
          ConfigValidator.validate(ConfigYamlParser.parse(text), schema, Locale.ROOT);

      String expectedIssue = manifest.getProperty(prefix + "expected.validator.issue", "");
      String expectedWarning = manifest.getProperty(prefix + "expected.validator.warning", "");
      if (expectedIssue.isEmpty()) {
        assertTrue(validation.errors().isEmpty(), file + " had unexpected errors");
      } else {
        assertTrue(hasIssue(validation.errors(), expectedIssue), file + " lacked " + expectedIssue);
      }
      if (!expectedWarning.isEmpty()) {
        assertTrue(
            hasIssue(validation.warnings(), expectedWarning), file + " lacked " + expectedWarning);
      }

      ConfigManager manager =
          new ConfigManager(
              copy,
              schema,
              Collections.<ConfigMigration>emptyList(),
              Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC),
              Locale.ROOT);
      String outcome = required(manifest, prefix + "expected.outcome");
      if ("pass".equals(outcome)) {
        assertEquals(1, manager.load().configVersion(), file);
        assertTrue(manager.lastValidation().get().isValid(), file);
        assertTrue(manager.current().isPresent(), file);
      } else {
        assertEquals("fail", outcome, file);
        ConfigException failure = assertThrows(ConfigException.class, manager::load, file);
        assertLoaderMessage(
            failure.getMessage(),
            required(manifest, prefix + "expected.loader.key"),
            manifest.getProperty(prefix + "found.version", ""));
        assertFalse(manager.current().isPresent(), file);
      }
    }
  }

  private static boolean hasIssue(List<ConfigIssue> issues, String key) {
    for (ConfigIssue issue : issues) {
      if (key.equals(issue.messageKey())) {
        return true;
      }
    }
    return false;
  }

  private static void assertLoaderMessage(String message, String key, String version) {
    if ("config.error.invalid".equals(key)) {
      String sentinel = "CONFIG_CORPUS_SENTINEL";
      String formatted = ConfigMessages.format(Locale.ROOT, key, sentinel);
      String prefix = formatted.substring(0, formatted.indexOf(sentinel));
      assertTrue(message.startsWith(prefix), "Unexpected loader message: " + message);
      return;
    }
    Object[] parameters;
    if ("config.error.versionMissing".equals(key)) {
      parameters = new Object[] {1};
    } else if ("config.error.version".equals(key)) {
      parameters = new Object[] {Integer.valueOf(version), 1};
    } else if ("config.error.migrationPath".equals(key)) {
      parameters = new Object[] {Integer.valueOf(version), 1};
    } else {
      throw new AssertionError("Unexpected loader key: " + key);
    }
    assertTrue(
        message.contains(ConfigMessages.format(Locale.ROOT, key, parameters)),
        "Unexpected loader message: " + message);
  }

  private static String identifier(String file) {
    String stem = file.substring(0, file.length() - ".yml".length());
    return stem.replace('-', '_');
  }

  private static Properties manifest() throws IOException {
    Properties properties = new Properties();
    try (InputStream stream = ConfigCorpusTest.class.getResourceAsStream(MANIFEST)) {
      assertNotNull(stream, "Missing test resource: " + MANIFEST);
      properties.load(stream);
    }
    return properties;
  }

  private static byte[] resourceBytes(String resource) throws IOException {
    try (InputStream stream = ConfigCorpusTest.class.getResourceAsStream(resource)) {
      assertNotNull(stream, "Missing test resource: " + resource);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    assertNotNull(value, "Missing manifest key: " + key);
    return value;
  }

  private static List<String> values(String commaSeparated) {
    List<String> values = new ArrayList<String>();
    for (String value : commaSeparated.split(",")) {
      if (!value.isEmpty()) {
        values.add(value);
      }
    }
    return values;
  }
}
