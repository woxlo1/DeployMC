package io.github.woxlo1.deploymc.core.snapshot;

import io.github.woxlo1.deploymc.core.files.FileRevision;
import io.github.woxlo1.deploymc.core.files.SnapshotEntry;
import io.github.woxlo1.deploymc.core.files.SnapshotHook;
import io.github.woxlo1.deploymc.core.files.SnapshotRequest;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxPath;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotJson.IndexData;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Host-controlled, content-addressed snapshot store for {@link SnapshotHook}.
 *
 * <p>The store never accepts a raw host path from a request. Entry paths are sandbox-relative and
 * are validated through the owning {@link SandboxResolver} before any store write. The whole data
 * directory is protected by an OS-level lock for the lifetime of an open store.
 */
public final class SnapshotStore implements SnapshotHook, AutoCloseable {

  private static final String FORMAT_VERSION = "1\n";
  private static final String ID_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
  private static final String BLOB_STAGING_PREFIX = ".deploymc-object-";
  private static final String STAGING_SUFFIX = ".tmp";
  private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
  private static final int BUFFER_SIZE = 8192;

  private final Path historyDir;
  private final Path objectsDir;
  private final Path formatsFile;
  private final Path snapshotsDir;
  private final Path filesIndexDir;
  private final Path tmpDir;
  private final Path lockPath;
  private final SandboxResolver resolver;
  private final SnapshotStoreConfig config;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final ReentrantLock storeLock = new ReentrantLock(true);
  private final Set<String> activeDeployments = new HashSet<String>();
  private final Set<String> activeOperations = new HashSet<String>();

  private FileChannel lockChannel;
  private FileLock fileLock;
  private volatile boolean open;
  private Instant lastCreatedAt = Instant.EPOCH;

  private SnapshotStore(
      Path dataRoot, SandboxResolver resolver, SnapshotStoreConfig config, Clock clock) {
    Path deploymcDir = dataRoot.resolve(".deploymc");
    this.historyDir = deploymcDir.resolve("history");
    this.objectsDir = historyDir.resolve("objects");
    this.formatsFile = historyDir.resolve("format-version");
    this.snapshotsDir = historyDir.resolve("index").resolve("snapshots");
    this.filesIndexDir = historyDir.resolve("index").resolve("files");
    this.tmpDir = historyDir.resolve("tmp");
    this.lockPath = deploymcDir.resolve("data.lock");
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Opens the store with host defaults. The caller owns and closes the returned instance. */
  public static SnapshotStore open(Path dataRoot, SandboxResolver resolver)
      throws SnapshotStoreException {
    return open(dataRoot, resolver, SnapshotStoreConfig.defaults(), Clock.systemUTC());
  }

  public static SnapshotStore open(
      Path dataRoot, SandboxResolver resolver, SnapshotStoreConfig config)
      throws SnapshotStoreException {
    return open(dataRoot, resolver, config, Clock.systemUTC());
  }

  static SnapshotStore open(
      Path dataRoot, SandboxResolver resolver, SnapshotStoreConfig config, Clock clock)
      throws SnapshotStoreException {
    Objects.requireNonNull(dataRoot, "dataRoot");
    SnapshotStore store = new SnapshotStore(dataRoot, resolver, config, clock);
    store.initialize();
    return store;
  }

  /** Host-controlled deployment registration. A terminal deployment must be completed. */
  public void registerDeployment(String deploymentId) throws SnapshotStoreException {
    Objects.requireNonNull(deploymentId, "deploymentId");
    locked(
        () -> {
          if (deploymentId.isEmpty()) {
            throw unavailable(SnapshotStoreDetail.DEPLOYMENT_ID_EMPTY);
          }
          activeDeployments.add(deploymentId);
          return null;
        });
  }

  public void completeDeployment(String deploymentId) throws SnapshotStoreException {
    Objects.requireNonNull(deploymentId, "deploymentId");
    locked(
        () -> {
          activeDeployments.remove(deploymentId);
          return null;
        });
  }

  public boolean hasActiveDeployment() throws SnapshotStoreException {
    return locked(() -> !activeDeployments.isEmpty() || !activeOperations.isEmpty());
  }

  /** Captures one request and returns its shared capture id. */
  @Override
  public String capture(SnapshotRequest request) throws SnapshotStoreException {
    return captureDetailed(request).captureId();
  }

  /** Captures one request and exposes the individual immutable snapshot ids. */
  public CaptureResult captureDetailed(SnapshotRequest request) throws SnapshotStoreException {
    Objects.requireNonNull(request, "request");
    return locked(() -> captureLocked(request));
  }

  public List<SnapshotRecord> findByCapture(String captureId) throws SnapshotStoreException {
    Objects.requireNonNull(captureId, "captureId");
    return locked(
        () -> {
          List<SnapshotRecord> result = new ArrayList<SnapshotRecord>();
          for (SnapshotRecord record : readAllRecords()) {
            if (captureId.equals(record.capture())) {
              result.add(record);
            }
          }
          Collections.sort(result, CREATED_ORDER);
          return result;
        });
  }

  public SnapshotRecord read(String snapshotId) throws SnapshotStoreException {
    return locked(() -> readRecord(snapshotId));
  }

  public List<SnapshotRecord> findByPath(String relativePath) throws SnapshotStoreException {
    String normalized = normalizeEntryPath(relativePath, SandboxAccessMode.READ);
    return locked(
        () -> {
          List<SnapshotRecord> result = new ArrayList<SnapshotRecord>();
          for (SnapshotRecord record : readAllRecords()) {
            if (normalized.equals(record.path())) {
              result.add(record);
            }
          }
          return result;
        });
  }

  /** Restores one snapshot and records the current target before mutation. */
  public RestoreResult restore(
      String snapshotId, String targetPath, String expectedRevision, String actor)
      throws SnapshotStoreException {
    Objects.requireNonNull(targetPath, "targetPath");
    if (actor == null || actor.isEmpty()) {
      throw unavailable(SnapshotStoreDetail.RESTORE_ACTOR_EMPTY);
    }
    return locked(() -> restoreLocked(snapshotId, targetPath, expectedRevision, actor));
  }

  public RetentionReport retain() throws SnapshotStoreException {
    return locked(this::retainLocked);
  }

  public RetentionReport prunePath(String relativePath) throws SnapshotStoreException {
    String normalized = normalizeEntryPath(relativePath, SandboxAccessMode.READ);
    return locked(() -> pruneLocked(record -> normalized.equals(record.path())));
  }

  public RetentionReport pruneSnapshot(String snapshotId) throws SnapshotStoreException {
    if (!SnapshotJson.SNAPSHOT_ID.matcher(snapshotId).matches()) {
      throw notFound(snapshotId);
    }
    return locked(() -> pruneLocked(record -> snapshotId.equals(record.id())));
  }

  public GcReport gc() throws SnapshotStoreException {
    return locked(this::gcLocked);
  }

  public VerifyReport verify() throws SnapshotStoreException {
    return locked(this::verifyLocked);
  }

  public RepairReport repair() throws SnapshotStoreException {
    return locked(this::repairLocked);
  }

  public long storeSizeBytes() throws SnapshotStoreException {
    return locked(this::storeBytes);
  }

  @Override
  public void close() throws IOException {
    synchronized (this) {
      if (!open && fileLock == null && lockChannel == null) {
        return;
      }
      open = false;
      if (fileLock != null) {
        fileLock.release();
        fileLock = null;
      }
      if (lockChannel != null) {
        lockChannel.close();
        lockChannel = null;
      }
    }
  }

  private void initialize() throws SnapshotStoreException {
    try {
      if (!exists(dataRoot())) {
        Files.createDirectories(dataRoot());
      }
      if (!Files.isDirectory(dataRoot(), LinkOption.NOFOLLOW_LINKS)) {
        throw unavailable(SnapshotStoreDetail.DATA_ROOT_NOT_DIRECTORY);
      }
      Files.createDirectories(lockPath.getParent());
      lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      try {
        fileLock = lockChannel.tryLock();
      } catch (OverlappingFileLockException overlap) {
        fileLock = null;
      }
      if (fileLock == null || !fileLock.isValid()) {
        throw unavailable(SnapshotStoreDetail.DATA_DIRECTORY_LOCKED);
      }
      Files.createDirectories(historyDir);
      Files.createDirectories(objectsDir);
      Files.createDirectories(snapshotsDir);
      Files.createDirectories(filesIndexDir);
      Files.createDirectories(tmpDir);
      validateOrCreateFormat();
      cleanupStaging();
      open = true;
    } catch (SnapshotStoreException failure) {
      releaseAfterFailure();
      throw failure;
    } catch (IOException failure) {
      releaseAfterFailure();
      throw io(failure);
    }
  }

  private Path dataRoot() {
    return lockPath.getParent().getParent();
  }

  private void releaseAfterFailure() {
    try {
      close();
    } catch (IOException ignored) {
      // Preserve the original startup failure.
    }
  }

  private void validateOrCreateFormat() throws SnapshotStoreException {
    if (!exists(formatsFile)) {
      if (containsNonDirectory(historyDir)) {
        throw corrupt(SnapshotStoreDetail.FORMAT_MISSING_NONEMPTY);
      }
      atomicWrite(formatsFile, FORMAT_VERSION.getBytes(StandardCharsets.US_ASCII));
      return;
    }
    String version = readUtf8(formatsFile);
    if (!FORMAT_VERSION.equals(version)) {
      throw unavailable(SnapshotStoreDetail.FORMAT_UNSUPPORTED);
    }
  }

  private boolean containsNonDirectory(Path directory) throws SnapshotStoreException {
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        BasicFileAttributes attributes = attributes(entry);
        if (attributes == null || !attributes.isDirectory() || containsNonDirectory(entry)) {
          return true;
        }
      }
      return false;
    } catch (IOException failure) {
      throw io(failure);
    }
  }

  private void cleanupStaging() throws SnapshotStoreException {
    for (Path staging : listStagingFiles()) {
      try {
        Files.delete(staging);
      } catch (IOException failure) {
        throw unavailable(SnapshotStoreDetail.STAGING_REMOVE_FAILED);
      }
    }
  }

  private <T> T locked(LockedOperation<T> operation) throws SnapshotStoreException {
    ensureOpen();
    boolean acquired = false;
    try {
      acquired = storeLock.tryLock(config.lockTimeoutMillis(), TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw unavailable(SnapshotStoreDetail.STORE_BUSY);
      }
      ensureOpen();
      return operation.run();
    } catch (InterruptedException interruption) {
      Thread.currentThread().interrupt();
      throw unavailable(SnapshotStoreDetail.STORE_LOCK_INTERRUPTED);
    } finally {
      if (acquired) {
        storeLock.unlock();
      }
    }
  }

  private void ensureOpen() throws SnapshotStoreException {
    if (!open) {
      throw unavailable(SnapshotStoreDetail.STORE_CLOSED);
    }
  }

  private void requireFormat() throws SnapshotStoreException {
    String version = readUtf8(formatsFile);
    if (!FORMAT_VERSION.equals(version)) {
      throw unavailable(SnapshotStoreDetail.FORMAT_UNSUPPORTED);
    }
  }

  private CaptureResult captureLocked(SnapshotRequest request) throws SnapshotStoreException {
    requireFormat();
    validateRoot(request.rootPath());
    SnapshotReason reason = SnapshotReason.fromHookReason(request.reason());
    if (reason == null) {
      throw corrupt(SnapshotStoreDetail.SNAPSHOT_REASON_UNSUPPORTED);
    }
    if (request.entries().isEmpty()) {
      throw corrupt(SnapshotStoreDetail.CAPTURE_NO_ENTRIES);
    }

    Map<String, List<String>> indexes = new LinkedHashMap<String, List<String>>();
    Set<String> seenPaths = new HashSet<String>();
    List<PreparedEntry> prepared = new ArrayList<PreparedEntry>();
    for (SnapshotEntry entry : request.entries()) {
      String path = normalizeEntryPath(entry.relativePath(), SandboxAccessMode.READ);
      if (!seenPaths.add(path)) {
        throw corrupt(SnapshotStoreDetail.CAPTURE_DUPLICATE_PATH, path);
      }
      SnapshotKind kind = kindOf(path, entry);
      byte[] content = kind.hasContent() ? entry.content() : new byte[0];
      String revision = kind.hasContent() ? FileRevision.of(content) : null;
      if (kind.hasContent() && entry.revision() != null && !entry.revision().equals(revision)) {
        throw corrupt(SnapshotStoreDetail.REVISION_MISMATCH, path);
      }
      if (!indexes.containsKey(path)) {
        indexes.put(path, validatedIndexIds(path));
      }
      List<String> ids = indexes.get(path);
      String parent = ids.isEmpty() ? null : ids.get(ids.size() - 1);
      prepared.add(
          new PreparedEntry(path, kind, content, revision, parent, request.pluginMetadata()));
    }

    for (PreparedEntry item : prepared) {
      if (item.content.length > 0 || item.kind.hasContent()) {
        ensureBlob(item.hashHex(), item.content);
      }
    }

    String captureId = newId("cap");
    List<String> snapshotIds = new ArrayList<String>();
    for (PreparedEntry item : prepared) {
      String id = newSnapshotId();
      String hash = item.revision;
      long size = item.content.length;
      if (!item.kind.hasContent()) {
        hash = null;
        size = 0L;
      }
      SnapshotRecord record =
          new SnapshotRecord(
              id,
              captureId,
              item.path,
              item.kind,
              hash,
              size,
              null,
              nextCreatedAt(),
              request.actor(),
              reason,
              request.deploymentId(),
              item.parent,
              item.pluginMetadata);
      atomicWrite(
          snapshotFile(id), SnapshotJson.writeRecord(record).getBytes(StandardCharsets.UTF_8));
      snapshotIds.add(id);
    }

    for (PreparedEntry item : prepared) {
      List<String> ids = indexes.get(item.path);
      int position = snapshotIds.size() - prepared.size() + prepared.indexOf(item);
      ids.add(snapshotIds.get(position));
      atomicWrite(
          indexFile(item.path),
          SnapshotJson.writeIndex(item.path, ids).getBytes(StandardCharsets.UTF_8));
    }
    return new CaptureResult(captureId, snapshotIds);
  }

  private RestoreResult restoreLocked(
      String snapshotId, String targetPath, String expectedRevision, String actor)
      throws SnapshotStoreException {
    requireFormat();
    if (snapshotId == null || !SnapshotJson.SNAPSHOT_ID.matcher(snapshotId).matches()) {
      throw notFound(String.valueOf(snapshotId));
    }
    SnapshotRecord record = readRecord(snapshotId);
    SandboxPath target = resolveTarget(targetPath);
    if (!record.path().equals(target.relativePath())) {
      throw conflict(target.relativePath());
    }
    if (!activeDeployments.isEmpty()) {
      throw unavailable(SnapshotStoreDetail.ACTIVE_DEPLOYMENT);
    }
    String operation = newId("op");
    activeOperations.add(operation);
    try {
      CurrentState current = currentState(target);
      if (!revisionMatches(current.revision, expectedRevision)) {
        throw conflict(target.relativePath());
      }
      if (current.directory && record.kind().hasContent()) {
        throw conflict(target.relativePath());
      }
      SnapshotEntry preEntry =
          current.exists
              ? (current.directory
                  ? SnapshotEntry.directory(current.path.relativePath())
                  : SnapshotEntry.file(
                      current.path.relativePath(), current.content, current.revision))
              : SnapshotEntry.deleted(current.path.relativePath());
      List<SnapshotEntry> entries = new ArrayList<SnapshotEntry>();
      entries.add(preEntry);
      SnapshotRequest preRequest =
          new SnapshotRequest(
              resolver.root().toString(),
              "before-restore",
              entries,
              actor,
              record.deploymentId(),
              null);
      CaptureResult preCapture = captureLocked(preRequest);
      String preRestoreId = preCapture.snapshotIds().get(0);

      if (record.kind() == SnapshotKind.DELETED) {
        deleteTarget(current, expectedRevision);
      } else if (record.kind() == SnapshotKind.DIRECTORY) {
        ensureDirectory(current);
      } else {
        byte[] bytes = readVerifiedBlob(record);
        writeTarget(current, bytes, expectedRevision);
      }
      return new RestoreResult(snapshotId, preRestoreId);
    } finally {
      activeOperations.remove(operation);
    }
  }

  private CurrentState currentState(SandboxPath path) throws SnapshotStoreException {
    BasicFileAttributes attributes = attributes(path.absolutePath());
    if (attributes == null) {
      return new CurrentState(path, false, false, null, null, new byte[0]);
    }
    if (attributes.isSymbolicLink() || attributes.isOther()) {
      throw pathRejected(path.relativePath(), SnapshotStoreDetail.PATH_LINK);
    }
    if (attributes.isDirectory()) {
      return new CurrentState(path, true, true, null, null, new byte[0]);
    }
    if (!attributes.isRegularFile()) {
      throw pathRejected(path.relativePath(), SnapshotStoreDetail.PATH_UNSUPPORTED_TYPE);
    }
    byte[] content = readFile(path.absolutePath());
    return new CurrentState(path, true, false, attributes, FileRevision.of(content), content);
  }

  private void writeTarget(CurrentState current, byte[] content, String expectedRevision)
      throws SnapshotStoreException {
    Path target = current.path.absolutePath();
    Path parent = target.getParent();
    if (parent == null) {
      throw pathRejected(current.path.relativePath(), SnapshotStoreDetail.PATH_NO_PARENT);
    }
    Path temporary = null;
    try {
      temporary = Files.createTempFile(parent, ".deploymc-restore-", ".tmp");
      restrictPermissions(temporary);
      writeAndForce(temporary, content);
      recheckTarget(current, expectedRevision);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        throw io(unsupported);
      }
      temporary = null;
      fsyncParent(target);
      SandboxPath committed = resolveTarget(current.path.relativePath());
      BasicFileAttributes after = attributes(committed.absolutePath());
      if (after == null || !after.isRegularFile()) {
        throw corrupt(SnapshotStoreDetail.RESTORE_TARGET_NOT_FILE);
      }
      resolver.verifyOpened(committed, committed.absolutePath());
      String written = FileRevision.of(committed.absolutePath());
      if (!written.equals(FileRevision.of(content))) {
        throw corrupt(SnapshotStoreDetail.RESTORE_CONTENT_CHANGED);
      }
    } catch (SandboxRejectionException rejection) {
      throw pathRejected(
          current.path.relativePath(),
          SnapshotStoreDetail.PATH_SANDBOX_REJECTED,
          rejection.code().name());
    } catch (SnapshotStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw io(failure);
    } finally {
      deleteQuietly(temporary);
    }
  }

  private void recheckTarget(CurrentState current, String expectedRevision)
      throws SnapshotStoreException {
    BasicFileAttributes now = attributes(current.path.absolutePath());
    if (!current.exists) {
      if (now != null) {
        throw conflict(current.path.relativePath());
      }
      return;
    }
    if (now == null || !sameIdentity(current.attributes, now)) {
      throw conflict(current.path.relativePath());
    }
    if (current.revision != null) {
      String actual = FileRevision.of(readFile(current.path.absolutePath()));
      if (!current.revision.equals(actual)
          || (expectedRevision != null && !expectedRevision.equals(actual))) {
        throw conflict(current.path.relativePath());
      }
    }
  }

  private void deleteTarget(CurrentState current, String expectedRevision)
      throws SnapshotStoreException {
    recheckTarget(current, expectedRevision);
    if (!current.exists) {
      return;
    }
    if (current.directory) {
      throw conflict(current.path.relativePath());
    }
    try {
      Files.delete(current.path.absolutePath());
      fsyncParent(current.path.absolutePath());
    } catch (IOException failure) {
      throw io(failure);
    }
  }

  private void ensureDirectory(CurrentState current) throws SnapshotStoreException {
    if (current.exists) {
      if (!current.directory) {
        throw conflict(current.path.relativePath());
      }
      return;
    }
    try {
      Files.createDirectory(current.path.absolutePath());
      fsyncParent(current.path.absolutePath());
    } catch (IOException failure) {
      throw io(failure);
    }
  }

  private byte[] readVerifiedBlob(SnapshotRecord record) throws SnapshotStoreException {
    Path blob = blobPath(record.hashHex());
    byte[] bytes = readFile(blob);
    String actual = FileRevision.of(bytes);
    if (!record.hash().equals(actual) || bytes.length != record.size()) {
      throw corrupt(SnapshotStoreDetail.BLOB_METADATA_MISMATCH, record.id());
    }
    return bytes;
  }

  private RetentionReport retainLocked() throws SnapshotStoreException {
    return pruneLocked(null);
  }

  private RetentionReport pruneLocked(Selector selector) throws SnapshotStoreException {
    requireFormat();
    List<SnapshotRecord> records = readAllRecords();
    Map<String, IndexData> indexes = readAllIndexes();
    Map<String, List<SnapshotRecord>> byPath = new LinkedHashMap<String, List<SnapshotRecord>>();
    for (SnapshotRecord record : records) {
      List<SnapshotRecord> pathRecords = byPath.get(record.path());
      if (pathRecords == null) {
        pathRecords = new ArrayList<SnapshotRecord>();
        byPath.put(record.path(), pathRecords);
      }
      pathRecords.add(record);
    }
    for (List<SnapshotRecord> pathRecords : byPath.values()) {
      Collections.sort(pathRecords, CREATED_ORDER);
    }

    Set<String> protectedIds = new HashSet<String>();
    for (List<SnapshotRecord> pathRecords : byPath.values()) {
      int newestStart = Math.max(0, pathRecords.size() - 3);
      for (int i = newestStart; i < pathRecords.size(); i++) {
        protectedIds.add(pathRecords.get(i).id());
      }
    }
    for (SnapshotRecord record : records) {
      if (record.deploymentId() != null && activeDeployments.contains(record.deploymentId())) {
        protectedIds.add(record.id());
      }
    }

    Set<String> candidates = new LinkedHashSet<String>();
    if (selector == null) {
      for (List<SnapshotRecord> pathRecords : byPath.values()) {
        int limit = Math.min(config.maxVersionsPerPath(), pathRecords.size());
        for (int i = 0; i < pathRecords.size() - limit; i++) {
          if (!protectedIds.contains(pathRecords.get(i).id())) {
            candidates.add(pathRecords.get(i).id());
          }
        }
      }
      Instant cutoff = Instant.now(clock).minus(Duration.ofDays(config.minAgeDays()));
      for (SnapshotRecord record : records) {
        if (record.createdAt().isBefore(cutoff) && !protectedIds.contains(record.id())) {
          candidates.add(record.id());
        }
      }
      long bytes = storeBytes();
      if (bytes > config.maxStoreBytes()) {
        List<SnapshotRecord> eligible = new ArrayList<SnapshotRecord>();
        for (SnapshotRecord record : records) {
          if (!protectedIds.contains(record.id())) {
            eligible.add(record);
          }
        }
        Collections.sort(eligible, CREATED_ORDER);
        for (SnapshotRecord record : eligible) {
          if (bytes <= config.maxStoreBytes()) {
            break;
          }
          candidates.add(record.id());
          bytes -= Math.max(0L, record.size());
        }
      }
    } else {
      for (SnapshotRecord record : records) {
        if (selector.matches(record) && !protectedIds.contains(record.id())) {
          candidates.add(record.id());
        }
      }
    }

    long before = storeBytes();
    int pruned = 0;
    int failed = 0;
    for (String id : candidates) {
      SnapshotRecord record = find(records, id);
      if (record == null) {
        failed++;
        continue;
      }
      try {
        if (record.hashHex() != null) {
          Path blob = blobPath(record.hashHex());
          if (exists(blob)) {
            Files.setLastModifiedTime(blob, FileTime.from(Instant.now(clock)));
          }
        }
        IndexData index = indexes.get(pathHash(record.path()));
        List<String> ids = index == null ? new ArrayList<String>() : new ArrayList<>(index.ids());
        ids.remove(record.id());
        IndexData updated = new IndexData(record.path(), ids);
        atomicWrite(
            indexFile(record.path()),
            SnapshotJson.writeIndex(updated.path(), updated.ids())
                .getBytes(StandardCharsets.UTF_8));
        indexes.put(pathHash(record.path()), updated);
        Path metadata = snapshotFile(id);
        if (exists(metadata)) {
          Files.delete(metadata);
        }
        pruned++;
      } catch (SnapshotStoreException failure) {
        failed++;
      } catch (IOException failure) {
        failed++;
      }
    }
    return new RetentionReport(
        records.size(), protectedIds.size(), pruned, failed, before, storeBytes());
  }

  private GcReport gcLocked() throws SnapshotStoreException {
    requireFormat();
    if (!activeDeployments.isEmpty() || !activeOperations.isEmpty()) {
      throw unavailable(SnapshotStoreDetail.ACTIVE_DEPLOYMENT);
    }
    VerifyReport verification = verifyLocked();
    if (hasBlockingVerifyIssue(verification)) {
      return new GcReport(true, verification, null, 0, 0, 0L);
    }
    RetentionReport retention = retainLocked();
    List<SnapshotRecord> records = readAllRecords();
    Set<String> reachable = new HashSet<String>();
    for (SnapshotRecord record : records) {
      if (record.hashHex() != null) {
        reachable.add(record.hashHex());
      }
    }
    List<Path> blobs = listBlobFiles();
    Instant cutoff = Instant.now(clock).minus(Duration.ofHours(config.gcGraceHours()));
    int deleted = 0;
    long reclaimed = 0L;
    for (Path blob : blobs) {
      String hex = blobHash(blob);
      String hash = verifyBlob(blob, hex);
      if (reachable.contains(hash)) {
        continue;
      }
      BasicFileAttributes attributes = attributes(blob);
      if (attributes == null || !attributes.lastModifiedTime().toInstant().isBefore(cutoff)) {
        continue;
      }
      long size = attributes.size();
      try {
        Files.delete(blob);
        fsyncParent(blob);
        deleted++;
        reclaimed += size;
      } catch (IOException failure) {
        throw io(failure);
      }
    }
    return new GcReport(false, verification, retention, blobs.size(), deleted, reclaimed);
  }

  private static boolean hasBlockingVerifyIssue(VerifyReport report) {
    for (VerifyIssue issue : report.issues()) {
      if (!"blob.orphan".equals(issue.code())) {
        return true;
      }
    }
    return false;
  }

  private VerifyReport verifyLocked() throws SnapshotStoreException {
    List<VerifyIssue> issues = new ArrayList<VerifyIssue>();
    if (!exists(formatsFile) || !FORMAT_VERSION.equals(readUtf8(formatsFile))) {
      issues.add(new VerifyIssue("format.unsupported", "format-version"));
    }

    Map<String, SnapshotRecord> records = new LinkedHashMap<String, SnapshotRecord>();
    for (Path file : listJsonFiles(snapshotsDir)) {
      String id = fileName(file);
      try {
        SnapshotRecord record = SnapshotJson.parseRecord(readUtf8(file), id);
        String normalized = normalizeEntryPath(record.path(), SandboxAccessMode.READ);
        if (!normalized.equals(record.path())) {
          throw new IllegalArgumentException("path is not normalized");
        }
        if (records.put(id, record) != null) {
          issues.add(new VerifyIssue("metadata.invalid", historyRelative(file)));
        }
      } catch (RuntimeException | SnapshotStoreException failure) {
        issues.add(new VerifyIssue("metadata.invalid", historyRelative(file)));
      }
    }

    Map<String, IndexData> indexes = new LinkedHashMap<String, IndexData>();
    for (Path file : listJsonFiles(filesIndexDir)) {
      try {
        IndexData index = SnapshotJson.parseIndex(readUtf8(file));
        String hash = pathHash(index.path());
        if (!hash.equals(fileName(file))) {
          throw new IllegalArgumentException("index path hash mismatch");
        }
        Set<String> seen = new HashSet<String>();
        for (String id : index.ids()) {
          if (!seen.add(id)) {
            throw new IllegalArgumentException("duplicate index id");
          }
          SnapshotRecord record = records.get(id);
          if (record == null || !record.path().equals(index.path())) {
            throw new IllegalArgumentException("index id does not resolve");
          }
        }
        List<String> expected = new ArrayList<String>(index.ids());
        List<SnapshotRecord> pathRecords = recordsForPath(records, index.path());
        Collections.sort(pathRecords, CREATED_ORDER);
        List<String> ordered = new ArrayList<String>();
        for (SnapshotRecord record : pathRecords) {
          ordered.add(record.id());
        }
        if (!expected.equals(ordered)) {
          throw new IllegalArgumentException("index order is unstable");
        }
        indexes.put(hash, index);
      } catch (RuntimeException failure) {
        issues.add(new VerifyIssue("index.invalid", historyRelative(file)));
      }
    }

    for (SnapshotRecord record : records.values()) {
      IndexData index = indexes.get(pathHash(record.path()));
      if (index == null) {
        issues.add(
            new VerifyIssue("index.missing", "index/files/" + pathHash(record.path()) + ".json"));
      } else if (!index.ids().contains(record.id())) {
        issues.add(
            new VerifyIssue("index.invalid", "index/files/" + pathHash(record.path()) + ".json"));
      }
      if (record.hashHex() != null) {
        Path blob = blobPath(record.hashHex());
        if (!exists(blob)) {
          issues.add(new VerifyIssue("blob.missing", historyRelative(blob)));
        } else {
          try {
            verifyBlob(blob, record.hashHex());
          } catch (SnapshotStoreException failure) {
            issues.add(new VerifyIssue("blob.corrupt", historyRelative(blob)));
          }
        }
      }
    }

    Set<String> reachable = new HashSet<String>();
    for (SnapshotRecord record : records.values()) {
      if (record.hashHex() != null) {
        reachable.add(record.hashHex());
      }
    }
    int blobs = 0;
    for (Path blob : listBlobFiles()) {
      blobs++;
      final String hex;
      try {
        hex = blobHash(blob);
      } catch (SnapshotStoreException failure) {
        issues.add(new VerifyIssue("blob.corrupt", historyRelative(blob)));
        continue;
      }
      try {
        verifyBlob(blob, hex);
        if (!reachable.contains(hex)) {
          issues.add(new VerifyIssue("blob.orphan", historyRelative(blob)));
        }
      } catch (SnapshotStoreException failure) {
        issues.add(new VerifyIssue("blob.corrupt", historyRelative(blob)));
      }
    }
    int staging = 0;
    for (Path file : listStagingFiles()) {
      staging++;
      issues.add(new VerifyIssue("staging.unreferenced", historyRelative(file)));
    }
    return new VerifyReport(records.size(), indexes.size(), blobs, staging, issues);
  }

  private RepairReport repairLocked() throws SnapshotStoreException {
    VerifyReport verification = verifyLocked();
    List<RepairAction> actions = new ArrayList<RepairAction>();
    List<VerifyIssue> unresolved = new ArrayList<VerifyIssue>(verification.issues());

    for (Path file : listStagingFiles()) {
      String detail = historyRelative(file);
      try {
        Files.delete(file);
        actions.add(new RepairAction("staging.removed", detail));
        unresolved.remove(new VerifyIssue("staging.unreferenced", detail));
      } catch (IOException ignored) {
        unresolved.add(new VerifyIssue("staging.remove-failed", detail));
      }
    }

    Map<String, List<SnapshotRecord>> groups = new LinkedHashMap<String, List<SnapshotRecord>>();
    boolean invalidMetadata = false;
    for (Path file : listJsonFiles(snapshotsDir)) {
      String id = fileName(file);
      try {
        SnapshotRecord record = SnapshotJson.parseRecord(readUtf8(file), id);
        String hash = pathHash(record.path());
        List<SnapshotRecord> group = groups.get(hash);
        if (group == null) {
          group = new ArrayList<SnapshotRecord>();
          groups.put(hash, group);
        }
        group.add(record);
      } catch (RuntimeException failure) {
        invalidMetadata = true;
      }
    }
    if (!invalidMetadata) {
      for (Map.Entry<String, List<SnapshotRecord>> entry : groups.entrySet()) {
        List<SnapshotRecord> group = entry.getValue();
        Collections.sort(group, CREATED_ORDER);
        List<String> ids = new ArrayList<String>();
        for (SnapshotRecord record : group) {
          ids.add(record.id());
        }
        String path = group.get(0).path();
        String detail = "index/files/" + entry.getKey() + ".json";
        boolean consistent = false;
        Path index = indexFile(path);
        if (exists(index)) {
          try {
            IndexData parsed = SnapshotJson.parseIndex(readUtf8(index));
            consistent = parsed.ids().equals(ids) && pathHash(parsed.path()).equals(entry.getKey());
          } catch (RuntimeException failure) {
            consistent = false;
          }
        }
        if (!consistent) {
          atomicWrite(index, SnapshotJson.writeIndex(path, ids).getBytes(StandardCharsets.UTF_8));
          actions.add(new RepairAction("index.rebuilt", detail));
          unresolved.remove(new VerifyIssue("index.missing", detail));
          unresolved.remove(new VerifyIssue("index.invalid", detail));
        }
      }
    }
    return new RepairReport(actions, unresolved);
  }

  private List<SnapshotRecord> recordsForPath(Map<String, SnapshotRecord> records, String path) {
    List<SnapshotRecord> result = new ArrayList<SnapshotRecord>();
    for (SnapshotRecord record : records.values()) {
      if (path.equals(record.path())) {
        result.add(record);
      }
    }
    return result;
  }

  private List<String> validatedIndexIds(String path) throws SnapshotStoreException {
    Path file = indexFile(path);
    if (!exists(file)) {
      return new ArrayList<String>();
    }
    IndexData index;
    try {
      index = SnapshotJson.parseIndex(readUtf8(file));
    } catch (RuntimeException failure) {
      throw corrupt(SnapshotStoreDetail.INDEX_INVALID_PATH, path);
    }
    if (!path.equals(index.path()) || !pathHash(path).equals(fileName(file))) {
      throw corrupt(SnapshotStoreDetail.INDEX_PATH_MISMATCH);
    }
    List<String> ids = index.ids();
    Set<String> seen = new HashSet<String>();
    Map<String, SnapshotRecord> loaded = new HashMap<String, SnapshotRecord>();
    for (String id : ids) {
      if (!seen.add(id)) {
        throw corrupt(SnapshotStoreDetail.INDEX_DUPLICATE_ID, id);
      }
      final SnapshotRecord record;
      try {
        record = readRecord(id);
      } catch (SnapshotStoreException failure) {
        throw corrupt(SnapshotStoreDetail.INDEX_UNREADABLE_ID);
      }
      if (!path.equals(record.path())) {
        throw corrupt(SnapshotStoreDetail.INDEX_FOREIGN_ID);
      }
      loaded.put(id, record);
    }
    List<SnapshotRecord> ordered = new ArrayList<SnapshotRecord>(loaded.values());
    Collections.sort(ordered, CREATED_ORDER);
    for (int i = 0; i < ids.size(); i++) {
      if (!ids.get(i).equals(ordered.get(i).id())) {
        throw corrupt(SnapshotStoreDetail.INDEX_ORDER_UNSTABLE, path);
      }
    }
    return new ArrayList<String>(ids);
  }

  private Map<String, IndexData> readAllIndexes() throws SnapshotStoreException {
    Map<String, IndexData> result = new LinkedHashMap<String, IndexData>();
    for (Path file : listJsonFiles(filesIndexDir)) {
      IndexData index;
      try {
        index = SnapshotJson.parseIndex(readUtf8(file));
      } catch (RuntimeException failure) {
        throw corrupt(SnapshotStoreDetail.INDEX_INVALID);
      }
      if (!pathHash(index.path()).equals(fileName(file))) {
        throw corrupt(SnapshotStoreDetail.INDEX_HASH_MISMATCH);
      }
      Set<String> seen = new HashSet<String>();
      for (String id : index.ids()) {
        if (!seen.add(id)) {
          throw corrupt(SnapshotStoreDetail.INDEX_DUPLICATE_ID);
        }
      }
      result.put(fileName(file), index);
    }
    return result;
  }

  private List<SnapshotRecord> readAllRecords() throws SnapshotStoreException {
    List<SnapshotRecord> records = new ArrayList<SnapshotRecord>();
    for (Path file : listJsonFiles(snapshotsDir)) {
      SnapshotRecord record = readRecord(fileName(file));
      records.add(record);
    }
    Collections.sort(records, CREATED_ORDER);
    return records;
  }

  private static SnapshotRecord find(List<SnapshotRecord> records, String id) {
    for (SnapshotRecord record : records) {
      if (record.id().equals(id)) {
        return record;
      }
    }
    return null;
  }

  private SnapshotRecord readRecord(String id) throws SnapshotStoreException {
    if (id == null || !SnapshotJson.SNAPSHOT_ID.matcher(id).matches()) {
      throw notFound(String.valueOf(id));
    }
    Path file = snapshotFile(id);
    if (!exists(file)) {
      throw notFound(id);
    }
    final SnapshotRecord record;
    try {
      record = SnapshotJson.parseRecord(readUtf8(file), id);
    } catch (RuntimeException failure) {
      throw corrupt(SnapshotStoreDetail.METADATA_INVALID, id);
    }
    try {
      String normalized = normalizeEntryPath(record.path(), SandboxAccessMode.READ);
      if (!normalized.equals(record.path())) {
        throw corrupt(SnapshotStoreDetail.METADATA_PATH_UNNORMALIZED, id);
      }
    } catch (SnapshotStoreException failure) {
      throw corrupt(SnapshotStoreDetail.METADATA_PATH_REJECTED, id);
    }
    return record;
  }

  private void ensureBlob(String hex, byte[] content) throws SnapshotStoreException {
    Path blob = blobPath(hex);
    if (exists(blob)) {
      verifyBlob(blob, hex);
      return;
    }
    Path temporary = null;
    try {
      Path bucket = blob.getParent();
      if (bucket == null) {
        throw corrupt(SnapshotStoreDetail.BLOB_PATH_INVALID);
      }
      Files.createDirectories(bucket);
      temporary = Files.createTempFile(bucket, BLOB_STAGING_PREFIX, STAGING_SUFFIX);
      restrictPermissions(temporary);
      writeAndForce(temporary, content);
      if (exists(blob)) {
        verifyBlob(blob, hex);
        return;
      }
      try {
        Files.move(temporary, blob, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        throw io(unsupported);
      } catch (FileAlreadyExistsException race) {
        verifyBlob(blob, hex);
        return;
      }
      temporary = null;
      fsyncParent(blob);
    } catch (SnapshotStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw io(failure);
    } finally {
      deleteQuietly(temporary);
    }
  }

  private String verifyBlob(Path blob, String expectedHex) throws SnapshotStoreException {
    BasicFileAttributes attributes = attributes(blob);
    if (attributes == null || !attributes.isRegularFile()) {
      throw corrupt(SnapshotStoreDetail.BLOB_MISSING);
    }
    byte[] bytes = readFile(blob);
    String actual = FileRevision.of(bytes).substring("sha256:".length());
    if (!expectedHex.equals(actual)) {
      throw corrupt(SnapshotStoreDetail.BLOB_HASH_MISMATCH);
    }
    return actual;
  }

  private String blobHash(Path blob) throws SnapshotStoreException {
    Path parent = blob.getParent();
    if (parent == null) {
      throw corrupt(SnapshotStoreDetail.BLOB_PATH_INVALID);
    }
    String directory = parent.getFileName().toString();
    String file = blob.getFileName().toString();
    if (directory.length() != 2 || file.length() != 62 || !isLowerHex(directory + file)) {
      throw corrupt(SnapshotStoreDetail.BLOB_NAME_INVALID);
    }
    return directory + file;
  }

  private Path blobPath(String hex) {
    return objectsDir.resolve(hex.substring(0, 2)).resolve(hex.substring(2));
  }

  private Path snapshotFile(String id) {
    return snapshotsDir.resolve(id + ".json");
  }

  private Path indexFile(String path) {
    return filesIndexDir.resolve(pathHash(path) + ".json");
  }

  private void atomicWrite(Path target, byte[] content) throws SnapshotStoreException {
    Path temporary = null;
    try {
      temporary = Files.createTempFile(tmpDir, ".deploymc-meta-", ".tmp");
      restrictPermissions(temporary);
      writeAndForce(temporary, content);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        throw io(unsupported);
      }
      temporary = null;
      fsyncParent(target);
    } catch (SnapshotStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw io(failure);
    } finally {
      deleteQuietly(temporary);
    }
  }

  private void writeAndForce(Path target, byte[] content) throws IOException {
    FileChannel channel =
        FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      ByteBuffer buffer = ByteBuffer.wrap(content);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    } finally {
      channel.close();
    }
  }

  private void validateRoot(String rootPath) throws SnapshotStoreException {
    try {
      Path root = Paths.get(rootPath).toRealPath();
      if (!root.equals(resolver.root())) {
        throw unavailable(SnapshotStoreDetail.ROOT_MISMATCH);
      }
    } catch (InvalidPathException | IOException failure) {
      throw unavailable(SnapshotStoreDetail.ROOT_INVALID);
    }
  }

  private String normalizeEntryPath(String requested, SandboxAccessMode mode)
      throws SnapshotStoreException {
    if (requested == null || requested.isEmpty()) {
      throw pathRejected(String.valueOf(requested), SnapshotStoreDetail.PATH_EMPTY);
    }
    if (requested.indexOf('\\') >= 0 || requested.indexOf('%') >= 0) {
      throw pathRejected(requested, SnapshotStoreDetail.PATH_SEPARATOR_ESCAPE);
    }
    String normalized = Normalizer.normalize(requested, Normalizer.Form.NFC);
    if (normalized.startsWith("/") || DRIVE_PREFIX.matcher(normalized).matches()) {
      throw pathRejected(requested, SnapshotStoreDetail.PATH_ABSOLUTE);
    }
    for (String segment : normalized.split("/", -1)) {
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
        throw pathRejected(requested, SnapshotStoreDetail.PATH_DOT_SEGMENT);
      }
    }
    try {
      SandboxPath resolved = resolver.resolve(normalized, mode);
      if (resolved.relativePath().isEmpty() || !normalized.equals(resolved.relativePath())) {
        throw pathRejected(requested, SnapshotStoreDetail.PATH_OUTSIDE_ROOT);
      }
      return normalized;
    } catch (SandboxRejectionException rejection) {
      throw pathRejected(
          requested, SnapshotStoreDetail.PATH_SANDBOX_REJECTED, rejection.code().name());
    }
  }

  private SandboxPath resolveTarget(String requested) throws SnapshotStoreException {
    try {
      SandboxPath path = resolver.resolve(requested, SandboxAccessMode.WRITE);
      if (path.relativePath().isEmpty()) {
        throw pathRejected(requested, SnapshotStoreDetail.PATH_SANDBOX_ROOT);
      }
      return path;
    } catch (SandboxRejectionException rejection) {
      throw pathRejected(
          requested, SnapshotStoreDetail.PATH_SANDBOX_REJECTED, rejection.code().name());
    }
  }

  private static SnapshotKind kindOf(String path, SnapshotEntry entry) {
    if (entry.deleted()) {
      return SnapshotKind.DELETED;
    }
    if (entry.directory()) {
      return SnapshotKind.DIRECTORY;
    }
    if (path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
      return SnapshotKind.JAR;
    }
    return SnapshotKind.FILE;
  }

  private static boolean revisionMatches(String actual, String expected) {
    if (expected == null) {
      return true;
    }
    return expected.equals(actual == null ? "absent" : actual);
  }

  private Instant nextCreatedAt() {
    Instant now = Instant.now(clock);
    if (!now.isAfter(lastCreatedAt)) {
      now = lastCreatedAt.plusMillis(1L);
    }
    lastCreatedAt = now;
    return now;
  }

  private String newSnapshotId() throws SnapshotStoreException {
    while (true) {
      String id = "snap_" + randomChars(26);
      if (!exists(snapshotFile(id))) {
        return id;
      }
    }
  }

  private String newId(String prefix) {
    return prefix + "_" + randomChars(26);
  }

  private String randomChars(int length) {
    StringBuilder result = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      result.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
    }
    return result.toString();
  }

  private static String pathHash(String path) {
    String normalized = Normalizer.normalize(path, Normalizer.Form.NFC);
    return hex(digest().digest(normalized.getBytes(StandardCharsets.UTF_8)));
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
    }
  }

  private static String hex(byte[] bytes) {
    char[] result = new char[bytes.length * 2];
    char[] alphabet = "0123456789abcdef".toCharArray();
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xff;
      result[i * 2] = alphabet[value >>> 4];
      result[i * 2 + 1] = alphabet[value & 0x0f];
    }
    return new String(result);
  }

  private static boolean isLowerHex(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
        return false;
      }
    }
    return true;
  }

  private String readUtf8(Path file) throws SnapshotStoreException {
    byte[] bytes = readFile(file);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private byte[] readFile(Path file) throws SnapshotStoreException {
    BasicFileAttributes attributes = attributes(file);
    if (attributes == null || !attributes.isRegularFile()) {
      throw corrupt(SnapshotStoreDetail.FILE_MISSING);
    }
    try {
      return Files.readAllBytes(file);
    } catch (IOException failure) {
      throw io(failure);
    }
  }

  private BasicFileAttributes attributes(Path file) throws SnapshotStoreException {
    try {
      return Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (NoSuchFileException missing) {
      return null;
    } catch (IOException failure) {
      throw io(failure);
    }
  }

  private static boolean exists(Path path) {
    return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
  }

  private List<Path> listJsonFiles(Path directory) throws SnapshotStoreException {
    List<Path> result = new ArrayList<Path>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
      for (Path entry : entries) {
        if (exists(entry)) {
          result.add(entry);
        }
      }
    } catch (IOException failure) {
      throw io(failure);
    }
    Collections.sort(result);
    return result;
  }

  private List<Path> listRegularFiles(Path directory) throws SnapshotStoreException {
    List<Path> result = new ArrayList<Path>();
    if (!exists(directory)) {
      return result;
    }
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        BasicFileAttributes attributes = attributes(entry);
        if (attributes != null && attributes.isRegularFile()) {
          result.add(entry);
        }
      }
    } catch (IOException failure) {
      throw io(failure);
    }
    Collections.sort(result);
    return result;
  }

  private List<Path> listStagingFiles() throws SnapshotStoreException {
    List<Path> result = new ArrayList<Path>(listRegularFiles(tmpDir));
    if (exists(objectsDir)) {
      try (DirectoryStream<Path> buckets = Files.newDirectoryStream(objectsDir)) {
        for (Path bucket : buckets) {
          BasicFileAttributes bucketAttributes = attributes(bucket);
          if (bucketAttributes == null || !bucketAttributes.isDirectory()) {
            continue;
          }
          for (Path file : listRegularFiles(bucket)) {
            if (isBlobStaging(file)) {
              result.add(file);
            }
          }
        }
      } catch (IOException failure) {
        throw io(failure);
      }
    }
    Collections.sort(result);
    return result;
  }

  private static boolean isBlobStaging(Path path) {
    String name = path.getFileName().toString();
    return name.startsWith(BLOB_STAGING_PREFIX) && name.endsWith(STAGING_SUFFIX);
  }

  private List<Path> listBlobFiles() throws SnapshotStoreException {
    List<Path> result = new ArrayList<Path>();
    if (!exists(objectsDir)) {
      return result;
    }
    try (DirectoryStream<Path> buckets = Files.newDirectoryStream(objectsDir)) {
      for (Path bucket : buckets) {
        BasicFileAttributes bucketAttributes = attributes(bucket);
        if (bucketAttributes == null || !bucketAttributes.isDirectory()) {
          result.add(bucket);
          continue;
        }
        for (Path blob : listRegularFiles(bucket)) {
          if (!isBlobStaging(blob)) {
            result.add(blob);
          }
        }
      }
    } catch (IOException failure) {
      throw io(failure);
    }
    Collections.sort(result);
    return result;
  }

  private long storeBytes() throws SnapshotStoreException {
    long total = 0L;
    for (Path file : listRegularFiles(snapshotsDir)) {
      total += sizeOf(file);
    }
    for (Path file : listJsonFiles(filesIndexDir)) {
      total += sizeOf(file);
    }
    for (Path blob : listBlobFiles()) {
      total += sizeOf(blob);
    }
    if (exists(formatsFile)) {
      total += sizeOf(formatsFile);
    }
    return total;
  }

  private long sizeOf(Path file) throws SnapshotStoreException {
    BasicFileAttributes attributes = attributes(file);
    return attributes == null ? 0L : attributes.size();
  }

  private String historyRelative(Path path) {
    return historyDir.relativize(path).toString().replace('\\', '/');
  }

  private static String fileName(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? name : name.substring(0, dot);
  }

  private static void restrictPermissions(Path path) {
    try {
      Files.setPosixFilePermissions(
          path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Windows and some file systems do not expose POSIX permissions.
    }
  }

  private static void fsyncParent(Path path) {
    Path parent = path.getParent();
    if (parent == null) {
      return;
    }
    FileChannel channel = null;
    try {
      channel = FileChannel.open(parent, StandardOpenOption.READ);
      channel.force(true);
    } catch (IOException | UnsupportedOperationException ignored) {
      // Directory fsync is best effort and is unavailable on some platforms.
    } finally {
      if (channel != null) {
        try {
          channel.close();
        } catch (IOException ignored) {
          // Best effort.
        }
      }
    }
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // A crash can also leave a staging file for startup cleanup.
    }
  }

  private static boolean sameIdentity(BasicFileAttributes left, BasicFileAttributes right) {
    if (left.fileKey() != null && right.fileKey() != null) {
      return Objects.equals(left.fileKey(), right.fileKey())
          && left.size() == right.size()
          && left.lastModifiedTime().equals(right.lastModifiedTime());
    }
    return left.size() == right.size() && left.lastModifiedTime().equals(right.lastModifiedTime());
  }

  private static SnapshotStoreException notFound(String id) {
    return new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, String.valueOf(id));
  }

  private static SnapshotStoreException corrupt(SnapshotStoreDetail detail, Object... parameters) {
    return new SnapshotStoreException(
        SnapshotStoreException.Reason.CORRUPT, new LocalizedDetail(detail, parameters));
  }

  private static SnapshotStoreException conflict(String detail) {
    return new SnapshotStoreException(SnapshotStoreException.Reason.RESTORE_CONFLICT, detail);
  }

  private static SnapshotStoreException unavailable(
      SnapshotStoreDetail detail, Object... parameters) {
    return new SnapshotStoreException(
        SnapshotStoreException.Reason.STORE_UNAVAILABLE, new LocalizedDetail(detail, parameters));
  }

  private static SnapshotStoreException pathRejected(
      String path, SnapshotStoreDetail detail, Object... parameters) {
    return new SnapshotStoreException(
        SnapshotStoreException.Reason.PATH_REJECTED,
        String.valueOf(path),
        new LocalizedDetail(detail, parameters));
  }

  private static SnapshotStoreException io(IOException failure) {
    return new SnapshotStoreException(
        SnapshotStoreException.Reason.IO, failure, failure.getClass().getSimpleName());
  }

  private interface LockedOperation<T> {
    T run() throws SnapshotStoreException;
  }

  private interface Selector {
    boolean matches(SnapshotRecord record);
  }

  private static final Comparator<SnapshotRecord> CREATED_ORDER =
      new Comparator<SnapshotRecord>() {
        @Override
        public int compare(SnapshotRecord left, SnapshotRecord right) {
          int byTime = left.createdAt().compareTo(right.createdAt());
          return byTime != 0 ? byTime : left.id().compareTo(right.id());
        }
      };

  private static final class PreparedEntry {
    private final String path;
    private final SnapshotKind kind;
    private final byte[] content;
    private final String revision;
    private final String parent;
    private final Map<String, String> pluginMetadata;

    private PreparedEntry(
        String path,
        SnapshotKind kind,
        byte[] content,
        String revision,
        String parent,
        Map<String, String> pluginMetadata) {
      this.path = path;
      this.kind = kind;
      this.content = content;
      this.revision = revision;
      this.parent = parent;
      this.pluginMetadata = pluginMetadata;
    }

    private String hashHex() {
      return revision == null ? null : revision.substring("sha256:".length());
    }
  }

  private static final class CurrentState {
    private final SandboxPath path;
    private final boolean exists;
    private final boolean directory;
    private final BasicFileAttributes attributes;
    private final String revision;
    private final byte[] content;

    private CurrentState(
        SandboxPath path,
        boolean exists,
        boolean directory,
        BasicFileAttributes attributes,
        String revision,
        byte[] content) {
      this.path = path;
      this.exists = exists;
      this.directory = directory;
      this.attributes = attributes;
      this.revision = revision;
      this.content = content;
    }
  }
}
