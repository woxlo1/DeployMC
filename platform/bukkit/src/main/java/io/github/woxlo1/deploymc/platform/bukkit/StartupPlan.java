package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Validated startup values shown in the banner before any server feature starts. */
public final class StartupPlan {

  private static final List<String> SUPPORTED_AUTH_MODES =
      Collections.unmodifiableList(Arrays.asList("cloudflare", "token", "local-onetime"));

  private final HttpBindConfig bind;
  private final String authMode;
  private final String pluginVersion;

  private StartupPlan(HttpBindConfig bind, String authMode, String pluginVersion) {
    this.bind = bind;
    this.authMode = authMode;
    this.pluginVersion = pluginVersion;
  }

  /** Creates the documented safe defaults from the configuration specification. */
  public static StartupPlan defaults(String pluginVersion) {
    return of("127.0.0.1", 8080, false, "cloudflare", pluginVersion);
  }

  /** Creates a validated plan. An unsupported authentication mode is rejected. */
  public static StartupPlan of(
      String address, int port, boolean allowNonLoopback, String authMode, String pluginVersion) {
    Objects.requireNonNull(authMode, "authMode");
    Objects.requireNonNull(pluginVersion, "pluginVersion");
    if (!SUPPORTED_AUTH_MODES.contains(authMode)) {
      throw new IllegalArgumentException("unsupported auth mode: " + authMode);
    }
    if (pluginVersion.trim().isEmpty()) {
      throw new IllegalArgumentException("pluginVersion must not be empty");
    }
    return new StartupPlan(
        HttpBindConfig.of(address, port, allowNonLoopback), authMode, pluginVersion);
  }

  public HttpBindConfig bind() {
    return bind;
  }

  public String authMode() {
    return authMode;
  }

  public String pluginVersion() {
    return pluginVersion;
  }

  /** Returns message keys for warnings the banner must print, in stable order. */
  public List<String> warningKeys() {
    List<String> keys = new ArrayList<String>();
    if (!bind.isLoopbackOnly()) {
      keys.add(MessageKeys.WARNING_NON_LOOPBACK);
    }
    keys.add(MessageKeys.WARNING_NO_RESTART_SCRIPT);
    return Collections.unmodifiableList(keys);
  }
}
