package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.Locale;
import java.util.Optional;

/** Server-independent view of a command sender used by {@link ConfigCommandHandler}. */
public interface CommandOutput {

  boolean hasPermission(String permission);

  boolean isConsole();

  void send(String message);

  /** Returns a platform-provided sender locale, or empty when the platform does not expose one. */
  default Optional<Locale> locale() {
    return Optional.empty();
  }
}
