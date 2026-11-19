package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SenderLocaleResolverTest {

  @Test
  void resolvesLocaleAccessor() {
    assertEquals(Optional.of(Locale.JAPANESE), SenderLocaleResolver.resolve(new LocaleSender()));
  }

  @Test
  void resolvesStringLanguageTag() {
    assertEquals(
        Optional.of(Locale.forLanguageTag("ja-JP")),
        SenderLocaleResolver.resolve(new StringSender()));
  }

  @Test
  void returnsEmptyForMissingOrFailingAccessor() {
    assertFalse(SenderLocaleResolver.resolve(new NoLocaleSender()).isPresent());
    assertFalse(SenderLocaleResolver.resolve(new FailingSender()).isPresent());
    assertFalse(SenderLocaleResolver.resolve(null).isPresent());
  }

  public static final class LocaleSender {
    public Locale locale() {
      return Locale.JAPANESE;
    }
  }

  public static final class StringSender {
    public String getLocale() {
      return "ja_JP";
    }
  }

  public static final class NoLocaleSender {}

  public static final class FailingSender {
    public Locale locale() {
      throw new IllegalStateException("unavailable");
    }
  }
}
