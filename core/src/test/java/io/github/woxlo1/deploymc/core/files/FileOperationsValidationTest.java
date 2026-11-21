package io.github.woxlo1.deploymc.core.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.validation.Severity;
import io.github.woxlo1.deploymc.core.validation.ValidationException;
import io.github.woxlo1.deploymc.core.validation.ValidationGate;
import io.github.woxlo1.deploymc.core.validation.ValidationIssue;
import io.github.woxlo1.deploymc.core.validation.ValidationLimitException;
import io.github.woxlo1.deploymc.core.validation.ValidationLimits;
import io.github.woxlo1.deploymc.core.validation.ValidationResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOperationsValidationTest {

  @TempDir Path tempDir;

  private RecordingSnapshotHook snapshots;
  private FileOperationsService service;

  @BeforeEach
  void setUp() {
    snapshots = new RecordingSnapshotHook();
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), snapshots, GATE);
  }

  @Test
  void errorFindingPreventsMutationAndSnapshot() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    FileInfo before = service.stat("data.txt");
    Behavior behavior = new Behavior();
    behavior.result =
        ValidationResult.matched(
            "data.txt",
            "test",
            Collections.singletonList(
                new ValidationIssue(
                    Severity.ERROR,
                    "validation.test-error",
                    "/x",
                    1,
                    1,
                    -1L,
                    Collections.<String>emptyList())),
            FileOperationsValidationTest.LIMITS);
    service = serviceWith(behavior);

    FileOperationException failure =
        assertThrows(
            FileOperationException.class,
            () ->
                service.write(
                    "data.txt", "replacement".getBytes(StandardCharsets.UTF_8), before.revision()));

    assertEquals(FileOperationException.Reason.VALIDATION_FAILED, failure.reason());
    assertEquals("validation.failed", failure.code());
    assertEquals("validation.test-error", failure.parameters().get(1));
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());
  }

  @Test
  void limitAndValidatorFailuresFailClosed() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    FileInfo before = service.stat("data.txt");
    Behavior limitBehavior = new Behavior();
    limitBehavior.failure = new ValidationLimitException("validation.json-depth", "maxDepth", 9, 8);
    service = serviceWith(limitBehavior);

    FileOperationException limitFailure =
        assertThrows(
            FileOperationException.class,
            () ->
                service.write(
                    "data.txt", "replacement".getBytes(StandardCharsets.UTF_8), before.revision()));
    assertEquals(FileOperationException.Reason.VALIDATION_LIMIT, limitFailure.reason());
    assertEquals("validation.limit-exceeded", limitFailure.code());
    assertEquals("validation.json-depth", limitFailure.issueCode());
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());

    Behavior unavailableBehavior = new Behavior();
    unavailableBehavior.failure =
        new ValidationException("validation.test-unavailable", "code.validation.failed");
    service = serviceWith(unavailableBehavior);
    FileOperationException unavailable =
        assertThrows(
            FileOperationException.class,
            () ->
                service.write(
                    "data.txt", "replacement".getBytes(StandardCharsets.UTF_8), before.revision()));
    assertEquals(FileOperationException.Reason.VALIDATOR_UNAVAILABLE, unavailable.reason());
    assertEquals("validation.validator-unavailable", unavailable.code());
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());
  }

  @Test
  void warningsAreExposedAndDoNotBlockWrites() throws Exception {
    Behavior behavior = new Behavior();
    behavior.result =
        ValidationResult.matched(
            "warn.txt",
            "test",
            Collections.singletonList(
                new ValidationIssue(
                    Severity.WARNING,
                    "validation.properties-duplicate-key",
                    "/a",
                    1,
                    1,
                    -1L,
                    Collections.<String>emptyList())),
            FileOperationsValidationTest.LIMITS);
    service = serviceWith(behavior);

    ValidatedFileResult result =
        service.writeValidated("warn.txt", "value".getBytes(StandardCharsets.UTF_8), null);

    assertTrue(result.validation().valid());
    assertEquals("validation.properties-duplicate-key", result.validation().issues().get(0).code());
    assertArrayEquals(
        "value".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(tempDir.resolve("warn.txt")));
  }

  @Test
  void uploadValidatesStagedBytesBeforeTargetIsChanged() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    FileInfo before = service.stat("data.txt");
    Behavior behavior = new Behavior();
    behavior.result =
        ValidationResult.matched(
            "data.txt",
            "test",
            Collections.singletonList(
                new ValidationIssue(
                    Severity.ERROR,
                    "validation.test-error",
                    "",
                    0,
                    0,
                    -1L,
                    Collections.<String>emptyList())),
            FileOperationsValidationTest.LIMITS);
    service = serviceWith(behavior);

    assertThrows(
        FileOperationException.class,
        () ->
            service.uploadValidated(
                "data.txt",
                new ByteArrayInputStream("replacement".getBytes(StandardCharsets.UTF_8)),
                -1L,
                before.revision()));
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());
  }

  @Test
  void createRejectsInvalidContentWithoutLeavingATarget() throws Exception {
    Behavior behavior = new Behavior();
    behavior.result =
        ValidationResult.matched(
            "new.txt",
            "test",
            Collections.singletonList(
                new ValidationIssue(
                    Severity.ERROR,
                    "validation.test-error",
                    "",
                    0,
                    0,
                    -1L,
                    Collections.<String>emptyList())),
            FileOperationsValidationTest.LIMITS);
    service = serviceWith(behavior);

    assertThrows(
        FileOperationException.class,
        () -> service.create("new.txt", "content".getBytes(StandardCharsets.UTF_8)));
    assertTrue(Files.notExists(tempDir.resolve("new.txt")));
    assertTrue(snapshots.empty());
  }

  @Test
  void moveAndDeleteValidateExistingContentBeforeSnapshots() throws Exception {
    Files.write(tempDir.resolve("source.txt"), "source".getBytes(StandardCharsets.UTF_8));
    Behavior behavior = new Behavior();
    behavior.failure =
        new ValidationException("validation.test-unavailable", "code.validation.failed");
    service = serviceWith(behavior);

    assertThrows(
        FileOperationException.class, () -> service.move("source.txt", "target.txt", false, null));
    assertThrows(
        FileOperationException.class,
        () -> service.delete("source.txt", DeleteRequest.single(null)));
    assertTrue(Files.exists(tempDir.resolve("source.txt")));
    assertTrue(Files.notExists(tempDir.resolve("target.txt")));
    assertTrue(snapshots.empty());
  }

  @Test
  void byteArrayWritesValidateAnImmutableCopy() throws Exception {
    byte[] content = "value".getBytes(StandardCharsets.UTF_8);
    CapturingGate capturing = new CapturingGate();
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), snapshots, capturing);

    service.write("copy.txt", content, null);

    assertNotSame(content, capturing.content);
    assertArrayEquals(content, capturing.content);
  }

  @Test
  void validationReadLimitAppliesToExistingContent() throws Exception {
    byte[] oversized = new byte[33];
    Arrays.fill(oversized, (byte) 'a');
    Files.write(tempDir.resolve("large.txt"), oversized);
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir),
            FileOperationLimits.defaults(),
            snapshots,
            new LimitedGate(32L));

    FileOperationException failure =
        assertThrows(
            FileOperationException.class,
            () -> service.delete("large.txt", DeleteRequest.single(null)));

    assertEquals(FileOperationException.Reason.VALIDATION_LIMIT, failure.reason());
    assertEquals("validation.limit-exceeded", failure.code());
    assertTrue(Files.exists(tempDir.resolve("large.txt")));
    assertTrue(snapshots.empty());
  }

  @Test
  void noGatePreservesLegacyBehavior() throws Exception {
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), snapshots);

    ValidatedFileResult result =
        service.writeValidated("plain.txt", "value".getBytes(StandardCharsets.UTF_8), null);

    assertNull(result.validation());
    assertArrayEquals(
        "value".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(tempDir.resolve("plain.txt")));
  }

  private FileOperationsService serviceWith(Behavior behavior) {
    ValidationGate gate =
        (path, content) -> {
          behavior.calls++;
          if (behavior.failure != null) {
            throw behavior.failure;
          }
          return behavior.result;
        };
    return new FileOperationsService(
        new SandboxResolver(tempDir), FileOperationLimits.defaults(), snapshots, gate);
  }

  private static final ValidationLimits LIMITS =
      new ValidationLimits(4096L, 4096, 128, 8, 128, 2, 64, 32);

  private static final class Behavior {
    private ValidationResult result;
    private ValidationException failure;
    private int calls;
  }

  private static final class CapturingGate implements ValidationGate {
    private byte[] content;

    @Override
    public ValidationResult validate(String relativePath, byte[] content) {
      this.content = content;
      return ValidationResult.unmatched(relativePath);
    }
  }

  private static final class LimitedGate implements ValidationGate {
    private final ValidationLimits limits;

    private LimitedGate(long maxBytes) {
      this.limits = new ValidationLimits(maxBytes, 4096, 128, 8, 128, 2, 64, 32);
    }

    @Override
    public ValidationResult validate(String relativePath, byte[] content) {
      return ValidationResult.unmatched(relativePath);
    }

    @Override
    public ValidationLimits limits() {
      return limits;
    }
  }

  private static final ValidationGate GATE = (path, content) -> ValidationResult.unmatched(path);
}
