package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.files.FileOperationLimits;
import io.github.woxlo1.deploymc.core.files.FileOperationsService;
import io.github.woxlo1.deploymc.core.sandbox.SandboxPolicy;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreConfig;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreException;
import io.github.woxlo1.deploymc.core.validation.ValidatorRegistry;
import io.github.woxlo1.deploymc.transport.FileApiHandler;
import io.github.woxlo1.deploymc.transport.HttpHandler;
import io.github.woxlo1.deploymc.transport.SnapshotApiHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Host-wired file API services. Request data never reaches a constructor in this class. */
public final class FileApiRuntime implements AutoCloseable {

  private static final String DEFAULT_SNAPSHOT_DIRECTORY = ".deploymc/history";
  private static final long MAX_FILE_MB = 1024L;
  private static final long MAX_UPLOAD_MB = 4096L;
  private static final long MAX_SNAPSHOT_STORE_MB = 1048576L;
  private static final List<String> DEFAULT_PROTECTED_PATHS =
      Collections.unmodifiableList(
          Arrays.asList(SandboxPolicy.DEFAULT_PROTECTED_PATTERNS.toArray(new String[0])));

  private final SnapshotStore snapshots;
  private final HttpHandler handler;
  private final HttpHandler snapshotHandler;

  private FileApiRuntime(
      SnapshotStore snapshots, HttpHandler handler, HttpHandler snapshotHandler) {
    this.snapshots = snapshots;
    this.handler = handler;
    this.snapshotHandler = snapshotHandler;
  }

  public static FileApiRuntime create(
      ConfigView view, Path pluginsDirectory, Locale locale, int maximumBodyBytes) {
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(locale, "locale");
    Path base =
        Objects.requireNonNull(pluginsDirectory, "pluginsDirectory").toAbsolutePath().normalize();

    Path sandboxRoot = resolveRelative(view, base, "sandbox.root", true);
    prepareDirectory(sandboxRoot, "sandbox.root");
    SandboxPolicy policy =
        new SandboxPolicy(
            SandboxPolicy.DEFAULT_MAX_PATH_LENGTH,
            stringList(
                view.get("sandbox.protected-paths"),
                DEFAULT_PROTECTED_PATHS,
                "sandbox.protected-paths"),
            stringList(
                view.get("sandbox.deny-patterns"),
                Collections.<String>emptyList(),
                "sandbox.deny-patterns"),
            view.getBoolean("sandbox.follow-symlinks", false));
    SandboxResolver resolver = new SandboxResolver(sandboxRoot, policy);

    Path configuredSnapshots = resolveRelative(view, base, "snapshots.directory", false);
    Path supportedSnapshots = base.resolve(DEFAULT_SNAPSHOT_DIRECTORY).normalize();
    if (!configuredSnapshots.equals(supportedSnapshots)) {
      throw invalid(
          "startup.fileApi.snapshotDirectory",
          "value",
          view.getString("snapshots.directory", DEFAULT_SNAPSHOT_DIRECTORY));
    }

    FileOperationLimits limits =
        new FileOperationLimits(
            megabytes(view, "sandbox.max-file-size-mb", 10L, MAX_FILE_MB),
            megabytes(view, "sandbox.max-upload-size-mb", 64L, MAX_UPLOAD_MB),
            positiveInt(view, "sandbox.max-list-entries", 5000),
            FileOperationLimits.DEFAULT_MAX_DELETE_ENTRIES);
    SnapshotStoreConfig snapshotConfig =
        SnapshotStoreConfig.builder()
            .maxVersionsPerPath(positiveInt(view, "snapshots.retention.max-versions-per-file", 50))
            .minAgeDays(nonNegativeLong(view, "snapshots.retention.max-age-days", 90L))
            .maxStoreBytes(
                megabytes(
                    view, "snapshots.retention.max-total-size-mb", 2048L, MAX_SNAPSHOT_STORE_MB))
            .build();

    SnapshotStore opened;
    try {
      opened = SnapshotStore.open(base, resolver, snapshotConfig);
    } catch (SnapshotStoreException failure) {
      throw new StartupConfigurationException(
          MessageKeys.STARTUP_FILE_API_FAILED,
          Collections.singletonMap("error", failure.localizedMessage(locale)));
    }
    try {
      ValidatorRegistry validators = ValidatorRegistry.withBuiltIns();
      FileOperationsService files = new FileOperationsService(resolver, limits, opened, validators);
      FileApiHandler handler =
          new FileApiHandler(resolver, files, validators, locale, maximumBodyBytes);
      SnapshotApiHandler snapshotsHandler =
          new SnapshotApiHandler(resolver, opened, locale, maximumBodyBytes);
      return new FileApiRuntime(opened, handler, snapshotsHandler);
    } catch (RuntimeException failure) {
      try {
        opened.close();
      } catch (IOException ignored) {
        // The original startup failure is the useful diagnostic.
      }
      throw failure;
    }
  }

  public HttpHandler handler() {
    return handler;
  }

  public HttpHandler snapshotHandler() {
    return snapshotHandler;
  }

  @Override
  public void close() throws IOException {
    snapshots.close();
  }

  private static Path resolveRelative(
      ConfigView view, Path base, String setting, boolean allowCurrentDirectory) {
    String value =
        view.getString(setting, allowCurrentDirectory ? "." : DEFAULT_SNAPSHOT_DIRECTORY);
    if (value.trim().isEmpty()) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    Path relative;
    try {
      relative = Paths.get(value);
    } catch (RuntimeException invalidPath) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    if (relative.isAbsolute()) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    for (Path segment : relative) {
      if ("..".equals(segment.toString())) {
        throw invalid("startup.fileApi.invalidConfig", "setting", setting);
      }
    }
    Path resolved = base.resolve(relative).normalize();
    if (!resolved.startsWith(base)) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    return resolved;
  }

  private static void prepareDirectory(Path directory, String setting) {
    try {
      Files.createDirectories(directory);
    } catch (IOException failure) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
  }

  private static List<String> stringList(Object raw, List<String> fallback, String setting) {
    if (raw == null) {
      return fallback;
    }
    if (!(raw instanceof List)) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    List<String> result = new ArrayList<String>();
    for (Object item : (List<?>) raw) {
      if (!(item instanceof String)) {
        throw invalid("startup.fileApi.invalidConfig", "setting", setting);
      }
      result.add((String) item);
    }
    return result;
  }

  private static int positiveInt(ConfigView view, String setting, int fallback) {
    int value = view.getInt(setting, fallback);
    if (value < 1) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    return value;
  }

  private static long nonNegativeLong(ConfigView view, String setting, long fallback) {
    Object raw = view.get(setting);
    long value = raw instanceof Number ? ((Number) raw).longValue() : fallback;
    if (value < 0L) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    return value;
  }

  private static long megabytes(
      ConfigView view, String setting, long fallback, long maximumMegabytes) {
    long value = nonNegativeLong(view, setting, fallback);
    if (value < 1L || value > maximumMegabytes) {
      throw invalid("startup.fileApi.invalidConfig", "setting", setting);
    }
    return value * 1024L * 1024L;
  }

  private static StartupConfigurationException invalid(
      String messageKey, String placeholder, String value) {
    Map<String, String> values = Collections.singletonMap(placeholder, value);
    return new StartupConfigurationException(messageKey, values);
  }
}
