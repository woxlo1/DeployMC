package io.github.woxlo1.deploymc.transport;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Structured diagnostic logger. Emits fixed fields and never logs addresses or raw paths. */
public final class JsonRequestLogger implements RequestLogger {

  private static final Pattern REASON = Pattern.compile("^[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)*$");

  private final Logger logger;

  public JsonRequestLogger(Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public void started(String address) {
    logger.info(event("started"));
  }

  @Override
  public void stopped() {
    logger.info(event("stopped"));
  }

  @Override
  public void nonLoopbackWarning(String address) {
    logger.info(event("non-loopback-warning"));
  }

  @Override
  public void log(RequestLogEntry entry) {
    Objects.requireNonNull(entry, "entry");
    StringBuilder json = new StringBuilder();
    json.append('{');
    field(json, "requestId", entry.requestId(), true);
    field(json, "method", safeMethod(entry.method()), false);
    field(json, "path", safePath(entry.path()), false);
    json.append(",\"status\":").append(entry.status());
    json.append(",\"durationMillis\":").append(entry.durationMillis());
    field(json, "outcome", safeReason(entry.outcome()), false);
    json.append('}');
    logger.info(json.toString());
  }

  @Override
  public void rejected(String requestId, String reason) {
    StringBuilder json = new StringBuilder();
    json.append('{');
    field(json, "event", "request-rejected", true);
    field(json, "requestId", requestId == null ? "unknown" : requestId, false);
    field(json, "reason", safeReason(reason), false);
    json.append('}');
    logger.info(json.toString());
  }

  @Override
  public void websocketRejected(String remoteAddress, String reason) {
    logger.info(
        "{\"event\":\"websocket-rejected\",\"reason\":\"" + escape(safeReason(reason)) + "\"}");
  }

  @Override
  public void websocketOpened(String connectionId, String remoteAddress) {
    logger.info(event("websocket-opened"));
  }

  @Override
  public void websocketClosed(String connectionId) {
    logger.info(event("websocket-closed"));
  }

  @Override
  public void websocketQueueFull(String connectionId) {
    logger.info(event("websocket-queue-full"));
  }

  private static String event(String name) {
    return "{\"event\":\"" + escape(name) + "\"}";
  }

  private static String safePath(String path) {
    if (path == null) {
      return EndpointClass.OTHER.wireLabel();
    }
    for (EndpointClass endpoint : EndpointClass.values()) {
      if (endpoint.wireLabel().equals(path)) {
        return endpoint.wireLabel();
      }
    }
    return EndpointClass.fromPath(path).wireLabel();
  }

  private static String safeMethod(String method) {
    if (method == null) {
      return "OTHER";
    }
    String upper = method.toUpperCase(Locale.ROOT);
    String[] allowed = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"};
    for (String candidate : allowed) {
      if (candidate.equals(upper)) {
        return candidate;
      }
    }
    return "OTHER";
  }

  private static String safeReason(String reason) {
    if (reason == null || !REASON.matcher(reason).matches()) {
      return "other";
    }
    return reason;
  }

  private static void field(StringBuilder json, String name, String value, boolean first) {
    if (!first) {
      json.append(',');
    }
    json.append('"').append(name).append("\":\"").append(escape(value)).append('"');
  }

  static String escape(String value) {
    Objects.requireNonNull(value, "value");
    StringBuilder escaped = new StringBuilder(value.length() + 8);
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (character < 0x20) {
            escaped.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character)));
          } else {
            escaped.append(character);
          }
      }
    }
    return escaped.toString();
  }
}
