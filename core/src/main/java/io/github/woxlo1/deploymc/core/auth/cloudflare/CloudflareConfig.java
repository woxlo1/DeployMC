package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
