package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.util.Locale;

/** Closed, non-secret reason why host configuration cannot create a Cloudflare verifier. */
public final class CloudflareConfigException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Closed reasons safe to pass to localized host messages. */
  public enum Reason {
    MISSING_SECTION,
    MISSING_TEAM_DOMAIN,
    INVALID_TEAM_DOMAIN,
    MISSING_AUDIENCE,
    INVALID_AUDIENCE,
    INVALID_CLOCK_SKEW,
    INVALID_CACHE_MAX_AGE,
    INVALID_MAX_STALE,
    UNSUPPORTED_SHARED_SECRET_HEADER
  }

  private final Reason reason;

  public CloudflareConfigException(Reason reason) {
    super(reason == null ? "reason" : reason.name());
    this.reason = reason == null ? Reason.MISSING_SECTION : reason;
  }

  public Reason reason() {
    return reason;
  }

  /** Stable lowercase code for localized messages and diagnostics. Never includes input values. */
  public String code() {
    return reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
