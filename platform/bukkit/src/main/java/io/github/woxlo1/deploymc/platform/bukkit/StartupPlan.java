package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated startup values shown in the banner before any server feature starts. */
public final class StartupPlan {

  private static final String TOKEN_MODE = "token";

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
    return of("127.0.0.1", 8080, false, TOKEN_MODE, pluginVersion);
  }

  /** Creates a validated plan from the loaded host configuration. */
  public static StartupPlan fromConfig(ConfigView view, String pluginVersion) {
    if (view == null) {
      throw new IllegalArgumentException("view");
    }
    return of(
        view.getString("bind.address", "127.0.0.1"),
        view.getInt("bind.port", 8080),
        view.getBoolean("bind.allow-non-loopback", false),
        view.getString("auth.mode", TOKEN_MODE),
        pluginVersion);
  }

  /** Creates a validated plan. Only host-provisioned token mode is implemented. */
  public static StartupPlan of(
      String address, int port, boolean allowNonLoopback, String authMode, String pluginVersion) {
    if (authMode == null) {
      throw new IllegalArgumentException("authMode");
    }
    Objects.requireNonNull(pluginVersion, "pluginVersion");
    if (!TOKEN_MODE.equals(authMode)) {
      Map<String, String> values = new LinkedHashMap<String, String>();
      values.put("mode", authMode);
      throw new StartupConfigurationException(MessageKeys.STARTUP_AUTH_UNSUPPORTED, values);
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
