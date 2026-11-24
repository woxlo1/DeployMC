package io.github.woxlo1.deploymc.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.diff.DiffEngine;
import io.github.woxlo1.deploymc.core.diff.DiffLimits;
import io.github.woxlo1.deploymc.core.diff.DiffMode;
import io.github.woxlo1.deploymc.core.diff.DiffRequest;
import io.github.woxlo1.deploymc.core.diff.DiffResult;
import io.github.woxlo1.deploymc.core.diff.DiffStatus;
import io.github.woxlo1.deploymc.core.diff.DiffStrategy;
import io.github.woxlo1.deploymc.core.validation.Severity;
import io.github.woxlo1.deploymc.core.validation.ValidationException;
import io.github.woxlo1.deploymc.core.validation.ValidationIssue;
import io.github.woxlo1.deploymc.core.validation.ValidationLimitException;
import io.github.woxlo1.deploymc.core.validation.ValidationLimits;
import io.github.woxlo1.deploymc.core.validation.ValidationResult;
import io.github.woxlo1.deploymc.core.validation.ValidatorRegistry;
import io.github.woxlo1.deploymc.core.validation.YamlValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** PART 33.9 contract checks for implemented YAML limits and documented archive gaps. */
class YamlArchiveSafetyLimitsContractTest {

  private static final String SPEC_PATH =
      "docs/development/DeployMC_Complete_Master_Development_Specification.md";
  private static final String MANIFEST = "/hostile-inputs/hostile-inputs-manifest.properties";

  @Test
  void yamlEnforcesDepthNodeAndAliasBudgetsWithStableIssueCodes() throws Exception {
    ValidationLimitException depth =
        assertThrows(
            ValidationLimitException.class,
            () -> yaml("a:\n  b: 1\n", new ValidationLimits(1024L, 256, 16, 1, 64, 2, 64, 32)));
    assertEquals("validation.yaml-depth", depth.issueCode());
    assertEquals("validation.limit-exceeded", depth.code());
    assertEquals("maxDepth", depth.limitName());

    ValidationLimitException nodes =
        assertThrows(
            ValidationLimitException.class,
            () ->
                yaml(
                    "a:\n  b:\n    c: 1\n", new ValidationLimits(1024L, 256, 16, 8, 4, 2, 64, 32)));
    assertEquals("validation.yaml-node-limit", nodes.issueCode());
    assertEquals("maxNodes", nodes.limitName());

    ValidationLimitException aliases =
        assertThrows(
            ValidationLimitException.class,
            () ->
                yaml(
                    "base: &base\n  value: 1\ncopy1: *base\ncopy2: *base\n",
                    ValidationLimits.small()));
    assertEquals("validation.yaml-alias-limit", aliases.issueCode());
    assertEquals("maxAliasExpansionsPerNode", aliases.limitName());

    ValidationLimitException scalar =
        assertThrows(
            ValidationLimitException.class,
            () -> yaml("abcd", new ValidationLimits(1024L, 3, 16, 8, 32, 2, 16, 16)));
    assertEquals("maxCodePoints", scalar.limitName());
  }

  @Test
  void yamlReportsMultipleDocumentsTabsAndStrictUtf8WithDeterministicCodes() throws Exception {
    ValidationResult multiple = yaml("---\na: 1\n---\nb: 2\n", ValidationLimits.defaults());
    assertFalse(multiple.valid());
    assertTrue(codes(multiple).contains("validation.yaml-multiple-documents"));

    ValidationResult duplicate = yaml("a: 1\na: 2\n", ValidationLimits.defaults());
    assertFalse(duplicate.valid());
    assertTrue(codes(duplicate).contains("validation.yaml-duplicate-key"));
    assertEquals(codes(duplicate), codes(yaml("a: 1\na: 2\n", ValidationLimits.defaults())));

    byte[] invalidUtf8 = {'a', ':', ' ', (byte) 0xff, '\n'};
    ValidationResult encoding =
        new YamlValidator().validate("config.yml", invalidUtf8, ValidationLimits.defaults());
    assertFalse(encoding.valid());
    assertTrue(codes(encoding).contains("validation.encoding"));
    assertEquals("invalid-utf8", encoding.issues().get(0).parameters().get(0));

    ValidationResult nul =
        new YamlValidator()
            .validate("config.yml", new byte[] {'a', 0, 'b'}, ValidationLimits.defaults());
    assertTrue(codes(nul).contains("validation.encoding"));
    assertEquals("nul", nul.issues().get(0).parameters().get(0));

    ValidationResult tabs = yaml("server:\n\tport: 1\n", ValidationLimits.defaults());
    assertTrue(codes(tabs).contains("validation.yaml-tab"));
    assertEquals(Severity.ERROR, tabs.issues().get(0).severity());
  }

  @Test
  void registryPropagatesStructuralAndInputLimitsAsValidationLimitExceeded() throws Exception {
    ValidatorRegistry registry =
        ValidatorRegistry.withBuiltIns(new ValidationLimits(1024L, 256, 16, 1, 64, 2, 64, 32));
    ValidationLimitException structural =
        assertThrows(
            ValidationLimitException.class,
            () -> registry.validate("config.yml", utf8("a:\n  b: 1\n")));
    assertEquals("validation.limit-exceeded", structural.code());
    assertEquals("validation.yaml-depth", structural.issueCode());

    ValidatorRegistry inputLimited =
        ValidatorRegistry.withBuiltIns(new ValidationLimits(4L, 256, 16, 8, 64, 2, 64, 32));
    ValidationLimitException bytes =
        assertThrows(
            ValidationLimitException.class,
            () -> inputLimited.validate("config.yml", utf8("abcdef")));
    assertEquals("validation.limit-exceeded", bytes.code());
    assertEquals(null, bytes.issueCode());
    assertEquals("maxBytes", bytes.limitName());
  }

  @Test
  void diffLimitsMayOnlyBeLoweredAndBoundReportedWork() {
    assertEquals(2_097_152, DiffLimits.DEFAULTS.maxBytesPerSide());
    assertEquals(100_000, DiffLimits.DEFAULTS.maxJarEntries());
    assertThrows(IllegalArgumentException.class, () -> DiffLimits.DEFAULTS.withMaxBytesPerSide(0));
    assertThrows(
        IllegalArgumentException.class, () -> DiffLimits.DEFAULTS.withMaxJarEntries(100_001));
    assertThrows(
        IllegalArgumentException.class,
        () -> DiffLimits.DEFAULTS.withMaxJarDescriptorTotalBytes(2_000_000));
    assertEquals(4, DiffLimits.DEFAULTS.withMaxBytesPerSide(4).maxBytesPerSide());

    DiffResult tooLarge =
        DiffEngine.diff(
            new DiffRequest(
                utf8("aaaa"),
                utf8("bbbbb"),
                DiffMode.TEXT,
                DiffLimits.DEFAULTS.withMaxBytesPerSide(4)));
    assertEquals(DiffStatus.TOO_LARGE, tooLarge.status());
    assertEquals(DiffStrategy.NONE, tooLarge.strategy());
    assertEquals("diff.too-large", tooLarge.reason());
    assertEquals("bytes-per-side", tooLarge.limit());
    assertEquals(Long.valueOf(5L), tooLarge.observed());
  }

  @Test
  void zipMetadataReaderReportsTruncatedAndZip64NotesWithoutExtraction() {
    byte[] archive = storedZip("plugin.yml", "name: demo\n");
    int central = findSignature(archive, 0x02014b50L);
    assertTrue(central >= 0);
    byte[] truncated = Arrays.copyOf(archive, archive.length);
    putU32(truncated, central, 0L);

    DiffResult truncatedResult = diff(truncated, archive, DiffMode.JAR);
    assertEquals(DiffStatus.INVALID_INPUT, truncatedResult.status());
    assertEquals("diff.invalid-input", truncatedResult.reason());
    assertTrue(truncatedResult.notes().contains("diff.zip-truncated"));

    byte[] zip64 = new byte[22];
    putU32(zip64, 0, 0x06054b50L);
    putU16(zip64, 8, 0xffff);
    putU16(zip64, 10, 0xffff);
    putU32(zip64, 12, 0xffffffffL);
    putU32(zip64, 16, 0xffffffffL);
    byte[] changedZip64 = Arrays.copyOf(zip64, zip64.length);
    changedZip64[21] = 1;

    DiffResult zip64Result = diff(zip64, changedZip64, DiffMode.JAR);
    assertEquals(DiffStatus.INVALID_INPUT, zip64Result.status());
    assertTrue(zip64Result.notes().contains("diff.zip64-unsupported"));
  }

  @Test
  void hostileFixtureManifestDeclaresTenVersionedCases() throws Exception {
    Properties manifest = manifest();
    assertEquals("1", required(manifest, "schema.version"));
    assertFalse(required(manifest, "corpus.version").isEmpty());
    assertEquals(
        "tests/security/hostile-inputs/generate_hostile_inputs.py",
        required(manifest, "generator"));
    List<String> files = split(required(manifest, "fixture.list"));
    assertEquals(10, files.size());
    assertEquals(files.size(), Integer.parseInt(required(manifest, "fixture.count")));

    int archives = 0;
    int yaml = 0;
    for (String file : files) {
      String prefix = "fixture." + identifier(file) + ".";
      assertEquals(file, required(manifest, prefix + "file"));
      String category = required(manifest, prefix + "category");
      assertTrue("archive".equals(category) || "yaml".equals(category), file);
      assertFalse(required(manifest, prefix + "risk").isEmpty());
      assertFalse(required(manifest, prefix + "current.reader").isEmpty());
      assertFalse(required(manifest, prefix + "future.requirement").isEmpty());
      assertTrue(required(manifest, prefix + "sha256").matches("[0-9a-f]{64}"));
      assertTrue(Long.parseLong(required(manifest, prefix + "bytes")) > 0L);
      if ("archive".equals(category)) {
        archives++;
      } else {
        yaml++;
      }
    }
    assertEquals(6, archives);
    assertEquals(4, yaml);
    assertEquals(
        "not-rejected-by-source-review", required(manifest, "fixture.zip_slip.current.reader"));
    assertEquals(
        "rejected:diff.descriptor-duplicate",
        required(manifest, "fixture.duplicate_descriptor.current.reader"));
    assertEquals(
        "product-check-not-executed", required(manifest, "fixture.deep_nesting.current.reader"));
  }

  @Test
  void recordsUnimplementedArchivePathAndSizeRulesAsSpecGaps() throws Exception {
    String spec = specSection("## 33.9 YAML and Archive Safety Limits");
    assertTrue(spec.contains("enforce entry count, total size, ratio,"));
    assertTrue(spec.contains("reject entries with absolute paths, '..', drive letters, backslash"));
    assertTrue(spec.contains("names differing only by case/Unicode normalization (collision)"));
    Class<?> reader = Class.forName("io.github.woxlo1.deploymc.core.diff.ZipMetadataReader");
    for (Method method : reader.getDeclaredMethods()) {
      String name = method.getName().toLowerCase(Locale.ROOT);
      assertFalse(name.contains("ratio"), "Unexpected ratio check: " + name);
      assertFalse(name.contains("collision"), "Unexpected collision check: " + name);
      assertFalse(name.contains("symlink"), "Unexpected symlink check: " + name);
    }

    Map<String, String> gaps = new LinkedHashMap<String, String>();
    gaps.put("absolute-path and '..' entry rejection", "NOT IMPLEMENTED");
    gaps.put("drive-letter and backslash entry rejection", "NOT IMPLEMENTED");
    gaps.put("symlink entry rejection", "NOT IMPLEMENTED");
    gaps.put("case and Unicode normalization collision rejection", "NOT IMPLEMENTED");
    gaps.put("total uncompressed size limit", "NOT IMPLEMENTED");
    gaps.put("compression ratio limit", "NOT IMPLEMENTED");
    assertEquals(6, gaps.size());
    for (Map.Entry<String, String> gap : gaps.entrySet()) {
      assertEquals("NOT IMPLEMENTED", gap.getValue(), gap.getKey());
    }
  }

  private static ValidationResult yaml(String text, ValidationLimits limits)
      throws ValidationException {
    return new YamlValidator().validate("config.yml", utf8(text), limits);
  }

  private static DiffResult diff(byte[] before, byte[] after, DiffMode mode) {
    return DiffEngine.diff(new DiffRequest(before, after, mode));
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static List<String> codes(ValidationResult result) {
    List<String> resultCodes = new ArrayList<String>();
    for (ValidationIssue issue : result.issues()) {
      resultCodes.add(issue.code());
    }
    return resultCodes;
  }

  /** Builds one stored ZIP entry with a valid central directory and no archive comment. */
  private static byte[] storedZip(String name, String content) {
    byte[] nameBytes = utf8(name);
    byte[] contentBytes = utf8(content);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(contentBytes);
    ByteArrayOutputStream local = new ByteArrayOutputStream();
    byte[] localHeader = new byte[30 + nameBytes.length];
    putU32(localHeader, 0, 0x04034b50L);
    putU16(localHeader, 4, 20);
    putU32(localHeader, 14, crc.getValue());
    putU32(localHeader, 18, contentBytes.length);
    putU32(localHeader, 22, contentBytes.length);
    putU16(localHeader, 26, nameBytes.length);
    System.arraycopy(nameBytes, 0, localHeader, 30, nameBytes.length);
    local.write(localHeader, 0, localHeader.length);
    local.write(contentBytes, 0, contentBytes.length);

    byte[] centralHeader = new byte[46 + nameBytes.length];
    putU32(centralHeader, 0, 0x02014b50L);
    putU16(centralHeader, 4, 20);
    putU16(centralHeader, 6, 20);
    putU32(centralHeader, 16, crc.getValue());
    putU32(centralHeader, 20, contentBytes.length);
    putU32(centralHeader, 24, contentBytes.length);
    putU16(centralHeader, 28, nameBytes.length);
    putU32(centralHeader, 42, 0L);
    System.arraycopy(nameBytes, 0, centralHeader, 46, nameBytes.length);

    byte[] end = new byte[22];
    putU32(end, 0, 0x06054b50L);
    putU16(end, 8, 1);
    putU16(end, 10, 1);
    putU32(end, 12, centralHeader.length);
    putU32(end, 16, local.size());
    ByteArrayOutputStream archive = new ByteArrayOutputStream();
    archive.write(local.toByteArray(), 0, local.size());
    archive.write(centralHeader, 0, centralHeader.length);
    archive.write(end, 0, end.length);
    return archive.toByteArray();
  }

  private static int findSignature(byte[] bytes, long signature) {
    for (int offset = 0; offset + 4 <= bytes.length; offset++) {
      if (u32(bytes, offset) == signature) {
        return offset;
      }
    }
    return -1;
  }

  private static void putU16(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) (value & 0xff);
    bytes[offset + 1] = (byte) ((value >>> 8) & 0xff);
  }

  private static void putU32(byte[] bytes, int offset, long value) {
    for (int i = 0; i < 4; i++) {
      bytes[offset + i] = (byte) ((value >>> (8 * i)) & 0xff);
    }
  }

  private static long u32(byte[] bytes, int offset) {
    return (bytes[offset] & 0xffL)
        | ((bytes[offset + 1] & 0xffL) << 8)
        | ((bytes[offset + 2] & 0xffL) << 16)
        | ((bytes[offset + 3] & 0xffL) << 24);
  }

  private static Properties manifest() throws IOException {
    Properties properties = new Properties();
    try (InputStream stream =
        YamlArchiveSafetyLimitsContractTest.class.getResourceAsStream(MANIFEST)) {
      assertNotNull(stream, "Missing test resource: " + MANIFEST);
      properties.load(stream);
    }
    return properties;
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    assertNotNull(value, "Missing manifest key: " + key);
    return value;
  }

  private static List<String> split(String values) {
    List<String> result = new ArrayList<String>();
    for (String value : values.split(",")) {
      if (!value.isEmpty()) {
        result.add(value);
      }
    }
    return result;
  }

  private static String identifier(String file) {
    String name = file.substring(file.lastIndexOf('/') + 1);
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      name = name.substring(0, dot);
    }
    return name.replace('-', '_');
  }

  private static String specSection(String heading) throws IOException {
    Path file = locateSpec();
    assertNotNull(file, "Missing master specification: " + SPEC_PATH);
    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    int start = text.indexOf(heading);
    assertTrue(start >= 0, "Missing specification section: " + heading);
    int end = text.indexOf("\n# PART ", start + heading.length());
    return text.substring(start, end < 0 ? text.length() : end);
  }

  private static Path locateSpec() {
    Path relative = Paths.get(SPEC_PATH);
    Path current = Paths.get("").toAbsolutePath();
    List<Path> candidates = new ArrayList<Path>();
    candidates.add(current);
    if (current.getParent() != null) {
      candidates.add(current.getParent());
      if (current.getParent().getParent() != null) {
        candidates.add(current.getParent().getParent());
      }
    }
    for (Path candidate : candidates) {
      Path resolved = candidate.resolve(relative);
      if (Files.isRegularFile(resolved)) {
        return resolved;
      }
    }
    return null;
  }
}
