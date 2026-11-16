package io.github.woxlo1.deploymc.core.spi;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Single entry point implemented by every platform adapter.
 *
 * <p>Core must depend only on this interface and never on Minecraft or platform classes.
 */
public interface PlatformAdapter {

  PlatformInfo info();

  PlatformCapabilities capabilities();

  Path pluginsDirectory();

  Path dataDirectory();

  PlatformScheduler scheduler();

  ConsoleBridge console();

  ServerController server();

  PluginInspector plugins();

  MessageSink messages();

  Logger logger();
}
