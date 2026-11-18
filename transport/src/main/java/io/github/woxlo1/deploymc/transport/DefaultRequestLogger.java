package io.github.woxlo1.deploymc.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Default request logger using the transport catalog and no header or body values. */
public final class DefaultRequestLogger implements RequestLogger {

  private final Logger logger;
  private final TransportMessages messages;

  public DefaultRequestLogger(Logger logger, TransportMessages messages) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  @Override
  public void started(String address) {
    logger.info(messages.format(MessageKeys.STARTED, values("address", address)));
  }

  @Override
  public void stopped() {
    logger.info(messages.format(MessageKeys.STOPPED));
  }

  @Override
  public void nonLoopbackWarning(String address) {
    logger.warning(messages.format(MessageKeys.WARNING_NON_LOOPBACK, values("address", address)));
  }

  @Override
  public void log(RequestLogEntry entry) {
    Map<String, Object> values = new HashMap<>();
    values.put("requestId", entry.requestId());
    values.put("method", entry.method());
    values.put("path", entry.path());
    values.put("status", entry.status());
    values.put("durationMs", entry.durationMillis());
    logger.info(messages.format(MessageKeys.REQUEST_COMPLETED, values));
  }

  @Override
  public void rejected(String requestId, String reason) {
    logger.warning(
        messages.format(
            MessageKeys.REQUEST_REJECTED, values("requestId", requestId, "reason", reason)));
  }

  @Override
  public void websocketRejected(String remoteAddress, String reason) {
    logger.warning(
        messages.format(
            MessageKeys.WEBSOCKET_REJECTED,
            values("remoteAddress", remoteAddress, "reason", reason)));
  }

  @Override
  public void websocketOpened(String connectionId, String remoteAddress) {
    logger.info(
        messages.format(
            MessageKeys.WEBSOCKET_OPENED,
            values("connectionId", connectionId, "remoteAddress", remoteAddress)));
  }

  @Override
  public void websocketClosed(String connectionId) {
    logger.info(
        messages.format(MessageKeys.WEBSOCKET_CLOSED, values("connectionId", connectionId)));
  }

  @Override
  public void websocketQueueFull(String connectionId) {
    logger.warning(
        messages.format(MessageKeys.WEBSOCKET_QUEUE_FULL, values("connectionId", connectionId)));
  }

  private static Map<String, Object> values(String key, Object value) {
    Map<String, Object> values = new HashMap<>();
    values.put(key, value);
    return values;
  }

  private static Map<String, Object> values(
      String key1, Object value1, String key2, Object value2) {
    Map<String, Object> values = new HashMap<>();
    values.put(key1, value1);
    values.put(key2, value2);
    return values;
  }
}
