package io.github.woxlo1.deploymc.core.config;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Formats configuration diagnostics through localized catalogs. */
public final class ConfigMessages {

  private static final String BUNDLE_NAME = "io.github.woxlo1.deploymc.core.config.config-messages";
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private ConfigMessages() {}

  public static String format(Locale locale, String key, Object... parameters) {
    ResourceBundle bundle = bundle(locale);
    String pattern;
    try {
      pattern = bundle.getString(key);
    } catch (MissingResourceException missing) {
      pattern = bundle(Locale.ROOT).getString(key);
    }
    return new MessageFormat(pattern, Locale.ROOT).format(parameters);
  }

  private static ResourceBundle bundle(Locale locale) {
    Locale effective = locale == null ? Locale.ROOT : locale;
    return ResourceBundle.getBundle(
        BUNDLE_NAME, effective, ConfigMessages.class.getClassLoader(), CONTROL);
  }
}
