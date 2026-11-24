package io.github.woxlo1.deploymc.core.auth;

/**
 * Framework signal that an authenticated identity lacks permission for an operation.
 *
 * <p>WP-3.4 supplies the RBAC engine. This exception only establishes the uniform, detail-free 403
 * path in the transport; it carries no identity, path, or policy information.
 */
public final class AuthorizationDeniedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Stable public code from the API error catalog. */
  public static final String DEFAULT_CODE = "rbac.denied";

  private final String code;

  public AuthorizationDeniedException() {
    this(DEFAULT_CODE);
  }

  public AuthorizationDeniedException(String code) {
    super(null, null, false, false);
    if (code == null || code.trim().isEmpty()) {
      throw new IllegalArgumentException("code must not be empty");
    }
    this.code = code;
  }

  public String code() {
    return code;
  }
}
