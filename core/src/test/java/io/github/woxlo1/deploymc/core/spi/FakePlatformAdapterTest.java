package io.github.woxlo1.deploymc.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.CapabilityState;
import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Exercises the SPI contract without a server; no real platform classes are imported. */
class FakePlatformAdapterTest {

  private static final Path ROOT = Paths.get("fake-root");

  @Test
  void reportsIdentityAndDirectories() {
    PlatformInfo info = new PlatformInfo("fake-platform", "0.0.0", "0.0.0", "fake-java", "fake-os");
    Logger logger = Logger.getLogger("FakePlatformAdapterTest");
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put(CapabilityKeys.LOG_CAPTURE, CapabilityState.AVAILABLE);

    FakePlatformAdapter adapter =
        new FakePlatformAdapter(
            info,
            PlatformCapabilities.of(states),
            ROOT.resolve("plugins"),
            ROOT.resolve("data"),
            Collections.<LoadedPlugin>emptyList(),
            true,
            logger);

    assertSame(info, adapter.info());
    assertSame(logger, adapter.logger());
    assertEquals(ROOT.resolve("plugins"), adapter.pluginsDirectory());
    assertEquals(ROOT.resolve("data"), adapter.dataDirectory());
    assertTrue(adapter.server().restartSupported());
  }

  @Test
  void reportsConfiguredAndUnknownCapabilities() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put(CapabilityKeys.LOG_CAPTURE, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.SERVER_RESTART_SCRIPT, CapabilityState.UNAVAILABLE);
    FakePlatformAdapter adapter =
        new FakePlatformAdapter(
            adapterInfo(),
            PlatformCapabilities.of(states),
            ROOT,
            ROOT,
            Collections.<LoadedPlugin>emptyList(),
            false,
            adapterLogger());

    assertTrue(adapter.capabilities().isAvailable(CapabilityKeys.LOG_CAPTURE));
    assertFalse(adapter.capabilities().isAvailable(CapabilityKeys.SERVER_RESTART_SCRIPT));
    assertEquals(
        CapabilityState.UNKNOWN, adapter.capabilities().state(CapabilityKeys.PLAYERS_LOCALE));

    FakePlatformAdapter empty = FakePlatformAdapter.create(ROOT);
    assertEquals(CapabilityState.UNKNOWN, empty.capabilities().state(CapabilityKeys.LOG_CAPTURE));
    assertFalse(empty.capabilities().isAvailable(CapabilityKeys.LOG_CAPTURE));
  }

  @Test
  void consoleDeliversLinesUntilSubscriptionCloses() throws Exception {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);
    final List<ConsoleLine> lines = new ArrayList<ConsoleLine>();

    AutoCloseable subscription = adapter.console().subscribe(lines::add);
    assertEquals(1, adapter.console().listenerCount());
    adapter.console().emit("first line");
    assertEquals(1, lines.size());
    assertEquals("first line", lines.get(0).message());
    assertEquals("INFO", lines.get(0).level());

    subscription.close();
    subscription.close();
    adapter.console().emit("second line");
    assertEquals(0, adapter.console().listenerCount());
    assertEquals(1, lines.size());
  }

  @Test
  void consoleRecordsCommandsAndRejectsEmptyInput() {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);

    adapter.console().dispatchCommand("say hello");

    assertEquals(Collections.singletonList("say hello"), adapter.console().dispatchedCommands());
    assertThrows(IllegalArgumentException.class, () -> adapter.console().dispatchCommand("  "));
  }

  @Test
  void schedulerRunsImmediateWorkAndQueuesDelayedWork() {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);
    final List<String> order = new ArrayList<String>();

    adapter.scheduler().runGlobal(() -> order.add("global"));
    adapter.scheduler().runAsync(() -> order.add("async"));
    ScheduledTask delayed =
        adapter.scheduler().runLater(() -> order.add("later"), 5, TimeUnit.SECONDS);

    assertEquals(Arrays.asList("global", "async"), order);
    assertEquals(Arrays.asList("global", "async"), adapter.scheduler().executions());
    assertEquals(1, adapter.scheduler().tasks().size());
    assertEquals(5L, ((FakePlatformAdapter.FakeTask) delayed).delay());
    assertEquals(TimeUnit.SECONDS, ((FakePlatformAdapter.FakeTask) delayed).unit());
    assertFalse(delayed.isCancelled());
    assertFalse(((FakePlatformAdapter.FakeTask) delayed).isExecuted());

    adapter.scheduler().runPending();

    assertEquals(Arrays.asList("global", "async", "later"), order);
    assertTrue(((FakePlatformAdapter.FakeTask) delayed).isExecuted());
    assertFalse(delayed.cancel());
  }

  @Test
  void cancellationPreventsDelayedWork() {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);
    final List<String> executed = new ArrayList<String>();
    ScheduledTask task =
        adapter.scheduler().runLater(() -> executed.add("no"), 1, TimeUnit.MINUTES);

    assertTrue(task.cancel());
    assertTrue(task.isCancelled());
    assertFalse(task.cancel());
    adapter.scheduler().runPending();

    assertTrue(executed.isEmpty());
  }

  @Test
  void schedulerShutdownRejectsNewWorkAndCancelsPendingWork() {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);
    final List<String> executed = new ArrayList<String>();
    ScheduledTask task =
        adapter.scheduler().runLater(() -> executed.add("no"), 1, TimeUnit.MINUTES);

    adapter.scheduler().shutdown();
    adapter.scheduler().runPending();

    assertTrue(task.isCancelled());
    assertTrue(executed.isEmpty());
    assertThrows(IllegalStateException.class, () -> adapter.scheduler().runGlobal(() -> {}));
    assertThrows(IllegalStateException.class, () -> adapter.scheduler().runAsync(() -> {}));
    assertThrows(
        IllegalStateException.class,
        () -> adapter.scheduler().runLater(() -> {}, 1, TimeUnit.SECONDS));
  }

  @Test
  void serverRecordsLifecycleRequests() {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);

    adapter.server().saveAll();
    adapter.server().shutdown("maintenance");

    assertEquals(1, adapter.server().saveCount());
    assertEquals(Collections.singletonList("maintenance"), adapter.server().shutdownReasons());
    assertFalse(adapter.server().restartSupported());
    assertThrows(IllegalArgumentException.class, () -> adapter.server().shutdown("  "));
  }

  @Test
  void pluginSnapshotIsImmutable() {
    LoadedPlugin plugin = new LoadedPlugin("ExamplePlugin", "1.0.0", "example.Main", true);
    FakePlatformAdapter adapter =
        new FakePlatformAdapter(
            adapterInfo(),
            PlatformCapabilities.empty(),
            ROOT,
            ROOT,
            Collections.singletonList(plugin),
            false,
            adapterLogger());

    assertEquals(Collections.singletonList(plugin), adapter.plugins().loadedPlugins());
    assertThrows(
        UnsupportedOperationException.class, () -> adapter.plugins().loadedPlugins().clear());
  }

  @Test
  void messagesCopyPlaceholdersAndRejectEmptyKeys() {
    FakePlatformAdapter adapter = FakePlatformAdapter.create(ROOT);
    Map<String, String> placeholders = new LinkedHashMap<String, String>();
    placeholders.put("path", "plugins/example.jar");

    adapter.messages().send("deploy.success", placeholders);
    placeholders.put("path", "changed");

    assertEquals(1, adapter.messages().sent().size());
    assertEquals("deploy.success", adapter.messages().sent().get(0).key());
    assertEquals(
        "plugins/example.jar", adapter.messages().sent().get(0).placeholders().get("path"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> adapter.messages().sent().get(0).placeholders().put("key", "value"));
    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.messages().send(" ", Collections.<String, String>emptyMap()));
  }

  private static PlatformInfo adapterInfo() {
    return new PlatformInfo("fake-platform", "0.0.0", "0.0.0", "fake-java", "fake-os");
  }

  private static Logger adapterLogger() {
    return Logger.getLogger("FakePlatformAdapterTest");
  }
}
