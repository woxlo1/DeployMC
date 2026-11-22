package io.github.woxlo1.deploymc.fixture;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin whose descriptor declares a missing hard dependency.
 *
 * <p>The platform should refuse to enable this plugin and report the missing dependency. The log
 * string is a deliberate test signal and is not product UI.
 */
public final class DependencyTestPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    getLogger().info("deploymc-test-dependency enabled; missing hard dependency was not enforced");
  }
}
