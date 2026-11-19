package io.github.woxlo1.deploymc.core.config;

/** One ordered configuration format migration step. */
public interface ConfigMigration {

  int fromVersion();

  int toVersion();

  /** Returns an equivalent value whose {@code config-version} is {@link #toVersion()}. */
  YamlValue migrate(YamlValue value) throws ConfigException;
}
