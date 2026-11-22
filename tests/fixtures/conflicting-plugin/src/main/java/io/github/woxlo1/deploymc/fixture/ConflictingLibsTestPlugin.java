package io.github.woxlo1.deploymc.fixture;

import io.netty.util.ReferenceCountUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Compatibility fixture only.
 *
 * <p>This plugin deliberately bundles unrelocated Netty 4.1.100.Final, while DeployMC relocates
 * Netty 4.1.141.Final into its own namespace. Logging the loaded class name proves that the fixture
 * used its own copy without affecting DeployMC's shaded copy. It also exposes a console-only
 * command that disables DeployMC through the Bukkit API, so the harness can compare thread dumps
 * before and after an explicit plugin disable rather than relying on a server shutdown.
 */
public final class ConflictingLibsTestPlugin extends JavaPlugin {

  private static final String TARGET_PLUGIN = "DeployMC";
  private static final String DISABLE_COMMAND = "fixture-disable-deploymc";

  @Override
  public void onEnable() {
    getLogger()
        .info(
            "DeployMCTestConflictingLibs enabled; bundled class="
                + ReferenceCountUtil.class.getName());
    PluginCommand command = getCommand(DISABLE_COMMAND);
    if (command != null) {
      command.setExecutor(this);
    }
  }

  @Override
  public boolean onCommand(
      org.bukkit.command.CommandSender sender,
      org.bukkit.command.Command command,
      String label,
      String[] args) {
    if (!DISABLE_COMMAND.equalsIgnoreCase(command.getName())) {
      return false;
    }
    Plugin target = getServer().getPluginManager().getPlugin(TARGET_PLUGIN);
    if (target != null && target.isEnabled()) {
      getServer().getPluginManager().disablePlugin(target);
    }
    return true;
  }
}
