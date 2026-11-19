package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.TokenProvisioner;
import io.github.woxlo1.deploymc.core.auth.TokenStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Host-owned authentication components created at startup, never exposed through the API. */
public final class AuthenticationRuntime {

  private final TokenStore store;
  private final Authenticator authenticator;
  private final TokenProvisioner provisioner;
  private final Duration defaultTokenLifetime;

  private AuthenticationRuntime(
      TokenStore store,
      Authenticator authenticator,
      TokenProvisioner provisioner,
      Duration defaultTokenLifetime) {
    this.store = store;
    this.authenticator = authenticator;
    this.provisioner = provisioner;
    this.defaultTokenLifetime = defaultTokenLifetime;
  }

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
        new io.github.woxlo1.deploymc.core.auth.TokenAuthenticator(store, clock),
        new TokenProvisioner(store, clock, random),
        defaultTokenLifetime);
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
}
