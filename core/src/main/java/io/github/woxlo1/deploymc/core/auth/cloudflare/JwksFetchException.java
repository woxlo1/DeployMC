package io.github.woxlo1.deploymc.core.auth.cloudflare;

/** Signals that no trustworthy JWKS response could be obtained. Never carries response bodies. */
public final class JwksFetchException extends Exception {

  private static final long serialVersionUID = 1L;

  public JwksFetchException(String message) {
    super(message);
  }

  public JwksFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}
