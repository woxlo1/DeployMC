package io.github.woxlo1.deploymc.transport;

/** Stable transport failure categories. */
public enum TransportError {
  INVALID_CONFIG("transport.invalid_config"),
  ALREADY_STARTED("transport.already_started"),
  NOT_RUNNING("transport.not_running"),
  PORT_IN_USE("transport.port_in_use"),
  BIND_FAILED("transport.bind_failed"),
  START_FAILED("transport.start_failed"),
  SHUTDOWN_FAILED("transport.shutdown_failed");

  private final String code;

  TransportError(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
