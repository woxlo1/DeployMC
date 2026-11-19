package io.github.woxlo1.deploymc.platform.bukkit;

/** Server-independent view of a command sender used by {@link ConfigCommandHandler}. */
public interface CommandOutput {

  boolean hasPermission(String permission);

  boolean isConsole();

  void send(String message);
}
