package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransportMessagesTest {

  @Test
  void requestedLocaleIgnoresJvmDefaultLocale() {
    Locale previous = Locale.getDefault();
    Map<String, Object> values = Collections.<String, Object>singletonMap("address", "127.0.0.1");
    try {
      Locale.setDefault(Locale.JAPANESE);

      String english = TransportMessages.of(Locale.ENGLISH).format(MessageKeys.STARTED, values);
      String japanese = TransportMessages.of(Locale.JAPANESE).format(MessageKeys.STARTED, values);
      String unsupported = TransportMessages.of(Locale.FRENCH).format(MessageKeys.STARTED, values);

      assertTrue(english.startsWith("Embedded transport"), english);
      assertNotEquals(english, japanese);
      assertEquals(english, unsupported);
    } finally {
      Locale.setDefault(previous);
    }
  }
}
