package io.github.woxlo1.deploymc.core.auth.cloudflare;

/** Non-secret, closed reasons recorded for Cloudflare JWT rejections. */
public enum JwtRejection {
  MISSING_ASSERTION("auth.required"),
  MULTIPLE_ASSERTIONS("auth.invalid"),
  MALFORMED_TOKEN("auth.invalid"),
  UNSUPPORTED_ALGORITHM("auth.invalid"),
  MISSING_KEY_ID("auth.invalid"),
  UNKNOWN_KEY("auth.invalid"),
  SIGNATURE_MISMATCH("auth.invalid"),
  ISSUER_MISMATCH("auth.invalid"),
  AUDIENCE_MISMATCH("auth.invalid"),
  TOKEN_EXPIRED("auth.expired"),
  NOT_YET_VALID("auth.not-yet-valid"),
  ISSUED_IN_FUTURE("auth.not-yet-valid"),
  JWKS_UNAVAILABLE("auth.jwks-unavailable");

  private final String errorCode;

  JwtRejection(String errorCode) {
    this.errorCode = errorCode;
  }

  /** Stable RFC 9457 code exposed by the uniform authentication response. */
  public String errorCode() {
    return errorCode;
  }
}
