package io.github.woxlo1.deploymc.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Applies ordered migrations and refuses unknown or future configuration versions. */
public final class ConfigMigrator {

  private final ConfigSchema schema;
  private final List<ConfigMigration> migrations;

  public ConfigMigrator(ConfigSchema schema, List<ConfigMigration> migrations) {
    this.schema = schema;
    List<ConfigMigration> sorted = new ArrayList<ConfigMigration>(migrations);
    Collections.sort(
        sorted,
        Comparator.comparingInt(ConfigMigration::fromVersion)
            .thenComparingInt(ConfigMigration::toVersion));
    this.migrations = Collections.unmodifiableList(sorted);
  }

  public int version(YamlValue root) throws ConfigException {
    if (!root.isObject()) {
      throw missingVersionError(schema.supportedVersion());
    }
    Map<String, YamlValue> entries = root.entries();
    YamlValue value = entries.get("config-version");
    if (value == null || !value.isInteger()) {
      throw missingVersionError(schema.supportedVersion());
    }
    return ((Number) value.raw()).intValue();
  }

  public int version(Map<String, Object> raw) throws ConfigException {
    Object value = raw.get("config-version");
    if (!(value instanceof Number)) {
      throw missingVersionError(schema.supportedVersion());
    }
    return ((Number) value).intValue();
  }

  public boolean needsMigration(YamlValue root) throws ConfigException {
    int version = version(root);
    if (version > schema.supportedVersion()) {
      throw versionError(version, schema.supportedVersion());
    }
    return version < schema.supportedVersion();
  }

  public YamlValue migrate(YamlValue root) throws ConfigException {
    YamlValue current = root;
    int guard = 0;
    while (needsMigration(current)) {
      int version = version(current);
      ConfigMigration step = findStep(version);
      if (step == null) {
        throw new ConfigException(
            ConfigMessages.format(
                Locale.ROOT, "config.error.migrationPath", version, schema.supportedVersion()));
      }
      current = step.migrate(current);
      int resultingVersion = version(current);
      if (resultingVersion <= version || resultingVersion > schema.supportedVersion()) {
        throw new ConfigException(
            ConfigMessages.format(
                Locale.ROOT,
                "config.error.migration",
                step.getClass().getSimpleName() + " produced config-version " + resultingVersion));
      }
      if (++guard > migrations.size() + 1) {
        throw new ConfigException(
            ConfigMessages.format(Locale.ROOT, "config.error.migration", "migration cycle"));
      }
    }
    return current;
  }

  private ConfigMigration findStep(int version) {
    for (ConfigMigration migration : migrations) {
      if (migration.fromVersion() == version) {
        return migration;
      }
    }
    return null;
  }

  private static ConfigException versionError(Object found, int expected) {
    return new ConfigException(
        ConfigMessages.format(Locale.ROOT, "config.error.version", found, expected));
  }

  private static ConfigException missingVersionError(int expected) {
    return new ConfigException(
        ConfigMessages.format(Locale.ROOT, "config.error.versionMissing", expected));
  }

  /** Replaces the version field in a migrated mapping without losing other keys. */
  public static YamlValue withVersion(YamlValue value, int version) {
    if (!value.isObject()) {
      return value;
    }
    Map<String, YamlValue> entries =
        new java.util.LinkedHashMap<String, YamlValue>(value.entries());
    YamlValue oldVersion = entries.get("config-version");
    int line = oldVersion == null ? value.line() : oldVersion.line();
    int column = oldVersion == null ? value.column() : oldVersion.column();
    entries.put("config-version", new YamlValue((long) version, line, column));
    return new YamlValue(entries, value.line(), value.column());
  }
}
