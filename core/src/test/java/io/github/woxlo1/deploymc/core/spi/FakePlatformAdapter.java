package io.github.woxlo1.deploymc.core.spi;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * In-memory {@link PlatformAdapter} for core tests. It never touches a server or the file system.
 *
 * <p>Delayed work is queued rather than executed by a background thread, so tests remain
 * deterministic. Immediate scheduling runs on the calling thread. This fixture is intentionally
 * part of the test source set: it is a contract test double, not a usable platform adapter.
 */
public final class FakePlatformAdapter implements PlatformAdapter {

  private final PlatformInfo info;
  private final PlatformCapabilities capabilities;
  private final Path pluginsDirectory;
  private final Path dataDirectory;
  private final FakeScheduler scheduler = new FakeScheduler();
  private final FakeConsole console = new FakeConsole();
  private final FakeServer server;
  private final FakePluginInspector plugins;
  private final FakeMessages messages = new FakeMessages();
  private final Logger logger;

  public FakePlatformAdapter(
      PlatformInfo info,
      PlatformCapabilities capabilities,
      Path pluginsDirectory,
      Path dataDirectory,
      List<LoadedPlugin> plugins,
      boolean restartSupported,
      Logger logger) {
    this.info = Objects.requireNonNull(info, "info");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
    this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    this.plugins = new FakePluginInspector(plugins);
    this.server = new FakeServer(restartSupported);
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  /** Creates a fixture with empty capabilities, no plugins, and fake identity values. */
  public static FakePlatformAdapter create(Path root) {
    Objects.requireNonNull(root, "root");
    return new FakePlatformAdapter(
        new PlatformInfo("fake-platform", "0.0.0-test", "0.0.0-test", "fake-java", "fake-os"),
        PlatformCapabilities.empty(),
        root.resolve("plugins"),
        root.resolve("data"),
        Collections.<LoadedPlugin>emptyList(),
        false,
        Logger.getLogger("FakePlatformAdapter"));
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
  public FakeScheduler scheduler() {
    return scheduler;
  }

  @Override
  public FakeConsole console() {
    return console;
  }

  @Override
  public FakeServer server() {
    return server;
  }

  @Override
  public FakePluginInspector plugins() {
    return plugins;
  }

  @Override
  public FakeMessages messages() {
    return messages;
  }

  @Override
  public Logger logger() {
    return logger;
  }

  /** Deterministic scheduler double. */
  public static final class FakeScheduler implements PlatformScheduler {

    private final List<FakeTask> tasks = new ArrayList<FakeTask>();
    private final List<String> executions = new ArrayList<String>();
    private boolean shutdown;

    @Override
    public void runGlobal(Runnable task) {
      runNow("global", task);
    }

    @Override
    public void runAsync(Runnable task) {
      runNow("async", task);
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delay, TimeUnit unit) {
      Objects.requireNonNull(task, "task");
      Objects.requireNonNull(unit, "unit");
      ensureOpen();
      if (delay < 0) {
        throw new IllegalArgumentException("delay must not be negative");
      }
      FakeTask scheduled = new FakeTask(task, delay, unit);
      tasks.add(scheduled);
      return scheduled;
    }

    @Override
    public void shutdown() {
      shutdown = true;
      for (FakeTask task : tasks) {
        task.cancel();
      }
    }

    /** Executes every pending delayed task in submission order. */
    public void runPending() {
      for (FakeTask task : new ArrayList<FakeTask>(tasks)) {
        task.runIfPending();
      }
    }

    /** Returns the kinds of immediate tasks executed so far, in order. */
    public List<String> executions() {
      return Collections.unmodifiableList(new ArrayList<String>(executions));
    }

    public List<FakeTask> tasks() {
      return Collections.unmodifiableList(new ArrayList<FakeTask>(tasks));
    }

    private void runNow(String kind, Runnable task) {
      Objects.requireNonNull(task, "task");
      ensureOpen();
      executions.add(kind);
      task.run();
    }

    private void ensureOpen() {
      if (shutdown) {
        throw new IllegalStateException("scheduler is shut down");
      }
    }
  }

  /** Cancellable delayed-task handle. */
  public static final class FakeTask implements ScheduledTask {

    private final Runnable task;
    private final long delay;
    private final TimeUnit unit;
    private boolean pending = true;
    private boolean cancelled;

    private FakeTask(Runnable task, long delay, TimeUnit unit) {
      this.task = task;
      this.delay = delay;
      this.unit = unit;
    }

    @Override
    public boolean cancel() {
      if (!pending || cancelled) {
        return false;
      }
      pending = false;
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    public boolean isExecuted() {
      return !pending && !cancelled;
    }

    public long delay() {
      return delay;
    }

    public TimeUnit unit() {
      return unit;
    }

    private void runIfPending() {
      if (!pending) {
        return;
      }
      pending = false;
      task.run();
    }
  }

  /** Console double that records commands and delivers lines to subscribers. */
  public static final class FakeConsole implements ConsoleBridge {

    private final List<Consumer<ConsoleLine>> listeners = new ArrayList<Consumer<ConsoleLine>>();
    private final List<String> dispatched = new ArrayList<String>();

    @Override
    public AutoCloseable subscribe(Consumer<ConsoleLine> listener) {
      Objects.requireNonNull(listener, "listener");
      listeners.add(listener);
      return new Subscription(listener);
    }

    @Override
    public void dispatchCommand(String command) {
      if (command == null || command.trim().isEmpty()) {
        throw new IllegalArgumentException("command must not be empty");
      }
      dispatched.add(command);
    }

    /** Delivers a line to every current subscriber. */
    public void emit(ConsoleLine line) {
      Objects.requireNonNull(line, "line");
      for (Consumer<ConsoleLine> listener : new ArrayList<Consumer<ConsoleLine>>(listeners)) {
        listener.accept(line);
      }
    }

    /** Delivers a simple INFO line with obviously fake metadata. */
    public void emit(String message) {
      emit(new ConsoleLine(1L, "INFO", "fake-logger", "fake-thread", message));
    }

    public int listenerCount() {
      return listeners.size();
    }

    public List<String> dispatchedCommands() {
      return Collections.unmodifiableList(new ArrayList<String>(dispatched));
    }

    private final class Subscription implements AutoCloseable {

      private final Consumer<ConsoleLine> listener;
      private boolean closed;

      private Subscription(Consumer<ConsoleLine> listener) {
        this.listener = listener;
      }

      @Override
      public void close() {
        if (closed) {
          return;
        }
        closed = true;
        Iterator<Consumer<ConsoleLine>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
          if (iterator.next() == listener) {
            iterator.remove();
            return;
          }
        }
      }
    }
  }

  /** Server lifecycle double that records requests without stopping the JVM. */
  public static final class FakeServer implements ServerController {

    private final boolean restartSupported;
    private int saveCount;
    private final List<String> shutdownReasons = new ArrayList<String>();

    private FakeServer(boolean restartSupported) {
      this.restartSupported = restartSupported;
    }

    @Override
    public void saveAll() {
      saveCount++;
    }

    @Override
    public void shutdown(String reason) {
      if (reason == null || reason.trim().isEmpty()) {
        throw new IllegalArgumentException("reason must not be empty");
      }
      shutdownReasons.add(reason);
    }

    @Override
    public boolean restartSupported() {
      return restartSupported;
    }

    public int saveCount() {
      return saveCount;
    }

    public List<String> shutdownReasons() {
      return Collections.unmodifiableList(new ArrayList<String>(shutdownReasons));
    }
  }

  /** Plugin inspection double backed by an immutable snapshot. */
  public static final class FakePluginInspector implements PluginInspector {

    private final List<LoadedPlugin> plugins;

    private FakePluginInspector(List<LoadedPlugin> plugins) {
      Objects.requireNonNull(plugins, "plugins");
      this.plugins = Collections.unmodifiableList(new ArrayList<LoadedPlugin>(plugins));
    }

    @Override
    public List<LoadedPlugin> loadedPlugins() {
      return plugins;
    }
  }

  /** Message double that records keys and copied placeholders. */
  public static final class FakeMessages implements MessageSink {

    private final List<SentMessage> sent = new ArrayList<SentMessage>();

    @Override
    public void send(String key, Map<String, String> placeholders) {
      if (key == null || key.trim().isEmpty()) {
        throw new IllegalArgumentException("key must not be empty");
      }
      Objects.requireNonNull(placeholders, "placeholders");
      sent.add(new SentMessage(key, placeholders));
    }

    public List<SentMessage> sent() {
      return Collections.unmodifiableList(new ArrayList<SentMessage>(sent));
    }
  }

  /** Immutable record of one message delivery. */
  public static final class SentMessage {

    private final String key;
    private final Map<String, String> placeholders;

    private SentMessage(String key, Map<String, String> placeholders) {
      this.key = key;
      this.placeholders =
          Collections.unmodifiableMap(new LinkedHashMap<String, String>(placeholders));
    }

    public String key() {
      return key;
    }

    public Map<String, String> placeholders() {
      return placeholders;
    }
  }
}
