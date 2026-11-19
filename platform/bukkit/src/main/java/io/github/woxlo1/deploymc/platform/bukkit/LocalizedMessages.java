package io.github.woxlo1.deploymc.platform.bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side message catalog loader with an explicit fallback chain.
 *
 * <p>Catalogs are {@code .properties} resources. A lookup checks the requested locale, then the
 * server default locale, then the English base catalog. Unlike {@link ResourceBundle}, this loader
 * never consults the JVM default locale and can fall back one key at a time when a translation is
 * partial. Messages use named {@code {placeholder}} tokens because the command layer passes a
 * name/value map without knowing a positional order.
 */
public final class LocalizedMessages {

  private static final String BASE_NAME = "io.github.woxlo1.deploymc.platform.bukkit.messages";
  private static final String BASE_RESOURCE = BASE_NAME.replace('.', '/') + ".properties";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
  private static final Map<String, String> NO_PLACEHOLDERS =
      Collections.unmodifiableMap(new LinkedHashMap<String, String>());
  private static final ClassLoader LOADER = LocalizedMessages.class.getClassLoader();

  private final Map<String, String> requested;
  private final Map<String, String> serverDefault;
  private final Map<String, String> english;
  private final Locale locale;

  private LocalizedMessages(
      Map<String, String> requested,
      Map<String, String> serverDefault,
      Map<String, String> english,
      Locale locale) {
    this.requested = requested;
    this.serverDefault = serverDefault;
    this.english = english;
    this.locale = locale;
  }

  /** Loads a catalog whose only fallback is the English base catalog. */
  public static LocalizedMessages of(Locale locale) {
    return of(locale, Locale.ROOT);
  }

  /** Loads a catalog with the chain requested locale, server default locale, English base. */
  public static LocalizedMessages of(Locale locale, Locale serverDefault) {
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(serverDefault, "serverDefault");
    Map<String, String> english = loadBase();
    Map<String, String> requestedMessages = isEnglish(locale) ? english : loadLocale(locale);
    Map<String, String> serverMessages =
        isEnglish(serverDefault) ? english : loadLocale(serverDefault);
    return new LocalizedMessages(requestedMessages, serverMessages, english, locale);
  }

  /** Returns the locale that was requested for this catalog. */
  public Locale locale() {
    return locale;
  }

  /** Returns whether any rung of the fallback chain defines {@code key}. */
  public boolean contains(String key) {
    Objects.requireNonNull(key, "key");
    return requested.containsKey(key) || serverDefault.containsKey(key) || english.containsKey(key);
  }

  /** Formats a known catalog entry with the supplied named values. */
  public String format(String key, Map<String, String> placeholders) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(placeholders, "placeholders");
    if (!contains(key)) {
      Map<String, String> values = new LinkedHashMap<String, String>();
      values.put("key", key);
      return render(lookup(MessageKeys.MESSAGE_MISSING_KEY), values, key);
    }
    return render(lookup(key), placeholders, key);
  }

  /** Formats a known catalog entry that has no placeholders. */
  public String format(String key) {
    return format(key, NO_PLACEHOLDERS);
  }

  private String lookup(String key) {
    if (requested.containsKey(key)) {
      return requested.get(key);
    }
    if (serverDefault.containsKey(key)) {
      return serverDefault.get(key);
    }
    return english.get(key);
  }

  private String render(String pattern, Map<String, String> placeholders, String key) {
    if (pattern == null) {
      throw new IllegalStateException("message catalog entry is unavailable: " + key);
    }
    Matcher matcher = PLACEHOLDER.matcher(pattern);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      String value = placeholders.get(name);
      if (value == null) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("placeholder", name);
        values.put("key", key);
        return render(lookup(MessageKeys.MESSAGE_MISSING_PLACEHOLDER), values, key);
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static Map<String, String> loadBase() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    loadInto(BASE_RESOURCE, values);
    if (values.isEmpty()) {
      throw new IllegalStateException("message catalog is unavailable: " + BASE_NAME);
    }
    return Collections.unmodifiableMap(values);
  }

  private static Map<String, String> loadLocale(Locale locale) {
    Map<String, String> values = new LinkedHashMap<String, String>();
    for (String suffix : localeSuffixes(locale)) {
      loadInto(resourcePath(suffix), values);
    }
    return Collections.unmodifiableMap(values);
  }

  private static boolean isEnglish(Locale locale) {
    String language = locale.getLanguage();
    return language.isEmpty() || "en".equalsIgnoreCase(language);
  }

  private static List<String> localeSuffixes(Locale locale) {
    List<String> suffixes = new ArrayList<String>();
    String language = locale.getLanguage();
    String country = locale.getCountry();
    String variant = locale.getVariant();
    if (language.isEmpty()) {
      return suffixes;
    }
    if (!country.isEmpty() && !variant.isEmpty()) {
      suffixes.add(language + "_" + country + "_" + variant);
    }
    if (!country.isEmpty()) {
      suffixes.add(language + "_" + country);
    }
    suffixes.add(language);
    return suffixes;
  }

  private static String resourcePath(String suffix) {
    int extension = BASE_RESOURCE.lastIndexOf('.');
    return BASE_RESOURCE.substring(0, extension)
        + "_"
        + suffix
        + BASE_RESOURCE.substring(extension);
  }

  private static void loadInto(String path, Map<String, String> target) {
    InputStream stream = LOADER.getResourceAsStream(path);
    if (stream == null) {
      return;
    }
    Properties properties = new Properties();
    try {
      properties.load(stream);
    } catch (IOException exception) {
      throw new IllegalStateException("cannot read message catalog: " + path, exception);
    } finally {
      try {
        stream.close();
      } catch (IOException ignored) {
        // Closing a classpath stream cannot change the catalog contents.
      }
    }
    for (String name : properties.stringPropertyNames()) {
      if (!target.containsKey(name)) {
        target.put(name, properties.getProperty(name));
      }
    }
  }
}
