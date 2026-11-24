package io.github.woxlo1.deploymc.core.auth.cloudflare;

import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationRequest;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * WP-3.2 {@link Authenticator} for Cloudflare Access JWT assertions.
 *
 * <p>Only the signed {@code Cf-Access-Jwt-Assertion} header is trusted. The plain {@code
 * Cf-Access-Authenticated-User-Email} header is deliberately ignored because anything that can
 * reach the port could forge it. Verification is fail closed: no usable signing key means no
 * identity. Diagnostics retain only closed rejection reasons, never token or header contents.
 *
 * <p>This class verifies locally supplied assertions against a host-configured JWKS endpoint. It
 * does not claim compatibility with any live Cloudflare plan or domain; that requires separate
 * evidence recorded in the project decisions document.
 */
public final class CloudflareJwtVerifier implements Authenticator {

  static final String ASSERTION_HEADER = "cf-access-jwt-assertion";
  static final String FORWARDED_EMAIL_HEADER = "cf-access-authenticated-user-email";
  static final String IDENTITY_METHOD = "cloudflare-access";

  private static final int MAX_ASSERTION_CHARACTERS = 8192;
  private static final int MAX_SEGMENT_CHARACTERS = 4096;
  private static final int MAX_KID_CHARACTERS = 256;
  private static final int MAX_SUBJECT_CHARACTERS = 256;
  private static final int MAX_EMAIL_CHARACTERS = 320;
  private static final int MAX_COMMON_NAME_CHARACTERS = 256;
  private static final int MAX_COMMON_CLAIM_CHARACTERS = 256;
  private static final int MAX_JWKS_KEYS = 16;
  private static final int MIN_REFRESH_INTERVAL_MILLIS = 5_000;
  private static final String ALGORITHM = "RS256";

  private final CloudflareConfig config;
  private final JwksClient jwksClient;
  private final Clock clock;
  private final JwtDiagnostics diagnostics;
  private final Object cacheLock = new Object();
  private volatile Cache cache;

  public CloudflareJwtVerifier(
      CloudflareConfig config, JwksClient jwksClient, Clock clock, JwtDiagnostics diagnostics) {
    this.config = Objects.requireNonNull(config, "config");
    this.jwksClient = Objects.requireNonNull(jwksClient, "jwksClient");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
  }

  /** Read-only rejection counters for the doctor check. */
  public JwtDiagnostics diagnostics() {
    return diagnostics;
  }

  @Override
  public AuthenticationResult authenticate(AuthenticationRequest request) {
    if (request == null) {
      return reject(JwtRejection.MALFORMED_TOKEN);
    }
    List<String> assertions = request.headers().get(ASSERTION_HEADER);
    if (assertions == null || assertions.isEmpty() || assertions.get(0).isEmpty()) {
      return reject(JwtRejection.MISSING_ASSERTION);
    }
    if (assertions.size() > 1) {
      return reject(JwtRejection.MULTIPLE_ASSERTIONS);
    }
    String token = assertions.get(0);
    if (token.length() > MAX_ASSERTION_CHARACTERS) {
      return reject(JwtRejection.MALFORMED_TOKEN);
    }

    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || parts[2].isEmpty()) {
      return reject(JwtRejection.MALFORMED_TOKEN);
    }
    String headerSegment = parts[0];
    String payloadSegment = parts[1];
    String signatureSegment = parts[2];

    Map<String, Object> header;
    Map<String, Object> payload;
    byte[] signature;
    try {
      header =
          JwtJson.asObject(
              JwtJson.parse(new String(decodeSegment(headerSegment), StandardCharsets.UTF_8)));
      payload =
          JwtJson.asObject(
              JwtJson.parse(new String(decodeSegment(payloadSegment), StandardCharsets.UTF_8)));
      signature = decodeSegment(signatureSegment);
    } catch (IllegalArgumentException exception) {
      return reject(JwtRejection.MALFORMED_TOKEN);
    }

    if (!ALGORITHM.equals(header.get("alg"))) {
      return reject(JwtRejection.UNSUPPORTED_ALGORITHM);
    }
    Object typ = header.get("typ");
    if (typ != null && !"JWT".equals(typ)) {
      return reject(JwtRejection.MALFORMED_TOKEN);
    }
    String kid;
    try {
      kid = boundedString(header.get("kid"), MAX_KID_CHARACTERS);
    } catch (IllegalArgumentException exception) {
      return reject(JwtRejection.MISSING_KEY_ID);
    }

    KeyLookup lookup = findKey(kid);
    if (lookup.unavailable) {
      return reject(JwtRejection.JWKS_UNAVAILABLE);
    }
    if (lookup.key == null) {
      return reject(JwtRejection.UNKNOWN_KEY);
    }
    if (!verifySignature(lookup.key, headerSegment + "." + payloadSegment, signature)) {
      return reject(JwtRejection.SIGNATURE_MISMATCH);
    }

    try {
      return authenticated(payload);
    } catch (IllegalArgumentException exception) {
      return reject(JwtRejection.MALFORMED_TOKEN);
    }
  }

  private AuthenticationResult authenticated(Map<String, Object> payload) {
    if (!config.issuer().equals(payload.get("iss"))) {
      return reject(JwtRejection.ISSUER_MISMATCH);
    }
    if (!audienceMatches(payload.get("aud"))) {
      return reject(JwtRejection.AUDIENCE_MISMATCH);
    }
    long expiration = JwtJson.asInteger(payload.get("exp"));
    long notBefore = JwtJson.asInteger(payload.get("nbf"));
    long issuedAt = JwtJson.asInteger(payload.get("iat"));
    long now = clock.instant().getEpochSecond();
    int skew = config.clockSkewSeconds();
    if (now > expiration + skew) {
      return reject(JwtRejection.TOKEN_EXPIRED);
    }
    if (now < notBefore - skew) {
      return reject(JwtRejection.NOT_YET_VALID);
    }
    if (now < issuedAt - skew) {
      return reject(JwtRejection.ISSUED_IN_FUTURE);
    }

    String subject = boundedString(payload.get("sub"), MAX_SUBJECT_CHARACTERS);
    String email = optionalString(payload, "email", MAX_EMAIL_CHARACTERS);
    String commonName = optionalString(payload, "common_name", MAX_COMMON_NAME_CHARACTERS);
    String jti = optionalString(payload, "jti", MAX_COMMON_CLAIM_CHARACTERS);
    String id = email != null ? email : commonName != null ? commonName : subject;
    String label = commonName != null ? commonName : email != null ? email : subject;
    Map<String, String> claims = new TreeMap<String, String>();
    claims.put("sub", subject);
    claims.put("iss", config.issuer());
    if (email != null) {
      claims.put("email", email);
    }
    if (commonName != null) {
      claims.put("common_name", commonName);
    }
    if (jti != null) {
      claims.put("jti", jti);
    }
    return AuthenticationResult.authenticated(
        new Identity(id, label, IDENTITY_METHOD, false, Collections.<String>emptyList(), claims));
  }

  private boolean audienceMatches(Object value) {
    Set<String> presented = new LinkedHashSet<String>();
    if (value instanceof String) {
      presented.add((String) value);
    } else if (value instanceof List) {
      for (Object item : (List<?>) value) {
        if (!(item instanceof String)) {
          return false;
        }
        presented.add((String) item);
      }
    } else {
      return false;
    }
    for (String audience : presented) {
      if (config.audiences().contains(audience)) {
        return true;
      }
    }
    return false;
  }

  private AuthenticationResult reject(JwtRejection reason) {
    diagnostics.record(reason);
    return AuthenticationResult.failed(failure(reason));
  }

  private static AuthenticationFailure failure(JwtRejection reason) {
    String code = reason.errorCode();
    if ("auth.required".equals(code)) {
      return AuthenticationFailure.REQUIRED;
    }
    if ("auth.expired".equals(code)) {
      return AuthenticationFailure.EXPIRED;
    }
    if ("auth.not-yet-valid".equals(code)) {
      return AuthenticationFailure.NOT_YET_VALID;
    }
    if ("auth.jwks-unavailable".equals(code)) {
      return AuthenticationFailure.JWKS_UNAVAILABLE;
    }
    return AuthenticationFailure.INVALID;
  }

  private KeyLookup findKey(String kid) {
    long now = clock.millis();
    Cache current = cache;
    if (current == null || now >= current.refreshAtMillis) {
      synchronized (cacheLock) {
        current = cache;
        if (current == null || now >= current.refreshAtMillis) {
          current = refresh(current, now);
          cache = current;
        }
      }
    }
    if (current == null || current.unavailable) {
      return new KeyLookup(null, true);
    }
    return new KeyLookup(current.keys.get(kid), false);
  }

  private Cache refresh(Cache previous, long now) {
    if (previous != null && now - previous.lastFetchMillis < MIN_REFRESH_INTERVAL_MILLIS) {
      return previous;
    }
    try {
      JwksResponse response = jwksClient.fetch(previous == null ? null : previous.etag);
      if (response.isNotModified()) {
        if (previous == null) {
          throw new JwksFetchException("unexpected not-modified response without a cached key set");
        }
        return new Cache(
            previous.keys,
            previous.etag,
            now + previous.maxAgeMillis,
            now + config.maxStaleSeconds() * 1000L,
            now,
            previous.maxAgeMillis,
            false);
      }
      Map<String, PublicKey> keys = parseJwks(response.body());
      long maxAgeMillis = Math.min(response.maxAgeSeconds(), config.cacheMaxAgeSeconds()) * 1000L;
      return new Cache(
          keys,
          response.etag(),
          now + maxAgeMillis,
          now + config.maxStaleSeconds() * 1000L,
          now,
          maxAgeMillis,
          false);
    } catch (JwksFetchException | RuntimeException exception) {
      if (previous != null && now < previous.staleUntilMillis) {
        return new Cache(
            previous.keys,
            previous.etag,
            now + MIN_REFRESH_INTERVAL_MILLIS,
            previous.staleUntilMillis,
            previous.lastFetchMillis,
            previous.maxAgeMillis,
            false);
      }
      return new Cache(
          Collections.<String, PublicKey>emptyMap(),
          null,
          now + MIN_REFRESH_INTERVAL_MILLIS,
          now,
          now,
          0L,
          true);
    }
  }

  private static Map<String, PublicKey> parseJwks(byte[] body) throws JwksFetchException {
    try {
      Map<String, Object> document =
          JwtJson.asObject(JwtJson.parse(new String(body, StandardCharsets.US_ASCII)));
      List<Object> entries = JwtJson.asArray(document.get("keys"));
      if (entries.isEmpty() || entries.size() > MAX_JWKS_KEYS) {
        throw new JwksFetchException("JWKS key count is outside the supported range");
      }
      Map<String, PublicKey> result = new LinkedHashMap<String, PublicKey>();
      KeyFactory factory = KeyFactory.getInstance("RSA");
      for (Object entry : entries) {
        Map<String, Object> jwk = JwtJson.asObject(entry);
        if (!"RSA".equals(jwk.get("kty"))) {
          continue;
        }
        Object use = jwk.get("use");
        if (use != null && !"sig".equals(use)) {
          continue;
        }
        Object algorithm = jwk.get("alg");
        if (algorithm != null && !ALGORITHM.equals(algorithm)) {
          continue;
        }
        String kid = boundedString(jwk.get("kid"), MAX_KID_CHARACTERS);
        BigInteger modulus = new BigInteger(1, decodeSegment(JwtJson.asString(jwk.get("n"))));
        BigInteger exponent = new BigInteger(1, decodeSegment(JwtJson.asString(jwk.get("e"))));
        if (result.containsKey(kid)) {
          throw new JwksFetchException("JWKS contains a duplicate key id");
        }
        result.put(kid, factory.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
      }
      if (result.isEmpty()) {
        throw new JwksFetchException("JWKS contains no usable RSA signing keys");
      }
      return Collections.unmodifiableMap(result);
    } catch (GeneralSecurityException exception) {
      throw new JwksFetchException("JWKS contains an unusable RSA key", exception);
    } catch (IllegalArgumentException exception) {
      throw new JwksFetchException("JWKS document is malformed", exception);
    }
  }

  private static boolean verifySignature(PublicKey key, String signingInput, byte[] signature) {
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key);
      verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return verifier.verify(signature);
    } catch (GeneralSecurityException exception) {
      return false;
    }
  }

  private static String boundedString(Object value, int maximum) {
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("expected a string claim");
    }
    String result = (String) value;
    if (result.isEmpty() || result.length() > maximum) {
      throw new IllegalArgumentException("string claim is empty or too long");
    }
    for (int index = 0; index < result.length(); index++) {
      char character = result.charAt(index);
      if (character < 0x20 || character == 0x7f) {
        throw new IllegalArgumentException("string claim contains a control character");
      }
    }
    return result;
  }

  private static String optionalString(Map<String, Object> payload, String name, int maximum) {
    Object value = payload.get(name);
    if (value == null) {
      return null;
    }
    return boundedString(value, maximum);
  }

  private static byte[] decodeSegment(String segment) {
    if (segment.isEmpty() || segment.length() > MAX_SEGMENT_CHARACTERS) {
      throw new IllegalArgumentException("JWT segment is empty or too long");
    }
    for (int index = 0; index < segment.length(); index++) {
      char character = segment.charAt(index);
      if (!(character >= 'A' && character <= 'Z')
          && !(character >= 'a' && character <= 'z')
          && !(character >= '0' && character <= '9')
          && character != '-'
          && character != '_') {
        throw new IllegalArgumentException("JWT segment is not base64url");
      }
    }
    try {
      return Base64.getUrlDecoder().decode(segment);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("JWT segment is not valid base64url");
    }
  }

  private static final class KeyLookup {
    private final PublicKey key;
    private final boolean unavailable;

    KeyLookup(PublicKey key, boolean unavailable) {
      this.key = key;
      this.unavailable = unavailable;
    }
  }

  private static final class Cache {
    private final Map<String, PublicKey> keys;
    private final String etag;
    private final long refreshAtMillis;
    private final long staleUntilMillis;
    private final long lastFetchMillis;
    private final long maxAgeMillis;
    private final boolean unavailable;

    Cache(
        Map<String, PublicKey> keys,
        String etag,
        long refreshAtMillis,
        long staleUntilMillis,
        long lastFetchMillis,
        long maxAgeMillis,
        boolean unavailable) {
      this.keys = keys;
      this.etag = etag;
      this.refreshAtMillis = refreshAtMillis;
      this.staleUntilMillis = staleUntilMillis;
      this.lastFetchMillis = lastFetchMillis;
      this.maxAgeMillis = maxAgeMillis;
      this.unavailable = unavailable;
    }
  }
}
