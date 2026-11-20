package io.github.woxlo1.deploymc.core.files;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Formats file-operation diagnostics through localized catalogs. */
public final class FileMessages {

  private static final String BUNDLE_NAME = "io.github.woxlo1.deploymc.core.files.file-messages";
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private FileMessages() {}

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
        BUNDLE_NAME, effective, FileMessages.class.getClassLoader(), CONTROL);
  }
}
