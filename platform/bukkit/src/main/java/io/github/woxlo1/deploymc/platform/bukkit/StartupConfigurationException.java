package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Startup rejection with a stable localized message key and placeholders. */
public final class StartupConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String messageKey;
  private final LinkedHashMap<String, String> values;

  public StartupConfigurationException(String messageKey, Map<String, String> values) {
    super(messageKey);
    this.messageKey = messageKey;
    this.values = new LinkedHashMap<String, String>(values);
  }

  public String messageKey() {
    return messageKey;
  }

  public Map<String, String> values() {
    return Collections.unmodifiableMap(values);
  }
}
