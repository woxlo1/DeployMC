package io.github.woxlo1.deploymc.platform.bukkit;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Feature-detects a per-sender locale without compiling against newer Bukkit APIs.
 *
 * <p>Old servers may expose no locale accessor, newer servers may return {@link Locale}, and some
 * platforms return a language tag as a string. Any missing or failing accessor is treated as
 * unavailable so the caller can use the configured server default.
 */
public final class SenderLocaleResolver {

  private static final String[] METHOD_NAMES = {"locale", "getLocale"};

  private SenderLocaleResolver() {}

  /** Returns the locale reported by {@code sender}, if the platform exposes one. */
  public static Optional<Locale> resolve(Object sender) {
    if (sender == null) {
      return Optional.empty();
    }
    for (String name : METHOD_NAMES) {
      Optional<Locale> locale = tryMethod(sender, name);
      if (locale.isPresent()) {
        return locale;
      }
    }
    return Optional.empty();
  }

  private static Optional<Locale> tryMethod(Object sender, String name) {
    try {
      Method method = sender.getClass().getMethod(name);
      Class<?> returnType = method.getReturnType();
      if (!Locale.class.equals(returnType) && !CharSequence.class.isAssignableFrom(returnType)) {
        return Optional.empty();
      }
      Object value = method.invoke(sender);
      if (value instanceof Locale) {
        return Optional.of((Locale) value);
      }
      if (value instanceof CharSequence) {
        String tag = value.toString().trim();
        if (!tag.isEmpty()) {
          return Optional.of(ConfigLanguage.toLocale(tag));
        }
      }
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // Feature detection: no locale accessor, or the accessor failed.
    }
    return Optional.empty();
  }
}
