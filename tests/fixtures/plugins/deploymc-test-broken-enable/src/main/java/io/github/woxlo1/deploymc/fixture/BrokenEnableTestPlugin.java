package io.github.woxlo1.deploymc.fixture;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin that deliberately fails during {@code onEnable}.
 *
 * <p>The config file is created before the failure so deployment rollback can be observed. The log
 * and exception strings are deliberate test signals and are not product UI.
 */
public final class BrokenEnableTestPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    saveDefaultConfig();
    getLogger().info("deploymc-test-broken-enable enabling; expecting failure");
    throw new IllegalStateException("deploymc-test-broken-enable deliberate onEnable failure");
  }
}
