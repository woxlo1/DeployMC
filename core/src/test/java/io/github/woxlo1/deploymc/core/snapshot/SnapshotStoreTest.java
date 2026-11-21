package io.github.woxlo1.deploymc.core.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.files.FileOperationLimits;
import io.github.woxlo1.deploymc.core.files.FileOperationsService;
import io.github.woxlo1.deploymc.core.files.FileRevision;
import io.github.woxlo1.deploymc.core.files.SnapshotEntry;
import io.github.woxlo1.deploymc.core.files.SnapshotRequest;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {

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
  void captureWritesFormatOneCanonicalLayoutAndDeduplicatesBlobs() throws Exception {
    byte[] content = "shared".getBytes(StandardCharsets.UTF_8);
    List<SnapshotEntry> entries = new ArrayList<SnapshotEntry>();
    entries.add(SnapshotEntry.file("a.txt", content, FileRevision.of(content)));
    entries.add(SnapshotEntry.file("b.txt", content, FileRevision.of(content)));
    try (SnapshotStore store = openStore()) {
      String capture =
          store.capture(
              new SnapshotRequest(resolver.root().toString(), "before-overwrite", entries));
      List<SnapshotRecord> records = store.findByCapture(capture);

      assertEquals(
          "1\n",
          new String(
              Files.readAllBytes(historyDir.resolve("format-version")), StandardCharsets.US_ASCII));
      assertEquals(2, records.size());
      assertEquals(capture, records.get(0).capture());
      assertEquals(capture, records.get(1).capture());
      assertEquals("file", records.get(0).kind().value());
      assertEquals(FileRevision.of(content), records.get(0).hash());
      assertEquals(content.length, records.get(0).size());
      assertEquals("system", records.get(0).actor());
      assertEquals("edit", records.get(0).reason().value());
      assertEquals(1, countRegularFiles(historyDir.resolve("objects")));
      assertEquals(2, countJson(historyDir.resolve("index").resolve("snapshots")));
      assertEquals(2, countJson(historyDir.resolve("index").resolve("files")));
      assertFalse(store.verify().hasIssues());
    }
  }

  @Test
  void captureStoresRelativePathAndLinksOnePathInCaptureOrder() throws Exception {
    try (SnapshotStore store = openStore()) {
      store.capture(
          request("linked.txt", "one".getBytes(StandardCharsets.UTF_8), "before-overwrite"));
      store.capture(
          request("linked.txt", "two".getBytes(StandardCharsets.UTF_8), "before-overwrite"));
      List<SnapshotRecord> records = store.findByPath("linked.txt");

      assertEquals(2, records.size());
      assertEquals(null, records.get(0).parent());
      assertEquals(records.get(0).id(), records.get(1).parent());
      assertTrue(records.get(1).createdAt().isAfter(records.get(0).createdAt()));
      assertFalse(records.get(0).toString().contains(sandboxRoot.toString()));
    }
  }

  @Test
  void captureRejectsRevisionMismatchBeforeAnyMetadataWrite() throws Exception {
    try (SnapshotStore store = openStore()) {
      byte[] content = "content".getBytes(StandardCharsets.UTF_8);
      List<SnapshotEntry> entries =
          Collections.singletonList(SnapshotEntry.file("a.txt", content, "sha256:wrong"));

      SnapshotStoreException failure =
          assertThrows(
              SnapshotStoreException.class,
              () ->
                  store.capture(
                      new SnapshotRequest(
                          resolver.root().toString(), "before-overwrite", entries)));

      assertEquals("snapshots.corrupt", failure.code());
      assertEquals(0, countJson(historyDir.resolve("index").resolve("snapshots")));
    }
  }

  @Test
  void captureRejectsPathsOutsideTheEditableSandbox() throws Exception {
    String[] rejected = {
      ".deploymc/history/format-version",
      "../escape.txt",
      "a\\b.txt",
      "/etc/passwd",
      "C:/secret.txt",
      "%2e%2e/secret.txt",
      "folder/./file.txt"
    };
    try (SnapshotStore store = openStore()) {
      for (String path : rejected) {
        SnapshotStoreException failure =
            assertThrows(
                SnapshotStoreException.class,
                () ->
                    store.capture(
                        request(path, "x".getBytes(StandardCharsets.UTF_8), "before-edit")),
                path);
        assertTrue(
            failure.code().equals("files.path-rejected")
                || failure.code().equals("snapshots.corrupt"),
            path + " produced " + failure.code());
      }
      assertEquals(0, countJson(historyDir.resolve("index").resolve("snapshots")));
    }
  }

  @Test
  void captureRejectsARequestFromAnotherSandboxRoot() throws Exception {
    Path otherRoot = Files.createDirectories(tempDir.resolve("other-sandbox"));
    SandboxResolver otherResolver = new SandboxResolver(otherRoot);
    try (SnapshotStore store = openStore()) {
      List<SnapshotEntry> entries =
          Collections.singletonList(
              SnapshotEntry.file("a.txt", "x".getBytes(StandardCharsets.UTF_8), null));
      SnapshotStoreException failure =
          assertThrows(
              SnapshotStoreException.class,
              () ->
                  store.capture(
                      new SnapshotRequest(
                          otherResolver.root().toString(), "before-edit", entries)));
      assertEquals("snapshots.store-unavailable", failure.code());
    }
  }

  @Test
  void openRejectsHigherOrUnparseableFormatVersions() throws Exception {
    Files.createDirectories(historyDir);
    Files.write(historyDir.resolve("format-version"), "2\n".getBytes(StandardCharsets.US_ASCII));
    SnapshotStoreException higher = assertThrows(SnapshotStoreException.class, () -> openStore());
    assertEquals("snapshots.store-unavailable", higher.code());

    Files.write(historyDir.resolve("format-version"), "broken".getBytes(StandardCharsets.US_ASCII));
    SnapshotStoreException broken = assertThrows(SnapshotStoreException.class, () -> openStore());
    assertEquals("snapshots.store-unavailable", broken.code());
  }

  @Test
  void openRejectsMissingFormatVersionInNonEmptyHistory() throws Exception {
    Path stray = historyDir.resolve("objects").resolve("aa").resolve("bb");
    Files.createDirectories(stray.getParent());
    Files.write(stray, new byte[] {1});

    SnapshotStoreException failure = assertThrows(SnapshotStoreException.class, () -> openStore());
    assertEquals("snapshots.corrupt", failure.code());
  }

  @Test
  void secondOpenFailsWhileTheFirstInstanceHoldsTheDataLock() throws Exception {
    try (SnapshotStore first = openStore()) {
      assertNotNull(first);
      SnapshotStoreException failure =
          assertThrows(SnapshotStoreException.class, () -> openStore());
      assertEquals("snapshots.store-unavailable", failure.code());
    }
    try (SnapshotStore second = openStore()) {
      assertTrue(second.verify().issues().isEmpty());
    }
  }

  @Test
  void restoreRoundTripsFileContentAndCapturesThePreRestoreState() throws Exception {
    Path target = sandboxRoot.resolve("settings.yml");
    byte[] original = "old: true\n".getBytes(StandardCharsets.UTF_8);
    byte[] changed = "old: false\n".getBytes(StandardCharsets.UTF_8);
    Files.write(target, original);

    try (SnapshotStore store = openStore()) {
      String id = store.capture(request("settings.yml", original, "before-overwrite"));
      String snapshotId = store.findByCapture(id).get(0).id();
      Files.write(target, changed);

      RestoreResult result =
          store.restore(snapshotId, "settings.yml", FileRevision.of(changed), "operator");

      assertArrayEquals(original, Files.readAllBytes(target));
      assertEquals(snapshotId, result.restoredSnapshotId());
      SnapshotRecord pre = store.read(result.preRestoreSnapshotId());
      assertEquals("restore", pre.reason().value());
      assertEquals(FileRevision.of(changed), pre.hash());
      assertEquals("operator", pre.actor());
      assertEquals("settings.yml", pre.path());
      assertFalse(store.verify().hasIssues());
    }
  }

  @Test
  void restoreDeletionMarkerDeletesTargetAndKeepsItsContent() throws Exception {
    Path target = sandboxRoot.resolve("gone.txt");
    byte[] current = "present".getBytes(StandardCharsets.UTF_8);
    try (SnapshotStore store = openStore()) {
      List<SnapshotEntry> entries = Collections.singletonList(SnapshotEntry.deleted("gone.txt"));
      String capture =
          store.capture(new SnapshotRequest(resolver.root().toString(), "before-delete", entries));
      String deletionId = store.findByCapture(capture).get(0).id();
      SnapshotRecord deletion = store.read(deletionId);
      assertEquals("deleted", deletion.kind().value());
      assertEquals(null, deletion.hash());
      assertEquals(0L, deletion.size());

      Files.write(target, current);
      RestoreResult result =
          store.restore(deletionId, "gone.txt", FileRevision.of(current), "operator");

      assertFalse(Files.exists(target));
      assertEquals(FileRevision.of(current), store.read(result.preRestoreSnapshotId()).hash());
      assertFalse(store.verify().hasIssues());
    }
  }

  @Test
  void restoreRefusesAChangedTargetWithoutMutatingFiles() throws Exception {
    Path target = sandboxRoot.resolve("locked.txt");
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    byte[] changed = "changed".getBytes(StandardCharsets.UTF_8);
    Files.write(target, original);
    try (SnapshotStore store = openStore()) {
      String capture = store.capture(request("locked.txt", original, "before-overwrite"));
      String id = store.findByCapture(capture).get(0).id();
      Files.write(target, changed);

      SnapshotStoreException failure =
          assertThrows(
              SnapshotStoreException.class,
              () -> store.restore(id, "locked.txt", FileRevision.of(original), "operator"));

      assertEquals("snapshots.restore-conflict", failure.code());
      assertArrayEquals(changed, Files.readAllBytes(target));
    }
  }

  @Test
  void restoreRejectsProtectedTargetPaths() throws Exception {
    try (SnapshotStore store = openStore()) {
      String capture =
          store.capture(request("safe.txt", "x".getBytes(StandardCharsets.UTF_8), "before-edit"));
      String id = store.findByCapture(capture).get(0).id();
      SnapshotStoreException failure =
          assertThrows(
              SnapshotStoreException.class,
              () -> store.restore(id, ".deploymc/config.yml", null, "operator"));
      assertEquals("files.path-rejected", failure.code());
    }
  }

  @Test
  void missingSnapshotUsesStableNotFoundCode() throws Exception {
    try (SnapshotStore store = openStore()) {
      SnapshotStoreException failure =
          assertThrows(
              SnapshotStoreException.class, () -> store.read("snap_00000000000000000000000000"));
      assertEquals("snapshots.not-found", failure.code());
      assertTrue(failure.localizedMessage(Locale.JAPANESE).contains("スナップショット"));
    }
  }

  @Test
  void diagnosticsUseLocalizedDetailCatalogs() throws Exception {
    try (SnapshotStore store = openStore()) {
      List<SnapshotEntry> entries =
          Collections.singletonList(
              SnapshotEntry.file("a.txt", "x".getBytes(StandardCharsets.UTF_8), null));
      SnapshotStoreException failure =
          assertThrows(
              SnapshotStoreException.class,
              () ->
                  store.capture(
                      new SnapshotRequest(resolver.root().toString(), "unknown-reason", entries)));

      assertEquals("snapshots.corrupt", failure.code());
      assertTrue(failure.getMessage().contains("Unsupported snapshot reason"));
      assertTrue(failure.localizedMessage(Locale.JAPANESE).contains("サポートされていない"));
    }
  }

  @Test
  void verifyReportsInvalidBlobNamesWithoutThrowing() throws Exception {
    try (SnapshotStore store = openStore()) {
      Path invalid = historyDir.resolve("objects").resolve("zz").resolve("not-a-blob");
      Files.createDirectories(invalid.getParent());
      Files.write(invalid, new byte[] {1});

      VerifyReport report = store.verify();

      assertTrue(hasCode(report, "blob.corrupt"));
      assertFalse(hasCode(report, "blob.orphan"));
    }
  }

  @Test
  void startupRemovesObjectBucketStagingFiles() throws Exception {
    Path staging;
    try (SnapshotStore store = openStore()) {
      assertNotNull(store);
      Path bucket = Files.createDirectories(historyDir.resolve("objects").resolve("aa"));
      staging = Files.createTempFile(bucket, ".deploymc-object-", ".tmp");
    }

    try (SnapshotStore store = openStore()) {
      assertFalse(Files.exists(staging));
      assertFalse(hasCode(store.verify(), "staging.unreferenced"));
    }
  }

  @Test
  void verifyDetectsBlobCorruptionAndRepairDoesNotDeleteIt() throws Exception {
    try (SnapshotStore store = openStore()) {
      String capture =
          store.capture(
              request("a.txt", "payload".getBytes(StandardCharsets.UTF_8), "before-edit"));
      SnapshotRecord record = store.findByCapture(capture).get(0);
      Path blob = blobPath(record.hashHex());
      byte[] damaged = "damaged".getBytes(StandardCharsets.UTF_8);
      Files.write(blob, damaged);

      VerifyReport corrupt = store.verify();
      assertTrue(hasCode(corrupt, "blob.corrupt"));

      RepairReport repair = store.repair();
      assertTrue(Files.exists(blob));
      assertTrue(hasCode(repair.unresolved(), "blob.corrupt"));
      assertTrue(repair.actions().isEmpty());

      Files.write(blob, "payload".getBytes(StandardCharsets.UTF_8));
      assertFalse(store.verify().hasIssues());
    }
  }

  @Test
  void verifyDetectsIndexDriftAndRepairRebuildsTheIndex() throws Exception {
    try (SnapshotStore store = openStore()) {
      String capture =
          store.capture(request("drift.txt", "x".getBytes(StandardCharsets.UTF_8), "before-edit"));
      SnapshotRecord record = store.findByCapture(capture).get(0);
      Path index = historyDir.resolve("index").resolve("files");
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(index, "*.json")) {
        for (Path file : entries) {
          Files.delete(file);
        }
      }

      VerifyReport missing = store.verify();
      assertTrue(hasCode(missing, "index.missing"));
      assertNotNull(store.read(record.id()));

      RepairReport repair = store.repair();
      assertTrue(hasAction(repair, "index.rebuilt"));
      assertFalse(store.verify().hasIssues());
    }
  }

  @Test
  void repairRemovesUnreferencedStagingFiles() throws Exception {
    try (SnapshotStore store = openStore()) {
      assertNotNull(store);
    }
    Path staging =
        Files.createFile(
            Files.createDirectories(historyDir.resolve("tmp")).resolve("abandoned.tmp"));
    try (SnapshotStore store = openStore()) {
      assertFalse(Files.exists(staging));
      assertFalse(hasCode(store.verify(), "staging.unreferenced"));
      assertTrue(store.repair().actions().isEmpty());
    }
  }

  @Test
  void retentionRemovesOldestVersionsBeyondTheConfiguredCount() throws Exception {
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().maxVersionsPerPath(3).build();
    try (SnapshotStore store = openStore(config, Clock.systemUTC())) {
      List<String> ids = new ArrayList<String>();
      for (int i = 0; i < 5; i++) {
        String capture =
            store.capture(
                request(
                    "versions.txt",
                    ("value-" + i).getBytes(StandardCharsets.UTF_8),
                    "before-edit"));
        ids.add(store.findByCapture(capture).get(0).id());
      }

      RetentionReport report = store.retain();

      assertEquals(
          2,
          report.pruned(),
          "considered="
              + report.considered()
              + " protected="
              + report.protectedRecords()
              + " failed="
              + report.failed());
      assertEquals(3, report.considered() - report.pruned());
      assertEquals(3, report.protectedRecords());
      assertThrows(SnapshotStoreException.class, () -> store.read(ids.get(0)));
      assertThrows(SnapshotStoreException.class, () -> store.read(ids.get(1)));
      assertNotNull(store.read(ids.get(2)));
      assertNotNull(store.read(ids.get(3)));
      assertNotNull(store.read(ids.get(4)));
      VerifyReport afterPrune = store.verify();
      assertTrue(hasCode(afterPrune, "blob.orphan"));
      for (VerifyIssue issue : afterPrune.issues()) {
        assertEquals("blob.orphan", issue.code());
      }
    }
  }

  @Test
  void retentionProtectsRecordsReferencedByAnActiveDeployment() throws Exception {
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().maxVersionsPerPath(3).build();
    try (SnapshotStore store = openStore(config, Clock.systemUTC())) {
      String deployment = "dep_active";
      store.registerDeployment(deployment);
      List<SnapshotEntry> entries =
          Collections.singletonList(
              SnapshotEntry.file("deployed.txt", "old".getBytes(StandardCharsets.UTF_8), null));
      String firstCapture =
          store.capture(
              new SnapshotRequest(
                  resolver.root().toString(),
                  "deploy-validate",
                  entries,
                  "system",
                  deployment,
                  null));
      String protectedId = store.findByCapture(firstCapture).get(0).id();
      for (int i = 1; i < 4; i++) {
        store.capture(
            request("deployed.txt", ("new-" + i).getBytes(StandardCharsets.UTF_8), "before-edit"));
      }

      RetentionReport report = store.retain();

      assertEquals(0, report.pruned());
      assertNotNull(store.read(protectedId));
      store.completeDeployment(deployment);
      assertEquals(1, store.retain().pruned());
    }
  }

  @Test
  void gcHonorsGracePeriodBeforeDeletingUnreachableBlobs() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    SnapshotStoreConfig config = SnapshotStoreConfig.builder().maxVersionsPerPath(3).build();
    try (SnapshotStore store = openStore(config, clock)) {
      List<String> hashes = new ArrayList<String>();
      for (int i = 0; i < 4; i++) {
        String capture =
            store.capture(
                request("gc.txt", ("blob-" + i).getBytes(StandardCharsets.UTF_8), "before-edit"));
        hashes.add(store.findByCapture(capture).get(0).hashHex());
      }
      assertEquals(1, store.retain().pruned());
      Path unreachable = blobPath(hashes.get(0));
      assertTrue(Files.exists(unreachable));

      clock.advance(Duration.ofHours(2L));
      GcReport withinGrace = store.gc();
      assertFalse(withinGrace.aborted());
      assertTrue(Files.exists(unreachable));
      assertEquals(0, withinGrace.blobsDeleted());

      clock.advance(Duration.ofHours(23L));
      GcReport afterGrace = store.gc();
      assertFalse(afterGrace.aborted());
      assertTrue(afterGrace.blobsDeleted() >= 1);
      assertFalse(Files.exists(unreachable));
    }
  }

  @Test
  void gcRefusesToRunDuringAnActiveDeployment() throws Exception {
    try (SnapshotStore store = openStore()) {
      store.registerDeployment("dep_running");
      SnapshotStoreException failure = assertThrows(SnapshotStoreException.class, store::gc);
      assertEquals("snapshots.store-unavailable", failure.code());
    }
  }

  @Test
  void concurrentCapturesAreSerializedAndProduceUniqueSnapshotIds() throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(4);
    try (SnapshotStore store = openStore()) {
      List<Future<String>> futures = new ArrayList<Future<String>>();
      for (int i = 0; i < 4; i++) {
        String path = "parallel-" + i + ".txt";
        byte[] content = ("value-" + i).getBytes(StandardCharsets.UTF_8);
        futures.add(
            workers.submit(
                () -> {
                  String capture = store.capture(request(path, content, "before-edit"));
                  return store.findByCapture(capture).get(0).id();
                }));
      }
      Set<String> ids = new HashSet<String>();
      for (Future<String> future : futures) {
        ids.add(future.get(10L, TimeUnit.SECONDS));
      }
      assertEquals(4, ids.size());
      assertFalse(store.verify().hasIssues());
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void fileOperationsServiceUsesTheStoreAsItsSnapshotHook() throws Exception {
    byte[] original = "before".getBytes(StandardCharsets.UTF_8);
    Path target = sandboxRoot.resolve("hooked.txt");
    Files.write(target, original);
    try (SnapshotStore store = openStore()) {
      FileOperationsService files =
          new FileOperationsService(resolver, FileOperationLimits.defaults(), store);
      files.write(
          "hooked.txt", "after".getBytes(StandardCharsets.UTF_8), FileRevision.of(original));

      List<SnapshotRecord> records = store.findByPath("hooked.txt");
      assertEquals(1, records.size());
      assertEquals("edit", records.get(0).reason().value());
      assertEquals(FileRevision.of(original), records.get(0).hash());
    }
  }

  private SnapshotStore openStore() throws SnapshotStoreException {
    return openStore(SnapshotStoreConfig.defaults(), Clock.systemUTC());
  }

  private SnapshotStore openStore(SnapshotStoreConfig config, Clock clock)
      throws SnapshotStoreException {
    return SnapshotStore.open(dataRoot, resolver, config, clock);
  }

  private SnapshotRequest request(String path, byte[] content, String reason) {
    List<SnapshotEntry> entries =
        Collections.singletonList(SnapshotEntry.file(path, content, FileRevision.of(content)));
    return new SnapshotRequest(resolver.root().toString(), reason, entries);
  }

  private Path blobPath(String hex) {
    return historyDir.resolve("objects").resolve(hex.substring(0, 2)).resolve(hex.substring(2));
  }

  private int countJson(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return 0;
    }
    int count = 0;
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
      for (Path ignored : entries) {
        count++;
      }
    }
    return count;
  }

  private int countRegularFiles(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return 0;
    }
    int count = 0;
    try (DirectoryStream<Path> buckets = Files.newDirectoryStream(directory)) {
      for (Path bucket : buckets) {
        if (Files.isDirectory(bucket)) {
          count += countRegularFiles(bucket);
        } else if (Files.isRegularFile(bucket)) {
          count++;
        }
      }
    }
    return count;
  }

  private static boolean hasCode(VerifyReport report, String code) {
    return hasCode(report.issues(), code);
  }

  private static boolean hasCode(List<VerifyIssue> issues, String code) {
    for (VerifyIssue issue : issues) {
      if (code.equals(issue.code())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAction(RepairReport report, String code) {
    for (RepairAction action : report.actions()) {
      if (code.equals(action.code())) {
        return true;
      }
    }
    return false;
  }

  private static final class MutableClock extends Clock {

    private final ZoneId zone = ZoneId.of("UTC");
    private volatile Instant now;

    private MutableClock(Instant start) {
      this.now = start;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
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
      return now;
    }
  }
}
