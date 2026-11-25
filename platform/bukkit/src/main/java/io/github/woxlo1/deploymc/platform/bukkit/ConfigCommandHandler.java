package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.auth.CreatedToken;
import io.github.woxlo1.deploymc.core.auth.TokenStoreException;
import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigValidationResult;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.ReloadReport;
import io.github.woxlo1.deploymc.core.doctor.DoctorReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Server-independent logic for {@code /deploymc}. */
public final class ConfigCommandHandler {

  private static final List<String> SUBCOMMANDS =
      Collections.unmodifiableList(Arrays.asList("status", "reload", "doctor", "token"));

  private final ConfigManager manager;
  private final StartupPlan plan;
  private final AuthenticationRuntime authentication;
  private final DoctorRuntime doctor;

  public ConfigCommandHandler(ConfigManager manager, StartupPlan plan) {
    this(manager, plan, null, null);
  }

  public ConfigCommandHandler(
      ConfigManager manager, StartupPlan plan, AuthenticationRuntime authentication) {
    this(manager, plan, authentication, null);
  }

  public ConfigCommandHandler(
      ConfigManager manager,
      StartupPlan plan,
      AuthenticationRuntime authentication,
      DoctorRuntime doctor) {
    if (manager == null) {
      throw new IllegalArgumentException("manager");
    }
    if (plan == null) {
      throw new IllegalArgumentException("plan");
    }
    this.manager = manager;
    this.plan = plan;
    this.authentication = authentication;
    this.doctor = doctor;
  }

  /** Handles one invocation. Always returns {@code true} so Bukkit does not show generic usage. */
  public boolean handle(CommandOutput output, String[] arguments) {
    LocalizedMessages messages = messages(output);
    if (!output.hasPermission(CommandPermissions.ROOT)) {
      output.send(
          messages.format(
              MessageKeys.COMMAND_PERMISSION_DENIED,
              values("permission", CommandPermissions.ROOT)));
      return true;
    }
    String[] args = arguments == null ? new String[0] : arguments;
    if (args.length == 0 || !SUBCOMMANDS.contains(args[0])) {
      output.send(messages.format(MessageKeys.COMMAND_USAGE));
      return true;
    }
    String subcommand = args[0];
    String permission = permissionFor(subcommand);
    if (!output.hasPermission(permission)) {
      output.send(
          messages.format(MessageKeys.COMMAND_PERMISSION_DENIED, values("permission", permission)));
      return true;
    }
    if ("status".equals(subcommand)) {
      status(output, messages);
    } else if ("reload".equals(subcommand)) {
      reload(output, messages);
    } else if ("doctor".equals(subcommand)) {
      doctor(output, messages);
    } else {
      token(output, messages, args);
    }
    return true;
  }

  /** Returns permitted subcommands for tab completion. */
  public List<String> complete(CommandOutput output, String[] arguments) {
    if (!output.hasPermission(CommandPermissions.ROOT)) {
      return Collections.emptyList();
    }
    if (arguments != null && arguments.length > 1) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<String>();
    for (String subcommand : SUBCOMMANDS) {
      if (output.hasPermission(permissionFor(subcommand))) {
        result.add(subcommand);
      }
    }
    return Collections.unmodifiableList(result);
  }

  private void status(CommandOutput output, LocalizedMessages messages) {
    Optional<ConfigView> current = manager.current();
    if (!current.isPresent()) {
      output.send(messages.format(MessageKeys.COMMAND_UNAVAILABLE));
      return;
    }
    ConfigView view = current.get();
    output.send(messages.format(MessageKeys.COMMAND_STATUS_TITLE));
    output.send(
        messages.format(
            MessageKeys.COMMAND_STATUS_VERSION, values("version", plan.pluginVersion())));
    output.send(
        messages.format(
            MessageKeys.COMMAND_STATUS_CONFIG_VERSION,
            values("version", Integer.toString(view.configVersion()))));
    output.send(
        messages.format(MessageKeys.COMMAND_STATUS_LANGUAGE, values("language", view.language())));
    output.send(
        messages.format(
            MessageKeys.STARTUP_BIND,
            values(
                "address", plan.bind().address(), "port", Integer.toString(plan.bind().port()))));
    output.send(messages.format(MessageKeys.STARTUP_AUTH, values("authMode", plan.authMode())));
    output.send(
        messages.format(
            MessageKeys.COMMAND_STATUS_WARNINGS,
            values("count", Integer.toString(warningCount()))));
    output.send(
        messages.format(
            MessageKeys.COMMAND_STATUS_CONFIG_FILE,
            values("path", manager.configFile().toString())));
  }

  private void reload(CommandOutput output, LocalizedMessages messages) {
    try {
      ReloadReport report = manager.reload();
      output.send(messages.format(MessageKeys.COMMAND_RELOAD_SUCCESS));
      if (report.changedPaths().isEmpty()) {
        output.send(messages.format(MessageKeys.COMMAND_RELOAD_NO_CHANGES));
      } else {
        output.send(
            messages.format(
                MessageKeys.COMMAND_RELOAD_CHANGED, values("paths", join(report.changedPaths()))));
      }
      if (!report.restartRequiredPaths().isEmpty()) {
        output.send(
            messages.format(
                MessageKeys.COMMAND_RELOAD_RESTART_REQUIRED,
                values("paths", join(report.restartRequiredPaths()))));
      }
      if (!report.warnings().isEmpty()) {
        output.send(
            messages.format(
                MessageKeys.COMMAND_RELOAD_WARNINGS,
                values("count", Integer.toString(report.warnings().size()))));
      }
    } catch (ConfigException exception) {
      output.send(
          messages.format(
              MessageKeys.COMMAND_RELOAD_FAILED,
              values("error", String.valueOf(exception.getMessage()))));
    }
  }

  private void doctor(CommandOutput output, LocalizedMessages messages) {
    output.send(messages.format(MessageKeys.COMMAND_DOCTOR_TITLE));
    if (doctor == null) {
      output.send(messages.format(MessageKeys.COMMAND_UNAVAILABLE));
      return;
    }
    DoctorReport report = doctor.run();
    for (String line : report.renderHuman(messages.locale())) {
      output.send(line);
    }
  }

  private void token(CommandOutput output, LocalizedMessages messages, String[] args) {
    if (!output.isConsole()) {
      output.send(messages.format(MessageKeys.COMMAND_CONSOLE_ONLY));
      return;
    }
    if (args.length < 2 || !"create".equals(args[1])) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_USAGE));
      return;
    }
    if (authentication == null || !authentication.tokenProvisioningEnabled()) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_UNAVAILABLE));
      return;
    }
    String label = args.length > 2 ? join(Arrays.asList(args).subList(2, args.length)) : "console";
    try {
      CreatedToken created =
          authentication.provisioner().create(label, authentication.defaultTokenLifetime());
      output.send(
          messages.format(
              MessageKeys.COMMAND_TOKEN_CREATED,
              values("id", created.record().id(), "label", created.record().label())));
      output.send(
          messages.format(MessageKeys.COMMAND_TOKEN_TOKEN_LINE, values("token", created.token())));
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_ADMIN_EQUIVALENT));
    } catch (TokenStoreException | RuntimeException exception) {
      output.send(
          messages.format(
              MessageKeys.COMMAND_TOKEN_FAILED,
              values("error", String.valueOf(exception.getMessage()))));
    }
  }

  private LocalizedMessages messages(CommandOutput output) {
    LocalizedMessages serverMessages = serverMessages();
    Optional<Locale> senderLocale = output.locale();
    if (!senderLocale.isPresent()) {
      return serverMessages;
    }
    return LocalizedMessages.of(senderLocale.get(), serverMessages.locale());
  }

  private LocalizedMessages serverMessages() {
    Optional<ConfigView> current = manager.current();
    String language = current.isPresent() ? current.get().language() : Locale.ROOT.getLanguage();
    return LocalizedMessages.of(ConfigLanguage.toLocale(language));
  }

  private int warningCount() {
    Optional<ConfigValidationResult> validation = manager.lastValidation();
    return validation.isPresent() ? validation.get().warnings().size() : 0;
  }

  private static String join(List<String> paths) {
    StringBuilder result = new StringBuilder();
    for (String path : paths) {
      if (result.length() > 0) {
        result.append(", ");
      }
      result.append(path);
    }
    return result.toString();
  }

  private static String permissionFor(String subcommand) {
    if ("status".equals(subcommand)) {
      return CommandPermissions.STATUS;
    }
    if ("reload".equals(subcommand)) {
      return CommandPermissions.RELOAD;
    }
    if ("doctor".equals(subcommand)) {
      return CommandPermissions.DOCTOR;
    }
    return CommandPermissions.TOKEN;
  }

  private static Map<String, String> values(String... pairs) {
    if (pairs.length % 2 != 0) {
      throw new IllegalArgumentException("placeholder pairs must be even");
    }
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (int index = 0; index < pairs.length; index += 2) {
      result.put(pairs[index], pairs[index + 1]);
    }
    return result;
  }
}
