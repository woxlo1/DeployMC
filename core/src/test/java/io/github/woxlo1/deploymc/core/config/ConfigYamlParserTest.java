package io.github.woxlo1.deploymc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigYamlParserTest {

  @Test
  void parsesScalarTypes() throws Exception {
    YamlValue value =
        ConfigYamlParser.parse("integer: 42\nfloat: 1.5\nboolean: true\ntext: hello\nempty:\n");

    assertTrue(value.get("integer").isInteger());
    assertEquals(42L, value.get("integer").raw());
    assertTrue(value.get("float").isNumber());
    assertFalse(value.get("float").isInteger());
    assertTrue(value.get("boolean").isBoolean());
    assertTrue(value.get("text").isString());
    assertTrue(value.get("empty").isNull());
  }

  @Test
  void keepsOneBasedLineAndColumn() throws Exception {
    YamlValue value = ConfigYamlParser.parse("config-version: 1\nlanguage: ja\n");

    assertEquals(2, value.get("language").line());
    assertEquals(11, value.get("language").column());
  }

  @Test
  void rejectsDuplicateKeys() {
    assertThrows(
        ConfigException.class,
        () -> ConfigYamlParser.parse("config-version: 1\nconfig-version: 2\n"));
  }

  @Test
  void rejectsAliases() {
    assertThrows(
        ConfigException.class,
        () -> ConfigYamlParser.parse("first: &shared value\nsecond: *shared\n"));
  }

  @Test
  void rejectsGlobalTags() {
    assertThrows(
        ConfigException.class,
        () -> ConfigYamlParser.parse("value: !!python/object:example.Thing {}\n"));
  }

  @Test
  void rejectsNonScalarMappingKeys() {
    assertThrows(ConfigException.class, () -> ConfigYamlParser.parse("? [a, b]\n: value\n"));
  }
}
