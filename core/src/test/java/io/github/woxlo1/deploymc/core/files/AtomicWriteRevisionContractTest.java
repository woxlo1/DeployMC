package io.github.woxlo1.deploymc.core.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.validation.Severity;
import io.github.woxlo1.deploymc.core.validation.ValidationGate;
import io.github.woxlo1.deploymc.core.validation.ValidationIssue;
import io.github.woxlo1.deploymc.core.validation.ValidationLimits;
import io.github.woxlo1.deploymc.core.validation.ValidationResult;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PART 33.2 contract and regression checks for the real atomic write path. */
class AtomicWriteRevisionContractTest {

  private static final String SPEC_PATH =
      "docs/development/DeployMC_Complete_Master_Development_Specification.md";
  private static final String TEMP_PREFIX = ".deploymc-file-";

  @TempDir Path tempDir;

  @Test
  void revisionIsPrefixedSha256WithSixtyFourLowercaseHexDigits() {
    String revision = FileRevision.of("abc".getBytes(StandardCharsets.UTF_8));

    assertEquals(
        "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", revision);
    assertTrue(revision.matches("^sha256:[0-9a-f]{64}$"));
    assertTrue(FileRevision.isValid(revision));
    assertFalse(FileRevision.isValid(revision.toUpperCase(Locale.ROOT)));
    assertFalse(FileRevision.isValid("sha256:abc"));
    assertFalse(FileRevision.isValid("md5:" + revision.substring("sha256:".length())));
  }

  @Test
  void existingFileWithoutExpectedRevisionFailsClosed() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    RecordingSnapshotHook snapshots = new RecordingSnapshotHook();
    FileOperationsService service = service(snapshots, WriteCrashPoint.NONE, null);

    FileOperationException failure =
        assertThrows(
            FileOperationException.class,
            () -> service.write("data.txt", "replacement".getBytes(StandardCharsets.UTF_8), null));

    assertEquals(FileOperationException.Reason.PRECONDITION_REQUIRED, failure.reason());
    assertEquals("request.precondition-required", failure.code());
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());
    assertTrue(tempFiles().isEmpty());
  }

  @Test
  void revisionConflictCarriesExpectedAndActualRevisions() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    String current = FileRevision.of(original);
    RecordingSnapshotHook snapshots = new RecordingSnapshotHook();
    FileOperationsService service = service(snapshots, WriteCrashPoint.NONE, null);

    FileOperationException failure =
        assertThrows(
            FileOperationException.class,
            () ->
                service.write(
                    "data.txt", "replacement".getBytes(StandardCharsets.UTF_8), "sha256:wrong"));

    assertEquals(FileOperationException.Reason.REVISION_CONFLICT, failure.reason());
    assertEquals("files.revision-conflict", failure.code());
    assertTrue(failure.parameters().contains("sha256:wrong"));
    assertTrue(failure.parameters().contains(current));
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());
    assertTrue(tempFiles().isEmpty());
  }

  @Test
  void snapshotsOldBytesBeforeSameParentTempAndSingleCommit() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    ObservingHook snapshots = new ObservingHook(tempDir.resolve("data.txt"));
    TempObservingCrash crash = new TempObservingCrash(tempDir, snapshots);
    FileOperationsService service = service(snapshots, crash, null);

    FileInfo written = service.write("data.txt", replacement, FileRevision.of(original));

    assertEquals(1, snapshots.requests().size());
    assertEquals("before-overwrite", snapshots.requests().get(0).reason());
    assertEquals(1, snapshots.requests().get(0).entries().size());
    assertEquals("data.txt", snapshots.requests().get(0).entries().get(0).relativePath());
    assertEquals(
        FileRevision.of(original), snapshots.requests().get(0).entries().get(0).revision());
    assertArrayEquals(original, snapshots.observedBytes());
    assertEquals(1, crash.beforeCommits());
    assertEquals(1, crash.afterCommits());
    assertEquals(tempDir.toString(), crash.tempParent());
    assertEquals(1, crash.tempAtCommit().size());
    assertFalse(Files.exists(crash.tempAtCommit().get(0)));
    assertTrue(tempFiles().isEmpty());
    assertArrayEquals(replacement, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertEquals(written.revision(), FileRevision.of(replacement));
  }

  @Test
  void crashBeforeCommitLeavesOldBytesAndNoTempFile() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    RecordingSnapshotHook snapshots = new RecordingSnapshotHook();
    FileOperationsService service =
        service(snapshots, new CrashingWrite(Stage.BEFORE_COMMIT), null);

    assertThrows(
        SimulatedCrash.class,
        () ->
            service.write(
                "data.txt",
                "replacement".getBytes(StandardCharsets.UTF_8),
                FileRevision.of(original)));

    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(tempFiles().isEmpty());
    assertEquals(1, snapshots.requests().size());
  }

  @Test
  void crashAfterCommitLeavesCompleteNewBytesAndNoTempFile() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    RecordingSnapshotHook snapshots = new RecordingSnapshotHook();
    FileOperationsService service = service(snapshots, new CrashingWrite(Stage.AFTER_COMMIT), null);

    assertThrows(
        SimulatedCrash.class,
        () -> service.write("data.txt", replacement, FileRevision.of(original)));

    assertArrayEquals(replacement, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertEquals(
        FileRevision.of(replacement),
        FileRevision.of(Files.readAllBytes(tempDir.resolve("data.txt"))));
    assertTrue(tempFiles().isEmpty());
    assertEquals(1, snapshots.requests().size());
  }

  @Test
  void validationFailurePreventsMutationAndSnapshot() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("data.txt"), original);
    RecordingSnapshotHook snapshots = new RecordingSnapshotHook();
    FileOperationsService service = service(snapshots, WriteCrashPoint.NONE, REJECTING_GATE);

    FileOperationException failure =
        assertThrows(
            FileOperationException.class,
            () ->
                service.write(
                    "data.txt",
                    "replacement".getBytes(StandardCharsets.UTF_8),
                    FileRevision.of(original)));

    assertEquals(FileOperationException.Reason.VALIDATION_FAILED, failure.reason());
    assertTrue(failure.parameters().contains("validation.contract-error"));
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("data.txt")));
    assertTrue(snapshots.empty());
    assertTrue(tempFiles().isEmpty());
  }

  @Test
  void twoWritersWithTheSameExpectedRevisionConflictDeterministically() throws Exception {
    byte[] original = "shared".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("race.txt"), original);
    String expected = FileRevision.of(original);
    RecordingSnapshotHook firstSnapshots = new RecordingSnapshotHook();
    RecordingSnapshotHook secondSnapshots = new RecordingSnapshotHook();
    FileOperationsService first = service(firstSnapshots, WriteCrashPoint.NONE, null);
    FileOperationsService second = service(secondSnapshots, WriteCrashPoint.NONE, null);

    FileInfo winner =
        first.write("race.txt", "writer-one".getBytes(StandardCharsets.UTF_8), expected);
    FileOperationException loser =
        assertThrows(
            FileOperationException.class,
            () ->
                second.write("race.txt", "writer-two".getBytes(StandardCharsets.UTF_8), expected));

    assertEquals(FileOperationException.Reason.REVISION_CONFLICT, loser.reason());
    assertTrue(loser.parameters().contains(expected));
    assertTrue(loser.parameters().contains(winner.revision()));
    assertArrayEquals(
        "writer-one".getBytes(StandardCharsets.UTF_8),
        Files.readAllBytes(tempDir.resolve("race.txt")));
    assertEquals(1, firstSnapshots.requests().size());
    assertTrue(secondSnapshots.empty());
    assertTrue(tempFiles().isEmpty());
  }

  @Test
  void recordsOsLockAuditEventsAndStartupCleanupAsUnimplementedSpecGaps() throws Exception {
    String spec = specSection("## 33.2 Atomic File Write With Revision Check");
    assertTrue(spec.contains("acquire OS file lock if available"));
    assertTrue(spec.contains("audit(write, path, newRevision)"));
    assertTrue(spec.contains("publish file.changed event"));
    assertTrue(spec.contains("temp files are cleaned up on startup"));
    assertFalse(declaresMethodContaining(FileOperationsService.class, "audit"));
    assertFalse(declaresMethodContaining(FileOperationsService.class, "publish"));
    assertFalse(declaresMethodContaining(FileOperationsService.class, "cleanup"));

    Map<String, String> gaps = new LinkedHashMap<String, String>();
    gaps.put("OS-level per-path file lock", "NOT IMPLEMENTED");
    gaps.put("audit write event", "NOT IMPLEMENTED");
    gaps.put("file.changed event", "NOT IMPLEMENTED");
    gaps.put("startup temporary-file cleanup", "NOT IMPLEMENTED");
    assertEquals(4, gaps.size());
    for (Map.Entry<String, String> gap : gaps.entrySet()) {
      assertEquals("NOT IMPLEMENTED", gap.getValue(), gap.getKey());
    }
  }

  private FileOperationsService service(
      SnapshotHook snapshots, WriteCrashPoint crashPoint, ValidationGate gate) {
    return new FileOperationsService(
        new SandboxResolver(tempDir), FileOperationLimits.defaults(), snapshots, crashPoint, gate);
  }

  private List<Path> tempFiles() {
    List<Path> result = new ArrayList<Path>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
      for (Path path : stream) {
        if (path.getFileName().toString().startsWith(TEMP_PREFIX)) {
          result.add(path);
        }
      }
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    return result;
  }

  private static boolean declaresMethodContaining(Class<?> owner, String fragment) {
    for (Method method : owner.getDeclaredMethods()) {
      if (method.getName().toLowerCase(Locale.ROOT).contains(fragment)) {
        return true;
      }
    }
    return false;
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

  private static final ValidationGate REJECTING_GATE =
      (path, content) ->
          ValidationResult.matched(
              path,
              "contract",
              Collections.singletonList(
                  new ValidationIssue(
                      Severity.ERROR,
                      "validation.contract-error",
                      "",
                      0,
                      0,
                      -1L,
                      Collections.<String>emptyList())),
              ValidationLimits.defaults());

  private enum Stage {
    BEFORE_COMMIT,
    AFTER_COMMIT
  }

  private static final class SimulatedCrash extends AssertionError {
    private static final long serialVersionUID = 1L;
  }

  private static final class CrashingWrite implements WriteCrashPoint {
    private final Stage stage;

    private CrashingWrite(Stage stage) {
      this.stage = stage;
    }

    @Override
    public void beforeCommit() {
      if (stage == Stage.BEFORE_COMMIT) {
        throw new SimulatedCrash();
      }
    }

    @Override
    public void afterCommit() {
      if (stage == Stage.AFTER_COMMIT) {
        throw new SimulatedCrash();
      }
    }
  }

  /** Snapshot hook that records the target bytes at capture time. */
  private static final class ObservingHook implements SnapshotHook {
    private final Path target;
    private final List<SnapshotRequest> requests = new ArrayList<SnapshotRequest>();
    private byte[] observedBytes;

    private ObservingHook(Path target) {
      this.target = target;
    }

    @Override
    public String capture(SnapshotRequest request) throws IOException {
      requests.add(request);
      observedBytes = Files.readAllBytes(target);
      return "snapshot-" + requests.size();
    }

    private List<SnapshotRequest> requests() {
      return requests;
    }

    private byte[] observedBytes() {
      return observedBytes;
    }
  }

  /** Crash point that observes the staged temporary file immediately before the atomic move. */
  private static final class TempObservingCrash implements WriteCrashPoint {
    private final Path parent;
    private final ObservingHook snapshots;
    private int beforeCommits;
    private int afterCommits;
    private String tempParent;
    private List<Path> tempAtCommit;

    private TempObservingCrash(Path parent, ObservingHook snapshots) {
      this.parent = parent;
      this.snapshots = snapshots;
    }

    @Override
    public void beforeCommit() {
      beforeCommits++;
      assertEquals(1, snapshots.requests().size());
      tempAtCommit = temps(parent);
      assertEquals(1, tempAtCommit.size());
      tempParent = tempAtCommit.get(0).getParent().toString();
    }

    @Override
    public void afterCommit() {
      afterCommits++;
    }

    private int beforeCommits() {
      return beforeCommits;
    }

    private int afterCommits() {
      return afterCommits;
    }

    private String tempParent() {
      return tempParent;
    }

    private List<Path> tempAtCommit() {
      return tempAtCommit;
    }
  }

  private static List<Path> temps(Path directory) {
    List<Path> result = new ArrayList<Path>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path path : stream) {
        if (path.getFileName().toString().startsWith(TEMP_PREFIX)) {
          result.add(path);
        }
      }
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    return result;
  }
}
