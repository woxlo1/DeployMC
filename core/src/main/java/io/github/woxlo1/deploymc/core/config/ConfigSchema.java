package io.github.woxlo1.deploymc.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads the normative JSON schema from the classpath and exposes its loader metadata. */
public final class ConfigSchema {

  private static final String RESOURCE = "config/config.schema.json";

  private final Map<String, Object> root;

  private ConfigSchema(Map<String, Object> root) {
    this.root = root;
  }

  @SuppressWarnings("unchecked")
  public static ConfigSchema load() throws ConfigException {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setCodePointLimit(2_000_000);
    try (InputStream stream = ConfigSchema.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new ConfigException(
            ConfigMessages.format(Locale.ROOT, "config.error.schema", RESOURCE));
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        Object parsed = new Yaml(new SafeConstructor(options)).load(reader);
        if (!(parsed instanceof Map)) {
          throw new ConfigException(
              ConfigMessages.format(Locale.ROOT, "config.error.schema", RESOURCE));
        }
        return new ConfigSchema((Map<String, Object>) parsed);
      }
    } catch (IOException exception) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.schema", exception.getMessage()),
          exception);
    } catch (RuntimeException exception) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.schema", exception.getMessage()),
          exception);
    }
  }

  public Map<String, Object> root() {
    return root;
  }

  public int supportedVersion() {
    Object properties = root.get("properties");
    if (!(properties instanceof Map)) {
      return 1;
    }
    Object version = ((Map<?, ?>) properties).get("config-version");
    if (!(version instanceof Map)) {
      return 1;
    }
    Object constant = ((Map<?, ?>) version).get("const");
    if (constant instanceof Number) {
      return ((Number) constant).intValue();
    }
    return 1;
  }

  /** Resolves a local {@code $ref} such as {@code #/$defs/roleName}. */
  @SuppressWarnings("unchecked")
  public Object resolve(Object schema) {
    Object current = schema;
    while (current instanceof Map) {
      Object reference = ((Map<String, Object>) current).get("$ref");
      if (!(reference instanceof String)) {
        return current;
      }
      String pointer = (String) reference;
      if (!pointer.startsWith("#/")) {
        throw new IllegalStateException("Only local schema references are supported: " + pointer);
      }
      current = root;
      for (String segment : pointer.substring(2).split("/")) {
        if (!(current instanceof Map)) {
          throw new IllegalStateException("Unresolvable schema reference: " + pointer);
        }
        current =
            ((Map<String, Object>) current).get(segment.replace("~1", "/").replace("~0", "~"));
      }
    }
    return current;
  }

  /** Returns the hot-reload flag of a dotted property path, or {@code null} when unknown. */
  public Boolean hotReloadable(String path) {
    Object current = root;
    for (String segment : path.split("\\.")) {
      if (!(current instanceof Map)) {
        return null;
      }
      Map<?, ?> map = (Map<?, ?>) current;
      current = map.get("properties");
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<?, ?>) current).get(segment);
    }
    if (current instanceof Map) {
      Object flag = ((Map<?, ?>) current).get("x-deploymc-hot-reload");
      return flag instanceof Boolean ? (Boolean) flag : null;
    }
    return null;
  }
}
