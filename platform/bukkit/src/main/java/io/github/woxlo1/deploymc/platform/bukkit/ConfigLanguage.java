package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.Locale;

/** Maps the host-controlled {@code language} setting to a message catalog locale. */
public final class ConfigLanguage {

  private ConfigLanguage() {}

  /** Converts a configuration language tag to a locale. Blank values use English. */
  public static Locale toLocale(String language) {
    if (language == null || language.trim().isEmpty()) {
      return Locale.ENGLISH;
    }
    return Locale.forLanguageTag(language.trim().replace('_', '-'));
  }
}
