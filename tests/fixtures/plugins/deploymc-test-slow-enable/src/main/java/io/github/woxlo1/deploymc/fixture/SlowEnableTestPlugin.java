package io.github.woxlo1.deploymc.fixture;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin that deliberately sleeps during {@code onEnable}.
 *
 * <p>The delay is injected through the {@code slow-enable-millis} config value or the {@code
 * deploymc.fixture.slow-enable.millis} system property so timeout tests can stay deterministic. The
 * default exceeds a normal verification timeout. Log strings are deliberate test signals and are
 * not product UI.
 */
public final class SlowEnableTestPlugin extends JavaPlugin {

  private static final String CONFIG_KEY = "slow-enable-millis";
  private static final String SYSTEM_PROPERTY = "deploymc.fixture.slow-enable.millis";
  private static final long DEFAULT_DELAY_MILLIS = 30000L;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    long delayMillis = delayMillis();
    getLogger()
        .info(
            "deploymc-test-slow-enable sleeping "
                + delayMillis
                + "ms; expecting verification timeout");
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      getLogger().warning("deploymc-test-slow-enable sleep interrupted; test signal incomplete");
      return;
    }
    getLogger().info("deploymc-test-slow-enable enabled after " + delayMillis + "ms");
  }

  private long delayMillis() {
    String override = System.getProperty(SYSTEM_PROPERTY);
    if (override != null) {
      return Long.parseLong(override);
    }
    return getConfig().getLong(CONFIG_KEY, DEFAULT_DELAY_MILLIS);
  }
}
