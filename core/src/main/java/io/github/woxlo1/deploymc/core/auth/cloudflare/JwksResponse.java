package io.github.woxlo1.deploymc.core.auth.cloudflare;

import java.util.Arrays;
import java.util.Objects;

/** Bounded result of one JWKS fetch. An ETag revalidation returns {@code notModified}. */
public final class JwksResponse {

  private static final JwksResponse NOT_MODIFIED = new JwksResponse(null, null, 0, true);

  private final byte[] body;
  private final String etag;
  private final int maxAgeSeconds;
  private final boolean notModified;

  private JwksResponse(byte[] body, String etag, int maxAgeSeconds, boolean notModified) {
    this.body = body;
    this.etag = etag;
    this.maxAgeSeconds = maxAgeSeconds;
    this.notModified = notModified;
  }

  public static JwksResponse cached(byte[] body, String etag, int maxAgeSeconds) {
    Objects.requireNonNull(body, "body");
    if (body.length == 0 || body.length > HttpJwksClient.MAX_RESPONSE_BYTES) {
      throw new IllegalArgumentException("JWKS body is empty or too large");
    }
    if (maxAgeSeconds < 0 || maxAgeSeconds > 86_400) {
      throw new IllegalArgumentException("JWKS max age must be between 0 and 86400");
    }
    return new JwksResponse(Arrays.copyOf(body, body.length), etag, maxAgeSeconds, false);
  }

  public static JwksResponse notModified() {
    return NOT_MODIFIED;
  }

  public boolean isNotModified() {
    return notModified;
  }

  public byte[] body() {
    return body == null ? new byte[0] : Arrays.copyOf(body, body.length);
  }

  public String etag() {
    return etag;
  }

  public int maxAgeSeconds() {
    return maxAgeSeconds;
  }
}
