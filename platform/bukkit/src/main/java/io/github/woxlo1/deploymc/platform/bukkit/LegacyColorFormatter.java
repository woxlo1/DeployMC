package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.Objects;

/**
 * Converts legacy ampersand color codes to Minecraft section-sign codes.
 *
 * <p>This deliberately depends only on the JDK. It does not use {@code ChatColor} or any modern
 * text-component API, so it works on old Bukkit-family servers. Existing {@code §} codes are left
 * untouched and unknown {@code &} sequences remain literal.
 */
public final class LegacyColorFormatter {

  private static final char ALT_COLOR_CHAR = '&';
  private static final char SECTION_SIGN = '\u00A7';
  private static final String VALID_CODES = "0123456789abcdefklor";

  private LegacyColorFormatter() {}

  /** Returns {@code message} with valid {@code &} color codes translated to section-sign codes. */
  public static String translate(String message) {
    Objects.requireNonNull(message, "message");
    if (message.length() < 2) {
      return message;
    }
    StringBuilder result = new StringBuilder(message.length());
    for (int index = 0; index < message.length(); index++) {
      char current = message.charAt(index);
      if (current == ALT_COLOR_CHAR
          && index + 1 < message.length()
          && isValidCode(message.charAt(index + 1))) {
        result.append(SECTION_SIGN);
      } else {
        result.append(current);
      }
    }
    return result.toString();
  }

  private static boolean isValidCode(char candidate) {
    return VALID_CODES.indexOf(Character.toLowerCase(candidate)) >= 0;
  }
}
