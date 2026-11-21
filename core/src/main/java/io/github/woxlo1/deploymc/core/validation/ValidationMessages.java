package io.github.woxlo1.deploymc.core.validation;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Formats validation diagnostics through localized catalogs. */
public final class ValidationMessages {

  private static final String BUNDLE_NAME =
      "io.github.woxlo1.deploymc.core.validation.validation-messages";
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private ValidationMessages() {}

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

  /** Renders only the localized detail for a stable issue code. */
  public static String detail(Locale locale, ValidationIssue issue) {
    return format(locale, "code." + issue.code(), issue.parameters().toArray());
  }

  private static ResourceBundle bundle(Locale locale) {
    Locale effective = locale == null ? Locale.ROOT : locale;
    return ResourceBundle.getBundle(
        BUNDLE_NAME, effective, ValidationMessages.class.getClassLoader(), CONTROL);
  }
}
