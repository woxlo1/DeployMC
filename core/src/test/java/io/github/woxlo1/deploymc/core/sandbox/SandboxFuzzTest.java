package io.github.woxlo1.deploymc.core.sandbox;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Secure-random fuzz coverage for the resolver, also used by the scheduled CI job. */
class SandboxFuzzTest {

  private static final char[] ALPHABET =
      ("abcXYZ019.-_/\\:%?*<>|\"' ~ CON NUL"
              + "\u0000\u0001\r\n\t\u007f"
              + "\u00e9\u0301\uff0e\uff0f\u3002\ud800\u202e")
          .toCharArray();

  @TempDir Path tempDir;

  @Test
  void fuzzNeverEscapesRoot() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);
    SecureRandom random = new SecureRandom();
    int accepted = 0;

    for (int i = 0; i < 5000; i++) {
      String candidate = randomString(random);
      SandboxAccessMode mode = SandboxAccessMode.values()[random.nextInt(3)];
      try {
        SandboxPath resolved = resolver.resolve(candidate, mode);
        assertNotNull(resolved);
        // Containment is against the canonical root, not the possibly different configured path.
        assertTrue(
            resolved.absolutePath().startsWith(resolver.root()),
            "Escaped root: [" + candidate + "]");
        assertTrue(resolved.belongsTo(resolver.rootIdentity()));
        accepted++;
      } catch (SandboxRejectionException expected) {
        assertNotNull(expected.code());
      } catch (RuntimeException unexpected) {
        fail("Unexpected runtime failure for [" + candidate + "]: " + unexpected);
      }
    }

    assertTrue(accepted > 0, "Fuzz corpus should contain some acceptable paths");
  }

  private static String randomString(SecureRandom random) {
    int length = random.nextInt(80);
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    }
    return builder.toString();
  }
}
