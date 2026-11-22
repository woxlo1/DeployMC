package io.github.woxlo1.deploymc.fixture;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Test-only plugin that emits a log line at every Java logging level.
 *
 * <p>Rates are configured as server ticks in {@code config.yml} and can be overridden with {@code
 * deploymc.fixture.log-noise.<level>.ticks} system properties for deterministic console and
 * backpressure tests. A non-positive tick value disables that level. Log strings are deliberate
 * test signals and are not product UI.
 */
public final class LogNoiseTestPlugin extends JavaPlugin {

  private static final String PROPERTY_PREFIX = "deploymc.fixture.log-noise.";

  private final List<BukkitTask> tasks = new ArrayList<BukkitTask>();

  @Override
  public void onEnable() {
    saveDefaultConfig();
    getLogger().setLevel(Level.ALL);
    if (!enabled()) {
      getLogger().info("deploymc-test-log-noise disabled by configuration");
      return;
    }

    schedule("finest", Level.FINEST, 40L);
    schedule("finer", Level.FINER, 40L);
    schedule("fine", Level.FINE, 40L);
    schedule("config", Level.CONFIG, 40L);
    schedule("info", Level.INFO, 20L);
    schedule("warning", Level.WARNING, 20L);
    schedule("severe", Level.SEVERE, 20L);
    getLogger().info("deploymc-test-log-noise enabled with " + tasks.size() + " scheduled levels");
  }

  @Override
  public void onDisable() {
    for (BukkitTask task : tasks) {
      task.cancel();
    }
    tasks.clear();
    getLogger().info("deploymc-test-log-noise disabled; scheduled level tasks cancelled");
  }

  private boolean enabled() {
    String override = System.getProperty(PROPERTY_PREFIX + "enabled");
    if (override != null) {
      return Boolean.parseBoolean(override);
    }
    return getConfig().getBoolean("enabled", true);
  }

  private void schedule(final String name, final Level level, long defaultTicks) {
    long ticks = ticks(name, defaultTicks);
    if (ticks <= 0L) {
      return;
    }
    tasks.add(
        getServer()
            .getScheduler()
            .runTaskTimer(
                this,
                new Runnable() {
                  @Override
                  public void run() {
                    getLogger().log(level, "deploymc-test-log-noise " + name + " test signal");
                  }
                },
                ticks,
                ticks));
  }

  private long ticks(String name, long defaultTicks) {
    String override = System.getProperty(PROPERTY_PREFIX + name + ".ticks");
    if (override != null) {
      try {
        return Long.parseLong(override);
      } catch (NumberFormatException invalid) {
        getLogger()
            .warning("deploymc-test-log-noise invalid tick rate for " + name + ": " + override);
      }
    }
    return getConfig().getLong("levels." + name + ".ticks", defaultTicks);
  }
}
