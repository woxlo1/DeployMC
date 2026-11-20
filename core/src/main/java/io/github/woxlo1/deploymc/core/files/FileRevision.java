package io.github.woxlo1.deploymc.core.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Computes the strong content revision used as the file ETag source. */
public final class FileRevision {

  private static final String PREFIX = "sha256:";
  private static final int BUFFER_SIZE = 8192;

  private FileRevision() {}

  public static String of(byte[] content) {
    if (content == null) {
      throw new IllegalArgumentException("content");
    }
    return PREFIX + hex(digest().digest(content));
  }

  public static String of(Path path) throws IOException {
    MessageDigest digest = digest();
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    FileChannel channel =
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    try {
      while (true) {
        buffer.clear();
        int read = channel.read(buffer);
        if (read < 0) {
          break;
        }
        buffer.flip();
        digest.update(buffer);
      }
    } finally {
      channel.close();
    }
    return PREFIX + hex(digest.digest());
  }

  public static String fromDigest(MessageDigest digest) {
    return PREFIX + hex(digest.digest());
  }

  public static boolean isValid(String revision) {
    if (revision == null || !revision.startsWith(PREFIX)) {
      return false;
    }
    String value = revision.substring(PREFIX.length());
    if (value.length() != 64) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
        return false;
      }
    }
    return true;
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
}
