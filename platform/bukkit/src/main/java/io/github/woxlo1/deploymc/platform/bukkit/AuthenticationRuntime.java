package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.OneTimeToken;
import io.github.woxlo1.deploymc.core.auth.OneTimeTokenAuthenticator;
import io.github.woxlo1.deploymc.core.auth.TokenAuthenticator;
import io.github.woxlo1.deploymc.core.auth.TokenProvisioner;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareConfig;
import io.github.woxlo1.deploymc.core.auth.cloudflare.CloudflareJwtVerifier;
import io.github.woxlo1.deploymc.core.auth.cloudflare.HttpJwksClient;
import io.github.woxlo1.deploymc.core.auth.cloudflare.JwtDiagnostics;
import io.github.woxlo1.deploymc.core.auth.cloudflare.JwtDiagnosticsSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Host-owned authentication components created at startup, never exposed through the API. */
public final class AuthenticationRuntime {

  private final TokenStore store;
  private final Authenticator authenticator;
  private final TokenProvisioner provisioner;
  private final Duration defaultTokenLifetime;
  private final JwtDiagnostics jwtDiagnostics;
  private final OneTimeToken oneTimeToken;

  private AuthenticationRuntime(
      TokenStore store,
      Authenticator authenticator,
      TokenProvisioner provisioner,
      Duration defaultTokenLifetime,
      JwtDiagnostics jwtDiagnostics,
      OneTimeToken oneTimeToken) {
    this.store = store;
    this.authenticator = authenticator;
    this.provisioner = provisioner;
    this.defaultTokenLifetime = defaultTokenLifetime;
    this.jwtDiagnostics = jwtDiagnostics;
    this.oneTimeToken = oneTimeToken;
  }

  /** Creates the host-provisioned token runtime. */
  public static AuthenticationRuntime create(
      TokenStore store,
      Clock clock,
      java.security.SecureRandom random,
      Duration defaultTokenLifetime) {
    Objects.requireNonNull(store, "store");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(random, "random");
    return new AuthenticationRuntime(
        store,
        new TokenAuthenticator(store, clock),
        new TokenProvisioner(store, clock, random),
        defaultTokenLifetime,
        null,
        null);
  }

  /**
   * Creates the in-process local testing runtime. No token store, provisioner, or JWT verifier is
   * created, and the token is held only in memory until the process exits.
   */
  public static AuthenticationRuntime createLocalOneTime(
      Clock clock, java.security.SecureRandom random, Duration lifetime, int maxAttempts) {
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(random, "random");
    Objects.requireNonNull(lifetime, "lifetime");
    OneTimeToken token = OneTimeToken.issue(random, clock, lifetime);
    return new AuthenticationRuntime(
        null, new OneTimeTokenAuthenticator(token, clock, maxAttempts), null, null, null, token);
  }

  /** Creates the Cloudflare Access runtime. No host token store or provisioner is created. */
  public static AuthenticationRuntime createCloudflare(CloudflareConfig config, Clock clock) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(clock, "clock");
    JwtDiagnostics diagnostics = new JwtDiagnostics(clock);
    Authenticator verifier =
        new CloudflareJwtVerifier(
            config, new HttpJwksClient(config.jwksEndpoint()), clock, diagnostics);
    return new AuthenticationRuntime(null, verifier, null, null, diagnostics, null);
  }

  public TokenStore store() {
    return store;
  }

  public Authenticator authenticator() {
    return authenticator;
  }

  public TokenProvisioner provisioner() {
    return provisioner;
  }

  public Duration defaultTokenLifetime() {
    return defaultTokenLifetime;
  }

  /** Null outside local one-time mode. The secret is printed once by the host console. */
  public OneTimeToken oneTimeToken() {
    return oneTimeToken;
  }

  /** False in Cloudflare and local one-time modes: a provisioned host token would be unusable. */
  public boolean tokenProvisioningEnabled() {
    return provisioner != null;
  }

  /** Null outside Cloudflare mode. The object contains only closed rejection counters. */
  public JwtDiagnostics jwtDiagnostics() {
    return jwtDiagnostics;
  }

  /** Null outside Cloudflare mode. The snapshot is safe to pass into the doctor report. */
  public JwtDiagnosticsSnapshot jwtDiagnosticsSnapshot() {
    return jwtDiagnostics == null ? null : jwtDiagnostics.snapshot();
  }
}
