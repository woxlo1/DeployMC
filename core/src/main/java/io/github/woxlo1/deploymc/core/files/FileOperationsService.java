package io.github.woxlo1.deploymc.core.files;

import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxPath;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Sandboxed local file operations with strong revisions, atomic writes, and explicit destructive
 * deletion.
 *
 * <p>All request paths are resolved by {@link SandboxResolver}. No method accepts a raw host path.
 * Revisions are {@code sha256:<lowercase hex>} hashes of file bytes, so they can be used directly
 * as ETag sources. In-process writes are serialized with fixed stripes; cross-process writers are
 * detected by re-checking revisions and file identity immediately before the atomic commit, but are
 * not serialized by this class.
 */
public final class FileOperationsService {

  private static final int LOCK_STRIPES = 256;
  private static final int COPY_BUFFER_SIZE = 8192;
  private static final String TEMP_PREFIX = ".deploymc-file-";
  private static final String TEMP_SUFFIX = ".tmp";
  private static final Comparator<PathState> PATH_STATE_ORDER =
      new Comparator<PathState>() {
        @Override
        public int compare(PathState left, PathState right) {
          return left.path.relativePath().compareTo(right.path.relativePath());
        }
      };

  private final SandboxResolver resolver;
  private final FileOperationLimits limits;
  private final SnapshotHook snapshotHook;
  private final WriteCrashPoint crashPoint;
  private final Object[] locks = new Object[LOCK_STRIPES];

  public FileOperationsService(SandboxResolver resolver) {
    this(resolver, FileOperationLimits.defaults(), null, WriteCrashPoint.NONE);
  }

  public FileOperationsService(
      SandboxResolver resolver, FileOperationLimits limits, SnapshotHook snapshotHook) {
    this(resolver, limits, snapshotHook, WriteCrashPoint.NONE);
  }

  FileOperationsService(
      SandboxResolver resolver,
      FileOperationLimits limits,
      SnapshotHook snapshotHook,
      WriteCrashPoint crashPoint) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.snapshotHook = snapshotHook;
    this.crashPoint = Objects.requireNonNull(crashPoint, "crashPoint");
    for (int i = 0; i < locks.length; i++) {
      locks[i] = new Object();
    }
  }

  /** Lists readable sandbox entries. Rejected children are omitted; no absolute path is exposed. */
  public List<FileInfo> list(String requestPath) throws FileOperationException {
    SandboxPath directory = resolve(requestPath, SandboxAccessMode.READ);
    PathState state = requireState(directory);
    if (!state.attributes.isDirectory()) {
      throw new FileOperationException(
          FileOperationException.Reason.NOT_DIRECTORY, directory.relativePath());
    }
    List<FileInfo> entries = new ArrayList<FileInfo>();
    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(directory.absolutePath());
      try {
        for (Path child : stream) {
          String relative = join(directory.relativePath(), child.getFileName().toString());
          SandboxPath resolvedChild;
          try {
            resolvedChild = resolver.resolve(relative, SandboxAccessMode.READ);
          } catch (SandboxRejectionException rejected) {
            continue;
          }
          BasicFileAttributes attributes = rawAttributes(resolvedChild);
          if (attributes == null) {
            continue;
          }
          try {
            verify(resolvedChild, attributes);
          } catch (FileOperationException rejected) {
            continue;
          }
          if (attributes.isDirectory()) {
            entries.add(FileInfo.directory(relative, modifiedMillis(attributes)));
          } else if (attributes.isRegularFile()) {
            entries.add(
                FileInfo.file(relative, attributes.size(), null, modifiedMillis(attributes)));
          }
          if (entries.size() > limits.maxListEntries()) {
            throw new FileOperationException(
                FileOperationException.Reason.TOO_LARGE,
                requestPath,
                Long.valueOf(limits.maxListEntries()));
          }
        }
      } finally {
        stream.close();
      }
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.IO, requestPath, failure.getClass().getSimpleName());
    }
    Collections.sort(
        entries,
        new Comparator<FileInfo>() {
          @Override
          public int compare(FileInfo left, FileInfo right) {
            return left.relativePath().compareTo(right.relativePath());
          }
        });
    return Collections.unmodifiableList(entries);
  }

  /** Returns metadata and a full-file revision. Directory revisions are {@code null}. */
  public FileInfo stat(String requestPath) throws FileOperationException {
    return toInfo(requireState(resolve(requestPath, SandboxAccessMode.READ)));
  }

  /** Reads a full file or a bounded byte range. The revision always describes the whole file. */
  public FileContent read(String requestPath, ReadRange range) throws FileOperationException {
    Objects.requireNonNull(range, "range");
    SandboxPath path = resolve(requestPath, SandboxAccessMode.READ);
    PathState state = requireFileState(path);
    long size = state.attributes.size();
    if (size > limits.maxFileSizeBytes()) {
      throw new FileOperationException(
          FileOperationException.Reason.TOO_LARGE,
          requestPath,
          Long.valueOf(limits.maxFileSizeBytes()));
    }
    long offset = range.offset();
    long length;
    if (offset > size) {
      throw new FileOperationException(
          FileOperationException.Reason.INVALID_RANGE, requestPath, Long.valueOf(offset));
    }
    if (range.bounded()) {
      if (range.length() > size - offset) {
        throw new FileOperationException(
            FileOperationException.Reason.INVALID_RANGE, requestPath, Long.valueOf(range.length()));
      }
      length = range.length();
    } else {
      length = size - offset;
    }
    byte[] all = readAll(path, size);
    String revision = FileRevision.of(all);
    byte[] selected = Arrays.copyOfRange(all, (int) offset, (int) (offset + length));
    return new FileContent(path.relativePath(), selected, revision, size, offset);
  }

  public FileContent download(String requestPath) throws FileOperationException {
    return read(requestPath, ReadRange.full());
  }

  /** Creates a new file. An existing target is never replaced. */
  public FileInfo create(String requestPath, byte[] content) throws FileOperationException {
    Objects.requireNonNull(content, "content");
    SandboxPath path = resolve(requestPath, SandboxAccessMode.CREATE);
    return withLocks(
        () -> {
          requireWritableFileTarget(path);
          if (attributes(path) != null) {
            throw new FileOperationException(
                FileOperationException.Reason.ALREADY_EXISTS, path.relativePath());
          }
          CommittedWrite committed =
              writeTempAndCommit(
                  path,
                  new ByteArrayInputStream(content),
                  content.length,
                  limits.maxFileSizeBytes(),
                  false,
                  null,
                  null);
          return stat(path.relativePath());
        },
        path.relativePath());
  }

  public FileInfo mkdir(String requestPath) throws FileOperationException {
    SandboxPath path = resolve(requestPath, SandboxAccessMode.CREATE);
    return withLocks(
        () -> {
          requireWritableFileTarget(path);
          if (attributes(path) != null) {
            throw new FileOperationException(
                FileOperationException.Reason.ALREADY_EXISTS, path.relativePath());
          }
          try {
            Files.createDirectory(path.absolutePath());
          } catch (IOException failure) {
            throw new FileOperationException(
                FileOperationException.Reason.IO,
                path.relativePath(),
                failure.getClass().getSimpleName());
          }
          return stat(path.relativePath());
        },
        path.relativePath());
  }

  /** Atomically replaces an existing file only when the caller supplies its current revision. */
  public FileInfo write(String requestPath, byte[] content, String expectedRevision)
      throws FileOperationException {
    Objects.requireNonNull(content, "content");
    SandboxPath path = resolve(requestPath, SandboxAccessMode.WRITE);
    return withLocks(
        () -> {
          PathState existing = writePreconditions(path, expectedRevision);
          if (existing != null) {
            captureSingle(path.relativePath(), "before-overwrite", existing);
          }
          CommittedWrite committed =
              writeTempAndCommit(
                  path,
                  new ByteArrayInputStream(content),
                  content.length,
                  limits.maxFileSizeBytes(),
                  existing != null,
                  existing == null ? null : existing.attributes,
                  expectedRevision);
          return stat(path.relativePath());
        },
        path.relativePath());
  }

  /** Streams an upload into a same-directory temporary file before the atomic commit. */
  public FileInfo upload(
      String requestPath, InputStream content, long declaredSize, String expectedRevision)
      throws FileOperationException {
    Objects.requireNonNull(content, "content");
    if (declaredSize < -1L) {
      throw new FileOperationException(
          FileOperationException.Reason.INVALID_RANGE, requestPath, Long.valueOf(declaredSize));
    }
    SandboxPath path = resolve(requestPath, SandboxAccessMode.WRITE);
    return withLocks(
        () -> {
          PathState existing = writePreconditions(path, expectedRevision);
          if (existing != null) {
            captureSingle(path.relativePath(), "before-overwrite", existing);
          }
          CommittedWrite committed =
              writeTempAndCommit(
                  path,
                  content,
                  declaredSize,
                  Math.min(limits.maxUploadBytes(), limits.maxFileSizeBytes()),
                  existing != null,
                  existing == null ? null : existing.attributes,
                  expectedRevision);
          return stat(path.relativePath());
        },
        path.relativePath());
  }

  /** Moves a file or directory. Overwriting the destination requires an explicit flag. */
  public FileInfo move(
      String sourcePath, String destinationPath, boolean overwrite, String expectedRevision)
      throws FileOperationException {
    SandboxPath source = resolve(sourcePath, SandboxAccessMode.WRITE);
    SandboxPath destination = resolve(destinationPath, SandboxAccessMode.WRITE);
    return withLocks(
        () -> {
          PathState sourceState = requireState(source);
          if (sourceState.revision != null) {
            checkRevision(source.relativePath(), expectedRevision, sourceState.revision);
          }
          if (destination.absolutePath().startsWith(source.absolutePath())) {
            throw new FileOperationException(
                FileOperationException.Reason.INVALID_OPERATION, sourcePath);
          }
          PathState targetState = attributes(destination);
          if (targetState != null) {
            verify(destination, targetState.attributes);
            if (!overwrite) {
              throw new FileOperationException(
                  FileOperationException.Reason.ALREADY_EXISTS, destination.relativePath());
            }
          }
          List<PathState> sourceTree = collectTree(source);
          List<SnapshotEntry> snapshot = new ArrayList<SnapshotEntry>();
          for (PathState entry : sourceTree) {
            snapshot.add(toSnapshotEntry(entry));
          }
          if (targetState != null) {
            List<PathState> targetTree = collectTree(targetState);
            for (PathState entry : targetTree) {
              snapshot.add(toSnapshotEntry(entry));
            }
          }
          capture(destination.relativePath(), "before-move", snapshot);
          for (PathState entry : sourceTree) {
            verifyUnchanged(entry, SandboxAccessMode.WRITE);
          }
          if (targetState != null) {
            verifyUnchanged(targetState, SandboxAccessMode.WRITE);
          }
          try {
            if (overwrite) {
              Files.move(
                  source.absolutePath(),
                  destination.absolutePath(),
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);
            } else {
              Files.move(
                  source.absolutePath(),
                  destination.absolutePath(),
                  StandardCopyOption.ATOMIC_MOVE);
            }
          } catch (AtomicMoveNotSupportedException unsupported) {
            throw new FileOperationException(
                FileOperationException.Reason.ATOMIC_MOVE_UNSUPPORTED, unsupported);
          } catch (IOException failure) {
            throw new FileOperationException(
                FileOperationException.Reason.IO, sourcePath, failure.getClass().getSimpleName());
          }
          fsyncParent(source.absolutePath());
          fsyncParent(destination.absolutePath());
          return stat(destinationPath);
        },
        source.relativePath(),
        destination.relativePath());
  }

  /** Deletes a file or explicitly confirmed directory tree after a successful snapshot capture. */
  public int delete(String requestPath, DeleteRequest request) throws FileOperationException {
    Objects.requireNonNull(request, "request");
    SandboxPath path = resolve(requestPath, SandboxAccessMode.WRITE);
    return withLocks(
        () -> {
          PathState state = requireState(path);
          if (state.revision != null) {
            checkRevision(path.relativePath(), request.expectedRevision(), state.revision);
          }
          List<PathState> tree = collectTree(new PathState(path, state.attributes, state.revision));
          if (!request.recursive() && tree.size() > 1) {
            throw new FileOperationException(
                FileOperationException.Reason.DIRECTORY_NOT_EMPTY, path.relativePath());
          }
          if (tree.size() > limits.maxDeleteEntries()) {
            throw new FileOperationException(
                FileOperationException.Reason.DELETE_LIMIT,
                path.relativePath(),
                Integer.valueOf(tree.size()),
                Integer.valueOf(limits.maxDeleteEntries()));
          }
          if (request.recursive()) {
            Long confirmed = request.expectedEntryCount();
            if (confirmed == null || confirmed.longValue() != tree.size()) {
              throw new FileOperationException(
                  FileOperationException.Reason.DELETE_CONFIRMATION,
                  path.relativePath(),
                  Integer.valueOf(tree.size()));
            }
          }
          List<SnapshotEntry> snapshot = new ArrayList<SnapshotEntry>();
          for (PathState entry : tree) {
            snapshot.add(toSnapshotEntry(entry));
          }
          capture(path.relativePath(), "before-delete", snapshot);
          List<PathState> ordered = new ArrayList<PathState>(tree);
          Collections.sort(ordered, DEEPEST_FIRST);
          for (PathState entry : ordered) {
            verifyUnchanged(entry, SandboxAccessMode.WRITE);
          }
          for (PathState entry : ordered) {
            try {
              Files.delete(entry.path.absolutePath());
            } catch (IOException failure) {
              throw new FileOperationException(
                  FileOperationException.Reason.IO,
                  entry.path.relativePath(),
                  failure.getClass().getSimpleName());
            }
          }
          fsyncParent(path.absolutePath());
          return ordered.size();
        },
        path.relativePath());
  }

  private static final Comparator<PathState> DEEPEST_FIRST =
      new Comparator<PathState>() {
        @Override
        public int compare(PathState left, PathState right) {
          int depth = depth(right.path.relativePath()) - depth(left.path.relativePath());
          if (depth != 0) {
            return depth;
          }
          return right.path.relativePath().compareTo(left.path.relativePath());
        }

        private int depth(String relative) {
          if (relative.isEmpty()) {
            return 0;
          }
          int result = 1;
          for (int i = 0; i < relative.length(); i++) {
            if (relative.charAt(i) == '/') {
              result++;
            }
          }
          return result;
        }
      };

  private PathState writePreconditions(SandboxPath path, String expectedRevision)
      throws FileOperationException {
    requireWritableFileTarget(path);
    PathState existing = attributes(path);
    if (existing == null) {
      if (expectedRevision != null) {
        throw new FileOperationException(
            FileOperationException.Reason.REVISION_CONFLICT,
            path.relativePath(),
            expectedRevision,
            "absent");
      }
      return null;
    }
    verify(path, existing.attributes);
    if (existing.revision == null) {
      throw new FileOperationException(
          FileOperationException.Reason.INVALID_OPERATION, path.relativePath());
    }
    if (expectedRevision == null) {
      throw new FileOperationException(
          FileOperationException.Reason.PRECONDITION_REQUIRED, path.relativePath());
    }
    checkRevision(path.relativePath(), expectedRevision, existing.revision);
    return existing;
  }

  private void requireWritableFileTarget(SandboxPath path) throws FileOperationException {
    if (path.relativePath().isEmpty()) {
      throw new FileOperationException(FileOperationException.Reason.INVALID_OPERATION, "");
    }
  }

  private PathState requireState(SandboxPath path) throws FileOperationException {
    BasicFileAttributes attributes = rawAttributes(path);
    if (attributes == null) {
      throw new FileOperationException(
          FileOperationException.Reason.NOT_FOUND, path.relativePath());
    }
    verify(path, attributes);
    String revision = null;
    if (attributes.isRegularFile()) {
      revision = revisionOf(path);
    }
    return new PathState(path, attributes, revision);
  }

  private PathState requireFileState(SandboxPath path) throws FileOperationException {
    PathState state = requireState(path);
    if (!state.attributes.isRegularFile()) {
      throw new FileOperationException(
          FileOperationException.Reason.INVALID_OPERATION, path.relativePath());
    }
    return state;
  }

  private static FileInfo toInfo(PathState state) {
    if (state.attributes.isDirectory()) {
      return FileInfo.directory(state.path.relativePath(), modifiedMillis(state.attributes));
    }
    return FileInfo.file(
        state.path.relativePath(),
        state.attributes.size(),
        state.revision,
        modifiedMillis(state.attributes));
  }

  private static SnapshotEntry toSnapshotEntry(PathState state) throws FileOperationException {
    if (state.attributes.isDirectory()) {
      return SnapshotEntry.directory(state.path.relativePath());
    }
    return SnapshotEntry.file(state.path.relativePath(), readSnapshotBytes(state), state.revision);
  }

  private static byte[] readSnapshotBytes(PathState state) throws FileOperationException {
    long size = state.attributes.size();
    if (size > Integer.MAX_VALUE) {
      throw new FileOperationException(
          FileOperationException.Reason.TOO_LARGE,
          state.path.relativePath(),
          Long.valueOf(Integer.MAX_VALUE));
    }
    return readAll(state.path, size);
  }

  private List<PathState> collectTree(SandboxPath root) throws FileOperationException {
    PathState rootState = requireState(root);
    List<PathState> result = new ArrayList<PathState>();
    result.add(rootState);
    if (!rootState.attributes.isDirectory()) {
      return result;
    }
    collectChildren(root, result);
    Collections.sort(result, PATH_STATE_ORDER);
    return result;
  }

  private List<PathState> collectTree(PathState rootState) throws FileOperationException {
    List<PathState> result = new ArrayList<PathState>();
    result.add(rootState);
    if (!rootState.attributes.isDirectory()) {
      return result;
    }
    collectChildren(rootState.path, result);
    Collections.sort(result, PATH_STATE_ORDER);
    return result;
  }

  private void collectChildren(SandboxPath directory, List<PathState> result)
      throws FileOperationException {
    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(directory.absolutePath());
      try {
        for (Path child : stream) {
          String relative = join(directory.relativePath(), child.getFileName().toString());
          SandboxPath resolved;
          try {
            resolved = resolver.resolve(relative, SandboxAccessMode.READ);
          } catch (SandboxRejectionException rejected) {
            throw FileOperationException.fromSandbox(rejected, relative);
          }
          BasicFileAttributes attributes = rawAttributes(resolved);
          if (attributes == null || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new FileOperationException(
                FileOperationException.Reason.PATH_REJECTED, relative, "changed");
          }
          verify(resolved, attributes);
          String revision = null;
          if (attributes.isRegularFile()) {
            if (attributes.size() > Integer.MAX_VALUE) {
              throw new FileOperationException(
                  FileOperationException.Reason.TOO_LARGE,
                  relative,
                  Long.valueOf(Integer.MAX_VALUE));
            }
            revision = revisionOf(resolved);
          }
          result.add(new PathState(resolved, attributes, revision));
          if (result.size() > limits.maxDeleteEntries()) {
            throw new FileOperationException(
                FileOperationException.Reason.DELETE_LIMIT,
                directory.relativePath(),
                Integer.valueOf(result.size()),
                Integer.valueOf(limits.maxDeleteEntries()));
          }
          if (attributes.isDirectory()) {
            collectChildren(resolved, result);
          }
        }
      } finally {
        stream.close();
      }
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.IO,
          directory.relativePath(),
          failure.getClass().getSimpleName());
    }
  }

  private void capture(String rootPath, String reason, List<SnapshotEntry> entries)
      throws FileOperationException {
    if (snapshotHook == null) {
      throw new FileOperationException(FileOperationException.Reason.SNAPSHOT_REQUIRED, rootPath);
    }
    try {
      snapshotHook.capture(new SnapshotRequest(resolver.root().toString(), reason, entries));
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.SNAPSHOT_FAILED,
          rootPath,
          failure.getClass().getSimpleName());
    }
  }

  private void captureSingle(String relativePath, String reason, PathState state)
      throws FileOperationException {
    List<SnapshotEntry> entries = new ArrayList<SnapshotEntry>();
    entries.add(toSnapshotEntry(state));
    capture(relativePath, reason, entries);
  }

  private void verifyUnchanged(PathState expected, SandboxAccessMode mode)
      throws FileOperationException {
    SandboxPath path = resolve(expected.path.relativePath(), mode);
    BasicFileAttributes current = rawAttributes(path);
    if (current == null || !sameIdentity(expected.attributes, current)) {
      throw new FileOperationException(
          FileOperationException.Reason.PATH_REJECTED, expected.path.relativePath(), "changed");
    }
    verify(path, current);
    if (expected.revision != null && current.isRegularFile()) {
      String now = revisionOf(path);
      if (!expected.revision.equals(now)) {
        throw new FileOperationException(
            FileOperationException.Reason.PATH_REJECTED, expected.path.relativePath(), "changed");
      }
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

  private CommittedWrite writeTempAndCommit(
      SandboxPath path,
      InputStream source,
      long declaredSize,
      long maximumBytes,
      boolean replaceExisting,
      BasicFileAttributes before,
      String expectedRevision)
      throws FileOperationException {
    if (declaredSize > maximumBytes) {
      throw new FileOperationException(
          FileOperationException.Reason.TOO_LARGE, path.relativePath(), Long.valueOf(maximumBytes));
    }
    Path parent = path.absolutePath().getParent();
    if (parent == null) {
      throw new FileOperationException(
          FileOperationException.Reason.INVALID_OPERATION, path.relativePath());
    }
    Path temporary = null;
    try {
      temporary = Files.createTempFile(parent, TEMP_PREFIX, TEMP_SUFFIX);
      restrictPermissions(temporary);
      MessageDigest digest = digest();
      long written = 0L;
      FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        while (true) {
          int read = source.read(buffer);
          if (read < 0) {
            break;
          }
          if (read == 0) {
            continue;
          }
          if (written > maximumBytes - read) {
            throw new FileOperationException(
                FileOperationException.Reason.TOO_LARGE,
                path.relativePath(),
                Long.valueOf(maximumBytes));
          }
          digest.update(buffer, 0, read);
          ByteBuffer wrapped = ByteBuffer.wrap(buffer, 0, read);
          while (wrapped.hasRemaining()) {
            channel.write(wrapped);
          }
          written += read;
        }
        channel.force(true);
      } finally {
        channel.close();
      }
      recheckTarget(path, replaceExisting, before, expectedRevision);
      crashPoint.beforeCommit();
      try {
        if (replaceExisting) {
          Files.move(
              temporary,
              path.absolutePath(),
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } else {
          Files.move(temporary, path.absolutePath(), StandardCopyOption.ATOMIC_MOVE);
        }
      } catch (AtomicMoveNotSupportedException unsupported) {
        throw new FileOperationException(
            FileOperationException.Reason.ATOMIC_MOVE_UNSUPPORTED, unsupported);
      }
      temporary = null;
      fsyncParent(path.absolutePath());
      crashPoint.afterCommit();
      return new CommittedWrite(FileRevision.fromDigest(digest), written);
    } catch (FileOperationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.IO,
          path.relativePath(),
          failure.getClass().getSimpleName());
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // Best-effort cleanup; a crash can also leave a temp file for startup cleanup.
        }
      }
    }
  }

  private void recheckTarget(
      SandboxPath path,
      boolean replaceExisting,
      BasicFileAttributes before,
      String expectedRevision)
      throws FileOperationException {
    BasicFileAttributes current = rawAttributes(path);
    if (!replaceExisting) {
      if (current != null) {
        throw new FileOperationException(
            FileOperationException.Reason.ALREADY_EXISTS, path.relativePath());
      }
      return;
    }
    if (current == null) {
      throw new FileOperationException(
          FileOperationException.Reason.PATH_REJECTED, path.relativePath(), "removed");
    }
    verify(path, current);
    if (expectedRevision != null) {
      String now = revisionOf(path);
      if (!expectedRevision.equals(now)) {
        throw new FileOperationException(
            FileOperationException.Reason.REVISION_CONFLICT,
            path.relativePath(),
            expectedRevision,
            now);
      }
    } else if (before != null && !sameIdentity(before, current)) {
      throw new FileOperationException(
          FileOperationException.Reason.PATH_REJECTED, path.relativePath(), "changed");
    }
  }

  private static byte[] readAll(SandboxPath path, long expectedSize) throws FileOperationException {
    if (expectedSize > Integer.MAX_VALUE) {
      throw new FileOperationException(
          FileOperationException.Reason.TOO_LARGE,
          path.relativePath(),
          Long.valueOf(Integer.MAX_VALUE));
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream((int) expectedSize);
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    FileChannel channel = null;
    try {
      channel =
          FileChannel.open(path.absolutePath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
      while (true) {
        int read = channel.read(ByteBuffer.wrap(buffer));
        if (read < 0) {
          break;
        }
        output.write(buffer, 0, read);
        if (output.size() > expectedSize) {
          throw new FileOperationException(
              FileOperationException.Reason.PATH_REJECTED, path.relativePath(), "changed");
        }
      }
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.IO,
          path.relativePath(),
          failure.getClass().getSimpleName());
    } finally {
      if (channel != null) {
        try {
          channel.close();
        } catch (IOException ignored) {
          // The read result is already finalized.
        }
      }
    }
    return output.toByteArray();
  }

  private BasicFileAttributes rawAttributes(SandboxPath path) throws FileOperationException {
    try {
      return Files.readAttributes(
          path.absolutePath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (NoSuchFileException missing) {
      return null;
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.IO,
          path.relativePath(),
          failure.getClass().getSimpleName());
    }
  }

  private PathState attributes(SandboxPath path) throws FileOperationException {
    BasicFileAttributes attributes = rawAttributes(path);
    if (attributes == null) {
      return null;
    }
    String revision = null;
    if (attributes.isRegularFile()) {
      revision = revisionOf(path);
    }
    return new PathState(path, attributes, revision);
  }

  private void verify(SandboxPath path, BasicFileAttributes attributes)
      throws FileOperationException {
    if (attributes.isSymbolicLink() || attributes.isOther()) {
      throw new FileOperationException(
          FileOperationException.Reason.PATH_REJECTED, path.relativePath(), "link");
    }
    if (path.fileKey() != null && !Objects.equals(path.fileKey(), attributes.fileKey())) {
      throw new FileOperationException(
          FileOperationException.Reason.PATH_REJECTED, path.relativePath(), "changed");
    }
    try {
      resolver.verifyOpened(path, path.absolutePath());
    } catch (SandboxRejectionException rejection) {
      throw FileOperationException.fromSandbox(rejection, path.relativePath());
    }
  }

  private void checkRevision(String requestPath, String expected, String actual)
      throws FileOperationException {
    if (expected == null || expected.equals(actual)) {
      return;
    }
    throw new FileOperationException(
        FileOperationException.Reason.REVISION_CONFLICT,
        requestPath,
        expected,
        actual == null ? "absent" : actual);
  }

  private SandboxPath resolve(String requestPath, SandboxAccessMode mode)
      throws FileOperationException {
    try {
      return resolver.resolve(requestPath, mode);
    } catch (SandboxRejectionException rejection) {
      throw FileOperationException.fromSandbox(rejection, String.valueOf(requestPath));
    }
  }

  private <T> T withLocks(LockedBody<T> body, String... requestPaths)
      throws FileOperationException {
    int[] indexes = new int[requestPaths.length];
    for (int i = 0; i < requestPaths.length; i++) {
      indexes[i] = stripe(requestPaths[i]);
    }
    Arrays.sort(indexes);
    return withIndexes(indexes, 0, body);
  }

  private <T> T withIndexes(int[] indexes, int position, LockedBody<T> body)
      throws FileOperationException {
    while (position < indexes.length
        && position > 0
        && indexes[position] == indexes[position - 1]) {
      position++;
    }
    if (position >= indexes.length) {
      return body.run();
    }
    synchronized (locks[indexes[position]]) {
      return withIndexes(indexes, position + 1, body);
    }
  }

  private static int stripe(String relativePath) {
    return Math.abs(relativePath.hashCode() % LOCK_STRIPES);
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

  private static String revisionOf(SandboxPath path) throws FileOperationException {
    try {
      return FileRevision.of(path.absolutePath());
    } catch (IOException failure) {
      throw new FileOperationException(
          FileOperationException.Reason.IO,
          path.relativePath(),
          failure.getClass().getSimpleName());
    }
  }

  private static long modifiedMillis(BasicFileAttributes attributes) {
    return attributes.lastModifiedTime().toMillis();
  }

  private static String join(String parent, String child) {
    return parent.isEmpty() ? child : parent + "/" + child;
  }

  @FunctionalInterface
  private interface LockedBody<T> {
    T run() throws FileOperationException;
  }

  private static final class CommittedWrite {
    private final String revision;
    private final long size;

    private CommittedWrite(String revision, long size) {
      this.revision = revision;
      this.size = size;
    }
  }

  private static final class PathState {
    private final SandboxPath path;
    private final BasicFileAttributes attributes;
    private final String revision;

    private PathState(SandboxPath path, BasicFileAttributes attributes, String revision) {
      this.path = path;
      this.attributes = attributes;
      this.revision = revision;
    }
  }
}
