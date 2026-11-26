package io.github.woxlo1.deploymc.core.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** SHA-256 chain helpers with the {@code sha256:} prefix required by the format. */
public final class AuditHash {

  public static final String ZERO =
      "sha256:0000000000000000000000000000000000000000000000000000000000000000";

  private static final Pattern VALID = Pattern.compile("^sha256:[0-9a-f]{64}$");

  private AuditHash() {}

  public static String hash(String canonicalJson) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(bytes.length * 2 + 7);
      result.append("sha256:");
      for (byte value : bytes) {
        result.append(Character.forDigit((value >> 4) & 0xf, 16));
        result.append(Character.forDigit(value & 0xf, 16));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }

  public static boolean valid(String value) {
    return value != null && VALID.matcher(value).matches();
  }
}
