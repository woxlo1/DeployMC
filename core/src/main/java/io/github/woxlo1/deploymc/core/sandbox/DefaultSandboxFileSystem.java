package io.github.woxlo1.deploymc.core.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** {@link SandboxFileSystem} backed by the host file system. */
public final class DefaultSandboxFileSystem implements SandboxFileSystem {

  public static final DefaultSandboxFileSystem INSTANCE = new DefaultSandboxFileSystem();

  public DefaultSandboxFileSystem() {}

  @Override
  public EntryAttributes readAttributes(Path path) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    return new EntryAttributes(
        attributes.isSymbolicLink(),
        attributes.isOther(),
        attributes.isDirectory(),
        attributes.fileKey());
  }

  @Override
  public Path toRealPath(Path path) throws IOException {
    return path.toRealPath();
  }
}
