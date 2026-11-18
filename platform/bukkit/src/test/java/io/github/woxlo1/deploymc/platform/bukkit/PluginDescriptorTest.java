package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
}
