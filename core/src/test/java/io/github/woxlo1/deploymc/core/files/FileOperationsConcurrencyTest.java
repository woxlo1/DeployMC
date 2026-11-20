package io.github.woxlo1.deploymc.core.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOperationsConcurrencyTest {

  @TempDir Path tempDir;

  @Test
  void onlyOneWriterWithTheSameExpectedRevisionWins() throws Exception {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    Files.write(tempDir.resolve("race.txt"), original);
    String expected = FileRevision.of(original);
    FileOperationsService service =
        new FileOperationsService(
            new SandboxResolver(tempDir),
            FileOperationLimits.defaults(),
            new RecordingSnapshotHook());
    byte[] first = "writer-one".getBytes(StandardCharsets.UTF_8);
    byte[] second = "writer-two".getBytes(StandardCharsets.UTF_8);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    try {
      Future<FileInfo> one = pool.submit(write(service, first, expected, ready, start));
      Future<FileInfo> two = pool.submit(write(service, second, expected, ready, start));
      ready.await();
      start.countDown();

      int successes = 0;
      int conflicts = 0;
      for (Future<FileInfo> future : Arrays.asList(one, two)) {
        try {
          future.get();
          successes++;
        } catch (ExecutionException failure) {
          assertTrue(failure.getCause() instanceof FileOperationException);
          FileOperationException rejection = (FileOperationException) failure.getCause();
          // Atomic replacement can change the file key before the stale writer rechecks.
          assertTrue(
              rejection.reason() == FileOperationException.Reason.REVISION_CONFLICT
                  || rejection.reason() == FileOperationException.Reason.PATH_REJECTED,
              "Unexpected conflict reason: " + rejection.reason());
          conflicts++;
        }
      }
      assertEquals(1, successes);
      assertEquals(1, conflicts);
      byte[] finalBytes = Files.readAllBytes(tempDir.resolve("race.txt"));
      assertTrue(Arrays.equals(first, finalBytes) || Arrays.equals(second, finalBytes));
      assertArrayEquals(finalBytes, service.download("race.txt").content());
    } finally {
      pool.shutdownNow();
    }
  }

  private static Callable<FileInfo> write(
      FileOperationsService service,
      byte[] content,
      String expected,
      CountDownLatch ready,
      CountDownLatch start) {
    return () -> {
      ready.countDown();
      start.await();
      return service.write("race.txt", content, expected);
    };
  }
}
