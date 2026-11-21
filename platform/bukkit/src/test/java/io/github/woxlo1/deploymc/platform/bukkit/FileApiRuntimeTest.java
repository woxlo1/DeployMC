package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free coverage for host configuration and snapshot lifecycle wiring. */
class FileApiRuntimeTest {

  @TempDir Path pluginsDirectory;

  @Test
  void buildsAnAuthenticatedHandlerFromHostConfigAndReleasesTheStore() throws Exception {
    ConfigView view = loadConfig();
    Files.write(pluginsDirectory.resolve("runtime.txt"), "ok".getBytes(StandardCharsets.UTF_8));

    try (FileApiRuntime first =
        FileApiRuntime.create(view, pluginsDirectory, Locale.ENGLISH, 2 * 1024 * 1024)) {
      HttpResponse response = first.handler().handle(listRequest(""));
      assertEquals(200, response.status());
      assertTrue(containsPath(parse(response), "runtime.txt"));
      assertTrue(Files.isDirectory(pluginsDirectory.resolve(".deploymc/history")));
    }

    try (FileApiRuntime second =
        FileApiRuntime.create(view, pluginsDirectory, Locale.ENGLISH, 2 * 1024 * 1024)) {
      HttpResponse response = second.handler().handle(listRequest(""));
      assertEquals(200, response.status());
      assertTrue(containsPath(parse(response), "runtime.txt"));
    }
  }

  @Test
  void acceptsTheSchemaMaximumSizeBudgets() throws Exception {
    ConfigView view =
        loadConfig(
            new String[] {"max-upload-size-mb: 64", "max-upload-size-mb: 4096"},
            new String[] {"max-total-size-mb: 2048", "max-total-size-mb: 1048576"});

    try (FileApiRuntime runtime =
        FileApiRuntime.create(view, pluginsDirectory, Locale.ENGLISH, 2 * 1024 * 1024)) {
      assertEquals(200, runtime.handler().handle(listRequest("")).status());
    }
  }

  @Test
  void failsClosedForUnsupportedSnapshotDirectory() throws Exception {
    ConfigView view =
        loadConfig(
            new String[] {
              "directory: \".deploymc/history\"", "directory: \".deploymc/unsupported\""
            });

    StartupConfigurationException failure =
        assertThrows(
            StartupConfigurationException.class,
            () -> FileApiRuntime.create(view, pluginsDirectory, Locale.ENGLISH, 2 * 1024 * 1024));

    assertEquals(MessageKeys.STARTUP_FILE_API_SNAPSHOT_DIRECTORY, failure.messageKey());
    assertEquals(".deploymc/unsupported", failure.values().get("value"));
    assertFalse(Files.exists(pluginsDirectory.resolve(".deploymc/unsupported")));
  }

  @Test
  void failsClosedForSandboxRootEscape() throws Exception {
    ConfigView view = loadConfig(new String[] {"root: \".\"", "root: \"../escape\""});

    StartupConfigurationException failure =
        assertThrows(
            StartupConfigurationException.class,
            () -> FileApiRuntime.create(view, pluginsDirectory, Locale.ENGLISH, 2 * 1024 * 1024));

    assertEquals(MessageKeys.STARTUP_FILE_API_INVALID_CONFIG, failure.messageKey());
    assertEquals("sandbox.root", failure.values().get("setting"));
    assertFalse(Files.exists(pluginsDirectory.getParent().resolve("escape")));
  }

  private ConfigView loadConfig(String[]... replacements) throws Exception {
    String template = DefaultConfigWriter.template();
    for (String[] replacement : replacements) {
      assertTrue(template.contains(replacement[0]));
      template = template.replace(replacement[0], replacement[1]);
    }
    Path configFile = pluginsDirectory.resolve("config.yml");
    Files.write(configFile, template.getBytes(StandardCharsets.UTF_8));
    return new ConfigManager(configFile).load();
  }

  private static HttpRequest listRequest(String path) {
    return new HttpRequest(
        "req-runtime",
        "GET",
        "/api/v1/files",
        "path=" + path,
        Collections.<String, java.util.List<String>>emptyMap(),
        new byte[0]);
  }

  private static boolean containsPath(YamlValue page, String expected) {
    for (YamlValue item : page.get("items").items()) {
      if (expected.equals(item.get("path").raw())) {
        return true;
      }
    }
    return false;
  }

  private static YamlValue parse(HttpResponse response) throws Exception {
    String body = new String(response.body(), StandardCharsets.UTF_8);
    return ConfigYamlParser.parseSingleDocument(body);
  }
}
