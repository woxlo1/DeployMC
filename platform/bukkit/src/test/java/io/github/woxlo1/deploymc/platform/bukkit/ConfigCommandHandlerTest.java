package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.auth.TokenRecord;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfig;
import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import io.github.woxlo1.deploymc.core.doctor.DoctorMessages;
import io.github.woxlo1.deploymc.core.doctor.DoctorService;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicyManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free coverage for the configuration command surface. */
class ConfigCommandHandlerTest {

  @TempDir Path temporaryDirectory;

  private Path configFile;
  private ConfigManager manager;
  private RbacPolicyManager policies;
  private ConfigCommandHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    configFile = temporaryDirectory.resolve("config.yml");
    DefaultConfigWriter.writeIfMissing(configFile);
    Files.write(configFile, ownersConfig().getBytes(StandardCharsets.UTF_8));
    manager = new ConfigManager(configFile);
    ConfigView view = manager.load();
    StartupPlan plan = StartupPlan.fromConfig(view, "0.1.0-test");
    policies = RbacPolicyManager.from(view);
    handler = new ConfigCommandHandler(manager, plan, null, doctorRuntime(plan), policies);
  }

  @Test
  void deniesCommandWithoutRootPermission() {
    RecordingOutput output = new RecordingOutput(false);

    handler.handle(output, new String[] {"status"});

    assertEquals(
        Collections.singletonList(
            english(
                MessageKeys.COMMAND_PERMISSION_DENIED,
                values("permission", CommandPermissions.ROOT))),
        output.messages);
  }

  @Test
  void usageCoversMissingAndUnknownSubcommands() {
    RecordingOutput empty = operatorOutput(false);
    RecordingOutput unknown = operatorOutput(false);
    String usage = english(MessageKeys.COMMAND_USAGE);

    handler.handle(empty, new String[0]);
    handler.handle(unknown, new String[] {"bogus"});

    assertEquals(Collections.singletonList(usage), empty.messages);
    assertEquals(Collections.singletonList(usage), unknown.messages);
  }

  @Test
  void statusReportsActiveSettingsWithoutSecrets() {
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"status"});

    assertEquals(english(MessageKeys.COMMAND_STATUS_TITLE), output.messages.get(0));
    assertTrue(
        output.messages.contains(
            english(MessageKeys.COMMAND_STATUS_VERSION, values("version", "0.1.0-test"))));
    assertTrue(
        output.messages.contains(
            english(MessageKeys.COMMAND_STATUS_CONFIG_VERSION, values("version", "1"))));
    assertTrue(
        output.messages.contains(english(MessageKeys.STARTUP_AUTH, values("authMode", "token"))));
    assertTrue(
        output.messages.contains(
            english(MessageKeys.STARTUP_BIND, values("address", "127.0.0.1", "port", "8080"))));
    for (String message : output.messages) {
      assertFalse(message.contains("Authorization"), message);
      assertFalse(message.contains("secret"), message);
    }
  }

  @Test
  void reloadReportsChangedAndRestartOnlyPaths() throws Exception {
    String yaml = DefaultConfigWriter.template();
    Files.write(
        configFile,
        yaml.replace("language: en", "language: ja")
            .replace("port: 8080", "port: 9090")
            .getBytes(StandardCharsets.UTF_8));
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"reload"});

    assertTrue(containsMessage(output, "language"));
    assertTrue(containsMessage(output, "bind.port"));
    assertTrue(
        containsMessage(
            output, english(MessageKeys.COMMAND_RELOAD_RESTART_REQUIRED, values("paths", ""))));

    RecordingOutput status = operatorOutput(false);
    handler.handle(status, new String[] {"status"});
    assertTrue(
        status.messages.contains(
            LocalizedMessages.of(Locale.JAPANESE).format(MessageKeys.COMMAND_STATUS_TITLE)));
  }

  @Test
  void senderLocaleOverridesConfiguredLanguage() throws Exception {
    Files.write(
        configFile,
        DefaultConfigWriter.template()
            .replace("language: en", "language: ja")
            .getBytes(StandardCharsets.UTF_8));
    manager.reload();
    RecordingOutput output = operatorOutput(false, Locale.ENGLISH);

    handler.handle(output, new String[] {"status"});

    assertEquals(english(MessageKeys.COMMAND_STATUS_TITLE), output.messages.get(0));
  }

  @Test
  void invalidReloadKeepsLastKnownGoodConfiguration() throws Exception {
    Files.write(
        configFile, "config-version: 1\nauth:\n  mode: none\n".getBytes(StandardCharsets.UTF_8));
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"reload"});

    assertTrue(
        containsMessage(
            output, english(MessageKeys.COMMAND_RELOAD_FAILED, values("error", "")).split(":")[0]));
    RecordingOutput status = operatorOutput(false);
    handler.handle(status, new String[] {"status"});
    assertTrue(
        status.messages.contains(
            english(MessageKeys.COMMAND_STATUS_LANGUAGE, values("language", "en"))));
  }

  @Test
  void validReloadInstallsValidatedPolicySnapshot() throws Exception {
    Files.write(
        configFile,
        ownersConfig()
            .replace("  alice:\n    roles: [ci]", "  carol:\n    roles: [ci]")
            .getBytes(StandardCharsets.UTF_8));
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"reload"});

    assertTrue(containsMessage(output, english(MessageKeys.COMMAND_RELOAD_SUCCESS)));
    assertTrue(policies.current().valid());
    assertTrue(policies.current().rolesFor("carol").contains("ci"));
    assertEquals(Collections.singletonList("none"), policies.current().rolesFor("alice"));
  }

  @Test
  void invalidReloadInstallsDenyAllPolicy() throws Exception {
    Files.write(
        configFile, "config-version: 1\nusers: not-a-map\n".getBytes(StandardCharsets.UTF_8));
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"reload"});

    assertTrue(
        containsMessage(
            output, english(MessageKeys.COMMAND_RELOAD_FAILED, values("error", "")).split(":")[0]));
    assertFalse(policies.current().valid());
    assertEquals(Collections.singletonList("none"), policies.current().rolesFor("alice"));
  }

  @Test
  void doctorReportsAllFifteenChecksAndNoSupportTierVerdict() {
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"doctor"});

    assertEquals(english(MessageKeys.COMMAND_DOCTOR_TITLE), output.messages.get(0));
    assertEquals(15, DoctorService.checkIds().size());
    for (String id : DoctorService.checkIds()) {
      assertTrue(containsMessage(output, "] " + id + ":"), id);
    }
    assertEquals(
        DoctorMessages.format(Locale.ENGLISH, DoctorMessages.FOOTER_NOT_SUPPORT_TIER),
        output.messages.get(output.messages.size() - 1));
  }

  @Test
  void tokenIsConsoleOnlyAndRequiresCreateAction() throws Exception {
    RecordingOutput player = operatorOutput(false);
    handler.handle(player, new String[] {"token", "create"});
    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_CONSOLE_ONLY)), player.messages);

    RecordingOutput console = operatorOutput(true);
    handler.handle(console, new String[] {"token"});
    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_TOKEN_USAGE)), console.messages);
  }

  @Test
  void tokenCreatePrintsTokenOnceAndStoresOnlyHash() throws Exception {
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens.d"));
    AuthenticationRuntime authentication =
        AuthenticationRuntime.create(
            store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(30));
    ConfigCommandHandler tokenHandler = tokenHandler(authentication);
    RecordingOutput console = operatorOutput(true);

    tokenHandler.handle(console, new String[] {"token", "create", "ci runner", "--owner", "alice"});

    assertTrue(containsMessage(console, "Created token"));
    assertTrue(
        console.messages.contains(
            english(
                MessageKeys.COMMAND_TOKEN_EFFECTIVE_ACCESS,
                values("owner", "alice", "permissions", "server.read, files.read, files.write"))));
    assertFalse(containsMessage(console, "admin-equivalent"));
    assertEquals("alice", store.records().get(0).owner());
    String tokenLine = null;
    for (String message : console.messages) {
      if (message.startsWith("Token: ")) {
        tokenLine = message;
      }
    }
    assertTrue(tokenLine != null, console.messages.toString());
    String token = tokenLine.substring("Token: ".length());
    assertTrue(token.startsWith("dmc_"), token);

    Path[] files;
    try (java.util.stream.Stream<Path> stream = Files.list(store.directory())) {
      files = stream.toArray(Path[]::new);
    }
    assertEquals(1, files.length);
    String record = new String(Files.readAllBytes(files[0]), StandardCharsets.UTF_8);
    assertTrue(record.contains("secretHash"));
    assertFalse(record.contains(token));
  }

  @Test
  void tokenProvisioningIsRefusedInCloudflareMode() throws Exception {
    CloudflareConfig config =
        CloudflareConfig.of(
            "https://example.cloudflareaccess.com",
            Collections.singleton("audience-fake"),
            60,
            3600,
            86400);
    AuthenticationRuntime authentication =
        AuthenticationRuntime.createCloudflare(config, Clock.systemUTC());
    ConfigCommandHandler cloudflareHandler =
        new ConfigCommandHandler(manager, StartupPlan.defaults("0.1.0-test"), authentication);
    RecordingOutput console = operatorOutput(true);

    cloudflareHandler.handle(console, new String[] {"token", "create", "ci runner"});

    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_TOKEN_UNAVAILABLE)),
        console.messages);
  }

  @Test
  void tokenCreateHonorsTtlAndScopesWithoutPrintingDigest() throws Exception {
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens-options.d"));
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(30)));
    RecordingOutput console = operatorOutput(true);

    tokenHandler.handle(
        console,
        new String[] {
          "token",
          "create",
          "ci runner",
          "--owner=alice",
          "--ttl-days",
          "2",
          "--scopes",
          "files.read,files.write"
        });

    assertTrue(containsMessage(console, "Created token"));
    assertTrue(
        console.messages.contains(
            english(MessageKeys.COMMAND_TOKEN_EFFECTIVE_TTL, values("days", "2"))));
    assertTrue(
        console.messages.contains(
            english(
                MessageKeys.COMMAND_TOKEN_STORED_SCOPES,
                values("scopes", "files.read, files.write"))));
    assertTrue(
        console.messages.contains(
            english(
                MessageKeys.COMMAND_TOKEN_EFFECTIVE_ACCESS,
                values("owner", "alice", "permissions", "files.read, files.write"))));
    assertEquals("alice", store.records().get(0).owner());
    String token = tokenFrom(console);
    assertTrue(token.startsWith("dmc_"), token);
    assertNoDigest(console);
    String record = readOnlyRecord(store);
    assertTrue(record.contains("files.read"), record);
    assertTrue(record.contains("files.write"), record);
    assertFalse(record.contains(token), record);
  }

  @Test
  void tokenCreateClampsRequestedTtlToHostMaximum() throws Exception {
    Files.write(
        configFile,
        ownersConfig()
            .replace("max-ttl-days: 365", "max-ttl-days: 7")
            .getBytes(StandardCharsets.UTF_8));
    manager.reload();
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens-clamp.d"));
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(30)));
    RecordingOutput console = operatorOutput(true);

    tokenHandler.handle(
        console,
        new String[] {"token", "create", "--owner", "alice", "--ttl-days", "400", "clamped"});

    assertTrue(
        console.messages.contains(
            english(MessageKeys.COMMAND_TOKEN_EFFECTIVE_TTL, values("days", "7"))));
  }

  @Test
  void tokenCreateRejectsInvalidOptionsWithClosedMessage() throws Exception {
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens-invalid.d"));
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(30)));

    String[][] invalid = {
      {"token", "create", "--owner", "alice", "--ttl-days", "0"},
      {"token", "create", "--owner", "alice", "--ttl-days", "not-a-number"},
      {"token", "create", "--owner", "alice", "--scopes", "Bad.Scope"},
      {"token", "create", "--owner", "alice", "--scopes", "files.read,files.read"},
      {"token", "create", "--owner", "alice", "--unknown"}
    };
    String[] names = {"ttl-days", "ttl-days", "scopes", "scopes", "option"};
    for (int index = 0; index < invalid.length; index++) {
      RecordingOutput console = operatorOutput(true);

      tokenHandler.handle(console, invalid[index]);

      assertEquals(
          Collections.singletonList(
              english(MessageKeys.COMMAND_TOKEN_INVALID_VALUE, values("name", names[index]))),
          console.messages);
    }
    assertTrue(store.records().isEmpty());
  }

  @Test
  void tokenCreateRejectsMissingOrUnknownOwner() throws Exception {
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens-owner.d"));
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(30)));
    String[][] invalid = {
      {"token", "create"},
      {"token", "create", "--owner"},
      {"token", "create", "--owner="},
      {"token", "create", "--owner", "unknown-identity"}
    };

    for (String[] arguments : invalid) {
      RecordingOutput console = operatorOutput(true);

      tokenHandler.handle(console, arguments);

      assertEquals(
          Collections.singletonList(english(MessageKeys.COMMAND_TOKEN_OWNER_INVALID)),
          console.messages);
    }
    assertTrue(store.records().isEmpty());
  }

  @Test
  void tokenCreateFailureDoesNotLeakExceptionDetails() throws Exception {
    Path missingDirectory = temporaryDirectory.resolve("tokens-missing.d");
    TokenStore store = TokenStore.load(missingDirectory);
    Files.delete(missingDirectory);
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(1)));
    RecordingOutput console = operatorOutput(true);

    tokenHandler.handle(console, new String[] {"token", "create", "--owner", "alice"});

    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_TOKEN_FAILED)), console.messages);
    assertNoDigest(console);
  }

  @Test
  void tokenRevokeAndListReportStateWithoutSecrets() throws Exception {
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens-lifecycle.d"));
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(3)));
    RecordingOutput empty = operatorOutput(true);

    tokenHandler.handle(empty, new String[] {"token", "list"});

    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_TOKEN_LIST_EMPTY)), empty.messages);

    RecordingOutput created = operatorOutput(true);
    tokenHandler.handle(created, new String[] {"token", "create", "ops", "--owner", "alice"});
    String token = tokenFrom(created);
    TokenRecord record = store.records().get(0);
    assertEquals("alice", record.owner());
    String activeEntry =
        english(
            MessageKeys.COMMAND_TOKEN_LIST_ENTRY,
            values(
                "id",
                record.id(),
                "label",
                "ops",
                "owner",
                "alice",
                "status",
                english(MessageKeys.COMMAND_TOKEN_STATUS_ACTIVE),
                "expiresAt",
                record.expiresAt().toString(),
                "lastUsedAt",
                english(MessageKeys.COMMAND_TOKEN_NEVER),
                "scopes",
                english(MessageKeys.COMMAND_TOKEN_NONE)));
    RecordingOutput active = operatorOutput(true);

    tokenHandler.handle(active, new String[] {"token", "list"});

    assertTrue(active.messages.contains(activeEntry), active.messages.toString());
    assertNoSecret(active, token);
    assertEquals(
        Collections.singletonList(record.id()),
        tokenHandler.complete(operatorOutput(false), new String[] {"token", "revoke", "t-"}));

    RecordingOutput revoked = operatorOutput(true);
    tokenHandler.handle(revoked, new String[] {"token", "revoke", record.id()});

    assertEquals(
        Collections.singletonList(
            english(MessageKeys.COMMAND_TOKEN_REVOKED, values("id", record.id()))),
        revoked.messages);
    assertTrue(store.records().get(0).isRevoked());

    RecordingOutput after = operatorOutput(true);
    tokenHandler.handle(after, new String[] {"token", "list"});

    assertTrue(containsMessage(after, english(MessageKeys.COMMAND_TOKEN_STATUS_REVOKED)));
    assertNoSecret(after, token);

    RecordingOutput missing = operatorOutput(true);
    tokenHandler.handle(missing, new String[] {"token", "revoke", "t-000000000000"});

    assertEquals(
        Collections.singletonList(
            english(MessageKeys.COMMAND_TOKEN_NOT_FOUND, values("id", "t-000000000000"))),
        missing.messages);
  }

  @Test
  void tokenListChecksRevocationBeforeExpiry() throws Exception {
    TokenStore store = TokenStore.load(temporaryDirectory.resolve("tokens-status.d"));
    byte[] expiredHash = new byte[32];
    byte[] revokedHash = new byte[32];
    revokedHash[0] = 1;
    byte[] bothHash = new byte[32];
    bothHash[0] = 2;
    store.write(
        new TokenRecord(
            "t-000000000001", "expired", expiredHash, Instant.parse("2020-01-01T00:00:00Z")));
    store.write(
        new TokenRecord(
            "t-000000000002",
            "revoked",
            revokedHash,
            Instant.parse("2030-01-01T00:00:00Z"),
            Collections.<String>emptyList(),
            Instant.parse("2025-01-01T00:00:00Z"),
            null));
    store.write(
        new TokenRecord(
            "t-000000000003",
            "both",
            bothHash,
            Instant.parse("2020-01-01T00:00:00Z"),
            Collections.<String>emptyList(),
            Instant.parse("2021-01-01T00:00:00Z"),
            null));
    ConfigCommandHandler tokenHandler =
        tokenHandler(
            AuthenticationRuntime.create(
                store, Clock.systemUTC(), new SecureRandom(), Duration.ofDays(1)));
    RecordingOutput console = operatorOutput(true);

    tokenHandler.handle(console, new String[] {"token", "list"});

    assertTrue(
        containsMessage(
            console,
            "t-000000000001 | expired | owner "
                + english(MessageKeys.COMMAND_TOKEN_NONE)
                + " | "
                + english(MessageKeys.COMMAND_TOKEN_STATUS_EXPIRED)));
    assertTrue(
        containsMessage(
            console,
            "t-000000000002 | revoked | owner "
                + english(MessageKeys.COMMAND_TOKEN_NONE)
                + " | "
                + english(MessageKeys.COMMAND_TOKEN_STATUS_REVOKED)));
    assertTrue(
        containsMessage(
            console,
            "t-000000000003 | both | owner "
                + english(MessageKeys.COMMAND_TOKEN_NONE)
                + " | "
                + english(MessageKeys.COMMAND_TOKEN_STATUS_REVOKED)));
  }

  @Test
  void tokenProvisioningIsRefusedInLocalOneTimeMode() {
    AuthenticationRuntime oneTime =
        AuthenticationRuntime.createLocalOneTime(
            Clock.systemUTC(), new SecureRandom(), Duration.ofMinutes(5), 3);
    ConfigCommandHandler tokenHandler = tokenHandler(oneTime);
    RecordingOutput console = operatorOutput(true);

    tokenHandler.handle(console, new String[] {"token", "create"});
    tokenHandler.handle(console, new String[] {"token", "list"});
    tokenHandler.handle(console, new String[] {"token", "revoke", "t-000000000000"});

    assertEquals(
        Arrays.asList(
            english(MessageKeys.COMMAND_TOKEN_UNAVAILABLE),
            english(MessageKeys.COMMAND_TOKEN_UNAVAILABLE),
            english(MessageKeys.COMMAND_TOKEN_UNAVAILABLE)),
        console.messages);
    assertFalse(oneTime.oneTimeToken().toString().contains(oneTime.oneTimeToken().secret()));
  }

  @Test
  void completionOnlyReturnsPermittedSubcommands() {
    RecordingOutput limited =
        new RecordingOutput(false, CommandPermissions.ROOT, CommandPermissions.STATUS);
    RecordingOutput denied = new RecordingOutput(false);

    assertEquals(
        Collections.singletonList("status"), handler.complete(limited, new String[] {"st"}));
    assertTrue(handler.complete(denied, new String[] {"st"}).isEmpty());
    assertEquals(
        Collections.singletonList("create"),
        handler.complete(operatorOutput(false), new String[] {"token", "cr"}));
    assertTrue(
        handler.complete(operatorOutput(false), new String[] {"token", "revoke", "t-"}).isEmpty());
    assertEquals(
        Collections.singletonList("--owner=alice"),
        handler.complete(operatorOutput(false), new String[] {"token", "create", "--owner=al"}));
    assertEquals(
        Collections.singletonList("bob"),
        handler.complete(operatorOutput(false), new String[] {"token", "create", "--owner", "bo"}));
  }

  private static String ownersConfig() throws ConfigException {
    return DefaultConfigWriter.template()
        .replace(
            "roles: {}", "roles:\n  ci:\n    permissions: [server.read, files.read, files.write]")
        .replace("users: {}", "users:\n  alice:\n    roles: [ci]\n  bob:\n    roles: [ci]");
  }

  private ConfigCommandHandler tokenHandler(AuthenticationRuntime authentication) {
    return new ConfigCommandHandler(
        manager,
        StartupPlan.fromConfig(manager.current().get(), "0.1.0-test"),
        authentication,
        null,
        policies);
  }

  private static String tokenFrom(RecordingOutput output) {
    for (String message : output.messages) {
      if (message.startsWith("Token: ")) {
        return message.substring("Token: ".length());
      }
    }
    throw new AssertionError("no token line in " + output.messages);
  }

  private static String readOnlyRecord(TokenStore store) throws Exception {
    Path[] files;
    try (java.util.stream.Stream<Path> stream = Files.list(store.directory())) {
      files = stream.toArray(Path[]::new);
    }
    assertEquals(1, files.length);
    return new String(Files.readAllBytes(files[0]), StandardCharsets.UTF_8);
  }

  private static void assertNoDigest(RecordingOutput output) {
    for (String message : output.messages) {
      assertFalse(message.contains("secretHash"), message);
    }
  }

  private static void assertNoSecret(RecordingOutput output, String secret) {
    for (String message : output.messages) {
      assertFalse(message.contains(secret), message);
      assertFalse(message.contains("secretHash"), message);
    }
  }

  private DoctorRuntime doctorRuntime(StartupPlan plan) {
    return new DoctorRuntime(
        manager,
        plan,
        new PlatformInfo("Paper", "1.21.1", "1.21.1", "21", "test-os"),
        PlatformCapabilities.empty(),
        Locale.ENGLISH,
        "en",
        null,
        LocalizedMessages.of(Locale.ENGLISH),
        Clock.systemUTC());
  }

  private RecordingOutput operatorOutput(boolean console) {
    return operatorOutput(console, null);
  }

  private RecordingOutput operatorOutput(boolean console, Locale locale) {
    return new RecordingOutput(
        console,
        locale,
        CommandPermissions.ROOT,
        CommandPermissions.STATUS,
        CommandPermissions.RELOAD,
        CommandPermissions.DOCTOR,
        CommandPermissions.TOKEN);
  }

  private static boolean containsMessage(RecordingOutput output, String fragment) {
    for (String message : output.messages) {
      if (message.contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  private static String english(String key, Map<String, String> placeholders) {
    return LocalizedMessages.of(Locale.ENGLISH).format(key, placeholders);
  }

  private static String english(String key) {
    return english(key, Collections.<String, String>emptyMap());
  }

  private static Map<String, String> values(String... pairs) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (int index = 0; index < pairs.length; index += 2) {
      result.put(pairs[index], pairs[index + 1]);
    }
    return result;
  }

  private static final class RecordingOutput implements CommandOutput {

    private final boolean console;
    private final Optional<Locale> locale;
    private final Set<String> permissions;
    private final List<String> messages = new ArrayList<String>();

    private RecordingOutput(boolean console, String... permissions) {
      this(console, null, permissions);
    }

    private RecordingOutput(boolean console, Locale locale, String... permissions) {
      this.console = console;
      this.locale = Optional.ofNullable(locale);
      this.permissions = new HashSet<String>(Arrays.asList(permissions));
    }

    @Override
    public boolean hasPermission(String permission) {
      return permissions.contains(permission);
    }

    @Override
    public boolean isConsole() {
      return console;
    }

    @Override
    public Optional<Locale> locale() {
      return locale;
    }

    @Override
    public void send(String message) {
      messages.add(message);
    }
  }
}
