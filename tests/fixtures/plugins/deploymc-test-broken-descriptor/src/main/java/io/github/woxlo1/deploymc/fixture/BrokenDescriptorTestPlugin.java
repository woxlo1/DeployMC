package io.github.woxlo1.deploymc.fixture;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin source for a JAR whose generated descriptor is deliberately invalid.
 *
 * <p>The descriptor omits the required {@code main} entry, so a validator should reject the JAR
 * before this class can be loaded. The log string is a deliberate test signal and is not product
 * UI.
 */
public final class BrokenDescriptorTestPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    getLogger()
        .info("deploymc-test-broken-descriptor unexpectedly enabled; validation did not reject it");
  }
}
