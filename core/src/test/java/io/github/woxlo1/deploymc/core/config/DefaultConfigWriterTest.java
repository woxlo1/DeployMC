package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultConfigWriterTest {

  @TempDir Path tempDir;

  @Test
  void writesACommentedTemplateOnlyOnce() throws Exception {
    Path target = tempDir.resolve("config.yml");

    assertTrue(DefaultConfigWriter.writeIfMissing(target));
    assertFalse(DefaultConfigWriter.writeIfMissing(target));

    String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
    assertTrue(content.startsWith("# DeployMC configuration"));
    assertTrue(content.contains("config-version: 1"));
    assertTrue(content.contains("Generated on first start"));
    assertFalse(content.contains("myteam.cloudflareaccess.com"));
  }

  @Test
  void generatedTemplatePassesTheNormativeSchema() throws Exception {
    ConfigSchema schema = ConfigSchema.load();
    ConfigValidationResult result =
        ConfigValidator.validate(
            ConfigYamlParser.parse(DefaultConfigWriter.template()), schema, Locale.ROOT);

    assertTrue(result.isValid(), result.toString());
    assertEquals(0, result.errors().size());
  }
}
