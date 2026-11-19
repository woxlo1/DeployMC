package io.github.woxlo1.deploymc.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class TestHashes {

  private TestHashes() {}

  static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(Character.forDigit((value >> 4) & 0xf, 16));
      result.append(Character.forDigit(value & 0xf, 16));
    }
    return result.toString();
  }
}
