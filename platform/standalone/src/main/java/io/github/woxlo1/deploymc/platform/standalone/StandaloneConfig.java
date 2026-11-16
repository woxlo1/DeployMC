package io.github.woxlo1.deploymc.platform.standalone;

import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import java.nio.file.Path;
import java.util.Objects;

/** Validated configuration for standalone mode. */
public final class StandaloneConfig {

  private final Path rootDirectory;
  private final HttpBindConfig bind;
  private final Path logFile;

  private StandaloneConfig(Path rootDirectory, HttpBindConfig bind, Path logFile) {
    this.rootDirectory = rootDirectory;
    this.bind = bind;
    this.logFile = logFile;
  }

  public static StandaloneConfig of(Path rootDirectory, HttpBindConfig bind, Path logFile) {
    Objects.requireNonNull(rootDirectory, "rootDirectory");
    Objects.requireNonNull(bind, "bind");
    Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
    Path normalizedLog = logFile == null ? null : logFile.toAbsolutePath().normalize();
    return new StandaloneConfig(normalizedRoot, bind, normalizedLog);
  }

  public Path rootDirectory() {
    return rootDirectory;
  }

  public HttpBindConfig bind() {
    return bind;
  }

  public Path logFile() {
    return logFile;
  }

  public boolean hasLogFile() {
    return logFile != null;
  }
}
