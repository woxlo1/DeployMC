package io.github.woxlo1.deploymc.core.auth.cloudflare;

import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfigException.Reason;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated, non-secret Cloudflare Access inputs. The issuer is derived from the team domain. */
public final class CloudflareConfig {

  private static final Pattern HOST =
      Pattern.compile(
          "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\."
              + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");
  private static final int MAX_AUDIENCE_LENGTH = 256;

  private final String issuer;
  private final Set<String> audiences;
  private final int clockSkewSeconds;
  private final int cacheMaxAgeSeconds;
  private final int maxStaleSeconds;

  private CloudflareConfig(
      String issuer,
      Collection<String> audiences,
      int clockSkewSeconds,
      int cacheMaxAgeSeconds,
      int maxStaleSeconds) {
    this.issuer = requireIssuer(issuer);
    this.audiences = requireAudiences(audiences);
    this.clockSkewSeconds = requireRange(clockSkewSeconds, 0, 300, "clockSkewSeconds");
    this.cacheMaxAgeSeconds = requireRange(cacheMaxAgeSeconds, 60, 86_400, "cacheMaxAgeSeconds");
    this.maxStaleSeconds = requireRange(maxStaleSeconds, 0, 604_800, "maxStaleSeconds");
  }

  /** Creates a configuration whose issuer must be {@code https://<valid host>} with no path. */
  public static CloudflareConfig of(
      String issuer,
      Collection<String> audiences,
      int clockSkewSeconds,
      int cacheMaxAgeSeconds,
      int maxStaleSeconds) {
    return new CloudflareConfig(
        issuer, audiences, clockSkewSeconds, cacheMaxAgeSeconds, maxStaleSeconds);
  }

  /** Creates a configuration from the host-controlled team domain. */
  public static CloudflareConfig fromTeamDomain(
      String teamDomain,
      Collection<String> audiences,
      int clockSkewSeconds,
      int cacheMaxAgeSeconds,
      int maxStaleSeconds) {
    if (teamDomain == null
        || !HOST.matcher(teamDomain.toLowerCase(java.util.Locale.ROOT)).matches()) {
      throw new IllegalArgumentException("teamDomain is not a valid host name");
    }
    return of(
        "https://" + teamDomain.toLowerCase(java.util.Locale.ROOT),
        audiences,
        clockSkewSeconds,
        cacheMaxAgeSeconds,
        maxStaleSeconds);
  }

  /**
   * Creates a verifier configuration from the host-controlled configuration view.
   *
   * <p>No value from this file is a secret. The optional shared-secret header name is reserved and
   * must stay empty because no secret store or product consumer exists for it yet.
   */
  public static CloudflareConfig fromConfig(ConfigView view) throws CloudflareConfigException {
    if (view == null || !(view.get("auth.cloudflare") instanceof Map)) {
      throw new CloudflareConfigException(Reason.MISSING_SECTION);
    }
    String sharedSecretHeader = view.getString("auth.cloudflare.require-shared-secret-header", "");
    if (!sharedSecretHeader.isEmpty()) {
      throw new CloudflareConfigException(Reason.UNSUPPORTED_SHARED_SECRET_HEADER);
    }
    String teamDomain = view.getString("auth.cloudflare.team-domain", "");
    if (teamDomain.isEmpty()) {
      throw new CloudflareConfigException(Reason.MISSING_TEAM_DOMAIN);
    }
    if (!HOST.matcher(teamDomain.toLowerCase(java.util.Locale.ROOT)).matches()) {
      throw new CloudflareConfigException(Reason.INVALID_TEAM_DOMAIN);
    }
    Collection<String> audiences = audiences(view.get("auth.cloudflare.audience"));
    if (audiences.isEmpty()) {
      throw new CloudflareConfigException(Reason.MISSING_AUDIENCE);
    }
    int clockSkew = view.getInt("auth.cloudflare.clock-skew-seconds", 60);
    int cacheMaxAge = view.getInt("auth.cloudflare.jwks-cache-max-age-seconds", 3600);
    int maxStale = view.getInt("auth.cloudflare.jwks-max-stale-seconds", 86400);
    if (clockSkew < 0 || clockSkew > 300) {
      throw new CloudflareConfigException(Reason.INVALID_CLOCK_SKEW);
    }
    if (cacheMaxAge < 60 || cacheMaxAge > 86_400) {
      throw new CloudflareConfigException(Reason.INVALID_CACHE_MAX_AGE);
    }
    if (maxStale < 0 || maxStale > 604_800) {
      throw new CloudflareConfigException(Reason.INVALID_MAX_STALE);
    }
    try {
      return fromTeamDomain(teamDomain, audiences, clockSkew, cacheMaxAge, maxStale);
    } catch (IllegalArgumentException invalid) {
      throw new CloudflareConfigException(Reason.INVALID_AUDIENCE);
    }
  }

  private static Collection<String> audiences(Object value) throws CloudflareConfigException {
    List<String> result = new ArrayList<String>();
    if (value instanceof String) {
      result.add((String) value);
    } else if (value instanceof Collection) {
      for (Object item : (Collection<?>) value) {
        if (!(item instanceof String)) {
          throw new CloudflareConfigException(Reason.INVALID_AUDIENCE);
        }
        result.add((String) item);
      }
    } else {
      throw new CloudflareConfigException(Reason.INVALID_AUDIENCE);
    }
    return result;
  }

  /** Derived JWKS endpoint. The host never configures a raw URL. */
  public String jwksEndpoint() {
    return issuer + "/cdn-cgi/access/certs";
  }

  public String issuer() {
    return issuer;
  }

  public Set<String> audiences() {
    return audiences;
  }

  public int clockSkewSeconds() {
    return clockSkewSeconds;
  }

  public int cacheMaxAgeSeconds() {
    return cacheMaxAgeSeconds;
  }

  public int maxStaleSeconds() {
    return maxStaleSeconds;
  }

  private static String requireIssuer(String value) {
    Objects.requireNonNull(value, "issuer");
    if (!value.startsWith("https://") || value.length() <= 8 || value.indexOf('/', 8) >= 0) {
      throw new IllegalArgumentException("issuer must be an https host without a path");
    }
    String host = value.substring(8);
    if (!HOST.matcher(host.toLowerCase(java.util.Locale.ROOT)).matches()) {
      throw new IllegalArgumentException("issuer host is invalid");
    }
    return "https://" + host.toLowerCase(java.util.Locale.ROOT);
  }

  private static Set<String> requireAudiences(Collection<String> values) {
    Objects.requireNonNull(values, "audiences");
    if (values.isEmpty() || values.size() > 16) {
      throw new IllegalArgumentException("audiences must contain 1 to 16 values");
    }
    Set<String> result = new LinkedHashSet<String>();
    for (String value : values) {
      Objects.requireNonNull(value, "audience");
      if (value.isEmpty() || value.length() > MAX_AUDIENCE_LENGTH || !value.trim().equals(value)) {
        throw new IllegalArgumentException("audience is empty, padded, or too long");
      }
      for (int index = 0; index < value.length(); index++) {
        char character = value.charAt(index);
        if (character <= 0x20 || character == 0x7f) {
          throw new IllegalArgumentException("audience contains a control character");
        }
      }
      result.add(value);
    }
    return Collections.unmodifiableSet(result);
  }

  private static int requireRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
    return value;
  }
}
