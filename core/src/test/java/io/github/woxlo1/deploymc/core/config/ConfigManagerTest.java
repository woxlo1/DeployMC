package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {

  @TempDir Path tempDir;

  @Test
  void loadGeneratesAndActivatesAValidDefault() throws Exception {
    Path file = tempDir.resolve("config.yml");
    ConfigManager manager = new ConfigManager(file);

    ConfigView view = manager.load();

    assertTrue(Files.exists(file));
    assertTrue(manager.current().isPresent());
    assertEquals(1, view.configVersion());
    assertEquals("en", view.language());
    assertTrue(manager.lastValidation().get().isValid());
  }

  @Test
  void reloadAppliesHotReloadableKeys() throws Exception {
    Path file = tempDir.resolve("config.yml");
    ConfigManager manager = new ConfigManager(file);
    manager.load();

    write(file, DefaultConfigWriter.template().replace("language: en", "language: ja"));
    ReloadReport report = manager.reload();

    assertTrue(report.changedPaths().contains("language"));
    assertFalse(report.requiresRestart());
    assertEquals("ja", manager.current().get().language());
  }

  @Test
  void reloadReportsRestartOnlyKeys() throws Exception {
    Path file = tempDir.resolve("config.yml");
    ConfigManager manager = new ConfigManager(file);
    manager.load();

    write(file, DefaultConfigWriter.template().replace("port: 8080", "port: 8081"));
    ReloadReport report = manager.reload();

    assertTrue(report.changedPaths().contains("bind.port"));
    assertTrue(report.restartRequiredPaths().contains("bind.port"));
    assertEquals(8081, manager.current().get().getInt("bind.port", 0));
  }

  @Test
  void invalidReloadKeepsTheLastKnownGoodView() throws Exception {
    Path file = tempDir.resolve("config.yml");
    ConfigManager manager = new ConfigManager(file);
    manager.load();
    ConfigView before = manager.current().get();

    write(file, DefaultConfigWriter.template().replace("mode: token", "mode: wrong"));

    assertThrows(ConfigException.class, manager::reload);
    assertEquals("en", manager.current().get().language());
    assertEquals(before.configVersion(), manager.current().get().configVersion());
  }

  @Test
  void invalidStartupFailsClosed() throws Exception {
    Path file = tempDir.resolve("config.yml");
    write(file, "config-version: 1\nauth:\n  mode: wrong\n");
    ConfigManager manager = new ConfigManager(file);

    assertThrows(ConfigException.class, manager::load);
    assertFalse(manager.current().isPresent());
  }

  @Test
  void migrationBacksUpBeforeRewriting() throws Exception {
    Path file = tempDir.resolve("config.yml");
    String oldConfig =
        DefaultConfigWriter.template().replace("config-version: 1", "config-version: 0");
    write(file, oldConfig);
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T12:34:56Z"), ZoneOffset.UTC);
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
    ConfigManager manager =
        new ConfigManager(
            file, ConfigSchema.load(), Arrays.asList(step), clock, java.util.Locale.ROOT);

    ConfigView view = manager.load();

    assertEquals(1, view.configVersion());
    assertTrue(manager.lastBackup().isPresent());
    Path backup = manager.lastBackup().get();
    assertTrue(Files.exists(backup));
    assertTrue(
        new String(Files.readAllBytes(backup), StandardCharsets.UTF_8)
            .contains("config-version: 0"));
    assertTrue(
        new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("config-version: 1"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void exposedViewIsDeeplyImmutable() throws Exception {
    Path file = tempDir.resolve("config.yml");
    ConfigView view = new ConfigManager(file).load();

    assertThrows(UnsupportedOperationException.class, () -> view.raw().put("extra", "value"));
    Map<String, Object> bind = (Map<String, Object>) view.get("bind");
    assertThrows(UnsupportedOperationException.class, () -> bind.put("port", 1L));
    List<Object> protectedPaths = (List<Object>) view.get("sandbox.protected-paths");
    assertThrows(UnsupportedOperationException.class, () -> protectedPaths.add("extra"));
  }

  private static void write(Path path, String content) throws Exception {
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }
}
