package io.github.woxlo1.deploymc.core.sandbox;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns untrusted request strings into {@link SandboxPath} values.
 *
 * <p>The resolver is pure apart from the injected {@link SandboxFileSystem}; it never reads request
 * data from the environment and never widens its host-controlled root or policy. Windows name rules
 * are applied on every platform so that a repository behaves identically across hosts.
 */
public final class SandboxResolver {

  private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
  private static final Pattern SHORT_NAME = Pattern.compile("~[0-9]");
  private static final String FORBIDDEN_SEGMENT_CHARS = "<>:\"|?*";
  private static final String DOT_AND_SEPARATOR_LOOKALIKES = "\uff0e\u3002\uff0f\u2215\uff3c";
  private static final List<String> ENCODED_TRAVERSAL_MARKERS =
      Collections.unmodifiableList(
          Arrays.asList(
              "%2e", "%2f", "%5c", "%00", "%25", "%c0", "%e0", "%ef", "%f0", "%f8", "%fc", "%u"));
  private static final Set<String> RESERVED_NAMES = reservedNames();
  private static final Set<String> ALWAYS_PROTECTED =
      Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(".deploymc", "deploymc")));

  private final Path configuredRoot;
  private final Path rootReal;
  private final SandboxPolicy policy;
  private final SandboxFileSystem fileSystem;
  private final Object rootIdentity = new Object();

  public SandboxResolver(Path root) {
    this(root, new SandboxPolicy(), DefaultSandboxFileSystem.INSTANCE);
  }

  public SandboxResolver(Path root, SandboxPolicy policy) {
    this(root, policy, DefaultSandboxFileSystem.INSTANCE);
  }

  public SandboxResolver(Path root, SandboxPolicy policy, SandboxFileSystem fileSystem) {
    Objects.requireNonNull(root, "root");
    this.configuredRoot = root;
    this.policy = Objects.requireNonNull(policy, "policy");
    this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    try {
      this.rootReal = fileSystem.toRealPath(root);
    } catch (IOException failure) {
      throw new IllegalStateException(
          SandboxMessages.format(
              Locale.ROOT,
              SandboxErrorCode.SANDBOX_IO.messageKey(),
              failure.getClass().getSimpleName()),
          failure);
    }
  }

  /** Opaque identity used to reject paths minted by another resolver. */
  public Object rootIdentity() {
    return rootIdentity;
  }

  /** The canonical sandbox root, computed once at construction. */
  public Path root() {
    return rootReal;
  }

  /**
   * Resolves an untrusted path. Every failure throws before any file is opened.
   *
   * @param requested sandbox-relative request path; empty or {@code .} means the root
   * @param mode intended operation
   */
  public SandboxPath resolve(String requested, SandboxAccessMode mode)
      throws SandboxRejectionException {
    Objects.requireNonNull(mode, "mode");
    assertRootStillValid();
    if (requested == null) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_EMPTY);
    }
    if (requested.length() > policy.maxPathLength()) {
      throw new SandboxRejectionException(
          SandboxErrorCode.SANDBOX_TOO_LONG, Integer.valueOf(policy.maxPathLength()));
    }
    if (requested.indexOf('\0') >= 0) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_NUL);
    }
    for (int i = 0; i < requested.length(); i++) {
      char c = requested.charAt(i);
      if (Character.isISOControl(c)) {
        throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_CONTROL_CHAR);
      }
      if (isUnpairedSurrogate(requested, i)) {
        throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_INVALID_UNICODE);
      }
      if (Character.getType(c) == Character.FORMAT) {
        throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_INVALID_UNICODE);
      }
    }

    String normalized = Normalizer.normalize(requested, Normalizer.Form.NFC);
    if (isAbsolute(normalized)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_ABSOLUTE);
    }

    List<String> segments = new ArrayList<String>();
    String[] raw = normalized.replace('\\', '/').split("/", -1);
    for (String segment : raw) {
      if (segment.isEmpty() || ".".equals(segment)) {
        continue;
      }
      if ("..".equals(segment)) {
        throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_TRAVERSAL);
      }
      validateSegment(segment);
      segments.add(segment);
    }

    String relative = join(segments, '/');
    if (ALWAYS_PROTECTED.contains(relative.toLowerCase(Locale.ROOT))
        || policy.matchesProtected(relative)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_PROTECTED, relative);
    }
    if (policy.matchesDenied(relative)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_DENIED, relative);
    }

    ResolvedTarget target = resolveWithAncestorChecks(segments);
    return SandboxPath.create(relative, target.path, mode, rootIdentity, target.fileKey);
  }

  /**
   * Re-checks an already opened entry. File operations must call this before using an open handle,
   * which narrows the time-of-check/time-of-use window. A changed file key rejects a swap at the
   * same path; on file systems without file keys the canonical-path check remains the fallback.
   */
  public void verifyOpened(SandboxPath path, Path openedReal) throws SandboxRejectionException {
    if (path == null || !path.belongsTo(rootIdentity)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT);
    }
    Path real;
    try {
      real = fileSystem.toRealPath(openedReal);
    } catch (IOException failure) {
      throw new SandboxRejectionException(
          SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
    }
    if (!isContained(real) || !path.absolutePath().equals(real)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT);
    }

    SandboxFileSystem.EntryAttributes attributes;
    try {
      attributes = fileSystem.readAttributes(real);
    } catch (IOException failure) {
      throw new SandboxRejectionException(
          SandboxErrorCode.SANDBOX_FILE_CHANGED, failure.getClass().getSimpleName());
    }
    if (attributes.symbolicLink()
        || attributes.other()
        || (path.fileKey() != null && !Objects.equals(attributes.fileKey(), path.fileKey()))) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_FILE_CHANGED);
    }
  }

  private void assertRootStillValid() throws SandboxRejectionException {
    Path current;
    try {
      current = fileSystem.toRealPath(configuredRoot);
    } catch (IOException failure) {
      throw new SandboxRejectionException(
          SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
    }
    if (!current.equals(rootReal)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT);
    }
  }

  private ResolvedTarget resolveWithAncestorChecks(List<String> segments)
      throws SandboxRejectionException {
    Path candidate = rootReal;
    Object fileKey;
    try {
      fileKey = fileSystem.readAttributes(rootReal).fileKey();
    } catch (IOException failure) {
      throw new SandboxRejectionException(
          SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
    }
    List<String> remaining = new ArrayList<String>(segments);
    while (!remaining.isEmpty()) {
      Path next = candidate.resolve(remaining.get(0));
      SandboxFileSystem.EntryAttributes attributes;
      try {
        attributes = fileSystem.readAttributes(next);
      } catch (NoSuchFileException missing) {
        break;
      } catch (IOException failure) {
        throw new SandboxRejectionException(
            SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
      }
      if (attributes.symbolicLink() || attributes.other()) {
        if (!policy.followSymlinks()) {
          throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_SYMLINK);
        }
        try {
          candidate = fileSystem.toRealPath(next);
        } catch (IOException failure) {
          throw new SandboxRejectionException(
              SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
        }
        if (!isContained(candidate)) {
          throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT);
        }
      } else {
        candidate = next;
      }
      if (!attributes.symbolicLink() && !attributes.other()) {
        fileKey = attributes.fileKey();
      } else {
        try {
          fileKey = fileSystem.readAttributes(candidate).fileKey();
        } catch (IOException failure) {
          throw new SandboxRejectionException(
              SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
        }
      }
      remaining.remove(0);
    }

    Path realAncestor;
    try {
      realAncestor = fileSystem.toRealPath(candidate);
    } catch (IOException failure) {
      throw new SandboxRejectionException(
          SandboxErrorCode.SANDBOX_IO, failure.getClass().getSimpleName());
    }
    if (!isContained(realAncestor)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT);
    }
    for (String segment : remaining) {
      realAncestor = realAncestor.resolve(segment);
    }
    if (!isContained(realAncestor)) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_OUTSIDE_ROOT);
    }
    return new ResolvedTarget(realAncestor, fileKey);
  }

  private boolean isContained(Path candidate) {
    // Path.startsWith compares components, never string prefixes such as plugins-evil.
    return candidate.startsWith(rootReal);
  }

  private static boolean isAbsolute(String value) {
    return value.startsWith("/") || value.startsWith("\\") || DRIVE_PREFIX.matcher(value).matches();
  }

  private static boolean isUnpairedSurrogate(String value, int index) {
    char c = value.charAt(index);
    if (Character.isHighSurrogate(c)) {
      return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
    }
    return Character.isLowSurrogate(c)
        && (index == 0 || !Character.isHighSurrogate(value.charAt(index - 1)));
  }

  private static void validateSegment(String segment) throws SandboxRejectionException {
    if (segment.startsWith("..")) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_TRAVERSAL);
    }
    String lower = segment.toLowerCase(Locale.ROOT);
    for (String marker : ENCODED_TRAVERSAL_MARKERS) {
      if (lower.contains(marker)) {
        throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_TRAVERSAL);
      }
    }
    if (segment.length() > 255
        || segment.endsWith(".")
        || segment.endsWith(" ")
        || segment.trim().isEmpty()
        || SHORT_NAME.matcher(segment).find()) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_INVALID_NAME, segment);
    }
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      if (FORBIDDEN_SEGMENT_CHARS.indexOf(c) >= 0 || DOT_AND_SEPARATOR_LOOKALIKES.indexOf(c) >= 0) {
        throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_INVALID_NAME, segment);
      }
    }
    String base = segment.split("\\.", 2)[0].trim();
    while (base.endsWith(".") || base.endsWith(" ")) {
      base = base.substring(0, base.length() - 1);
    }
    if (RESERVED_NAMES.contains(base.toUpperCase(Locale.ROOT))) {
      throw new SandboxRejectionException(SandboxErrorCode.SANDBOX_INVALID_NAME, segment);
    }
  }

  private static String join(List<String> segments, char separator) {
    if (segments.isEmpty()) {
      return "";
    }
    StringBuilder joined = new StringBuilder();
    for (String segment : segments) {
      if (joined.length() > 0) {
        joined.append(separator);
      }
      joined.append(segment);
    }
    return joined.toString();
  }

  private static Set<String> reservedNames() {
    Set<String> names = new HashSet<String>(Arrays.asList("CON", "PRN", "AUX", "NUL", "CLOCK$"));
    for (int i = 1; i <= 9; i++) {
      names.add("COM" + i);
      names.add("LPT" + i);
    }
    for (String superscript : Arrays.asList("\u00b9", "\u00b2", "\u00b3")) {
      names.add("COM" + superscript);
      names.add("LPT" + superscript);
    }
    names.add("CONIN$");
    names.add("CONOUT$");
    return Collections.unmodifiableSet(names);
  }

  /** Resolved path plus the file key used for the later opened-object check. */
  private static final class ResolvedTarget {
    private final Path path;
    private final Object fileKey;

    private ResolvedTarget(Path path, Object fileKey) {
      this.path = path;
      this.fileKey = fileKey;
    }
  }
}
