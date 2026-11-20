package io.github.woxlo1.deploymc.core.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOperationsCrashSafetyTest {

  @TempDir Path tempDir;

  private enum Stage {
    BEFORE_COMMIT,
    AFTER_COMMIT
  }

  @Test
  void crashBeforeCommitLeavesOldContentAndNoTemporaryFile() throws Exception {
    byte[] old = "old content".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("crash.txt"), old);
    FileOperationsService service = service(Stage.BEFORE_COMMIT);
    String revision = FileRevision.of(old);

    assertThrows(
        SimulatedCrash.class,
        () -> service.write("crash.txt", "new content".getBytes(StandardCharsets.UTF_8), revision));

    assertArrayEquals(old, Files.readAllBytes(tempDir.resolve("crash.txt")));
    assertEquals(0, tempFiles().size());
  }

  @Test
  void crashAfterCommitLeavesCompleteNewContent() throws Exception {
    byte[] old = "old content".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("crash.txt"), old);
    FileOperationsService service = service(Stage.AFTER_COMMIT);
    String revision = FileRevision.of(old);

    assertThrows(
        SimulatedCrash.class,
        () -> service.write("crash.txt", "new content".getBytes(StandardCharsets.UTF_8), revision));

    assertEquals(
        "new content",
        new String(Files.readAllBytes(tempDir.resolve("crash.txt")), StandardCharsets.UTF_8));
    assertEquals(0, tempFiles().size());
  }

  private FileOperationsService service(Stage stage) {
    return new FileOperationsService(
        new SandboxResolver(tempDir),
        FileOperationLimits.defaults(),
        new RecordingSnapshotHook(),
        new CrashingWrite(stage));
  }

  private List<Path> tempFiles() throws IOException {
    List<Path> result = new ArrayList<Path>();
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
      for (Path path : stream) {
        if (path.getFileName().toString().startsWith(".deploymc-file-")) {
          result.add(path);
        }
      }
    }
    return result;
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
}
