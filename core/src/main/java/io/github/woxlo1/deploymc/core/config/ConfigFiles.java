package io.github.woxlo1.deploymc.core.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;

/** Host-local file helpers for the administrator-controlled configuration file. */
final class ConfigFiles {

  private static final DateTimeFormatter BACKUP_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private ConfigFiles() {}

  static String readUtf8(Path path) throws ConfigException {
    try {
      return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.read", path), exception);
    }
  }

  static void writeUtf8(Path path, String content, boolean replaceExisting) throws ConfigException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent == null) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.write", path.toString()));
    }
    Path temporary = null;
    try {
      Files.createDirectories(parent);
      temporary = Files.createTempFile(parent, ".deploymc-config-", ".tmp");
      restrictPermissions(temporary);
      FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
        channel.force(true);
      } finally {
        channel.close();
      }
      move(temporary, path, replaceExisting);
      temporary = null;
    } catch (IOException exception) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.write", path.toString()), exception);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // Best effort cleanup; the original failure is already reported.
        }
      }
    }
  }

  static Path backup(Path source, String content, Clock clock) throws ConfigException {
    Path parent = source.toAbsolutePath().getParent();
    if (parent == null) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.write", source.toString()));
    }
    String stamp = BACKUP_TIME.format(clock.instant());
    Path candidate = source.resolveSibling(source.getFileName() + ".backup-" + stamp);
    int suffix = 1;
    while (Files.exists(candidate)) {
      candidate = source.resolveSibling(source.getFileName() + ".backup-" + stamp + "-" + suffix);
      suffix++;
    }
    writeUtf8(candidate, content, false);
    return candidate;
  }

  private static void move(Path temporary, Path target, boolean replaceExisting)
      throws IOException {
    if (replaceExisting) {
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return;
      } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return;
      }
    }
    try {
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, target);
    }
  }

  private static void restrictPermissions(Path path) {
    try {
      Files.setPosixFilePermissions(
          path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Windows and some filesystems do not expose POSIX permissions.
    }
  }
}
