package io.github.woxlo1.deploymc.platform.bukkit;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit-family entry point. The generated {@code plugin.yml} points to this class. */
public final class DeployMCPlugin extends JavaPlugin {

  private BukkitPlatformAdapter adapter;

  @Override
  public void onLoad() {
    File dataFolder = getDataFolder();
    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
      Map<String, String> values =
          java.util.Collections.singletonMap("path", dataFolder.getAbsolutePath());
      Logger logger = getLogger();
      logger.warning(
          LocalizedMessages.of(Locale.getDefault())
              .format(MessageKeys.WARNING_DATA_FOLDER, values));
    }
  }

  @Override
  public void onEnable() {
    LocalizedMessages messages = LocalizedMessages.of(Locale.getDefault());
    try {
      StartupPlan plan = StartupPlan.defaults(getDescription().getVersion());
      BukkitPlatformAdapter created = new BukkitPlatformAdapter(this, plan, messages);
      StartupBanner banner = new StartupBanner(messages);
      for (String line : banner.render(created.info(), plan)) {
        getLogger().info(line);
      }
      adapter = created;
    } catch (RuntimeException exception) {
      Map<String, String> values =
          java.util.Collections.singletonMap("error", String.valueOf(exception.getMessage()));
      getLogger().severe(messages.format(MessageKeys.LIFECYCLE_START_FAILED, values));
      if (adapter != null) {
        adapter.close();
        adapter = null;
      }
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    if (adapter != null) {
      adapter.close();
      adapter = null;
    }
  }
}
