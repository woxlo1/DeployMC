package io.github.woxlo1.deploymc.transport;

/** Failure raised by transport lifecycle operations. */
public final class TransportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final TransportError error;

  public TransportException(TransportError error) {
    super(error.code());
    this.error = error;
  }

  public TransportException(TransportError error, Throwable cause) {
    super(error.code(), cause);
    this.error = error;
  }

  public TransportError error() {
    return error;
  }
}
