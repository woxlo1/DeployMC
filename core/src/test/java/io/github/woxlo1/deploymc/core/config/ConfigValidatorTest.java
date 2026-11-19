package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {

  private static ConfigSchema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    schema = ConfigSchema.load();
  }

  @Test
  void acceptsGeneratedDefaultConfiguration() throws Exception {
    ConfigValidationResult result = validate(DefaultConfigWriter.template());

    assertTrue(result.isValid(), result.toString());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void reportsInvalidEnumWithExactLocation() throws Exception {
    ConfigValidationResult result = validate("config-version: 1\nauth:\n  mode: nobody\n");

    assertFalse(result.isValid());
    ConfigIssue issue = result.errors().get(0);
    assertEquals("auth.mode", issue.path());
    assertEquals(3, issue.line());
    assertEquals(9, issue.column());
    assertEquals("config.error.enum", issue.messageKey());
    assertTrue(issue.format(Locale.ROOT).contains("nobody"));
  }

  @Test
  void requiresOptInForNonLoopbackBind() throws Exception {
    ConfigValidationResult result =
        validate("config-version: 1\nbind:\n  address: 0.0.0.0\n  port: 8080\n");

    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(issue -> "bind.allow-non-loopback".equals(issue.path())));
  }

  @Test
  void warnsAboutUnknownKeysWithoutFailing() throws Exception {
    ConfigValidationResult result = validate("config-version: 1\nmystery: 3\n");

    assertTrue(result.isValid());
    assertEquals(1, result.warnings().size());
    assertEquals("config.warning.unknownKey", result.warnings().get(0).messageKey());
  }

  @Test
  void validatesReferencedPathGlob() throws Exception {
    ConfigValidationResult result =
        validate("config-version: 1\nsandbox:\n  protected-paths:\n    - /absolute/**\n");

    assertFalse(result.isValid());
    assertEquals("sandbox.protected-paths[0]", result.errors().get(0).path());
  }

  @Test
  void appliesOneOfToAudience() throws Exception {
    assertTrue(
        validate(
                "config-version: 1\nauth:\n  mode: cloudflare\n  cloudflare:\n    team-domain: example.invalid\n    audience: abc\n")
            .isValid());
    assertTrue(
        validate(
                "config-version: 1\nauth:\n  mode: cloudflare\n  cloudflare:\n    team-domain: example.invalid\n    audience:\n      - abc\n      - def\n")
            .isValid());
    assertFalse(validate("config-version: 1\nauth:\n  audience: 42\n").isValid());
  }

  @Test
  void rejectsUnknownRoleReferences() throws Exception {
    ConfigValidationResult result =
        validate(
            "config-version: 1\ndefault-role: Admin\nusers:\n  alice:\n    roles:\n      - missing\n");

    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream()
            .allMatch(issue -> "config.error.unknownRole".equals(issue.messageKey())));
  }

  @Test
  void exposesHotReloadMetadata() throws Exception {
    assertEquals(1, schema.supportedVersion());
    assertEquals(Boolean.TRUE, schema.hotReloadable("language"));
    assertEquals(Boolean.FALSE, schema.hotReloadable("bind.port"));
    assertEquals(Boolean.TRUE, schema.hotReloadable("sandbox.protected-paths"));
  }

  private static ConfigValidationResult validate(String source) throws Exception {
    return ConfigValidator.validate(ConfigYamlParser.parse(source), schema, Locale.ROOT);
  }
}
