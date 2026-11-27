package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.audit.AuditActions;
import io.github.woxlo1.deploymc.core.audit.AuditConfig;
import io.github.woxlo1.deploymc.core.audit.AuditQuery;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicyManager;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
      HttpResponse snapshots = first.snapshotHandler().handle(snapshotListRequest("runtime.txt"));
      assertEquals(200, snapshots.status());
      assertEquals(0L, parse(snapshots).get("total").raw());
      assertTrue(Files.isDirectory(pluginsDirectory.resolve(".deploymc/history")));
    }

    try (FileApiRuntime second =
        FileApiRuntime.create(view, pluginsDirectory, Locale.ENGLISH, 2 * 1024 * 1024)) {
      HttpResponse response = second.handler().handle(listRequest(""));
      assertEquals(200, response.status());
      assertTrue(containsPath(parse(response), "runtime.txt"));
      assertEquals(
          200, second.snapshotHandler().handle(snapshotListRequest("runtime.txt")).status());
    }
  }

  @Test
  void recordsMutationsAndAutomaticSnapshotsThroughTheAuditStore() throws Exception {
    ConfigView view = loadConfig();
    Path dataDirectory = pluginsDirectory.resolve("data");
    Files.createDirectories(dataDirectory);
    AuditConfig auditConfig =
        AuditConfig.of("audit", 1024L * 1024L * 1024L, 365, AuditConfig.FsyncPolicy.RECORD, 1);

    try (AuditStore audit = AuditStore.open(dataDirectory, auditConfig, Clock.systemUTC());
        FileApiRuntime runtime =
            FileApiRuntime.create(
                view,
                pluginsDirectory,
                Locale.ENGLISH,
                2 * 1024 * 1024,
                RbacPolicyManager.from(view),
                audit)) {
      HttpResponse created = runtime.handler().handle(createRequest("audited.txt", "a: 1\n"));
      assertEquals(201, created.status());
      String revision = parse(created).get("entry").get("revision").raw().toString();
      HttpResponse overwritten =
          runtime.handler().handle(overwriteRequest("audited.txt", "a: 2\n", revision));
      assertEquals(200, overwritten.status());

      List<AuditRecord> writes = records(audit, AuditActions.FILES_WRITE);
      assertEquals(4, writes.size());
      assertEquals(AuditResult.SUCCESS, writes.get(writes.size() - 1).result());
      List<AuditRecord> snapshots = records(audit, AuditActions.SNAPSHOTS_CREATE);
      assertEquals(2, snapshots.size());
      assertEquals(AuditResult.SUCCESS, snapshots.get(1).result());
      assertEquals("audited.txt", snapshots.get(1).target().path());
      assertEquals("before-overwrite", snapshots.get(1).details().get("reason"));
      assertTrue(audit.verify().ok());
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

  private static HttpRequest createRequest(String path, String content) {
    String body =
        "{\"path\":\""
            + path
            + "\",\"kind\":\"file\",\"content\":\""
            + content.replace("\n", "\\n")
            + "\"}";
    return new HttpRequest(
        "req-audit-create",
        "POST",
        "/api/v1/files",
        "",
        Collections.singletonMap("content-type", Arrays.asList("application/json")),
        body.getBytes(StandardCharsets.UTF_8));
  }

  private static HttpRequest overwriteRequest(String path, String content, String revision) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("content-type", Arrays.asList("application/octet-stream"));
    headers.put("if-match", Arrays.asList("\"" + revision + "\""));
    return new HttpRequest(
        "req-audit-overwrite",
        "PUT",
        "/api/v1/files/content",
        "path=" + path,
        headers,
        content.getBytes(StandardCharsets.UTF_8));
  }

  private static List<AuditRecord> records(AuditStore audit, String action) throws Exception {
    List<AuditRecord> result = new ArrayList<AuditRecord>();
    for (AuditRecord record : audit.query(AuditQuery.defaults()).items()) {
      if (action.equals(record.action())) {
        result.add(record);
      }
    }
    return result;
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

  private static HttpRequest snapshotListRequest(String path) {
    return new HttpRequest(
        "req-runtime",
        "GET",
        "/api/v1/snapshots",
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
