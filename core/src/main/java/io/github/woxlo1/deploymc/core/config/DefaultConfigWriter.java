package io.github.woxlo1.deploymc.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Writes the commented, fail-closed default configuration on first start. */
public final class DefaultConfigWriter {

  private static final String RESOURCE = "config/default-config.yml";

  private DefaultConfigWriter() {}

  /** Creates the file only when it is absent. Returns {@code true} when a file was created. */
  public static boolean writeIfMissing(Path target) throws ConfigException {
    if (Files.exists(target)) {
      return false;
    }
    ConfigFiles.writeUtf8(target, template(), false);
    return true;
  }

  public static String template() throws ConfigException {
    try (InputStream stream =
        DefaultConfigWriter.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new ConfigException(ConfigMessages.format(Locale.ROOT, "config.error.template"));
      }
      StringBuilder result = new StringBuilder();
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
          result.append(buffer, 0, read);
        }
      }
      return result.toString();
    } catch (IOException exception) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.template"), exception);
    }
  }
}
