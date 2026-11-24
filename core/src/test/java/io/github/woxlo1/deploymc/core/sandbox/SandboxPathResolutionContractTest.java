package io.github.woxlo1.deploymc.core.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PART 33.1 contract and regression checks for the real sandbox resolver. */
class SandboxPathResolutionContractTest {

  private static final String SPEC_PATH =
      "docs/development/DeployMC_Complete_Master_Development_Specification.md";

  @TempDir Path tempDir;

  @Test
  void rejectsMalformedRequestsBeforeTouchingAncestors() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    CountingFileSystem fileSystem = new CountingFileSystem(root);
    SandboxResolver resolver = new SandboxResolver(root, new SandboxPolicy(), fileSystem);
    fileSystem.reset();

    assertEquals(SandboxErrorCode.SANDBOX_EMPTY, reject(resolver, null));
    assertEquals(SandboxErrorCode.SANDBOX_NUL, reject(resolver, "a\u0000b"));
    assertEquals(SandboxErrorCode.SANDBOX_CONTROL_CHAR, reject(resolver, "a\rb"));
    assertEquals(SandboxErrorCode.SANDBOX_INVALID_UNICODE, reject(resolver, "a\ud800b"));
    assertEquals(SandboxErrorCode.SANDBOX_INVALID_UNICODE, reject(resolver, "a\u200eb"));
    assertEquals(SandboxErrorCode.SANDBOX_TOO_LONG, reject(resolver, repeat('a', 5000)));
    assertEquals(SandboxErrorCode.SANDBOX_ABSOLUTE, reject(resolver, "/etc/passwd"));
    assertEquals(SandboxErrorCode.SANDBOX_ABSOLUTE, reject(resolver, "C:\\tmp\\secret"));

    assertEquals(0, fileSystem.attributeReads());
  }

  @Test
  void treatsBothSeparatorsAndDotSegmentsIdentically() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);

    SandboxPath slash = resolver.resolve("nested/dir/./plugin.yml", SandboxAccessMode.WRITE);
    SandboxPath backslash = resolver.resolve("nested\\dir\\.\\plugin.yml", SandboxAccessMode.WRITE);

    assertEquals("nested/dir/plugin.yml", slash.relativePath());
    assertEquals(slash.relativePath(), backslash.relativePath());
    assertEquals(slash.absolutePath(), backslash.absolutePath());
    assertEquals(resolver.root().resolve("nested/dir/plugin.yml"), slash.absolutePath());
    assertEquals("", resolver.resolve("", SandboxAccessMode.READ).relativePath());
    assertEquals("", resolver.resolve(".", SandboxAccessMode.READ).relativePath());
    assertEquals(SandboxErrorCode.SANDBOX_TRAVERSAL, reject(resolver, ".."));
    assertEquals(SandboxErrorCode.SANDBOX_TRAVERSAL, reject(resolver, "a/../secret"));
  }

  @Test
  void appliesWindowsReservedAndInvalidNamesOnEveryHost() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxResolver resolver = new SandboxResolver(root);
    List<String> invalid =
        Arrays.asList(
            "CON",
            "NUL.txt",
            "COM1",
            "LPT9.txt",
            "AUX.yml",
            "CONIN$",
            "file:stream",
            "trailing.",
            "trailing ",
            "file~1",
            "file<bad",
            "file|bad",
            "file?bad",
            "file*bad",
            "file\"bad",
            "%2e%2e/config",
            "..%5csecret",
            "\uff0e\uff0e/config");

    for (String request : invalid) {
      SandboxErrorCode code = reject(resolver, request);
      assertTrue(
          code == SandboxErrorCode.SANDBOX_INVALID_NAME
              || code == SandboxErrorCode.SANDBOX_TRAVERSAL,
          "Must reject " + request + " but got " + code);
    }
    assertEquals(SandboxErrorCode.SANDBOX_INVALID_NAME, reject(resolver, "CON"));
    assertEquals(SandboxErrorCode.SANDBOX_TRAVERSAL, reject(resolver, "%2e%2e/config"));
  }

  @Test
  void normalizesUnicodeToNfcAndAppliesPoliciesCaseInsensitively() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("plugins"));
    SandboxPolicy policy =
        new SandboxPolicy(
            SandboxPolicy.DEFAULT_MAX_PATH_LENGTH,
            SandboxPolicy.DEFAULT_PROTECTED_PATTERNS,
            Collections.singletonList("secret*/**"),
            false);
    SandboxResolver resolver = new SandboxResolver(root, policy);
    String nfd = Normalizer.normalize("caf\u00e9/file.yml", Normalizer.Form.NFD);

    assertEquals(
        "caf\u00e9/file.yml", resolver.resolve(nfd, SandboxAccessMode.CREATE).relativePath());
    assertEquals(
        SandboxErrorCode.SANDBOX_PROTECTED,
        reject(resolver, "DeployMC/config.yml", SandboxAccessMode.WRITE));
    assertEquals(
        SandboxErrorCode.SANDBOX_PROTECTED,
        reject(resolver, ".DEPLOYMC/history", SandboxAccessMode.READ));
    assertEquals(
        SandboxErrorCode.SANDBOX_DENIED,
        reject(resolver, "SECRET-data/key.txt", SandboxAccessMode.READ));
    assertNotNull(resolver.resolve("plugins/other.yml", SandboxAccessMode.READ));
  }

  @Test
  void rejectsForeignResolverPathsAndRetargetedRoots() throws Exception {
    Path root = Paths.get("/deploymc-root");
    CountingFileSystem fileSystem = new CountingFileSystem(root);
    SandboxResolver resolver = new SandboxResolver(root, new SandboxPolicy(), fileSystem);
    SandboxPath path = resolver.resolve("plugin.yml", SandboxAccessMode.READ);
    SandboxResolver other =
        new SandboxResolver(root, new SandboxPolicy(), new CountingFileSystem(root));
    SandboxPath foreign = other.resolve("plugin.yml", SandboxAccessMode.READ);

    assertTrue(path.belongsTo(resolver.rootIdentity()));
    assertFalse(foreign.belongsTo(resolver.rootIdentity()));
    assertEquals(
        SandboxErrorCode.SANDBOX_OUTSIDE_ROOT,
        assertThrows(
                SandboxRejectionException.class,
                () -> resolver.verifyOpened(foreign, root.resolve("plugin.yml")))
            .code());

    fileSystem.redirect(root, Paths.get("/deploymc-elsewhere"));
    assertEquals(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT, reject(resolver, "plugin.yml"));
  }

  @Test
  void enforcesComponentWiseContainmentForFollowedLinks() throws Exception {
    Path root = Paths.get("/deploymc-root");
    CountingFileSystem fileSystem = new CountingFileSystem(root);
    Path link = root.resolve("escape");
    fileSystem.set(link, new SandboxFileSystem.EntryAttributes(true, false, false, "link-key"));
    fileSystem.redirect(link, Paths.get("/deploymc-root-evil"));
    SandboxPolicy following =
        new SandboxPolicy(
            SandboxPolicy.DEFAULT_MAX_PATH_LENGTH,
            SandboxPolicy.DEFAULT_PROTECTED_PATTERNS,
            Collections.<String>emptyList(),
            true);
    SandboxResolver resolver = new SandboxResolver(root, following, fileSystem);

    assertEquals(
        SandboxErrorCode.SANDBOX_OUTSIDE_ROOT,
        reject(resolver, "escape/secret.txt", SandboxAccessMode.READ));
  }

  @Test
  void rejectsLinksAndRechecksOpenedFileKeys() throws Exception {
    Path root = Paths.get("/deploymc-root");
    CountingFileSystem fileSystem = new CountingFileSystem(root);
    Path target = root.resolve("plugin.yml");
    fileSystem.set(
        target, new SandboxFileSystem.EntryAttributes(false, false, false, "key-before"));
    SandboxResolver resolver = new SandboxResolver(root, new SandboxPolicy(), fileSystem);
    SandboxPath path = resolver.resolve("plugin.yml", SandboxAccessMode.READ);
    resolver.verifyOpened(path, target);

    fileSystem.set(target, new SandboxFileSystem.EntryAttributes(true, false, false, "key-before"));
    assertEquals(
        SandboxErrorCode.SANDBOX_FILE_CHANGED,
        assertThrows(SandboxRejectionException.class, () -> resolver.verifyOpened(path, target))
            .code());

    fileSystem.set(target, new SandboxFileSystem.EntryAttributes(false, true, false, "key-before"));
    assertEquals(
        SandboxErrorCode.SANDBOX_FILE_CHANGED,
        assertThrows(SandboxRejectionException.class, () -> resolver.verifyOpened(path, target))
            .code());

    fileSystem.set(target, new SandboxFileSystem.EntryAttributes(false, false, true, "key-after"));
    assertEquals(
        SandboxErrorCode.SANDBOX_FILE_CHANGED,
        assertThrows(SandboxRejectionException.class, () -> resolver.verifyOpened(path, target))
            .code());

    fileSystem.set(
        root.resolve("junction"), new SandboxFileSystem.EntryAttributes(false, true, true, "j"));
    assertEquals(SandboxErrorCode.SANDBOX_SYMLINK, reject(resolver, "junction/file.txt"));
  }

  @Test
  void recordsOriginalBytesAndDescriptorFstatAsUnimplementedSpecGaps() throws Exception {
    String spec = specSection("## 33.1 Sandbox Path Resolution");
    assertTrue(spec.contains("keep original bytes for the actual file name"));
    assertTrue(spec.contains("After opening the file, re-check (fstat / file key)"));
    assertFalse(declaresParameterType(SandboxResolver.class, FileChannel.class));

    Map<String, String> gaps = new LinkedHashMap<String, String>();
    gaps.put("original bytes for existing non-NFC file names", "NOT IMPLEMENTED");
    gaps.put("fstat on an already opened descriptor", "NOT IMPLEMENTED");
    assertEquals(2, gaps.size());
    for (Map.Entry<String, String> gap : gaps.entrySet()) {
      assertEquals("NOT IMPLEMENTED", gap.getValue(), gap.getKey());
    }
  }

  private static SandboxErrorCode reject(SandboxResolver resolver, String request) {
    return reject(resolver, request, SandboxAccessMode.READ);
  }

  private static SandboxErrorCode reject(
      SandboxResolver resolver, String request, SandboxAccessMode mode) {
    return assertThrows(SandboxRejectionException.class, () -> resolver.resolve(request, mode))
        .code();
  }

  private static String repeat(char value, int count) {
    char[] chars = new char[count];
    Arrays.fill(chars, value);
    return new String(chars);
  }

  private static boolean declaresParameterType(Class<?> owner, Class<?> parameterType) {
    for (Method method : owner.getDeclaredMethods()) {
      for (Class<?> candidate : method.getParameterTypes()) {
        if (parameterType.equals(candidate)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String specSection(String heading) throws IOException {
    Path file = locateSpec();
    assertNotNull(file, "Missing master specification: " + SPEC_PATH);
    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    int start = text.indexOf(heading);
    assertTrue(start >= 0, "Missing specification section: " + heading);
    int end = text.indexOf("\n# PART ", start + heading.length());
    return text.substring(start, end < 0 ? text.length() : end);
  }

  private static Path locateSpec() {
    Path relative = Paths.get(SPEC_PATH);
    Path current = Paths.get("").toAbsolutePath();
    List<Path> candidates = new ArrayList<Path>();
    candidates.add(current);
    if (current.getParent() != null) {
      candidates.add(current.getParent());
      if (current.getParent().getParent() != null) {
        candidates.add(current.getParent().getParent());
      }
    }
    for (Path candidate : candidates) {
      Path resolved = candidate.resolve(relative);
      if (Files.isRegularFile(resolved)) {
        return resolved;
      }
    }
    return null;
  }

  /** In-memory file system with a configurable root and per-entry attributes. */
  private static final class CountingFileSystem implements SandboxFileSystem {
    private final Path root;
    private final Map<Path, EntryAttributes> attributes =
        new LinkedHashMap<Path, EntryAttributes>();
    private final Map<Path, Path> redirects = new LinkedHashMap<Path, Path>();
    private int reads;
    private int realPaths;

    private CountingFileSystem(Path root) {
      this.root = root;
      attributes.put(root, new EntryAttributes(false, false, true, "root-key"));
    }

    private void set(Path path, EntryAttributes value) {
      attributes.put(path, value);
    }

    private void redirect(Path from, Path to) {
      redirects.put(from, to);
    }

    private void reset() {
      reads = 0;
      realPaths = 0;
    }

    private int attributeReads() {
      return reads;
    }

    @Override
    public EntryAttributes readAttributes(Path path) {
      reads++;
      EntryAttributes value = attributes.get(path);
      return value == null ? new EntryAttributes(false, false, false, path.toString()) : value;
    }

    @Override
    public Path toRealPath(Path path) {
      realPaths++;
      Path target = redirects.get(path);
      return target == null ? path : target;
    }
  }
}
