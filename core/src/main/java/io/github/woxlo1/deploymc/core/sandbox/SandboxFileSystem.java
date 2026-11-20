package io.github.woxlo1.deploymc.core.sandbox;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The small file-system surface the resolver needs. Injecting it keeps the resolver pure and lets
 * tests exercise identical logic on every host.
 */
public interface SandboxFileSystem {

  /** Attributes of the entry itself, without following a link. */
  EntryAttributes readAttributes(Path path) throws IOException;

  /** Canonical path with links resolved. */
  Path toRealPath(Path path) throws IOException;

  /** Minimal entry metadata used for containment checks. */
  final class EntryAttributes {
    private final boolean symbolicLink;
    private final boolean other;
    private final boolean directory;
    private final Object fileKey;

    public EntryAttributes(boolean symbolicLink, boolean other, boolean directory, Object fileKey) {
      this.symbolicLink = symbolicLink;
      this.other = other;
      this.directory = directory;
      this.fileKey = fileKey;
    }

    public boolean symbolicLink() {
      return symbolicLink;
    }

    /** Windows junctions and other reparse points are reported as "other" by the JDK. */
    public boolean other() {
      return other;
    }

    public boolean directory() {
      return directory;
    }

    public Object fileKey() {
      return fileKey;
    }
  }
}
