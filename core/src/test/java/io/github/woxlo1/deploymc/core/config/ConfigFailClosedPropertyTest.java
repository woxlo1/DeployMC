package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Secure-random property test: every generated invalid configuration must fail closed. */
class ConfigFailClosedPropertyTest {

  @TempDir Path tempDir;

  @Test
  void randomInvalidConfigurationsNeverBecomeActive() throws Exception {
    ConfigSchema schema = ConfigSchema.load();
    String template = DefaultConfigWriter.template();
    List<UnaryOperator<String>> mutations = mutations();
    SecureRandom random = new SecureRandom();
    Path file = tempDir.resolve("random-invalid.yml");

    for (int attempt = 0; attempt < 250; attempt++) {
      UnaryOperator<String> mutation = mutations.get(random.nextInt(mutations.size()));
      String yaml = mutation.apply(template);
      assertNotEquals(
          template, yaml, "mutation at attempt " + attempt + " did not change the file");

      ConfigValidationResult validation =
          ConfigValidator.validate(ConfigYamlParser.parse(yaml), schema, Locale.ROOT);
      assertFalse(
          validation.isValid(),
          "mutation at attempt " + attempt + " unexpectedly validated: " + yaml);

      Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
      ConfigManager manager = new ConfigManager(file);
      assertThrows(ConfigException.class, manager::load);
      assertFalse(manager.current().isPresent());
    }
  }

  private static List<UnaryOperator<String>> mutations() {
    return new ArrayList<UnaryOperator<String>>(
        Arrays.<UnaryOperator<String>>asList(
            source -> source.replace("config-version: 1", "config-version: 2"),
            source -> source.replace("language: en", "language: EN"),
            source -> source.replace("address: 127.0.0.1", "address: 0.0.0.0"),
            source -> source.replace("port: 8080", "port: 70000"),
            source -> source.replace("mode: token", "mode: nobody"),
            source -> source.replace("trusted-proxies: []", "trusted-proxies: not-an-array"),
            source -> source.replace("- \".deploymc/**\"", "- \"/absolute/**\""),
            source -> source.replace("max-file-size-mb: 10", "max-file-size-mb: 0"),
            source -> source.replace("max-upload-size-mb: 64", "max-upload-size-mb: 7000"),
            source -> source.replace("rotate-size-mb: 50", "rotate-size-mb: 0"),
            source -> source.replace("retention-days: 365", "retention-days: 0"),
            source -> source.replace("rate-limit-per-minute: 300", "rate-limit-per-minute: 0"),
            source ->
                source.replace("max-websocket-connections: 50", "max-websocket-connections: 0"),
            source -> source.replace("default-role: none", "default-role: Admin"),
            source ->
                source.replace("allow-private-targets: false", "allow-private-targets: \"false\""),
            source -> source.replace("  enabled: false\n", "  enabled: maybe\n")));
  }
}
