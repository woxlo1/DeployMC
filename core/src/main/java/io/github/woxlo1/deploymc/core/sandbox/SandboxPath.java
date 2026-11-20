package io.github.woxlo1.deploymc.core.sandbox;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A path that passed sandbox validation.
 *
 * <p>Instances are created only by {@link SandboxResolver}. Callers cannot build one from a raw
 * request string, so every file operation that accepts this type has a verified path.
 */
public final class SandboxPath {

  private final String relativePath;
  private final Path absolutePath;
  private final SandboxAccessMode mode;
  private final Object rootIdentity;
  private final Object fileKey;
  private final int hash;

  private SandboxPath(
      String relativePath,
      Path absolutePath,
      SandboxAccessMode mode,
      Object rootIdentity,
      Object fileKey) {
    this.relativePath = relativePath;
    this.absolutePath = absolutePath;
    this.mode = mode;
    this.rootIdentity = rootIdentity;
    this.fileKey = fileKey;
    this.hash =
        Objects.hash(
            relativePath, absolutePath, mode, System.identityHashCode(rootIdentity), fileKey);
  }

  static SandboxPath create(
      String relativePath,
      Path absolutePath,
      SandboxAccessMode mode,
      Object rootIdentity,
      Object fileKey) {
    return new SandboxPath(relativePath, absolutePath, mode, rootIdentity, fileKey);
  }

  /** NFC-normalized, slash-separated path relative to the sandbox root. Empty means the root. */
  public String relativePath() {
    return relativePath;
  }

  /** Absolute real path under the sandbox root. */
  public Path absolutePath() {
    return absolutePath;
  }

  public SandboxAccessMode mode() {
    return mode;
  }

  /** File key of the validated entry, or of its deepest existing ancestor for a new path. */
  public Object fileKey() {
    return fileKey;
  }

  /** True when this path was issued by the given resolver instance. */
  public boolean belongsTo(Object identity) {
    return rootIdentity == identity;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SandboxPath)) {
      return false;
    }
    SandboxPath that = (SandboxPath) other;
    return rootIdentity == that.rootIdentity
        && relativePath.equals(that.relativePath)
        && absolutePath.equals(that.absolutePath)
        && mode == that.mode
        && Objects.equals(fileKey, that.fileKey);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public String toString() {
    return "SandboxPath[" + relativePath + ", " + mode + "]";
  }
}
