package io.github.woxlo1.deploymc.transport;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resources for transport log messages. User-facing sentences stay in catalog files. */
public final class TransportMessages {

  private static final String BASE_NAME = "io.github.woxlo1.deploymc.transport.messages";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");

  private final ResourceBundle bundle;

  private TransportMessages(ResourceBundle bundle) {
    this.bundle = bundle;
  }

  public static TransportMessages of(Locale locale) {
    return new TransportMessages(ResourceBundle.getBundle(BASE_NAME, locale));
  }

  public String format(String key) {
    return format(key, Collections.<String, Object>emptyMap());
  }

  public String format(String key, Map<String, Object> values) {
    String pattern;
    try {
      pattern = bundle.getString(key);
    } catch (Exception exception) {
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
}
