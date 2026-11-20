package io.github.woxlo1.deploymc.transport;

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
 * Resources for transport log messages. User-facing sentences stay in catalog files.
 *
 * <p>The lookup chain is the requested locale followed by the English base catalog. The JVM default
 * locale is deliberately never consulted: an English configuration must not produce Japanese log
 * messages on a Japanese host.
 */
public final class TransportMessages {

  private static final String BASE_NAME = "io.github.woxlo1.deploymc.transport.messages";
  private static final String BASE_RESOURCE = "messages.properties";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
  private static final ClassLoader LOADER = TransportMessages.class.getClassLoader();

  private final Map<String, String> messages;

  private TransportMessages(Map<String, String> messages) {
    this.messages = Collections.unmodifiableMap(messages);
  }

  public static TransportMessages of(Locale locale) {
    Objects.requireNonNull(locale, "locale");
    Map<String, String> selected = new LinkedHashMap<String, String>(load(BASE_RESOURCE));
    for (String resource : localeResources(locale)) {
      selected.putAll(load(resource));
    }
    return new TransportMessages(selected);
  }

  public String format(String key) {
    return format(key, Collections.<String, Object>emptyMap());
  }

  public String format(String key, Map<String, Object> values) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(values, "values");
    String pattern = messages.get(key);
    if (pattern == null) {
      return "missing catalog key: " + key;
    }
    Matcher matcher = PLACEHOLDER.matcher(pattern);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      Object value = values.get(name);
      matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static List<String> localeResources(Locale locale) {
    List<String> resources = new ArrayList<String>();
    String language = locale.getLanguage();
    String country = locale.getCountry();
    String variant = locale.getVariant();
    if (isEnglish(language) || language.isEmpty()) {
      return resources;
    }
    if (!variant.isEmpty() && !country.isEmpty()) {
      resources.add("messages_" + language + "_" + country + "_" + variant + ".properties");
    }
    if (!country.isEmpty()) {
      resources.add("messages_" + language + "_" + country + ".properties");
    }
    resources.add("messages_" + language + ".properties");
    return resources;
  }

  private static boolean isEnglish(String language) {
    return "en".equals(language.toLowerCase(Locale.ROOT));
  }

  private static Map<String, String> load(String resource) {
    String path =
        BASE_NAME.substring(0, BASE_NAME.lastIndexOf('.') + 1).replace('.', '/') + resource;
    Map<String, String> values = new LinkedHashMap<String, String>();
    InputStream stream = LOADER.getResourceAsStream(path);
    if (stream == null) {
      return values;
    }
    try {
      Properties properties = new Properties();
      properties.load(stream);
      for (String name : properties.stringPropertyNames()) {
        values.put(name, properties.getProperty(name));
      }
    } catch (IOException exception) {
      throw new IllegalStateException("could not read catalog " + resource, exception);
    } finally {
      try {
        stream.close();
      } catch (IOException ignored) {
        // The catalog has already been read; closing failure is not actionable.
      }
    }
    return values;
  }
}
