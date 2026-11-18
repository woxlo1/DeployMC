package io.github.woxlo1.deploymc.transport;

import java.util.Objects;

/** Redacted request log record. Never carries headers, cookies, or bodies. */
public final class RequestLogEntry {

  private final String requestId;
  private final String method;
  private final String path;
  private final int status;
  private final long durationMillis;
  private final String outcome;

  public RequestLogEntry(
      String requestId,
      String method,
      String path,
      int status,
      long durationMillis,
      String outcome) {
    this.requestId = Objects.requireNonNull(requestId, "requestId");
    this.method = Objects.requireNonNull(method, "method");
    this.path = Objects.requireNonNull(path, "path");
    this.status = status;
    this.durationMillis = durationMillis;
    this.outcome = Objects.requireNonNull(outcome, "outcome");
  }

  public String requestId() {
    return requestId;
  }

  public String method() {
    return method;
  }

  public String path() {
    return path;
  }

  public int status() {
    return status;
  }

  public long durationMillis() {
    return durationMillis;
  }

  public String outcome() {
    return outcome;
  }
}
