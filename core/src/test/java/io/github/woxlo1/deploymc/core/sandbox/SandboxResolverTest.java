package io.github.woxlo1.deploymc.core.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior tests that complement the large traversal corpus. */
class SandboxResolverTest {

  @TempDir Path tempDir;

  @Test
  void resolvesRelativePathsAndNormalizesSeparators() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    SandboxPath path = resolver.resolve("nested\\dir/./plugin.yml", SandboxAccessMode.WRITE);

    assertEquals("nested/dir/plugin.yml", path.relativePath());
    // Canonicalization may change the configured path, especially on Windows hosts.
    assertEquals(resolver.root().resolve("nested/dir/plugin.yml"), path.absolutePath());
    assertEquals(SandboxAccessMode.WRITE, path.mode());
    assertTrue(path.belongsTo(resolver.rootIdentity()));
  }

  @Test
  void emptyAndDotMeanRoot() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    assertEquals("", resolver.resolve("", SandboxAccessMode.READ).relativePath());
    assertEquals("", resolver.resolve(".", SandboxAccessMode.READ).relativePath());
    assertEquals(
        SandboxErrorCode.SANDBOX_ABSOLUTE,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("/", SandboxAccessMode.READ))
            .code());
  }

  @Test
  void rejectsNullAbsoluteAndTraversalPaths() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    assertEquals(
        SandboxErrorCode.SANDBOX_EMPTY,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve(null, SandboxAccessMode.READ))
            .code());
    for (String absolute :
        Arrays.asList("/etc/passwd", "\\Windows", "C:\\tmp", "\\\\host\\share")) {
      assertEquals(
          SandboxErrorCode.SANDBOX_ABSOLUTE,
          assertThrows(
                  SandboxRejectionException.class,
                  () -> resolver.resolve(absolute, SandboxAccessMode.READ))
              .code());
    }
    for (String traversal : Arrays.asList("..", "../secret", "a\\..\\secret", "....//x")) {
      assertThrows(
          SandboxRejectionException.class,
          () -> resolver.resolve(traversal, SandboxAccessMode.READ));
    }
  }

  @Test
  void rejectsInvalidNamesAndEncodedPayloads() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    for (String invalid :
        Arrays.asList(
            "CON",
            "NUL.txt",
            "COM1",
            "file.txt:stream",
            "trailing.",
            "trailing ",
            "file~1",
            "file<bad",
            "%2e%2e/config",
            "..%2fconfig",
            "\uff0e\uff0e/config")) {
      assertThrows(
          SandboxRejectionException.class,
          () -> resolver.resolve(invalid, SandboxAccessMode.WRITE),
          "Must reject: " + invalid);
    }
  }

  @Test
  void rejectsNulControlInvalidUnicodeAndOverlongPaths() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    assertEquals(
        SandboxErrorCode.SANDBOX_NUL,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("a\u0000b", SandboxAccessMode.READ))
            .code());
    assertEquals(
        SandboxErrorCode.SANDBOX_CONTROL_CHAR,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("a\rb", SandboxAccessMode.READ))
            .code());
    assertEquals(
        SandboxErrorCode.SANDBOX_INVALID_UNICODE,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("a\ud800b", SandboxAccessMode.READ))
            .code());
    assertEquals(
        SandboxErrorCode.SANDBOX_TOO_LONG,
        assertThrows(
                SandboxRejectionException.class,
                () ->
                    resolver.resolve(
                        new String(new char[5000]).replace('\0', 'a'), SandboxAccessMode.READ))
            .code());
  }

  @Test
  void enforcesProtectedAndDenyPatternsCaseInsensitively() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxPolicy policy =
        new SandboxPolicy(
            SandboxPolicy.DEFAULT_MAX_PATH_LENGTH,
            SandboxPolicy.DEFAULT_PROTECTED_PATTERNS,
            Collections.singletonList("secret*/**"),
            false);
    SandboxResolver resolver = new SandboxResolver(root, policy);

    assertEquals(
        SandboxErrorCode.SANDBOX_PROTECTED,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("DeployMC/config.yml", SandboxAccessMode.WRITE))
            .code());
    assertEquals(
        SandboxErrorCode.SANDBOX_PROTECTED,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve(".DEPLOYMC/history", SandboxAccessMode.READ))
            .code());
    assertEquals(
        SandboxErrorCode.SANDBOX_DENIED,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("secret-data/key.txt", SandboxAccessMode.READ))
            .code());
    assertNotNull(resolver.resolve("plugins/other.yml", SandboxAccessMode.READ));
  }

  @Test
  void normalizesUnicodeToNfc() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);
    String nfd = Normalizer.normalize("caf\u00e9/file.yml", Normalizer.Form.NFD);

    SandboxPath resolved = resolver.resolve(nfd, SandboxAccessMode.CREATE);

    assertEquals("caf\u00e9/file.yml", resolved.relativePath());
  }

  @Test
  void rejectsSymlinksByDefaultAndRechecksFollowedLinks() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    Path outside = Files.createDirectories(tempDir.resolve("outside"));
    Path inside = Files.createDirectories(root.resolve("inside"));
    Files.write(outside.resolve("secret.txt"), "secret".getBytes("UTF-8"));
    Path outsideLink = root.resolve("outside-link");
    Path insideLink = root.resolve("inside-link");
    try {
      Files.createSymbolicLink(outsideLink, outside);
      Files.createSymbolicLink(insideLink, inside);
    } catch (IOException | UnsupportedOperationException unsupported) {
      assumeTrue(false, "This host cannot create symbolic links: " + unsupported.getMessage());
      return;
    }

    SandboxResolver strict = new SandboxResolver(root);
    assertEquals(
        SandboxErrorCode.SANDBOX_SYMLINK,
        assertThrows(
                SandboxRejectionException.class,
                () -> strict.resolve("inside-link/file.txt", SandboxAccessMode.READ))
            .code());

    SandboxPolicy following =
        new SandboxPolicy(
            SandboxPolicy.DEFAULT_MAX_PATH_LENGTH,
            SandboxPolicy.DEFAULT_PROTECTED_PATTERNS,
            Collections.<String>emptyList(),
            true);
    SandboxResolver permissive = new SandboxResolver(root, following);
    assertEquals(
        SandboxErrorCode.SANDBOX_OUTSIDE_ROOT,
        assertThrows(
                SandboxRejectionException.class,
                () -> permissive.resolve("outside-link/secret.txt", SandboxAccessMode.READ))
            .code());
    assertNotNull(permissive.resolve("inside-link/file.txt", SandboxAccessMode.READ));
  }

  @Test
  void verifiesOpenedPathsAndRejectsForeignOnes() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    Path file = root.resolve("plugin.yml");
    Files.write(file, "name: test".getBytes("UTF-8"));
    SandboxResolver resolver = new SandboxResolver(root);
    SandboxPath resolved = resolver.resolve("plugin.yml", SandboxAccessMode.READ);

    resolver.verifyOpened(resolved, file);

    SandboxResolver other = new SandboxResolver(root);
    SandboxPath foreign = other.resolve("plugin.yml", SandboxAccessMode.READ);
    assertEquals(
        SandboxErrorCode.SANDBOX_OUTSIDE_ROOT,
        assertThrows(SandboxRejectionException.class, () -> resolver.verifyOpened(foreign, file))
            .code());
  }

  @Test
  void rejectsRetargetedRootBeforeResolving() throws Exception {
    Path configured = Paths.get("/deploymc-configured");
    SwitchingFileSystem fileSystem =
        new SwitchingFileSystem(configured, Paths.get("/deploymc-real"));
    SandboxResolver resolver = new SandboxResolver(configured, new SandboxPolicy(), fileSystem);

    assertNotNull(resolver.resolve("plugin.yml", SandboxAccessMode.READ));

    fileSystem.retarget(Paths.get("/deploymc-elsewhere"));
    assertEquals(
        SandboxErrorCode.SANDBOX_OUTSIDE_ROOT,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.resolve("plugin.yml", SandboxAccessMode.READ))
            .code());
  }

  @Test
  void rejectsOpenedObjectWhoseFileKeyChanged() throws Exception {
    Path root = Paths.get("/deploymc-root");
    KeyedFileSystem fileSystem = new KeyedFileSystem(root);
    SandboxResolver resolver = new SandboxResolver(root, new SandboxPolicy(), fileSystem);
    SandboxPath resolved = resolver.resolve("plugin.yml", SandboxAccessMode.READ);

    fileSystem.replaceFileKey();

    assertEquals(
        SandboxErrorCode.SANDBOX_FILE_CHANGED,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.verifyOpened(resolved, root.resolve("plugin.yml")))
            .code());
  }

  @Test
  void exposesLocalizedDiagnosticKeys() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    SandboxRejectionException rejection =
        assertThrows(
            SandboxRejectionException.class,
            () -> resolver.resolve("DeployMC/config.yml", SandboxAccessMode.WRITE));

    assertEquals("sandbox.error.protected", rejection.messageKey());
    assertTrue(rejection.parameters().contains("DeployMC/config.yml"));
    assertTrue(rejection.getMessage().contains("DeployMC"));
  }

  /** In-memory file system whose configured root can be retargeted after construction. */
  private static final class SwitchingFileSystem implements SandboxFileSystem {
    private final Path configured;
    private Path current;

    private SwitchingFileSystem(Path configured, Path current) {
      this.configured = configured;
      this.current = current;
    }

    private void retarget(Path next) {
      current = next;
    }

    @Override
    public EntryAttributes readAttributes(Path path) {
      return new SandboxFileSystem.EntryAttributes(false, false, true, path.toString());
    }

    @Override
    public Path toRealPath(Path path) {
      return configured.equals(path) ? current : path;
    }
  }

  /** In-memory file system that simulates a file replacement after validation. */
  private static final class KeyedFileSystem implements SandboxFileSystem {
    private final Path root;
    private Object fileKey = "key-before";

    private KeyedFileSystem(Path root) {
      this.root = root;
    }

    private void replaceFileKey() {
      fileKey = "key-after";
    }

    @Override
    public EntryAttributes readAttributes(Path path) {
      return new SandboxFileSystem.EntryAttributes(false, false, true, fileKey);
    }

    @Override
    public Path toRealPath(Path path) {
      return path;
    }
  }
}
