package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.auth.CreatedToken;
import io.github.woxlo1.deploymc.core.auth.TokenRecord;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import io.github.woxlo1.deploymc.core.auth.TokenStoreException;
import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigValidationResult;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.ReloadReport;
import io.github.woxlo1.deploymc.core.doctor.DoctorReport;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicyManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Server-independent logic for {@code /deploymc}. */
public final class ConfigCommandHandler {

  private static final List<String> SUBCOMMANDS =
      Collections.unmodifiableList(Arrays.asList("status", "reload", "doctor", "token"));
  private static final List<String> TOKEN_ACTIONS =
      Collections.unmodifiableList(Arrays.asList("create", "revoke", "list"));
  private static final int MAX_TOKEN_ID_LENGTH = 64;

  private final ConfigManager manager;
  private final StartupPlan plan;
  private final AuthenticationRuntime authentication;
  private final DoctorRuntime doctor;
  private final RbacPolicyManager policies;
  private final Clock clock;

  public ConfigCommandHandler(ConfigManager manager, StartupPlan plan) {
    this(manager, plan, null, null, null);
  }

  public ConfigCommandHandler(
      ConfigManager manager, StartupPlan plan, AuthenticationRuntime authentication) {
    this(manager, plan, authentication, null, null);
  }

  public ConfigCommandHandler(
      ConfigManager manager,
      StartupPlan plan,
      AuthenticationRuntime authentication,
      DoctorRuntime doctor) {
    this(manager, plan, authentication, doctor, null);
  }

  public ConfigCommandHandler(
      ConfigManager manager,
      StartupPlan plan,
      AuthenticationRuntime authentication,
      DoctorRuntime doctor,
      RbacPolicyManager policies) {
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
    this.policies = policies;
    this.clock = Clock.systemUTC();
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

  /** Returns permitted subcommands and token actions for tab completion. */
  public List<String> complete(CommandOutput output, String[] arguments) {
    if (!output.hasPermission(CommandPermissions.ROOT)) {
      return Collections.emptyList();
    }
    String[] args = arguments == null ? new String[0] : arguments;
    if (args.length == 0) {
      return permitted(SUBCOMMANDS, output, "");
    }
    if (args.length == 1) {
      return permitted(SUBCOMMANDS, output, args[0]);
    }
    if (!"token".equals(args[0]) || !output.hasPermission(CommandPermissions.TOKEN)) {
      return Collections.emptyList();
    }
    if (args.length == 2) {
      return permitted(TOKEN_ACTIONS, output, args[1]);
    }
    if (args.length == 3 && "revoke".equals(args[1]) && tokenStore() != null) {
      List<String> ids = new ArrayList<String>();
      for (TokenRecord record : tokenStore().records()) {
        if (record.id().startsWith(args[2])) {
          ids.add(record.id());
        }
      }
      return Collections.unmodifiableList(ids);
    }
    if ("create".equals(args[1])) {
      String last = args[args.length - 1];
      if (last.startsWith("--owner=")) {
        List<String> suggestions = new ArrayList<String>();
        for (String owner : knownOwners()) {
          if (owner.startsWith(last.substring("--owner=".length()))) {
            suggestions.add("--owner=" + owner);
          }
        }
        return Collections.unmodifiableList(suggestions);
      }
      if ("--owner".equals(last)) {
        return Collections.unmodifiableList(new ArrayList<String>(knownOwners()));
      }
      if (args.length >= 4 && "--owner".equals(args[args.length - 2])) {
        List<String> suggestions = new ArrayList<String>();
        for (String owner : knownOwners()) {
          if (owner.startsWith(last)) {
            suggestions.add(owner);
          }
        }
        return Collections.unmodifiableList(suggestions);
      }
    }
    return Collections.emptyList();
  }

  private static List<String> permitted(
      List<String> candidates, CommandOutput output, String prefix) {
    List<String> result = new ArrayList<String>();
    for (String candidate : candidates) {
      if (candidate.startsWith(prefix) && output.hasPermission(permissionFor(candidate))) {
        result.add(candidate);
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
      if (policies != null) {
        policies.install(manager.current().get());
      }
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
      if (policies != null) {
        policies.denyAll();
      }
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
    if (args.length < 2 || !TOKEN_ACTIONS.contains(args[1])) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_USAGE));
      return;
    }
    if ("create".equals(args[1])) {
      createToken(output, messages, args);
    } else if ("revoke".equals(args[1])) {
      revokeToken(output, messages, args);
    } else {
      listTokens(output, messages);
    }
  }

  private void createToken(CommandOutput output, LocalizedMessages messages, String[] args) {
    if (tokenStore() == null) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_UNAVAILABLE));
      return;
    }
    Integer requestedTtl = null;
    List<String> scopes = new ArrayList<String>();
    String owner = null;
    StringBuilder label = new StringBuilder();
    for (int index = 2; index < args.length; index++) {
      String argument = args[index];
      if ("--ttl-days".equals(argument)) {
        if (++index >= args.length) {
          invalidValue(output, messages, "ttl-days");
          return;
        }
        requestedTtl = parseTtlDays(args[index], output, messages);
        if (requestedTtl == null) {
          return;
        }
      } else if (argument.startsWith("--ttl-days=")) {
        requestedTtl = parseTtlDays(argument.substring("--ttl-days=".length()), output, messages);
        if (requestedTtl == null) {
          return;
        }
      } else if ("--scopes".equals(argument)) {
        if (++index >= args.length) {
          invalidValue(output, messages, "scopes");
          return;
        }
        scopes = parseScopes(args[index], output, messages);
        if (scopes == null) {
          return;
        }
      } else if (argument.startsWith("--scopes=")) {
        scopes = parseScopes(argument.substring("--scopes=".length()), output, messages);
        if (scopes == null) {
          return;
        }
      } else if ("--owner".equals(argument)) {
        if (++index >= args.length) {
          output.send(messages.format(MessageKeys.COMMAND_TOKEN_OWNER_INVALID));
          return;
        }
        owner = args[index].trim();
      } else if (argument.startsWith("--owner=")) {
        owner = argument.substring("--owner=".length()).trim();
      } else if (argument.startsWith("--")) {
        invalidValue(output, messages, "option");
        return;
      } else {
        if (label.length() > 0) {
          label.append(' ');
        }
        label.append(argument);
      }
    }
    if (owner == null || owner.isEmpty() || !knownOwner(owner)) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_OWNER_INVALID));
      return;
    }

    int maxTtl = maxTtlDays();
    int effectiveTtl =
        requestedTtl == null
            ? (int) Math.max(1L, authentication.defaultTokenLifetime().toDays())
            : requestedTtl;
    effectiveTtl = Math.max(1, Math.min(effectiveTtl, maxTtl));
    try {
      CreatedToken created =
          authentication
              .provisioner()
              .create(
                  label.length() == 0 ? "console" : label.toString(),
                  owner,
                  Duration.ofDays(effectiveTtl),
                  scopes);
      output.send(
          messages.format(
              MessageKeys.COMMAND_TOKEN_CREATED,
              values("id", created.record().id(), "label", created.record().label())));
      output.send(
          messages.format(
              MessageKeys.COMMAND_TOKEN_EFFECTIVE_TTL,
              values("days", Integer.toString(effectiveTtl))));
      if (!created.record().scopes().isEmpty()) {
        output.send(
            messages.format(
                MessageKeys.COMMAND_TOKEN_STORED_SCOPES,
                values("scopes", join(created.record().scopes()))));
      }
      output.send(
          messages.format(MessageKeys.COMMAND_TOKEN_TOKEN_LINE, values("token", created.token())));
      output.send(
          messages.format(
              MessageKeys.COMMAND_TOKEN_EFFECTIVE_ACCESS,
              values(
                  "owner",
                  created.record().owner(),
                  "permissions",
                  effectivePermissions(created.record(), messages))));
    } catch (TokenStoreException | RuntimeException exception) {
      tokenFailed(output, messages);
    }
  }

  private void revokeToken(CommandOutput output, LocalizedMessages messages, String[] args) {
    TokenStore store = tokenStore();
    if (store == null) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_UNAVAILABLE));
      return;
    }
    if (args.length != 3 || args[2].trim().isEmpty() || args[2].length() > MAX_TOKEN_ID_LENGTH) {
      invalidValue(output, messages, "id");
      return;
    }
    String id = args[2];
    try {
      store.revoke(id, clock.instant());
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_REVOKED, values("id", id)));
    } catch (TokenStoreException exception) {
      if (exception.reason() == TokenStoreException.Reason.RECORD) {
        output.send(messages.format(MessageKeys.COMMAND_TOKEN_NOT_FOUND, values("id", id)));
      } else {
        tokenFailed(output, messages);
      }
    } catch (RuntimeException exception) {
      tokenFailed(output, messages);
    }
  }

  private void listTokens(CommandOutput output, LocalizedMessages messages) {
    TokenStore store = tokenStore();
    if (store == null) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_UNAVAILABLE));
      return;
    }
    List<TokenRecord> records = store.records();
    if (records.isEmpty()) {
      output.send(messages.format(MessageKeys.COMMAND_TOKEN_LIST_EMPTY));
      return;
    }
    output.send(
        messages.format(
            MessageKeys.COMMAND_TOKEN_LIST_TITLE,
            values("count", Integer.toString(records.size()))));
    Instant now = clock.instant();
    for (TokenRecord record : records) {
      String statusKey =
          record.isRevoked()
              ? MessageKeys.COMMAND_TOKEN_STATUS_REVOKED
              : (record.isExpired(now)
                  ? MessageKeys.COMMAND_TOKEN_STATUS_EXPIRED
                  : MessageKeys.COMMAND_TOKEN_STATUS_ACTIVE);
      String expiresAt =
          record.expiresAt() == null
              ? messages.format(MessageKeys.COMMAND_TOKEN_NEVER)
              : record.expiresAt().toString();
      String lastUsedAt =
          record.lastUsedAt() == null
              ? messages.format(MessageKeys.COMMAND_TOKEN_NEVER)
              : record.lastUsedAt().toString();
      String scopes =
          record.scopes().isEmpty()
              ? messages.format(MessageKeys.COMMAND_TOKEN_NONE)
              : join(record.scopes());
      output.send(
          messages.format(
              MessageKeys.COMMAND_TOKEN_LIST_ENTRY,
              values(
                  "id",
                  record.id(),
                  "label",
                  record.label(),
                  "owner",
                  record.owner() == null
                      ? messages.format(MessageKeys.COMMAND_TOKEN_NONE)
                      : record.owner(),
                  "status",
                  messages.format(statusKey),
                  "expiresAt",
                  expiresAt,
                  "lastUsedAt",
                  lastUsedAt,
                  "scopes",
                  scopes)));
    }
  }

  private TokenStore tokenStore() {
    return authentication != null && authentication.tokenProvisioningEnabled()
        ? authentication.store()
        : null;
  }

  private String effectivePermissions(TokenRecord record, LocalizedMessages messages) {
    List<String> permissions;
    if (policies == null) {
      permissions = record.scopes();
    } else {
      RbacEvaluator evaluator = new RbacEvaluator(policies::current, null);
      permissions = evaluator.effective(record.identity());
    }
    return permissions.isEmpty()
        ? messages.format(MessageKeys.COMMAND_TOKEN_NONE)
        : join(permissions);
  }

  @SuppressWarnings("unchecked")
  private List<String> knownOwners() {
    Optional<ConfigView> current = manager.current();
    if (!current.isPresent()) {
      return Collections.emptyList();
    }
    Object users = current.get().get("users");
    if (!(users instanceof Map)) {
      return Collections.emptyList();
    }
    return new ArrayList<String>(((Map<String, ?>) users).keySet());
  }

  private boolean knownOwner(String owner) {
    return knownOwners().contains(owner);
  }

  private int maxTtlDays() {
    Optional<ConfigView> current = manager.current();
    int max = current.isPresent() ? current.get().getInt("auth.token.max-ttl-days", 365) : 365;
    return Math.max(1, max);
  }

  private Integer parseTtlDays(String raw, CommandOutput output, LocalizedMessages messages) {
    try {
      int days = Integer.parseInt(raw);
      if (days < 1) {
        throw new NumberFormatException("ttl-days must be positive");
      }
      return Integer.valueOf(days);
    } catch (NumberFormatException exception) {
      invalidValue(output, messages, "ttl-days");
      return null;
    }
  }

  private List<String> parseScopes(String raw, CommandOutput output, LocalizedMessages messages) {
    List<String> scopes = new ArrayList<String>();
    if (raw.trim().isEmpty()) {
      return scopes;
    }
    java.util.Set<String> unique = new LinkedHashSet<String>();
    for (String part : raw.split(",", -1)) {
      String scope = part.trim();
      if (scope.isEmpty()) {
        continue;
      }
      if (!TokenRecord.isValidScope(scope) || !unique.add(scope)) {
        invalidValue(output, messages, "scopes");
        return null;
      }
      scopes.add(scope);
    }
    return scopes;
  }

  private static void invalidValue(CommandOutput output, LocalizedMessages messages, String name) {
    output.send(messages.format(MessageKeys.COMMAND_TOKEN_INVALID_VALUE, values("name", name)));
  }

  private static void tokenFailed(CommandOutput output, LocalizedMessages messages) {
    output.send(messages.format(MessageKeys.COMMAND_TOKEN_FAILED));
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
