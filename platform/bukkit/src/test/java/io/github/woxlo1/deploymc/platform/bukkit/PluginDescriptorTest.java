package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies the generated Bukkit descriptor without loading a server. */
class PluginDescriptorTest {

  private static final String MAIN_CLASS =
      "io.github.woxlo1.deploymc.platform.bukkit.DeployMCPlugin";

  @Test
  void descriptorContainsRequiredFieldsAndInjectedVersion() throws Exception {
    Map<String, String> fields = readDescriptor();

    String version = unquote(fields.get("version"));

    assertEquals("DeployMC", fields.get("name"));
    assertEquals(MAIN_CLASS, fields.get("main"));
    assertEquals(System.getProperty("deploymcVersion"), version);
    assertTrue(version.endsWith("-SNAPSHOT") || version.contains("."));
  }

  @Test
  void descriptorDeclaresDeliberateApiVersion() throws Exception {
    Map<String, String> fields = readDescriptor();

    assertEquals("'1.13'", fields.get("api-version"));
  }

  @Test
  void descriptorOmitsFoliaFlagUntilRegionSupportExists() throws Exception {
    Map<String, String> fields = readDescriptor();

    assertFalse(fields.containsKey("folia-supported"));
    assertFalse(fields.containsKey("paper-plugin-loader"));
  }

  @Test
  void descriptorRegistersCommandBehindRootPermission() throws Exception {
    String descriptor = readDescriptorText();

    assertTrue(
        descriptor.contains("commands:\n  deploymc:\n    permission: " + CommandPermissions.ROOT));
  }

  @Test
  void descriptorDeclaresEveryCommandPermissionAsOperatorOnly() throws Exception {
    String descriptor = readDescriptorText();
    List<String> permissions =
        Arrays.asList(
            CommandPermissions.ROOT,
            CommandPermissions.STATUS,
            CommandPermissions.RELOAD,
            CommandPermissions.DOCTOR,
            CommandPermissions.TOKEN);

    for (String permission : permissions) {
      assertTrue(
          descriptor.contains("  " + permission + ":\n    default: op"),
          "missing operator-only permission " + permission);
    }
  }

  private static String unquote(String value) {
    assertNotNull(value);
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static Map<String, String> readDescriptor() throws Exception {
    Map<String, String> fields = new LinkedHashMap<String, String>();
    try (InputStream stream =
        PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
      assertNotNull(stream, "generated plugin.yml is missing from the test classpath");
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          int separator = line.indexOf(':');
          if (separator <= 0 || line.startsWith("#")) {
            continue;
          }
          String key = line.substring(0, separator).trim();
          String value = line.substring(separator + 1).trim();
          fields.put(key, value);
        }
      }
    }
    return fields;
  }

  private static String readDescriptorText() throws Exception {
    StringBuilder result = new StringBuilder();
    try (InputStream stream =
        PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
      assertNotNull(stream, "generated plugin.yml is missing from the test classpath");
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (result.length() > 0) {
            result.append('\n');
          }
          result.append(line);
        }
      }
    }
    return result.toString();
  }
}
