package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side message catalog loader.
 *
 * <p>Catalogs are {@code .properties} resource bundles. Unlike the Web UI's ICU catalogs, these
 * messages use named {@code {placeholder}} tokens so the SPI can pass a name/value map without
 * knowing a positional order. Values may never be hard-coded in adapter code.
 */
public final class LocalizedMessages {

  private static final String BASE_NAME = "io.github.woxlo1.deploymc.platform.bukkit.messages";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
  private static final Map<String, String> NO_PLACEHOLDERS =
      Collections.unmodifiableMap(new LinkedHashMap<String, String>());

  private final ResourceBundle bundle;
  private final Locale locale;

  private LocalizedMessages(ResourceBundle bundle, Locale locale) {
    this.bundle = bundle;
    this.locale = locale;
  }

  /** Loads the catalog for {@code locale}, falling back to the English base catalog. */
  public static LocalizedMessages of(Locale locale) {
    Objects.requireNonNull(locale, "locale");
    return new LocalizedMessages(ResourceBundle.getBundle(BASE_NAME, locale), locale);
  }

  /** Returns the locale used to select the catalog. */
  public Locale locale() {
    return locale;
  }

  /** Returns whether the selected catalog defines {@code key}. */
  public boolean contains(String key) {
    return bundle.containsKey(Objects.requireNonNull(key, "key"));
  }

  /** Formats a known catalog entry with the supplied named values. */
  public String format(String key, Map<String, String> placeholders) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(placeholders, "placeholders");
    if (!bundle.containsKey(key)) {
      Map<String, String> values = new LinkedHashMap<String, String>();
      values.put("key", key);
      return render(MessageKeys.MESSAGE_MISSING_KEY, values, key);
    }
    return render(key, placeholders, key);
  }

  /** Formats a known catalog entry that has no placeholders. */
  public String format(String key) {
    return format(key, NO_PLACEHOLDERS);
  }

  private String render(String key, Map<String, String> placeholders, String requestedKey) {
    String pattern;
    try {
      pattern = bundle.getString(key);
    } catch (MissingResourceException exception) {
      throw new IllegalStateException("message catalog entry is unavailable: " + key, exception);
    }

    Matcher matcher = PLACEHOLDER.matcher(pattern);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      String value = placeholders.get(name);
      if (value == null) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("placeholder", name);
        values.put("key", requestedKey);
        return render(MessageKeys.MESSAGE_MISSING_PLACEHOLDER, values, requestedKey);
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
