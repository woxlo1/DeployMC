package io.github.woxlo1.deploymc.core.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DoctorCatalogTest {

  private static final String BUNDLE_NAME = "io.github.woxlo1.deploymc.core.doctor.doctor-messages";
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  @Test
  void englishAndJapaneseCatalogsHaveTheSameCompleteKeySet() {
    Set<String> english = keys(Locale.ROOT);
    Set<String> japanese = keys(Locale.JAPANESE);

    assertEquals(DoctorMessages.keys(), english);
    assertEquals(english, japanese);
    for (String key : english) {
      assertFalse(value(Locale.ROOT, key).trim().isEmpty(), key);
      assertFalse(value(Locale.JAPANESE, key).trim().isEmpty(), key);
    }
  }

  @Test
  void japaneseCatalogIsLocalizedAndMarksUnimplementedChecks() {
    String english = DoctorMessages.format(Locale.ROOT, DoctorMessages.DETAIL_SNAPSHOT_PASS);
    String japanese = DoctorMessages.format(Locale.JAPANESE, DoctorMessages.DETAIL_SNAPSHOT_PASS);
    assertNotEquals(english, japanese);
    assertTrue(japanese.contains("\u30b9\u30ca\u30c3\u30d7\u30b7\u30e7\u30c3\u30c8"));

    String notImplemented = DoctorMessages.format(Locale.JAPANESE, DoctorMessages.DETAIL_UPDATES);
    assertTrue(notImplemented.contains("\u672a\u5b9f\u88c5"));
  }

  @Test
  void formattedDetailsStayWithinSchemaLengthLimit() {
    StringBuilder hostile = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      hostile.append('x');
    }
    String english =
        DoctorMessages.format(
            Locale.ROOT, DoctorMessages.DETAIL_LOCALIZATION_PASS, "en", hostile.toString());
    String japanese =
        DoctorMessages.format(
            Locale.JAPANESE, DoctorMessages.DETAIL_LOCALIZATION_PASS, "ja", hostile.toString());

    assertTrue(english.length() <= DoctorText.MAX_TEXT_LENGTH);
    assertTrue(japanese.length() <= DoctorText.MAX_TEXT_LENGTH);
  }

  private static Set<String> keys(Locale locale) {
    return new HashSet<String>(bundle(locale).keySet());
  }

  private static String value(Locale locale, String key) {
    try {
      return bundle(locale).getString(key);
    } catch (MissingResourceException missing) {
      return "";
    }
  }

  private static ResourceBundle bundle(Locale locale) {
    return ResourceBundle.getBundle(
        BUNDLE_NAME, locale, DoctorCatalogTest.class.getClassLoader(), CONTROL);
  }
}
