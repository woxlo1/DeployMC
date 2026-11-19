package io.github.woxlo1.deploymc.core.config;

/** Thrown when configuration cannot be loaded safely. The caller must fail closed. */
public final class ConfigException extends Exception {

  private static final long serialVersionUID = 1L;

  public ConfigException(String message) {
    super(message);
  }

  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
