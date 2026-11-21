package io.github.woxlo1.deploymc.core.snapshot;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Formats snapshot-store diagnostics through localized catalogs. */
public final class SnapshotStoreMessages {

  private static final String BUNDLE_NAME =
      "io.github.woxlo1.deploymc.core.snapshot.snapshot-messages";
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private SnapshotStoreMessages() {}

  public static String format(Locale locale, String key, Object... parameters) {
    ResourceBundle bundle = bundle(locale);
    String pattern;
    try {
      pattern = bundle.getString(key);
    } catch (MissingResourceException missing) {
      pattern = bundle(Locale.ROOT).getString(key);
    }
    Object[] arguments = parameters == null ? new Object[0] : new Object[parameters.length];
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] =
          parameters[i] instanceof LocalizedDetail
              ? ((LocalizedDetail) parameters[i]).localized(locale)
              : parameters[i];
    }
    return new MessageFormat(pattern, Locale.ROOT).format(arguments);
  }

  private static ResourceBundle bundle(Locale locale) {
    Locale effective = locale == null ? Locale.ROOT : locale;
    return ResourceBundle.getBundle(
        BUNDLE_NAME, effective, SnapshotStoreMessages.class.getClassLoader(), CONTROL);
  }
}
