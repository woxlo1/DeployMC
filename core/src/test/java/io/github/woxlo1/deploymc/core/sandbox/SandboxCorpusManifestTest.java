package io.github.woxlo1.deploymc.core.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Validates the versioned corpus manifest and applies its expectations on the current OS. */
class SandboxCorpusManifestTest {

  private static final String MANIFEST = "/sandbox/corpus-manifest.properties";
  private static final int SUPPORTED_SCHEMA = 1;
  private static final List<String> SOURCES = Arrays.asList("traversal", "allowed");

  @TempDir Path tempDir;

  @Test
  void manifestDeclaresSupportedSchemaAndCurrentOs() throws IOException {
    Properties manifest = manifest();

    assertEquals(String.valueOf(SUPPORTED_SCHEMA), manifest.getProperty("schema.version"));
    List<String> supported = csv(manifest.getProperty("supported.os"));
    assertTrue(supported.contains(currentOs()), "Manifest must declare OS: " + currentOs());

    int overrides = Integer.parseInt(manifest.getProperty("override.count"));
    assertEquals(0, overrides, "Schema 1 has no verified per-OS outcome differences");
    for (String key : manifest.stringPropertyNames()) {
      assertFalse(key.matches("category\\..+\\.os\\..+"), "Unexpected override: " + key);
    }
  }

  @Test
  void manifestDigestsAndVersionsMatchGeneratedFiles() throws IOException {
    Properties manifest = manifest();

    for (String source : SOURCES) {
      String file = manifest.getProperty("source." + source + ".file");
      assertNotNull(file, "Missing source file for " + source);
      byte[] bytes = resource("/sandbox/" + file);
      assertEquals(
          manifest.getProperty("source." + source + ".sha256"), sha256(bytes), file + " digest");

      int lines = SandboxCorpus.lines(file).size();
      assertEquals(
          Integer.parseInt(manifest.getProperty("source." + source + ".lines")),
          lines,
          file + " payload count");

      List<String> raw = rawLines("/sandbox/" + file);
      String schemaLine = findHeader(raw, "@schema-version");
      assertTrue(
          schemaLine.contains("@schema-version " + SUPPORTED_SCHEMA),
          file + " schema header: " + schemaLine);
      assertTrue(
          schemaLine.contains("@corpus-version " + manifest.getProperty("corpus.version")),
          file + " corpus version header: " + schemaLine);
    }
  }

  @Test
  void everyCategoryExpectationMatchesResolverOnCurrentOs() throws Exception {
    Properties manifest = manifest();
    List<String> declaredCategories = csv(manifest.getProperty("category.list"));
    Path root = Files.createDirectories(tempDir.resolve("root"));
    SandboxResolver resolver = new SandboxResolver(root);

    for (String source : SOURCES) {
      String file = manifest.getProperty("source." + source + ".file");
      Map<String, List<String>> groups = categorized(file);
      assertFalse(groups.isEmpty(), file + " must declare categories");

      for (Map.Entry<String, List<String>> group : groups.entrySet()) {
        String category = group.getKey();
        assertTrue(declaredCategories.contains(category), "Undeclared category: " + category);
        assertFalse(group.getValue().isEmpty(), "Empty category: " + category);

        String expected = expectedOutcome(manifest, source, category);
        assertEquals(
            manifest.getProperty("source." + source + ".default.outcome"),
            expected,
            "Schema 1 override must be explicit: " + category);

        for (String encoded : group.getValue()) {
          String payload = SandboxCorpus.decode(encoded);
          if ("accept".equals(expected)) {
            assertNotNull(resolver.resolve(payload, SandboxAccessMode.READ), payload);
          } else if ("reject".equals(expected)) {
            try {
              resolver.resolve(payload, SandboxAccessMode.READ);
              fail("Payload must be rejected: [" + payload + "]");
            } catch (SandboxRejectionException expectedRejection) {
              assertNotNull(expectedRejection.code());
            }
          } else {
            fail("Unknown expected outcome: " + expected);
          }
        }
      }
    }
  }

  private static String expectedOutcome(Properties manifest, String source, String category) {
    String key = "category." + category + ".os." + currentOs();
    return manifest.getProperty(key, manifest.getProperty("source." + source + ".default.outcome"));
  }

  private static Map<String, List<String>> categorized(String file) throws IOException {
    Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
    String current = null;
    for (String line : rawLines("/sandbox/" + file)) {
      if (line.startsWith("# @category ")) {
        current = tokenValue(line, "category");
        assertTrue(current != null && !current.isEmpty(), "Category header needs an id: " + line);
        groups.put(current, new ArrayList<String>());
      } else if (!line.startsWith("#") && !line.trim().isEmpty()) {
        assertNotNull(current, "Payload without category in " + file + ": " + line);
        groups.get(current).add(line);
      }
    }
    return groups;
  }

  private static String tokenValue(String line, String key) {
    String prefix = "@" + key + " ";
    int start = line.indexOf(prefix);
    if (start < 0) {
      return null;
    }
    int from = start + prefix.length();
    int end = line.indexOf(' ', from);
    return end < 0 ? line.substring(from) : line.substring(from, end);
  }

  private static String findHeader(List<String> lines, String marker) {
    for (String line : lines) {
      if (line.contains(marker)) {
        return line;
      }
    }
    return "";
  }

  private static List<String> rawLines(String resource) throws IOException {
    List<String> lines = new ArrayList<String>();
    String text = new String(resource(resource), "UTF-8");
    for (String line : text.split("\\n", -1)) {
      if (!line.endsWith("\r")) {
        lines.add(line);
      } else {
        lines.add(line.substring(0, line.length() - 1));
      }
    }
    return lines;
  }

  private static Properties manifest() throws IOException {
    Properties properties = new Properties();
    try (InputStream stream = SandboxCorpusManifestTest.class.getResourceAsStream(MANIFEST)) {
      assertNotNull(stream, "Missing test resource: " + MANIFEST);
      properties.load(stream);
    }
    return properties;
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream stream = SandboxCorpusManifestTest.class.getResourceAsStream(name)) {
      assertNotNull(stream, "Missing test resource: " + name);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) > 0) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static String sha256(byte[] bytes) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder result = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        result.append(Character.forDigit((value >> 4) & 0xf, 16));
        result.append(Character.forDigit(value & 0xf, 16));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IOException(impossible);
    }
  }

  private static List<String> csv(String value) {
    List<String> values = new ArrayList<String>();
    if (value != null && !value.trim().isEmpty()) {
      for (String part : value.split(",")) {
        values.add(part.trim());
      }
    }
    return values;
  }

  private static String currentOs() {
    String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (name.contains("win")) {
      return "windows";
    }
    if (name.contains("mac") || name.contains("darwin")) {
      return "macos";
    }
    return "linux";
  }
}
