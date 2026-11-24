package io.github.woxlo1.deploymc.core.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.files.FileRevision;
import io.github.woxlo1.deploymc.core.files.SnapshotEntry;
import io.github.woxlo1.deploymc.core.files.SnapshotRequest;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PART 33.6 garbage-collection contract probes against the real snapshot store. */
class SnapshotGcContractTest {

  private static final Instant START = Instant.parse("2026-02-01T00:00:00Z");

  @TempDir Path tempDir;

  private Path sandboxRoot;
  private Path dataRoot;
  private Path historyDir;
  private SandboxResolver resolver;

  @BeforeEach
  void setUp() throws IOException {
    sandboxRoot = Files.createDirectories(tempDir.resolve("sandbox"));
    dataRoot = Files.createDirectories(tempDir.resolve("data"));
    historyDir = dataRoot.resolve(".deploymc").resolve("history");
    resolver = new SandboxResolver(sandboxRoot);
  }

  @Test
  void gcAbortsBeforeDeletingWhenVerificationFindsCorruption() throws Exception {
    MutableClock clock = new MutableClock(START);
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().maxVersionsPerPath(3).build();
    try (SnapshotStore store = open(config, clock)) {
      byte[] content = "reachable-bytes".getBytes(StandardCharsets.UTF_8);
      String id = capture(store, "reachable.txt", content);
      Path orphan = orphanBlob("orphan-bytes", START.minus(Duration.ofDays(2L)));
      Files.write(blobPath(hex(content)), "tampered".getBytes(StandardCharsets.UTF_8));

      GcReport report = store.gc();

      assertAborted(report, "blob.corrupt");
      assertNull(report.retention());
      assertTrue(Files.exists(orphan), "GC must not delete before verification succeeds");
      assertNotNull(store.read(id));
    }
  }

  @Test
  void gcAbortsBeforeDeletingWhenReachableBlobIsMissing() throws Exception {
    MutableClock clock = new MutableClock(START);
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().maxVersionsPerPath(3).build();
    try (SnapshotStore store = open(config, clock)) {
      byte[] content = "vanishing-bytes".getBytes(StandardCharsets.UTF_8);
      String id = capture(store, "missing.txt", content);
      Path orphan = orphanBlob("safe-orphan", START.minus(Duration.ofDays(2L)));
      Files.delete(blobPath(hex(content)));

      GcReport report = store.gc();

      assertAborted(report, "blob.missing");
      assertNull(report.retention());
      assertTrue(Files.exists(orphan), "GC must not delete after a read failure");
      assertNotNull(store.read(id));
    }
  }

  @Test
  void gcRefusesWhileRestoreOperationIsInFlight() throws Exception {
    MutableClock clock = new MutableClock(START);
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().maxVersionsPerPath(3).build();
    try (SnapshotStore store = open(config, clock)) {
      byte[] content = "original".getBytes(StandardCharsets.UTF_8);
      String id = capture(store, "active.txt", content);
      Files.write(sandboxRoot.resolve("active.txt"), content);
      Path orphan = orphanBlob("in-flight-orphan", START.minus(Duration.ofDays(2L)));
      final String[] observedCode = new String[1];
      final RuntimeException abort = new RuntimeException("restore probe abort");
      clock.onNextInstant(
          () -> {
            try {
              store.gc();
              observedCode[0] = "no-exception";
            } catch (SnapshotStoreException failure) {
              observedCode[0] = failure.code();
            }
            throw abort;
          });

      RuntimeException thrown =
          assertThrows(
              RuntimeException.class,
              () -> store.restore(id, "active.txt", FileRevision.of(content), "operator"));

      assertEquals(abort, thrown);
      assertEquals("snapshots.store-unavailable", observedCode[0]);
      assertTrue(Files.exists(orphan), "an in-flight restore must block collection");
      assertFalse(store.hasActiveDeployment(), "the operation must be released in finally");
      GcReport afterRestore = store.gc();
      assertFalse(afterRestore.aborted());
      assertEquals(1, afterRestore.blobsDeleted());
      assertFalse(Files.exists(orphan));
    }
  }

  @Test
  void maxVersionsPerPathFloorRejectsBelowThree() {
    assertThrows(
        IllegalArgumentException.class, () -> SnapshotStoreConfig.builder().maxVersionsPerPath(2));
    assertEquals(
        3, SnapshotStoreConfig.builder().maxVersionsPerPath(3).build().maxVersionsPerPath());
    assertEquals(24L, SnapshotStoreConfig.DEFAULT_GC_GRACE_HOURS);
    assertEquals(24L, SnapshotStoreConfig.defaults().gcGraceHours());
  }

  @Test
  void gcWithZeroGraceDeletesOldOrphanAndKeepsReachableContent() throws Exception {
    MutableClock clock = new MutableClock(START);
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().gcGraceHours(0L).build();
    try (SnapshotStore store = open(config, clock)) {
      byte[] content = "kept".getBytes(StandardCharsets.UTF_8);
      String id = capture(store, "kept.txt", content);
      Path orphan = orphanBlob("discarded", START.minusMillis(1L));
      clock.advance(Duration.ofMillis(1L));

      GcReport report = store.gc();

      assertFalse(report.aborted());
      assertEquals(1, report.blobsDeleted());
      assertEquals("discarded".length(), report.bytesReclaimed());
      assertFalse(Files.exists(orphan));
      assertArrayEquals(content, store.readContent(id));
    }
  }

  @Test
  void gcEnforcesThreeVersionFloorWithZeroMinAgeAndReportsExactCounts() throws Exception {
    MutableClock clock = new MutableClock(START);
    clock.stepBy(Duration.ofSeconds(1L));
    SnapshotStoreConfig config =
        SnapshotStoreConfig.builder().maxVersionsPerPath(3).minAgeDays(0L).gcGraceHours(0L).build();
    List<byte[]> contents = new ArrayList<byte[]>();
    List<String> ids = new ArrayList<String>();
    try (SnapshotStore store = open(config, clock)) {
      for (int version = 0; version < 5; version++) {
        byte[] content = ("version-" + version).getBytes(StandardCharsets.UTF_8);
        contents.add(content);
        ids.add(capture(store, "versions.txt", content));
      }

      GcReport report = store.gc();

      assertFalse(report.aborted());
      assertNotNull(report.retention());
      assertEquals(2, report.retention().pruned());
      assertEquals(5, report.blobsConsidered());
      assertEquals(2, report.blobsDeleted());
      assertEquals(contents.get(0).length + contents.get(1).length, report.bytesReclaimed());
      for (int version = 0; version < 2; version++) {
        assertFalse(Files.exists(blobPath(hex(contents.get(version)))));
      }
      for (int version = 2; version < 5; version++) {
        assertArrayEquals(contents.get(version), store.readContent(ids.get(version)));
      }
      assertFalse(store.verify().hasIssues());
    }
  }

  @Test
  void secondGcPassIsIdempotentAndPreservesReachableRecords() throws Exception {
    MutableClock clock = new MutableClock(START);
    clock.stepBy(Duration.ofSeconds(1L));
    SnapshotStoreConfig config =
        SnapshotStoreConfig.builder().maxVersionsPerPath(3).minAgeDays(0L).gcGraceHours(0L).build();
    List<byte[]> contents = new ArrayList<byte[]>();
    List<String> ids = new ArrayList<String>();
    try (SnapshotStore store = open(config, clock)) {
      for (int version = 0; version < 4; version++) {
        byte[] content = ("idempotent-" + version).getBytes(StandardCharsets.UTF_8);
        contents.add(content);
        ids.add(capture(store, "idempotent.txt", content));
      }

      GcReport first = store.gc();
      assertEquals(1, first.blobsDeleted());
      GcReport second = store.gc();

      assertFalse(second.aborted());
      assertNotNull(second.retention());
      assertEquals(0, second.retention().pruned());
      assertEquals(3, second.blobsConsidered());
      assertEquals(0, second.blobsDeleted());
      assertEquals(0L, second.bytesReclaimed());
      for (int version = 1; version < 4; version++) {
        assertArrayEquals(contents.get(version), store.readContent(ids.get(version)));
      }
      assertFalse(store.verify().hasIssues());
    }
  }

  private SnapshotStore open(SnapshotStoreConfig config, Clock clock)
      throws SnapshotStoreException {
    return SnapshotStore.open(dataRoot, resolver, config, clock);
  }

  private String capture(SnapshotStore store, String path, byte[] content)
      throws SnapshotStoreException {
    List<SnapshotEntry> entries =
        Collections.singletonList(SnapshotEntry.file(path, content, FileRevision.of(content)));
    SnapshotRequest request =
        new SnapshotRequest(resolver.root().toString(), "before-edit", entries);
    return store.findByCapture(store.capture(request)).get(0).id();
  }

  private Path orphanBlob(String text, Instant mtime) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    Path blob = blobPath(hex(bytes));
    Files.createDirectories(blob.getParent());
    Files.write(blob, bytes);
    Files.setLastModifiedTime(blob, FileTime.from(mtime));
    return blob;
  }

  private Path blobPath(String hex) {
    return historyDir.resolve("objects").resolve(hex.substring(0, 2)).resolve(hex.substring(2));
  }

  private static String hex(byte[] content) {
    return FileRevision.of(content).substring("sha256:".length());
  }

  private static void assertAborted(GcReport report, String expectedIssue) {
    assertTrue(report.aborted());
    assertNotNull(report.verify());
    assertTrue(hasIssue(report.verify(), expectedIssue));
    assertEquals(0, report.blobsConsidered());
    assertEquals(0, report.blobsDeleted());
    assertEquals(0L, report.bytesReclaimed());
  }

  private static boolean hasIssue(VerifyReport report, String code) {
    for (VerifyIssue issue : report.issues()) {
      if (code.equals(issue.code())) {
        return true;
      }
    }
    return false;
  }

  private static final class MutableClock extends Clock {

    private final ZoneId zone = ZoneId.of("UTC");
    private volatile Instant now;
    private volatile Duration step = Duration.ZERO;
    private volatile Runnable once;

    private MutableClock(Instant start) {
      this.now = start;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    private void stepBy(Duration duration) {
      step = duration;
    }

    private void onNextInstant(Runnable callback) {
      once = callback;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zoneId) {
      return this;
    }

    @Override
    public Instant instant() {
      Runnable callback = once;
      if (callback != null) {
        once = null;
        callback.run();
      }
      Instant result = now;
      now = now.plus(step);
      return result;
    }
  }
}
