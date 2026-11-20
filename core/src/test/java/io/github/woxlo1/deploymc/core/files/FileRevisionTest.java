package io.github.woxlo1.deploymc.core.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRevisionTest {

  @TempDir Path tempDir;

  @Test
  void hashesContentWithSha256Prefix() throws Exception {
    byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
    String revision = FileRevision.of(bytes);

    assertEquals(
        "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", revision);
    assertTrue(FileRevision.isValid(revision));
    assertFalse(FileRevision.isValid(revision.substring(7)));
    assertFalse(FileRevision.isValid("sha256:xyz"));
    assertNotEquals(revision, FileRevision.of("abd".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void pathHashMatchesByteHash() throws Exception {
    byte[] bytes = new byte[] {0, 1, 2, (byte) 0xff};
    Path file = tempDir.resolve("bytes.bin");
    Files.write(file, bytes);

    assertEquals(FileRevision.of(bytes), FileRevision.of(file));
  }

  @Test
  void digestsCanBeReusedByStreamingWriters() throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update("ab".getBytes(StandardCharsets.UTF_8));
    digest.update("c".getBytes(StandardCharsets.UTF_8));

    assertEquals(
        FileRevision.of("abc".getBytes(StandardCharsets.UTF_8)), FileRevision.fromDigest(digest));
  }
}
