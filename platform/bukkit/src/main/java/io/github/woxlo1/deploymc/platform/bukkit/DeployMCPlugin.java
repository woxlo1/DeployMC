package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.audit.AuditConfig;
import io.github.woxlo1.deploymc.core.audit.AuditException;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import io.github.woxlo1.deploymc.core.auth.TokenStoreException;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfig;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigMigration;
import io.github.woxlo1.deploymc.core.config.ConfigSchema;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicyManager;
import io.github.woxlo1.deploymc.transport.AuditApiHandler;
import io.github.woxlo1.deploymc.transport.DefaultTransportPolicy;
import io.github.woxlo1.deploymc.transport.JsonRequestLogger;
import io.github.woxlo1.deploymc.transport.LoggingFormat;
import io.github.woxlo1.deploymc.transport.MeHandler;
import io.github.woxlo1.deploymc.transport.MetricsRegistry;
import io.github.woxlo1.deploymc.transport.NettyTransportServer;
import io.github.woxlo1.deploymc.transport.RequestLogger;
import io.github.woxlo1.deploymc.transport.TransportConfig;
import io.github.woxlo1.deploymc.transport.TransportException;
import io.github.woxlo1.deploymc.transport.TransportMessages;
import java.io.File;
import java.io.IOException;
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
  private NettyTransportServer transport;
  private FileApiRuntime fileApi;
  private AuditStore auditStore;

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
    NettyTransportServer createdTransport = null;
    FileApiRuntime createdFileApi = null;
    DoctorRuntime createdDoctor = null;
    AuditStore createdAuditStore = null;
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

      AuthenticationRuntime authentication;
      if (plan.cloudflareMode()) {
        try {
          authentication =
              AuthenticationRuntime.createCloudflare(
                  CloudflareConfig.fromConfig(view), Clock.systemUTC());
        } catch (CloudflareConfigException invalid) {
          Map<String, String> values = Collections.singletonMap("reason", invalid.code());
          throw new StartupConfigurationException(MessageKeys.STARTUP_CLOUDFLARE_INVALID, values);
        }
      } else if (plan.localOneTimeMode()) {
        int ttlMinutes = view.getInt("auth.local-onetime.ttl-minutes", 15);
        int maxAttempts = view.getInt("auth.local-onetime.max-attempts", 5);
        authentication =
            AuthenticationRuntime.createLocalOneTime(
                Clock.systemUTC(),
                new SecureRandom(),
                Duration.ofMinutes(Math.max(1, Math.min(ttlMinutes, 60))),
                Math.max(1, Math.min(maxAttempts, 20)));
      } else {
        int defaultTtl = view.getInt("auth.token.default-ttl-days", 90);
        int maxTtl = view.getInt("auth.token.max-ttl-days", 365);
        Duration lifetime = Duration.ofDays(Math.max(1, Math.min(defaultTtl, maxTtl)));
        TokenStore tokenStore = TokenStore.load(getDataFolder().toPath().resolve("tokens.d"));
        authentication =
            AuthenticationRuntime.create(
                tokenStore, Clock.systemUTC(), new SecureRandom(), lifetime);
      }

      created = new BukkitPlatformAdapter(this, plan, messages);

      Locale locale = ConfigLanguage.toLocale(view.language());
      TransportMessages transportMessages = TransportMessages.of(locale);
      HostObservabilityConfig observability = HostObservabilityConfig.from(view);
      RequestLogger requestLogger = requestLogger(observability, transportMessages);
      TransportConfig transportConfig = observability.transportConfig(plan.bind());
      File pluginsDirectory = getDataFolder().getParentFile();
      if (pluginsDirectory == null) {
        throw new StartupConfigurationException(
            MessageKeys.STARTUP_FILE_API_INVALID_CONFIG,
            Collections.singletonMap("setting", "plugins directory"));
      }
      RbacPolicyManager policies = RbacPolicyManager.from(view);
      if (!policies.current().valid()) {
        throw new StartupConfigurationException(
            MessageKeys.STARTUP_RBAC_INVALID, Collections.<String, String>emptyMap());
      }
      try {
        createdAuditStore =
            AuditStore.open(
                getDataFolder().toPath(), AuditConfig.fromConfig(view), Clock.systemUTC());
      } catch (AuditException failure) {
        throw new StartupConfigurationException(
            MessageKeys.STARTUP_AUDIT_FAILED,
            Collections.singletonMap("reason", failure.problemCode()));
      }
      createdFileApi =
          FileApiRuntime.create(
              view,
              pluginsDirectory.toPath(),
              locale,
              transportConfig.maxBodyBytes(),
              policies,
              createdAuditStore);
      RbacEvaluator evaluator = createdFileApi.evaluator();
      createdDoctor =
          DoctorRuntime.create(
              createdConfigManager,
              plan,
              created,
              locale,
              view.language(),
              createdFileApi,
              messages,
              authentication);
      MetricsRegistry metrics = new MetricsRegistry();
      createdTransport =
          new NettyTransportServer(
              transportConfig,
              new DeployMCHttpHandler(
                  new ServerInfoHandler(
                      plan.pluginVersion(), created.info(), created.capabilities()),
                  createdFileApi.handler(),
                  createdFileApi.snapshotHandler(),
                  new DoctorHandler(createdDoctor, messages, evaluator),
                  new MeHandler(evaluator, transportMessages),
                  new AuditApiHandler(createdAuditStore, createdFileApi.auditRecorder(), locale)),
              new NoopWebSocketListener(),
              new DefaultTransportPolicy(transportConfig),
              requestLogger,
              authentication.authenticator(),
              transportMessages,
              evaluator,
              metrics,
              createdFileApi.auditRecorder());
      createdTransport.start();
      publishSnapshotStoreGauge(metrics, createdFileApi, messages);

      StartupBanner banner = new StartupBanner(messages);
      for (String line : banner.render(created.info(), plan)) {
        getLogger().info(line);
      }
      if (plan.localOneTimeMode()) {
        Map<String, String> values = new java.util.LinkedHashMap<String, String>();
        values.put("token", authentication.oneTimeToken().secret());
        values.put("expiresAt", authentication.oneTimeToken().expiresAt().toString());
        getServer()
            .getConsoleSender()
            .sendMessage(messages.format(MessageKeys.STARTUP_ONE_TIME_TOKEN, values));
      }

      PluginCommand command = getCommand("deploymc");
      if (command == null) {
        getLogger().severe(messages.format(MessageKeys.COMMAND_REGISTRATION_FAILED));
        disableAfterFailure(created, createdTransport, createdFileApi, createdAuditStore);
        return;
      }
      DeployMCCommandExecutor executor =
          new DeployMCCommandExecutor(
              new ConfigCommandHandler(
                  createdConfigManager,
                  plan,
                  authentication,
                  createdDoctor,
                  policies,
                  createdFileApi.auditRecorder(),
                  createdAuditStore));
      command.setExecutor(executor);
      command.setTabCompleter(executor);

      adapter = created;
      configManager = createdConfigManager;
      transport = createdTransport;
      fileApi = createdFileApi;
      auditStore = createdAuditStore;
    } catch (StartupConfigurationException exception) {
      getLogger().severe(messages.format(exception.messageKey(), exception.values()));
      disableAfterFailure(created, createdTransport, createdFileApi, createdAuditStore);
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
      disableAfterFailure(created, createdTransport, createdFileApi, createdAuditStore);
    } catch (TransportException exception) {
      Map<String, String> values = Collections.singletonMap("error", exception.error().code());
      getLogger().severe(messages.format(MessageKeys.STARTUP_TRANSPORT_FAILED, values));
      disableAfterFailure(created, createdTransport, createdFileApi, createdAuditStore);
    } catch (ConfigException | RuntimeException exception) {
      Map<String, String> values =
          java.util.Collections.singletonMap("error", String.valueOf(exception.getMessage()));
      getLogger().severe(fallback.format(MessageKeys.LIFECYCLE_START_FAILED, values));
      disableAfterFailure(created, createdTransport, createdFileApi, createdAuditStore);
    }
  }

  @Override
  public void onDisable() {
    stopTransport();
    closeFileApi(fileApi);
    fileApi = null;
    closeAuditStore(auditStore);
    auditStore = null;
    if (adapter != null) {
      adapter.close();
      adapter = null;
    }
    configManager = null;
  }

  private RequestLogger requestLogger(
      HostObservabilityConfig observability, TransportMessages transportMessages) {
    if (observability.observability().loggingFormat() == LoggingFormat.JSON) {
      return new JsonRequestLogger(Logger.getLogger("io.github.woxlo1.deploymc"));
    }
    return new PluginRequestLogger(getLogger(), transportMessages);
  }

  private void publishSnapshotStoreGauge(
      MetricsRegistry metrics, FileApiRuntime fileApi, LocalizedMessages messages) {
    try {
      metrics.setSnapshotStoreBytes(fileApi.snapshotStore().storeSizeBytes());
    } catch (Exception failure) {
      getLogger().warning(messages.format(MessageKeys.WARNING_OBSERVABILITY_SNAPSHOT_GAUGE));
    }
  }

  private void disableAfterFailure(
      BukkitPlatformAdapter created,
      NettyTransportServer createdTransport,
      FileApiRuntime createdFileApi,
      AuditStore createdAuditStore) {
    stopTransport(createdTransport);
    closeFileApi(createdFileApi);
    closeAuditStore(createdAuditStore);
    if (created != null) {
      created.close();
    }
    if (adapter != null) {
      adapter.close();
      adapter = null;
    }
    getServer().getPluginManager().disablePlugin(this);
  }

  private void stopTransport() {
    stopTransport(transport);
    transport = null;
  }

  private void stopTransport(NettyTransportServer target) {
    if (target == null) {
      return;
    }
    try {
      target.stop();
    } catch (TransportException exception) {
      Map<String, String> values = Collections.singletonMap("error", exception.error().code());
      getLogger()
          .severe(
              LocalizedMessages.of(Locale.getDefault())
                  .format(MessageKeys.LIFECYCLE_TRANSPORT_STOP_FAILED, values));
    }
  }

  private void closeFileApi(FileApiRuntime target) {
    if (target == null) {
      return;
    }
    try {
      target.close();
    } catch (IOException exception) {
      Map<String, String> values =
          Collections.singletonMap("error", String.valueOf(exception.getMessage()));
      getLogger()
          .severe(
              LocalizedMessages.of(Locale.getDefault())
                  .format(MessageKeys.LIFECYCLE_FILE_API_STOP_FAILED, values));
    }
  }

  private void closeAuditStore(AuditStore target) {
    if (target == null) {
      return;
    }
    try {
      target.close();
    } catch (AuditException exception) {
      Map<String, String> values = Collections.singletonMap("reason", exception.problemCode());
      getLogger()
          .severe(
              LocalizedMessages.of(Locale.getDefault())
                  .format(MessageKeys.LIFECYCLE_AUDIT_STOP_FAILED, values));
    }
  }
}
