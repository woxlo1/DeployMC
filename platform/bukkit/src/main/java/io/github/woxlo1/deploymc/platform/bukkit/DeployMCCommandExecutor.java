package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;

/** Adapts the server-independent command handler to Bukkit's command API. */
final class DeployMCCommandExecutor implements CommandExecutor, TabCompleter {

  private final ConfigCommandHandler handler;

  DeployMCCommandExecutor(ConfigCommandHandler handler) {
    if (handler == null) {
      throw new IllegalArgumentException("handler");
    }
    this.handler = handler;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    return handler.handle(new BukkitCommandOutput(sender), args);
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    return handler.complete(new BukkitCommandOutput(sender), args);
  }

  private static final class BukkitCommandOutput implements CommandOutput {

    private final CommandSender sender;

    private BukkitCommandOutput(CommandSender sender) {
      this.sender = sender;
    }

    @Override
    public boolean hasPermission(String permission) {
      return sender.hasPermission(permission);
    }

    @Override
    public boolean isConsole() {
      return sender instanceof ConsoleCommandSender;
    }

    @Override
    public Optional<Locale> locale() {
      return SenderLocaleResolver.resolve(sender);
    }

    @Override
    public void send(String message) {
      sender.sendMessage(LegacyColorFormatter.translate(message));
    }
  }
}
