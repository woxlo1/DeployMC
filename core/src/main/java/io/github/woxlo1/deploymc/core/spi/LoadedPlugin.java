package io.github.woxlo1.deploymc.core.spi;

import java.util.Objects;

/** Immutable description of a loaded plugin. */
public final class LoadedPlugin {

  private final String name;
  private final String version;
  private final String mainClass;
  private final boolean enabled;

  public LoadedPlugin(String name, String version, String mainClass, boolean enabled) {
    this.name = Objects.requireNonNull(name, "name");
    this.version = Objects.requireNonNull(version, "version");
    this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
    this.enabled = enabled;
  }

  public String name() {
    return name;
  }

  public String version() {
    return version;
  }

  public String mainClass() {
    return mainClass;
  }

  public boolean isEnabled() {
    return enabled;
  }
}
