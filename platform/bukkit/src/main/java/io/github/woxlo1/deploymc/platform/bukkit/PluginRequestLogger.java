package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.transport.MessageKeys;
import io.github.woxlo1.deploymc.transport.RequestLogEntry;
import io.github.woxlo1.deploymc.transport.RequestLogger;
import io.github.woxlo1.deploymc.transport.TransportMessages;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Routes transport lifecycle and redacted request events through localized plugin messages. */
public final class PluginRequestLogger implements RequestLogger {

  private final Logger logger;
  private final TransportMessages messages;

  public PluginRequestLogger(Logger logger, TransportMessages messages) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  @Override
  public void started(String address) {
    logger.info(format(MessageKeys.STARTED, "address", address));
  }

  @Override
  public void stopped() {
    logger.info(messages.format(MessageKeys.STOPPED));
  }

  @Override
  public void nonLoopbackWarning(String address) {
    logger.warning(format(MessageKeys.WARNING_NON_LOOPBACK, "address", address));
  }

  @Override
  public void log(RequestLogEntry entry) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("requestId", entry.requestId());
    values.put("method", entry.method());
    values.put("path", entry.path());
    values.put("status", Integer.valueOf(entry.status()));
    values.put("durationMs", Long.valueOf(entry.durationMillis()));
    logger.info(messages.format(MessageKeys.REQUEST_COMPLETED, values));
  }

  @Override
  public void rejected(String requestId, String reason) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("requestId", requestId);
    values.put("reason", reason);
    logger.info(messages.format(MessageKeys.REQUEST_REJECTED, values));
  }

  @Override
  public void websocketRejected(String remoteAddress, String reason) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("remoteAddress", remoteAddress);
    values.put("reason", reason);
    logger.info(messages.format(MessageKeys.WEBSOCKET_REJECTED, values));
  }

  @Override
  public void websocketOpened(String connectionId, String remoteAddress) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("connectionId", connectionId);
    values.put("remoteAddress", remoteAddress);
    logger.info(messages.format(MessageKeys.WEBSOCKET_OPENED, values));
  }

  @Override
  public void websocketClosed(String connectionId) {
    logger.info(format(MessageKeys.WEBSOCKET_CLOSED, "connectionId", connectionId));
  }

  @Override
  public void websocketQueueFull(String connectionId) {
    logger.warning(format(MessageKeys.WEBSOCKET_QUEUE_FULL, "connectionId", connectionId));
  }

  private String format(String key, String placeholder, String value) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put(placeholder, value);
    return messages.format(key, values);
  }
}
