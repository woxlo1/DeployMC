package io.github.woxlo1.deploymc.core.spi;

import java.util.Map;

/** Sink for localized messages delivered to the server console or players. */
public interface MessageSink {

  /**
   * Sends a message identified by a stable key.
   *
   * @param key stable message key from the message catalog
   * @param placeholders named placeholder values, never pre-formatted sentences
   */
  void send(String key, Map<String, String> placeholders);
}
