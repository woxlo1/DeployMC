package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.spi.ConsoleBridge;
import io.github.woxlo1.deploymc.core.spi.ConsoleLine;
import io.github.woxlo1.deploymc.core.spi.LoadedPlugin;
import io.github.woxlo1.deploymc.core.spi.MessageSink;
import io.github.woxlo1.deploymc.core.spi.PlatformAdapter;
import io.github.woxlo1.deploymc.core.spi.PlatformScheduler;
import io.github.woxlo1.deploymc.core.spi.PluginInspector;
import io.github.woxlo1.deploymc.core.spi.ScheduledTask;
import io.github.woxlo1.deploymc.core.spi.ServerController;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Minimal Bukkit-family implementation of the platform SPI.
 *
 * <p>The adapter uses only the public Bukkit API available in 1.8.8. It never imports NMS or
 * CraftBukkit classes. Capabilities that need Log4j, region schedulers, or a restart supervisor are
 * reported as unavailable rather than guessed.
 */
public final class BukkitPlatformAdapter implements PlatformAdapter {

  private static final long MILLIS_PER_TICK = 50L;
  private static final long NEXT_TICK = 1L;

  private final JavaPlugin plugin;
  private final Server server;
  private final LocalizedMessages messages;
  private final Logger logger;
  private final PlatformInfo info;
  private final PlatformCapabilities capabilities;
  private final Path pluginsDirectory;
  private final Path dataDirectory;
  private final BukkitSchedulerAdapter scheduler;
  private final ConsoleBridge console;
  private final ServerController serverController;
  private final PluginInspector pluginInspector;
  private final MessageSink messageSink;
  private volatile boolean closed;

  public BukkitPlatformAdapter(JavaPlugin plugin, StartupPlan plan, LocalizedMessages messages) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(plan, "plan");
    this.messages = Objects.requireNonNull(messages, "messages");
    this.server = Objects.requireNonNull(plugin.getServer(), "server");
    this.logger = Objects.requireNonNull(plugin.getLogger(), "logger");

    BukkitPlatformVersion version =
        BukkitPlatformVersion.parse(text(server.getVersion()), text(server.getBukkitVersion()));
    this.info =
        new PlatformInfo(
            version.software(),
            version.implementation(),
            version.minecraftVersion(),
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"));
    this.capabilities = BukkitCapabilities.create();

    File dataFolder = plugin.getDataFolder().getAbsoluteFile();
    this.dataDirectory = dataFolder.toPath();
    this.pluginsDirectory = dataFolder.getParentFile().toPath();

    this.scheduler = new BukkitSchedulerAdapter();
    this.console = new BukkitConsoleBridge();
    this.serverController = new BukkitServerController();
    this.pluginInspector = new BukkitPluginInspector();
    this.messageSink = new BukkitMessageSink();
  }

  @Override
  public PlatformInfo info() {
    return info;
  }

  @Override
  public PlatformCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public Path pluginsDirectory() {
    return pluginsDirectory;
  }

  @Override
  public Path dataDirectory() {
    return dataDirectory;
  }

  @Override
  public PlatformScheduler scheduler() {
    return scheduler;
  }

  @Override
  public ConsoleBridge console() {
    return console;
  }

  @Override
  public ServerController server() {
    return serverController;
  }

  @Override
  public PluginInspector plugins() {
    return pluginInspector;
  }

  @Override
  public MessageSink messages() {
    return messageSink;
  }

  @Override
  public Logger logger() {
    return logger;
  }

  /** Releases every task this adapter created. Safe to call more than once. */
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    scheduler.shutdown();
    logger.info(messages.format(MessageKeys.LIFECYCLE_DISABLED));
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("Bukkit adapter is disabled");
    }
  }

  private static String text(String value) {
    return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
  }

  private static Map<String, String> values(String key, String value) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    result.put(key, value);
    return result;
  }

  private final class BukkitSchedulerAdapter implements PlatformScheduler {

    private final TrackedTasks<TrackedBukkitTask> tasks =
        new TrackedTasks<TrackedBukkitTask>(
            new Consumer<TrackedBukkitTask>() {
              @Override
              public void accept(TrackedBukkitTask task) {
                task.cancelFromRegistry();
              }
            });
    private volatile boolean schedulerClosed;

    @Override
    public void runGlobal(Runnable task) {
      Objects.requireNonNull(task, "task");
      submit(task, false, -1L);
    }

    @Override
    public void runAsync(Runnable task) {
      Objects.requireNonNull(task, "task");
      submit(task, true, -1L);
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delay, TimeUnit unit) {
      Objects.requireNonNull(task, "task");
      Objects.requireNonNull(unit, "unit");
      if (delay < 0) {
        throw new IllegalArgumentException("delay must not be negative");
      }
      long ticks = Math.max(NEXT_TICK, unit.toMillis(delay) / MILLIS_PER_TICK);
      return submit(task, false, ticks);
    }

    @Override
    public void shutdown() {
      schedulerClosed = true;
      tasks.cancelAll();
    }

    private TrackedBukkitTask submit(final Runnable task, boolean async, long ticks) {
      ensureSchedulerOpen();
      final TrackedBukkitTask[] wrapper = new TrackedBukkitTask[1];
      Runnable wrapped =
          new Runnable() {
            @Override
            public void run() {
              try {
                task.run();
              } finally {
                if (wrapper[0] != null) {
                  tasks.complete(wrapper[0]);
                }
              }
            }
          };

      BukkitTask created;
      if (ticks >= 0) {
        created = server.getScheduler().runTaskLater(plugin, wrapped, ticks);
      } else if (async) {
        created = server.getScheduler().runTaskAsynchronously(plugin, wrapped);
      } else {
        created = server.getScheduler().runTask(plugin, wrapped);
      }
      wrapper[0] = new TrackedBukkitTask(created, tasks);
      if (!tasks.register(wrapper[0])) {
        wrapper[0].cancelFromRegistry();
      }
      return wrapper[0];
    }

    private void ensureSchedulerOpen() {
      if (schedulerClosed || closed) {
        throw new IllegalStateException("scheduler is shut down");
      }
    }
  }

  private static final class TrackedBukkitTask implements ScheduledTask {

    private final BukkitTask handle;
    private final TrackedTasks<TrackedBukkitTask> registry;
    private volatile boolean taskCancelled;

    private TrackedBukkitTask(BukkitTask handle, TrackedTasks<TrackedBukkitTask> registry) {
      this.handle = handle;
      this.registry = registry;
    }

    @Override
    public boolean cancel() {
      if (taskCancelled) {
        return false;
      }
      taskCancelled = true;
      registry.complete(this);
      handle.cancel();
      return true;
    }

    @Override
    public boolean isCancelled() {
      return taskCancelled;
    }

    private void cancelFromRegistry() {
      if (taskCancelled) {
        return;
      }
      taskCancelled = true;
      handle.cancel();
    }
  }

  private final class BukkitConsoleBridge implements ConsoleBridge {

    @Override
    public AutoCloseable subscribe(Consumer<ConsoleLine> listener) {
      Objects.requireNonNull(listener, "listener");
      throw new UnsupportedOperationException(
          messages.format(
              MessageKeys.MESSAGE_CAPABILITY_UNAVAILABLE,
              values("capability", CapabilityKeys.LOG_CAPTURE)));
    }

    @Override
    public void dispatchCommand(String command) {
      Objects.requireNonNull(command, "command");
      if (command.trim().isEmpty()) {
        throw new IllegalArgumentException("command must not be empty");
      }
      ensureOpen();
      final String copy = command;
      scheduler.runGlobal(
          new Runnable() {
            @Override
            public void run() {
              server.dispatchCommand(server.getConsoleSender(), copy);
            }
          });
    }
  }

  private final class BukkitServerController implements ServerController {

    @Override
    public void saveAll() {
      ensureOpen();
      scheduler.runGlobal(
          new Runnable() {
            @Override
            public void run() {
              for (World world : server.getWorlds()) {
                world.save();
              }
              server.savePlayers();
            }
          });
    }

    @Override
    public void shutdown(String reason) {
      Objects.requireNonNull(reason, "reason");
      if (reason.trim().isEmpty()) {
        throw new IllegalArgumentException("reason must not be empty");
      }
      ensureOpen();
      logger.info(
          messages.format(MessageKeys.MESSAGE_SHUTDOWN_REQUESTED, values("reason", reason)));
      scheduler.runGlobal(
          new Runnable() {
            @Override
            public void run() {
              server.shutdown();
            }
          });
    }

    @Override
    public boolean restartSupported() {
      return false;
    }
  }

  private final class BukkitPluginInspector implements PluginInspector {

    @Override
    public List<LoadedPlugin> loadedPlugins() {
      ensureOpen();
      List<LoadedPlugin> result = new ArrayList<LoadedPlugin>();
      for (Plugin candidate : server.getPluginManager().getPlugins()) {
        PluginDescriptionFile description = candidate.getDescription();
        result.add(
            new LoadedPlugin(
                description.getName(),
                description.getVersion(),
                description.getMain(),
                candidate.isEnabled()));
      }
      return Collections.unmodifiableList(result);
    }
  }

  private final class BukkitMessageSink implements MessageSink {

    @Override
    public void send(String key, Map<String, String> placeholders) {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(placeholders, "placeholders");
      ensureOpen();
      logger.info(messages.format(key, placeholders));
    }
  }
}
