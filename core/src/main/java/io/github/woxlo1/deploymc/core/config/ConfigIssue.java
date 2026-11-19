package io.github.woxlo1.deploymc.core.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One schema finding with the exact source location and a localizable message key. */
public final class ConfigIssue {

  public enum Severity {
    ERROR,
    WARNING
  }

  private final Severity severity;
  private final String path;
  private final int line;
  private final int column;
  private final String messageKey;
  private final List<Object> parameters;

  public ConfigIssue(
      Severity severity,
      String path,
      int line,
      int column,
      String messageKey,
      Object... parameters) {
    this.severity = Objects.requireNonNull(severity, "severity");
    this.path = Objects.requireNonNull(path, "path");
    this.line = Math.max(1, line);
    this.column = Math.max(1, column);
    this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
    this.parameters = Collections.unmodifiableList(Arrays.asList(parameters));
  }

  public Severity severity() {
    return severity;
  }

  public String path() {
    return path;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public String messageKey() {
    return messageKey;
  }

  public List<Object> parameters() {
    return parameters;
  }

  public boolean isError() {
    return severity == Severity.ERROR;
  }

  public String format(Locale locale) {
    return ConfigMessages.format(locale, messageKey, parameters.toArray());
  }

  @Override
  public String toString() {
    return severity + " " + path + " (" + line + ":" + column + ") " + format(Locale.ROOT);
  }
}
