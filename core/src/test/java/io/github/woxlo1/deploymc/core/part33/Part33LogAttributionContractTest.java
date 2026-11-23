package io.github.woxlo1.deploymc.core.part33;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * PART 33.7 spec and fixture contract only. No product log consumer exists, and no parity with one
 * is claimed.
 */
class Part33LogAttributionContractTest {

  private static final String SPEC =
      "docs/development/DeployMC_Complete_Master_Development_Specification.md";

  @Test
  void specDefinesOrderedHighMediumAndUnattributedRules() throws Exception {
    String section = part37();

    int high =
        requireIndex(
            section,
            "1. If the logger name matches a loaded plugin's name or main-class package "
                + "-> that plugin (high confidence)");
    int bracketed =
        requireIndex(
            section,
            "2. Else if the message begins with a bracketed plugin name \"[Name]\" "
                + "matching a loaded plugin -> medium");
    int classloader =
        requireIndex(
            section,
            "3. Else if the throwable's stack frames include classes loaded by a plugin's "
                + "classloader -> medium (first such frame)");
    int unattributed = requireIndex(section, "4. Else unattributed");

    assertTrue(high < bracketed, "logger-name rule must precede bracketed prefix");
    assertTrue(bracketed < classloader, "prefix rule must precede classloader frame");
    assertTrue(classloader < unattributed, "classloader rule must precede fallback");
    assertTrue(section.contains("bracketed plugin name \"[Name]\""));
    assertTrue(section.contains("classes loaded by a plugin's classloader"));
    assertTrue(section.contains("first such frame"));
    assertTrue(section.contains("Output includes confidence level"));
  }

  @Test
  void specRequiresHeuristicLabelsAndExplicitFailureEvidence() throws Exception {
    String section = part37();

    assertTrue(section.contains("UI/CLI label attribution as heuristic"));
    assertTrue(section.contains("Verification checks for the deployed"));
    assertTrue(section.contains("plugin use high/medium-confidence matches"));
    assertTrue(section.contains("explicit enable-failure events where the platform provides them"));
  }

  @Test
  void manifestPinsLogCorpusFixturesAndAbsentConsumer() throws Exception {
    Properties manifest = manifest();

    assertEquals("1", required(manifest, "schema.version"));
    assertEquals("2026-11-22.1", required(manifest, "corpus.version"));
    assertEquals(
        "tests/fixtures/log-corpora/generate_log_corpora.py", required(manifest, "generator"));
    assertEquals("UTF-8", required(manifest, "encoding"));
    assertEquals("LF", required(manifest, "line.separator"));
    assertEquals("false", required(manifest, "bom"));
    List<String> files = split(required(manifest, "fixture.list"));
    assertEquals(Integer.parseInt(required(manifest, "fixture.count")), files.size());
    assertEquals(
        Arrays.asList("stack-traces.log", "ansi-sequences.log", "long-lines.log", "unicode.log"),
        files);

    for (String file : files) {
      String prefix = "fixture." + identifier(file) + ".";
      assertEquals(file, required(manifest, prefix + "file"));
      assertEquals("not-implemented", required(manifest, prefix + "current.consumer"));
      assertFalse(required(manifest, prefix + "future.requirement").isEmpty(), file);
      assertTrue(
          required(manifest, prefix + "sha256").matches("[0-9a-f]{64}"),
          file + " must pin a SHA-256 digest");
      assertTrue(Long.parseLong(required(manifest, prefix + "bytes")) > 0L, file);
      assertTrue(Long.parseLong(required(manifest, prefix + "codepoints")) > 0L, file);
    }
  }

  @Test
  void generatorAndReadmeKeepTheFixtureAsTestOnly() throws Exception {
    Properties manifest = manifest();
    String generator = repositoryText("tests/fixtures/log-corpora/generate_log_corpora.py");
    String readme = repositoryText("tests/fixtures/log-corpora/README.md");

    assertTrue(generator.contains("CORPUS_VERSION = \"2026-11-22.1\""));
    assertTrue(
        generator.contains("GENERATOR = \"tests/fixtures/log-corpora/generate_log_corpora.py\""));
    assertTrue(generator.contains("not_implemented = \"not-implemented\""));
    assertTrue(generator.contains("No product console consumer is invoked"));
    for (String file : split(required(manifest, "fixture.list"))) {
      assertTrue(generator.contains("\"" + file + "\""), file + " missing from generator");
    }
    assertTrue(
        generator.contains(
            "\\tat io.github.woxlo1.deploymc.fixture.FailingTask.run(FailingTask.java:17)"));
    assertTrue(readme.contains("No product console log consumer exists yet"));
    assertTrue(readme.contains("current.consumer=not-implemented"));
    assertTrue(readme.contains("They never invoke a product parser or console consumer"));
  }

  private static String part37() throws IOException {
    String spec = repositoryText(SPEC);
    int start = spec.indexOf("## 33.7 Log Attribution (Heuristic)");
    assertTrue(start >= 0, "missing PART 33.7 section");
    int end = spec.indexOf("## 33.8 Rate Limiting and Backpressure", start);
    assertTrue(end > start, "missing PART 33.8 section boundary");
    return spec.substring(start, end);
  }

  private static int requireIndex(String text, String fragment) {
    int index = text.indexOf(fragment);
    assertTrue(index >= 0, "missing contract fragment: " + fragment);
    return index;
  }

  private static Properties manifest() throws IOException {
    Path path = repoRoot().resolve("tests/fixtures/log-corpora/log-corpora-manifest.properties");
    Properties properties = new Properties();
    try (InputStream stream = Files.newInputStream(path)) {
      properties.load(stream);
    }
    return properties;
  }

  private static String repositoryText(String relativePath) throws IOException {
    Path path = repoRoot().resolve(relativePath);
    assertTrue(Files.isRegularFile(path), "Missing repository file: " + relativePath);
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private static Path repoRoot() {
    Path current = Paths.get("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve(SPEC))
          && Files.isDirectory(current.resolve("tests/fixtures/log-corpora"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("Repository root not found from " + Paths.get("").toAbsolutePath());
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    assertTrue(value != null, "Missing manifest key: " + key);
    return value;
  }

  private static String identifier(String file) {
    String stem = file.substring(0, file.length() - ".log".length());
    return stem.replace('-', '_');
  }

  private static List<String> split(String value) {
    List<String> values = new ArrayList<String>();
    for (String part : value.split(",")) {
      if (!part.isEmpty()) {
        values.add(part);
      }
    }
    return values;
  }
}
