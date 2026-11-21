package io.github.woxlo1.deploymc.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormatValidatorsTest {

  private static final ValidationLimits DEFAULTS = ValidationLimits.defaults();

  @Test
  void yamlDetectsDuplicateKeysTabsTagsMultipleDocumentsAndPositions() throws Exception {
    ValidationResult duplicates = yaml("server:\n  port: 1\n  port: 2\n");
    assertFalse(duplicates.valid());
    assertTrue(hasCode(duplicates, "validation.yaml-duplicate-key"));
    assertEquals("ERROR", duplicates.issues().get(0).severity().name());
    assertTrue(duplicates.issues().get(0).line() > 0);

    ValidationResult tabs = yaml("server:\n\tport: 1\n");
    assertTrue(hasCode(tabs, "validation.yaml-tab"));
    assertEquals(2, tabs.issues().get(0).line());

    ValidationResult customTag = yaml("value: !custom hello\n");
    assertTrue(hasCode(customTag, "validation.yaml-tag-rejected"));
    assertTrue(customTag.issues().get(0).parameters().get(0).contains("custom"));

    ValidationResult multiple = yaml("---\na: 1\n---\nb: 2\n");
    assertTrue(hasCode(multiple, "validation.yaml-multiple-documents"));

    ValidationResult syntax = yaml("a: [1, 2\n");
    assertTrue(hasCode(syntax, "validation.yaml-syntax"));
    assertTrue(syntax.issues().get(0).line() > 0);
  }

  @Test
  void yamlAcceptsEmptyDocumentAndHonorsBoundariesAndAliasBudgets() throws Exception {
    ValidationResult empty = yaml("");
    assertTrue(empty.valid());
    assertTrue(empty.issues().isEmpty());

    ValidationLimits tiny = new ValidationLimits(1024L, 3, 16, 8, 32, 2, 16, 16);
    assertEquals(
        "maxCodePoints",
        assertThrows(ValidationLimitException.class, () -> yaml("abcd", tiny)).limitName());

    ValidationLimits nodeLimited = new ValidationLimits(1024L, 256, 16, 8, 4, 2, 64, 32);
    assertEquals(
        "maxNodes",
        assertThrows(
                ValidationLimitException.class, () -> yaml("a:\n  b:\n    c: 1\n", nodeLimited))
            .limitName());

    ValidationResult aliases =
        yaml("base: &base\n  value: 1\ncopy1: *base\ncopy2: *base\n", ValidationLimits.small());
    assertTrue(hasCode(aliases, "validation.yaml-alias-limit"), aliases.issues().toString());

    ValidationResult cycle = yaml("loop: &loop\n  self: *loop\n", ValidationLimits.small());
    assertTrue(hasCode(cycle, "validation.yaml-alias-limit"), cycle.issues().toString());
  }

  @Test
  void jsonDetectsSyntaxDuplicateKeysDepthAndInvalidUtf8() throws Exception {
    ValidationResult syntax = json("{\"a\": 1,}");
    assertTrue(hasCode(syntax, "validation.json-syntax"));
    assertEquals("ERROR", syntax.issues().get(0).severity().name());

    ValidationResult scalarPointer = json("{\"a\": tru}");
    assertEquals("/a", scalarPointer.issues().get(0).pointer());

    ValidationResult arrayPointer = json("{\"items\":[1, no]}");
    assertEquals("/items/1", arrayPointer.issues().get(0).pointer());

    ValidationResult duplicate = json("{\"a\": 1, \"a\": 2}");
    assertTrue(hasCode(duplicate, "validation.json-duplicate-key"));

    ValidationResult depth =
        json("[[[[1]]]]", new ValidationLimits(1024L, 64, 16, 3, 64, 2, 16, 16));
    assertTrue(hasCode(depth, "validation.json-depth"));

    ValidationResult invalidUtf8 =
        new JsonValidator().validate("data.json", new byte[] {'"', (byte) 0xff, '"'}, DEFAULTS);
    assertTrue(hasCode(invalidUtf8, "validation.encoding"));
  }

  @Test
  void jsonAcceptsNestedDocumentsWithEscapesAndNumbers() throws Exception {
    ValidationResult result =
        json("{\"a\":[1,-2.5e+3,true,null,\"\\uD83D\\uDE00\"],\"b\":{\"c\":\"x\"}}");
    assertTrue(result.valid(), result.issues().toString());
    assertTrue(result.issues().isEmpty());
  }

  @Test
  void tomlAcceptsProfileAndDetectsUnsupportedDuplicateAndMalformed() throws Exception {
    String valid =
        "# comment\n"
            + "title = \"demo\"\n"
            + "literal = 'C:\\path'\n"
            + "hex = 0xDEAD_BEEF\n"
            + "pi = 3.14\n"
            + "enabled = true\n"
            + "when = 1979-05-27T07:32:00Z\n"
            + "items = [1, 2, 3]\n"
            + "[server]\n"
            + "port = 25565\n"
            + "[[plugins]]\n"
            + "name = \"first\"\n";
    ValidationResult accepted = toml(valid);
    assertTrue(accepted.valid(), accepted.issues().toString());

    ValidationResult unsupported = toml("value = \"\"\"multi\nline\"\"\"\n");
    assertTrue(hasCode(unsupported, "validation.toml-unsupported"));

    ValidationResult mixed = toml("items = [1, \"two\"]\n");
    assertTrue(hasCode(mixed, "validation.toml-unsupported"));

    ValidationResult duplicate = toml("a = 1\na = 2\n[server]\nb = 1\n[server]\nb = 2\n");
    assertTrue(hasCode(duplicate, "validation.toml-duplicate-key"));

    ValidationResult malformed = toml("a = \n");
    assertTrue(hasCode(malformed, "validation.toml-syntax"));
  }

  @Test
  void propertiesAcceptsEscapesContinuationsAndWarnsOnDuplicates() throws Exception {
    ValidationResult valid =
        properties(
            "# comment\n"
                + "! another\n"
                + "greeting = hello\\ world\n"
                + "path: C\\:\\\\server\n"
                + "unicode = \\u3042\\u3044\n"
                + "long = first\\\nsecond\n");
    assertTrue(valid.valid(), valid.issues().toString());
    assertTrue(valid.issues().isEmpty(), valid.issues().toString());

    ValidationResult duplicate = properties("a=1\na=2\n");
    assertTrue(duplicate.valid());
    assertEquals("validation.properties-duplicate-key", duplicate.issues().get(0).code());
    assertEquals("WARNING", duplicate.issues().get(0).severity().name());
    assertEquals("/a", duplicate.issues().get(0).pointer());

    ValidationResult invalidEscape = properties("a = \\q\n");
    assertTrue(hasCode(invalidEscape, "validation.properties-invalid-escape"));

    ValidationResult unterminated = properties("a = one\\\n");
    assertTrue(hasCode(unterminated, "validation.properties-syntax"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new PropertiesValidator().validate("../config.properties", new byte[0], DEFAULTS));
  }

  @Test
  void yamlScalarEditorPreservesUntouchedBytesAndRejectsUnsafeEdits() throws Exception {
    byte[] source =
        ("# keep this comment\n"
                + "server:\n"
                + "  port: 25565 # inline\n"
                + "  name: 'old name'\n"
                + "  quote: \"old value\"\n")
            .getBytes(StandardCharsets.UTF_8);

    byte[] plain = YamlScalarEditor.update(source, "/server/port", "25566", DEFAULTS);
    assertTrue(new String(plain, StandardCharsets.UTF_8).contains("# keep this comment\n"));
    assertTrue(new String(plain, StandardCharsets.UTF_8).contains("# inline\n"));
    assertTrue(new String(plain, StandardCharsets.UTF_8).contains("  name: 'old name'\n"));
    assertTrue(new String(plain, StandardCharsets.UTF_8).contains("  port: 25566 # inline\n"));
    assertEquals(
        "# keep this comment\nserver:\n  port: 25565 # inline\n  name: 'old name'\n  quote: \"old value\"\n",
        new String(source, StandardCharsets.UTF_8));

    byte[] single = YamlScalarEditor.update(source, "/server/name", "new name", DEFAULTS);
    assertTrue(new String(single, StandardCharsets.UTF_8).contains("  name: 'new name'\n"));

    byte[] quoted = YamlScalarEditor.update(source, "/server/quote", "new: value", DEFAULTS);
    assertTrue(new String(quoted, StandardCharsets.UTF_8).contains("  quote: \"new: value\"\n"));

    assertEquals(
        "validation.comment-edit-unsupported",
        assertThrows(
                ValidationException.class,
                () -> YamlScalarEditor.update(source, "/server/port", "1 # unsafe", DEFAULTS))
            .code());
    assertEquals(
        "validation.comment-edit-unsupported",
        assertThrows(
                ValidationException.class,
                () -> YamlScalarEditor.update(source, "/missing", "value", DEFAULTS))
            .code());
  }

  @Test
  void resultsAreDeterministicAcrossRepeatedRuns() throws Exception {
    byte[] content = "{\"z\":1,\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8);
    JsonValidator validator = new JsonValidator();
    List<ValidationResult> results = new ArrayList<ValidationResult>();
    for (int i = 0; i < 5; i++) {
      results.add(validator.validate("deterministic.json", content, DEFAULTS));
    }
    for (ValidationResult result : results) {
      assertEquals(results.get(0).issues().toString(), result.issues().toString());
      assertEquals(results.get(0).valid(), result.valid());
    }
  }

  private static ValidationResult yaml(String content) throws Exception {
    return yaml(content, DEFAULTS);
  }

  private static ValidationResult yaml(String content, ValidationLimits limits) throws Exception {
    return new YamlValidator()
        .validate("config.yml", content.getBytes(StandardCharsets.UTF_8), limits);
  }

  private static ValidationResult json(String content) throws Exception {
    return json(content, DEFAULTS);
  }

  private static ValidationResult json(String content, ValidationLimits limits) throws Exception {
    return new JsonValidator()
        .validate("data.json", content.getBytes(StandardCharsets.UTF_8), limits);
  }

  private static ValidationResult toml(String content) throws Exception {
    return new TomlValidator()
        .validate("config.toml", content.getBytes(StandardCharsets.UTF_8), DEFAULTS);
  }

  private static ValidationResult properties(String content) throws Exception {
    return new PropertiesValidator()
        .validate("config.properties", content.getBytes(StandardCharsets.UTF_8), DEFAULTS);
  }

  private static boolean hasCode(ValidationResult result, String code) {
    for (ValidationIssue issue : result.issues()) {
      if (code.equals(issue.code())) {
        return true;
      }
    }
    return false;
  }
}
