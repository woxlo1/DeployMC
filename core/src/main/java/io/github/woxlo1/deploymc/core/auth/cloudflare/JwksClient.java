package io.github.woxlo1.deploymc.core.auth.cloudflare;

/** Host-controlled signing-key source. Implementations must not follow untrusted redirects. */
public interface JwksClient {

  /** Fetches the JWKS document, optionally revalidating the supplied strong ETag. */
  JwksResponse fetch(String etag) throws JwksFetchException;
}
