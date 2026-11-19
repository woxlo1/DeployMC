package io.github.woxlo1.deploymc.core.auth;

/**
 * Minimal authentication boundary used by HTTP requests and WebSocket upgrades.
 *
 * <p>The argument is the raw {@code Authorization} header value or {@code null}. The final form in
 * WP-3.1 will accept a richer request context; this interface is deliberately narrow so that no
 * caller can supply an identity directly.
 */
public interface Authenticator {

  AuthenticationResult authenticate(String authorizationHeader);
}
