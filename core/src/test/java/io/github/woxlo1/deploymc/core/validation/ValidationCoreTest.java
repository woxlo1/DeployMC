package io.github.woxlo1.deploymc.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ValidationCoreTest {

  @Test
  void strictDecoderRejectsInvalidUtf8WithByteOffset() throws Exception {
    byte[] bytes = new byte[] {'a', (byte) 0xff, 'b'};

    TextInput input = TextInput.decode(bytes, ValidationLimits.defaults());

    assertTrue(input.hasEncodingIssue());
    assertEquals("validation.encoding", input.encodingIssue().code());
    assertEquals(1L, input.encodingIssue().offset());
    assertEquals(Collections.singletonList("invalid-utf8"), input.encodingIssue().parameters());
  }

  @Test
  void strictDecoderRejectsRawNulAndOverlongSequences() throws Exception {
    assertTrue(
        TextInput.decode(new byte[] {'a', 0, 'b'}, ValidationLimits.defaults())
            .encodingIssue()
            .parameters()
            .contains("nul"));
    assertTrue(
        TextInput.decode(new byte[] {(byte) 0xc0, (byte) 0xaf}, ValidationLimits.defaults())
            .hasEncodingIssue());
  }

  @Test
  void bomIsSkippedForTextButCountedForOffsets() throws Exception {
    byte[] withBom =
        new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'a', (byte) 0xc3, (byte) 0xa9};

    TextInput input = TextInput.decode(withBom, ValidationLimits.defaults());

    assertEquals("a\u00e9", input.text());
    assertEquals(3, input.bomBytes());
    assertEquals(3L, input.byteOffset(0));
    assertEquals(4L, input.byteOffset(1));
    assertEquals(6L, input.byteOffset(2));
  }

  @Test
  void inputLimitsAreInclusiveAndFailClosed() throws Exception {
    ValidationLimits small = new ValidationLimits(4L, 2, 4, 4, 16, 2, 8, 4);

    assertEquals("", TextInput.decode(new byte[0], small).text());
    assertEquals("ab", TextInput.decode("ab".getBytes(StandardCharsets.UTF_8), small).text());
    assertEquals(
        "maxCodePoints",
        assertThrows(
                ValidationLimitException.class,
                () -> TextInput.decode("abc".getBytes(StandardCharsets.UTF_8), small))
            .limitName());
    assertEquals(
        "maxBytes",
        assertThrows(
                ValidationLimitException.class,
                () -> TextInput.decode("abcde".getBytes(StandardCharsets.UTF_8), small))
            .limitName());
    ValidationLimits lineLimited = new ValidationLimits(16L, 16, 2, 4, 16, 2, 8, 4);
    assertEquals(
        "maxLines",
        assertThrows(
                ValidationLimitException.class,
                () -> TextInput.decode("a\nb\nc".getBytes(StandardCharsets.UTF_8), lineLimited))
            .limitName());
  }

  @Test
  void findingsAreDeterministicallyOrderedAndTruncated() {
    ValidationLimits limits = new ValidationLimits(1024L, 1024, 64, 8, 64, 2, 64, 2);
    List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
    issues.add(issue(Severity.WARNING, "validation.z-last", "/z"));
    issues.add(issue(Severity.ERROR, "validation.b-error", "/b"));
    issues.add(issue(Severity.ERROR, "validation.a-error", "/a"));

    ValidationResult first = ValidationResult.matched("f.json", "json", issues, limits);
    ValidationResult second = ValidationResult.matched("f.json", "json", issues, limits);

    assertFalse(first.valid());
    assertEquals(2, first.issues().size());
    assertEquals("validation.a-error", first.issues().get(0).code());
    assertEquals("validation.b-error", first.issues().get(1).code());
    assertEquals(first.issues().toString(), second.issues().toString());
  }

  @Test
  void unmatchedResultIsExplicitAndNotPresentedAsValidated() throws Exception {
    ValidationResult result = ValidatorRegistry.empty().validate("archive.bin", new byte[] {1});

    assertFalse(result.matched());
    assertNull(result.format());
    assertTrue(result.issues().isEmpty());
    assertEquals("archive.bin", result.path());
    assertEquals("validation.bad-path", "validation.bad-path");
    assertThrows(
        IllegalArgumentException.class,
        () -> ValidatorRegistry.empty().validate("../x", new byte[0]));
  }

  @Test
  void registryUsesExactPathThenPatternThenExtension() throws Exception {
    ValidatorRegistry registry =
        ValidatorRegistry.empty()
            .register(
                "ext",
                Collections.<String>emptySet(),
                Collections.<String>emptyList(),
                Collections.singleton(".txt"),
                new StubValidator("ext"))
            .register(
                "broad",
                Collections.<String>emptySet(),
                Collections.singletonList("*.txt"),
                Collections.<String>emptySet(),
                new StubValidator("broad"))
            .register(
                "config",
                Collections.<String>emptySet(),
                Collections.singletonList("config/*.txt"),
                Collections.<String>emptySet(),
                new StubValidator("config"))
            .register(
                "exact",
                Collections.singleton("config/special.txt"),
                Collections.<String>emptyList(),
                Collections.<String>emptySet(),
                new StubValidator("exact"));

    assertEquals("exact", registry.validate("config/special.txt", new byte[0]).format());
    assertEquals("config", registry.validate("config/other.txt", new byte[0]).format());
    assertEquals("broad", registry.validate("other.txt", new byte[0]).format());
    assertFalse(registry.validate("other.bin", new byte[0]).matched());
  }

  @Test
  void unexpectedValidatorFailureFailsClosed() {
    ValidatorRegistry registry =
        ValidatorRegistry.empty()
            .register(
                "broken",
                Collections.singleton("bad.yml"),
                Collections.<String>emptyList(),
                Collections.<String>emptySet(),
                new ContentValidator() {
                  @Override
                  public String id() {
                    return "broken";
                  }

                  @Override
                  public ValidationResult validate(
                      String relativePath, byte[] content, ValidationLimits limits) {
                    throw new IllegalStateException("parser exploded");
                  }
                });

    ValidatorUnavailableException failure =
        assertThrows(
            ValidatorUnavailableException.class,
            () -> registry.validate("bad.yml", new byte[] {'x'}));
    assertEquals("validation.validator-unavailable", failure.code());
    assertNotNull(failure.getCause());
  }

  @Test
  void detailsAreLocalizedWhileCodesAndPointersAreStable() {
    ValidationIssue issue = issue(Severity.ERROR, "validation.json-syntax", "/a/b");

    assertEquals("JSON syntax error.", issue.detail(Locale.ENGLISH));
    assertEquals("JSON の構文エラーです。", issue.detail(Locale.JAPANESE));
    assertEquals("validation.json-syntax", issue.code());
    assertEquals("/a/b", issue.pointer());
  }

  @Test
  void pointersEscapeTokensRoundTrip() {
    assertEquals("/a~1b/a~1b", Pointers.append("/a~1b", "a/b"));
    assertEquals("a/b~c", Pointers.unescape("a~1b~0c"));
    assertThrows(IllegalArgumentException.class, () -> Pointers.unescape("a~2b"));
  }

  private static ValidationIssue issue(Severity severity, String code, String pointer) {
    return new ValidationIssue(severity, code, pointer, 1, 1, -1L, Collections.<String>emptyList());
  }

  private static final class StubValidator implements ContentValidator {
    private final String id;

    private StubValidator(String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits) {
      return ValidationResult.matched(
          relativePath, id, Collections.<ValidationIssue>emptyList(), limits);
    }
  }
}
