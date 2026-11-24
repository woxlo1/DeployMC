package io.github.woxlo1.deploymc.core.auth;

/**
 * Final WP-3.1 authentication boundary for HTTP requests and WebSocket upgrades.
 *
 * <p>An implementation receives only an immutable {@link AuthenticationRequest} and returns an
 * {@link AuthenticationResult}. It must derive every identity field from verified credential
 * material, never from untrusted request attributes or client-supplied identity headers.
 */
public interface Authenticator {

  AuthenticationResult authenticate(AuthenticationRequest request);
}
