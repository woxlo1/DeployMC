package io.github.woxlo1.deploymc.fixture;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Healthy test-only plugin used to prove enable, config creation, version reporting, and JAR
 * replacement with a stable plugin identity.
 *
 * <p>The strings are deliberate test signals and are not product UI.
 */
public final class VersionedTestPlugin extends JavaPlugin {

  private static final String COMMAND = "dmctest";

  @Override
  public void onEnable() {
    saveDefaultConfig();
    getLogger()
        .info(
            "deploymc-test-a enabled; version="
                + getDescription().getVersion()
                + "; config="
                + getConfig().getString("fixture-message", "unset"));
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!COMMAND.equalsIgnoreCase(command.getName())) {
      return false;
    }
    sender.sendMessage("deploymc-test-a " + getDescription().getVersion());
    return true;
  }
}
