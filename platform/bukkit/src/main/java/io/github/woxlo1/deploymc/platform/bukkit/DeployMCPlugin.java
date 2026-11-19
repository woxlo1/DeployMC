package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.auth.TokenStore;
import io.github.woxlo1.deploymc.core.auth.TokenStoreException;
import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigMigration;
import io.github.woxlo1.deploymc.core.config.ConfigSchema;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import java.io.File;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit-family entry point. The generated {@code plugin.yml} points to this class. */
public final class DeployMCPlugin extends JavaPlugin {

  private BukkitPlatformAdapter adapter;
  private ConfigManager configManager;

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
    LocalizedMessages fallback = LocalizedMessages.of(Locale.getDefault());
    LocalizedMessages messages = fallback;
    BukkitPlatformAdapter created = null;
    try {
      ConfigManager createdConfigManager =
          new ConfigManager(
              getDataFolder().toPath().resolve("config.yml"),
              ConfigSchema.load(),
              Collections.<ConfigMigration>emptyList(),
              Clock.systemUTC(),
              Locale.getDefault());
      ConfigView view = createdConfigManager.load();
      messages = LocalizedMessages.of(ConfigLanguage.toLocale(view.language()));
      StartupPlan plan = StartupPlan.fromConfig(view, getDescription().getVersion());

      int defaultTtl = view.getInt("auth.token.default-ttl-days", 90);
      int maxTtl = view.getInt("auth.token.max-ttl-days", 365);
      Duration lifetime = Duration.ofDays(Math.max(1, Math.min(defaultTtl, maxTtl)));
      TokenStore tokenStore = TokenStore.load(getDataFolder().toPath().resolve("tokens.d"));
      AuthenticationRuntime authentication =
          AuthenticationRuntime.create(tokenStore, Clock.systemUTC(), new SecureRandom(), lifetime);

      created = new BukkitPlatformAdapter(this, plan, messages);

      StartupBanner banner = new StartupBanner(messages);
      for (String line : banner.render(created.info(), plan)) {
        getLogger().info(line);
      }

      PluginCommand command = getCommand("deploymc");
      if (command == null) {
        getLogger().severe(messages.format(MessageKeys.COMMAND_REGISTRATION_FAILED));
        disableAfterFailure(created);
        return;
      }
      DeployMCCommandExecutor executor =
          new DeployMCCommandExecutor(
              new ConfigCommandHandler(createdConfigManager, plan, authentication));
      command.setExecutor(executor);
      command.setTabCompleter(executor);

      adapter = created;
      configManager = createdConfigManager;
    } catch (StartupConfigurationException exception) {
      getLogger().severe(messages.format(exception.messageKey(), exception.values()));
      disableAfterFailure(created);
    } catch (TokenStoreException exception) {
      String key;
      switch (exception.reason()) {
        case RECORD:
          key = MessageKeys.STARTUP_TOKEN_STORE_RECORD;
          break;
        case IO:
          key = MessageKeys.STARTUP_TOKEN_STORE_IO;
          break;
        case DIRECTORY:
        default:
          key = MessageKeys.STARTUP_TOKEN_STORE_DIRECTORY;
          break;
      }
      getLogger()
          .severe(
              messages.format(
                  key, Collections.singletonMap("detail", String.valueOf(exception.detail()))));
      disableAfterFailure(created);
    } catch (ConfigException | RuntimeException exception) {
      Map<String, String> values =
          java.util.Collections.singletonMap("error", String.valueOf(exception.getMessage()));
      getLogger().severe(fallback.format(MessageKeys.LIFECYCLE_START_FAILED, values));
      disableAfterFailure(created);
    }
  }

  @Override
  public void onDisable() {
    if (adapter != null) {
      adapter.close();
      adapter = null;
    }
    configManager = null;
  }

  private void disableAfterFailure(BukkitPlatformAdapter created) {
    if (created != null) {
      created.close();
    }
    if (adapter != null) {
      adapter.close();
      adapter = null;
    }
    getServer().getPluginManager().disablePlugin(this);
  }
}
