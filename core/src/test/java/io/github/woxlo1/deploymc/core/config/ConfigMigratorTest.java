package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ConfigMigratorTest {

  @Test
  void acceptsCurrentVersionWithoutMigration() throws Exception {
    ConfigSchema schema = ConfigSchema.load();
    ConfigMigrator migrator = new ConfigMigrator(schema, Collections.<ConfigMigration>emptyList());
    YamlValue current = ConfigYamlParser.parse("config-version: 1\nlanguage: ja\n");

    assertFalse(migrator.needsMigration(current));
    assertEquals(1, migrator.version(current));
    assertEquals(current, migrator.migrate(current));
  }

  @Test
  void failsClosedWhenNoMigrationPathExists() throws Exception {
    ConfigSchema schema = ConfigSchema.load();
    ConfigMigrator migrator = new ConfigMigrator(schema, Collections.<ConfigMigration>emptyList());
    YamlValue old = ConfigYamlParser.parse("config-version: 0\nlanguage: ja\n");

    assertTrue(migrator.needsMigration(old));
    assertThrows(ConfigException.class, () -> migrator.migrate(old));
  }

  @Test
  void appliesAnOrderedMigrationStep() throws Exception {
    ConfigSchema schema = ConfigSchema.load();
    ConfigMigration step =
        new ConfigMigration() {
          @Override
          public int fromVersion() {
            return 0;
          }

          @Override
          public int toVersion() {
            return 1;
          }

          @Override
          public YamlValue migrate(YamlValue value) {
            return ConfigMigrator.withVersion(value, 1);
          }
        };
    ConfigMigrator migrator = new ConfigMigrator(schema, Arrays.asList(step));
    YamlValue old = ConfigYamlParser.parse("config-version: 0\nlanguage: ja\n");

    YamlValue migrated = migrator.migrate(old);

    assertEquals(1, migrator.version(migrated));
    assertEquals("ja", migrated.get("language").raw());
    assertFalse(migrator.needsMigration(migrated));
  }

  @Test
  void rejectsFutureAndMissingVersions() throws Exception {
    ConfigSchema schema = ConfigSchema.load();
    ConfigMigrator migrator = new ConfigMigrator(schema, Collections.<ConfigMigration>emptyList());

    assertThrows(
        ConfigException.class,
        () -> migrator.needsMigration(ConfigYamlParser.parse("config-version: 2\n")));
    assertThrows(
        ConfigException.class, () -> migrator.version(ConfigYamlParser.parse("language: ja\n")));
  }
}
