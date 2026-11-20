package io.github.woxlo1.deploymc.core.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOperationsServiceTest {

  @TempDir Path tempDir;

  private FileOperationsService service;

  @BeforeEach
  void setUp() {
    service = new FileOperationsService(new SandboxResolver(tempDir));
  }

  @Test
  void statReturnsStrongRevisionWithoutExposingAbsolutePath() throws Exception {
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("note.txt"), bytes);

    FileInfo info = service.stat("note.txt");

    assertEquals("note.txt", info.relativePath());
    assertEquals(FileRevision.of(bytes), info.revision());
    assertTrue(info.revision().startsWith("sha256:"));
    assertFalse(info.directory());
    assertFalse(info.toString().contains(tempDir.toString()));
  }

  @Test
  void readSupportsFullContentAndBoundedRanges() throws Exception {
    Files.write(tempDir.resolve("range.txt"), "abcdef".getBytes(StandardCharsets.US_ASCII));

    FileContent full = service.read("range.txt", ReadRange.full());
    FileContent slice = service.read("range.txt", ReadRange.between(2L, 3L));
    FileContent tail = service.read("range.txt", ReadRange.from(4L));

    assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII), full.content());
    assertArrayEquals("cde".getBytes(StandardCharsets.US_ASCII), slice.content());
    assertArrayEquals("ef".getBytes(StandardCharsets.US_ASCII), tail.content());
    assertEquals(2L, slice.rangeStart());
    assertEquals(6L, slice.totalSize());
    assertEquals(full.revision(), slice.revision());
  }

  @Test
  void readRejectsOutOfBoundsRanges() throws Exception {
    Files.write(tempDir.resolve("range.txt"), "abc".getBytes(StandardCharsets.US_ASCII));

    assertEquals(
        FileOperationException.Reason.INVALID_RANGE,
        assertThrows(
                FileOperationException.class, () -> service.read("range.txt", ReadRange.from(4L)))
            .reason());
    assertEquals(
        FileOperationException.Reason.INVALID_RANGE,
        assertThrows(
                FileOperationException.class,
                () -> service.read("range.txt", ReadRange.between(1L, 4L)))
            .reason());
  }

  @Test
  void downloadPreservesBinaryBytesAndCrlf() throws Exception {
    byte[] bytes = new byte[] {0, 1, 2, (byte) 0xff, 10, 13};
    Files.write(tempDir.resolve("binary.bin"), bytes);
    byte[] crlf = "line1\r\nline2\r\n".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("crlf.txt"), crlf);

    assertArrayEquals(bytes, service.download("binary.bin").content());
    assertArrayEquals(crlf, service.download("crlf.txt").content());
  }

  @Test
  void createRejectsExistingFileAndMkdirRejectsDuplicate() throws Exception {
    FileInfo created = service.create("new.txt", "x".getBytes(StandardCharsets.UTF_8));
    assertEquals(FileRevision.of("x".getBytes(StandardCharsets.UTF_8)), created.revision());

    assertEquals(
        FileOperationException.Reason.ALREADY_EXISTS,
        assertThrows(
                FileOperationException.class,
                () -> service.create("new.txt", "y".getBytes(StandardCharsets.UTF_8)))
            .reason());

    service.mkdir("folder");
    assertTrue(service.stat("folder").directory());
    assertEquals(
        FileOperationException.Reason.ALREADY_EXISTS,
        assertThrows(FileOperationException.class, () -> service.mkdir("folder")).reason());
  }

  @Test
  void writeRequiresCurrentRevisionForExistingFile() throws Exception {
    byte[] old = "old".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("existing.txt"), old);
    String current = FileRevision.of(old);
    RecordingSnapshotHook hook = new RecordingSnapshotHook();
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), hook);

    assertEquals(
        FileOperationException.Reason.PRECONDITION_REQUIRED,
        assertThrows(
                FileOperationException.class,
                () -> service.write("existing.txt", "new".getBytes(StandardCharsets.UTF_8), null))
            .reason());

    FileOperationException conflict =
        assertThrows(
            FileOperationException.class,
            () ->
                service.write(
                    "existing.txt", "new".getBytes(StandardCharsets.UTF_8), "sha256:wrong"));
    assertEquals(FileOperationException.Reason.REVISION_CONFLICT, conflict.reason());
    assertTrue(conflict.parameters().contains(current));
    assertArrayEquals(old, Files.readAllBytes(tempDir.resolve("existing.txt")));
    assertTrue(hook.empty());

    FileInfo written =
        service.write("existing.txt", "new".getBytes(StandardCharsets.UTF_8), current);
    assertEquals(1, hook.requests().size());
    assertEquals("before-overwrite", hook.requests().get(0).reason());
    assertEquals(
        "new",
        new String(Files.readAllBytes(tempDir.resolve("existing.txt")), StandardCharsets.UTF_8));
    assertEquals(written.revision(), FileRevision.of("new".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void writeCanCreateMissingFileWithoutExpectedRevision() throws Exception {
    FileInfo created = service.write("fresh.txt", "data".getBytes(StandardCharsets.UTF_8), null);

    assertEquals("fresh.txt", created.relativePath());
    assertArrayEquals(
        "data".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(tempDir.resolve("fresh.txt")));
    assertEquals(created.revision(), service.stat("fresh.txt").revision());
  }

  @Test
  void uploadStreamsBytesAndEnforcesLimits() throws Exception {
    FileOperationLimits limits = new FileOperationLimits(4L, 4L, 10, 10);
    service = new FileOperationsService(new SandboxResolver(tempDir), limits, null);

    FileInfo uploaded =
        service.upload("upload.bin", new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), 4L, null);
    assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(tempDir.resolve("upload.bin")));
    assertEquals(4L, uploaded.size());

    assertEquals(
        FileOperationException.Reason.TOO_LARGE,
        assertThrows(
                FileOperationException.class,
                () ->
                    service.upload(
                        "too-large.bin",
                        new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}),
                        -1L,
                        null))
            .reason());
    assertFalse(Files.exists(tempDir.resolve("too-large.bin")));
    assertEquals(0, listTempFiles().size());
  }

  @Test
  void listOmitsProtectedEntriesAndEnforcesEntryLimit() throws Exception {
    Files.write(tempDir.resolve("a.txt"), new byte[] {1});
    Files.write(tempDir.resolve("b.txt"), new byte[] {2});
    Files.createDirectories(tempDir.resolve(".deploymc"));

    List<FileInfo> entries = service.list("");
    assertEquals(2, entries.size());
    assertEquals("a.txt", entries.get(0).relativePath());
    assertEquals("b.txt", entries.get(1).relativePath());
    assertNull(entries.get(0).revision());

    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), new FileOperationLimits(10L, 10L, 1, 10), null);
    assertEquals(
        FileOperationException.Reason.TOO_LARGE,
        assertThrows(FileOperationException.class, () -> service.list("")).reason());
  }

  @Test
  void protectedPathsMapToStableErrorCode() {
    FileOperationException rejected =
        assertThrows(FileOperationException.class, () -> service.stat(".deploymc/secret.txt"));
    assertEquals(FileOperationException.Reason.PROTECTED, rejected.reason());
    assertEquals("files.protected", rejected.code());
  }

  @Test
  void traversalAttemptsMapToPathRejected() {
    FileOperationException rejected =
        assertThrows(FileOperationException.class, () -> service.stat("../outside.txt"));
    assertEquals(FileOperationException.Reason.PATH_REJECTED, rejected.reason());
    assertEquals("files.path-rejected", rejected.code());
  }

  @Test
  void deleteRequiresExplicitRecursiveConfirmation() throws Exception {
    Files.createDirectories(tempDir.resolve("tree/sub"));
    Files.write(tempDir.resolve("tree/a.txt"), new byte[] {1});
    Files.write(tempDir.resolve("tree/sub/b.txt"), new byte[] {2});

    FileOperationException unconfirmed =
        assertThrows(
            FileOperationException.class,
            () -> service.delete("tree", DeleteRequest.recursive(1L, null)));
    assertEquals(FileOperationException.Reason.DELETE_CONFIRMATION, unconfirmed.reason());
    assertTrue(unconfirmed.parameters().contains("4"));
    assertTrue(Files.exists(tempDir.resolve("tree/sub/b.txt")));

    RecordingSnapshotHook hook = new RecordingSnapshotHook();
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), hook);
    assertEquals(4, service.delete("tree", DeleteRequest.recursive(4L, null)));
    assertFalse(Files.exists(tempDir.resolve("tree")));
    assertEquals(1, hook.requests().size());
    assertEquals(4, hook.requests().get(0).entries().size());
  }

  @Test
  void deleteEnforcesEntryCapBeforeConfirmation() throws Exception {
    for (int i = 0; i < 3; i++) {
      Files.write(tempDir.resolve("many-" + i + ".txt"), new byte[] {(byte) i});
    }
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir),
            new FileOperationLimits(10L, 10L, 10, 2),
            new RecordingSnapshotHook());

    FileOperationException rejected =
        assertThrows(
            FileOperationException.class,
            () -> service.delete("", DeleteRequest.recursive(3L, null)));
    assertEquals(FileOperationException.Reason.DELETE_LIMIT, rejected.reason());
    assertTrue(Files.exists(tempDir.resolve("many-0.txt")));
  }

  @Test
  void nonRecursiveDeleteRefusesNonEmptyDirectory() throws Exception {
    Files.createDirectories(tempDir.resolve("nonempty"));
    Files.write(tempDir.resolve("nonempty/child.txt"), new byte[] {1});
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir),
            FileOperationLimits.defaults(),
            new RecordingSnapshotHook());

    assertEquals(
        FileOperationException.Reason.DIRECTORY_NOT_EMPTY,
        assertThrows(
                FileOperationException.class,
                () -> service.delete("nonempty", DeleteRequest.single(null)))
            .reason());
    assertTrue(Files.exists(tempDir.resolve("nonempty/child.txt")));
  }

  @Test
  void moveRefusesExistingDestinationUnlessOverwriteIsRequested() throws Exception {
    Files.write(tempDir.resolve("source.txt"), "source".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDir.resolve("target.txt"), "target".getBytes(StandardCharsets.UTF_8));
    RecordingSnapshotHook hook = new RecordingSnapshotHook();
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), hook);

    assertEquals(
        FileOperationException.Reason.ALREADY_EXISTS,
        assertThrows(
                FileOperationException.class,
                () -> service.move("source.txt", "target.txt", false, null))
            .reason());
    assertTrue(hook.empty());

    service.move("source.txt", "target.txt", true, null);
    assertEquals(
        "source",
        new String(Files.readAllBytes(tempDir.resolve("target.txt")), StandardCharsets.UTF_8));
    assertFalse(Files.exists(tempDir.resolve("source.txt")));
    assertEquals(1, hook.requests().size());
    assertEquals(2, hook.requests().get(0).entries().size());
  }

  @Test
  void destructiveOperationsFailClosedWithoutSnapshotHook() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("a.txt"), original);
    Files.write(tempDir.resolve("b.txt"), "target".getBytes(StandardCharsets.UTF_8));

    assertEquals(
        FileOperationException.Reason.SNAPSHOT_REQUIRED,
        assertThrows(
                FileOperationException.class,
                () ->
                    service.write(
                        "a.txt",
                        "changed".getBytes(StandardCharsets.UTF_8),
                        FileRevision.of(original)))
            .reason());
    assertEquals(
        FileOperationException.Reason.SNAPSHOT_REQUIRED,
        assertThrows(FileOperationException.class, () -> service.move("a.txt", "b.txt", true, null))
            .reason());
    assertEquals(
        FileOperationException.Reason.SNAPSHOT_REQUIRED,
        assertThrows(
                FileOperationException.class,
                () -> service.delete("a.txt", DeleteRequest.single(FileRevision.of(original))))
            .reason());
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("a.txt")));
    assertEquals(
        "target", new String(Files.readAllBytes(tempDir.resolve("b.txt")), StandardCharsets.UTF_8));
  }

  @Test
  void failedSnapshotCaptureAbortsMutation() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("a.txt"), original);
    RecordingSnapshotHook hook = new RecordingSnapshotHook();
    hook.failWith(new IOException("store unavailable"));
    service =
        new FileOperationsService(
            new SandboxResolver(tempDir), FileOperationLimits.defaults(), hook);

    assertEquals(
        FileOperationException.Reason.SNAPSHOT_FAILED,
        assertThrows(
                FileOperationException.class,
                () ->
                    service.write(
                        "a.txt",
                        "changed".getBytes(StandardCharsets.UTF_8),
                        FileRevision.of(original)))
            .reason());
    assertArrayEquals(original, Files.readAllBytes(tempDir.resolve("a.txt")));
  }

  @Test
  void statOfMissingPathReportsNotFound() {
    assertEquals(
        FileOperationException.Reason.NOT_FOUND,
        assertThrows(FileOperationException.class, () -> service.stat("missing.txt")).reason());
  }

  private List<Path> listTempFiles() throws IOException {
    List<Path> result = new java.util.ArrayList<Path>();
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
      for (Path path : stream) {
        if (path.getFileName().toString().startsWith(".deploymc-file-")) {
          result.add(path);
        }
      }
    }
    return result;
  }
}
