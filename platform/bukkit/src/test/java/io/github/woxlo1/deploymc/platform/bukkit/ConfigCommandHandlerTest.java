package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private ConfigCommandHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    configFile = temporaryDirectory.resolve("config.yml");
    DefaultConfigWriter.writeIfMissing(configFile);
    manager = new ConfigManager(configFile);
    ConfigView view = manager.load();
    handler = new ConfigCommandHandler(manager, StartupPlan.fromConfig(view, "0.1.0-test"));
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
        output.messages.contains(
            english(MessageKeys.STARTUP_AUTH, values("authMode", "local-onetime"))));
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
            .replace("  mode: local-onetime", "  mode: token")
            .getBytes(StandardCharsets.UTF_8));
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"reload"});

    assertTrue(containsMessage(output, "language"));
    assertTrue(containsMessage(output, "auth.mode"));
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
  void doctorReportsImplementedChecksAndLimitedCoverage() {
    RecordingOutput output = operatorOutput(false);

    handler.handle(output, new String[] {"doctor"});

    assertEquals(english(MessageKeys.COMMAND_DOCTOR_TITLE), output.messages.get(0));
    assertTrue(containsMessage(output, "PASS configuration"));
    assertTrue(containsMessage(output, "PASS bind address"));
    assertTrue(containsMessage(output, "PASS authentication mode"));
    assertTrue(containsMessage(output, "PASS localization"));
    assertTrue(containsMessage(output, "WARN check coverage"));
  }

  @Test
  void tokenIsConsoleOnlyAndCreatesNothing() throws Exception {
    byte[] before = Files.readAllBytes(configFile);

    RecordingOutput player = operatorOutput(false);
    handler.handle(player, new String[] {"token"});
    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_CONSOLE_ONLY)), player.messages);

    RecordingOutput console = operatorOutput(true);
    handler.handle(console, new String[] {"token"});
    assertEquals(
        Collections.singletonList(english(MessageKeys.COMMAND_TOKEN_UNAVAILABLE)),
        console.messages);
    assertEquals(Arrays.hashCode(before), Arrays.hashCode(Files.readAllBytes(configFile)));
  }

  @Test
  void completionOnlyReturnsPermittedSubcommands() {
    RecordingOutput limited =
        new RecordingOutput(false, CommandPermissions.ROOT, CommandPermissions.STATUS);
    RecordingOutput denied = new RecordingOutput(false);

    assertEquals(
        Collections.singletonList("status"), handler.complete(limited, new String[] {"st"}));
    assertTrue(handler.complete(denied, new String[] {"st"}).isEmpty());
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
