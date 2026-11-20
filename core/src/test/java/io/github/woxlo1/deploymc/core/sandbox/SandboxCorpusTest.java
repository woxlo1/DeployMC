package io.github.woxlo1.deploymc.core.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the shared security corpus through the resolver on every host and access mode. */
class SandboxCorpusTest {

  @TempDir Path tempDir;

  @Test
  void rejectsEveryTraversalPayload() throws Exception {
    List<String> corpus = SandboxCorpus.lines("traversal-corpus.txt");
    assertTrue(corpus.size() >= 500, "Traversal corpus must contain several hundred entries");
    Path root = Files.createDirectories(tempDir.resolve("root"));
    SandboxResolver resolver = new SandboxResolver(root);

    for (String encoded : corpus) {
      String payload = SandboxCorpus.decode(encoded);
      for (SandboxAccessMode mode : SandboxAccessMode.values()) {
        assertThrows(
            SandboxRejectionException.class,
            () -> resolver.resolve(payload, mode),
            "Payload must be rejected: [" + payload + "] mode=" + mode);
      }
    }
  }

  @Test
  void acceptsEveryAllowedPath() throws Exception {
    List<String> allowed = SandboxCorpus.lines("allowed-paths.txt");
    assertTrue(allowed.size() >= 10, "Allowed corpus must contain ordinary paths");
    Path root = Files.createDirectories(tempDir.resolve("root"));
    SandboxResolver resolver = new SandboxResolver(root);

    for (String encoded : allowed) {
      String payload = SandboxCorpus.decode(encoded);
      for (SandboxAccessMode mode : SandboxAccessMode.values()) {
        SandboxPath resolved = resolver.resolve(payload, mode);
        assertNotNull(resolved);
        // Containment is against the canonical root, not the possibly different configured path.
        assertTrue(resolved.absolutePath().startsWith(resolver.root()));
        assertTrue(resolved.belongsTo(resolver.rootIdentity()));
        assertEquals(mode, resolved.mode());
      }
    }
  }

  @Test
  void corpusIsDecodeable() throws IOException {
    List<String> decoded =
        SandboxCorpus.lines("traversal-corpus.txt").stream()
            .map(SandboxCorpus::decode)
            .collect(java.util.stream.Collectors.toList());
    assertTrue(decoded.stream().anyMatch(value -> value.indexOf('\0') >= 0));
    assertTrue(decoded.stream().anyMatch(value -> value.indexOf('\\') >= 0));
    assertTrue(decoded.stream().anyMatch(value -> value.length() > 4096));
  }
}
