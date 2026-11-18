package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.api.CapabilityState;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the truthful capability map for the minimal Bukkit-family adapter. */
public final class BukkitCapabilities {

  private BukkitCapabilities() {}

  /**
   * Returns capabilities this adapter actually implements. Everything else is explicitly
   * unavailable or unknown; no capability is inferred from the presence of a Bukkit class.
   */
  public static PlatformCapabilities create() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put(CapabilityKeys.LOG_CAPTURE, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.LOG_CAPTURE_STRUCTURED, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.COMMAND_DISPATCH, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.COMMAND_OUTPUT, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.SERVER_SAVE, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.SERVER_SHUTDOWN, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.SERVER_RESTART_SCRIPT, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.PLUGINS_ENUMERATE, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.PLUGINS_ENABLE_STATE, CapabilityState.AVAILABLE);
    states.put(CapabilityKeys.PLUGINS_LOAD_ERRORS, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.UPDATE_FOLDER, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.SCHEDULER_REGION, CapabilityState.UNAVAILABLE);
    states.put(CapabilityKeys.PLAYERS_LOCALE, CapabilityState.UNKNOWN);
    states.put(CapabilityKeys.PLAYERS_BROADCAST, CapabilityState.UNAVAILABLE);
    return PlatformCapabilities.of(states);
  }
}
