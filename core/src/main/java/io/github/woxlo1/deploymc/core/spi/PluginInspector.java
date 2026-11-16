package io.github.woxlo1.deploymc.core.spi;

import java.util.List;

/** Platform-independent plugin enumeration. */
public interface PluginInspector {

  /** Returns an immutable snapshot of loaded plugins. */
  List<LoadedPlugin> loadedPlugins();
}
